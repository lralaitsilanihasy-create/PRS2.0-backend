package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.service.HeuresOuvrees;

/**
 * ⚠️ <strong>{@code HeuresOuvrees.ajouter}, réciproque de {@code ecoulees}</strong> (demande front du 2026-09-14,
 * accueil « À faire », §4) — test unitaire pur. C'est lui qui date l'<strong>échéance</strong> d'une étape :
 * s'il s'écartait d'une heure de {@code ecoulees}, une tâche « à 0 h » serait déjà en retard, ou l'inverse.
 *
 * <p>Dates de référence : le <strong>lundi 2026-09-07</strong>, le vendredi 11, le samedi 12, le lundi 14.</p>
 */
class HeuresOuvreesAjouterTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 7);
    private static final LocalDate MARDI = LocalDate.of(2026, 9, 8);
    private static final LocalDate VENDREDI = LocalDate.of(2026, 9, 11);
    private static final LocalDate SAMEDI = LocalDate.of(2026, 9, 12);
    private static final LocalDate LUNDI_SUIVANT = LocalDate.of(2026, 9, 14);
    private static final LocalDate MARDI_SUIVANT = LocalDate.of(2026, 9, 15);

    @Test
    @DisplayName("⚠️ PROPRIÉTÉ — ecoulees(d, ajouter(d, h)) == h, et c'est le premier instant qui la vérifie : "
            + "deux semaines de départs (week-ends, nuits, secondes) × 0..60 h")
    void propriete_reciproqueEtMinimale() {
        // Pas de 37 min 13 s : on traverse toutes les positions de la fenêtre, les bords compris, avec des secondes.
        LocalDateTime debut = LocalDate.of(2026, 9, 5).atStartOfDay();   // samedi
        LocalDateTime fin = LocalDate.of(2026, 9, 20).atStartOfDay();
        int verifications = 0;
        for (LocalDateTime d = debut; d.isBefore(fin); d = d.plusMinutes(37).plusSeconds(13)) {
            for (long h = 0; h <= 60; h++) {
                LocalDateTime echeance = HeuresOuvrees.ajouter(d, h);
                assertEquals(h, HeuresOuvrees.ecoulees(d, echeance), "départ " + d + ", " + h + " h → " + echeance);
                if (h > 0) {
                    // Une seconde plus tôt, l'heure n'est pas pleine : l'échéance est bien la plus précoce.
                    assertEquals(h - 1, HeuresOuvrees.ecoulees(d, echeance.minusSeconds(1)),
                            "minimalité : départ " + d + ", " + h + " h → " + echeance);
                }
                verifications++;
            }
        }
        assertTrue(verifications > 30_000, "le balayage doit être dense : " + verifications);
    }

    @Test
    @DisplayName("Dans la journée — l'échéance suit l'horloge")
    void dansLaJournee() {
        assertEquals(LUNDI.atTime(15, 0), HeuresOuvrees.ajouter(LUNDI.atTime(9, 0), 6));
        assertEquals(LUNDI.atTime(10, 30), HeuresOuvrees.ajouter(LUNDI.atTime(9, 30), 1));
    }

    @Test
    @DisplayName("Bornes 08:00 / 16:00 — une journée entière tient de 08:00 à 16:00 du MÊME jour, sans glisser au lendemain")
    void bornesDeLaFenetre() {
        assertEquals(LUNDI.atTime(16, 0), HeuresOuvrees.ajouter(LUNDI.atTime(8, 0), 8));
        assertEquals(LUNDI.atTime(16, 0), HeuresOuvrees.ajouter(LUNDI.atTime(15, 0), 1));
        // Départ à la fermeture : rien ne se consomme avant l'ouverture suivante.
        assertEquals(MARDI.atTime(9, 0), HeuresOuvrees.ajouter(LUNDI.atTime(16, 0), 1));
        // Départ avant l'ouverture : l'échéance compte depuis 08:00.
        assertEquals(LUNDI.atTime(9, 0), HeuresOuvrees.ajouter(LUNDI.atTime(6, 30), 1));
        // Le verrou d'ecoulees, lu à l'envers : lundi 09:00 + 8 h = mardi 09:00.
        assertEquals(MARDI.atTime(9, 0), HeuresOuvrees.ajouter(LUNDI.atTime(9, 0), 8));
        // Départ de nuit : 22:00 + 1 h = lendemain 09:00.
        assertEquals(MARDI.atTime(9, 0), HeuresOuvrees.ajouter(LUNDI.atTime(22, 0), 1));
    }

    @Test
    @DisplayName("Week-ends — vendredi 14:00 + 8 h = lundi 14:00 ; un départ le samedi part du lundi 08:00")
    void weekEnds() {
        // L'exemple de la demande : entrée vendredi 11/09 14:00, standard 8 h → échéance lundi 14/09 14:00.
        assertEquals(LUNDI_SUIVANT.atTime(14, 0), HeuresOuvrees.ajouter(VENDREDI.atTime(14, 0), 8));
        // Et l'autre : entrée vendredi 11/09 12:00, standard 16 h → échéance mardi 15/09 12:00.
        assertEquals(MARDI_SUIVANT.atTime(12, 0), HeuresOuvrees.ajouter(VENDREDI.atTime(12, 0), 16));
        assertEquals(LUNDI_SUIVANT.atTime(10, 0), HeuresOuvrees.ajouter(SAMEDI.atTime(11, 0), 2));
        // Semaine entière : lundi 08:00 + 40 h = vendredi 16:00.
        assertEquals(VENDREDI.atTime(16, 0), HeuresOuvrees.ajouter(LUNDI.atTime(8, 0), 40));
    }

    @Test
    @DisplayName("Dégénérés — 0 ou négatif rend le départ tel quel (même un samedi), départ nul rend nul")
    void degeneres() {
        assertEquals(LUNDI.atTime(9, 0), HeuresOuvrees.ajouter(LUNDI.atTime(9, 0), 0));
        assertEquals(SAMEDI.atTime(9, 0), HeuresOuvrees.ajouter(SAMEDI.atTime(9, 0), 0));
        assertEquals(LUNDI.atTime(9, 0), HeuresOuvrees.ajouter(LUNDI.atTime(9, 0), -3));
        assertNull(HeuresOuvrees.ajouter(null, 5));
    }
}
