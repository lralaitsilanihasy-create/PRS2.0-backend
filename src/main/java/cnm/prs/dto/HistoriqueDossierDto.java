package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ 2026-10-31 (demande front « filiation-dossier », §B2) — un objet rattaché à un dossier, dans son historique : son {@code type},
 * sa {@code date}, son identifiant ({@code id}) et sa référence propre quand il en a une, pour que le front en fasse un lien.
 * {@code statut}, {@code avis}, {@code acteur}, {@code detail}, {@code lot}, {@code numero} : selon le type (nuls sinon).
 */
public record HistoriqueDossierDto(String type, LocalDateTime date, Integer id, String reference, String statut, String avis, String acteur,
        String detail, Integer lot, Integer numero) {

    public static final String RECEPTION = "RECEPTION";
    public static final String DISPATCH = "DISPATCH";
    public static final String EXAMEN = "EXAMEN";
    public static final String PV = "PV";
    public static final String LETTRE_RENVOI = "LETTRE_RENVOI";
    public static final String TRANSMISSION_SIGMP = "TRANSMISSION_SIGMP";
    public static final String SUSPENSION = "SUSPENSION";
    public static final String RETRAIT = "RETRAIT";
    public static final String VERSION_ARCHIVEE = "VERSION_ARCHIVEE";
    public static final String ACTE_GESTION = "ACTE_GESTION";
    // ⚠️ 2026-10-31 (§B2, second temps) — la procédure : publications, séance, évaluation, attribution, contrat, bons, réceptions.
    public static final String PUBLICATION_AGPM = "PUBLICATION_AGPM";
    public static final String PUBLICATION_AVIS = "PUBLICATION_AVIS";
    public static final String SEANCE = "SEANCE";
    public static final String EVALUATION = "EVALUATION";
    public static final String ATTRIBUTION = "ATTRIBUTION";
    public static final String CONTRAT = "CONTRAT";
    public static final String BON_COMMANDE = "BON_COMMANDE";
    public static final String RECEPTION_COMMANDE = "RECEPTION_COMMANDE";
}
