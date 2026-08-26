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

    /** Prochaine PK allouee par la sequence serveur {@code seq_changement_ligne} (allocation atomique). */
    @Query(value = "select nextval('seq_changement_ligne')", nativeQuery = true)
    Long nextIdChangement();
}
