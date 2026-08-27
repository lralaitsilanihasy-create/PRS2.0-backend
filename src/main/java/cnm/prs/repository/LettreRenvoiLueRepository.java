package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.LettreRenvoiLue;

@Repository
public interface LettreRenvoiLueRepository extends JpaRepository<LettreRenvoiLue, Integer> {

    /**
     * Vrai si l'<strong>agent</strong> (login) a déjà lu la lettre (anti-doublon + flag {@code lue} du DTO).
     *
     * <p>⚠️ Décision métier 2026-08-27 — remplace {@code existsByIdLettreAndIdPrmp} : le suivi de lecture
     * est individuel, la consultation par une UGPM ne vaut plus lecture pour sa PRMP de tutelle.</p>
     */
    boolean existsByIdLettreAndLoginAgent(Integer idLettre, String loginAgent);

    /**
     * Résolution EN LOT du flag « lue » (⚠️ audit 2026-08-27, lot D §5) : identifiants, parmi
     * {@code ids}, des lettres que {@code login} a déjà lues. Remplace un
     * {@link #existsByIdLettreAndLoginAgent} par lettre dans les listes — une requête, quelle que
     * soit la taille de la liste, sur le modèle de {@code PieceDemandeRetraitRepository#findMetaByIdDemandeRetraitIn}.
     */
    @Query("select lu.idLettre from LettreRenvoiLue lu where lu.loginAgent = :login and lu.idLettre in :ids")
    List<Integer> findIdLettresLuesPourLogin(@Param("ids") Collection<Integer> ids, @Param("login") String login);

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les accusés de lecture des lettres de renvoi d'un dossier
     * retiré (via {@code LettreRenvoi.idDossier}). À appeler <strong>avant</strong> les lettres de renvoi.
     */
    @Modifying
    @Query("delete from LettreRenvoiLue lu where lu.idLettre in "
            + "(select l.idLettre from LettreRenvoi l where l.idDossier = :idDossier)")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
