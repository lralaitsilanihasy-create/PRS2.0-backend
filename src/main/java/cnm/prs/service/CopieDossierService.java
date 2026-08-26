package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CopieDossierDto;
import cnm.prs.entity.CopieDossier;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.CopieDossierMapper;
import cnm.prs.repository.CopieDossierRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link CopieDossier}.
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1 : CRUD auparavant sans aucune garde. La copie de dossier est une
 * pièce <strong>interne</strong> du circuit, créée par {@code DispatchService} au moment du dispatch.
 * Lecture bornée à la localité du contrôleur (Président/Administrateur : tout ; PRMP : rien) ;
 * écriture générique réservée à l'Administrateur ({@code @PreAuthorize} sur le contrôleur).</p>
 */
@Service
@Transactional
public class CopieDossierService {

    private final CopieDossierRepository repository;
    private final DossierRepository dossierRepository;

    public CopieDossierService(CopieDossierRepository repository, DossierRepository dossierRepository) {
        this.repository = repository;
        this.dossierRepository = dossierRepository;
    }

    @Transactional(readOnly = true)
    public List<CopieDossierDto> findAll() {
        return Visibilite.filtrer(repository::findAll,
                        loc -> repository.findByIdDossierIn(dossierRepository.findIdsVisiblesParLocalite(loc)))
                .stream().map(CopieDossierMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CopieDossierDto findById(Integer id) {
        CopieDossier entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CopieDossier introuvable : " + id));
        Visibilite.controler(loc -> dossierRepository.existsDansLocalite(entity.getIdDossier(), loc));
        return CopieDossierMapper.toDto(entity);
    }

    public CopieDossierDto create(CopieDossierDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdCopie(), repository::existsById, "copie de dossier");
        CopieDossier entity = CopieDossierMapper.toEntity(dto);
        return CopieDossierMapper.toDto(repository.save(entity));
    }

    public CopieDossierDto update(Integer id, CopieDossierDto dto) {
        CopieDossier existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CopieDossier introuvable : " + id));
        existing.setIdDispatch(dto.getIdDispatch());
        existing.setIdDossier(dto.getIdDossier());
        existing.setImDestinataire(dto.getImDestinataire());
        existing.setTypeCopie(dto.getTypeCopie());
        existing.setDateTransmission(dto.getDateTransmission());
        existing.setAccuseReception(dto.getAccuseReception());
        existing.setDateAccuse(dto.getDateAccuse());
        existing.setObservation(dto.getObservation());
        return CopieDossierMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("CopieDossier introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
