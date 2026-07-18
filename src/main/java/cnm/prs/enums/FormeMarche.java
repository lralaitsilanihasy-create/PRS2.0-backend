package cnm.prs.enums;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import cnm.prs.exception.BadRequestException;

/**
 * ⚠️ Règle ajoutée (2026-07-18) — <strong>forme du marché</strong> (colonne {@code t_marche.FORME_MARCHE}).
 * Liste fermée réglementaire → enum contrôlé (pas de référentiel en table).
 *
 * <p>Libellés d'affichage (côté front) : {@code A_COMMANDE} = « Marché à commande »,
 * {@code CONTRAT_CADRE} = « Contrat cadre », {@code QUANTITE_FIXE} = « À quantité fixe ».</p>
 *
 * <p><strong>Défaut</strong> : si la forme n'est pas mentionnée dans l'objet du marché (ni saisie),
 * {@link #QUANTITE_FIXE} — jamais {@code null} côté API.</p>
 */
public enum FormeMarche {

    /** « Marché à commande » — motif « à commande » / « marché à commande » dans l'objet. */
    A_COMMANDE,

    /** « Contrat cadre » — motif « contrat cadre » (avec/sans parenthèses) dans l'objet. */
    CONTRAT_CADRE,

    /** « À quantité fixe » — défaut quand l'objet ne mentionne aucune forme. */
    QUANTITE_FIXE;

    /**
     * Motifs de détection sur la désignation <strong>normalisée</strong> (accents/casse neutralisés,
     * séparateurs réduits à une espace — les frontières de mots sont PRÉSERVÉES, contrairement à
     * {@code LibelleNormalisation.normaliser} qui concatène les tokens : « la commande » ne doit pas
     * matcher « à commande »). Pluriels tolérés par un « S? » dans le motif.
     */
    private static final Pattern MOTIF_CONTRAT_CADRE = Pattern.compile(" CONTRATS? CADRES? ");
    private static final Pattern MOTIF_A_COMMANDE = Pattern.compile(" A COMMANDES? ");

    /**
     * Relève la forme du marché mentionnée dans une désignation (casse/accents/parenthèses libres) :
     * « contrat cadre » → {@link #CONTRAT_CADRE}, « à commande » → {@link #A_COMMANDE}, sinon défaut
     * {@link #QUANTITE_FIXE}. La désignation n'est jamais modifiée (on relève, on ne retire pas —
     * même décision que pour les lots).
     */
    public static FormeMarche detecterDansDesignation(String designation) {
        if (designation == null || designation.isBlank()) {
            return QUANTITE_FIXE;
        }
        String sansAccents = Normalizer.normalize(designation, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String mots = " " + sansAccents.toUpperCase(Locale.FRENCH).replaceAll("[^A-Z0-9]+", " ").trim() + " ";
        if (MOTIF_CONTRAT_CADRE.matcher(mots).find()) {
            return CONTRAT_CADRE;
        }
        if (MOTIF_A_COMMANDE.matcher(mots).find()) {
            return A_COMMANDE;
        }
        return QUANTITE_FIXE;
    }

    /**
     * Code API → enum : absent/vide → défaut {@link #QUANTITE_FIXE} (champ optionnel, jamais null) ;
     * code inconnu → 400 ciblé.
     */
    public static FormeMarche depuisCodeOuDefaut(String code) {
        if (code == null || code.isBlank()) {
            return QUANTITE_FIXE;
        }
        try {
            return valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Forme de marché inconnue : « " + code
                    + " » — valeurs acceptées : A_COMMANDE, CONTRAT_CADRE, QUANTITE_FIXE.");
        }
    }
}
