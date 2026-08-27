package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AuditLogDto;
import cnm.prs.entity.AuditLog;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.AuditLogMapper;
import cnm.prs.repository.AuditLogRepository;

/**
 * Logique métier pour {@link AuditLog}.
 *
 * <p>⚠️ Audit 2026-08-27 (§3.2 du rapport) — le journal {@code t_audit_log} est en <strong>ajout
 * seul</strong>. Son unique auteur est {@link #enregistrer}, appelé par l'intercepteur HTTP après
 * chaque écriture réussie. Les trois verbes d'écriture du CRUD générique (POST, PUT, DELETE) sont
 * donc refusés en 409 : jusqu'ici seul DELETE l'était, si bien que {@code update} réécrivait la
 * totalité de la preuve (date, acteur, valeurs avant/après, IP, session) et que le POST permettait
 * d'insérer des entrées forgées.</p>
 */
@Service
@Transactional
public class AuditLogService {

    /** Préfixe commun aux trois refus d'écriture (⚠️ audit 2026-08-27) — un seul message d'immuabilité. */
    private static final String IMMUABLE = "Le journal d'audit est immuable :";

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> findAll() {
        return repository.findAll().stream().map(AuditLogMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AuditLogDto findById(Long id) {
        AuditLog entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog introuvable : " + id));
        return AuditLogMapper.toDto(entity);
    }

    /**
     * Création manuelle interdite (⚠️ audit 2026-08-27) : le journal n'est alimenté que par
     * {@link #enregistrer}, appelé par l'intercepteur HTTP après chaque écriture réussie. Un POST
     * ouvert permettait d'y <strong>forger</strong> des entrées — un journal où l'on peut écrire à la
     * main ne prouve plus rien. → HTTP 409, comme la suppression.
     */
    public AuditLogDto create(AuditLogDto dto) {
        throw new BusinessRuleException(IMMUABLE + " création interdite (§3.8).");
    }

    /**
     * Modification interdite (⚠️ audit 2026-08-27) : {@code update} réécrivait date, acteur, table,
     * valeurs avant/après, adresse IP et session — soit la totalité de la preuve. L'immuabilité
     * annoncée n'était vraie que pour DELETE. → HTTP 409.
     */
    public AuditLogDto update(Long id, AuditLogDto dto) {
        throw new BusinessRuleException(IMMUABLE + " modification interdite (§3.8).");
    }

    /**
     * Suppression interdite : le journal d'audit est immuable — toutes les actions y sont
     * tracées et conservées (§3.8). → HTTP 409.
     */
    public void delete(Long id) {
        throw new BusinessRuleException(IMMUABLE + " suppression interdite (§3.8).");
    }

    /**
     * Enregistre une entrée d'audit (§3.8). Appelé automatiquement par l'intercepteur HTTP
     * après chaque écriture réussie. {@code SESSION_ID} reste {@code null} (FK vers
     * {@code t_session_utilisateur}, pas de session réelle pour l'instant).
     */
    public void enregistrer(String imActeur, String nomTable, String idEnregistrement,
            String typeAction, String ipAdresse) {
        AuditLog log = new AuditLog();
        log.setIdLog(repository.nextIdAuditLog());   // PK serveur (sequence)
        log.setDateAction(LocalDateTime.now());
        log.setImActeur(imActeur);
        log.setNomTable(nomTable);
        log.setIdEnregistrement(idEnregistrement);
        log.setTypeAction(typeAction);
        log.setIpAdresse(ipAdresse);
        repository.save(log);
    }
}
