package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ChangementLigne;

/**
 * Trace figée du diff d'une version de PPM (append-only, cf. {@link ChangementLigne}).
 */
@Repository
public interface ChangementLigneRepository extends JpaRepository<ChangementLigne, Integer> {

    /** Changements portés par une version, dans l'ordre d'enregistrement. */
    List<ChangementLigne> findByIdDossierOrderByIdChangementAsc(Integer idDossier);

    /** Vrai si le diff de cette version a déjà été figé (garde d'idempotence à la soumission). */
    boolean existsByIdDossier(Integer idDossier);

    /** Historique complet d'UNE ligne à travers toutes les versions où elle a bougé. */
    List<ChangementLigne> findByIdLigneOrigineOrderByIdChangementAsc(Integer idLigneOrigine);

    /**
     * Prochaine PK de ligne de changement, allouée par la séquence serveur (Voie B).
     *
     * <p>Remplace un {@code max(ID_CHANGEMENT) + 1}. À consommer <strong>une fois par ligne</strong> :
     * la trace d'une mise à jour de PPM en écrit une par champ modifié, donc des dizaines d'affilée.
     */
    @Query(value = "select nextval('seq_changement_ligne')", nativeQuery = true)
    Long nextIdChangement();
}
