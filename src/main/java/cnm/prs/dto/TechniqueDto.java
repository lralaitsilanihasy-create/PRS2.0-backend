package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-c, §B4 ; V87) — l'évaluation technique des propositions de prestations intellectuelles : la
 * grille (éléments notés : sous-critères de la fiche, ou critères notés globalement), et par lot les propositions retenues à
 * l'examen préliminaire, la grille de chaque membre, les moyennes, la note technique, les écarts signalés, le statut.
 */
public record TechniqueDto(Long idDmc, BigDecimal scoreMinimum, BigDecimal seuilEcartPourcent, List<Element> elements, List<Lot> lots) {

    /** Un élément de la grille : {@code code} = {@code B06-TP-03#2} (sous-critère n° 2 du critère) ou {@code B06-TP-02} (critère global). */
    public record Element(String code, String critere, String libelleCritere, String libelle, BigDecimal max) {
    }

    /**
     * Un lot : {@code conformiteArretee} (l'examen préliminaire arrêté : la notation est ouverte) ; {@code arret} nul tant que le président
     * n'a pas arrêté l'étape technique.
     */
    public record Lot(Integer lot, boolean conformiteArretee, Arret arret, List<Offre> offres) {
    }

    public record Arret(LocalDateTime le, String par, String nom, String observation, LocalDateTime rouverteLe, String motifReouverture) {
    }

    /**
     * Une proposition : {@code grilles} (une par membre qui a noté), {@code moyennes} par élément, {@code total} = somme des moyennes,
     * {@code complete} (chaque membre décideur a noté chaque élément), {@code statut} ∈ {@code EN_COURS} · {@code QUALIFIEE} ·
     * {@code ELIMINEE} (sous le score minimum, à l'arrêt), {@code rang} parmi les qualifiées.
     */
    public record Offre(String idOffre, Integer numero, String nif, String raisonSociale, List<Grille> grilles, List<Moyenne> moyennes,
            BigDecimal total, boolean complete, String statut, String motifElimination, Integer rang) {
    }

    public record Grille(String im, String nom, List<Note> notes) {
    }

    public record Note(String element, BigDecimal note, String motif, LocalDateTime le) {
    }

    /** La moyenne d'un élément ; {@code ecart} : la note la plus haute et la plus basse s'écartent de plus du seuil (en % du maximum). */
    public record Moyenne(String element, BigDecimal moyenne, BigDecimal min, BigDecimal max, int nombreNotes, boolean ecart) {
    }

    // ------------------------------------------------------------------ corps des requêtes

    /** {@code PUT …/technique/offres/{idOffre}/notes} : la grille du membre (une ou plusieurs notes, chacune motivée). */
    public record NotesRequest(List<NoteSaisie> notes) {
    }

    public record NoteSaisie(String element, BigDecimal note, String motif) {
    }

    public record ArretRequest(String observation) {
    }

    public record ReouvertureRequest(String motif) {
    }
}
