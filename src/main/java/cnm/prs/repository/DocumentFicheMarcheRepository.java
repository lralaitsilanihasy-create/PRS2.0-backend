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

    /**
     * ⚠️ 2026-10-17 (« identite-dao-importe », §B4) — un document de ce type, à cette empreinte, a-t-il été produit ? La marque d'un DAO
     * réimporté ne fait foi que s'il est resté tel que l'application l'a produit.
     */
    boolean existsByEmpreinteAndType(String empreinte, String type);

    /**
     * ⚠️ 2026-09-30 — les documents de certains types (les publications : avis spécifique, ⚠️ 2026-10-01 lettres
     * d'invitation) de plusieurs versions, du plus récent au plus ancien.
     */
    List<DocumentFicheMarche> findByIdFicheInAndTypeInOrderByIdDocumentDesc(java.util.Collection<Integer> idFiches,
            java.util.Collection<String> types);

    /**
     * ⚠️ 2026-09-30 (statut « Lancé » à l'avis, §B2-§B4) — la date de la PREMIÈRE impression de l'avis spécifique (⚠️ 2026-10-01 ou des lettres
     * d'invitation, lot AV-4.1 §B5) d'une
     * filiation de lignes (même origine à travers les versions du plan), {@code null} sans avis.
     */
    @org.springframework.data.jpa.repository.Query("""
            select min(doc.dateGeneration) from DocumentFicheMarche doc, FicheMarche f, DossierMec d, Marche m
             where doc.type in ('AVIS', 'LETTRE_INVITATION') and doc.idFiche = f.idFiche and f.idDmc = d.idDmc and d.idDetail = m.idDetail
               and (m.idLigneOrigine = :origine or m.idDetail = :origine)
            """)
    java.time.LocalDateTime premierAvisDeLaFiliation(@org.springframework.data.repository.query.Param("origine") Integer origine);

    /** La même date pour toutes les filiations ({@code [Integer origine, LocalDateTime]}), en une requête. */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(m.idLigneOrigine, m.idDetail), min(doc.dateGeneration)
              from DocumentFicheMarche doc, FicheMarche f, DossierMec d, Marche m
             where doc.type in ('AVIS', 'LETTRE_INVITATION') and doc.idFiche = f.idFiche and f.idDmc = d.idDmc and d.idDetail = m.idDetail
             group by coalesce(m.idLigneOrigine, m.idDetail)
            """)
    List<Object[]> premiersAvisParOrigine();

    /**
     * ⚠️ 2026-10-18 (demande front « avis-a-imprimer », §B2) — les fiches ({@code idDmc}) dont un avis spécifique ou une lettre
     * d'invitation a déjà été produit, parmi celles données, en une requête.
     */
    @org.springframework.data.jpa.repository.Query("""
            select distinct f.idDmc from DocumentFicheMarche doc, FicheMarche f
             where doc.type in ('AVIS', 'LETTRE_INVITATION') and doc.idFiche = f.idFiche and f.idDmc in :idDmcs
            """)
    List<Long> dmcsImprimes(@org.springframework.data.repository.query.Param("idDmcs") java.util.Collection<Long> idDmcs);
}
