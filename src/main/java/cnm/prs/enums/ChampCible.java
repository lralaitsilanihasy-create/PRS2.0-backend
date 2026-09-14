package cnm.prs.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ⚠️ <strong>Cellule du document visée par une observation d'examen</strong> (demande front du 2026-09-14,
 * {@code demande-backend-2026-09-14-observation-cellule-document.md}) — liste FERMÉE des colonnes qu'une
 * ligne « Au lieu de / Lire » peut désigner, sur le modèle de {@link ChampAnomalie}.
 *
 * <p><strong>Pourquoi une liste fermée.</strong> Le front encadre la cellule du document officiel à partir
 * de ce code : un code libre qu'aucun écran ne sait placer serait une cible invisible. Retrouver la cellule
 * par la valeur « Au lieu de » est ambigu (deux montants égaux, bénéficiaires fusionnés, même mode sur
 * plusieurs lignes) et impossible pour la fiche et l'AGPM, dont les résultats n'ont pas de ligne.</p>
 *
 * <p><strong>Le préfixe dit le document.</strong> {@code derogatoires.}, {@code delaisAmenages.} et
 * {@code contratsCadres.} désignent la fiche de présentation, {@code agpm.} le projet d'AGPM ; un code sans
 * préfixe est une colonne du PPM. Le document se retrouve donc à partir du seul code
 * ({@link #document()}), sans rien stocker de plus.</p>
 */
public enum ChampCible {

    // ------------------------------------------------------------------ PPM, colonnes de la ligne
    NATURE("nature", DocumentCible.PPM, false),
    OBJET("objet", DocumentCible.PPM, false),
    MONT_ESTIM("montEstim", DocumentCible.PPM, false),
    NOUV_MONT_ESTIM("nouvMontEstim", DocumentCible.PPM, false),
    MODE("mode", DocumentCible.PPM, false),
    FINANCEMENT("financement", DocumentCible.PPM, false),
    LANCEMENT("lancement", DocumentCible.PPM, false),
    OUVERTURE("ouverture", DocumentCible.PPM, false),
    ATTRIBUTION("attribution", DocumentCible.PPM, false),

    // ------------------------------------------------------------------ PPM, colonnes par bénéficiaire
    SOA("soa", DocumentCible.PPM, true),
    COMPTE("compte", DocumentCible.PPM, true),
    MONT_BENEF("montBenef", DocumentCible.PPM, true),
    NOUV_MONT_BENEF("nouvMontBenef", DocumentCible.PPM, true),

    // ------------------------------------------------------------------ fiche de présentation
    DEROGATOIRES_OBJET("derogatoires.objet", DocumentCible.FICHE, false),
    DEROGATOIRES_MONT_ESTIM("derogatoires.montEstim", DocumentCible.FICHE, false),
    DEROGATOIRES_MODE("derogatoires.mode", DocumentCible.FICHE, false),
    DEROGATOIRES_JUSTIFICATION("derogatoires.justification", DocumentCible.FICHE, false),
    DELAIS_AMENAGES_OBJET("delaisAmenages.objet", DocumentCible.FICHE, false),
    DELAIS_AMENAGES_MONT_ESTIM("delaisAmenages.montEstim", DocumentCible.FICHE, false),
    DELAIS_AMENAGES_MODE("delaisAmenages.mode", DocumentCible.FICHE, false),
    DELAIS_AMENAGES_DELAI_REMISE("delaisAmenages.delaiRemise", DocumentCible.FICHE, false),
    DELAIS_AMENAGES_JUSTIFICATION("delaisAmenages.justification", DocumentCible.FICHE, false),
    CONTRATS_CADRES_OBJET("contratsCadres.objet", DocumentCible.FICHE, false),
    CONTRATS_CADRES_MONT_ESTIM("contratsCadres.montEstim", DocumentCible.FICHE, false),
    CONTRATS_CADRES_MODE("contratsCadres.mode", DocumentCible.FICHE, false),
    CONTRATS_CADRES_DELAI_REMISE("contratsCadres.delaiRemise", DocumentCible.FICHE, false),

    // ------------------------------------------------------------------ projet d'AGPM
    AGPM_COMPTE("agpm.compte", DocumentCible.AGPM, false),
    AGPM_NATURE("agpm.nature", DocumentCible.AGPM, false),
    AGPM_OBJET("agpm.objet", DocumentCible.AGPM, false),
    AGPM_MONT_ESTIM("agpm.montEstim", DocumentCible.AGPM, false),
    AGPM_FINANCEMENT("agpm.financement", DocumentCible.AGPM, false),
    AGPM_MODE("agpm.mode", DocumentCible.AGPM, false),
    AGPM_DATE_DAO("agpm.dateDao", DocumentCible.AGPM, false);

    /** Document officiel qui porte la cellule — servi tel quel en {@code ObservationPvDto.documentCible}. */
    public enum DocumentCible {
        PPM, FICHE, AGPM
    }

    /** Longueur de la colonne {@code CHAMP_CIBLE} (V30) : tous les codes y tiennent, le test le verrouille. */
    public static final int LONGUEUR_MAX = 40;

    private static final Map<String, ChampCible> PAR_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ChampCible::getCode, Function.identity()));

    private final String code;
    private final DocumentCible document;
    private final boolean parBeneficiaire;

    ChampCible(String code, DocumentCible document, boolean parBeneficiaire) {
        this.code = code;
        this.document = document;
        this.parBeneficiaire = parBeneficiaire;
    }

    /** Valeur sérialisée — ex. {@code DEROGATOIRES_MONT_ESTIM} → {@code "derogatoires.montEstim"}. */
    @JsonValue
    public String getCode() {
        return code;
    }

    /** Document qui porte la cellule, déduit du préfixe du code. */
    public DocumentCible document() {
        return document;
    }

    /** Vrai pour les colonnes répétées par bénéficiaire : seules à admettre un {@code idBenefCible}. */
    public boolean parBeneficiaire() {
        return parBeneficiaire;
    }

    /**
     * Le code est-il admis sur un point de cette portée ? PPM sur LIGNE et DOSSIER, fiche sur FICHE, AGPM sur
     * AGPM ; la portée SUPPRESSION (constat d'une ligne retirée) n'accepte aucun champ. Une portée inconnue
     * ({@code null}) n'admet rien — le côté sûr.
     */
    public boolean admisSur(PorteePointCtrl portee) {
        if (portee == null) {
            return false;
        }
        return switch (portee) {
            case LIGNE, DOSSIER -> document == DocumentCible.PPM;
            case FICHE -> document == DocumentCible.FICHE;
            case AGPM -> document == DocumentCible.AGPM;
            case SUPPRESSION -> false;
        };
    }

    /** Code API → valeur ; {@code null} pour un code absent ou inconnu (le validateur répond 400). */
    public static ChampCible depuisCode(String code) {
        return code == null ? null : PAR_CODE.get(code);
    }

    /** Document visé par un code stocké ; {@code null} sans code (ou code inconnu, donnée antérieure). */
    public static DocumentCible documentDuCode(String code) {
        ChampCible champ = depuisCode(code);
        return champ == null ? null : champ.document();
    }
}
