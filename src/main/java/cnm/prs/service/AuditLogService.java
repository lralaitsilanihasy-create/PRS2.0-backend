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
 * <p><strong>Journal immuable (§3.8).</strong> Le journal d'audit est la pièce probante du contrôle :
 * il n'a qu'une seule voie d'écriture, {@link #enregistrer}, appelée par le serveur lui-même
 * (intercepteur HTTP et services métier). Les trois verbes d'écriture du CRUD générique —
 * création, modification, suppression — sont fermés sans exception : une entrée du journal ne peut
 * être ni forgée, ni réécrite, ni effacée par un appel d'API, fût-il celui d'un administrateur.</p>
 */
@Service
@Transactional
public class AuditLogService {

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
     * Création par l'API interdite : une entrée d'audit n'est jamais déclarée par un client, elle est
     * constatée par le serveur (§3.8). Laisser un client poser lui-même {@code imActeur} reviendrait
     * à laisser fabriquer une preuve au nom d'un tiers. Voie d'écriture unique : {@link #enregistrer}.
     * → HTTP 409.
     */
    public AuditLogDto create(AuditLogDto dto) {
        throw new BusinessRuleException("Le journal d'audit est immuable : création interdite (§3.8).");
    }

    /**
     * Modification interdite : le journal d'audit est immuable — une entrée écrite ne se réécrit pas
     * (§3.8). Sans cette garde, un acteur pouvait réattribuer sa propre action à un tiers en
     * remplaçant {@code imActeur}, sans que la substitution laisse elle-même de trace.
     * → HTTP 409.
     */
    public AuditLogDto update(Long id, AuditLogDto dto) {
        throw new BusinessRuleException("Le journal d'audit est immuable : modification interdite (§3.8).");
    }

    /**
     * Suppression interdite : le journal d'audit est immuable — toutes les actions y sont
     * tracées et conservées (§3.8). → HTTP 409.
     */
    public void delete(Long id) {
        throw new BusinessRuleException("Le journal d'audit est immuable : suppression interdite (§3.8).");
    }

    /**
     * Enregistre une entrée d'audit (§3.8). <strong>Seule voie d'écriture du journal</strong> : appelée
     * par l'intercepteur HTTP après chaque écriture réussie, et par les services métier pour les
     * signaux qu'ils veulent tracer explicitement. {@code imActeur} vient toujours du principal
     * courant, jamais du corps d'une requête. {@code SESSION_ID} reste {@code null} (FK vers
     * {@code t_session_utilisateur}, pas de session réelle pour l'instant).
     */
    public void enregistrer(String imActeur, String nomTable, String idEnregistrement,
            String typeAction, String ipAdresse) {
        AuditLog log = new AuditLog();
        log.setIdLog(repository.nextIdAuditLog());   // PK serveur (seq_audit_log)
        log.setDateAction(LocalDateTime.now());
        log.setImActeur(imActeur);
        log.setNomTable(nomTable);
        log.setIdEnregistrement(idEnregistrement);
        log.setTypeAction(typeAction);
        log.setIpAdresse(ipAdresse);
        repository.save(log);
    }
}
