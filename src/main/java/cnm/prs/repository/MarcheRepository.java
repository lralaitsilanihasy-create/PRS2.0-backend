package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Marche;

@Repository
public interface MarcheRepository extends JpaRepository<Marche, Integer> {

    /** Lignes de marché d'un dossier (réconciliation à l'édition d'un brouillon). */
    List<Marche> findByIdDossier(Integer idDossier);

    /** Lignes de marché d'un PPM (cascade applicative à la suppression du PPM). */
    List<Marche> findByIdPpm(Integer idPpm);

    /** Prochaine PK marché, allouée par la séquence serveur (Voie B — l'id client est ignoré). */
    @Query(value = "select nextval('seq_marche')", nativeQuery = true)
    Long nextIdMarche();

    /** Vrai si le dossier porte au moins une ligne de marché (précondition de soumission d'un PPM). */
    boolean existsByIdDossier(Integer idDossier);

    /**
     * Vrai si le <strong>PPM</strong> comporte ≥1 marché dont le mode est « appel d'offres ouvert »
     * ({@code tr_mode_passation.DECLENCHE_AGPM = true}) → dérivé {@code agpmRequis} exposé sur le PPM.
     * Les marchés sans mode ou dont le mode ne déclenche pas l'AGPM sont exclus.
     */
    @Query("select (count(m) > 0) from Marche m where m.idPpm = :idPpm and m.mode.declencheAgpm = true")
    boolean existsMarcheDeclencheurAgpmByPpm(@Param("idPpm") Integer idPpm);

    /**
     * Vrai si le <strong>dossier</strong> comporte ≥1 marché « appel d'offres ouvert » — base du contrôle
     * conditionnel de la pièce AGPM à la soumission (couvre le cas multi-PPM d'un même dossier).
     */
    @Query("select (count(m) > 0) from Marche m where m.idDossier = :idDossier and m.mode.declencheAgpm = true")
    boolean existsMarcheDeclencheurAgpmByDossier(@Param("idDossier") Integer idDossier);

    /** Marchés d'une PRMP (§3.1) : ceux dont le PPM lui appartient — son périmètre propre. */
    @Query("select m from Marche m where exists "
            + "(select 1 from Ppm p where p.idPpm = m.idPpm and p.idPrmp = :idPrmp)")
    List<Marche> findVisiblesPourPrmp(@Param("idPrmp") String idPrmp);

    /** Vrai si le marché appartient à la PRMP (via son PPM) — contrôle de {@code GET /{id}}. */
    @Query("select (count(m) > 0) from Marche m where m.idDetail = :id and exists "
            + "(select 1 from Ppm p where p.idPpm = m.idPpm and p.idPrmp = :idPrmp)")
    boolean existsVisiblePourPrmp(@Param("id") Integer id, @Param("idPrmp") String idPrmp);

    /**
     * Marchés visibles d'une localité (§1) : ceux dont le dossier est de la localité donnée et
     * n'est pas un brouillon. Localité via {@code t_dossier.ID_LOCALITE}.
     */
    @Query("""
            select m from Marche m, Dossier d
            where d.idDossier = m.idDossier
              and (d.statut is null or d.statut <> 'BROUILLON')
              and d.idLocalite = :localite
            """)
    List<Marche> findVisiblesParLocalite(@Param("localite") String localite);

    /** Vrai si le marché est visible dans la localité (dossier non brouillon, même localité). */
    @Query("""
            select (count(m) > 0) from Marche m, Dossier d
            where m.idDetail = :id and d.idDossier = m.idDossier
              and (d.statut is null or d.statut <> 'BROUILLON')
              and d.idLocalite = :localite
            """)
    boolean existsVisibleParLocalite(@Param("id") Integer id, @Param("localite") String localite);
}
