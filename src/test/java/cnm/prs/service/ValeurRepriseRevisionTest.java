package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.ChampFicheMarche;

/**
 * ⚠️ Arbitrage du pilote du 2026-09-25 (formulaires du candidat, §B2) — une version validée n'est jamais convertie ; la
 * révision ne reprend que les valeurs que le référentiel d'aujourd'hui admet encore.
 */
class ValeurRepriseRevisionTest {

    private static ChampFicheMarche champ(String code, String type, String options, boolean parLot, boolean actif) {
        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode(code);
        c.setType(type);
        c.setOptions(options);
        c.setParLot(parLot);
        c.setActif(actif);
        return c;
    }

    private final Map<String, ChampFicheMarche> referentiel = new LinkedHashMap<>();
    {
        referentiel.put("B02-AU-03", champ("B02-AU-03", "TEXTE_LONG", null, false, false));
        referentiel.put("B04-CD-01", champ("B04-CD-01", "LISTE_MULTIPLE", "A1,A2,A3,A4", false, true));
        referentiel.put("B04-CD-02", champ("B04-CD-02", "LISTE", "C1,C2,C1 et C2", false, true));
        referentiel.put("B09-LL-01", champ("B09-LL-01", "TEXTE_LONG", null, true, true));
        referentiel.put("B05-TP-02", champ("B05-TP-02", "MONTANT", null, true, true));
        referentiel.put("B02-AU-04", champ("B02-AU-04", "NOMBRE", null, false, true));
    }

    @Test
    @DisplayName("Non reprises : valeur orpheline (champ désactivé ou inconnu), texte libre dans une liste, clé nue d'un champ "
            + "devenu par lot sur une ligne allotie ; reprises : option valide, clé par lot, champ ordinaire")
    void reprise() {
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B02-AU-03", "100 à 500 unités", 5)).isFalse();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B99-XX-01", "x", 5)).isFalse();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B04-CD-01", "Modèles A1 (identification)…", 5)).isFalse();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B04-CD-01", "A1,A3", 5)).isTrue();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B04-CD-02", "C1 — garantie bancaire", 5)).isFalse();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B04-CD-02", "C1 et C2", 5)).isTrue();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B09-LL-01", "Lot 1 : Ministère — lots 2 et 3 : …", 5)).isFalse();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B09-LL-01#2", "Ambatondrazaka", 5)).isTrue();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B09-LL-01", "Antananarivo", 0)).isTrue();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B05-TP-02#1", "1000", 0)).isFalse();
        assertThat(FicheMarcheService.valeurReprise(referentiel, "B02-AU-04", "12", 5)).isTrue();
    }

    @Test
    @DisplayName("Ordinal en lettres : premier, cinquième, cent cinquième, vingt et unième, quatre-vingtième, deux centième")
    void ordinal() {
        assertThat(NombreEnLettres.ordinal(1)).isEqualTo("premier");
        assertThat(NombreEnLettres.ordinal(5)).isEqualTo("cinquième");
        assertThat(NombreEnLettres.ordinal(105)).isEqualTo("cent cinquième");
        assertThat(NombreEnLettres.ordinal(21)).isEqualTo("vingt et unième");
        assertThat(NombreEnLettres.ordinal(80)).isEqualTo("quatre-vingtième");
        assertThat(NombreEnLettres.ordinal(200)).isEqualTo("deux centième");
        assertThat(NombreEnLettres.ordinal(90)).isEqualTo("quatre-vingt-dixième");
    }
}
