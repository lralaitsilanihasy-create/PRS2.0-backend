package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.FicheMateriel;

/** ⚠️ V60 (2026-10-03) — la liste « matériel exigé » d'une version de fiche DAO de travaux. */
public interface FicheMaterielRepository extends JpaRepository<FicheMateriel, Integer> {

    List<FicheMateriel> findByIdFicheOrderByOrdreAsc(Integer idFiche);

    void deleteByIdFiche(Integer idFiche);
}
