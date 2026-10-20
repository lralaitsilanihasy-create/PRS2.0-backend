package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.FicheMarcheValeur;

@Repository
public interface FicheMarcheValeurRepository extends JpaRepository<FicheMarcheValeur, Integer> {

    List<FicheMarcheValeur> findByIdFiche(Integer idFiche);

    /**
     * ⚠️ 2026-10-20 (« liste-dossier-marche », §B1) — les numéros de procédure de toutes les fiches, sur leur dernière version :
     * {@code [idDmc, typeMarche, codeChamp, valeur]} pour {@code B02-OE-01} et {@code B02-OB-03}.
     */
    @Query("""
            select f.idDmc, f.typeMarche, v.codeChamp, v.valeur from FicheMarche f, FicheMarcheValeur v
             where v.idFiche = f.idFiche and v.codeChamp in ('B02-OE-01', 'B02-OB-03')
               and f.numeroVersion = (select max(g.numeroVersion) from FicheMarche g where g.idDmc = f.idDmc)
            """)
    List<Object[]> findNumerosProcedure();

    /** Efface les valeurs d'un bloc d'une fiche (préfixe de code « B05- »), en un ordre SQL : le PUT d'un bloc les remplace. */
    @Modifying
    @Query("delete from FicheMarcheValeur v where v.idFiche = :idFiche and v.codeChamp like :prefixe")
    int deleteParBloc(@Param("idFiche") Integer idFiche, @Param("prefixe") String prefixe);
}
