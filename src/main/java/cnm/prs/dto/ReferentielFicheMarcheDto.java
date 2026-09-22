package cnm.prs.dto;

import java.util.List;

/**
 * Réponse de {@code GET /api/champs-fiche-marche?typeMarche=} — la <strong>structure</strong> de la fiche (blocs et
 * rubriques, comptes attendus) et les champs chargés, d'où le front dessine l'écran (demande du 2026-09-22, §B1).
 *
 * @param blocs  blocs du type de marché, dans l'ordre, avec leurs rubriques
 * @param champs champs actifs du type de marché, dans l'ordre d'affichage (rubrique, rang)
 */
public record ReferentielFicheMarcheDto(List<BlocDto> blocs, List<ChampFicheMarcheDto> champs) {

    /** Un bloc et ses rubriques. */
    public record BlocDto(String code, String libelle, Integer rang, List<RubriqueDto> rubriques) {
    }

    /**
     * Une rubrique : « une carte à l'écran ». {@code nbAttendu} = nombre d'informations que le fichier de
     * correspondance annonce ; tant que ses champs ne sont pas chargés, le front affiche ce compte.
     */
    public record RubriqueDto(String code, String libelle, Integer rang, String documentMaitre, Integer nbAttendu) {
    }
}
