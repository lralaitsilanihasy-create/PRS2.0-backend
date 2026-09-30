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

    /** ⚠️ 2026-09-30 — les documents d'un type (avis spécifique) de plusieurs versions, du plus récent au plus ancien. */
    List<DocumentFicheMarche> findByIdFicheInAndTypeOrderByIdDocumentDesc(java.util.Collection<Integer> idFiches, String type);

    /**
     * ⚠️ 2026-09-30 (statut « Lancé » à l'avis, §B2-§B4) — la date de la PREMIÈRE impression de l'avis spécifique d'une
     * filiation de lignes (même origine à travers les versions du plan), {@code null} sans avis.
     */
    @org.springframework.data.jpa.repository.Query("""
            select min(doc.dateGeneration) from DocumentFicheMarche doc, FicheMarche f, DossierMec d, Marche m
             where doc.type = 'AVIS' and doc.idFiche = f.idFiche and f.idDmc = d.idDmc and d.idDetail = m.idDetail
               and (m.idLigneOrigine = :origine or m.idDetail = :origine)
            """)
    java.time.LocalDateTime premierAvisDeLaFiliation(@org.springframework.data.repository.query.Param("origine") Integer origine);

    /** La même date pour toutes les filiations ({@code [Integer origine, LocalDateTime]}), en une requête. */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(m.idLigneOrigine, m.idDetail), min(doc.dateGeneration)
              from DocumentFicheMarche doc, FicheMarche f, DossierMec d, Marche m
             where doc.type = 'AVIS' and doc.idFiche = f.idFiche and f.idDmc = d.idDmc and d.idDetail = m.idDetail
             group by coalesce(m.idLigneOrigine, m.idDetail)
            """)
    List<Object[]> premiersAvisParOrigine();
}
