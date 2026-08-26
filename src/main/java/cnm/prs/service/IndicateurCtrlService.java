package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.IndicateurCtrlDto;
import cnm.prs.entity.IndicateurCtrl;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.IndicateurCtrlMapper;
import cnm.prs.repository.IndicateurCtrlRepository;

/**
 * Logique métier pour {@link IndicateurCtrl}.
 */
@Service
@Transactional
public class IndicateurCtrlService {

    private final IndicateurCtrlRepository repository;

    public IndicateurCtrlService(IndicateurCtrlRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<IndicateurCtrlDto> findAll() {
        return repository.findAll().stream().map(IndicateurCtrlMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public IndicateurCtrlDto findById(Integer id) {
        IndicateurCtrl entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IndicateurCtrl introuvable : " + id));
        return IndicateurCtrlMapper.toDto(entity);
    }

    public IndicateurCtrlDto create(IndicateurCtrlDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdIndicateur(), repository::existsById, "indicateur de contrôle");
        IndicateurCtrl entity = IndicateurCtrlMapper.toEntity(dto);
        return IndicateurCtrlMapper.toDto(repository.save(entity));
    }

    public IndicateurCtrlDto update(Integer id, IndicateurCtrlDto dto) {
        IndicateurCtrl existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IndicateurCtrl introuvable : " + id));
        existing.setImControleur(dto.getImControleur());
        existing.setPeriode(dto.getPeriode());
        existing.setNbExamens(dto.getNbExamens());
        existing.setNbConformes(dto.getNbConformes());
        existing.setDelaiMoyenExamen(dto.getDelaiMoyenExamen());
        existing.setNbObsEmises(dto.getNbObsEmises());
        return IndicateurCtrlMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("IndicateurCtrl introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
