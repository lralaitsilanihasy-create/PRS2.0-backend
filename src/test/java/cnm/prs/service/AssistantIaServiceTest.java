package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.SourceIaDto;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.service.ClientModeleIa.Message;
import cnm.prs.service.CorpusIaService.Passage;

/** Consigne et extraits envoyés au modèle par l'assistant IA. */
class AssistantIaServiceTest {

    @Test
    @DisplayName("Consigne : réponse sur extraits seuls, droit malgache, citations numérotées, aucun avis sur un dossier réel")
    void consigne_regles() {
        List<Message> messages = AssistantIaService.messages("Qu'est-ce qu'un avenant ?", List.of(), ProfilUtilisateur.PRMP);

        String consigne = messages.get(0).content();
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(consigne)
                .contains("UNIQUEMENT à partir des extraits")
                .contains("droit malgache")
                .contains("[1] ou [2][3]")
                .contains("Ne donne jamais d'avis sur un dossier réel")
                // Recette du 2026-09-18 : à « dois-je… », la PRMP recevait « Tu dois établir ton plan ».
                .contains("vouvoie toujours l'utilisateur")
                .contains("Personne responsable des marchés publics");
    }

    @Test
    @DisplayName("Demande : chaque extrait porte son numéro, son document et sa référence, la question vient en dernier")
    void demande_extraitsNumerotesPuisQuestion() {
        List<SourceIaDto> sources = AssistantIaService.numeroter(List.of(
                new Passage("d0-p15", "Manuel", "p. 15", "Fractionnement illicite : un seul marché par compte."),
                new Passage("d1-s3", "Règles", "3.1. PRMP", "Le mode de passation est purement saisi.")));

        String demande = AssistantIaService.messages("Le fractionnement ?", sources, ProfilUtilisateur.MEMBRE).get(1).content();

        assertThat(sources).extracting(SourceIaDto::numero).containsExactly(1, 2);
        assertThat(demande)
                .contains("[1] Manuel — p. 15\nFractionnement illicite")
                .contains("[2] Règles — 3.1. PRMP\nLe mode de passation")
                .endsWith("Question : Le fractionnement ?");
    }

    @Test
    @DisplayName("Extraits : un passage trop long est coupé et le signale, pour que cinq extraits tiennent dans le contexte")
    void numeroter_coupeLesPassagesTropLongs() {
        String long_ = "a".repeat(8000);

        SourceIaDto s = AssistantIaService.numeroter(List.of(new Passage("x", "Manuel", "p. 1", long_))).get(0);

        assertThat(s.extrait()).hasSizeLessThan(3700).endsWith("[…]");
    }

    @Test
    @DisplayName("Profil : les dix profils ont un libellé lisible par le modèle, et l'absence de profil aussi")
    void libelleProfil_tousLesProfils() {
        assertThat(Arrays.stream(ProfilUtilisateur.values()).map(AssistantIaService::libelleProfil))
                .allSatisfy(l -> assertThat(l).isNotBlank().doesNotContain("_"));
        assertThat(AssistantIaService.libelleProfil(null)).isEqualTo("un utilisateur de PRS");
    }
}
