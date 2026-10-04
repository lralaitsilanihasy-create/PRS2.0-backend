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
        CeremonieDto.Depositaire depositaire) {

    public ParametresInternesRequest(List<String> membresCommission, Integer quorum, String dateCeremonie) {
        this(membresCommission, quorum, dateCeremonie, null);
    }
}
