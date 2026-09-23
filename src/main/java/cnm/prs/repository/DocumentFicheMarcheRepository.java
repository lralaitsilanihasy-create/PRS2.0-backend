package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.DocumentFicheMarche;

/** ⚠️ Fiche marché, lot 2a (2026-09-23) — les documents générés d'une version de fiche. */
@Repository
public interface DocumentFicheMarcheRepository extends JpaRepository<DocumentFicheMarche, Integer> {

    /** Les documents d'une version, dans l'ordre de génération. */
    List<DocumentFicheMarche> findByIdFicheOrderByIdDocumentAsc(Integer idFiche);
}
