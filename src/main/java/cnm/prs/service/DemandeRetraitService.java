package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DemandeRetraitDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.DemandeRetraitVue;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutRetrait;
import cnm.prs.enums.TypeNotification;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DemandeRetraitMapper;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DemandeRetraitVueRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link DemandeRetrait}.
 */
@Service
@Transactional
public class DemandeRetraitService {

    private final DemandeRetraitRepository repository;
    private final DossierRepository dossierRepository;
    private final PrmpRepository prmpRepository;
    private final PpmRepository ppmRepository;
    private final CircuitCascadeService circuitCascade;
    private final NotificationService notificationService;
    private final ControleurDirectory controleurDirectory;
    private final DemandeRetraitVueRepository vueRepository;
    /** ⚠️ Spec « Mandats PRMP » — garde de propriété partagée (attribution OU PRMP en fonction) + vacance. */
    private final DossierIntegriteService dossierIntegrite;

    public DemandeRetraitService(DemandeRetraitRepository repository, DossierRepository dossierRepository,
            PrmpRepository prmpRepository, PpmRepository ppmRepository, CircuitCascadeService circuitCascade,
            NotificationService notificationService,
            ControleurDirectory controleurDirectory, DemandeRetraitVueRepository vueRepository,
            DossierIntegriteService dossierIntegrite) {
        this.dossierIntegrite = dossierIntegrite;
        this.repository = repository;
        this.dossierRepository = dossierRepository;
        this.prmpRepository = prmpRepository;
        this.ppmRepository = ppmRepository;
        this.circuitCascade = circuitCascade;
        this.notificationService = notificationService;
        this.controleurDirectory = controleurDirectory;
        this.vueRepository = vueRepository;
    }

    /**
     * Écran « Mes demandes de retrait » de la PRMP : renvoie ses demandes <strong>et marque l'écran
     * consulté</strong> (UPSERT {@code t_demande_retrait_vue.dateDerniereVue = now} pour cette PRMP),
     * ce qui remet à zéro le compteur de nouveautés. PRMP non identifiée → liste vide.
     */
    public List<DemandeRetraitDto> mesDemandes() {
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (idPrmp == null) {
            return List.of();
        }
        List<DemandeRetraitDto> demandes = repository.findByIdPrmp(idPrmp).stream()
                .map(DemandeRetraitMapper::toDto).toList();
        DemandeRetraitVue vue = vueRepository.findByIdPrmp(idPrmp)
                .orElseGet(() -> new DemandeRetraitVue(null, idPrmp, null));
        vue.setDateDerniereVue(LocalDateTime.now());
        vueRepository.save(vue);
        return demandes;
    }

    /**
     * Liste filtrée (§1, §3.1) : Président/Admin → tout ; PRMP → ses propres demandes ;
     * autres contrôleurs → demandes de leur localité.
     */
    @Transactional(readOnly = true)
    public List<DemandeRetraitDto> findAll() {
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().orElse(null);
            if (idPrmp == null || idPrmp.isBlank()) {
                return List.of();
            }
            return repository.findByIdPrmp(idPrmp).stream().map(DemandeRetraitMapper::toDto).toList();
        }
        return Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(DemandeRetraitMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DemandeRetraitDto findById(Integer id) {
        DemandeRetrait entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DemandeRetrait introuvable : " + id));
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().orElse(null);
            if (idPrmp == null || !idPrmp.equals(entity.getIdPrmp())) {
                throw new AccessDeniedException("Demande hors de votre périmètre de visibilité (§3.1).");
            }
        } else {
            Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        }
        return DemandeRetraitMapper.toDto(entity);
    }

    /**
     * Création d'une demande de retrait par la PRMP. Une nouvelle demande est toujours
     * {@link StatutRetrait#EN_ATTENTE}, sans décision (§3.1). Le motif est obligatoire
     * (déjà imposé par {@code @NotBlank} sur le DTO, MOTIF_RETRAIT NOT NULL).
     */
    public DemandeRetraitDto create(DemandeRetraitDto dto) {
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("PRMP non identifiée."));
        Integer idDossier = dto.getIdDossier();
        // Garde 1 — la PRMP doit être PROPRIÉTAIRE du dossier (§3.1).
        Dossier cible = idDossier == null ? null : dossierRepository.findById(idDossier).orElse(null);
        if (cible == null) {
            throw new AccessDeniedException("Retrait possible uniquement sur l'un de vos dossiers (§3.1).");
        }
        // ⚠️ Spec « Mandats PRMP » — second titre accepté : la PRMP EN FONCTION sur le périmètre du dossier
        // (reprise du traitement après changement de titulaire). Un dossier sans propriétaire ni entité
        // reste hors de portée : ce titre ne relâche pas la garde, il l'élargit à un cas précis.
        if (!dossierRepository.existsVisiblePourPrmp(idDossier, idPrmp)
                && !dossierIntegrite.estPrmpEnFonctionSurLeDossier(cible, idPrmp)) {
            throw new AccessDeniedException("Retrait possible uniquement sur l'un de vos dossiers (§3.1).");
        }
        // Garde 1 bis — sans mandat actif, aucune action de traitement (409 VACANCE_PRMP).
        dossierIntegrite.exigerMandatActif();
        // Garde 2 — dossier éligible : statut « avant PV signé » (§3.3). Le retrait est possible à toute
        // étape du circuit tant que le PV n'est pas signé ; refusé à partir de PV_SIGNE (et au-delà).
        // Même ensemble que GET /api/dossiers/retirables (source unique StatutDossier.NOMS_AVANT_PV_SIGNE).
        String statutDossier = dossierRepository.findById(idDossier).map(Dossier::getStatut).orElse(null);
        if (!StatutDossier.NOMS_AVANT_PV_SIGNE.contains(statutDossier)) {
            throw new BusinessRuleException(
                    "Retrait possible uniquement tant que le PV n'est pas signé (statut « " + statutDossier + " »).");
        }
        // Garde 3 — pas de demande déjà EN_ATTENTE pour ce dossier.
        if (repository.existsByIdDossierAndStatut(idDossier, StatutRetrait.EN_ATTENTE.name())) {
            throw new BusinessRuleException("Une demande de retrait est déjà en attente pour ce dossier.");
        }

        DemandeRetrait entity = new DemandeRetrait();
        entity.setIdDossier(idDossier);
        entity.setIdPrmp(idPrmp);                          // identité = JWT, jamais le corps
        entity.setMotifRetrait(dto.getMotifRetrait());     // @NotBlank
        entity.setStatut(StatutRetrait.EN_ATTENTE.name());
        entity.setDateDemande(LocalDateTime.now());        // date serveur
        DemandeRetrait saved = repository.save(entity);    // ID auto-généré (IDENTITY)
        notifierDemandeAValider(saved);
        return DemandeRetraitMapper.toDto(saved);
    }

    /** [Auto] Notifie le CC de la localité du dossier + le(s) Président(s) qu'une demande attend validation. */
    private void notifierDemandeAValider(DemandeRetrait demande) {
        String localite = dossierRepository.findById(demande.getIdDossier())
                .map(Dossier::getIdLocalite).orElse(null);
        String titre = "Demande de retrait à valider";
        String corps = "La PRMP " + demande.getIdPrmp() + " demande le retrait du dossier "
                + demande.getIdDossier() + ". Motif : " + demande.getMotifRetrait();
        List<Controleur> destinataires = new ArrayList<>(controleurDirectory.presidents());
        if (localite != null) {
            destinataires.addAll(controleurDirectory.chefsCommission(localite));
        }
        for (Controleur c : destinataires) {
            notificationService.emettre(demande.getIdDossier(), TypeNotification.DEMANDE_RETRAIT_A_VALIDER,
                    c.getImControleur(), c.getEmailCont(), titre, corps);
        }
    }

    /**
     * Décision d'<strong>acceptation</strong> d'une demande (CC de la localité ou Président).
     * ⚠️ Règle ajoutée — le dossier repasse en {@link StatutDossier#BROUILLON} ; la PRMP est notifiée.
     */
    public DemandeRetraitDto accepter(Integer id) {
        DemandeRetrait demande = chargerEnAttente(id);
        exigerDecideur(demande);
        demande.setStatut(StatutRetrait.ACCEPTEE.name());
        demande.setImCtrlCc(decideurAuthentifie());      // décideur réel (CC ou Président), JWT
        demande.setDateDecision(LocalDateTime.now());
        DemandeRetrait saved = repository.save(demande);
        if (demande.getIdDossier() != null) {
            dossierRepository.findById(demande.getIdDossier()).ifPresent(d -> {
                d.setStatut(StatutDossier.BROUILLON.name());
                // ⚠️ Règle ajoutée — restaure la référence INITIALE du dossier (celle générée à la création,
                // stockée dans t_ppm.REFERENCE, ex. « 00003/DGB/PPM/2026 ») dans refeDossier, invalidant ainsi
                // la référence de réception (ex. « 00002/PPM/CRM-ANT/2026 »). Le dossier redevient un brouillon
                // entièrement modifiable affichant sa référence d'origine. (Pas de PPM → refeDossier remis à null.)
                String refInitiale = ppmRepository.findByIdDossier(d.getIdDossier()).stream()
                        .findFirst().map(Ppm::getReference).orElse(null);
                d.setRefeDossier(refInitiale);
                dossierRepository.save(d);
                // ⚠️ Règle ajoutée (§3.3) — le retrait est désormais possible jusqu'à EXAMINE : le dossier peut
                // porter tout un enchaînement de circuit (réception → dispatch → examen → projet de PV → navettes,
                // + copies / lettres de renvoi / observations). On purge cet historique en une transaction, dans
                // l'ordre FK-safe (cf. CircuitCascadeService) — réceptions comprises. Après resoumission le dossier
                // redevient « SOUMIS sans réception » et réapparaît dans a-receptionner (re-réception INITIAL,
                // passage 1). Cas SOUMIS/PRET_DISPATCH (jamais dispatché) : seules les réceptions feuilles existent,
                // les autres suppressions portent sur 0 ligne. Le journal d'audit (sans FK) est conservé.
                circuitCascade.purgerCircuit(d.getIdDossier());
            });
        }
        notifierDecision(saved, StatutRetrait.ACCEPTEE);
        return DemandeRetraitMapper.toDto(saved);
    }

    /**
     * Décision de <strong>refus</strong> (CC de la localité ou Président). Le dossier reste inchangé ;
     * le motif de refus (optionnel) est enregistré ; la PRMP est notifiée.
     */
    public DemandeRetraitDto refuser(Integer id, String motif) {
        DemandeRetrait demande = chargerEnAttente(id);
        exigerDecideur(demande);
        demande.setStatut(StatutRetrait.REFUSEE.name());
        demande.setImCtrlCc(decideurAuthentifie());
        demande.setDateDecision(LocalDateTime.now());
        demande.setObsDecision(motif);
        DemandeRetrait saved = repository.save(demande);
        notifierDecision(saved, StatutRetrait.REFUSEE);
        return DemandeRetraitMapper.toDto(saved);
    }

    /** Charge une demande qui doit être {@code EN_ATTENTE} (sinon 409 : déjà traitée). */
    private DemandeRetrait chargerEnAttente(Integer id) {
        DemandeRetrait demande = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DemandeRetrait introuvable : " + id));
        if (!StatutRetrait.EN_ATTENTE.name().equals(demande.getStatut())) {
            throw new BusinessRuleException("La demande a déjà été traitée (statut « " + demande.getStatut() + " »).");
        }
        return demande;
    }

    /** Décision réservée au CC de la localité du dossier OU au Président (rôle↔localité dans le service). */
    private void exigerDecideur(DemandeRetrait demande) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT) {
            return;
        }
        if (profil == ProfilUtilisateur.CHEF_COMMISSION) {
            String localiteDossier = dossierRepository.findById(demande.getIdDossier())
                    .map(Dossier::getIdLocalite).orElse(null);
            String localiteCc = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
            if (localiteDossier != null && localiteDossier.equals(localiteCc)) {
                return;
            }
        }
        throw new AccessDeniedException("Décision réservée au CC de la localité du dossier ou au Président (§3.3).");
    }

    private String decideurAuthentifie() {
        return CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Décideur non identifié."));
    }

    /** Notifie la PRMP de la décision (RETRAIT_ACCEPTE / RETRAIT_REFUSE), par e-mail. */
    private void notifierDecision(DemandeRetrait demande, StatutRetrait decision) {
        String emailPrmp = prmpRepository.findById(demande.getIdPrmp())
                .map(Prmp::getEmailPrmp).orElse(null);
        TypeNotification type = decision == StatutRetrait.ACCEPTEE
                ? TypeNotification.RETRAIT_ACCEPTE : TypeNotification.RETRAIT_REFUSE;
        String titre = decision == StatutRetrait.ACCEPTEE
                ? "Demande de retrait acceptée" : "Demande de retrait refusée";
        String corps = decision == StatutRetrait.ACCEPTEE
                ? "Votre demande de retrait du dossier " + demande.getIdDossier()
                        + " a été acceptée ; le dossier repasse en brouillon."
                : "Votre demande de retrait du dossier " + demande.getIdDossier() + " a été refusée."
                        + (demande.getObsDecision() != null ? " Motif : " + demande.getObsDecision() : "");
        notificationService.emettre(demande.getIdDossier(), type, null, emailPrmp, titre, corps);
    }

    /** File « à valider » : demandes EN_ATTENTE (Président : toutes ; CC : sa localité de dossier). */
    @Transactional(readOnly = true)
    public List<DemandeRetraitDto> aValider() {
        return parStatuts(List.of(StatutRetrait.EN_ATTENTE.name()));
    }

    /** Historique : demandes décidées (ACCEPTEE / REFUSEE), même scope que « à valider ». */
    @Transactional(readOnly = true)
    public List<DemandeRetraitDto> historique() {
        return parStatuts(List.of(StatutRetrait.ACCEPTEE.name(), StatutRetrait.REFUSEE.name()));
    }

    private List<DemandeRetraitDto> parStatuts(List<String> statuts) {
        List<DemandeRetrait> list;
        if (CurrentUser.profil().orElse(null) == ProfilUtilisateur.PRESIDENT) {
            list = repository.findByStatutIn(statuts);
        } else {
            String loc = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
            list = loc == null ? List.of() : repository.findByStatutsEtLocaliteDossier(statuts, loc);
        }
        return list.stream().map(DemandeRetraitMapper::toDto).toList();
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("DemandeRetrait introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
