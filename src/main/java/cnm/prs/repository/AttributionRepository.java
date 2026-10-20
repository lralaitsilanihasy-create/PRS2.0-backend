package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.Attribution;

public interface AttributionRepository extends JpaRepository<Attribution, Attribution.Cle> {

    List<Attribution> findByIdDmcOrderByLotAsc(Long idDmc);

    /** ⚠️ 2c — les lots attribués à une offre (son candidat lit ses pièces, son marché). */
    List<Attribution> findByIdOffreAttribuee(String idOffreAttribuee);

    /** ⚠️ 2d-1 (§B7) — les lots des fiches d'une PRMP (ses compteurs). */
    @Query("""
            select a from Attribution a, DossierMec d, Marche m, Dossier ds
             where a.idDmc = d.idDmc and d.idDetail = m.idDetail and m.idDossier = ds.idDossier and ds.idPrmp = :idPrmp
            """)
    List<Attribution> pourPrmp(@Param("idPrmp") String idPrmp);

    /** ⚠️ 2d-1 (§B7) — les lots informés, ni signés ni retirés, dont le délai d'attente n'a pas encore été signalé écoulé. */
    List<Attribution> findByInformeLeIsNotNullAndSigneLeIsNullAndRetireLeIsNullAndAlerteDelaiLeIsNull();

    /** ⚠️ 2d-1 (§B7) — les marchés notifiés dont l'avis d'attribution n'est ni publié ni encore signalé. */
    List<Attribution> findByNotifieLeIsNotNullAndAvisPublieLeIsNullAndAlerteAvisLeIsNull();

    /** ⚠️ M2 — le lot dont un dossier est le dossier de marché (la catégorie de sa fiche décide de certaines pièces). */
    java.util.Optional<Attribution> findFirstByIdDossier(Integer idDossier);

    /**
     * ⚠️ 2026-10-19 (demande front « dossier-marche-lecture », §B2) — les dossiers de marché de toutes les fiches, en une requête :
     * {@code [idDmc, lot, idDossier, refeDossier, statut]}, par fiche et par lot.
     */
    @org.springframework.data.jpa.repository.Query("""
            select a.idDmc, a.lot, d.idDossier, d.refeDossier, d.statut from Attribution a, Dossier d
             where d.idDossier = a.idDossier order by a.idDmc, a.lot
            """)
    List<Object[]> findDossiersMarche();
}
