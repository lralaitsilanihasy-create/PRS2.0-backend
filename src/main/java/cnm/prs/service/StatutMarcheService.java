package cnm.prs.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.StatutMarcheDto;
import cnm.prs.entity.StatutMarche;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.StatutMarcheMapper;
import cnm.prs.repository.StatutMarcheRepository;

/**
 * ⚠️ <strong>Référentiel « Statut de marché »</strong> (demande pilote du 2026-09-09) — CRUD sur le
 * moule des natures et des modes de passation, plus la <strong>résolution du code</strong> que toute
 * écriture de marché traverse.
 *
 * <p>{@code t_marche.STATUT} était un texte libre — toujours {@code PREVU} en pratique, mais rien ne
 * l'imposait et l'Administrateur ne pouvait pas en ajouter. Le code est désormais validé à l'écriture, et
 * la liste des valeurs lui appartient.</p>
 */
@Service
@Transactional
public class StatutMarcheService {

    /**
     * ⚠️ <strong>Le statut par défaut</strong>, appliqué quand l'écriture d'un marché n'en dit rien.
     *
     * <p>C'est {@code PREVU} : le code que <em>toutes</em> les lignes existantes portent déjà, et celui
     * que la façade de saisie posait en dur. La demande proposait d'en faire un drapeau administrable ;
     * ce serait une colonne de plus <strong>et</strong> un invariant à tenir (exactement un défaut, jamais
     * zéro), pour désigner autre chose qu'un plan « prévu » — cas qui ne s'est pas présenté. Le défaut
     * reste donc un code connu, et sa <strong>suppression est refusée</strong> : c'est ce qui garantit
     * qu'il désigne toujours une valeur du référentiel.</p>
     */
    public static final String CODE_DEFAUT = "PREVU";

    private final StatutMarcheRepository repository;

    public StatutMarcheService(StatutMarcheRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<StatutMarcheDto> findAll() {
        return repository.findAllByOrderByOrdreAscCodeAsc().stream().map(StatutMarcheMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public StatutMarcheDto findById(String code) {
        return StatutMarcheMapper.toDto(charger(code));
    }

    public StatutMarcheDto create(StatutMarcheDto dto) {
        String code = dto.getCode() == null ? null : dto.getCode().trim();
        // ⚠️ LOT 3b — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(code, repository::existsById, "statut de marché");
        return StatutMarcheMapper.toDto(repository.save(StatutMarcheMapper.toEntity(dto)));
    }

    /** Le <strong>code ne se renomme pas</strong> : c'est la clé, et les marchés la portent déjà. */
    public StatutMarcheDto update(String code, StatutMarcheDto dto) {
        StatutMarche existing = charger(code);
        existing.setLibelle(dto.getLibelle() == null ? null : dto.getLibelle().trim());
        existing.setOrdre(dto.getOrdre());
        if (dto.getActif() != null) {
            existing.setActif(dto.getActif());
        }
        return StatutMarcheMapper.toDto(repository.save(existing));
    }

    /**
     * Suppression d'un statut. ⚠️ Le <strong>défaut est indestructible</strong> : le supprimer laisserait
     * les écritures sans valeur de repli, et un marché sans statut valide — un référentiel qu'on peut
     * vider de sa valeur pivot cesse d'en être un.
     */
    public void delete(String code) {
        StatutMarche existing = charger(code);
        if (CODE_DEFAUT.equalsIgnoreCase(existing.getCode())) {
            throw new BusinessRuleException("Le statut « " + CODE_DEFAUT + " » ne peut pas être supprimé : "
                    + "c'est celui qu'un marché reçoit à défaut. Désactivez-le si vous ne voulez plus "
                    + "qu'il soit proposé.");
        }
        repository.deleteById(existing.getCode());
    }

    /**
     * ⚠️ <strong>Résolution du statut d'un marché à l'écriture</strong> — le point de passage unique des
     * trois voies (endpoints granulaires, façade de saisie, rectification).
     *
     * <p>Absent ou vide, le statut vaut {@link #CODE_DEFAUT} : omettre n'est pas se tromper, et la
     * saisie posait déjà ce code en dur. Renseigné mais <strong>inconnu du référentiel</strong>, il est
     * refusé en <strong>400</strong> — et le refus <em>énumère les valeurs possibles</em>, faute de quoi
     * l'appelant n'a aucun moyen de savoir ce qu'on attend de lui.</p>
     *
     * <p>⚠️ Un statut <strong>désactivé</strong> reste accepté : il n'est plus proposé, mais des marchés
     * le portent, et refuser leur code interdirait de les ré-enregistrer. La désactivation guide la
     * saisie ; elle ne réécrit pas l'histoire.</p>
     */
    @Transactional(readOnly = true)
    public String normaliser(String code) {
        String demande = code == null ? "" : code.trim();
        if (demande.isEmpty()) {
            return CODE_DEFAUT;
        }
        if (repository.existsById(demande)) {
            return demande;
        }
        String possibles = repository.findByActifTrueOrderByOrdreAscCodeAsc().stream()
                .map(StatutMarche::getCode).collect(Collectors.joining(", "));
        throw new BadRequestException("Statut de marché inconnu : « " + demande + " »."
                + (possibles.isBlank() ? "" : " Valeurs possibles : " + possibles + "."));
    }

    private StatutMarche charger(String code) {
        return repository.findById(code == null ? "" : code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Statut de marché introuvable : " + code));
    }
}
