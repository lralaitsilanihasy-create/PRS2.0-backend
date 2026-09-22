package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.FicheMarche;

@Repository
public interface FicheMarcheRepository extends JpaRepository<FicheMarche, Integer> {

    /** La version courante d'un DMC (la plus récente). */
    Optional<FicheMarche> findFirstByIdDmcOrderByNumeroVersionDesc(Long idDmc);

    /** Toutes les versions d'un DMC, de la plus ancienne à la plus récente. */
    List<FicheMarche> findByIdDmcOrderByNumeroVersionAsc(Long idDmc);

    Optional<FicheMarche> findByIdDmcAndNumeroVersion(Long idDmc, Integer numeroVersion);

    /**
     * ⚠️ Lot 1b (2026-09-23, §B3) — les fiches <strong>rattachables</strong> : la dernière version de chaque DMC,
     * {@code VALIDEE}, dont le DMC ne porte encore aucun dossier ({@code t_dossier.ID_DMC}). Le plus récent d'abord ;
     * le périmètre est filtré par l'appelant.
     */
    @org.springframework.data.jpa.repository.Query("""
            select f from FicheMarche f
             where f.statut = 'VALIDEE'
               and f.numeroVersion = (select max(g.numeroVersion) from FicheMarche g where g.idDmc = f.idDmc)
               and not exists (select 1 from Dossier d where d.idDmc = f.idDmc)
             order by f.dateValidation desc, f.idDmc desc
            """)
    List<FicheMarche> findDernieresValideesSansDossier();
}
