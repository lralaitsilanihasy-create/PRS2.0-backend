package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ 2026-10-17 (demande front « canevas-rapport-evaluation », §B1) — le canevas de rapport d'évaluation d'un dossier né d'une fiche
 * validée ({@code GET /api/dossiers/{id}/canevas-rapport}) : le plan du rapport du guide de la CAO, rempli de ce que la fiche sait
 * avant les offres, avec les tableaux que la commission remplira.
 *
 * @param idDossier    le dossier
 * @param idDmc        la fiche
 * @param versionFiche la version présentée (la version soumise, à défaut la dernière validée)
 * @param categorie    {@code FOURNITURES_SERVICES}, {@code TRAVAUX}, {@code PRESTATIONS_INTELLECTUELLES}
 * @param sections     dans l'ordre du rapport ; une section sans ligne ni tableau n'est pas servie
 */
public record CanevasRapportDto(Integer idDossier, Long idDmc, Integer versionFiche, String categorie, List<Section> sections) {

    /**
     * @param partie  en prestations intellectuelles (Q2), {@code TECHNIQUE} ou {@code FINALE} ; nul sinon
     * @param lignes  ce que la fiche exige (« libellé : valeur »)
     * @param tableau à remplir par la commission, ou nul
     */
    public record Section(String code, String titre, String partie, List<FichePresentationDto.Ligne> lignes, Tableau tableau) {
    }

    /**
     * Un tableau à remplir : ses colonnes, et ses rangées déjà nommées (la première colonne), ou aucune quand la commission les
     * écrit (plis reçus, classement) ; {@code rangeesVides} rangées vierges s'ajoutent alors à l'impression.
     */
    public record Tableau(List<String> colonnes, List<String> rangees, int rangeesVides) {
    }
}
