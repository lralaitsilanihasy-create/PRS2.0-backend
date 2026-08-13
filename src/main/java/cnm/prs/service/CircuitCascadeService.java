package cnm.prs.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.repository.CopieDossierRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ExamenPieceRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.LettreRenvoiLueRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.ObservationControleRepository;
import cnm.prs.repository.ObservationPvRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.SuiviObservationRepository;
import cnm.prs.repository.VerificationRepository;

/**
 * ⚠️ Règle ajoutée (§3.3) — <strong>purge du circuit</strong> d'un dossier.
 *
 * <p>Quand une demande de retrait est <strong>acceptée</strong>, le dossier repasse en
 * {@code BROUILLON} (rééditable, re-soumissible). Comme le retrait est désormais possible à toute
 * étape « avant PV signé » (jusqu'à {@code EXAMINE} inclus), le dossier peut porter un enchaînement
 * réception → dispatch → examen → projet de PV → navettes, ainsi que copies, lettres de renvoi et
 * observations. Ce service supprime <strong>tout l'historique de circuit</strong> dans le bon ordre
 * (enfants → parents) pour éviter toute violation de clé étrangère, en une seule transaction.</p>
 *
 * <p>Fermeture des dépendances FK (validée sur le schéma réel) :
 * {@code t_observation_controle → t_examen_detail → {t_pv_navette, t_verification} → t_pv_examen →
 * t_lettre_renvoi_lue → t_lettre_renvoi → t_copie_dossier → t_examen → t_dispatch → t_reception}.
 * Chaque suppression est bornée au dossier ({@code deleteParDossier}) ; les tables sans ligne pour ce
 * dossier suppriment simplement 0 enregistrement (cas d'un dossier {@code SOUMIS}/{@code PRET_DISPATCH}
 * jamais dispatché). Le <strong>journal d'audit</strong> ({@code t_audit_log}, sans FK) est conservé.</p>
 */
@Service
public class CircuitCascadeService {

    private final ObservationControleRepository observationControleRepository;
    private final ExamenDetailRepository examenDetailRepository;
    private final ExamenPieceRepository examenPieceRepository;
    private final PvNavetteRepository pvNavetteRepository;
    private final VerificationRepository verificationRepository;
    private final PvExamenRepository pvExamenRepository;
    private final LettreRenvoiLueRepository lettreRenvoiLueRepository;
    private final LettreRenvoiRepository lettreRenvoiRepository;
    private final CopieDossierRepository copieDossierRepository;
    private final ExamenRepository examenRepository;
    private final DispatchRepository dispatchRepository;
    private final ReceptionRepository receptionRepository;
    private final SuiviObservationRepository suiviObservationRepository;
    private final ObservationPvRepository observationPvRepository;

    public CircuitCascadeService(ObservationControleRepository observationControleRepository,
            ExamenDetailRepository examenDetailRepository, ExamenPieceRepository examenPieceRepository,
            PvNavetteRepository pvNavetteRepository,
            VerificationRepository verificationRepository, PvExamenRepository pvExamenRepository,
            LettreRenvoiLueRepository lettreRenvoiLueRepository, LettreRenvoiRepository lettreRenvoiRepository,
            CopieDossierRepository copieDossierRepository, ExamenRepository examenRepository,
            DispatchRepository dispatchRepository, ReceptionRepository receptionRepository,
            SuiviObservationRepository suiviObservationRepository, ObservationPvRepository observationPvRepository) {
        this.suiviObservationRepository = suiviObservationRepository;
        this.observationPvRepository = observationPvRepository;
        this.observationControleRepository = observationControleRepository;
        this.examenDetailRepository = examenDetailRepository;
        this.examenPieceRepository = examenPieceRepository;
        this.pvNavetteRepository = pvNavetteRepository;
        this.verificationRepository = verificationRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.lettreRenvoiLueRepository = lettreRenvoiLueRepository;
        this.lettreRenvoiRepository = lettreRenvoiRepository;
        this.copieDossierRepository = copieDossierRepository;
        this.examenRepository = examenRepository;
        this.dispatchRepository = dispatchRepository;
        this.receptionRepository = receptionRepository;
    }

    /**
     * Supprime, en une transaction et dans l'ordre FK-safe (feuilles → racine), tout l'historique de
     * circuit d'un dossier : observations, détails d'examen, navettes, vérifications, projets de PV,
     * accusés de lecture, lettres de renvoi, copies, examens, dispatchs et réceptions. À appeler à
     * l'acceptation d'un retrait, avant/pendant la remise du dossier en {@code BROUILLON}.
     */
    @Transactional
    public void purgerCircuit(Integer idDossier) {
        purgerApresDispatch(idDossier);                              // 1..10 — aval du dispatch + dispatchs
        receptionRepository.deleteByIdDossier(idDossier);            // 11 — enfant de t_dossier
    }

    /**
     * ⚠️ Règle ajoutée — purge partielle pour l'<strong>annulation d'un dispatch</strong> (retrait du
     * dossier au Membre par le Président/CC, possible jusqu'à {@code EXAMINE}) : supprime tout l'aval
     * du dispatch (observations, détails d'examen, navettes, vérifications, projets de PV, lettres de
     * renvoi, copies, examens) puis les dispatchs eux-mêmes, mais <strong>conserve les réceptions</strong> :
     * le dossier revient {@code PRET_DISPATCH}, re-dispatchable sur sa réception existante.
     */
    @Transactional
    public void purgerApresDispatch(Integer idDossier) {
        // ⚠️ Spec observations FAVR (2026-08-02) — suivi des observations du PV (historique puis périmètre).
        suiviObservationRepository.deleteParDossier(idDossier);      // 0a — enfant de t_observation_pv
        observationPvRepository.deleteParDossier(idDossier);         // 0b — enfant de t_dossier / t_pv_examen
        observationControleRepository.deleteParDossier(idDossier);   // 1 — enfant de t_examen_detail
        examenDetailRepository.deleteParDossier(idDossier);          // 2 — enfant de t_examen
        examenPieceRepository.deleteParDossier(idDossier);           // 2b — enfant de t_examen (pièces examinées)
        pvNavetteRepository.deleteParDossier(idDossier);             // 3 — enfant de t_pv_examen
        verificationRepository.deleteParDossier(idDossier);          // 4 — enfant de t_pv_examen / t_reception
        pvExamenRepository.deleteParDossier(idDossier);              // 5 — enfant de t_examen
        lettreRenvoiLueRepository.deleteParDossier(idDossier);       // 6 — enfant de t_lettre_renvoi
        lettreRenvoiRepository.deleteParDossier(idDossier);          // 7 — enfant de t_examen / t_dossier
        copieDossierRepository.deleteParDossier(idDossier);          // 8 — enfant de t_dispatch / t_dossier
        examenRepository.deleteParDossier(idDossier);                // 9 — enfant de t_dispatch
        dispatchRepository.deleteParDossier(idDossier);              // 10 — enfant de t_reception
    }
}
