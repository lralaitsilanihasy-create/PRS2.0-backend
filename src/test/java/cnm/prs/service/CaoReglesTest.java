package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.CaoDto;
import cnm.prs.entity.Cao;
import cnm.prs.entity.CaoMembre;

/** ⚠️ 2026-10-28 (demande front « decision-cao-obligatoire », §B1, §B2) — la décision n'est complète qu'avec son PDF signé. */
class CaoReglesTest {

    @Test
    @DisplayName("Sans PDF : non constituée, INCOMPLETE, DECISION_SANS_FICHIER parmi ce qui manque ; avec : COMPLETE")
    void pdfObligatoire() {
        Cao cao = decision("DEC-001/2026", LocalDate.of(2026, 9, 30));
        List<CaoMembre> membres = List.of(membre(true), membre(false));
        assertThat(CaoRegles.constituee(cao, membres)).isFalse();
        assertThat(CaoRegles.etat(cao, membres)).isEqualTo(CaoRegles.INCOMPLETE);
        assertThat(CaoRegles.manques(cao, membres)).extracting(CaoDto.Anomalie::regle).containsExactly(CaoRegles.DECISION_SANS_FICHIER);
        assertThat(CaoRegles.manques(cao, membres)).extracting(CaoDto.Anomalie::message)
                .containsExactly("La décision de nomination de la commission n'est pas jointe (PDF signé).");

        cao.setDecisionFichier("%PDF-1.4".getBytes());
        cao.setDecisionTaille(8L);
        assertThat(CaoRegles.constituee(cao, membres)).isTrue();
        assertThat(CaoRegles.etat(cao, membres)).isEqualTo(CaoRegles.COMPLETE);
        assertThat(CaoRegles.anomalies(cao, membres, Map.of())).extracting(CaoDto.Anomalie::regle)
                .containsExactly(CaoRegles.COMPTES_NON_ACTIVES);
    }

    @Test
    @DisplayName("Chaque manque nommé, dans l'ordre : référence, date, PDF, membres, président ; le message de SE_CAO les reprend")
    void chaqueManqueNomme() {
        Cao cao = decision(" ", null);
        List<String> manques = CaoRegles.manques(cao, List.of(membre(false))).stream().map(CaoDto.Anomalie::message).toList();
        assertThat(manques).containsExactly("La décision de nomination n'a pas de référence.", "La décision de nomination n'a pas de date.",
                "La décision de nomination de la commission n'est pas jointe (PDF signé).", "Au moins deux membres sont attendus.",
                "Aucun président n'est désigné parmi les membres.");
        assertThat(CaoRegles.manques(null, List.of())).extracting(CaoDto.Anomalie::message)
                .containsExactly("La commission d'appel d'offres n'est pas encore désignée.");
        assertThat(RemiseElectronique.messageCao(List.of("La décision de nomination n'a pas de date.")))
                .isEqualTo("La décision de nomination n'a pas de date. " + RemiseElectronique.MESSAGE_CAO);
    }

    private static Cao decision(String reference, LocalDate date) {
        Cao cao = new Cao();
        cao.setIdDmc(1L);
        cao.setDecisionReference(reference);
        cao.setDecisionDate(date);
        return cao;
    }

    private static CaoMembre membre(boolean president) {
        CaoMembre m = new CaoMembre();
        m.setQualite(CaoMembre.MEMBRE);
        m.setPresident(president);
        return m;
    }
}
