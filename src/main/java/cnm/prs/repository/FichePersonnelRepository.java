package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.FichePersonnel;

/** ⚠️ V60 (2026-10-03) — la liste « personnel clé exigé » d'une version de fiche DAO de travaux. */
public interface FichePersonnelRepository extends JpaRepository<FichePersonnel, Integer> {

    List<FichePersonnel> findByIdFicheOrderByOrdreAsc(Integer idFiche);

    /** ⚠️ 2026-10-30 (« performance », reliquat de B1) — ceux de plusieurs versions, en une requête (préchargement des listes). */
    List<FichePersonnel> findByIdFicheInOrderByIdFicheAscOrdreAsc(java.util.Collection<Integer> idFiches);

    void deleteByIdFiche(Integer idFiche);
}
