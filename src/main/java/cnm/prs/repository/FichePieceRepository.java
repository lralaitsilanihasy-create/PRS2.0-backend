package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.FichePiece;

/** ⚠️ V61 (2026-10-03) — la liste « pièces de l'offre » d'une version de fiche DAO de travaux. */
public interface FichePieceRepository extends JpaRepository<FichePiece, Integer> {

    List<FichePiece> findByIdFicheOrderByOrdreAsc(Integer idFiche);

    void deleteByIdFiche(Integer idFiche);

    /** âš ï¸ 2026-10-30 (Â« performance Â», second chantier) â€” les piÃ¨ces de plusieurs versions, en une requÃªte. */
    List<FichePiece> findByIdFicheInOrderByIdFicheAscOrdreAsc(java.util.Collection<Integer> idFiches);

    /** âš ï¸ 2026-10-30 â€” les piÃ¨ces d'une version, lues une fois par appel de liste ({@link MemoLecture}). */
    default List<FichePiece> lues(Integer idFiche) {
        return MemoLecture.lire("pieces:" + idFiche, () -> findByIdFicheOrderByOrdreAsc(idFiche));
    }
}
