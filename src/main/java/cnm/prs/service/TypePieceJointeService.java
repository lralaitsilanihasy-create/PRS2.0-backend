package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.TypePieceJointeDto;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.TypePieceJointeMapper;
import cnm.prs.repository.TypePieceJointeRepository;

/**
 * Logique métier pour {@link TypePieceJointe} (référentiel des pièces jointes par type de dossier).
 */
@Service
@Transactional
public class TypePieceJointeService {

    private final TypePieceJointeRepository repository;

    public TypePieceJointeService(TypePieceJointeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TypePieceJointeDto> findAll(String typeDossier) {
        List<TypePieceJointe> list = (typeDossier == null || typeDossier.isBlank())
                ? repository.findAll()
                : repository.findByIdTypeDossierOrderByOrdreAsc(typeDossier);
        return list.stream().map(TypePieceJointeMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TypePieceJointeDto findById(Integer id) {
        TypePieceJointe entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de pièce introuvable : " + id));
        return TypePieceJointeMapper.toDto(entity);
    }

    public TypePieceJointeDto create(TypePieceJointeDto dto) {
        TypePieceJointe entity = TypePieceJointeMapper.toEntity(dto);
        entity.setIdTypePiece(null);   // PK auto (IDENTITY) ; tout id fourni est ignoré
        return TypePieceJointeMapper.toDto(repository.save(entity));
    }

    public TypePieceJointeDto update(Integer id, TypePieceJointeDto dto) {
        TypePieceJointe existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de pièce introuvable : " + id));
        existing.setLibellePiece(dto.getLibellePiece());
        existing.setCode(dto.getCode());
        existing.setObligatoire(dto.getObligatoire());
        existing.setIdTypeDossier(dto.getIdTypeDossier());
        existing.setOrdre(dto.getOrdre());
        return TypePieceJointeMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Type de pièce introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
