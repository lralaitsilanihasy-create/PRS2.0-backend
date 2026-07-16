package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.LettreRenvoi;

@Repository
public interface LettreRenvoiRepository extends JpaRepository<LettreRenvoi, Integer> {

    /** Nombre de lettres de renvoi à un statut donné (compteur du tableau de bord — vue globale). */
    long countByStatut(String statut);

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les lettres de renvoi d'un dossier retiré (FK directe
     * {@code ID_DOSSIER}). À appeler <strong>après</strong> leurs accusés de lecture et
     * <strong>avant</strong> les examens (FK {@code ID_EXAMEN}).
     */
    @Modifying
    @Query("delete from LettreRenvoi l where l.idDossier = :idDossier")
    int deleteParDossier(@Param("idDossier") Integer idDossier);

    /** Ce contrôleur a-t-il signé au moins une lettre de renvoi ? (garde de suppression) */
    boolean existsByImSignataire(String imSignataire);

    /** Nombre de lettres à un statut donné dans une localité (via examen→dispatch→réception) — CC. */
    @Query("""
            select count(l) from LettreRenvoi l
            where l.statut = :statut and l.examen.dispatch.reception.ctrlRecept.idLocalite = :loc
            """)
    long countByStatutEtLocalite(@Param("statut") String statut, @Param("loc") String loc);

    /** Lettres d'un Membre : celles de ses examens (attributaire {@code Examen.imCtrlMembre}). */
    @Query("select l from LettreRenvoi l where l.examen.imCtrlMembre = :im")
    List<LettreRenvoi> findByMembre(@Param("im") String im);

    /** Lettres d'un statut donné dont l'examen relève d'une localité (via examen→dispatch→réception). */
    @Query("""
            select l from LettreRenvoi l
            where l.statut = :statut and l.examen.dispatch.reception.ctrlRecept.idLocalite = :loc
            """)
    List<LettreRenvoi> findByStatutEtLocalite(@Param("statut") String statut, @Param("loc") String loc);

    /** Lettres SIGNE concernant les dossiers d'une PRMP (via PPM du dossier). */
    @Query("""
            select l from LettreRenvoi l
            where l.statut = 'SIGNE'
              and exists (select 1 from Ppm p where p.idDossier = l.idDossier and p.idPrmp = :idPrmp)
            """)
    List<LettreRenvoi> findSigneesPourPrmp(@Param("idPrmp") String idPrmp);

    /**
     * Nombre de lettres SIGNE des dossiers d'une PRMP <strong>non encore lues</strong> par elle
     * (compteur « Mes lettres de renvoi » du menu PRMP). Exclut celles tracées dans
     * {@code t_lettre_renvoi_lue} pour cette PRMP.
     */
    @Query("""
            select count(l) from LettreRenvoi l
            where l.statut = 'SIGNE'
              and exists (select 1 from Ppm p where p.idDossier = l.idDossier and p.idPrmp = :idPrmp)
              and not exists (select 1 from LettreRenvoiLue lu
                              where lu.idLettre = l.idLettre and lu.idPrmp = :idPrmp)
            """)
    long countSigneesNonLuesPourPrmp(@Param("idPrmp") String idPrmp);

    /** Localité de la lettre via la réception (repli quand {@code dossier.idLocalite} est absent). */
    @Query("select l.examen.dispatch.reception.ctrlRecept.idLocalite from LettreRenvoi l where l.idLettre = :id")
    java.util.Optional<String> findLocaliteByLettre(@Param("id") Integer id);

    /** Vrai si la lettre relève de la localité (contrôle d'accès au {@code GET /{id}}). */
    @Query("""
            select (count(l) > 0) from LettreRenvoi l
            where l.idLettre = :id and l.examen.dispatch.reception.ctrlRecept.idLocalite = :loc
            """)
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);
}
