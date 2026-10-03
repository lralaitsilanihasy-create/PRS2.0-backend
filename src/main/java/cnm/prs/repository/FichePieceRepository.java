package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.FichePiece;

/** ⚠️ V61 (2026-10-03) — la liste « pièces de l'offre » d'une version de fiche DAO de travaux. */
public interface FichePieceRepository extends JpaRepository<FichePiece, Integer> {

    List<FichePiece> findByIdFicheOrderByOrdreAsc(Integer idFiche);

    void deleteByIdFiche(Integer idFiche);
}
