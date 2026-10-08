package cnm.prs.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * ⚠️ 2026-10-08 (demande front « manuel de contrôle a priori », tranche M1, §B1 ; V94 ; arbitrages du pilote : {@code DAO} renommé
 * {@code DAOO}, variantes internationales en sous-types propres, codes sans accent, sous-type déduit du mode du plan) — les sous-types
 * de dossier du <em>Manuel de contrôle a priori</em> (CNM, février 2026) que l'application produit d'elle-même, et la règle qui les
 * déduit du mode de passation de la ligne du plan ({@code MODE}) et de la catégorie de la fiche.
 * <p>
 * Le <strong>type de DMC</strong> « DAO » ({@link DmcService#TYPE_DAO}, {@code t_type_dmc}) n'est pas un sous-type de dossier : il ne
 * change pas.
 */
public final class SousTypesDossier {

    // Famille DMC — les dossiers de mise en concurrence produits par une fiche marché.
    public static final String DAOO = "DAOO";
    public static final String DAOR = "DAOR";
    public static final String DAOOI = "DAOOI";
    public static final String DAORI = "DAORI";
    public static final String DAOOPREQUAL = "DAOOPREQUAL";
    public static final String DC = "DC";

    // Famille DDM — les dossiers de marché produits par l'attribution.
    public static final String MAOO = "MAOO";
    public static final String MAOR = "MAOR";
    public static final String MAOOI = "MAOOI";
    public static final String MAORI = "MAORI";
    public static final String MAOOPREQUAL = "MAOOPREQUAL";
    public static final String MPI = "MPI";

    /** Les sous-types qu'une fiche marché produit (le dossier porte alors {@code ID_DMC}). */
    public static final Set<String> PRODUITS_PAR_FICHE = Set.of(DAOO, DAOR, DAOOI, DAORI, DAOOPREQUAL, DC);

    private SousTypesDossier() {
    }

    /**
     * Le sous-type du dossier de mise en concurrence d'une fiche : prestations intellectuelles → {@code DC} ; mode « … international »
     * → {@code DAORI} (restreint) ou {@code DAOOI} ; « … pré-qualification » → {@code DAOOPREQUAL} ; « restreint » → {@code DAOR} ;
     * sinon {@code DAOO}.
     */
    public static String dossierMiseEnConcurrence(String mode, boolean prestationsIntellectuelles) {
        if (prestationsIntellectuelles) {
            return DC;
        }
        String m = normaliser(mode);
        boolean restreint = m.contains("restreint");
        if (m.contains("international")) {
            return restreint ? DAORI : DAOOI;
        }
        if (m.contains("pre-qualification") || m.contains("prequalification") || m.contains("pre qualification")) {
            return DAOOPREQUAL;
        }
        return restreint ? DAOR : DAOO;
    }

    /** Le sous-type du dossier de marché, par la même règle : {@code MPI}, {@code MAORI}, {@code MAOOI}, {@code MAOOPREQUAL}, {@code MAOR}, {@code MAOO}. */
    public static String dossierMarche(String mode, boolean prestationsIntellectuelles) {
        return switch (dossierMiseEnConcurrence(mode, prestationsIntellectuelles)) {
            case DC -> MPI;
            case DAORI -> MAORI;
            case DAOOI -> MAOOI;
            case DAOOPREQUAL -> MAOOPREQUAL;
            case DAOR -> MAOR;
            default -> MAOO;
        };
    }

    /** Minuscules, sans accent. */
    static String normaliser(String s) {
        return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
