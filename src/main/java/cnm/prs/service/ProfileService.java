package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ProfileDto;
import cnm.prs.entity.Profile;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ProfileMapper;
import cnm.prs.repository.ProfileRepository;

/**
 * Logique métier pour {@link Profile}.
 */
@Service
@Transactional
public class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProfileDto> findAll() {
        return repository.findAll().stream().map(ProfileMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProfileDto findById(Integer id) {
        Profile entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile introuvable : " + id));
        return ProfileMapper.toDto(entity);
    }

    public ProfileDto create(ProfileDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdProfile(), repository::existsById, "profil");
        Profile entity = ProfileMapper.toEntity(dto);
        return ProfileMapper.toDto(repository.save(entity));
    }

    public ProfileDto update(Integer id, ProfileDto dto) {
        Profile existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile introuvable : " + id));
        existing.setProfile(dto.getProfile());
        return ProfileMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Profile introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
