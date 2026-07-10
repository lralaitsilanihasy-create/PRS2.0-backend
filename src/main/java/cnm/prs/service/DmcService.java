package cnm.prs.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DmcDto;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.StatutDmc;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DmcMapper;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.TypeDmcRepository;

/**
 * Dossier de mise en concurrence (DMC) : création <strong>par ligne de marché</strong> avec type
 * <strong>dérivé du mode de passation</strong> (mapping en base, pas d'enum codé en dur). Service
 * dédié, non câblé automatiquement sur la saisie/soumission (déclenchement explicite).
 */
@Service
@Transactional
public class DmcService {

    private final DossierMecRepository repository;
    private final MarcheRepository marcheRepository;
    private final ModePassationRepository modeRepository;
    private final TypeDmcRepository typeDmcRepository;

    public DmcService(DossierMecRepository repository, MarcheRepository marcheRepository,
            ModePassationRepository modeRepository, TypeDmcRepository typeDmcRepository) {
        this.repository = repository;
        this.marcheRepository = marcheRepository;
        this.modeRepository = modeRepository;
        this.typeDmcRepository = typeDmcRepository;
    }

    /**
     * Crée le DMC d'une ligne de marché, son type dérivé du mode de passation. <strong>400</strong> si
     * le mode n'est pas mappé à un type actif (message de configuration) ; <strong>409</strong> si la
     * ligne a déjà un DMC (relation 1-1).
     */
    public DmcDto creerPourMarche(Integer idDetail) {
        Marche marche = marcheRepository.findById(idDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Marché introuvable : " + idDetail));
        if (repository.existsByIdDetail(idDetail)) {
            throw new BusinessRuleException("La ligne de marché " + idDetail + " a déjà un DMC.");
        }
        TypeDmc type = resoudreType(marche);

        DossierMec dmc = new DossierMec();
        dmc.setIdDetail(idDetail);
        dmc.setIdTypeDmc(type.getIdTypeDmc());
        dmc.setStatut(StatutDmc.A_PREPARER);
        dmc.setDateCreation(LocalDateTime.now());
        DossierMec saved = repository.save(dmc);
        saved.setTypeDmc(type);   // pour l'affichage code/libellé (association lecture seule)
        return DmcMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public DmcDto findByMarche(Integer idDetail) {
        return DmcMapper.toDto(repository.findByIdDetail(idDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun DMC pour la ligne de marché : " + idDetail)));
    }

    @Transactional(readOnly = true)
    public DmcDto findById(Long idDmc) {
        return DmcMapper.toDto(repository.findById(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc)));
    }

    /**
     * Re-dérive le type du DMC d'un marché <strong>si</strong> il existe et est encore {@code A_PREPARER}
     * (appelé au changement de mode de passation). Si le nouveau mode n'est pas mappé, le DMC est laissé
     * inchangé (on ne bloque pas la modification du marché).
     */
    public void reAffecterTypeSiApreparer(Integer idDetail) {
        repository.findByIdDetail(idDetail).ifPresent(dmc -> {
            if (dmc.getStatut() != StatutDmc.A_PREPARER) {
                return;
            }
            Marche marche = marcheRepository.findById(idDetail).orElse(null);
            if (marche == null || marche.getIdMode() == null) {
                return;
            }
            ModePassation mode = modeRepository.findById(marche.getIdMode()).orElse(null);
            TypeDmc type = typeDuMode(mode);
            if (type != null && type.isActif()) {
                dmc.setIdTypeDmc(type.getIdTypeDmc());
                dmc.setTypeDmc(type);   // garde l'association en phase avec la colonne (évite une lecture stale)
                repository.save(dmc);
            }
        });
    }

    /** Supprime le DMC d'un marché (cascade applicative à la suppression du marché). */
    public void supprimerPourMarche(Integer idDetail) {
        repository.deleteByIdDetail(idDetail);
    }

    private TypeDmc resoudreType(Marche marche) {
        ModePassation mode = marche.getIdMode() == null ? null
                : modeRepository.findById(marche.getIdMode()).orElse(null);
        TypeDmc type = typeDuMode(mode);
        if (type == null || !type.isActif()) {
            String mode0 = mode != null ? mode.getLibelle() : String.valueOf(marche.getIdMode());
            throw new BadRequestException("Aucun type de DMC actif n'est mappé au mode de passation « " + mode0
                    + " ». Configurez le mapping en administration (mode de passation → type de DMC).");
        }
        return type;
    }

    /**
     * Type de DMC mappé à un mode de passation, résolu via la <strong>colonne</strong> {@code ID_TYPE_DMC}
     * (et non l'association read-only, non renseignée quand seule la colonne est modifiée). {@code null} si
     * mode absent ou non mappé.
     */
    private TypeDmc typeDuMode(ModePassation mode) {
        if (mode == null || mode.getIdTypeDmc() == null) {
            return null;
        }
        return typeDmcRepository.findById(mode.getIdTypeDmc()).orElse(null);
    }
}
