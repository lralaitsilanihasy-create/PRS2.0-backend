package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.TransmissionSigmpDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TransmissionSigmp;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeNotification;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.TransmissionSigmpRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ Spec navette (2026-08-01) — transmission par le VÉRIFICATEUR du sens de la décision de la
 * Commission vers SIGMP (interop PRS 2.0 ↔ SIGMP, enregistrée côté PRS en attendant l'API réelle) :
 * <ul>
 *   <li><strong>Cas 1</strong> (dossier {@code EN_VERIFICATION}, avis ≠ FAVR) : FAV → {@code APPROUVE} ;
 *       DEF / NSP → {@code NON_APPROUVE} ;</li>
 *   <li><strong>Cas 2</strong> (dossier {@code OBSERVATIONS_LEVEES}, avis FAVR) : {@code APPROUVE}
 *       + levée des observations.</li>
 * </ul>
 * Effets : dossier → {@code DECISION_TRANSMISE_SIGMP} et notification {@code PV_A_ARCHIVER} aux
 * Assistants contrôleurs de la localité (le PV leur est transmis pour archivage — l'archivage clôt).
 */
@Service
@Transactional
public class TransmissionSigmpService {

    static final String SENS_APPROUVE = "APPROUVE";
    static final String SENS_NON_APPROUVE = "NON_APPROUVE";
    private static final String AVIS_FAVR = "FAVR";
    private static final String AVIS_FAV = "FAV";
    private static final String ENVOI_ENREGISTREE = "ENREGISTREE";

    private final TransmissionSigmpRepository repository;
    private final DossierRepository dossierRepository;
    private final PvExamenRepository pvExamenRepository;
    private final NotificationService notificationService;
    private final ControleurDirectory controleurDirectory;
    private final cnm.prs.security.PermissionService permissionService;

    public TransmissionSigmpService(TransmissionSigmpRepository repository, DossierRepository dossierRepository,
            PvExamenRepository pvExamenRepository, NotificationService notificationService,
            ControleurDirectory controleurDirectory, cnm.prs.security.PermissionService permissionService) {
        this.repository = repository;
        this.dossierRepository = dossierRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.notificationService = notificationService;
        this.controleurDirectory = controleurDirectory;
        this.permissionService = permissionService;
    }

    /**
     * ⚠️ Audit 2026-08-27 (§3.1 du rapport) — la liste était servie <strong>entière</strong> à tout
     * authentifié. La décision transmise à SIGMP est un acte interne du circuit : la lecture est bornée
     * au périmètre (§1) — Président/Administrateur tout, contrôleurs les dossiers visibles de leur
     * localité, <strong>PRMP/UGPM rien</strong> (elle est notifiée de la décision, elle ne consulte pas
     * le registre d'interopérabilité ; le front ne l'appelle que depuis l'écran du vérificateur).
     */
    @Transactional(readOnly = true)
    public List<TransmissionSigmpDto> findAll() {
        return Visibilite.filtrer(repository::findAll, this::transmissionsDeLaLocalite)
                .stream().map(this::toDto).toList();
    }

    /** Transmissions d'un dossier — ⚠️ même périmètre : liste vide si le dossier n'est pas visible. */
    @Transactional(readOnly = true)
    public List<TransmissionSigmpDto> findByDossier(Integer idDossier) {
        return Visibilite.filtrer(
                        () -> repository.findByIdDossier(idDossier),
                        loc -> dossierRepository.existsDansLocalite(idDossier, loc)
                                ? repository.findByIdDossier(idDossier) : List.of())
                .stream().map(this::toDto).toList();
    }

    /** Transmissions des dossiers visibles d'une localité (périmètre défini une seule fois, côté dossiers). */
    private List<TransmissionSigmp> transmissionsDeLaLocalite(String localite) {
        List<Integer> ids = dossierRepository.findIdsVisiblesParLocalite(localite);
        return ids.isEmpty() ? List.of() : repository.findByIdDossierIn(ids);
    }

    /** Transmet le sens de la décision du dossier (dérivé de l'avis du PV signé) — Vérificateur de la localité. */
    public TransmissionSigmpDto transmettre(Integer idDossier) {
        // ⚠️ Règle MODIFIÉE (2026-08-14, délégation ascendante) : tâche du Vérificateur, exerçable par
        // le titulaire OU via une paire ACTIVE de t_delegation_profil (garde centrale). Localité inchangée.
        if (!permissionService.peutExercer(ProfilUtilisateur.VERIFICATEUR)) {
            throw new AccessDeniedException(
                    "Transmission SIGMP réservée au Contrôleur vérificateur (titulaire ou délégation active).");
        }
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        PvExamen pv = pvExamenRepository.findSignesParDossier(idDossier).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "Aucun PV signé pour ce dossier : transmission SIGMP impossible."));
        String localiteDossier = pvExamenRepository.findLocaliteByPv(pv.getIdPv()).orElse(null);
        String maLocalite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localiteDossier != null && !localiteDossier.equals(maLocalite)) {
            throw new AccessDeniedException("Transmission réservée au vérificateur de la localité du dossier.");
        }

        String avis = pv.getIdAvis();
        String statut = dossier.getStatut();
        String sens;
        boolean levee;
        if (StatutDossier.EN_VERIFICATION.name().equals(statut) && !AVIS_FAVR.equals(avis)) {
            // Cas 1 — FAV approuvé ; DEF / NSP non approuvé.
            sens = AVIS_FAV.equals(avis) ? SENS_APPROUVE : SENS_NON_APPROUVE;
            levee = false;
        } else if (StatutDossier.OBSERVATIONS_LEVEES.name().equals(statut)) {
            // Cas 2 — fin de boucle FAVR : approbation + levée des observations.
            sens = SENS_APPROUVE;
            levee = true;
        } else {
            throw new BusinessRuleException("Transmission SIGMP impossible : statut du dossier « " + statut
                    + " » / avis « " + avis + " » (attendu : décision FAV/DEF/NSP en vérification, "
                    + "ou observations levées après boucle FAVR).");
        }

        TransmissionSigmp t = new TransmissionSigmp();
        t.setIdDossier(idDossier);
        t.setIdPv(pv.getIdPv());
        t.setSens(sens);
        t.setLeveeObservations(levee);
        t.setDateTransmission(LocalDateTime.now());
        t.setImVerificateur(CurrentUser.ref().orElse(null));
        t.setStatutEnvoi(ENVOI_ENREGISTREE);
        TransmissionSigmp saved = repository.save(t);

        dossier.setStatut(StatutDossier.DECISION_TRANSMISE_SIGMP.name());
        dossierRepository.save(dossier);
        notifierAssistantsPvAArchiver(dossier, pv, sens, levee);
        return toDto(saved);
    }

    /** Le PV est transmis aux Assistants contrôleurs de la localité pour ARCHIVAGE ({@code PV_A_ARCHIVER}). */
    private void notifierAssistantsPvAArchiver(Dossier dossier, PvExamen pv, String sens, boolean levee) {
        String localite = pvExamenRepository.findLocaliteByPv(pv.getIdPv()).orElse(null);
        if (localite == null) {
            return;
        }
        String ref = pv.getRefePv() != null ? pv.getRefePv()
                : pv.getReferencePv() != null ? pv.getReferencePv() : ("n° " + pv.getIdPv());
        String titre = "PV à archiver";
        String corps = "Décision transmise à SIGMP (" + (SENS_APPROUVE.equals(sens) ? "approuvé" : "non approuvé")
                + (levee ? ", observations levées" : "") + ") : le PV " + ref + " est à archiver.";
        for (Controleur a : controleurDirectory.assistantsControleurs(localite)) {
            notificationService.emettre(dossier.getIdDossier(), TypeNotification.PV_A_ARCHIVER,
                    a.getImControleur(), a.getEmailCont(), titre, corps);
        }
    }

    private TransmissionSigmpDto toDto(TransmissionSigmp t) {
        return new TransmissionSigmpDto(t.getIdTransmission(), t.getIdDossier(), t.getIdPv(), t.getSens(),
                t.getLeveeObservations(), t.getDateTransmission(), t.getImVerificateur(), t.getStatutEnvoi());
    }
}
