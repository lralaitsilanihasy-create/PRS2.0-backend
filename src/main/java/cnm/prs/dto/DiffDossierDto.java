package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ Règle ajoutée (2026-08-05, mise à jour des PPM) — comparaison d'une version de PPM avec son
 * prédécesseur : récapitulatif chiffré + détail ligne à ligne. Sert l'« aperçu du diff » AVANT
 * validation, et relit la trace figée une fois la version soumise.
 *
 * @param idDossier           la version comparée (le successeur)
 * @param idDossierPrecedent  son prédécesseur ({@code null} si le dossier n'est pas une mise à jour)
 * @param numMaj              numéro de mise à jour de cette version (1, 2, …)
 * @param motifMaj            motif métier saisi à la création de la version
 * @param fige                {@code true} si le diff provient de la trace figée (version soumise),
 *                            {@code false} s'il est recalculé à la volée sur un brouillon
 * @param recap               compteurs par type de changement
 * @param lignes              une entrée par ligne de marché des deux versions réunies
 */
public record DiffDossierDto(
        Integer idDossier,
        Integer idDossierPrecedent,
        Integer numMaj,
        String motifMaj,
        boolean fige,
        RecapDiff recap,
        List<LigneDiff> lignes) {

    /**
     * Récapitulatif chiffré exigé avant validation. {@code total} = nombre de lignes distinctes
     * (identités) présentes dans l'une ou l'autre version.
     */
    public record RecapDiff(
            int inchangees,
            int modifiees,
            int nouvelles,
            int supprimees,
            int restaurees,
            int total) {
    }

    /**
     * Une ligne de marché vue à travers les deux versions.
     *
     * @param idDetail        PK de la ligne dans la NOUVELLE version ({@code null} si la ligne n'existe
     *                        que chez le prédécesseur — cas d'une suppression franche)
     * @param idLigneOrigine  identité stable de la ligne à travers les versions
     * @param designation     libellé courant (celui du successeur, à défaut du prédécesseur)
     * @param type            {@code INCHANGEE} | {@code MODIFIEE} | {@code NOUVELLE} | {@code SUPPRIMEE}
     *                        | {@code RESTAUREE}
     * @param apparieePar     {@code ORIGINE} (clé stable) ou {@code LIBELLE_SOA} (repli pour une ligne
     *                        sans ancêtre, typiquement issue d'un réimport PDF) — rend l'appariement auditable
     * @param champs          champs modifiés, vide hors {@code MODIFIEE}
     */
    public record LigneDiff(
            Integer idDetail,
            Integer idLigneOrigine,
            String designation,
            String type,
            String apparieePar,
            List<ChampDiff> champs) {
    }

    /** Un champ modifié, avec ses valeurs avant/après rendues en texte comparable. */
    public record ChampDiff(String champ, String avant, String apres) {
    }
}
