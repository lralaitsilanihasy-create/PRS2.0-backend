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

    /** Efface les valeurs d'un bloc d'une fiche (préfixe de code « B05- »), en un ordre SQL : le PUT d'un bloc les remplace. */
    @Modifying
    @Query("delete from FicheMarcheValeur v where v.idFiche = :idFiche and v.codeChamp like :prefixe")
    int deleteParBloc(@Param("idFiche") Integer idFiche, @Param("prefixe") String prefixe);
}
