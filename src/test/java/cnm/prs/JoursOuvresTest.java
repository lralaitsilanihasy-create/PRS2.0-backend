package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.service.JoursOuvres;

/**
 * ⚠️ Arithmétique du CALENDRIER ouvré (arbitrage ③, 2026-09-01) — test unitaire pur : aucune base, aucun
 * contexte Spring. C'est le socle de tout le chronométrage, il doit se vérifier en millisecondes.
 *
 * <p>Les dates de référence sont choisies pour être lisibles : le <strong>lundi 2026-09-07</strong> ouvre
 * une semaine complète, le samedi 12 et le dimanche 13 la ferment.</p>
 */
class JoursOuvresTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 7);
    private static final LocalDate VENDREDI = LocalDate.of(2026, 9, 11);
    private static final LocalDate SAMEDI = LocalDate.of(2026, 9, 12);
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 13);
    private static final LocalDate LUNDI_SUIVANT = LocalDate.of(2026, 9, 14);

    @Test
    @DisplayName("Samedi et dimanche ne sont pas ouvrés ; du lundi au vendredi le sont")
    void weekEndExclu() {
        assertTrue(JoursOuvres.estOuvre(LUNDI));
        assertTrue(JoursOuvres.estOuvre(VENDREDI));
        assertFalse(JoursOuvres.estOuvre(SAMEDI));
        assertFalse(JoursOuvres.estOuvre(DIMANCHE));
    }

    @Test
    @DisplayName("Le week-end ne compte pas : du vendredi au lundi suivant il n'y a qu'UN jour ouvré")
    void weekEndNeComptePas() {
        assertEquals(1L, JoursOuvres.entre(VENDREDI, LUNDI_SUIVANT));
        assertEquals(4L, JoursOuvres.entre(LUNDI, VENDREDI));
    }

    @Test
    @DisplayName("Ajout de jours ouvrés — un vendredi + 1 tombe le lundi, jamais le samedi")
    void ajoutEnjambeLeWeekEnd() {
        assertEquals(LUNDI_SUIVANT, JoursOuvres.ajouter(VENDREDI, 1));
        assertEquals(VENDREDI, JoursOuvres.ajouter(LUNDI, 4));
    }

    @Test
    @DisplayName("Ajouter 0 rend la date de départ TELLE QUELLE, même un samedi — on ne déplace pas une échéance non demandée")
    void ajoutNulNeDeplacePas() {
        assertEquals(SAMEDI, JoursOuvres.ajouter(SAMEDI, 0));
        assertEquals(LUNDI, JoursOuvres.ajouter(LUNDI, 0));
    }

    @Test
    @DisplayName("Un intervalle négatif rend 0 : une incohérence de données n'est pas un crédit de temps")
    void intervalleNegatifVautZero() {
        assertEquals(0L, JoursOuvres.entre(VENDREDI, LUNDI));
    }

    @Test
    @DisplayName("Nuls tolérés partout — le chronométrage ne doit jamais lever sur une donnée absente")
    void nullsToleres() {
        assertEquals(0L, JoursOuvres.entre(null, LUNDI));
        org.junit.jupiter.api.Assertions.assertNull(JoursOuvres.ajouter(null, 3));
    }
}
