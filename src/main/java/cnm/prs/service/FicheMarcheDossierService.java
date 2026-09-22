package cnm.prs.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DossierDto;
import cnm.prs.dto.FicheMarcheResumeDto;
import cnm.prs.dto.FicheRattachableDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.TypeDmcRepository;
import cnm.prs.security.PerimetreDossier;

/**
 * ⚠️ <strong>La fiche marché et le dossier soumis à la CNM</strong> (demande front du 2026-09-23, lot 1b, arbitrage
 * pilote : « la fiche produit le dossier »). La liaison est {@code t_dossier.ID_DMC} (V36, unique, sous-type
 * {@code DAO} seulement).
 *
 * <ul>
 *   <li><strong>B2, chemin principal</strong> — {@link #creerDossier} : la PRMP produit, depuis une fiche
 *       <em>validée</em>, le dossier {@code DMC} / {@code DAO} en brouillon, en-tête dérivé de la ligne du PPM ;
 *       ses pièces attendues restent celles du sous-type, à joindre à la main (lot 2 : le DAO complet généré).</li>
 *   <li><strong>B3, secours</strong> — {@link #rattacher} / {@link #detacher} : un dossier {@code DAO} créé à la main
 *       (le n° 100332) reçoit une fiche validée, ou la perd, tant qu'il est brouillon.</li>
 * </ul>
 *
 * <p><strong>Ordre des gardes</strong> (celui du lot 1) : 404 → 403 périmètre (avant tout 409 : l'existence d'un
 * objet d'autrui ne se devine pas) → vacance de mandat → 409 à code stable.</p>
 */
@Service
@Transactional
public class FicheMarcheDossierService {

    private final DossierMecRepository dmcRepository;
    private final TypeDmcRepository typeDmcRepository;
    private final MarcheRepository marcheRepository;
    private final DossierRepository dossierRepository;
    private final FicheMarcheRepository ficheRepository;
    private final PerimetreDossier perimetre;
    private final DossierIntegriteService dossierIntegrite;
    private final SaisieService saisieService;
    private final DossierService dossierService;
    private final ValeursPpmService valeursPpm;
    private final FicheMarcheService ficheMarcheService;
    private final JournalDossierService journal;

    public FicheMarcheDossierService(DossierMecRepository dmcRepository, TypeDmcRepository typeDmcRepository,
            MarcheRepository marcheRepository, DossierRepository dossierRepository,
            FicheMarcheRepository ficheRepository, PerimetreDossier perimetre,
            DossierIntegriteService dossierIntegrite, SaisieService saisieService, DossierService dossierService,
            ValeursPpmService valeursPpm, FicheMarcheService ficheMarcheService, JournalDossierService journal) {
        this.dmcRepository = dmcRepository;
        this.typeDmcRepository = typeDmcRepository;
        this.marcheRepository = marcheRepository;
        this.dossierRepository = dossierRepository;
        this.ficheRepository = ficheRepository;
        this.perimetre = perimetre;
        this.dossierIntegrite = dossierIntegrite;
        this.saisieService = saisieService;
        this.dossierService = dossierService;
        this.valeursPpm = valeursPpm;
        this.ficheMarcheService = ficheMarcheService;
        this.journal = journal;
    }

    /**
     * B2 — produit le dossier soumis d'une fiche validée. 404 fiche inconnue → 403 plan hors périmètre → vacance →
     * 409 {@code DMC_NON_DAO}, {@code DOSSIER_EXISTANT} (avec l'{@code idDossier} existant, avant le statut de la
     * fiche : une révision ouverte depuis ne cache pas le dossier déjà produit), {@code FICHE_NON_VALIDEE}.
     */
    public DossierDto creerDossier(Long idDmc) {
        DossierMec dmc = chargerDmc(idDmc);
        perimetre.controler(dossierDuPlan(dmc));
        dossierIntegrite.exigerMandatActif();
        exigerDao(dmc);
        dossierRepository.findIdDossierByIdDmc(idDmc).ifPresent(id -> {
            throw new BusinessRuleException("Cette fiche marché a déjà produit le dossier " + id + ".",
                    "DOSSIER_EXISTANT", id);
        });
        FicheMarche fiche = exigerValidee(idDmc);

        ValeursPpmService.EnTete enTete = valeursPpm.enTete(dmc.getIdDetail());
        Dossier dossier = saisieService.creerDossierDao(enTete.idLocalite(), enTete.idEntiteContract(), idDmc);
        journal.tracer(dossier, JournalDossierService.DOSSIER_CREE_DEPUIS_FICHE, detail(fiche, idDmc));
        return dossierService.findById(dossier.getIdDossier());
    }

    /**
     * B3 — rattache une fiche validée à un dossier {@code DAO} existant. 404 dossier ou fiche → 403 (dossier d'autrui,
     * plan hors périmètre) → vacance → 409 {@code DOSSIER_NON_BROUILLON}, {@code DOSSIER_NON_DAO}, {@code DMC_NON_DAO},
     * {@code DOSSIER_DEJA_LIE}, {@code FICHE_DEJA_LIEE} (avec l'{@code idDossier} qui la porte),
     * {@code FICHE_NON_VALIDEE}. Rattacher la fiche déjà liée à ce dossier ne change rien (200).
     */
    public DossierDto rattacher(Integer idDossier, Long idDmc) {
        Dossier dossier = chargerDossier(idDossier);
        DossierMec dmc = chargerDmc(idDmc);
        dossierIntegrite.exigerProprietaire(dossier);
        perimetre.controler(dossierDuPlan(dmc));
        dossierIntegrite.exigerMandatActif();
        exigerBrouillonDao(dossier);
        exigerDao(dmc);
        if (dossier.getIdDmc() != null) {
            if (Objects.equals(dossier.getIdDmc(), idDmc)) {
                return dossierService.findById(idDossier);
            }
            throw new BusinessRuleException("Le dossier " + idDossier + " porte déjà une fiche marché (DMC "
                    + dossier.getIdDmc() + ") : détachez-la d'abord.", "DOSSIER_DEJA_LIE");
        }
        dossierRepository.findIdDossierByIdDmc(idDmc).ifPresent(id -> {
            throw new BusinessRuleException("Cette fiche marché est déjà liée au dossier " + id + ".",
                    "FICHE_DEJA_LIEE", id);
        });
        FicheMarche fiche = exigerValidee(idDmc);
        dossier.setIdDmc(idDmc);
        dossierRepository.saveAndFlush(dossier);
        journal.tracer(dossier, JournalDossierService.FICHE_MARCHE_RATTACHEE, detail(fiche, idDmc));
        return dossierService.findById(idDossier);
    }

    /**
     * B3 — défait le rattachement tant que le dossier est brouillon (erreur de manipulation). Dossier sans fiche :
     * rien à faire (200, pas de journal). 404 → 403 → vacance → 409 {@code DOSSIER_NON_BROUILLON}.
     */
    public DossierDto detacher(Integer idDossier) {
        Dossier dossier = chargerDossier(idDossier);
        dossierIntegrite.exigerProprietaire(dossier);
        dossierIntegrite.exigerMandatActif();
        exigerBrouillon(dossier);
        Long idDmc = dossier.getIdDmc();
        if (idDmc == null) {
            return dossierService.findById(idDossier);
        }
        dossier.setIdDmc(null);
        dossierRepository.saveAndFlush(dossier);
        journal.tracer(dossier, JournalDossierService.FICHE_MARCHE_DETACHEE, "Fiche marché du DMC " + idDmc
                + " (ligne " + dmcRepository.findById(idDmc).map(DossierMec::getIdDetail).orElse(null) + ") détachée");
        return dossierService.findById(idDossier);
    }

    /**
     * B3 — les fiches rattachables du périmètre. Avec {@code idDossier} : liste vide si ce dossier n'existe pas ou
     * n'est pas visible de l'appelant (200, jamais 403 : la liste ne sert qu'à proposer).
     */
    @Transactional(readOnly = true)
    public List<FicheRattachableDto> rattachables(Integer idDossier) {
        if (idDossier != null && (!dossierRepository.existsById(idDossier) || !perimetre.estVisible(idDossier))) {
            return List.of();
        }
        return ficheMarcheService.rattachables();
    }

    // ------------------------------------------------------------------ gardes

    private DossierMec chargerDmc(Long idDmc) {
        return dmcRepository.findById(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche marché introuvable : DMC " + idDmc + "."));
    }

    private Dossier chargerDossier(Integer idDossier) {
        return dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
    }

    /** Le dossier de planification de la ligne du DMC : c'est son périmètre qui ouvre la fiche. */
    private Integer dossierDuPlan(DossierMec dmc) {
        return marcheRepository.findIdDossierByIdDetail(dmc.getIdDetail()).orElse(null);
    }

    private void exigerDao(DossierMec dmc) {
        TypeDmc type = typeDmcRepository.findById(dmc.getIdTypeDmc()).orElse(null);
        if (type == null || !DmcService.TYPE_DAO.equalsIgnoreCase(type.getCode())) {
            throw new BusinessRuleException("Le DMC " + dmc.getIdDmc() + " n'est pas un dossier d'appel d'offres ("
                    + (type == null ? "type inconnu" : type.getCode()) + ") : pas de fiche marché.", "DMC_NON_DAO");
        }
    }

    /** La dernière version de la fiche doit être validée : un dossier naît d'un contenu figé. */
    private FicheMarche exigerValidee(Long idDmc) {
        FicheMarche fiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null);
        if (fiche == null || !StatutFicheMarche.VALIDEE.name().equals(fiche.getStatut())) {
            throw new BusinessRuleException(fiche == null
                    ? "La fiche marché n'a jamais été enregistrée : validez-la avant de produire le dossier."
                    : "La version " + fiche.getNumeroVersion() + " de la fiche marché est en brouillon : validez-la d'abord.",
                    "FICHE_NON_VALIDEE");
        }
        return fiche;
    }

    private static void exigerBrouillon(Dossier dossier) {
        if (!StatutDossier.BROUILLON.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException("Le dossier " + dossier.getIdDossier() + " n'est plus un brouillon (statut « "
                    + dossier.getStatut() + " ») : sa fiche marché ne se change plus.", "DOSSIER_NON_BROUILLON");
        }
    }

    private static void exigerBrouillonDao(Dossier dossier) {
        exigerBrouillon(dossier);
        if (!DmcService.TYPE_DAO.equals(dossier.getIdSousType())) {
            throw new BusinessRuleException("Le dossier " + dossier.getIdDossier() + " n'est pas un dossier d'appel "
                    + "d'offres (sous-type « " + dossier.getIdSousType() + " ») : pas de fiche marché.", "DOSSIER_NON_DAO");
        }
    }

    /** « Fiche marché version n, N information(s) » — N = champs de saisie renseignés, comme à la validation. */
    private String detail(FicheMarche fiche, Long idDmc) {
        FicheMarcheResumeDto resume = ficheMarcheService.resume(idDmc);
        return "Fiche marché version " + fiche.getNumeroVersion() + ", " + resume.nbSaisis() + " information(s)";
    }
}
