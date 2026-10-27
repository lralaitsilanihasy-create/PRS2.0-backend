package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DossierDto;
import cnm.prs.dto.LigneEligibleDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.PvExamen;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.config.HorlogeRecette;

/**
 * ⚠️ 2026-10-27 (demande front « dossier-marche-remise-papier », P1-P2 ; demande du pilote) — le dossier de marché d'une procédure à
 * <strong>remise papier</strong>, rattaché à sa procédure. La séance et l'évaluation ont eu lieu hors de l'application : le dossier
 * naît d'un lot de la fiche (une ligne {@code t_attribution} marquée {@code REMISE_PAPIER}, sans offre proposée), ce qui le relie à sa
 * procédure comme un dossier né de l'attribution ({@link PiecesFournies#procedureDe}). L'application fournit ce qu'elle a (fiche de
 * présentation, PV de la Commission sur le DAO, devis estimatif de la fiche, publication de l'AGPM) ; la PRMP joint les pièces papier.
 */
@Service
@Transactional
public class MarchePapierService {

    /** Les raisons pour lesquelles le dossier de marché d'une procédure papier ne se crée pas encore (P1, {@code marchePapier.motif}). */
    public static final String FICHE_NON_VALIDEE = "FICHE_NON_VALIDEE";
    public static final String DOSSIER_NON_CLOTURE = "DOSSIER_NON_CLOTURE";
    public static final String AVIS_NON_FAVORABLE = "AVIS_NON_FAVORABLE";
    public static final String AVIS_NON_PUBLIE = "AVIS_NON_PUBLIE";
    /** 409 : une procédure électronique a son dossier de marché par l'attribution. */
    public static final String PROCEDURE_ELECTRONIQUE = "PROCEDURE_ELECTRONIQUE";

    private final FicheMarcheService fiches;
    private final DossierMecRepository dmcs;
    private final DossierRepository dossiers;
    private final PvExamenRepository pvs;
    private final MarcheRepository marches;
    private final LotRepository lots;
    private final DocumentFicheMarcheRepository documents;
    private final AttributionRepository attributions;
    private final AttributionService attribution;
    private final SansSuiteService sansSuite;
    private final SaisieService saisie;
    private final ValeursPpmService valeursPpm;
    private final PublicationAgpmService publicationAgpm;
    private final JournalDossierService journal;
    private final DossierService dossierService;

    public MarchePapierService(FicheMarcheService fiches, DossierMecRepository dmcs, DossierRepository dossiers, PvExamenRepository pvs,
            MarcheRepository marches, LotRepository lots, DocumentFicheMarcheRepository documents, AttributionRepository attributions,
            AttributionService attribution, SansSuiteService sansSuite, SaisieService saisie, ValeursPpmService valeursPpm,
            PublicationAgpmService publicationAgpm, JournalDossierService journal, DossierService dossierService) {
        this.fiches = fiches;
        this.dmcs = dmcs;
        this.dossiers = dossiers;
        this.pvs = pvs;
        this.marches = marches;
        this.lots = lots;
        this.documents = documents;
        this.attributions = attributions;
        this.attribution = attribution;
        this.sansSuite = sansSuite;
        this.saisie = saisie;
        this.valeursPpm = valeursPpm;
        this.publicationAgpm = publicationAgpm;
        this.journal = journal;
        this.dossierService = dossierService;
    }

    /**
     * P1 — ce que la liste des procédures sert d'une fiche : son mode de remise (le mode figé par la dernière version validée, nul sans
     * version validée) et, pour une procédure papier, si son dossier de marché peut naître ({@code motif} nul) ou pourquoi pas encore.
     */
    @Transactional(readOnly = true)
    public Etat etat(Long idDmc) {
        String mode = fiches.modeFige(idDmc).orElse(null);
        if (!RemiseElectronique.PAPIER.equals(mode)) {
            return new Etat(mode, null);
        }
        String motif = motif(idDmc);
        return new Etat(mode, new LigneEligibleDto.MarchePapier(motif == null, motif));
    }

    /** Le mode de remise et, en papier seulement, l'ouverture du dossier de marché. */
    public record Etat(String modeRemise, LigneEligibleDto.MarchePapier marchePapier) {
    }

    /** Les lots de la procédure : ceux de la ligne du plan, numérotés dans leur ordre ; un lot unique (1) pour une ligne sans lot. */
    @Transactional(readOnly = true)
    public List<LigneEligibleDto.LotProcedure> lots(Integer idDetail) {
        List<Lot> duPlan = idDetail == null ? List.of()
                : lots.findByIdDetail(idDetail).stream().sorted(Comparator.comparing(Lot::getIdLot)).toList();
        if (duPlan.size() <= 1) {
            return List.of(new LigneEligibleDto.LotProcedure(1, duPlan.isEmpty() ? null : ProjetMarcheService.intituleLot(duPlan, 1)));
        }
        return IntStream.rangeClosed(1, duPlan.size()).mapToObj(n -> new LigneEligibleDto.LotProcedure(n, ProjetMarcheService.intituleLot(duPlan, n)))
                .toList();
    }

    /**
     * P2 — crée le dossier de marché d'un lot d'une procédure papier (PRMP de la fiche, ou son UGPM), rattaché à sa procédure, en
     * brouillon : le sous-type par la règle de l'attribution ({@code DAOO-CC} → {@code MAOO-CC}) ; joints d'office le devis estimatif
     * de la fiche et la publication de l'AGPM du plan. 404 (procédure, lot) ; 409 {@code DOSSIER_EXISTANT} (avec {@code idDossier}),
     * {@code PROCEDURE_ELECTRONIQUE}, {@code FICHE_NON_VALIDEE}, {@code DOSSIER_NON_CLOTURE}, {@code AVIS_NON_FAVORABLE},
     * {@code AVIS_NON_PUBLIE}, {@code SANS_SUITE_DECLAREE}.
     */
    public DossierDto creerDossier(Long idDmc, Integer lot) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("Le dossier de marché se crée par la PRMP de la fiche (ou son UGPM).");
        }
        fiches.controlerLecture(idDmc);
        DossierMec dmc = dmcs.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc));
        if (lots(dmc.getIdDetail()).stream().noneMatch(l -> l.lot().equals(lot))) {
            throw new ResourceNotFoundException("Lot introuvable dans la procédure : " + lot + ".");
        }
        Attribution existante = attributions.findById(new Attribution.Cle(idDmc, lot)).orElse(null);
        if (existante != null && existante.getIdDossier() != null) {
            throw new BusinessRuleException("Le dossier de marché de ce lot existe déjà : " + existante.getIdDossier() + ".", "DOSSIER_EXISTANT",
                    existante.getIdDossier());
        }
        String mode = fiches.modeFige(idDmc).orElse(null);
        if (mode != null && !RemiseElectronique.PAPIER.equals(mode)) {
            throw new BusinessRuleException("La procédure est à remise électronique : son dossier de marché naît de l'attribution du lot.",
                    PROCEDURE_ELECTRONIQUE);
        }
        String motif = motif(idDmc);
        if (motif != null) {
            throw new BusinessRuleException(message(motif), motif);
        }
        sansSuite.exigerAucuneDeclaration(idDmc);
        Dossier mec = dossiers.findIdDossierByIdDmc(idDmc).flatMap(dossiers::findById).orElseThrow();
        Map<String, String> plan = valeursPpm.lire(dmc.getIdDetail()).valeurs();
        boolean pi = fiches.etatValide(idDmc).map(v -> ModelesDao.sigleLettre(v.categorie()) != null).orElse(false);
        String sousType = SousTypesDossier.dossierMarche(plan.get("MODE"), pi, mec.getIdSousType());
        ValeursPpmService.EnTete enTete = valeursPpm.enTete(dmc.getIdDetail());
        Dossier dossier = saisie.creerDossierMarche(sousType, enTete.idLocalite(), enTete.idEntiteContract());
        Attribution a = existante != null ? existante : new Attribution();
        a.setIdDmc(idDmc);
        a.setLot(lot);
        a.setEtat(Attribution.AU_CONTROLE);
        a.setIdDossier(dossier.getIdDossier());
        a.setDossierCreeLe(LocalDateTime.now(HorlogeRecette.HORLOGE).withNano(0));
        a.setDossierCreePar(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        a.setRemisePapier(true);
        attributions.save(a);
        int jointes = attribution.joindreEstimatif(dossier, idDmc) + (publicationAgpm.joindreSiAbsente(dossier) ? 1 : 0);
        String detail = "Lot " + lot + " de la procédure à remise papier " + idDmc + " (dossier " + mec.getRefeDossier() + ") : dossier de "
                + "marché " + dossier.getIdDossier() + " (" + sousType + ") créé, " + jointes + " pièce(s) jointe(s) d'office";
        journal.tracer(dossier.getIdDossier(), JOURNAL, detail);
        journal.tracer(mec.getIdDossier(), JOURNAL, detail);
        return dossierService.findById(dossier.getIdDossier());
    }

    /** L'action de journal de la création (dossier de marché et dossier de mise en concurrence). */
    static final String JOURNAL = "DOSSIER_MARCHE_PAPIER";

    /**
     * La première raison qui retient le dossier de marché d'une procédure papier, nulle s'il peut naître : fiche validée ; dossier de
     * mise en concurrence clôturé ; PV signé de la Commission favorable, ou favorable avec réserves (levées, le dossier étant clôturé) ;
     * avis spécifique publié (imprimé : c'est l'impression qui lance la ligne).
     */
    private String motif(Long idDmc) {
        if (fiches.etatValide(idDmc).isEmpty()) {
            return FICHE_NON_VALIDEE;
        }
        Dossier mec = dossiers.findIdDossierByIdDmc(idDmc).flatMap(dossiers::findById).orElse(null);
        if (mec == null || !StatutDossier.CLOTURE.name().equals(mec.getStatut())) {
            return DOSSIER_NON_CLOTURE;
        }
        String avis = pvs.findSignesParDossierRows(mec.getIdDossier()).stream().findFirst().map(PvExamen::getIdAvis).orElse(null);
        if (!"FAV".equals(avis) && !"FAVR".equals(avis)) {
            return AVIS_NON_FAVORABLE;
        }
        Marche ligne = dmcs.findById(idDmc).map(DossierMec::getIdDetail).flatMap(marches::findById).orElse(null);
        Integer origine = ligne == null ? null : ligne.getIdLigneOrigine() != null ? ligne.getIdLigneOrigine() : ligne.getIdDetail();
        if (origine == null || documents.premierAvisDeLaFiliation(origine) == null) {
            return AVIS_NON_PUBLIE;
        }
        return null;
    }

    private static String message(String motif) {
        return switch (motif) {
            case FICHE_NON_VALIDEE -> "La fiche de la procédure n'est pas validée : pas de dossier de marché.";
            case DOSSIER_NON_CLOTURE -> "Le dossier de mise en concurrence n'est pas clôturé : le dossier de marché attend sa clôture.";
            case AVIS_NON_FAVORABLE -> "Le PV de la Commission sur le dossier de mise en concurrence n'est pas favorable : pas de dossier de marché.";
            default -> "L'avis spécifique n'est pas publié : le dossier de marché attend sa publication.";
        };
    }
}
