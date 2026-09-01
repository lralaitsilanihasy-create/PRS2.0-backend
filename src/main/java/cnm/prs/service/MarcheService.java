package cnm.prs.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MarcheDto;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.enums.FormeMarche;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.MarcheMapper;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.EcheanceRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.repository.TrancheRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Marche}.
 *
 * <p>Le <strong>mode de passation</strong> est désormais <strong>purement saisi</strong> (PRMP / import PPM) :
 * il n'y a <strong>plus de détermination automatique</strong> — les référentiels {@code t_situation},
 * {@code t_regle_passation} et {@code t_seuil} ont été retirés. Le champ {@code idMode} fourni est conservé
 * tel quel (l'intégrité vers {@code tr_mode} reste assurée par la FK).</p>
 */
@Service
@Transactional
public class MarcheService {

    private final MarcheRepository repository;
    private final DossierIntegriteService dossierIntegrite;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    private final LotRepository lotRepository;
    private final TrancheRepository trancheRepository;
    private final AuditLogService auditLogService;
    private final DmcService dmcService;
    private final AnomalieRepository anomalieRepository;
    private final EcheanceRepository echeanceRepository;

    public MarcheService(MarcheRepository repository, DossierIntegriteService dossierIntegrite,
            MarchePrevisionRepository marchePrevisionRepository,
            ServiceBeneficiaireRepository serviceBeneficiaireRepository, LotRepository lotRepository,
            TrancheRepository trancheRepository, AuditLogService auditLogService, DmcService dmcService,
            AnomalieRepository anomalieRepository, EcheanceRepository echeanceRepository) {
        this.repository = repository;
        this.dossierIntegrite = dossierIntegrite;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.lotRepository = lotRepository;
        this.trancheRepository = trancheRepository;
        this.auditLogService = auditLogService;
        this.dmcService = dmcService;
        this.anomalieRepository = anomalieRepository;
        this.echeanceRepository = echeanceRepository;
    }

    /**
     * Liste des marchés <strong>scopée au périmètre de l'appelant</strong> (§1, §3.1) — jamais la
     * table entière : Président/Administrateur voient tout ; la PRMP ne voit que <strong>les
     * siens</strong> (marchés de ses PPM) ; les contrôleurs ne voient que ceux de <strong>leur
     * localité</strong> (dossier non brouillon) ; tout autre profil (ou sans localité) → liste vide.
     */
    /**
     * ⚠️ Audit front (2026-08-16) — variante paginée ({@code ?page=&size=}), mêmes filtres de périmètre.
     *
     * <p>⚠️ Audit 2026-08-27 (lot D §3) — découpage <strong>en SQL</strong> : {@code t_marche} est la
     * plus volumineuse des trois tables paginées (une ligne par marché de chaque PPM), et elle était
     * chargée puis mappée en entier à chaque page demandée. Le périmètre reproduit celui de
     * {@link #findAll()} branche par branche.</p>
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MarcheDto> findAllPagine(
            org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Pageable page = Pagination.page(pageable, "idDetail");
        org.springframework.data.domain.Page<Marche> scopees;
        if (Visibilite.voitTout()) {
            scopees = repository.findAll(page);
        } else if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            scopees = idPrmp == null ? org.springframework.data.domain.Page.empty(page)
                    : repository.findVisiblesPourPrmpPagine(idPrmp, page);
        } else {
            String localite = Visibilite.localite().orElse(null);
            scopees = localite == null ? org.springframework.data.domain.Page.empty(page)
                    : repository.findVisiblesParLocalitePagine(localite, page);
        }
        return scopees.map(MarcheMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<MarcheDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(MarcheMapper::toDto).toList();
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            return idPrmp == null ? List.of()
                    : repository.findVisiblesPourPrmp(idPrmp).stream().map(MarcheMapper::toDto).toList();
        }
        return Visibilite.localite()
                .map(loc -> repository.findVisiblesParLocalite(loc).stream().map(MarcheMapper::toDto).toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public MarcheDto findById(Integer id) {
        Marche entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marche introuvable : " + id));
        controlerVisibilite(entity);
        return MarcheMapper.toDto(entity);
    }

    /**
     * Vérifie que le marché est dans le périmètre de l'appelant (§1, §3.1) — sinon 403.
     *
     * <p>⚠️ Correctif 2026-08-26 — l'UGPM partage le périmètre de sa tutelle
     * ({@link Visibilite#estPrmp()}), cf. §3.1.</p>
     */
    private void controlerVisibilite(Marche marche) {
        if (Visibilite.voitTout()) {
            return;
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            if (idPrmp != null && repository.existsVisiblePourPrmp(marche.getIdDetail(), idPrmp)) {
                return;
            }
            throw new AccessDeniedException("Marché hors de votre périmètre (§3.1).");
        }
        boolean ok = Visibilite.localite()
                .map(loc -> repository.existsVisibleParLocalite(marche.getIdDetail(), loc)).orElse(false);
        if (!ok) {
            throw new AccessDeniedException("Marché hors de votre périmètre de visibilité (§1).");
        }
    }

    public MarcheDto create(MarcheDto dto) {
        // Une ligne de marché s'ajoute uniquement à un dossier DDP (planification), en brouillon, propriété de la PRMP.
        dossierIntegrite.exigerBrouillonModifiable(dto.getIdDossier());
        dossierIntegrite.exigerFamilleDdp(dto.getIdDossier());
        Marche entity = MarcheMapper.toEntity(dto);
        entity.setIdDetail(repository.nextIdMarche().intValue());   // PK serveur (séquence) ; id client ignoré
        // Mode = celui saisi (PRMP/import) ; plus de détermination automatique (t_situation/t_regle/t_seuil retirés).
        MarcheDto resultat = MarcheMapper.toDto(repository.save(entity));
        dossierIntegrite.recalculerSousTypeDdp(dto.getIdDossier());   // sous-type PPM / PPM-AGPM (dérivé des marchés)
        return resultat;
    }

    public MarcheDto update(Integer id, MarcheDto dto) {
        Marche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marche introuvable : " + id));
        // ⚠️ 2026-08-02 — accepté aussi EN_ATTENTE_DECISION_PRMP (rectification par import du PPM).
        dossierIntegrite.exigerModifiablePourEditionPpm(existing.getIdDossier());
        // ⚠️ Verrou optimiste HTTP (plan §3) : version périmée → 409 CONFLIT_VERSION, avant toute écriture.
        VerrouOptimiste.exigerVersionCourante(dto.getVersion(), existing.getVersion());

        existing.setIdDossier(dto.getIdDossier());
        existing.setIdPpm(dto.getIdPpm());
        existing.setDesignationMarche(dto.getDesignationMarche());
        existing.setNumCompte(dto.getNumCompte());
        existing.setMontEstim(dto.getMontEstim());
        existing.setAncienMontEstim(dto.getAncienMontEstim());
        existing.setNouvMontEstim(dto.getNouvMontEstim());
        existing.setFinancement(dto.getFinancement());
        existing.setStatut(dto.getStatut());
        existing.setIdNature(dto.getIdNature());
        existing.setIdMode(dto.getIdMode());   // mode choisi (saisie manuelle)
        existing.setFormeMarche(FormeMarche.depuisCodeOuDefaut(dto.getFormeMarche()));
        // ⚠️ Fiche de présentation (2026-09-01) — SEULS champs de cette méthode dont l'absence ne vaut
        // PAS effacement. Les autres sont écrasés depuis le DTO, ce qui convient à une façade qui
        // renvoie toujours la ligne entière ; mais la mise à jour d'un PPM par IMPORT du PDF passe ici
        // avec des justifications nulles — le PDF n'en porte aucune —, et un écrasement effacerait
        // silencieusement tout ce que la PRMP a saisi. null conserve donc ; une chaîne fournie écrit
        // (trim), et un blanc efface volontairement, ce qui laisse au front un moyen de corriger.
        if (dto.getJustifModeDerogatoire() != null) {
            existing.setJustifModeDerogatoire(MarcheMapper.texteOuNull(dto.getJustifModeDerogatoire()));
        }
        if (dto.getJustifDelaiAmenage() != null) {
            existing.setJustifDelaiAmenage(MarcheMapper.texteOuNull(dto.getJustifDelaiAmenage()));
        }
        // ⚠️ saveAndFlush : l'incrément de @Version se fait au flush — sans lui la réponse rendrait
        // l'ancienne version et le client re-conflicterait au PUT suivant (cf. plan §4).
        MarcheDto resultat = MarcheMapper.toDto(repository.saveAndFlush(existing));
        // Si le mode a changé et qu'un DMC A_PREPARER existe, re-dériver son type.
        dmcService.reAffecterTypeSiApreparer(id);
        dossierIntegrite.recalculerSousTypeDdp(existing.getIdDossier());   // le mode a pu (dé)clencher l'AGPM
        return resultat;
    }

    /**
     * ⚠️ Règle ajoutée — édition restreinte (rectification) d'une ligne de marché dont le dossier est
     * {@code EN_ATTENTE_DECISION_PRMP}. La PRMP propriétaire corrige le contenu sans repasser par le
     * brouillon ; le <strong>statut du dossier reste inchangé</strong>. Identité figée (idDossier, idPpm) ;
     * mode revalidé. Tracé : MODIFICATION_RECTIFICATION / t_marche.
     */
    public MarcheDto modifierEnAttenteRectification(Integer id, MarcheDto dto) {
        Marche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marche introuvable : " + id));
        dossierIntegrite.exigerEnAttenteDecisionPrmpModifiable(existing.getIdDossier());
        // Identité figée : idDossier, idPpm conservés ; seul le contenu est modifié.
        existing.setDesignationMarche(dto.getDesignationMarche());
        existing.setNumCompte(dto.getNumCompte());
        existing.setMontEstim(dto.getMontEstim());
        existing.setAncienMontEstim(dto.getAncienMontEstim());
        existing.setNouvMontEstim(dto.getNouvMontEstim());
        existing.setFinancement(dto.getFinancement());
        existing.setStatut(dto.getStatut());
        existing.setIdNature(dto.getIdNature());
        existing.setIdMode(dto.getIdMode());   // mode choisi (saisie manuelle)
        existing.setFormeMarche(FormeMarche.depuisCodeOuDefaut(dto.getFormeMarche()));
        // ⚠️ Fiche de présentation (2026-09-01) — SEULS champs de cette méthode dont l'absence ne vaut
        // PAS effacement. Les autres sont écrasés depuis le DTO, ce qui convient à une façade qui
        // renvoie toujours la ligne entière ; mais la mise à jour d'un PPM par IMPORT du PDF passe ici
        // avec des justifications nulles — le PDF n'en porte aucune —, et un écrasement effacerait
        // silencieusement tout ce que la PRMP a saisi. null conserve donc ; une chaîne fournie écrit
        // (trim), et un blanc efface volontairement, ce qui laisse au front un moyen de corriger.
        if (dto.getJustifModeDerogatoire() != null) {
            existing.setJustifModeDerogatoire(MarcheMapper.texteOuNull(dto.getJustifModeDerogatoire()));
        }
        if (dto.getJustifDelaiAmenage() != null) {
            existing.setJustifDelaiAmenage(MarcheMapper.texteOuNull(dto.getJustifDelaiAmenage()));
        }
        Marche saved = repository.save(existing);
        auditLogService.enregistrer(CurrentUser.ref().orElse(null), "t_marche",
                String.valueOf(id), "MODIFICATION_RECTIFICATION", null);
        dossierIntegrite.recalculerSousTypeDdp(saved.getIdDossier());   // le mode a pu (dé)clencher l'AGPM
        return MarcheMapper.toDto(saved);
    }

    public void delete(Integer id) {
        Marche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marche introuvable : " + id));
        // Une ligne ne se retire que d'un dossier en brouillon, propriété de la PRMP courante.
        dossierIntegrite.exigerBrouillonModifiable(existing.getIdDossier());
        supprimerSousLignes(id);
        repository.deleteById(id);
        dossierIntegrite.recalculerSousTypeDdp(existing.getIdDossier());   // le dernier marché AOO a pu disparaître
    }

    /**
     * ⚠️ Cascade applicative (règle ajoutée) — supprime, en <strong>ordre FK-safe</strong>, tous les
     * enregistrements liés à un marché avant sa suppression : <strong>tranches</strong> de ses lots, puis
     * <strong>lots</strong> (`t_lot`), <strong>bénéficiaires</strong> (`t_service_beneficiaire`) et
     * <strong>dates prévisionnelles</strong> (`t_marche_prevision`). Réutilisée par la suppression d'un PPM.
     *
     * <p>⚠️ Audit 2026-08-27 (lot D §2) — le commentaire d'origine (« un marché supprimable est
     * BROUILLON, jamais dispatché : ni anomalie ni échéance possibles ») reposait sur une hypothèse
     * <strong>périmée</strong> : depuis que le retrait accepté ramène en {@code BROUILLON} un dossier
     * qui a bel et bien circulé, ses lignes peuvent porter des <strong>anomalies</strong>
     * ({@code t_anomalie.ID_DETAIL}) et des <strong>échéances</strong> ({@code t_echeance.ID_DETAIL}),
     * deux vraies FK vers {@code t_marche}. Leur absence de purge rendait la suppression du brouillon
     * en 409 « violation de clé étrangère ». Les deux tables ferment désormais la cascade.</p>
     */
    public void supprimerSousLignes(Integer idDetail) {
        dmcService.supprimerPourMarche(idDetail);   // DMC (1-1) de la ligne — cascade applicative
        List<Integer> idLots = lotRepository.findByIdDetail(idDetail).stream().map(Lot::getIdLot).toList();
        if (!idLots.isEmpty()) {
            trancheRepository.deleteByIdLotIn(idLots);
        }
        lotRepository.deleteByIdDetail(idDetail);
        serviceBeneficiaireRepository.deleteByIdDetail(idDetail);
        marchePrevisionRepository.deleteByIdDetail(idDetail);
        anomalieRepository.deleteByIdDetail(idDetail);     // ⚠️ lot D §2 — trace de circuit, FK vers t_marche
        echeanceRepository.deleteByIdDetail(idDetail);     // ⚠️ lot D §2 — jalons du marché, FK vers t_marche
    }
}
