package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.PointsCtrl;

/** ⚠️ 2026-10-18 (demande front « grille-contrat-cadre », §B2) — les codes de la fiche lus dans le texte d'un point. */
class CodesCitesPointsCtrlTest {

    @Test
    @DisplayName("Bloc, rubrique et champ ; ni article de loi, ni code collé à un mot ; sans doublon, dans l'ordre")
    void codes() {
        PointsCtrl p = new PointsCtrl();
        p.setLibelPointCtrl("Montants (B05-MT-01)");
        p.setDecriptPointCtrl("Avance (B08-AV, B08-FI-02), besoin (B12), lot B2B, AB12-CD-01, art. 19 ; rappel B05-MT-01 ?");
        assertThat(CodesCitesPointsCtrl.codes(p)).containsExactly("B05-MT-01", "B08-AV", "B08-FI-02", "B12");
    }
}
