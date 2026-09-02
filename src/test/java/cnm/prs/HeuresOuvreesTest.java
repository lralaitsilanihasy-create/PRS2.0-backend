package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.service.HeuresOuvrees;

/**
 * ⚠️ <strong>Chronométrage en heures ouvrées</strong> (règle du pilote, 2026-09-02) — test unitaire pur :
 * aucune base, aucun contexte Spring. C'est le socle de la nouvelle unité, il doit se vérifier en
 * millisecondes.
 *
 * <p>Ce que ces cas protègent avant tout, c'est le <strong>piège d'échelle</strong> : l'écoulé doit se
 * mesurer dans la même unité que la prévision, sans quoi une tâche prise en charge la veille passerait
 * pour être en dépassement. Le cas <em>lundi 09:00 → mardi 09:00 = 8 h</em> est le verrou : un plafond
 * journalier y aurait rendu 16 h.</p>
 *
 * <p>Dates de référence : le <strong>lundi 2026-09-07</strong> ouvre une semaine complète, le samedi 12
 * et le dimanche 13 la ferment.</p>
 */
class HeuresOuvreesTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 7);
    private static final LocalDate MARDI = LocalDate.of(2026, 9, 8);
    private static final LocalDate VENDREDI = LocalDate.of(2026, 9, 11);
    private static final LocalDate SAMEDI = LocalDate.of(2026, 9, 12);
    private static final LocalDate LUNDI_SUIVANT = LocalDate.of(2026, 9, 14);

    @Test
    @DisplayName("8 heures ouvrées = 1 jour ouvré — le taux de l'arbitrage ②")
    void tauxDeConversion() {
        assertEquals(8, HeuresOuvrees.HEURES_PAR_JOUR);
        // La fenêtre de service doit valoir exactement une journée : de 08:00 à 16:00 sur un jour ouvré.
        assertEquals(8L, HeuresOuvrees.ecoulees(LUNDI.atTime(8, 0), LUNDI.atTime(16, 0)));
    }

    @Test
    @DisplayName("Dans la journée — l'écoulé suit l'horloge tant qu'on reste dans la fenêtre")
    void ecouleDansLaJournee() {
        assertEquals(6L, HeuresOuvrees.ecoulees(LUNDI.atTime(9, 0), LUNDI.atTime(15, 0)));
        assertEquals(0L, HeuresOuvrees.ecoulees(LUNDI.atTime(9, 0), LUNDI.atTime(9, 30)));
        assertEquals(1L, HeuresOuvrees.ecoulees(LUNDI.atTime(9, 0), LUNDI.atTime(10, 0)));
    }

    @Test
    @DisplayName("⚠️ LE VERROU — lundi 09:00 → mardi 09:00 = 8 h, soit UN jour ouvré (un plafond journalier aurait dit 16 h)")
    void veilleAuLendemain_exactementUneJournee() {
        // 7 h le lundi (09:00 → 16:00) + 1 h le mardi (08:00 → 09:00). Une prévision de 8 h est donc
        // exactement consommée : la tâche n'est PAS en dépassement, ce que la règle exige.
        assertEquals(8L, HeuresOuvrees.ecoulees(LUNDI.atTime(9, 0), MARDI.atTime(9, 0)));
    }

    @Test
    @DisplayName("Hors fenêtre — une heure où personne ne travaille ne compte pas")
    void horsFenetreNeCompte() {
        // Prise en charge à 22:00 : rien avant l'ouverture du lendemain.
        assertEquals(1L, HeuresOuvrees.ecoulees(LUNDI.atTime(22, 0), MARDI.atTime(9, 0)));
        // Intervalle entièrement nocturne.
        assertEquals(0L, HeuresOuvrees.ecoulees(LUNDI.atTime(18, 0), LUNDI.atTime(23, 0)));
        // Intervalle entièrement dans le week-end.
        assertEquals(0L, HeuresOuvrees.ecoulees(SAMEDI.atTime(9, 0), SAMEDI.atTime(17, 0)));
    }

    @Test
    @DisplayName("Week-end enjambé — vendredi 15:00 → lundi 09:00 = 2 h, pas deux jours")
    void weekEndEnjambe() {
        assertEquals(2L, HeuresOuvrees.ecoulees(VENDREDI.atTime(15, 0), LUNDI_SUIVANT.atTime(9, 0)));
    }

    @Test
    @DisplayName("Semaine entière — lundi 08:00 → vendredi 16:00 = 40 h, soit 5 jours ouvrés")
    void semaineEntiere() {
        assertEquals(40L, HeuresOuvrees.ecoulees(LUNDI.atTime(8, 0), VENDREDI.atTime(16, 0)));
        assertEquals(5L, HeuresOuvrees.enJoursArrondiSuperieur(40L));
    }

    @Test
    @DisplayName("Intervalle négatif ou nul, et nuls : 0 — une incohérence n'est pas un crédit de temps")
    void intervallesDegeneres() {
        assertEquals(0L, HeuresOuvrees.ecoulees(MARDI.atTime(9, 0), LUNDI.atTime(9, 0)));
        assertEquals(0L, HeuresOuvrees.ecoulees(LUNDI.atTime(9, 0), LUNDI.atTime(9, 0)));
        assertEquals(0L, HeuresOuvrees.ecoulees(null, LUNDI.atTime(9, 0)));
        assertEquals(0L, HeuresOuvrees.ecoulees(LUNDI.atTime(9, 0), null));
    }

    @Test
    @DisplayName("Conversion en jours — arrondi au SUPÉRIEUR : une journée entamée compte pleine")
    void arrondiAuJourSuperieur() {
        assertEquals(0L, HeuresOuvrees.enJoursArrondiSuperieur(0L));
        assertEquals(1L, HeuresOuvrees.enJoursArrondiSuperieur(1L));
        assertEquals(1L, HeuresOuvrees.enJoursArrondiSuperieur(8L));
        assertEquals(2L, HeuresOuvrees.enJoursArrondiSuperieur(9L));
        assertEquals(2L, HeuresOuvrees.enJoursArrondiSuperieur(16L));
        assertEquals(14L, HeuresOuvrees.enJoursArrondiSuperieur(112L));
        // Un total négatif (dépassement déjà ramené à 0 en amont) ne recule jamais la date.
        assertEquals(0L, HeuresOuvrees.enJoursArrondiSuperieur(-5L));
    }

    @Test
    @DisplayName("Invariance de la bascule — les 112 h du seed valent les 14 jours d'avant le 02/09")
    void invarianceDeLaBascule() {
        // Seed converti x 8 : RECEPTION 8 + DISPATCH 8 + EXAMEN 40 + VISA 16 + COSIGNATURE 8
        // + VERIFICATION 24 + TRANSMISSION_SIGMP 8 = 112 h. ARCHIVAGE (16 h) est hors compteur global.
        long totalHeures = 8 + 8 + 40 + 16 + 8 + 24 + 8;
        assertEquals(112L, totalHeures);
        assertEquals(14L, HeuresOuvrees.enJoursArrondiSuperieur(totalHeures),
                "la bascule d'unité ne doit déplacer aucune date annoncée à la PRMP");
    }

    @Test
    @DisplayName("Heures pleines seulement — 90 minutes valent 1 h, jamais 2")
    void arrondiHoraireInferieur() {
        LocalDateTime debut = LUNDI.atTime(9, 0);
        assertEquals(1L, HeuresOuvrees.ecoulees(debut, LUNDI.atTime(10, 30)));
        assertEquals(2L, HeuresOuvrees.ecoulees(debut, LUNDI.atTime(11, 0)));
    }
}
