package cnm.prs.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MarcheDto;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.MarcheMapper;
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

    public MarcheService(MarcheRepository repository, DossierIntegriteService dossierIntegrite,
            MarchePrevisionRepository marchePrevisionRepository,
            ServiceBeneficiaireRepository serviceBeneficiaireRepository, LotRepository lotRepository,
            TrancheRepository trancheRepository, AuditLogService auditLogService, DmcService dmcService) {
        this.repository = repository;
        this.dossierIntegrite = dossierIntegrite;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.lotRepository = lotRepository;
        this.trancheRepository = trancheRepository;
        this.auditLogService = auditLogService;
        this.dmcService = dmcService;
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
     * <p>{@code idPpm} restreint au PPM demandé <strong>avant</strong> le découpage : l'écran
     * « Marchés » filtré par PPM doit paginer l'ensemble filtré, sinon les pages porteraient sur
     * l'ensemble complet et le filtre ne s'appliquerait qu'aux lignes déjà servies.</p>
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MarcheDto> findAllPagine(
            Integer idPpm, org.springframework.data.domain.Pageable pageable) {
        return Pagination.depuisListe(findAll(idPpm), pageable);
    }

    /** Même liste que {@link #findAll()}, restreinte au PPM {@code idPpm} s'il est fourni. */
    @Transactional(readOnly = true)
    public List<MarcheDto> findAll(Integer idPpm) {
        List<MarcheDto> tous = findAll();
        return idPpm == null ? tous : tous.stream().filter(m -> idPpm.equals(m.getIdPpm())).toList();
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

    // ——— Périmètre du marché, exposé aux ressources FILLES (lots, tranches, dates prévisionnelles) ———
    //
    // ⚠️ Ces ressources n'ont pas de périmètre propre : le leur est celui de leur marché parent. Sans
    // ces deux méthodes, chacune retombait sur `.anyRequest().authenticated()` et servait la table
    // entière — une PRMP recevait 403 sur GET /api/marches/{id} d'un marché d'une autre entité, mais
    // 200 sur GET /api/lots/par-marche/{le même id}. La règle est donc écrite ICI, une seule fois,
    // et réutilisée telle quelle par les services filles.

    /**
     * Ids des marchés visibles de l'appelant — <strong>exactement le périmètre de {@link #findAll()}</strong>
     * (§1, §3.1) : tout (Président/Administrateur), les siens (PRMP/UGPM, via la propriété du PPM), ceux de
     * sa localité (contrôleurs, dossier non brouillon), rien sinon. Sert à scoper les listes filles.
     */
    @Transactional(readOnly = true)
    public List<Integer> idsMarchesVisibles() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(Marche::getIdDetail).toList();
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            return idPrmp == null ? List.of()
                    : repository.findVisiblesPourPrmp(idPrmp).stream().map(Marche::getIdDetail).toList();
        }
        return Visibilite.localite()
                .map(loc -> repository.findVisiblesParLocalite(loc).stream().map(Marche::getIdDetail).toList())
                .orElseGet(List::of);
    }

    /**
     * Vrai si le marché est dans le périmètre de l'appelant, <strong>mêmes requêtes que
     * {@link #idsMarchesVisibles()}</strong> mais ciblées sur un identifiant.
     *
     * <p>⚠️ Une différence assumée avec {@link #controlerVisibilite(Marche)}, qui garde {@code GET
     * /api/marches/{id}} : celui-ci ne reconnaît que le profil {@code PRMP} là où {@link Visibilite#estPrmp()}
     * couvre aussi l'{@code UGPM} (dont le claim {@code ref} porte l'ID_PRMP de tutelle — même périmètre).
     * Les écrans PRMP sont ouverts à l'UGPM (routes front {@code roles: ['PRMP','UGPM']}) et consomment
     * lots / dates prévisionnelles : aligner les filles sur {@link #findAll()} est la seule lecture qui ne
     * les casse pas. Le durcissement de {@code GET /api/marches/{id}} pour l'UGPM est une question ouverte,
     * hors de cette correction de périmètre.</p>
     */
    @Transactional(readOnly = true)
    public boolean estMarcheVisible(Integer idDetail) {
        if (idDetail == null) {
            return false;
        }
        if (Visibilite.voitTout()) {
            return true;
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            return idPrmp != null && repository.existsVisiblePourPrmp(idDetail, idPrmp);
        }
        return Visibilite.localite().map(loc -> repository.existsVisibleParLocalite(idDetail, loc)).orElse(false);
    }

    /**
     * Lève {@link AccessDeniedException} (→ 403) si le marché parent est hors périmètre.
     *
     * <p><strong>Marché inconnu ⇒ aucune levée</strong> : la ressource fille serait vide de toute façon, et
     * les endpoints « par marché » sont contractuellement des filtres (liste vide, jamais 404). Contrôler un
     * id inexistant aurait transformé un 200 {@code []} en 403 — régression de contrat, et oracle d'existence
     * offert à l'appelant.</p>
     */
    @Transactional(readOnly = true)
    public void controlerAccesMarche(Integer idDetail) {
        if (idDetail == null || Visibilite.voitTout() || !repository.existsById(idDetail)) {
            return;
        }
        if (!estMarcheVisible(idDetail)) {
            throw new AccessDeniedException("Marché hors de votre périmètre de visibilité (§1, §3.1).");
        }
    }

    /** Vérifie que le marché est dans le périmètre de l'appelant (§1, §3.1) — sinon 403. */
    private void controlerVisibilite(Marche marche) {
        if (Visibilite.voitTout()) {
            return;
        }
        if (CurrentUser.profil().orElse(null) == ProfilUtilisateur.PRMP) {
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
        MarcheDto resultat = MarcheMapper.toDto(repository.save(existing));
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
     * <em>(Un marché supprimable est BROUILLON — jamais dispatché : ni anomalie ni échéance possibles.)</em>
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
    }
}
