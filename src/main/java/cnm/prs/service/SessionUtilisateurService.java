package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SessionUtilisateurDto;
import cnm.prs.entity.SessionUtilisateur;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.SessionUtilisateurMapper;
import cnm.prs.repository.SessionUtilisateurRepository;

/**
 * Logique métier pour {@link SessionUtilisateur}.
 */
@Service
@Transactional
public class SessionUtilisateurService {

    private final SessionUtilisateurRepository repository;

    public SessionUtilisateurService(SessionUtilisateurRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SessionUtilisateurDto> findAll() {
        return repository.findAll().stream().map(SessionUtilisateurMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SessionUtilisateurDto findById(String id) {
        SessionUtilisateur entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SessionUtilisateur introuvable : " + id));
        return SessionUtilisateurMapper.toDto(entity);
    }

    public SessionUtilisateurDto create(SessionUtilisateurDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdSession(), repository::existsById, "session utilisateur");
        SessionUtilisateur entity = SessionUtilisateurMapper.toEntity(dto);
        return SessionUtilisateurMapper.toDto(repository.save(entity));
    }

    public SessionUtilisateurDto update(String id, SessionUtilisateurDto dto) {
        SessionUtilisateur existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SessionUtilisateur introuvable : " + id));
        existing.setImControleur(dto.getImControleur());
        existing.setDateConnexion(dto.getDateConnexion());
        existing.setDateDeconnexion(dto.getDateDeconnexion());
        existing.setIpAdresse(dto.getIpAdresse());
        existing.setUserAgent(dto.getUserAgent());
        existing.setSucces(dto.getSucces());
        return SessionUtilisateurMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("SessionUtilisateur introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
