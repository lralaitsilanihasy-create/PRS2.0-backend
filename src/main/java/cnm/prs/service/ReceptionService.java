package cnm.prs.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ReceptionDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Reception;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeNotification;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ReceptionMapper;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Reception}.
 */
@Service
@Transactional
public class ReceptionService {

    private final ReceptionRepository repository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final ControleurRepository controleurRepository;
    private final ControleurDirectory controleurDirectory;
    private final NotificationService notificationService;
    private final ReferenceService referenceService;

    public ReceptionService(ReceptionRepository repository, DossierRepository dossierRepository,
            PpmRepository ppmRepository, ControleurRepository controleurRepository,
            ControleurDirectory controleurDirectory, NotificationService notificationService,
            ReferenceService referenceService) {
        this.repository = repository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.controleurRepository = controleurRepository;
        this.controleurDirectory = controleurDirectory;
        this.notificationService = notificationService;
        this.referenceService = referenceService;
    }

    @Transactional(readOnly = true)
    public List<ReceptionDto> findAll() {
        return Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(this::toDtoComplet).toList();
    }

    /**
     * Réceptions d'un <strong>seul dossier</strong> (filtre serveur {@code ?idDossier=}) — ne charge
     * que l'utile, dans le périmètre de l'appelant. Hors périmètre ou PRMP → liste vide (les
     * réceptions sont une ressource interne au circuit).
     */
    @Transactional(readOnly = true)
    public List<ReceptionDto> findByDossier(Integer idDossier) {
        if (idDossier == null) {
            return findAll();
        }
        if (Visibilite.estPrmp()) {
            return List.of();
        }
        if (!Visibilite.voitTout()) {
            String localite = Visibilite.localite().orElse(null);
            // Hors localité : aucune réception de ce dossier n'est visible.
            if (localite == null || !receptionsDansLocalite(idDossier, localite)) {
                return List.of();
            }
        }
        return repository.findByIdDossier(idDossier).stream().map(this::toDtoComplet).toList();
    }

    /**
     * Test léger « ce dossier est-il déjà réceptionné ? » (avant d'enregistrer une réception) —
     * sans charger l'historique. Renvoie {@code false} si le dossier est hors périmètre.
     */
    @Transactional(readOnly = true)
    public boolean dejaReceptionne(Integer idDossier) {
        if (idDossier == null || Visibilite.estPrmp()) {
            return false;
        }
        if (!Visibilite.voitTout()) {
            String localite = Visibilite.localite().orElse(null);
            if (localite == null || !receptionsDansLocalite(idDossier, localite)) {
                // Hors localité : on ne révèle pas l'état → traité comme « pas réceptionnable par vous ».
                return false;
            }
        }
        return repository.existsByIdDossier(idDossier);
    }

    /** Vrai si les réceptions du dossier (s'il en a) relèvent de la localité — ou s'il n'en a aucune. */
    private boolean receptionsDansLocalite(Integer idDossier, String localite) {
        List<String> locs = repository.findLocalitesByDossier(idDossier);
        return locs.isEmpty() || locs.contains(localite);
    }

    @Transactional(readOnly = true)
    public ReceptionDto findById(Integer id) {
        Reception entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reception introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return toDtoComplet(entity);
    }

    /**
     * Mappe la réception puis enrichit {@code dateSoumission} avec la date/heure de soumission du
     * dossier rattaché ({@code t_dossier.DATE_SOUMISSION}) — {@code null} pour un dossier ancien.
     */
    private ReceptionDto toDtoComplet(Reception entity) {
        ReceptionDto dto = ReceptionMapper.toDto(entity);
        if (dto != null && entity.getIdDossier() != null) {
            dossierRepository.findById(entity.getIdDossier())
                    .map(Dossier::getDateSoumission)
                    .ifPresent(ds -> dto.setDateSoumission(ReceptionMapper.format(ds)));
        }
        return dto;
    }

    public ReceptionDto create(ReceptionDto dto) {
        exigerDossierSoumis(dto.getIdDossier());
        exigerLocaliteDossier(dto.getIdDossier());
        validatePassage(dto);
        Reception entity = ReceptionMapper.toEntity(dto);
        entity.setIdReception(repository.nextIdReception().intValue());   // PK serveur (sequence), id client ignore (Voie B)
        Reception saved = repository.save(entity);
        String reference = genererReference(saved);   // (regle ajoutee) reference officielle a la reception
        saved.setReference(reference);                 // snapshot immuable persiste sur t_reception (survit aux mutations de refeDossier)
        saved = repository.save(saved);
        declencherPretDispatch(saved);
        return toDtoComplet(saved);                    // le mapper lit desormais reception.reference
    }

    /**
     * (Règle ajoutée) À la réception, génère la référence officielle
     * {@code xxxxx/sous_type/code_localite/annee} et la persiste sur le dossier ({@code REFE_DOSSIER},
     * REFE_DOSSIER restant vide depuis la soumission). ⚠️ Règle modifiée (2026-07-20) — le segment
     * central est le <strong>sous-type</strong> du dossier ({@code ID_SOUS_TYPE} : PPM, PPM-AGPM, DAO,
     * DAOR…), avec repli sur la <strong>famille</strong> ({@code ID_TYPE_DOSSIER}) si le sous-type est
     * absent (dossier historique non repris) ; la <strong>numérotation reste indexée sur la famille</strong>
     * (continuité inchangée). Segment localité : réception <strong>centrale</strong> (utilisateur
     * transversal, sans localité — ex. Président) -> "CNM" ; sinon "CRM-" + localité du dossier.
     */
    private String genererReference(Reception reception) {
        Dossier dossier = dossierRepository.findById(reception.getIdDossier()).orElse(null);
        if (dossier == null) {
            return null;
        }
        String famille = dossier.getIdTypeDossier();
        if (famille == null || famille.isBlank()) {
            // Dossier sans type : pas de référence structurée, mais la réception reste valide.
            return null;
        }
        // Segment affiché = sous-type ; repli sur la famille si le dossier n'a pas de sous-type.
        String segment = dossier.getIdSousType();
        if (segment == null || segment.isBlank()) {
            segment = famille;
        }
        String localite = localiteDuDossier(reception.getIdDossier());
        boolean estCentrale = Visibilite.localite().filter(l -> !l.isBlank()).isEmpty();
        int annee = exerciceDuDossier(reception.getIdDossier());
        String reference = referenceService.generer(segment, famille, localite, estCentrale, annee);
        dossier.setRefeDossier(reference);
        dossierRepository.save(dossier);
        return reference;
    }

    /** Exercice budgétaire du dossier (premier PPM), sinon année courante. */
    private int exerciceDuDossier(Integer idDossier) {
        return ppmRepository.findByIdDossier(idDossier).stream()
                .map(Ppm::getExercice).filter(Objects::nonNull)
                .findFirst().orElse(LocalDate.now().getYear());
    }

    /**
     * Précondition de circuit : on ne réceptionne pas un dossier encore en {@code BROUILLON}
     * (non soumis). Cohérent avec les autres préconditions du circuit → 409.
     */
    private void exigerDossierSoumis(Integer idDossier) {
        String statut = idDossier == null ? null
                : dossierRepository.findById(idDossier).map(Dossier::getStatut).orElse(null);
        if (StatutDossier.BROUILLON.name().equals(statut)) {
            throw new BusinessRuleException(
                    "Réception impossible : le dossier est en brouillon (non soumis).");
        }
    }

    public ReceptionDto update(Integer id, ReceptionDto dto) {
        exigerLocaliteDossier(dto.getIdDossier());
        validatePassage(dto);
        Reception existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reception introuvable : " + id));
        existing.setIdDossier(dto.getIdDossier());
        existing.setNumPassage(dto.getNumPassage());
        existing.setTypePassage(dto.getTypePassage());
        existing.setImCtrlRecept(dto.getImCtrlRecept());
        existing.setDateReception(ReceptionMapper.toLocalDateTime(dto.getDateReception()));
        existing.setObservation(dto.getObservation());
        existing.setComplet(dto.getComplet());
        existing.setIdReceptionPrec(dto.getIdReceptionPrec());
        Reception saved = repository.save(existing);
        declencherPretDispatch(saved);
        return toDtoComplet(saved);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Reception introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /**
     * Comportement {@code [Auto]} (§2.2) : dès qu'une réception est marquée
     * {@code COMPLET = true}, le dossier passe au statut {@code PRET_DISPATCH}.
     *
     * <p>Lors de la <em>transition</em> vers PRET_DISPATCH, une notification est adressée au
     * Président (toutes localités) et au Chef de commission de la localité du dossier
     * (déduite du contrôleur réceptionnaire).</p>
     */
    private void declencherPretDispatch(Reception reception) {
        if (!Boolean.TRUE.equals(reception.getComplet()) || reception.getIdDossier() == null) {
            return;
        }
        dossierRepository.findById(reception.getIdDossier()).ifPresent(dossier -> {
            String statut = dossier.getStatut();
            // Ne pas réactiver un dossier déjà retiré ou clôturé.
            if (StatutDossier.RETIRE.name().equals(statut) || StatutDossier.CLOTURE.name().equals(statut)) {
                return;
            }
            boolean dejaPret = StatutDossier.PRET_DISPATCH.name().equals(statut);
            dossier.setStatut(StatutDossier.PRET_DISPATCH.name());
            dossierRepository.save(dossier);
            if (!dejaPret) {
                notifierPretDispatch(reception, dossier.getIdDossier());
            }
        });
    }

    /** Notifie le Président et le CC de la localité du passage d'un dossier en PRET_DISPATCH (§2.2, §3.4). */
    private void notifierPretDispatch(Reception reception, Integer idDossier) {
        String titre = "Dossier prêt à dispatcher";
        String corps = "Le dossier " + idDossier + " est complet et prêt à être dispatché.";

        for (Controleur president : controleurDirectory.presidents()) {
            notificationService.emettre(idDossier, TypeNotification.PRET_DISPATCH,
                    president.getImControleur(), president.getEmailCont(), titre, corps);
        }
        String localite = reception.getImCtrlRecept() == null ? null
                : controleurRepository.findById(reception.getImCtrlRecept())
                        .map(Controleur::getIdLocalite).orElse(null);
        if (localite != null) {
            for (Controleur cc : controleurDirectory.chefsCommission(localite)) {
                notificationService.emettre(idDossier, TypeNotification.PRET_DISPATCH,
                        cc.getImControleur(), cc.getEmailCont(), titre, corps);
            }
        }
    }

    /**
     * Contrainte de localité (§3.3) : un contrôleur n'agit que sur des dossiers de sa
     * localité (sauf Président/Admin) — y compris à la <strong>première</strong> réception, dès
     * lors que la localité du dossier est connue (cf. {@link #localiteDuDossier}). Si elle est
     * indéterminée, aucune contrainte.
     */
    private void exigerLocaliteDossier(Integer idDossier) {
        Visibilite.exigerLocalite(localiteDuDossier(idDossier));
    }

    /**
     * Localité d'un dossier (§1), par ordre de priorité : sa propre localité
     * ({@code t_dossier.ID_LOCALITE}, estampillée à la soumission), sinon celle de son PPM
     * ({@code Ppm.idLocalite}), sinon celle d'une réception existante (contrôleur réceptionnaire).
     * {@code null} si aucune source ne la fournit.
     */
    private String localiteDuDossier(Integer idDossier) {
        if (idDossier == null) {
            return null;
        }
        String loc = dossierRepository.findById(idDossier)
                .map(Dossier::getIdLocalite).filter(l -> l != null && !l.isBlank()).orElse(null);
        if (loc != null) {
            return loc;
        }
        loc = ppmRepository.findByIdDossier(idDossier).stream()
                .map(Ppm::getIdLocalite).filter(l -> l != null && !l.isBlank()).findFirst().orElse(null);
        if (loc != null) {
            return loc;
        }
        return repository.findLocalitesByDossier(idDossier).stream().findFirst().orElse(null);
    }

    /** Valeur de TYPE_PASSAGE pour la réception initiale (§3.4). */
    private static final String TYPE_PASSAGE_INITIAL = "INITIAL";

    /**
     * Cohérence NUM_PASSAGE / TYPE_PASSAGE (§3.4) : la réception initiale porte
     * {@code NUM_PASSAGE = 1} et {@code TYPE_PASSAGE = INITIAL}, et inversement le type
     * INITIAL n'est autorisé qu'au premier passage. NUM_PASSAGE doit être &gt;= 1.
     */
    private void validatePassage(ReceptionDto dto) {
        Integer num = dto.getNumPassage();
        String type = dto.getTypePassage();

        if (num != null && num < 1) {
            throw new BusinessRuleException("NUM_PASSAGE doit être supérieur ou égal à 1.");
        }
        boolean estInitial = TYPE_PASSAGE_INITIAL.equalsIgnoreCase(type);
        if (num != null && num == 1 && !estInitial) {
            throw new BusinessRuleException(
                    "Au premier passage (NUM_PASSAGE = 1), TYPE_PASSAGE doit être INITIAL (§3.4).");
        }
        if (estInitial && num != null && num != 1) {
            throw new BusinessRuleException(
                    "TYPE_PASSAGE = INITIAL n'est autorisé qu'au premier passage (NUM_PASSAGE = 1) (§3.4).");
        }
    }
}
