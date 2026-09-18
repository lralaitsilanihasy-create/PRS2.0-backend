package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import cnm.prs.config.AssistantIaProperties;
import cnm.prs.config.AssistantIaProperties.DocumentCorpus;
import cnm.prs.service.CorpusIaService.Passage;

/**
 * <strong>Batterie de référence de la recherche</strong> de l'assistant IA, sur le vrai manuel de
 * contrôle a priori ({@code docs/plan-assistant-ia.md} §5, « Qualité »).
 *
 * <p>Chaque question porte la ou les pages du manuel où se trouve la réponse (relevées dans le texte
 * du manuel, pas de mémoire). La recherche est jugée sur ce qu'elle fournit au modèle : la bonne page
 * doit figurer parmi les cinq extraits retenus. Un modèle, si bon soit-il, ne peut pas répondre juste
 * sur un extrait qui n'est pas le bon.</p>
 *
 * <p>Le manuel n'est pas versé au dépôt : la batterie s'exécute là où il est présent
 * ({@code APP_IA_MANUEL}, sinon à la racine de l'espace de travail, à côté du dépôt) et
 * <strong>s'ignore</strong> ailleurs, notamment en CI.</p>
 */
@Tag("ia")
class AssistantIaBatterieCorpusTest {

    /**
     * Taux minimal de questions dont la bonne page figure parmi les extraits. Mesuré le 2026-09-18 :
     * <strong>38/40 (95 %)</strong> — 37/40 avant que chaque page n'emporte le début de la suivante.
     * Les deux ratés sont des questions dont les mots se retrouvent sur beaucoup de pages (« délai de
     * remise des offres », « résultats du contrôle ») : la limite connue d'une recherche par mots, à
     * suivre si le taux baisse.
     */
    private static final double TAUX_MINIMAL = 0.85;
    private static final int EXTRAITS = 5;

    private record Cas(String question, Set<Integer> pages) {
    }

    private static final List<Cas> BATTERIE = List.of(
            new Cas("Avant quelle date la PRMP doit-elle établir son plan de passation des marchés ?", Set.of(12)),
            new Cas("Quelles pièces composent le dossier d'un PPM présenté à la Commission ?", Set.of(12, 13, 129, 130)),
            new Cas("Que faut-il vérifier dans l'objet d'une ligne du PPM pour l'entretien d'un véhicule ?", Set.of(14)),
            new Cas("Sur quelle base apprécie-t-on le fractionnement illicite d'un marché de fournitures ?", Set.of(15)),
            new Cas("Quelles sont les seules formes de fractionnement autorisées ?", Set.of(15)),
            new Cas("Comment apprécier le fractionnement pour des travaux routiers ?", Set.of(15)),
            new Cas("Le fractionnement des prestations intellectuelles s'apprécie-t-il avec les TDR ?", Set.of(16)),
            new Cas("Dans quels cas peut-on recourir aux délais aménagés ?", Set.of(16)),
            new Cas("Quelle mention faut-il ajouter dans l'objet en cas de délai réduit ?", Set.of(14, 16)),
            new Cas("Quel doit être le montant de la garantie de soumission ?", Set.of(19, 24)),
            new Cas("Quels pourcentages pour les offres anormalement hautes et anormalement basses ?", Set.of(20, 25)),
            new Cas("Quel est le délai minimum de remise des offres pour un appel d'offres ouvert de travaux ?", Set.of(24)),
            new Cas("Quand faut-il exiger la garantie décennale ?", Set.of(26)),
            new Cas("Quel chiffre d'affaires peut-on exiger des candidats ?", Set.of(22, 26, 29)),
            new Cas("Dans quels cas peut-on recourir à un appel d'offres restreint ?", Set.of(29, 30)),
            new Cas("Comment constituer la liste restreinte quand seul un petit nombre de prestataires peut exécuter le marché ?", Set.of(31)),
            new Cas("Les marchés subséquents d'un contrat-cadre sont-ils soumis au contrôle a priori ?", Set.of(32)),
            new Cas("Comment éviter une utilisation abusive du contrat-cadre ?", Set.of(33)),
            new Cas("Quels sont les modes de sélection des consultants pour les prestations intellectuelles ?", Set.of(39)),
            new Cas("Que se passe-t-il si le délai de remise des manifestations d'intérêt n'est pas respecté ?", Set.of(40)),
            new Cas("Dans quels cas un marché de gré à gré est-il autorisé ?", Set.of(41, 42, 43)),
            new Cas("Quelles conditions pour passer un marché complémentaire ?", Set.of(43, 44)),
            new Cas("Comment justifier le montant d'un marché de gré à gré ?", Set.of(45, 46)),
            new Cas("Quelles formes de publicité sont obligatoires pour un appel d'offres ?", Set.of(47, 48)),
            new Cas("Quelles sont les étapes de l'évaluation des offres ?", Set.of(48, 49)),
            new Cas("Quels motifs empêchent la Commission de donner un avis favorable à un marché ?", Set.of(52, 53)),
            new Cas("Peut-on conclure un avenant après la réception définitive des travaux ?", Set.of(62, 67)),
            new Cas("Au-delà de quelle augmentation du prix faut-il passer un nouveau marché plutôt qu'un avenant ?", Set.of(63, 64, 65)),
            new Cas("Qu'est-ce que l'élément déclencheur d'un avenant ?", Set.of(65, 66)),
            new Cas("Quel est le délai de l'organe de contrôle pour se prononcer sur une déclaration sans suite ?", Set.of(68)),
            new Cas("Dans quels cas le titulaire peut-il être indemnisé ?", Set.of(71, 72)),
            new Cas("Dans quel délai le prestataire doit-il signaler les causes d'une demande de sursis d'exécution ?", Set.of(74)),
            new Cas("Quels sont les motifs de résiliation d'un marché ?", Set.of(77, 78)),
            new Cas("Que contient le rapport annuel de contrôle a priori ?", Set.of(80)),
            new Cas("Comment est attribué le numéro d'identification d'un dossier ?", Set.of(7)),
            new Cas("Qui signe la lettre de renvoi et pourquoi ?", Set.of(9)),
            new Cas("Quel est le délai de traitement d'un dossier par la Commission ?", Set.of(11, 135)),
            new Cas("Quelles personnes sont habilitées à procéder au contrôle a priori ?", Set.of(6, 123)),
            new Cas("Quel est le rôle du contrôleur vérificateur dans PRS ?", Set.of(124, 125)),
            new Cas("Quels sont les résultats possibles du contrôle d'un dossier ?", Set.of(135)));

    @Test
    @DisplayName("Recherche : la bonne page du manuel figure parmi les cinq extraits fournis au modèle")
    void laBonnePageEstFournieAuModele() {
        Path manuel = manuel();
        assumeTrue(manuel != null, "Manuel absent : batterie ignorée (APP_IA_MANUEL non défini, fichier introuvable).");
        CorpusIaService corpus = new CorpusIaService(new AssistantIaProperties(true, null, null, 0, 0, 0,
                List.of(new DocumentCorpus("Manuel", manuel.toString()))));

        List<String> rates = new ArrayList<>();
        int trouves = 0;
        for (Cas cas : BATTERIE) {
            List<Integer> pages = corpus.rechercher(cas.question(), EXTRAITS).stream()
                    .map(Passage::reference)
                    .map(r -> Integer.parseInt(r.replace("p. ", "")))
                    .toList();
            if (pages.stream().anyMatch(cas.pages()::contains)) {
                trouves++;
            } else {
                rates.add("  ✗ " + cas.question() + " — attendu " + cas.pages() + ", fourni " + pages);
            }
        }
        double taux = (double) trouves / BATTERIE.size();
        System.out.printf("Batterie de recherche : %d/%d (%.0f %%)%n%s%n", trouves, BATTERIE.size(), taux * 100,
                String.join("\n", rates));
        assertThat(taux).as("taux de questions dont la bonne page est fournie au modèle").isGreaterThanOrEqualTo(TAUX_MINIMAL);
    }

    private static Path manuel() {
        String env = System.getenv("APP_IA_MANUEL");
        List<Path> candidats = new ArrayList<>();
        if (env != null && !env.isBlank()) {
            candidats.add(Path.of(env));
        }
        candidats.add(Path.of("..", "MANUEL DE CONTROLE A PRIORI  VF 2026.pdf"));
        return candidats.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }
}
