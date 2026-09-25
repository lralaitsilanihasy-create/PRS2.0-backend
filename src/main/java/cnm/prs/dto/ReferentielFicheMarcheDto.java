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

    /**
     * Un bloc et ses rubriques. ⚠️ V46 (2026-09-25, §B6) — {@code rendu} : {@code null} = la liste des champs du bloc ;
     * {@code "BESOIN"} = la grille du besoin ({@code GET/PUT /api/fiches-marche/{idDmc}/articles}).
     */
    public record BlocDto(String code, String libelle, Integer rang, List<RubriqueDto> rubriques, String rendu) {
    }

    /**
     * Une rubrique : « une carte à l'écran ». {@code nbAttendu} = nombre d'informations que le fichier de
     * correspondance annonce ; tant que ses champs ne sont pas chargés, le front affiche ce compte.
     */
    public record RubriqueDto(String code, String libelle, Integer rang, String documentMaitre, Integer nbAttendu) {
    }
}
