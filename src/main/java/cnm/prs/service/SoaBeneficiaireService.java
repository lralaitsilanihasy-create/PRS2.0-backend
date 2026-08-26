package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SoaBeneficiaireDto;
import cnm.prs.entity.SoaBeneficiaire;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.SoaBeneficiaireMapper;
import cnm.prs.repository.SoaBeneficiaireRepository;

/**
 * Logique métier pour {@link SoaBeneficiaire}.
 */
@Service
@Transactional
public class SoaBeneficiaireService {

    private final SoaBeneficiaireRepository repository;

    public SoaBeneficiaireService(SoaBeneficiaireRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SoaBeneficiaireDto> findAll() {
        return repository.findAll().stream().map(SoaBeneficiaireMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SoaBeneficiaireDto findById(String id) {
        SoaBeneficiaire entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SoaBeneficiaire introuvable : " + id));
        return SoaBeneficiaireMapper.toDto(entity);
    }

    public SoaBeneficiaireDto create(SoaBeneficiaireDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getSoaCode(), repository::existsById, "service bénéficiaire SOA");
        SoaBeneficiaire entity = SoaBeneficiaireMapper.toEntity(dto);
        return SoaBeneficiaireMapper.toDto(repository.save(entity));
    }

    public SoaBeneficiaireDto update(String id, SoaBeneficiaireDto dto) {
        SoaBeneficiaire existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SoaBeneficiaire introuvable : " + id));
        existing.setLibelle(dto.getLibelle());
        return SoaBeneficiaireMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("SoaBeneficiaire introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
