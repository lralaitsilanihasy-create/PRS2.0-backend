package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.PieceSousType;

/** ⚠️ M2 (manuel de contrôle, §B2) — la condition d'une pièce sur la catégorie et la forme de la fiche. */
class PiecesExigeesTest {

    private static PieceSousType piece(String categorie, String forme) {
        return new PieceSousType(1, "DAOO", true, 1, categorie, forme);
    }

    @Test
    @DisplayName("Sans condition : toujours ; catégorie : seulement la sienne ; forme : contrat-cadre ou les autres ; sans fiche : inconnu")
    void conditions() {
        PiecesExigees.Contexte fs = new PiecesExigees.Contexte("FOURNITURES_SERVICES", "QUANTITE_FIXE");
        PiecesExigees.Contexte travauxCadre = new PiecesExigees.Contexte("TRAVAUX", "CONTRAT_CADRE");
        PiecesExigees.Contexte inconnu = new PiecesExigees.Contexte(null, null);
        assertThat(PiecesExigees.vaut(piece(null, null), inconnu)).isTrue();
        assertThat(PiecesExigees.vaut(piece("FOURNITURES_SERVICES", null), fs)).isTrue();
        assertThat(PiecesExigees.vaut(piece("TRAVAUX", null), fs)).isFalse();
        assertThat(PiecesExigees.vaut(piece("TRAVAUX", null), inconnu)).isNull();
        assertThat(PiecesExigees.vaut(piece(null, "AUTRE"), fs)).isTrue();
        assertThat(PiecesExigees.vaut(piece(null, "AUTRE"), travauxCadre)).isFalse();
        assertThat(PiecesExigees.vaut(piece(null, "CONTRAT_CADRE"), travauxCadre)).isTrue();
        assertThat(PiecesExigees.vaut(piece("TRAVAUX", "AUTRE"), travauxCadre)).isFalse();
        assertThat(PiecesExigees.vaut(piece(null, "AUTRE"), inconnu)).isNull();
    }
}
