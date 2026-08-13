package cnm.prs.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CapmDto;
import cnm.prs.entity.Capm;
import cnm.prs.entity.ModePassation;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.CapmMapper;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.ModePassationRepository;

/**
 * Logique métier pour {@link Capm} (référentiel des processus de marché).
 */
@Service
@Transactional
public class CapmService {

    private final CapmRepository repository;
    private final ModePassationRepository modePassationRepository;

    public CapmService(CapmRepository repository, ModePassationRepository modePassationRepository) {
        this.repository = repository;
        this.modePassationRepository = modePassationRepository;
    }

    @Transactional(readOnly = true)
    public List<CapmDto> findAll() {
        return findAll(null);
    }

    /**
     * ⚠️ Règle ajoutée — grille EFFECTIVE par mode de passation (modèle mixte, comme les points de
     * contrôle par sous-type) : {@code mode} fourni → les processus SPÉCIFIQUES à ce mode s'ils
     * existent, sinon ceux du <strong>mode modèle partagé</strong> ({@code tr_mode_passation
     * .ID_MODE_MODELE_CAPM}, ex. CPO/AMI → modèle « Appel d'offres ouvert »), sinon les processus
     * COMMUNS ({@code idMode} null) ; {@code mode} absent → tout le référentiel. Trié par {@code ordre} ASC.
     */
    @Transactional(readOnly = true)
    public List<CapmDto> findAll(Integer mode) {
        List<Capm> tous = repository.findAll();
        List<Capm> retenus;
        if (mode == null) {
            retenus = tous;
        } else {
            List<Capm> specifiques = tous.stream().filter(c -> Objects.equals(c.getIdMode(), mode)).toList();
            if (specifiques.isEmpty()) {
                Integer modele = modePassationRepository.findById(mode)
                        .map(ModePassation::getIdModeModeleCapm).orElse(null);
                if (modele != null) {
                    specifiques = tous.stream().filter(c -> Objects.equals(c.getIdMode(), modele)).toList();
                }
            }
            retenus = specifiques.isEmpty() ? tous.stream().filter(c -> c.getIdMode() == null).toList() : specifiques;
        }
        return retenus.stream()
                .sorted(Comparator.comparingInt(c -> c.getOrdre() == null ? 0 : c.getOrdre()))
                .map(CapmMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CapmDto findById(Integer id) {
        Capm entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processus (CAPM) introuvable : " + id));
        return CapmMapper.toDto(entity);
    }

    public CapmDto create(CapmDto dto) {
        Capm entity = CapmMapper.toEntity(dto);
        return CapmMapper.toDto(repository.save(entity));
    }

    public CapmDto update(Integer id, CapmDto dto) {
        Capm existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processus (CAPM) introuvable : " + id));
        existing.setLibelleProcessus(dto.getLibelleProcessus());
        existing.setOrdre(dto.getOrdre());
        existing.setIdMode(dto.getIdMode());
        existing.setGroupe(dto.getGroupe());
        return CapmMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Processus (CAPM) introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
