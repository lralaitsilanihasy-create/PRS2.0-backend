package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PrmpDto;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PrmpMapper;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.IndicateurPrmpRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * Logique métier pour {@link Prmp}.
 */
@Service
@Transactional
public class PrmpService {

    private final PrmpRepository repository;
    private final CompteAuthRepository compteRepository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final PrmpEntiteRepository prmpEntiteRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final IndicateurPrmpRepository indicateurPrmpRepository;
    private final UgpmRepository ugpmRepository;

    public PrmpService(PrmpRepository repository, CompteAuthRepository compteRepository,
            DossierRepository dossierRepository, PpmRepository ppmRepository,
            PrmpEntiteRepository prmpEntiteRepository, DemandeRetraitRepository demandeRetraitRepository,
            IndicateurPrmpRepository indicateurPrmpRepository, UgpmRepository ugpmRepository) {
        this.repository = repository;
        this.compteRepository = compteRepository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.prmpEntiteRepository = prmpEntiteRepository;
        this.demandeRetraitRepository = demandeRetraitRepository;
        this.indicateurPrmpRepository = indicateurPrmpRepository;
        this.ugpmRepository = ugpmRepository;
    }

    @Transactional(readOnly = true)
    public List<PrmpDto> findAll() {
        return repository.findAll().stream().map(PrmpMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PrmpDto findById(String id) {
        Prmp entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prmp introuvable : " + id));
        return PrmpMapper.toDto(entity);
    }

    public PrmpDto create(PrmpDto dto) {
        Prmp entity = PrmpMapper.toEntity(dto);
        return PrmpMapper.toDto(repository.save(entity));
    }

    public PrmpDto update(String id, PrmpDto dto) {
        Prmp existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prmp introuvable : " + id));
        existing.setNomPrmp(dto.getNomPrmp());
        existing.setPrenomsPrmp(dto.getPrenomsPrmp());
        existing.setArreteNomin(dto.getArreteNomin());
        existing.setDateNomin(dto.getDateNomin());
        existing.setCin(dto.getCin());
        existing.setDateCin(dto.getDateCin());
        existing.setLieuCin(dto.getLieuCin());
        existing.setEmailPrmp(dto.getEmailPrmp());
        existing.setTelPrmp(dto.getTelPrmp());
        return PrmpMapper.toDto(repository.save(existing));
    }

    /**
     * Supprime une PRMP et son compte d'authentification. <strong>Garde</strong> : refuse (409) tant que la PRMP
     * porte des données liées (dossiers, PPM, entités rattachées, demandes de retrait, indicateurs, UGPM de
     * tutelle) — pour éviter une perte massive et les violations de FK.
     */
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Prmp introuvable : " + id);
        }
        if (dossierRepository.existsByIdPrmp(id)
                || ppmRepository.countByIdPrmp(id) > 0
                || !prmpEntiteRepository.findByIdPrmp(id).isEmpty()
                || !demandeRetraitRepository.findByIdPrmp(id).isEmpty()
                || indicateurPrmpRepository.existsByIdPrmp(id)
                || !ugpmRepository.findByIdPrmpTutelle(id).isEmpty()) {
            throw new BusinessRuleException("Suppression impossible : la PRMP « " + id + " » a des données liées "
                    + "(dossiers, PPM, entités rattachées, demandes de retrait, indicateurs ou UGPM). "
                    + "Retirez d'abord ces éléments.");
        }
        // Compte d'authentification créé à l'inscription (REF_ACTEUR = idPrmp).
        compteRepository.deleteAll(compteRepository.findByRefActeurAndTypeActeur(id, TypeActeur.PRMP.name()));
        repository.deleteById(id);
    }
}
