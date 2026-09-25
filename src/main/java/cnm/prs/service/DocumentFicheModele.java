package cnm.prs.service;

import java.util.List;

/**
 * ⚠️ Fiche marché, lot 2a (demande front du 2026-09-23, §B5) — le <strong>contenu</strong> d'un document généré, sans
 * aucune mise en page : ce que {@link SelectionDocumentsFiche} a retenu (quelles informations vont dans quel document,
 * dans quel ordre), et que {@link GenerateurDocumentsFiche} dispose (docx, pdf). Au lot 2b, seul le générateur change.
 *
 * @param type     {@code DPAO} · {@code DPAC} · {@code CCAP} · {@code AE}
 * @param titre    intitulé du document
 * @param blocs    un bloc de la fiche par titre, dans l'ordre des rangs ; aucun bloc vide
 * @param piedDePage référence du plan, ligne, version de la fiche, date de validation
 * @param lot      ⚠️ 2026-09-25 — rang du lot d'un document établi par lot (acte d'engagement d'une ligne allotie) ;
 *                 {@code null} : document commun
 */
public record DocumentFicheModele(String type, String titre, String sousTitre, List<Bloc> blocs, String piedDePage,
        Integer lot) {

    /** Un document commun (sans lot). */
    public DocumentFicheModele(String type, String titre, String sousTitre, List<Bloc> blocs, String piedDePage) {
        this(type, titre, sousTitre, blocs, piedDePage, null);
    }

    /** Un bloc de la fiche (titre) et ses rubriques non vides. */
    public record Bloc(String titre, List<Rubrique> rubriques) {
    }

    /** Une rubrique (sous-titre) et ses lignes, jamais vide. */
    public record Rubrique(String titre, List<Ligne> lignes) {
    }

    /** « libellé : valeur » — la valeur est toujours renseignée (un champ sans valeur est omis). */
    public record Ligne(String libelle, String valeur) {
    }
}
