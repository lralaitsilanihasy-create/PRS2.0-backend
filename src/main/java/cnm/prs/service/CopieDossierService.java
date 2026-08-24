package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CopieDossierDto;
import cnm.prs.entity.CopieDossier;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.CopieDossierMapper;
import cnm.prs.repository.CopieDossierRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link CopieDossier}.
 *
 * <p>⚠️ Correction de périmètre — la copie de dossier est une pièce du <strong>circuit interne</strong>
 * (§3.3, {@code TYPE_COPIE = DISPATCH_CC}) : son périmètre est celui de son dossier
 * ({@code t_dossier.ID_LOCALITE}), suivant le motif habituel (§1) — Président/Administrateur voient
 * tout, un contrôleur voit sa localité, la PRMP (acteur externe) ne voit rien. Auparavant ce service
 * faisait {@code repository.findAll()} nu : la PRMP lisait à qui le CNM avait transmis copie de quel
 * dossier, dans toutes les localités — la cartographie du circuit interne, matricules compris.</p>
 */
@Service
@Transactional
public class CopieDossierService {

    private final CopieDossierRepository repository;

    public CopieDossierService(CopieDossierRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CopieDossierDto> findAll() {
        return Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(CopieDossierMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CopieDossierDto findById(Integer id) {
        CopieDossier entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CopieDossier introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return CopieDossierMapper.toDto(entity);
    }

    public CopieDossierDto create(CopieDossierDto dto) {
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
