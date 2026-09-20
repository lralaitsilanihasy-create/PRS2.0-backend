package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.enums.IntentionAssistant;
import cnm.prs.service.AiguillageAssistantIa.Aiguillage;

/**
 * ⚠️ Assistant IA, lot 4, étape 1 — <strong>l'aiguillage fermé</strong>, sans modèle ni Spring.
 *
 * <p>Ce que ces tests protègent : le modèle <strong>nomme</strong> une intention, il n'ouvre pas une
 * porte. Tout ce qui n'est pas exactement un nom de la liste — une invention, une phrase, un silence,
 * une intention fermée au profil — retombe sur la réponse documentaire, qui ne lit aucune donnée.</p>
 */
class AiguillageAssistantIaTest {

    private static final List<IntentionAssistant> TOUTES = List.of(IntentionAssistant.values());

    // ------------------------------------------------------------------ 1. lire une intention

    @Test
    @DisplayName("Un nom exact est lu ; la casse et les espaces sont tolérés, rien d'autre")
    void nomExact_lu() {
        assertThat(IntentionAssistant.lire("MES_TACHES")).isEqualTo(IntentionAssistant.MES_TACHES);
        assertThat(IntentionAssistant.lire("  mes_taches  ")).isEqualTo(IntentionAssistant.MES_TACHES);
    }

    @Test
    @DisplayName("⚠️ Une intention INVENTÉE, une phrase, un silence : tout retombe sur la réponse "
            + "documentaire — celle qui ne lit aucune donnée")
    void toutLeReste_retombeSurLeRepli() {
        assertThat(IntentionAssistant.lire("SUPPRIMER_DOSSIER")).isEqualTo(IntentionAssistant.REGLE);
        assertThat(IntentionAssistant.lire("MES_TACHES (je pense)")).isEqualTo(IntentionAssistant.REGLE);
        assertThat(IntentionAssistant.lire("Je dirais que l'utilisateur veut ses tâches."))
                .isEqualTo(IntentionAssistant.REGLE);
        assertThat(IntentionAssistant.lire("")).isEqualTo(IntentionAssistant.REGLE);
        assertThat(IntentionAssistant.lire(null)).isEqualTo(IntentionAssistant.REGLE);
    }

    @Test
    @DisplayName("Seule la réponse documentaire ne lit aucune donnée métier")
    void seulLeRepli_neLitAucuneDonnee() {
        assertThat(IntentionAssistant.REGLE.litDesDonnees()).isFalse();
        assertThat(IntentionAssistant.MES_TACHES.litDesDonnees()).isTrue();
        assertThat(IntentionAssistant.ETAT_DOSSIER.litDesDonnees()).isTrue();
    }

    // ------------------------------------------------------------------ 2. ce qui se décide sans modèle

    private final AiguillageAssistantIa aiguillage = new AiguillageAssistantIa(null);

    @Test
    @DisplayName("⚠️ Une référence de dossier décide SEULE : aucune passe de modèle, et la référence "
            + "part telle quelle — ce qui peut être vérifié ne se demande pas")
    void referenceDeDossier_decideSansLeModele() {
        Aiguillage a = aiguillage.sansLeModele("Où en est le 00002/PPM/CNM/2026 ?", TOUTES);

        assertThat(a).isNotNull();
        assertThat(a.intention()).isEqualTo(IntentionAssistant.ETAT_DOSSIER);
        assertThat(a.parametre()).isEqualTo("00002/PPM/CNM/2026");
        assertThat(a.parLeModele()).isFalse();
    }

    @Test
    @DisplayName("La référence espacée à la saisie se recompose : « 00002 / PPM / CNM / 2026 »")
    void referenceEspacee_recomposee() {
        Aiguillage a = aiguillage.sansLeModele("statut du 00002 / PPM / CNM / 2026 ?", TOUTES);

        assertThat(a.parametre()).isEqualTo("00002/PPM/CNM/2026");
    }

    @Test
    @DisplayName("« le dossier n° 100003 » désigne aussi un dossier, sans référence complète")
    void numeroDeDossier_decideSansLeModele() {
        Aiguillage a = aiguillage.sansLeModele("Que devient le dossier n° 100003 ?", TOUTES);

        assertThat(a.intention()).isEqualTo(IntentionAssistant.ETAT_DOSSIER);
        assertThat(a.parametre()).isEqualTo("100003");
    }

    @Test
    @DisplayName("Ce qui ressemble à une date ou à un montant n'est pas pris pour une référence")
    void datesEtMontants_neSontPasDesReferences() {
        assertThat(aiguillage.sansLeModele("Quel délai après le 12/06/2026 ?", TOUTES)).isNull();
        assertThat(aiguillage.sansLeModele("Le seuil est-il de 150 000 000 Ar ?", TOUTES)).isNull();
    }

    @Test
    @DisplayName("Une question qui ne dit rien de net laisse la main au modèle")
    void questionOrdinaire_laisseLaMainAuModele() {
        assertThat(aiguillage.sansLeModele("Qu'est-ce que j'ai à faire aujourd'hui ?", TOUTES)).isNull();
    }

    @Test
    @DisplayName("⚠️ Si l'état d'un dossier est FERMÉ au profil, une référence ne l'ouvre pas : "
            + "le raccourci ne contourne aucune garde")
    void referenceMaisIntentionFermee_pasDeRaccourci() {
        List<IntentionAssistant> sansEtat = List.of(IntentionAssistant.MES_CHIFFRES, IntentionAssistant.REGLE);

        assertThat(aiguillage.sansLeModele("Où en est le 00002/PPM/CNM/2026 ?", sansEtat)).isNull();
    }

    @Test
    @DisplayName("Une question vide se range d'office dans la réponse documentaire")
    void questionVide_repli() {
        Aiguillage a = aiguillage.sansLeModele("   ", TOUTES);

        assertThat(a.intention()).isEqualTo(IntentionAssistant.REGLE);
    }

    // ------------------------------------------------------------------ 3. la consigne de classification

    @Test
    @DisplayName("La consigne ne propose QUE les intentions ouvertes au profil, et interdit d'expliquer")
    void consigne_neProposeQueLOuvert() {
        List<IntentionAssistant> ouvertes = List.of(IntentionAssistant.MES_CHIFFRES, IntentionAssistant.REGLE);

        String consigne = AiguillageAssistantIa.messages("mes chiffres ?", ouvertes).get(0).content();

        assertThat(consigne).contains("MES_CHIFFRES", "REGLE", "UN SEUL MOT");
        assertThat(consigne).doesNotContain("ANNUAIRE", "TABLEAU_DE_BORD", "MES_TACHES");
        assertThat(consigne).contains("N'explique jamais ton choix", "Ne réponds pas à la question");
    }
}
