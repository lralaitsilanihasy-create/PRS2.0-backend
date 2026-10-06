package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.BilanControlesDto;

/** ⚠️ 2026-10-06 (décision du pilote, « A ») — {@code SE_PIECES_LISTEES} : en remise électronique, la liste B14 n'est pas vide. */
class PiecesListeesTest {

    private static BilanControlesDto bilan() {
        return new BilanControlesDto(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0, 0);
    }

    @Test
    @DisplayName("Électronique sans liste : bloquant (le texte n'y change rien) ; avec une pièce, quelle que soit sa rubrique : ok")
    void electronique() {
        BilanControlesDto b = bilan();
        ControlesFicheMarche.piecesListees(Map.of("modeRemise", "ELECTRONIQUE"), 0, b);
        assertThat(b.bloquants()).extracting(BilanControlesDto.Controle::regle).containsExactly("SE_PIECES_LISTEES");
        assertThat(b.bloquants().get(0).message()).isEqualTo(ControlesFicheMarche.MESSAGE_PIECES_LISTEES);
        assertThat(b.bloquants().get(0).bloc()).isEqualTo("B14");
        b = bilan();
        ControlesFicheMarche.piecesListees(Map.of("modeRemise", "ELECTRONIQUE"), 1, b);
        assertThat(b.bloquants()).isEmpty();
        assertThat(b.ok()).extracting(BilanControlesDto.Controle::regle).containsExactly("SE_PIECES_LISTEES");
    }

    @Test
    @DisplayName("Papier, ou sans cadrage : muette (PIECES_OFFRE_EXIGEES suffit, liste ou texte)")
    void papier() {
        BilanControlesDto b = bilan();
        ControlesFicheMarche.piecesListees(Map.of("modeRemise", "PAPIER"), 0, b);
        ControlesFicheMarche.piecesListees(null, 0, b);
        assertThat(b.bloquants()).isEmpty();
        assertThat(b.ok()).isEmpty();
    }
}
