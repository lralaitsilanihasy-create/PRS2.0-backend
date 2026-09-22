package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DmcDto;
import cnm.prs.dto.LigneEligibleDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.StatutDmc;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DmcMapper;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.TypeDmcRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PerimetreDossier;
import cnm.prs.security.Visibilite;

/**
 * Dossier de mise en concurrence (DMC) : création <strong>par ligne de marché</strong> avec type
 * <strong>dérivé du mode de passation</strong> (mapping en base, pas d'enum codé en dur). Service
 * dédié, non câblé automatiquement sur la saisie/soumission (déclenchement explicite).
 *
 * <p>⚠️ Fiche marché DAO (demande front du 2026-09-22, §B2) — la création s'ouvre à la <strong>PRMP et à son
 * UGPM</strong>, pour leurs propres lignes, sous les gardes H4 (voir {@link #motifInegibilite}) ; l'Administrateur
 * garde son geste d'origine (sans H4). {@code GET /eligibles} liste les lignes qui passent ces gardes.</p>
 */
@Service
@Transactional
public class DmcService {

    /** Code du type de DMC d'un appel d'offres (H4 : « mode mappé au type DAO »). */
    public static final String TYPE_DAO = "DAO";

    private final DossierMecRepository repository;
    private final MarcheRepository marcheRepository;
    private final ModePassationRepository modeRepository;
    private final TypeDmcRepository typeDmcRepository;
    private final PerimetreDossier perimetre;
    private final DossierRepository dossierRepository;
    private final DossierIntegriteService dossierIntegrite;
    private final ValeursPpmService valeursPpm;
    private final ChampFicheMarcheRepository champRepository;

    public DmcService(DossierMecRepository repository, MarcheRepository marcheRepository,
            ModePassationRepository modeRepository, TypeDmcRepository typeDmcRepository,
            PerimetreDossier perimetre, DossierRepository dossierRepository,
            DossierIntegriteService dossierIntegrite, ValeursPpmService valeursPpm,
            ChampFicheMarcheRepository champRepository) {
        this.repository = repository;
        this.marcheRepository = marcheRepository;
        this.modeRepository = modeRepository;
        this.typeDmcRepository = typeDmcRepository;
        this.perimetre = perimetre;
        this.dossierRepository = dossierRepository;
        this.dossierIntegrite = dossierIntegrite;
        this.valeursPpm = valeursPpm;
        this.champRepository = champRepository;
    }

    /**
     * Crée le DMC d'une ligne de marché, son type dérivé du mode de passation.
     *
     * <p>Administrateur : <strong>400</strong> si le mode n'est pas mappé à un type actif (message de configuration),
     * <strong>409</strong> {@code DAO_EXISTANT} si la ligne a déjà un DMC (relation 1-1). PRMP / UGPM (2026-09-22) :
     * dans l'ordre, <strong>403</strong> hors de ses dossiers (avant toute autre garde : l'existence d'un dossier
     * d'autrui ne se devine pas), 409 {@code VACANCE_PRMP} sans mandat actif, puis les 409 nominatifs de H4
     * ({@code LIGNE_RETIREE}, {@code MODE_NON_DAO}, {@code PV_NON_SIGNE}, {@code DAO_EXISTANT}). La réponse porte
     * les informations reprises du PPM ({@code valeursPpm}, {@code versionPpm}).</p>
     */
    public DmcDto creerPourMarche(Integer idDetail) {
        Marche marche = marcheRepository.findById(idDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Marché introuvable : " + idDetail));
        TypeDmc type;
        if (Visibilite.estPrmp()) {
            String ref = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            if (ref == null || marche.getIdDossier() == null
                    || !dossierRepository.existsVisiblePourPrmp(marche.getIdDossier(), ref)) {
                throw new AccessDeniedException("Cette ligne de marché n'est pas dans un de vos plans.");
            }
            dossierIntegrite.exigerMandatActif();
            Dossier dossier = dossierRepository.findById(marche.getIdDossier()).orElse(null);
            motifInegibilite(marche, dossier, new Caches(null)).ifPresent(m -> {
                throw new BusinessRuleException(m.message(), m.code());
            });
            type = typeDmcRepository.findByCode(TYPE_DAO).orElseThrow();
        } else {
            if (repository.existsByIdDetail(idDetail)) {
                throw new BusinessRuleException("La ligne de marché " + idDetail + " a déjà un DMC.", "DAO_EXISTANT");
            }
            type = resoudreType(marche);
        }

        DossierMec dmc = new DossierMec();
        dmc.setIdDetail(idDetail);
        dmc.setIdTypeDmc(type.getIdTypeDmc());
        dmc.setStatut(StatutDmc.A_PREPARER);
        dmc.setDateCreation(LocalDateTime.now());
        DossierMec saved = repository.save(dmc);
        saved.setTypeDmc(type);   // pour l'affichage code/libellé (association lecture seule)
        DmcDto dto = DmcMapper.toDto(saved);
        ValeursPpmService.ValeursPpm ppm = valeursPpm.lire(idDetail);
        // Clé = code du champ de source PPM (comme sur la fiche), valeur telle qu'affichée.
        Map<String, String> parCode = new java.util.TreeMap<>();
        champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().stream()
                .filter(c -> "PPM".equals(c.getSource()) && c.getClePpm() != null)
                .forEach(c -> parCode.put(c.getCode(), ppm.valeurs().get(c.getClePpm())));
        dto.setValeursPpm(parCode);
        dto.setVersionPpm(ppm.versionPpm());
        return dto;
    }

    /**
     * ⚠️ 2026-09-22 (§B2, H4) — les lignes de PPM éligibles à un appel d'offres pour l'utilisateur courant : ses
     * plans (PRMP / UGPM), tout (Président / Administrateur), rien pour les autres profils. Une ligne est éligible
     * si {@link #motifInegibilite} ne lui trouve rien — sauf {@code DAO_EXISTANT}, qui la garde dans la liste avec
     * {@code dejaDao = true} et l'{@code idDmc} à rouvrir.
     */
    @Transactional(readOnly = true)
    public List<LigneEligibleDto> eligibles() {
        List<Marche> lignes;
        if (Visibilite.voitTout()) {
            lignes = marcheRepository.findAll();
        } else if (Visibilite.estPrmp()) {
            lignes = CurrentUser.ref().filter(s -> !s.isBlank()).map(marcheRepository::findVisiblesPourPrmp)
                    .orElseGet(List::of);
        } else {
            return List.of();
        }
        // Une seule requête pour « dejaDao » sur la filiation (pas une par ligne — leçon du référentiel des délais).
        Map<Integer, DossierMec> dmcParOrigine = new HashMap<>();
        for (Object[] row : repository.findAvecOrigine()) {
            dmcParOrigine.putIfAbsent((Integer) row[0], (DossierMec) row[1]);
        }
        Caches caches = new Caches(dmcParOrigine);
        Map<Integer, Dossier> dossiers = new HashMap<>();
        List<LigneEligibleDto> out = new ArrayList<>();
        for (Marche m : lignes) {
            Dossier d = m.getIdDossier() == null ? null
                    : dossiers.computeIfAbsent(m.getIdDossier(), id -> dossierRepository.findById(id).orElse(null));
            Optional<Motif> motif = motifInegibilite(m, d, caches);
            if (motif.isPresent() && !"DAO_EXISTANT".equals(motif.get().code())) {
                continue;
            }
            ModePassation mode = caches.mode(m.getIdMode());
            DossierMec dmc = dmcParOrigine.get(m.getIdLigneOrigine());
            out.add(new LigneEligibleDto(m.getIdDetail(), m.getIdDossier(), d == null ? null : d.getRefeDossier(),
                    m.getDesignationMarche(), m.getIdMode(), mode == null ? null : mode.getLibelle(), m.getMontEstim(),
                    dmc != null, dmc == null ? null : dmc.getIdDmc()));
        }
        return out;
    }

    /** Un motif d'inéligibilité : code stable et message. */
    public record Motif(String code, String message) {
    }

    /**
     * H4, dans l'ordre : ligne retirée ({@code LIGNE_RETIREE}) ; mode non mappé au type {@code DAO} actif
     * ({@code MODE_NON_DAO}) ; plan sans PV signé favorable — avis {@code FAV} au PV signé, ou {@code FAVR} une fois les
     * réserves levées ({@code PV_NON_SIGNE}) ; DMC déjà créé ({@code DAO_EXISTANT}). Le PV pris en compte est le
     * plus récent des PV signés du dossier.
     *
     * @param caches modes, types, PV par dossier et DMC par filiation partagés par une liste d'éligibles
     */
    private Optional<Motif> motifInegibilite(Marche marche, Dossier dossier, Caches caches) {
        if (Boolean.TRUE.equals(marche.getSupprimee())) {
            return Optional.of(new Motif("LIGNE_RETIREE", "La ligne " + marche.getIdDetail() + " a été retirée du plan."));
        }
        if (dossier != null && StatutDossier.REMPLACE.name().equals(dossier.getStatut())) {
            return Optional.of(new Motif("VERSION_DEPASSEE", "Le plan " + dossier.getRefeDossier() + " a été remplacé "
                    + "par une version plus récente : préparer l'appel d'offres depuis la version courante."));
        }
        ModePassation mode = caches.mode(marche.getIdMode());
        TypeDmc type = mode == null || mode.getIdTypeDmc() == null ? null : caches.type(mode.getIdTypeDmc());
        String libelleMode = mode == null ? String.valueOf(marche.getIdMode()) : mode.getLibelle();
        if (type == null) {
            return Optional.of(new Motif("MODE_NON_DAO", "Le mode « " + libelleMode + " » n'est rattaché à aucun type "
                    + "de dossier de mise en concurrence — à faire par l'Administrateur (Types de DMC)."));
        }
        if (!type.isActif() || !TYPE_DAO.equalsIgnoreCase(type.getCode())) {
            return Optional.of(new Motif("MODE_NON_DAO", "Le mode « " + libelleMode + " » n'est pas un appel d'offres "
                    + "(type de DMC « " + type.getCode() + (type.isActif() ? "" : ", inactif") + " »)."));
        }
        if (dossier == null || !caches.pv.computeIfAbsent(dossier.getIdDossier(), id -> valeursPpm.pvSigneFavorable(dossier))) {
            return Optional.of(new Motif("PV_NON_SIGNE", "Le plan " + (dossier == null ? "" : dossier.getRefeDossier() + " ")
                    + "n'a pas de PV signé favorable (ou ses réserves ne sont pas levées)."));
        }
        if (caches.dejaDao(marche.getIdLigneOrigine())) {
            return Optional.of(new Motif("DAO_EXISTANT", "La ligne de marché " + marche.getIdDetail()
                    + " a déjà un DMC (sur cette version du plan ou une précédente)."));
        }
        return Optional.empty();
    }

    /** Caches d'une évaluation : référentiels lus une fois, DMC par filiation (précalculés pour une liste). */
    private final class Caches {
        private final Map<Integer, ModePassation> modes = new HashMap<>();
        private final Map<Long, TypeDmc> types = new HashMap<>();
        private final Map<Integer, Boolean> pv = new HashMap<>();
        private final Map<Integer, DossierMec> dmcParOrigine;

        Caches(Map<Integer, DossierMec> dmcParOrigine) {
            this.dmcParOrigine = dmcParOrigine;
        }

        ModePassation mode(Integer idMode) {
            return idMode == null ? null : modes.computeIfAbsent(idMode, id -> modeRepository.findById(id).orElse(null));
        }

        TypeDmc type(Long idType) {
            return types.computeIfAbsent(idType, id -> typeDmcRepository.findById(id).orElse(null));
        }

        boolean dejaDao(Integer origine) {
            return dmcParOrigine != null ? dmcParOrigine.containsKey(origine)
                    : !repository.findParFiliation(origine).isEmpty();
        }
    }


    @Transactional(readOnly = true)
    public DmcDto findByMarche(Integer idDetail) {
        DossierMec dmc = repository.findByIdDetail(idDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun DMC pour la ligne de marché : " + idDetail));
        controlerVisibilite(dmc);
        return avecDossierSoumis(DmcMapper.toDto(dmc));
    }

    @Transactional(readOnly = true)
    public DmcDto findById(Long idDmc) {
        DossierMec dmc = repository.findById(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc));
        controlerVisibilite(dmc);
        return avecDossierSoumis(DmcMapper.toDto(dmc));
    }

    /** ⚠️ Lot 1b (2026-09-23, §B1) — le dossier soumis que porte ce DMC ({@code t_dossier.ID_DMC}), nul sinon. */
    private DmcDto avecDossierSoumis(DmcDto dto) {
        dto.setIdDossierSoumis(dossierRepository.findIdDossierByIdDmc(dto.getIdDmc()).orElse(null));
        return dto;
    }

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.1 : le DMC est rattaché à une ligne de marché, donc à un dossier ;
     * sa lecture suit le périmètre de ce dossier (Président/Admin : tout ; contrôleurs : leur localité ;
     * PRMP : ses dossiers). La <strong>création</strong> est réservée à l'Administrateur, à la PRMP et à son UGPM
     * ({@code @PreAuthorize} sur {@code DmcController}).
     */
    private void controlerVisibilite(DossierMec dmc) {
        perimetre.controler(marcheRepository.findIdDossierByIdDetail(dmc.getIdDetail()).orElse(null));
    }

    /**
     * Re-dérive le type du DMC d'un marché <strong>si</strong> il existe et est encore {@code A_PREPARER}
     * (appelé au changement de mode de passation). Si le nouveau mode n'est pas mappé, le DMC est laissé
     * inchangé (on ne bloque pas la modification du marché).
     */
    public void reAffecterTypeSiApreparer(Integer idDetail) {
        repository.findByIdDetail(idDetail).ifPresent(dmc -> {
            if (dmc.getStatut() != StatutDmc.A_PREPARER) {
                return;
            }
            Marche marche = marcheRepository.findById(idDetail).orElse(null);
            if (marche == null || marche.getIdMode() == null) {
                return;
            }
            ModePassation mode = modeRepository.findById(marche.getIdMode()).orElse(null);
            TypeDmc type = typeDuMode(mode);
            if (type != null && type.isActif()) {
                dmc.setIdTypeDmc(type.getIdTypeDmc());
                dmc.setTypeDmc(type);   // garde l'association en phase avec la colonne (évite une lecture stale)
                repository.save(dmc);
            }
        });
    }

    /** Supprime le DMC d'un marché (cascade applicative à la suppression du marché). */
    public void supprimerPourMarche(Integer idDetail) {
        repository.deleteByIdDetail(idDetail);
    }

    private TypeDmc resoudreType(Marche marche) {
        ModePassation mode = marche.getIdMode() == null ? null
                : modeRepository.findById(marche.getIdMode()).orElse(null);
        TypeDmc type = typeDuMode(mode);
        if (type == null || !type.isActif()) {
            String mode0 = mode != null ? mode.getLibelle() : String.valueOf(marche.getIdMode());
            throw new BadRequestException("Aucun type de DMC actif n'est mappé au mode de passation « " + mode0
                    + " ». Configurez le mapping en administration (mode de passation → type de DMC).");
        }
        return type;
    }

    /**
     * Type de DMC mappé à un mode de passation, résolu via la <strong>colonne</strong> {@code ID_TYPE_DMC}
     * (et non l'association read-only, non renseignée quand seule la colonne est modifiée). {@code null} si
     * mode absent ou non mappé.
     */
    private TypeDmc typeDuMode(ModePassation mode) {
        if (mode == null || mode.getIdTypeDmc() == null) {
            return null;
        }
        return typeDmcRepository.findById(mode.getIdTypeDmc()).orElse(null);
    }
}
