package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ModePassationDto;
import cnm.prs.entity.ModePassation;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ModePassationMapper;
import cnm.prs.repository.ModePassationRepository;

/**
 * Logique métier pour {@link ModePassation}.
 */
@Service
@Transactional
public class ModePassationService {

    private final ModePassationRepository repository;

    public ModePassationService(ModePassationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ModePassationDto> findAll() {
        return repository.findAll().stream().map(ModePassationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ModePassationDto findById(Integer id) {
        ModePassation entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ModePassation introuvable : " + id));
        return ModePassationMapper.toDto(entity);
    }

    public ModePassationDto create(ModePassationDto dto) {
        ModePassation entity = ModePassationMapper.toEntity(dto);
        return ModePassationMapper.toDto(repository.save(entity));
    }

    public ModePassationDto update(Integer id, ModePassationDto dto) {
        ModePassation existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ModePassation introuvable : " + id));
        existing.setLibelle(dto.getLibelle());
        existing.setDescription(dto.getDescription());
        existing.setPubliciteRequise(dto.getPubliciteRequise());
        existing.setDelaiMinJours(dto.getDelaiMinJours());
        existing.setBaseLegale(dto.getBaseLegale());
        existing.setIdTypeDmc(dto.getIdTypeDmc());   // mapping mode → type de DMC
        return ModePassationMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ModePassation introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
