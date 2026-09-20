package cnm.prs.enums;

import java.util.Locale;

/**
 * ⚠️ Assistant IA, lot 4 (2026-09-20) — <strong>ce que le chatbot s'autorise à comprendre</strong>
 * ({@code docs/plan-assistant-ia.md} §4, lot 4, 4.a).
 *
 * <p>C'est une <strong>liste close</strong>, et c'est tout l'intérêt : le modèle ne choisit pas une
 * lecture, il <strong>nomme une intention</strong>, et sa réponse est validée contre cette énumération.
 * Tout ce qui n'y figure pas — une intention inventée, un texte libre, un silence — retombe sur
 * {@link #REGLE}, la réponse documentaire du lot 1, celle qui ne lit <strong>aucune donnée</strong>.</p>
 *
 * <p>Le repli n'est donc jamais un échec : c'est le comportement du lot 1, qui a déjà sa batterie et sa
 * recette. Une intention mal reconnue coûte une réponse moins précise, jamais une donnée de trop.</p>
 */
public enum IntentionAssistant {

    /**
     * « Qu'est-ce que j'ai à faire ? », « qu'est-ce qui m'attend ? » — les tâches du profil appelant.
     * L'Administrateur et le Chargé de publication n'en ont pas (403) : ils gardent leur accueil.
     */
    MES_TACHES("ce que l'utilisateur a à traiter"),

    /** « Combien de dossiers en retard ? », « mes chiffres » — les compteurs du rôle appelant, et d'eux seuls. */
    MES_CHIFFRES("les compteurs du rôle de l'utilisateur"),

    /** « Où en est la commission ? » — la vue d'ensemble, réservée au Président, à l'Administrateur et au Chef de commission. */
    TABLEAU_DE_BORD("la vue d'ensemble de la Commission"),

    /** « Trouve-moi le dossier du ministère des Travaux publics » — recherche, filtrée sur le périmètre. */
    TROUVER_DOSSIER("retrouver un dossier à partir de quelques mots"),

    /** « Où en est le 00002/PPM/CNM/2026 ? » — l'état d'UN dossier, par la lecture factuelle du lot 2. */
    ETAT_DOSSIER("l'état d'un dossier précis"),

    /** « Qui est la PRMP du ministère X ? » — l'annuaire, réservé à l'Administrateur. */
    ANNUAIRE("retrouver une personne dans l'annuaire"),

    /**
     * Une question sur les <strong>règles</strong> du contrôle des marchés ou sur PRS — et le
     * <strong>repli</strong> de tout ce qui n'est pas reconnu. Aucune donnée n'est lue.
     */
    REGLE("une question sur les règles ou sur le fonctionnement de PRS");

    private final String description;

    IntentionAssistant(String description) {
        this.description = description;
    }

    /** Ce que l'intention couvre, tel qu'on le présente au modèle pour qu'il choisisse. */
    public String description() {
        return description;
    }

    /** Cette intention fait-elle lire des DONNÉES métier ? {@link #REGLE} est la seule qui n'en lit pas. */
    public boolean litDesDonnees() {
        return this != REGLE;
    }

    /**
     * Lit une intention rendue par le modèle. Tout ce qui n'est pas <strong>exactement</strong> un nom
     * de cette énumération — texte autour, casse, invention, silence — donne {@link #REGLE}.
     *
     * <p>⚠️ La tolérance est volontairement <strong>faible</strong> : on accepte les espaces et la
     * casse, rien d'autre. Un modèle qui répond « MES_TACHES (je pense) » a mal répondu, et le repli
     * documentaire vaut mieux qu'une lecture déclenchée sur une phrase approximative.</p>
     */
    public static IntentionAssistant lire(String rendu) {
        if (rendu == null) {
            return REGLE;
        }
        String propre = rendu.strip().toUpperCase(Locale.ROOT);
        for (IntentionAssistant intention : values()) {
            if (intention.name().equals(propre)) {
                return intention;
            }
        }
        return REGLE;
    }
}
