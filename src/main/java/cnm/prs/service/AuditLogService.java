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

    public AuditLogDto create(AuditLogDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdLog(), repository::existsById, "entrée de journal");
        AuditLog entity = AuditLogMapper.toEntity(dto);
        return AuditLogMapper.toDto(repository.save(entity));
    }

    public AuditLogDto update(Long id, AuditLogDto dto) {
        AuditLog existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog introuvable : " + id));
        existing.setDateAction(dto.getDateAction());
        existing.setImActeur(dto.getImActeur());
        existing.setNomTable(dto.getNomTable());
        existing.setIdEnregistrement(dto.getIdEnregistrement());
        existing.setTypeAction(dto.getTypeAction());
        existing.setChampModifie(dto.getChampModifie());
        existing.setAncienneValeur(dto.getAncienneValeur());
        existing.setNouvelleValeur(dto.getNouvelleValeur());
        existing.setIpAdresse(dto.getIpAdresse());
        existing.setSessionId(dto.getSessionId());
        return AuditLogMapper.toDto(repository.save(existing));
    }

    /**
     * Suppression interdite : le journal d'audit est immuable — toutes les actions y sont
     * tracées et conservées (§3.8). → HTTP 409.
     */
    public void delete(Long id) {
        throw new BusinessRuleException("Le journal d'audit est immuable : suppression interdite (§3.8).");
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
