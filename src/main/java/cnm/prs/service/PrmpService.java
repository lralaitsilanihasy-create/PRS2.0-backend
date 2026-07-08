package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PrmpDto;
import cnm.prs.dto.SuppressionLotPrmpResult;
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

    /**
     * PRMP rattachées à une localité <strong>via leurs entités contractantes actives</strong> (la PRMP n'a pas
     * de localité propre). Liste distincte, éventuellement vide.
     */
    @Transactional(readOnly = true)
    public List<PrmpDto> findByLocalite(String idLocalite) {
        return repository.findByLocaliteViaEntitesActives(idLocalite).stream().map(PrmpMapper::toDto).toList();
    }

    /**
     * PRMP rattachée à une entité contractante via son affectation active (0 ou 1, invariant une seule PRMP
     * active par entité). Liste, vide si aucune.
     */
    @Transactional(readOnly = true)
    public List<PrmpDto> findByEntite(Integer idEntiteContract) {
        return repository.findByEntiteViaAffectationActive(idEntiteContract).stream().map(PrmpMapper::toDto).toList();
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
        if (aDesDonneesLiees(id)) {
            throw new BusinessRuleException("Suppression impossible : la PRMP « " + id + " » a des données liées "
                    + "(dossiers, PPM, entités rattachées, demandes de retrait, indicateurs ou UGPM). "
                    + "Retirez d'abord ces éléments.");
        }
        supprimerUne(id);
    }

    /**
     * Suppression <strong>en lot</strong> par matricule, <strong>tolérante</strong> : supprime chaque PRMP
     * existante <em>sans données liées</em> (avec son compte) ; les absents vont dans {@code introuvables}, les
     * PRMP à données liées dans {@code bloques} (comme le 409 unitaire). Jamais d'échec global. Doublons ignorés.
     */
    public SuppressionLotPrmpResult supprimerLot(List<String> matricules) {
        List<String> supprimes = new ArrayList<>();
        List<String> introuvables = new ArrayList<>();
        List<String> bloques = new ArrayList<>();
        for (String id : matricules.stream().distinct().toList()) {
            if (!repository.existsById(id)) {
                introuvables.add(id);
            } else if (aDesDonneesLiees(id)) {
                bloques.add(id);
            } else {
                supprimerUne(id);
                supprimes.add(id);
            }
        }
        return new SuppressionLotPrmpResult(supprimes, introuvables, bloques);
    }

    /** Vrai si la PRMP porte des données liées (garde de suppression). */
    private boolean aDesDonneesLiees(String id) {
        return dossierRepository.existsByIdPrmp(id)
                || ppmRepository.countByIdPrmp(id) > 0
                || !prmpEntiteRepository.findByIdPrmp(id).isEmpty()
                || !demandeRetraitRepository.findByIdPrmp(id).isEmpty()
                || indicateurPrmpRepository.existsByIdPrmp(id)
                || !ugpmRepository.findByIdPrmpTutelle(id).isEmpty();
    }

    /** Supprime une PRMP et son compte associé (sans contrôle — appelé après vérification existence + garde). */
    private void supprimerUne(String id) {
        compteRepository.deleteAll(compteRepository.findByRefActeurAndTypeActeur(id, TypeActeur.PRMP.name()));
        repository.deleteById(id);
    }
}
