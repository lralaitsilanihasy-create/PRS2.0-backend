package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ⚠️ 2026-10-05 (lot 5, §B2.1) — relire un prix écrit en lettres ; aller-retour avec {@link NombreEnLettres#cardinal}. */
class LettresEnNombreTest {

    @Test
    @DisplayName("Aller-retour avec le cardinal de PRS, de 0 aux milliards")
    void allerRetour() {
        for (long n : new long[] { 0, 1, 16, 17, 21, 71, 80, 81, 91, 99, 100, 101, 180, 200, 280, 999, 1000, 1001, 2026, 80_000, 200_000,
                2_450_000, 1_000_000, 3_200_480, 999_999_999, 1_000_000_000L, 2_300_000_000L, 1_234_567_891L }) {
            assertThat(LettresEnNombre.lire(NombreEnLettres.cardinal(n))).as(NombreEnLettres.cardinal(n)).isEqualByComparingTo(BigDecimal.valueOf(n));
        }
    }

    @Test
    @DisplayName("Tolérant : orthographe de 1990, majuscules, accents, « ariary », partie décimale ; illisible → null")
    void tolerant() {
        assertThat(LettresEnNombre.lire("Deux-millions-quatre-cent-cinquante-mille Ariary")).isEqualByComparingTo("2450000");
        assertThat(LettresEnNombre.lire("quatre-vingt-dix-sept")).isEqualByComparingTo("97");
        assertThat(LettresEnNombre.lire("ZÉRO")).isEqualByComparingTo("0");
        assertThat(LettresEnNombre.lire("mille deux cents virgule cinquante")).isEqualByComparingTo("1200.50");
        assertThat(LettresEnNombre.lire("deux mille virgule cinq")).isEqualByComparingTo("2000.5");
        assertThat(LettresEnNombre.lire("deux mille bananes")).isNull();
        assertThat(LettresEnNombre.lire("")).isNull();
        assertThat(LettresEnNombre.lire(null)).isNull();
    }

    @Test
    @DisplayName("Pièce → formulaire, d'après le libellé (§B1.3)")
    void formulaire() {
        assertThat(FormulairesEnLigne.formulaire("Bordereau des prix — lot 1", false)).isEqualTo("BORDEREAU");
        assertThat(FormulairesEnLigne.formulaire("Bordereau des prix unitaires", true)).isEqualTo("DQE");
        assertThat(FormulairesEnLigne.formulaire("Détail quantitatif et estimatif", true)).isEqualTo("DQE");
        assertThat(FormulairesEnLigne.formulaire("Sous-détail des prix", true)).isEqualTo("SOUS_DETAIL");
        assertThat(FormulairesEnLigne.formulaire("Tableau de conformité technique", false)).isEqualTo("CONFORMITE");
        assertThat(FormulairesEnLigne.formulaire("Calendrier de livraison", false)).isEqualTo("CALENDRIER");
        assertThat(FormulairesEnLigne.formulaire("Attestation de chiffre d'affaires", true)).isEqualTo("CAPACITES");
        assertThat(FormulairesEnLigne.formulaire("Liste du personnel clé", true)).isEqualTo("PERSONNEL");
        assertThat(FormulairesEnLigne.formulaire("Liste du matériel", true)).isEqualTo("MATERIEL");
        assertThat(FormulairesEnLigne.formulaire("Calcul du coefficient K1", true)).isEqualTo("K1");
        assertThat(FormulairesEnLigne.formulaire("Attestation fiscale", true)).isNull();
        assertThat(FormulairesEnLigne.formulaire("Liste du personnel clé", false)).isNull();   // fournitures : pas de formulaire
    }
}
