package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ V50 (2026-09-27, remise électronique, §B4) — le corps de {@code PUT /api/fiches-marche/{idDmc}/parametres-internes} :
 * les matricules des membres détenteurs d'une part de clé, le quorum et la date-heure de la cérémonie
 * ({@code AAAA-MM-JJTHH:MM}). 400 nominatifs sous {@code membresCommission}, {@code quorum}, {@code dateCeremonie}.
 * <p>
 * ⚠️ V66 (2026-10-04, soumission en ligne, lot 2, §B1) — {@code depositaire} : le dépositaire de la part de secours
 * ({@code nom} obligatoire s'il est donné ; {@code null} : aucun dépositaire).
 */
public record ParametresInternesRequest(List<String> membresCommission, Integer quorum, String dateCeremonie,
        CeremonieDto.Depositaire depositaire,
        /** ⚠️ R1 (manuel révisé, V101) — la surcharge de la règle de quorum de la CAO (MAJORITE, TOUS, nombre) ; nul ou vide : la règle globale. */
        String caoQuorum,
        /** ⚠️ R10 (manuel révisé, V113) — SCELLE_PLUS_ORIGINAL ou CERTIFIEE (réservé) ; nul ou vide : le défaut, SCELLE_PLUS_ORIGINAL. */
        String signatureOffre) {

    public ParametresInternesRequest(List<String> membresCommission, Integer quorum, String dateCeremonie) {
        this(membresCommission, quorum, dateCeremonie, null, null, null);
    }

    public ParametresInternesRequest(List<String> membresCommission, Integer quorum, String dateCeremonie, CeremonieDto.Depositaire depositaire) {
        this(membresCommission, quorum, dateCeremonie, depositaire, null, null);
    }

    public ParametresInternesRequest(List<String> membresCommission, Integer quorum, String dateCeremonie, CeremonieDto.Depositaire depositaire,
            String caoQuorum) {
        this(membresCommission, quorum, dateCeremonie, depositaire, caoQuorum, null);
    }
}
