package cnm.prs.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MarcheDto;
import cnm.prs.entity.Marche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.MarcheMapper;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
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
    private final AuditLogService auditLogService;

    public MarcheService(MarcheRepository repository, DossierIntegriteService dossierIntegrite,
            MarchePrevisionRepository marchePrevisionRepository, AuditLogService auditLogService) {
        this.repository = repository;
        this.dossierIntegrite = dossierIntegrite;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Liste des marchés <strong>scopée au périmètre de l'appelant</strong> (§1, §3.1) — jamais la
     * table entière : Président/Administrateur voient tout ; la PRMP ne voit que <strong>les
     * siens</strong> (marchés de ses PPM) ; les contrôleurs ne voient que ceux de <strong>leur
     * localité</strong> (dossier non brouillon) ; tout autre profil (ou sans localité) → liste vide.
     */
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
        // Une ligne de marché s'ajoute uniquement à un dossier PPM, en brouillon, propriété de la PRMP courante.
        dossierIntegrite.exigerBrouillonModifiable(dto.getIdDossier());
        dossierIntegrite.exigerTypePpm(dto.getIdDossier());
        Marche entity = MarcheMapper.toEntity(dto);
        entity.setIdDetail(repository.nextIdMarche().intValue());   // PK serveur (séquence) ; id client ignoré
        // Mode = celui saisi (PRMP/import) ; plus de détermination automatique (t_situation/t_regle/t_seuil retirés).
        return MarcheMapper.toDto(repository.save(entity));
    }

    public MarcheDto update(Integer id, MarcheDto dto) {
        Marche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marche introuvable : " + id));
        dossierIntegrite.exigerBrouillonModifiable(existing.getIdDossier());

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
        return MarcheMapper.toDto(repository.save(existing));
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
        Marche saved = repository.save(existing);
        auditLogService.enregistrer(CurrentUser.ref().orElse(null), "t_marche",
                String.valueOf(id), "MODIFICATION_RECTIFICATION", null);
        return MarcheMapper.toDto(saved);
    }

    public void delete(Integer id) {
        Marche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marche introuvable : " + id));
        // Une ligne ne se retire que d'un dossier en brouillon, propriété de la PRMP courante.
        dossierIntegrite.exigerBrouillonModifiable(existing.getIdDossier());
        // Cascade applicative : supprimer d'abord les dates prévisionnelles de CE marché (sous-lignes intrinsèques).
        marchePrevisionRepository.deleteByIdDetail(id);
        repository.deleteById(id);
    }
}
