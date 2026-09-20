package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.enums.IntentionAssistant;
import cnm.prs.service.AiguillageAssistantIa.Aiguillage;

/**
 * ⚠️ Assistant IA, lot 4, étape 1 — <strong>l'aiguillage</strong>, sans Spring ni modèle.
 *
 * <p>Ces tests tiennent lieu de <strong>batterie</strong> : l'aiguillage est déterministe (voir la
 * décision mesurée dans {@link AiguillageAssistantIa}), donc ce qui mesurait la compréhension du modèle
 * mesure ici des règles — et peut tourner à chaque build, sans serveur d'inférence.</p>
 *
 * <p>Ce qu'ils protègent : une question non reconnue retombe sur la réponse documentaire, qui ne lit
 * aucune donnée ; une intention fermée au profil n'est jamais choisie ; et ce qui désigne un dossier
 * sans ambiguïté n'a pas besoin d'être deviné.</p>
 */
class AiguillageAssistantIaTest {

    private static final List<IntentionAssistant> TOUTES = List.of(IntentionAssistant.values());
    /** Les intentions d'un contrôleur : ni annuaire, ni vue d'ensemble. */
    private static final List<IntentionAssistant> CONTROLEUR = List.of(
            IntentionAssistant.REGLE, IntentionAssistant.MES_CHIFFRES, IntentionAssistant.MES_TACHES,
            IntentionAssistant.TROUVER_DOSSIER, IntentionAssistant.ETAT_DOSSIER);

    private final AiguillageAssistantIa aiguillage = new AiguillageAssistantIa();

    private IntentionAssistant intention(String question) {
        return aiguillage.decider(question, CONTROLEUR).intention();
    }

    // ------------------------------------------------------------------ 1. lire une intention

    @Test
    @DisplayName("Un nom exact est lu ; la casse et les espaces sont tolérés, rien d'autre")
    void nomExact_lu() {
        assertThat(IntentionAssistant.lire("MES_TACHES")).isEqualTo(IntentionAssistant.MES_TACHES);
        assertThat(IntentionAssistant.lire("  mes_taches  ")).isEqualTo(IntentionAssistant.MES_TACHES);
    }

    @Test
    @DisplayName("Une intention inconnue retombe sur la réponse documentaire")
    void nomInconnu_retombeSurLeRepli() {
        assertThat(IntentionAssistant.lire("SUPPRIMER_DOSSIER")).isEqualTo(IntentionAssistant.REGLE);
        assertThat(IntentionAssistant.lire(null)).isEqualTo(IntentionAssistant.REGLE);
    }

    @Test
    @DisplayName("Seule la réponse documentaire ne lit aucune donnée métier")
    void seulLeRepli_neLitAucuneDonnee() {
        assertThat(IntentionAssistant.REGLE.litDesDonnees()).isFalse();
        assertThat(IntentionAssistant.MES_TACHES.litDesDonnees()).isTrue();
    }

    // ------------------------------------------------------------------ 2. la référence décide seule

    @Test
    @DisplayName("⚠️ Une référence de dossier décide SEULE, et part telle quelle")
    void referenceDeDossier_decideSeule() {
        Aiguillage a = aiguillage.decider("Où en est le 00002/PPM/CNM/2026 ?", CONTROLEUR);

        assertThat(a.intention()).isEqualTo(IntentionAssistant.ETAT_DOSSIER);
        assertThat(a.parametre()).isEqualTo("00002/PPM/CNM/2026");
        assertThat(a.surReference()).isTrue();
    }

    @Test
    @DisplayName("La référence espacée à la saisie se recompose : « 00002 / PPM / CNM / 2026 »")
    void referenceEspacee_recomposee() {
        assertThat(aiguillage.decider("statut du 00002 / PPM / CNM / 2026 ?", CONTROLEUR).parametre())
                .isEqualTo("00002/PPM/CNM/2026");
    }

    @Test
    @DisplayName("« le dossier n° 100003 » désigne aussi un dossier, sans référence complète")
    void numeroDeDossier_decideSeul() {
        Aiguillage a = aiguillage.decider("Que devient le dossier n° 100003 ?", CONTROLEUR);

        assertThat(a.intention()).isEqualTo(IntentionAssistant.ETAT_DOSSIER);
        assertThat(a.parametre()).isEqualTo("100003");
    }

    @Test
    @DisplayName("Ce qui ressemble à une date ou à un montant n'est pas pris pour une référence")
    void datesEtMontants_neSontPasDesReferences() {
        assertThat(intention("Quel délai après le 12/06/2026 ?")).isEqualTo(IntentionAssistant.REGLE);
        assertThat(intention("Le seuil est-il de 150 000 000 Ar ?")).isEqualTo(IntentionAssistant.REGLE);
    }

    @Test
    @DisplayName("⚠️ Si l'état d'un dossier est FERMÉ au profil, une référence ne l'ouvre pas")
    void referenceMaisIntentionFermee_pasDeRaccourci() {
        List<IntentionAssistant> sansEtat = List.of(IntentionAssistant.MES_CHIFFRES, IntentionAssistant.REGLE);

        assertThat(aiguillage.decider("Où en est le 00002/PPM/CNM/2026 ?", sansEtat).intention())
                .isEqualTo(IntentionAssistant.REGLE);
    }

    // ------------------------------------------------------------------ 3. la batterie des tournures

    @Test
    @DisplayName("Les tournures de travail sont reconnues : ce qu'on dit vraiment à un assistant")
    void tournuresDeTravail_reconnues() {
        assertThat(intention("Qu'est-ce que j'ai à faire aujourd'hui ?")).isEqualTo(IntentionAssistant.MES_TACHES);
        assertThat(intention("Quels dossiers m'attendent ?")).isEqualTo(IntentionAssistant.MES_TACHES);
        assertThat(intention("mes taches du jour")).isEqualTo(IntentionAssistant.MES_TACHES);

        assertThat(intention("Combien de dossiers sont en retard chez moi ?"))
                .isEqualTo(IntentionAssistant.MES_CHIFFRES);
        assertThat(intention("Donne-moi mes compteurs")).isEqualTo(IntentionAssistant.MES_CHIFFRES);

        assertThat(intention("Retrouve les dossiers du ministère des Travaux publics"))
                .isEqualTo(IntentionAssistant.TROUVER_DOSSIER);
        assertThat(intention("Je cherche un dossier de la commune d'Ambohimanga"))
                .isEqualTo(IntentionAssistant.TROUVER_DOSSIER);
    }

    @Test
    @DisplayName("⚠️ Les questions de RÈGLE restent des questions de règle : elles ne partent jamais en "
            + "lecture de données — c'est le lot 1, et il ne doit pas bouger")
    void questionsDeRegle_nePartentPasEnLecture() {
        assertThat(intention("Quel est le délai d'examen d'un dossier ?")).isEqualTo(IntentionAssistant.REGLE);
        assertThat(intention("Qu'est-ce qu'un fractionnement illicite ?")).isEqualTo(IntentionAssistant.REGLE);
        assertThat(intention("Quelles pièces faut-il joindre à un plan de passation ?"))
                .isEqualTo(IntentionAssistant.REGLE);
        assertThat(intention("Bonjour, comment ça va ?")).isEqualTo(IntentionAssistant.REGLE);
    }

    @Test
    @DisplayName("Les accents manquants ne changent rien : une question se tape vite")
    void sansAccents_memeResultat() {
        assertThat(intention("qu est-ce que j ai a faire ?")).isEqualTo(IntentionAssistant.MES_TACHES);
        assertThat(intention("combien de dossiers en retard")).isEqualTo(IntentionAssistant.MES_CHIFFRES);
    }

    @Test
    @DisplayName("Une intention fermée au profil n'est jamais choisie : l'annuaire reste à l'Administrateur")
    void intentionFermee_jamaisChoisie() {
        assertThat(intention("Qui est la PRMP du ministère des Travaux publics ?"))
                .isEqualTo(IntentionAssistant.REGLE);
        assertThat(aiguillage.decider("Qui est la PRMP du ministère ?", TOUTES).intention())
                .isEqualTo(IntentionAssistant.ANNUAIRE);
    }

    @Test
    @DisplayName("Une question vide se range d'office dans la réponse documentaire")
    void questionVide_repli() {
        assertThat(intention("   ")).isEqualTo(IntentionAssistant.REGLE);
        assertThat(intention(null)).isEqualTo(IntentionAssistant.REGLE);
    }

    // ------------------------------------------------------------------ 4. le paramètre de recherche

    @Test
    @DisplayName("Le critère de recherche garde les mots qui disent quelque chose, et jette le reste")
    void parametre_gardeLesMotsUtiles() {
        Aiguillage a = aiguillage.decider("Retrouve les dossiers du ministère des Travaux publics", CONTROLEUR);

        assertThat(a.intention()).isEqualTo(IntentionAssistant.TROUVER_DOSSIER);
        assertThat(a.parametre()).contains("ministère", "Travaux", "publics");
        assertThat(a.parametre()).doesNotContain("Retrouve", "dossiers");
    }

    @Test
    @DisplayName("Une intention qui ne cherche rien n'a pas de critère : on ne fabrique pas un paramètre "
            + "pour faire joli")
    void intentionSansRecherche_pasDeParametre() {
        assertThat(aiguillage.decider("qu'est-ce que j'ai à faire ?", CONTROLEUR).parametre()).isNull();
        assertThat(aiguillage.decider("combien de dossiers en retard ?", CONTROLEUR).parametre()).isNull();
    }

    // ------------------------------------------------------------------ 5. la relance (lot 4, étape 4)

    @Test
    @DisplayName("Une RELANCE courte reprend l'intention du tour précédent : « et maintenant ? » après "
            + "une question sur les compteurs parle encore des compteurs")
    void relance_reprendLIntentionPrecedente() {
        assertThat(aiguillage.decider("et maintenant ?", CONTROLEUR, IntentionAssistant.MES_CHIFFRES)
                .intention()).isEqualTo(IntentionAssistant.MES_CHIFFRES);
        assertThat(aiguillage.decider("et celui-là ?", CONTROLEUR, IntentionAssistant.MES_TACHES)
                .intention()).isEqualTo(IntentionAssistant.MES_TACHES);
    }

    @Test
    @DisplayName("⚠️ Une question COMPLÈTE décide toujours toute seule : le tour précédent ne l'emporte "
            + "jamais sur ce qui est écrit")
    void questionComplete_ignoreLePrecedent() {
        assertThat(aiguillage.decider("qu'est-ce que j'ai à faire ?", CONTROLEUR,
                IntentionAssistant.MES_CHIFFRES).intention()).isEqualTo(IntentionAssistant.MES_TACHES);
        assertThat(aiguillage.decider("qu'est-ce qu'un fractionnement illicite ?", CONTROLEUR,
                IntentionAssistant.MES_CHIFFRES).intention()).isEqualTo(IntentionAssistant.REGLE);
    }

    @Test
    @DisplayName("⚠️ Le critère de relance est ÉTROIT : une phrase longue, ou sans amorce de continuité, "
            + "n'est pas une relance — une relance mal reconnue ferait lire une donnée non demandée")
    void relance_critereEtroit() {
        assertThat(aiguillage.decider("pourriez-vous me dire ce qu'il en est de la situation générale "
                + "du service cette semaine", CONTROLEUR, IntentionAssistant.MES_CHIFFRES).intention())
                .isEqualTo(IntentionAssistant.REGLE);
        assertThat(aiguillage.decider("merci beaucoup", CONTROLEUR, null).intention())
                .isEqualTo(IntentionAssistant.REGLE);
    }

    @Test
    @DisplayName("Une relance ne réveille jamais une intention fermée au profil")
    void relance_neReveillePasUneIntentionFermee() {
        List<IntentionAssistant> sansChiffres = List.of(IntentionAssistant.REGLE);

        assertThat(aiguillage.decider("et maintenant ?", sansChiffres, IntentionAssistant.MES_CHIFFRES)
                .intention()).isEqualTo(IntentionAssistant.REGLE);
    }
}
