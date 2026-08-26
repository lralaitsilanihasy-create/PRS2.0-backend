package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PublicationDto;
import cnm.prs.entity.Publication;
import cnm.prs.enums.StatutPublication;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PublicationMapper;
import cnm.prs.repository.PublicationRepository;
import cnm.prs.security.CurrentUser;

/**
 * Logique métier pour {@link Publication} — portail de transparence (§3.7).
 *
 * <p>Le statut ({@code STATUT_PUBLI}) et les dates de publication/retrait ne sont pilotés que
 * par les transitions dédiées ({@link #publier}, {@link #retirer}) ; le compteur de
 * consultations par {@link #consulter}.</p>
 */
@Service
@Transactional
public class PublicationService {

    private final PublicationRepository repository;

    public PublicationService(PublicationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PublicationDto> findAll() {
        return repository.findAll().stream().map(PublicationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PublicationDto findById(Integer id) {
        return PublicationMapper.toDto(load(id));
    }

    /** Création d'une publication : démarre toujours en {@link StatutPublication#EN_ATTENTE}. */
    public PublicationDto create(PublicationDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdPublication(), repository::existsById, "publication");
        Publication entity = PublicationMapper.toEntity(dto);
        entity.setStatutPubli(StatutPublication.EN_ATTENTE.name());
        entity.setNbConsultations(0);
        entity.setDatePublication(null);
        entity.setImPubliePar(null);
        entity.setDateRetrait(null);
        entity.setMotifRetrait(null);
        return PublicationMapper.toDto(repository.save(entity));
    }

    /** Mise à jour de l'objet publié uniquement ; statut/dates pilotés par les transitions. */
    public PublicationDto update(Integer id, PublicationDto dto) {
        Publication existing = load(id);
        existing.setTypeObjet(dto.getTypeObjet());
        existing.setIdObjet(dto.getIdObjet());
        return PublicationMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Publication introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /** Publication : EN_ATTENTE → PUBLIE (§3.7). Horodate et enregistre l'auteur. */
    public PublicationDto publier(Integer id) {
        Publication p = load(id);
        requireStatut(p, StatutPublication.EN_ATTENTE);
        p.setStatutPubli(StatutPublication.PUBLIE.name());
        p.setDatePublication(LocalDateTime.now());
        CurrentUser.ref().filter(ref -> ref.length() <= 7).ifPresent(p::setImPubliePar);
        return PublicationMapper.toDto(repository.save(p));
    }

    /** Retrait documenté : PUBLIE → RETIRE avec motif obligatoire (§3.7). */
    public PublicationDto retirer(Integer id, String motif) {
        Publication p = load(id);
        requireStatut(p, StatutPublication.PUBLIE);
        if (motif == null || motif.isBlank()) {
            throw new BusinessRuleException("Le motif de retrait est obligatoire (§3.7).");
        }
        p.setStatutPubli(StatutPublication.RETIRE.name());
        p.setDateRetrait(LocalDate.now());
        p.setMotifRetrait(motif);
        return PublicationMapper.toDto(repository.save(p));
    }

    /** Incrémente le compteur de consultations (§3.7). */
    public PublicationDto consulter(Integer id) {
        Publication p = load(id);
        p.setNbConsultations(p.getNbConsultations() == null ? 1 : p.getNbConsultations() + 1);
        return PublicationMapper.toDto(repository.save(p));
    }

    private Publication load(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publication introuvable : " + id));
    }

    private void requireStatut(Publication p, StatutPublication attendu) {
        if (!attendu.name().equals(p.getStatutPubli())) {
            throw new BusinessRuleException("Action impossible : la publication est au statut « "
                    + p.getStatutPubli() + " », attendu « " + attendu.name() + " ».");
        }
    }
}
