package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ObservationPvDto;
import cnm.prs.dto.PassageObservationsRequest;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.ExamenPiece;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ObservationControle;
import cnm.prs.entity.ObservationPv;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.Reception;
import cnm.prs.entity.SuiviObservation;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.entity.Verification;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ExamenPieceRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ObservationControleRepository;
import cnm.prs.repository.ObservationPvRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.SuiviObservationRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — PÉRIMÈTRE FIGÉ.
 *
 * <p>Les observations transmises à la PRMP sont EXCLUSIVEMENT celles issues de l'examen, telles
 * qu'arrêtées dans le PV : le périmètre est figé à l'émission (signature) du PV FAVR et ne peut être
 * élargi à aucun stade du circuit de rectification, par aucun acteur (rejet backend, pas seulement
 * masquage UI). Le vérificateur ne rédige rien : à chaque itération il statue chaque observation
 * restante — LEVÉE (satisfaite, définitive : « levée = acquise », décision user 02/08) ou MAINTENUE
 * (rappel, précision facultative sur ce qui manque). Les maintenues constituent seules le rappel
 * adressé à la PRMP. Historisation par itération (auteur + horodatage).</p>
 */
@Service
@Transactional
public class ObservationPvService {

    private static final String LEVEE = "LEVEE";
    private static final String MAINTENUE = "MAINTENUE";

    private final ObservationPvRepository repository;
    private final SuiviObservationRepository suiviRepository;
    private final DossierRepository dossierRepository;
    private final ExamenDetailRepository examenDetailRepository;
    private final ObservationControleRepository observationControleRepository;
    private final ExamenPieceRepository examenPieceRepository;
    private final MarcheRepository marcheRepository;
    private final PointsCtrlRepository pointsCtrlRepository;
    private final PieceJointeDossierRepository pieceJointeDossierRepository;
    private final TypePieceJointeRepository typePieceJointeRepository;
    private final PvExamenRepository pvExamenRepository;
    private final ReceptionRepository receptionRepository;
    private final VerificationService verificationService;
    private final DossierIntegriteService dossierIntegrite;

    public ObservationPvService(ObservationPvRepository repository, SuiviObservationRepository suiviRepository,
            DossierRepository dossierRepository, ExamenDetailRepository examenDetailRepository,
            ObservationControleRepository observationControleRepository, ExamenPieceRepository examenPieceRepository,
            MarcheRepository marcheRepository, PointsCtrlRepository pointsCtrlRepository,
            PieceJointeDossierRepository pieceJointeDossierRepository,
            TypePieceJointeRepository typePieceJointeRepository, PvExamenRepository pvExamenRepository,
            ReceptionRepository receptionRepository, VerificationService verificationService,
            DossierIntegriteService dossierIntegrite) {
        this.repository = repository;
        this.suiviRepository = suiviRepository;
        this.dossierRepository = dossierRepository;
        this.examenDetailRepository = examenDetailRepository;
        this.observationControleRepository = observationControleRepository;
        this.examenPieceRepository = examenPieceRepository;
        this.marcheRepository = marcheRepository;
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.pieceJointeDossierRepository = pieceJointeDossierRepository;
        this.typePieceJointeRepository = typePieceJointeRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.receptionRepository = receptionRepository;
        this.verificationService = verificationService;
        this.dossierIntegrite = dossierIntegrite;
    }

    // ------------------------------------------------------------------
    // Génération du périmètre (signature du PV FAVR) — idempotente
    // ------------------------------------------------------------------

    /**
     * Fige le périmètre des observations du PV : une observation trackée par LIGNE « Au lieu de /
     * Lire » de chaque point non conforme (repli : texte libre du point) + une par PIÈCE non conforme.
     * Idempotent ({@code existsByIdPv}) ; appelé à la signature du PV FAVR, et en RATTRAPAGE paresseux
     * pour les dossiers FAVR signés avant cette règle.
     */
    public void genererPourPv(PvExamen pv) {
        if (pv == null || repository.existsByIdPv(pv.getIdPv())) {
            return;
        }
        Integer idDossier = pvExamenRepository.findIdDossierByPv(pv.getIdPv()).orElse(null);
        if (idDossier == null) {
            return;
        }
        List<ObservationPv> rows = new ArrayList<>();
        int ordre = 0;
        for (ExamenDetail d : examenDetailRepository.findByIdExamen(pv.getIdExamen())) {
            if (!Boolean.FALSE.equals(d.getConforme())) {
                continue;
            }
            String point = pointsCtrlRepository.findById(d.getIdPtControle())
                    .map(PointsCtrl::getLibelPointCtrl).orElse("Point " + d.getIdPtControle());
            String ligne = d.getIdDetail() == null ? null
                    : marcheRepository.findById(d.getIdDetail()).map(Marche::getDesignationMarche).orElse(null);
            String contexte = (ligne != null ? "Ligne « " + ligne + " » — " : "") + point;
            List<ObservationControle> lignesObs =
                    observationControleRepository.findByIdDetailOrderByOrdreAsc(d.getIdDetailExamen());
            if (lignesObs.isEmpty()) {
                String texte = d.getObsSiNonConforme() == null || d.getObsSiNonConforme().isBlank()
                        ? contexte + " : non conforme"
                        : contexte + " : " + d.getObsSiNonConforme();
                rows.add(nouvelle(idDossier, pv.getIdPv(), "POINT", null, null, texte, ++ordre));
            } else {
                for (ObservationControle oc : lignesObs) {
                    String texte = contexte + " : au lieu de « " + nvl(oc.getAuLieuDe())
                            + " », lire « " + nvl(oc.getLire()) + " »";
                    rows.add(nouvelle(idDossier, pv.getIdPv(), "POINT", oc.getIdObservation(), null, texte, ++ordre));
                }
            }
        }
        for (ExamenPiece ep : examenPieceRepository.findByIdExamen(pv.getIdExamen())) {
            if (!Boolean.FALSE.equals(ep.getConforme())) {
                continue;
            }
            String nomPiece = pieceJointeDossierRepository.findById(ep.getIdPiece())
                    .map(p -> typePieceJointeRepository.findById(p.getIdTypePiece())
                            .map(TypePieceJointe::getLibellePiece).orElse(p.getNomFichier()))
                    .orElse("Pièce " + ep.getIdPiece());
            String texte = "Pièce « " + nomPiece + " »"
                    + (ep.getObservation() == null || ep.getObservation().isBlank() ? " : non conforme"
                            : " : " + ep.getObservation());
            rows.add(nouvelle(idDossier, pv.getIdPv(), "PIECE", null, ep.getIdExamenPiece(), texte, ++ordre));
        }
        repository.saveAll(rows);
    }

    private ObservationPv nouvelle(Integer idDossier, Integer idPv, String source, Integer idObsCtrl,
            Integer idExamenPiece, String libelle, int ordre) {
        ObservationPv o = new ObservationPv();
        o.setIdDossier(idDossier);
        o.setIdPv(idPv);
        o.setSource(source);
        o.setIdObservationCtrl(idObsCtrl);
        o.setIdExamenPiece(idExamenPiece);
        o.setLibelle(libelle.length() > 1000 ? libelle.substring(0, 1000) : libelle);
        o.setOrdre(ordre);
        return o;
    }

    // ------------------------------------------------------------------
    // Lecture (vérificateur / PRMP propriétaire)
    // ------------------------------------------------------------------

    /**
     * Observations du dossier avec statut courant + historique. Accès : périmètre localité habituel,
     * OU PRMP propriétaire (elle reçoit les rectifications demandées). Rattrapage paresseux : un
     * dossier FAVR signé AVANT cette règle voit son périmètre généré au premier accès (migration).
     */
    public List<ObservationPvDto> parDossier(Integer idDossier) {
        Dossier dossier = charger(idDossier);
        controlerAccesLecture(dossier);
        List<ObservationPv> obs = repository.findByIdDossierOrderByOrdreAscIdObservationPvAsc(idDossier);
        if (obs.isEmpty()) {
            genererPourPv(pvSigneFavr(idDossier));
            obs = repository.findByIdDossierOrderByOrdreAscIdObservationPvAsc(idDossier);
        }
        return mapper(idDossier, obs);
    }

    // ------------------------------------------------------------------
    // Passage du vérificateur — décisions individuelles sur le périmètre
    // ------------------------------------------------------------------

    /**
     * Un PASSAGE de vérification : chaque observation RESTANTE (non levée) reçoit une décision —
     * LEVÉE (définitive) ou MAINTENUE (précision facultative). Gardes backend :
     * hors périmètre → 409 (périmètre figé, aucune création) ; déjà levée → 409 (« levée = acquise ») ;
     * restante non statuée → 400. Le passage {@code t_verification} est créé automatiquement (rappel
     * auto-généré des maintenues, aucun texte client) et la transition [Auto] s'applique : toutes
     * levées → {@code OBSERVATIONS_LEVEES} ; sinon → {@code EN_ATTENTE_DECISION_PRMP} + notification.
     */
    public List<ObservationPvDto> enregistrerPassage(PassageObservationsRequest req) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.VERIFICATEUR) {
            throw new AccessDeniedException("Seul un Contrôleur vérificateur peut statuer les observations (§3.6).");
        }
        String im = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Vérificateur non identifié."));
        Dossier dossier = charger(req.idDossier());
        Visibilite.exigerLocalite(dossier.getIdLocalite());
        if (!StatutDossier.EN_VERIFICATION.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "Passage impossible : le dossier n'est pas en vérification (statut « "
                            + dossier.getStatut() + " »).");
        }
        PvExamen pv = pvSigneFavr(req.idDossier());
        if (pv == null) {
            throw new BusinessRuleException("Aucun PV signé « favorable avec réserves » pour ce dossier.");
        }
        genererPourPv(pv);                                      // rattrapage (dossier signé avant la règle)
        List<ObservationPv> perimetre =
                repository.findByIdDossierOrderByOrdreAscIdObservationPvAsc(req.idDossier());
        if (perimetre.isEmpty()) {
            throw new BusinessRuleException("Le PV ne comporte aucune observation : rien à statuer.");
        }
        Map<Integer, String> etats = etatsCourants(req.idDossier());
        Map<Integer, ObservationPv> parId = new HashMap<>();
        perimetre.forEach(o -> parId.put(o.getIdObservationPv(), o));

        Set<Integer> statuees = new HashSet<>();
        for (PassageObservationsRequest.ObservationDecision dec : req.decisions()) {
            ObservationPv cible = parId.get(dec.idObservationPv());
            if (cible == null) {
                // ⚠️ Périmètre FIGÉ : toute décision hors des observations du PV du dossier est rejetée.
                throw new BusinessRuleException("Observation " + dec.idObservationPv()
                        + " hors du périmètre du PV : le périmètre des observations est figé, aucune"
                        + " observation ne peut être ajoutée au circuit de rectification.");
            }
            if (!statuees.add(dec.idObservationPv())) {
                throw new BadRequestException("Décision en double pour l'observation " + dec.idObservationPv() + ".");
            }
            if (!LEVEE.equals(dec.decision()) && !MAINTENUE.equals(dec.decision())) {
                throw new BadRequestException("Décision invalide « " + dec.decision() + " » (LEVEE ou MAINTENUE).");
            }
            if (LEVEE.equals(etats.get(dec.idObservationPv()))) {
                throw new BusinessRuleException("L'observation " + dec.idObservationPv()
                        + " est déjà levée : une levée est définitive (acquise).");
            }
        }
        // Complétude : chaque observation restante (non levée) doit être statuée à cette itération.
        for (ObservationPv o : perimetre) {
            if (!LEVEE.equals(etats.get(o.getIdObservationPv())) && !statuees.contains(o.getIdObservationPv())) {
                throw new BadRequestException("Chaque observation restante doit être statuée (levée ou"
                        + " maintenue) — observation non statuée : « " + o.getLibelle() + " ».");
            }
        }

        int iteration = suiviRepository.findParDossier(req.idDossier()).stream()
                .mapToInt(SuiviObservation::getIteration).max().orElse(0) + 1;
        LocalDateTime maintenant = LocalDateTime.now();
        List<String> rappel = new ArrayList<>();
        for (PassageObservationsRequest.ObservationDecision dec : req.decisions()) {
            SuiviObservation s = new SuiviObservation();
            s.setIdObservationPv(dec.idObservationPv());
            s.setIteration(iteration);
            s.setDecision(dec.decision());
            s.setPrecision(MAINTENUE.equals(dec.decision()) && dec.precision() != null
                    && !dec.precision().isBlank() ? dec.precision().trim() : null);
            s.setImVerificateur(im);
            s.setDateDecision(maintenant);
            suiviRepository.save(s);
            if (MAINTENUE.equals(dec.decision())) {
                ObservationPv o = parId.get(dec.idObservationPv());
                rappel.add(o.getLibelle() + (s.getPrecision() != null ? " [" + s.getPrecision() + "]" : ""));
            }
        }

        // Passage t_verification AUTO-GÉNÉRÉ (aucun texte client) + transition [Auto] habituelle.
        boolean toutesLevees = rappel.isEmpty();
        String resume = toutesLevees
                ? "Toutes les observations du PV sont levées (itération " + iteration + ")."
                : "Rappel (itération " + iteration + ") — observations du PV restées non satisfaites : "
                        + String.join(" ; ", rappel);
        Verification v = new Verification();
        v.setIdReception(derniereReception(req.idDossier()));
        v.setIdPv(pv.getIdPv());
        v.setImCtrlVerif(im);
        v.setDateVerif(LocalDate.now());
        v.setObservation(resume.length() > 500 ? resume.substring(0, 497) + "…" : resume);
        v.setObsLevees(toutesLevees);
        verificationService.enregistrerPassageAutomatique(v);

        return mapper(req.idDossier(), repository.findByIdDossierOrderByOrdreAscIdObservationPvAsc(req.idDossier()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Dossier charger(Integer idDossier) {
        return dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
    }

    /** Lecture : tout-voyant, PRMP propriétaire, ou contrôleur de la localité du dossier. */
    private void controlerAccesLecture(Dossier dossier) {
        if (Visibilite.voitTout()) {
            return;
        }
        if (Visibilite.estPrmp()) {
            dossierIntegrite.exigerProprietaire(dossier);
            return;
        }
        Visibilite.exigerLocalite(dossier.getIdLocalite());
    }

    /** Dernier PV SIGNÉ FAVR du dossier ({@code null} si aucun). */
    private PvExamen pvSigneFavr(Integer idDossier) {
        return pvExamenRepository.findSignesParDossierRows(idDossier).stream()
                .filter(pv -> "FAVR".equals(pv.getIdAvis())).findFirst().orElse(null);
    }

    /** Statut courant par observation : dernière décision (LEVEE définitive) ; absente = EMISE. */
    private Map<Integer, String> etatsCourants(Integer idDossier) {
        Map<Integer, String> etats = new HashMap<>();
        for (SuiviObservation s : suiviRepository.findParDossier(idDossier)) {
            etats.put(s.getIdObservationPv(), s.getDecision()); // ordre chronologique → la dernière gagne
        }
        return etats;
    }

    /** Dernière réception du dossier (support du passage {@code t_verification}). */
    private Integer derniereReception(Integer idDossier) {
        return receptionRepository.findByIdDossier(idDossier).stream()
                .sorted((a, b) -> Integer.compare(nvlInt(b.getNumPassage()), nvlInt(a.getNumPassage())))
                .map(Reception::getIdReception).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Dossier sans réception : passage impossible."));
    }

    private List<ObservationPvDto> mapper(Integer idDossier, List<ObservationPv> obs) {
        Map<Integer, List<SuiviObservation>> historiques = new HashMap<>();
        for (SuiviObservation s : suiviRepository.findParDossier(idDossier)) {
            historiques.computeIfAbsent(s.getIdObservationPv(), k -> new ArrayList<>()).add(s);
        }
        List<ObservationPvDto> dtos = new ArrayList<>();
        for (ObservationPv o : obs) {
            List<SuiviObservation> h = historiques.getOrDefault(o.getIdObservationPv(), List.of());
            SuiviObservation dernier = h.isEmpty() ? null : h.get(h.size() - 1);
            ObservationPvDto dto = new ObservationPvDto();
            dto.setIdObservationPv(o.getIdObservationPv());
            dto.setIdDossier(o.getIdDossier());
            dto.setIdPv(o.getIdPv());
            dto.setSource(o.getSource());
            dto.setIdExamenPiece(o.getIdExamenPiece());
            dto.setLibelle(o.getLibelle());
            dto.setOrdre(o.getOrdre());
            dto.setStatut(dernier == null ? "EMISE" : dernier.getDecision());
            dto.setPrecision(dernier == null ? null : dernier.getPrecision());
            dto.setIteration(dernier == null ? null : dernier.getIteration());
            dto.setHistorique(h.stream().map(s -> new ObservationPvDto.SuiviObservationDto(
                    s.getIteration(), s.getDecision(), s.getPrecision(), s.getImVerificateur(),
                    s.getDateDecision())).toList());
            dtos.add(dto);
        }
        return dtos;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static int nvlInt(Integer i) {
        return i == null ? 0 : i;
    }
}
