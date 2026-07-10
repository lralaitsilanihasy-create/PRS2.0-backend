package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.TypeDmcDto;
import cnm.prs.entity.TypeDmc;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.TypeDmcMapper;
import cnm.prs.repository.TypeDmcRepository;

/**
 * Logique métier pour {@link TypeDmc} (référentiel administrable des types de DMC).
 */
@Service
@Transactional
public class TypeDmcService {

    private final TypeDmcRepository repository;

    public TypeDmcService(TypeDmcRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TypeDmcDto> findAll() {
        return repository.findAll().stream().map(TypeDmcMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TypeDmcDto findById(Long id) {
        return TypeDmcMapper.toDto(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de DMC introuvable : " + id)));
    }

    public TypeDmcDto create(TypeDmcDto dto) {
        if (repository.existsByCode(dto.getCode())) {
            throw new BusinessRuleException("Un type de DMC porte déjà le code « " + dto.getCode() + " ».");
        }
        TypeDmc entity = TypeDmcMapper.toEntity(dto);
        entity.setIdTypeDmc(null);   // PK attribuée par la base (IDENTITY)
        return TypeDmcMapper.toDto(repository.save(entity));
    }

    public TypeDmcDto update(Long id, TypeDmcDto dto) {
        TypeDmc existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de DMC introuvable : " + id));
        if (!existing.getCode().equals(dto.getCode()) && repository.existsByCode(dto.getCode())) {
            throw new BusinessRuleException("Un type de DMC porte déjà le code « " + dto.getCode() + " ».");
        }
        existing.setCode(dto.getCode());
        existing.setLibelle(dto.getLibelle());
        existing.setActif(dto.isActif());
        return TypeDmcMapper.toDto(repository.save(existing));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Type de DMC introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
