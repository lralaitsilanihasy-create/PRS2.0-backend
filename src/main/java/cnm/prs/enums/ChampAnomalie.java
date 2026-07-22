package cnm.prs.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ⚠️ Règle ajoutée (2026-07-22) — champ ciblé par une {@code AnomalieTranscription} d'import PPM. Le
 * code JSON est en <strong>camelCase</strong> (aligné sur les champs de {@code MarcheImport}) : le front
 * peut pointer la ligne <em>et</em> le champ exact.
 */
public enum ChampAnomalie {

    OBJET("objet"),
    MONT_ESTIM("montEstim"),
    NOUV_MONT_ESTIM("nouvMontEstim"),
    MODE("mode"),
    NATURE("nature"),
    BENEFICIAIRE("beneficiaire"),
    DATE("date"),
    LOT("lot");

    private final String code;

    ChampAnomalie(String code) {
        this.code = code;
    }

    /** Valeur sérialisée (camelCase) — ex. {@code MONT_ESTIM} → {@code "montEstim"}. */
    @JsonValue
    public String getCode() {
        return code;
    }
}
