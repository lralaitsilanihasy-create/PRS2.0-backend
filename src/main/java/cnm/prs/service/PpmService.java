package cnm.prs.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PpmDto;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Ppm;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PpmMapper;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Ppm}.
 */
@Service
@Transactional
public class PpmService {

    private final PpmRepository repository;
    private final DossierIntegriteService dossierIntegrite;
    private final MarcheRepository marcheRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final AuditLogService auditLogService;
    private final DossierRepository dossierRepository;
    private final ReceptionRepository receptionRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final MarcheService marcheService;

    public PpmService(PpmRepository repository, DossierIntegriteService dossierIntegrite,
            MarcheRepository marcheRepository, MarchePrevisionRepository marchePrevisionRepository,
            AuditLogService auditLogService, DossierRepository dossierRepository,
            ReceptionRepository receptionRepository, DemandeRetraitRepository demandeRetraitRepository,
            MarcheService marcheService) {
        this.repository = repository;
        this.dossierIntegrite = dossierIntegrite;
        this.marcheRepository = marcheRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.auditLogService = auditLogService;
        this.dossierRepository = dossierRepository;
        this.receptionRepository = receptionRepository;
        this.demandeRetraitRepository = demandeRetraitRepository;
        this.marcheService = marcheService;
    }

    /**
     * Liste des PPM <strong>scopée au périmètre de l'appelant</strong> (§1, §3.1) — jamais la table
     * entière : Président/Administrateur voient tout ; la PRMP ne voit que <strong>les siens</strong>
     * ({@code t_ppm.ID_PRMP}) ; les contrôleurs ne voient que ceux de <strong>leur localité</strong>
     * (dossier non brouillon) ; tout autre profil (ou sans localité) → liste vide.
     */
    /** ⚠️ Audit front (2026-08-16) — variante paginée ({@code ?page=&size=}), mêmes filtres de périmètre. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PpmDto> findAllPagine(
            org.springframework.data.domain.Pageable pageable) {
        return Pagination.depuisListe(findAll(), pageable);
    }

    @Transactional(readOnly = true)
    public List<PpmDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(PpmMapper::toDto).map(this::enrichir).toList();
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            return idPrmp == null ? List.of()
                    : repository.findVisiblesParPrmp(idPrmp).stream().map(PpmMapper::toDto).map(this::enrichir).toList();
        }
        return Visibilite.localite()
                .map(loc -> repository.findVisiblesParLocalite(loc).stream()
                        .map(PpmMapper::toDto).map(this::enrichir).toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public PpmDto findById(Integer id) {
        Ppm entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ppm introuvable : " + id));
        controlerVisibilite(entity);
        return enrichir(PpmMapper.toDto(entity));
    }

    /**
     * Renseigne le dérivé serveur {@code agpmRequis} sur un PPM lu : {@code true} ssi ≥1 marché du PPM
     * est en « appel d'offres ouvert » ({@code ModePassation.declencheAgpm}). Appelé sur toutes les
     * lectures PPM ({@link #findAll}, {@link #findById}, {@link #findByDossier}).
     */
    private PpmDto enrichir(PpmDto dto) {
        if (dto != null && dto.getIdPpm() != null) {
            dto.setAgpmRequis(marcheRepository.existsMarcheDeclencheurAgpmByPpm(dto.getIdPpm()));
        }
        return dto;
    }

    /**
     * Résout le PPM rattaché à un dossier (mapping {@code idDossier → PPM}) pour ouvrir un brouillon
     * depuis « Mes brouillons » : lecture par le propriétaire <strong>quel que soit le statut</strong>
     * (même critère de visibilité que {@link #findById} — non filtré par BROUILLON). Couvre le cas d'un
     * brouillon PPM <strong>sans aucun marché</strong> (aucune autre source d'{@code idPpm} côté front).
     * Aucun PPM rattaché → {@code 404} ; hors périmètre → {@code 403}.
     */
    @Transactional(readOnly = true)
    public PpmDto findByDossier(Integer idDossier) {
        Ppm entity = repository.findByIdDossier(idDossier).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Aucun PPM rattaché au dossier : " + idDossier));
        controlerVisibilite(entity);
        return enrichir(PpmMapper.toDto(entity));
    }

    /**
     * Vérifie que le PPM est dans le périmètre de l'appelant (§1, §3.1) — sinon 403.
     *
     * <p>⚠️ Correctif 2026-08-26 — l'UGPM partage le périmètre de sa tutelle
     * ({@link Visibilite#estPrmp()}), cf. §3.1.</p>
     */
    private void controlerVisibilite(Ppm ppm) {
        if (Visibilite.voitTout()) {
            return;
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            if (idPrmp != null && idPrmp.equals(ppm.getIdPrmp())) {
                return;
            }
            throw new AccessDeniedException("PPM hors de votre périmètre (§3.1).");
        }
        boolean ok = Visibilite.localite()
                .map(loc -> repository.existsVisibleParLocalite(ppm.getIdPpm(), loc)).orElse(false);
        if (!ok) {
            throw new AccessDeniedException("PPM hors de votre périmètre de visibilité (§1).");
        }
    }

    public PpmDto create(PpmDto dto) {
        // Le PPM ne se rattache qu'à un dossier de la famille DDP, en brouillon, propriété de la PRMP courante.
        dossierIntegrite.exigerBrouillonModifiable(dto.getIdDossier());
        dossierIntegrite.exigerFamilleDdp(dto.getIdDossier());
        Ppm entity = PpmMapper.toEntity(dto);
        entity.setIdPpm(repository.nextIdPpm().intValue());         // ⚠️ PK serveur (séquence) ; id client ignoré
        return PpmMapper.toDto(repository.save(entity));
    }

    public PpmDto update(Integer id, PpmDto dto) {
        Ppm existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ppm introuvable : " + id));
        // ⚠️ 2026-08-02 — accepté aussi EN_ATTENTE_DECISION_PRMP (rectification par import du PPM).
        dossierIntegrite.exigerModifiablePourEditionPpm(existing.getIdDossier());
        // ⚠️ Verrou optimiste HTTP (plan §3) : version périmée → 409 CONFLIT_VERSION, avant toute écriture.
        VerrouOptimiste.exigerVersionCourante(dto.getVersion(), existing.getVersion());
        existing.setIdDossier(dto.getIdDossier());
        existing.setExercice(dto.getExercice());
        existing.setSignataire(dto.getSignataire());
        existing.setDateSignature(dto.getDateSignature());
        existing.setDatePpmInit(dto.getDatePpmInit());
        existing.setNumMajPrec(dto.getNumMajPrec());
        existing.setDateMajPrec(dto.getDateMajPrec());
        existing.setNumMaj(dto.getNumMaj());
        existing.setDateMaj(dto.getDateMaj());
        existing.setReference(dto.getReference());
        existing.setLibelle(dto.getLibelle());
        existing.setDateReceptionCnm(dto.getDateReceptionCnm());
        existing.setIdLocalite(dto.getIdLocalite());
        existing.setVu(dto.getVu());
        existing.setIdPrmp(dto.getIdPrmp());
        existing.setMotifMaj(dto.getMotifMaj());
        // ⚠️ saveAndFlush : l'incrément de @Version se fait au flush — sans lui la réponse rendrait
        // l'ancienne version et le client re-conflicterait au PUT suivant (cf. plan §4).
        return PpmMapper.toDto(repository.saveAndFlush(existing));
    }

    /**
     * ⚠️ Règle ajoutée — édition restreinte (rectification) de l'en-tête d'un PPM dont le dossier est
     * {@code EN_ATTENTE_DECISION_PRMP}. La PRMP propriétaire corrige le contenu sans repasser par le
     * brouillon ; le <strong>statut du dossier reste inchangé</strong>. Identité figée
     * (idDossier, idPrmp, idLocalite). Tracé : MODIFICATION_RECTIFICATION / t_ppm.
     */
    public PpmDto modifierEnAttenteRectification(Integer id, PpmDto dto) {
        Ppm existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ppm introuvable : " + id));
        dossierIntegrite.exigerEnAttenteDecisionPrmpModifiable(existing.getIdDossier());
        // Identité figée : idDossier, idPrmp, idLocalite conservés ; seul le contenu est modifié.
        existing.setExercice(dto.getExercice());
        existing.setSignataire(dto.getSignataire());
        existing.setDateSignature(dto.getDateSignature());
        existing.setDatePpmInit(dto.getDatePpmInit());
        existing.setNumMajPrec(dto.getNumMajPrec());
        existing.setDateMajPrec(dto.getDateMajPrec());
        existing.setNumMaj(dto.getNumMaj());
        existing.setDateMaj(dto.getDateMaj());
        existing.setReference(dto.getReference());
        existing.setLibelle(dto.getLibelle());
        existing.setDateReceptionCnm(dto.getDateReceptionCnm());
        existing.setVu(dto.getVu());
        existing.setMotifMaj(dto.getMotifMaj());
        Ppm saved = repository.save(existing);
        auditLogService.enregistrer(CurrentUser.ref().orElse(null), "t_ppm",
                String.valueOf(id), "MODIFICATION_RECTIFICATION", null);
        return PpmMapper.toDto(saved);
    }

    public void delete(Integer id) {
        Ppm existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ppm introuvable : " + id));
        // Un PPM ne se supprime que sur un dossier en brouillon, propriété de la PRMP courante.
        dossierIntegrite.exigerBrouillonModifiable(existing.getIdDossier());
        Integer idDossier = existing.getIdDossier();
        // Cascade applicative : SES marchés et TOUTES leurs sous-lignes (prévisions, bénéficiaires,
        // lots/tranches), puis le PPM — en une transaction (réutilise la cascade de MarcheService).
        List<Marche> marches = marcheRepository.findByIdPpm(id);
        for (Marche m : marches) {
            marcheService.supprimerSousLignes(m.getIdDetail());
        }
        marcheRepository.deleteAll(marches);
        repository.deleteById(id);
        // ⚠️ Règle ajoutée — cohérence « Mes brouillons » : si le dossier brouillon devient un
        // BROUILLON PUR (plus aucun PPM ni marché, et SANS historique de circuit : ni réception ni
        // demande de retrait), le supprimer. Un dossier avec historique (revenu BROUILLON via retrait)
        // porte des traces FK (réception, demande_retrait, notifications…) → conservé (pas de hard delete).
        // Cas multi-PPM également préservé (un autre PPM subsiste → non supprimé).
        if (!repository.existsByIdDossier(idDossier)
                && !marcheRepository.existsByIdDossier(idDossier)
                && !receptionRepository.existsByIdDossier(idDossier)
                && !demandeRetraitRepository.existsByIdDossier(idDossier)) {
            dossierRepository.deleteById(idDossier);
        }
        // Dossier conservé (historique) : plus de marchés → le sous-type DDP redescend à PPM.
        dossierIntegrite.recalculerSousTypeDdp(idDossier);
    }
}
