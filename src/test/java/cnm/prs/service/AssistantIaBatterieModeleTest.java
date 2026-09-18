package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import cnm.prs.config.AssistantIaProperties;
import cnm.prs.config.AssistantIaProperties.DocumentCorpus;
import cnm.prs.dto.SourceIaDto;
import cnm.prs.enums.ProfilUtilisateur;
import tools.jackson.databind.json.JsonMapper;

/**
 * <strong>Batterie de référence des réponses</strong> de l'assistant IA, contre le vrai modèle
 * ({@code docs/plan-assistant-ia.md} §5, « Qualité »). C'est elle qu'on rejoue avant de changer de
 * modèle : sans elle, « un modèle plus puissant » est une croyance, pas une mesure.
 *
 * <p>Lente (quelques secondes par question) et dépendante d'un serveur d'inférence : elle ne tourne
 * que sur demande explicite —
 * {@code mvnw test -Dtest=AssistantIaBatterieModeleTest -Dia.batterie=true} — avec le manuel présent
 * et le serveur joignable ({@code APP_IA_BASE_URL}, {@code APP_IA_MODELE}, sinon les valeurs de
 * développement).</p>
 *
 * <p>Chaque réponse est jugée sur ce qui se vérifie sans relecture humaine : elle cite au moins un
 * extrait ({@code [n]}), contient les mots attendus, et ne cite pas le droit français. Le rapport
 * imprime les réponses, pour la relecture qui, elle, reste humaine.</p>
 */
@Tag("ia")
@EnabledIfSystemProperty(named = "ia.batterie", matches = "true")
class AssistantIaBatterieModeleTest {

    /**
     * Mesuré le 2026-09-18 avec {@code qwen3.5:9b-q4_K_M} sur la RTX 5050 du poste de développement :
     * <strong>12/12</strong>, ~3 s par réponse modèle chargé. Deux défauts corrigés en chemin, que la
     * batterie garde à l'œil : seuils des offres anormales inversés (extraction du PDF qui fondait
     * les colonnes des tableaux) et conditions du marché complémentaire tronquées (liste coupée par un
     * saut de page). Une citation inventée (« [8] » sur cinq extraits) a été vue une fois sur trois
     * passages : le taux tolère ces écarts de tirage, le rapport les montre.
     */
    private static final double TAUX_MINIMAL = 0.8;

    private record Cas(String question, List<String> motsAttendus) {
    }

    private static final List<Cas> BATTERIE = List.of(
            new Cas("Avant quelle date la PRMP doit-elle établir son plan de passation des marchés ?", List.of("31 octobre")),
            new Cas("Quelles sont les seules formes de fractionnement autorisées ?", List.of("allotissement", "tranches", "commandes")),
            new Cas("Sur quelle base apprécie-t-on le fractionnement illicite d'un marché de fournitures ?", List.of("compte")),
            new Cas("Quel doit être le montant de la garantie de soumission ?", List.of("1", "2")),
            new Cas("Quels pourcentages pour les offres anormalement hautes et anormalement basses ?", List.of("20", "10")),
            new Cas("Quand faut-il exiger la garantie décennale ?", List.of("construction")),
            new Cas("Les marchés subséquents d'un contrat-cadre sont-ils soumis au contrôle a priori ?", List.of("ni")),
            new Cas("Quelles conditions pour passer un marché complémentaire ?", List.of("tiers")),
            new Cas("Peut-on conclure un avenant après la réception définitive des travaux ?", List.of("non")),
            new Cas("Quel est le délai de l'organe de contrôle pour se prononcer sur une déclaration sans suite ?", List.of("cinq")),
            new Cas("Qui signe la lettre de renvoi et pourquoi ?", List.of("président", "anonymat")),
            new Cas("Quels sont les modes de sélection des consultants pour les prestations intellectuelles ?", List.of("qualité")));

    @Test
    @DisplayName("Réponses du modèle : citées, fidèles aux mots attendus, jamais sur le droit français")
    void reponsesDuModele() {
        Path manuel = manuel();
        assumeTrue(manuel != null, "Manuel absent : batterie ignorée.");
        AssistantIaProperties props = new AssistantIaProperties(true, System.getenv("APP_IA_BASE_URL"),
                System.getenv("APP_IA_MODELE"), 120, 5, 900, List.of(new DocumentCorpus("Manuel de contrôle a priori", manuel.toString())));
        ClientModeleIa client = new ClientModeleIa(props, JsonMapper.builder().build());
        assumeTrue(client.disponible(), "Serveur d'inférence injoignable ou modèle absent : batterie ignorée.");
        CorpusIaService corpus = new CorpusIaService(props);

        List<String> rapport = new ArrayList<>();
        int reussis = 0;
        for (Cas cas : BATTERIE) {
            List<SourceIaDto> sources = AssistantIaService.numeroter(corpus.rechercher(cas.question(), props.extraits()));
            long debut = System.nanoTime();
            String reponse = client.generer(AssistantIaService.messages(cas.question(), sources, ProfilUtilisateur.MEMBRE),
                    morceau -> { }, () -> false);
            long ms = (System.nanoTime() - debut) / 1_000_000;
            String bas = reponse.toLowerCase(Locale.ROOT);
            List<String> manquants = cas.motsAttendus().stream().filter(m -> !bas.contains(m.toLowerCase(Locale.ROOT))).toList();
            List<Integer> citations = java.util.regex.Pattern.compile("\\[(\\d+)]").matcher(reponse).results()
                    .map(m -> Integer.parseInt(m.group(1))).toList();
            boolean cite = !citations.isEmpty();
            // Un numéro sans extrait correspondant est une citation inventée (vu le 2026-09-18 : « [8] »
            // avec cinq extraits). Le front l'affiche en texte simple ; la batterie, elle, le compte.
            List<Integer> inventees = citations.stream().filter(n -> n < 1 || n > sources.size()).distinct().toList();
            boolean droitFrancais = bas.contains("commande publique");
            boolean ok = manquants.isEmpty() && cite && inventees.isEmpty() && !droitFrancais;
            if (ok) {
                reussis++;
            }
            rapport.add("%s %s (%d ms)%n   → %s%s".formatted(ok ? "✓" : "✗", cas.question(), ms,
                    reponse.strip().replace("\n", "\n     "),
                    ok ? "" : "%n   manquants=%s cité=%s inventées=%s droitFrançais=%s".formatted(manquants, cite,
                            inventees, droitFrancais)));
        }
        double taux = (double) reussis / BATTERIE.size();
        System.out.printf("Batterie du modèle %s : %d/%d (%.0f %%)%n%s%n", props.modele(), reussis, BATTERIE.size(),
                taux * 100, String.join("\n", rapport));
        assertThat(taux).as("taux de réponses citées et fidèles").isGreaterThanOrEqualTo(TAUX_MINIMAL);
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
