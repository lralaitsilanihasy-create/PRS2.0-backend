package cnm.prs.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PrmpEntiteDto;
import cnm.prs.entity.PrmpEntite;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PrmpEntiteMapper;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier des affectations PRMP ↔ entité contractante ({@code t_prmp_entite}, §3.1).
 *
 * <p>Une PRMP gère plusieurs entités contractantes ; chaque entité n'est rattachée qu'à
 * <strong>une seule PRMP active</strong> (invariant d'unicité). Les écritures sont réservées à
 * l'Administrateur (cf. {@link cnm.prs.controller.PrmpEntiteController}) ; la lecture est
 * <strong>scopée</strong> : l'Administrateur voit tout, une PRMP ne voit que ses propres entités.</p>
 */
@Service
@Transactional
public class PrmpEntiteService {

    private final PrmpEntiteRepository repository;
    private final PrmpRepository prmpRepository;
    private final EntiteContractRepository entiteContractRepository;

    public PrmpEntiteService(PrmpEntiteRepository repository, PrmpRepository prmpRepository,
            EntiteContractRepository entiteContractRepository) {
        this.repository = repository;
        this.prmpRepository = prmpRepository;
        this.entiteContractRepository = entiteContractRepository;
    }

    /**
     * Liste <strong>scopée</strong> au périmètre de l'appelant (§1, §3.1) : Président/Administrateur
     * voient toutes les affectations ; la PRMP ne voit que les siennes ({@code ID_PRMP}) ; tout autre
     * profil → liste vide (les affectations sont une affaire Administrateur/PRMP).
     */
    @Transactional(readOnly = true)
    public List<PrmpEntiteDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(PrmpEntiteMapper::toDto).toList();
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            return idPrmp == null ? List.of()
                    : repository.findByIdPrmp(idPrmp).stream().map(PrmpEntiteMapper::toDto).toList();
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public PrmpEntiteDto findById(Integer id) {
        PrmpEntite entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrmpEntite introuvable : " + id));
        controlerVisibilite(entity);
        return PrmpEntiteMapper.toDto(entity);
    }

    /** Vérifie que l'affectation est dans le périmètre de l'appelant (§3.1) — sinon 403. */
    private void controlerVisibilite(PrmpEntite entity) {
        if (Visibilite.voitTout()) {
            return;
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            if (idPrmp != null && idPrmp.equals(entity.getIdPrmp())) {
                return;
            }
        }
        throw new AccessDeniedException("Affectation hors de votre périmètre (§3.1).");
    }

    /**
     * Crée une affectation PRMP↔entité (écriture réservée à l'Administrateur). La PRMP et l'entité
     * doivent exister (sinon 400) ; l'entité ne doit pas être déjà rattachée à une PRMP active
     * (invariant d'unicité, sinon 409). L'affectation est créée active, à la date du jour par défaut.
     */
    public PrmpEntiteDto create(PrmpEntiteDto dto) {
        if (!prmpRepository.existsById(dto.getIdPrmp())) {
            throw new BadRequestException("PRMP introuvable : " + dto.getIdPrmp() + ".");
        }
        if (!entiteContractRepository.existsById(dto.getIdEntiteContract())) {
            throw new BadRequestException("Entité contractante introuvable : " + dto.getIdEntiteContract() + ".");
        }
        repository.findByIdEntiteContractAndActifTrue(dto.getIdEntiteContract()).ifPresent(existante -> {
            throw new BusinessRuleException("L'entité " + dto.getIdEntiteContract()
                    + " est déjà rattachée à la PRMP " + existante.getIdPrmp() + " (§3.1).");
        });
        PrmpEntite entity = new PrmpEntite();
        entity.setIdPrmpEntite(repository.findMaxId() + 1);
        entity.setIdPrmp(dto.getIdPrmp());
        entity.setIdEntiteContract(dto.getIdEntiteContract());
        entity.setDateAffectation(dto.getDateAffectation() != null ? dto.getDateAffectation() : LocalDate.now());
        entity.setActif(Boolean.TRUE);
        return PrmpEntiteMapper.toDto(repository.save(entity));
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-26) — AUTO-RATTACHEMENT <strong>EN ATTENTE</strong> ({@code actif=false}) créé
     * lorsqu'une <strong>PRMP</strong> crée une entité contractante à l'import PPM (autorité hors périmètre).
     * Ne s'exécute que si l'appelant est une PRMP <strong>enregistrée</strong> ({@code CurrentUser.ref()} présent
     * dans {@code t_prmp}) — un Administrateur/contrôleur ne déclenche aucun rattachement. N'applique
     * <strong>pas</strong> l'invariant d'unicité des liens <em>actifs</em> (le lien est en attente) ; celui-ci
     * s'applique à l'<strong>activation</strong> ({@link #update} avec {@code actif=true} → 409 si conflit).
     * Idempotent : ne recrée pas un lien PRMP↔entité déjà présent.
     */
    public void autoRattacherEnAttenteSiPrmp(Integer idEntiteContract) {
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (idPrmp == null || !prmpRepository.existsById(idPrmp)) {
            return;   // pas une PRMP enregistrée (Admin/contrôleur) → aucun auto-rattachement
        }
        if (repository.existsByIdPrmpAndIdEntiteContract(idPrmp, idEntiteContract)) {
            return;   // lien déjà présent (idempotent en cas de re-création)
        }
        PrmpEntite lien = new PrmpEntite();
        lien.setIdPrmpEntite(repository.findMaxId() + 1);
        lien.setIdPrmp(idPrmp);
        lien.setIdEntiteContract(idEntiteContract);
        lien.setDateAffectation(LocalDate.now());
        lien.setActif(Boolean.FALSE);   // EN ATTENTE d'approbation Administrateur
        repository.save(lien);
    }

    /**
     * Met à jour une affectation (écriture Administrateur). Toute (ré)activation respecte l'invariant
     * d'unicité : si une autre affectation active existe déjà pour la même entité → 409.
     */
    public PrmpEntiteDto update(Integer id, PrmpEntiteDto dto) {
        PrmpEntite existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrmpEntite introuvable : " + id));
        if (Boolean.TRUE.equals(dto.getActif())) {
            repository.findByIdEntiteContractAndActifTrue(dto.getIdEntiteContract())
                    .filter(autre -> !autre.getIdPrmpEntite().equals(id))
                    .ifPresent(autre -> {
                        throw new BusinessRuleException("L'entité " + dto.getIdEntiteContract()
                                + " est déjà rattachée à la PRMP " + autre.getIdPrmp() + " (§3.1).");
                    });
        }
        existing.setIdPrmp(dto.getIdPrmp());
        existing.setIdEntiteContract(dto.getIdEntiteContract());
        existing.setDateAffectation(dto.getDateAffectation());
        existing.setActif(dto.getActif());
        return PrmpEntiteMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("PrmpEntite introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
