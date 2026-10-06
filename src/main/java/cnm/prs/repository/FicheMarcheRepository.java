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

    /**
     * ⚠️ 2026-10-04 (soumission en ligne, lot 1c, §B8) — la dernière version {@code VALIDEE} de chaque DMC qui en a une
     * (une révision ouverte ne la masque pas), candidates à la liste publique des procédures en ligne.
     */
    @org.springframework.data.jpa.repository.Query("""
            select f from FicheMarche f
             where f.statut = 'VALIDEE'
               and f.numeroVersion = (select max(g.numeroVersion) from FicheMarche g
                                       where g.idDmc = f.idDmc and g.statut = 'VALIDEE')
             order by f.idDmc desc
            """)
    List<FicheMarche> findDernieresValidees();

    /** ⚠️ 2026-10-06 (liste des procédures en ligne, §B1) — la dernière version de chaque DMC, quel que soit son statut. */
    @org.springframework.data.jpa.repository.Query("""
            select f from FicheMarche f
             where f.numeroVersion = (select max(g.numeroVersion) from FicheMarche g where g.idDmc = f.idDmc)
             order by f.idDmc desc
            """)
    List<FicheMarche> findDernieresVersions();
}
