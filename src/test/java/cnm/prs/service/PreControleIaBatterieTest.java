package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import cnm.prs.config.AssistantIaProperties;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.TypeSignalement;
import tools.jackson.databind.json.JsonMapper;

/**
 * <strong>Batterie de référence du pré-contrôle assisté</strong> (assistant IA, lot 3, étape 6 ;
 * {@code docs/plan-assistant-ia.md} §5, « Qualité »), contre le <strong>vrai modèle</strong>.
 *
 * <p>C'est elle qu'on rejoue avant de changer de modèle en production : sans elle, « un modèle plus
 * puissant » est une croyance, pas une mesure. Elle ne touche ni la base ni Spring — elle exerce
 * exactement les deux fonctions qui décident de la qualité : la <strong>consigne</strong> qu'on envoie et
 * le <strong>filtrage</strong> de ce qui revient ({@link DialogueAnalyseIa}).</p>
 *
 * <p>Lente (quelques secondes par plan) et dépendante d'un serveur d'inférence : elle ne tourne que sur
 * demande explicite —
 * {@code mvnw test -Dtest=PreControleIaBatterieTest -Dia.batterie=true} — avec {@code APP_IA_BASE_URL} et
 * {@code APP_IA_MODELE}, sinon les valeurs de développement (Ollama local).</p>
 *
 * <h2>Ce que la batterie mesure, et pourquoi le silence compte autant que la trouvaille</h2>
 * <p>Deux cas sur les sept attendent <strong>zéro piste</strong>. C'est délibéré : un modèle qui trouve
 * toujours quelque chose est inutilisable — la PRMP apprend à tout écarter sans lire, et la
 * fonctionnalité meurt en six semaines (plan, 3.e). Un cas vérifie aussi qu'il <strong>ne va pas sur le
 * terrain des règles</strong> : le mode, les seuils et le fractionnement par compte ne lui appartiennent
 * pas.</p>
 */
@Tag("ia")
@EnabledIfSystemProperty(named = "ia.batterie", matches = "true")
class PreControleIaBatterieTest {

    /**
     * Part minimale de cas réussis. Volontairement en dessous de 1 : un modèle local varie d'un tirage à
     * l'autre, et une batterie qui exige la perfection devient un test instable qu'on finit par ignorer.
     * Le rapport imprime chaque cas — c'est lui qu'on lit quand le taux baisse.
     *
     * <p><strong>Mesuré le 2026-09-20 avec {@code qwen3.5:9b-q4_K_M}</strong> sur la RTX 5050 du poste de
     * développement : <strong>5/7 puis 6/7</strong>, ~33 s par passage (3 appels par cas). Le chemin y est
     * instructif, et il est consigné dans la consigne elle-même ({@link DialogueAnalyseIa}) : une question
     * unique portant les trois recherches donnait 3/7, l'allonger n'y changeait rien, et c'est le découpage
     * en <strong>une passe par type</strong> — plus un garde-fou déterministe sur le même compte — qui a
     * tout changé.</p>
     *
     * <p>Le cas qui résiste encore est un <strong>plan sans défaut</strong> où le modèle trouve malgré tout
     * à dire. C'est exactement le risque de fatigue d'alerte (plan, 3.e), et c'est pourquoi une piste
     * s'écarte d'un clic avec son motif, et pourquoi chaque type de piste s'éteint depuis
     * l'administration sans redéploiement.</p>
     */
    private static final double TAUX_MINIMAL = 0.7;


    /**
     * Un cas de référence : un petit plan, et ce que l'assistant doit en dire.
     *
     * @param intitule      ce que le cas éprouve, pour le rapport
     * @param lignes        les lignes du plan (numéro, nature, compte, montant, objet)
     * @param typeAttendu   le type de piste attendu, ou {@code null} si l'assistant doit se taire
     * @param lignesVisees  les numéros de ligne que la piste doit viser (au moins un)
     */
    private record Cas(String intitule, List<LigneFixture> lignes, TypeSignalement typeAttendu,
            Set<Integer> lignesVisees) {
    }

    private record LigneFixture(int numero, String nature, String compte, String montant, String objet) {
    }

    private static final List<Cas> CAS = List.of(
            new Cas("fractionnement déguisé — une même route sous deux comptes",
                    List.of(new LigneFixture(1, "Travaux", "23110", "400000000",
                                    "Entretien de la RN 2 du PK 12 au PK 30"),
                            new LigneFixture(2, "Travaux", "23119", "380000000",
                                    "Travaux d'entretien routier RN 2, section PK 30 à PK 45")),
                    TypeSignalement.FRACTIONNEMENT_DEGUISE, Set.of(1, 2)),

            new Cas("fractionnement déguisé — un même bâtiment en deux marchés",
                    List.of(new LigneFixture(1, "Travaux", "23140", "900000000",
                                    "Construction du bâtiment A du centre hospitalier de Toamasina"),
                            new LigneFixture(2, "Travaux", "23141", "600000000",
                                    "Achèvement des travaux du bâtiment A du CHU de Toamasina")),
                    TypeSignalement.FRACTIONNEMENT_DEGUISE, Set.of(1, 2)),

            new Cas("objet imprécis — ni type ni quantité de fournitures",
                    List.of(new LigneFixture(1, "Fournitures", "61121", "80000000", "Achat de matériel"),
                            new LigneFixture(2, "Fournitures", "61122", "12000000",
                                    "Fourniture de 300 ramettes de papier A4 80 g pour la direction")),
                    TypeSignalement.OBJET_IMPRECIS, Set.of(1)),

            new Cas("objet imprécis — travaux sans site ni consistance",
                    List.of(new LigneFixture(1, "Travaux", "23150", "250000000", "Travaux divers"),
                            new LigneFixture(2, "Travaux", "23151", "180000000",
                                    "Réhabilitation de la clôture du lycée de Fianarantsoa, 420 mètres linéaires")),
                    TypeSignalement.OBJET_IMPRECIS, Set.of(1)),

            new Cas("nature incohérente — des fournitures déclarées en travaux",
                    List.of(new LigneFixture(1, "Travaux", "61123", "40000000",
                            "Fourniture de 15 ordinateurs portables pour la direction des ressources humaines")),
                    TypeSignalement.NATURE_INCOHERENTE, Set.of(1)),

            new Cas("SILENCE — deux lignes précises et sans rapport entre elles",
                    List.of(new LigneFixture(1, "Fournitures", "61124", "35000000",
                                    "Fourniture de 12 climatiseurs split 12 000 BTU pour le siège, avec pose"),
                            new LigneFixture(2, "Travaux", "23160", "120000000",
                                    "Réfection de la toiture du magasin central de Mahajanga, 800 m²")),
                    null, Set.of()),

            new Cas("SILENCE — le terrain des règles n'est pas le sien (même compte, mode, seuils)",
                    List.of(new LigneFixture(1, "Fournitures", "61125", "60000000",
                                    "Fourniture de 200 tables d'écolier pour l'EPP d'Ambohimanga"),
                            new LigneFixture(2, "Fournitures", "61125", "60000000",
                                    "Fourniture de 150 bancs d'écolier pour l'EPP d'Ambohimanga")),
                    null, Set.of()));

    @Test
    @DisplayName("Batterie du pré-contrôle assisté : l'assistant trouve ce qu'aucune règle ne voit, et se "
            + "tait quand il n'y a rien à dire")
    void batterie() {
        AssistantIaProperties props = new AssistantIaProperties(true, System.getenv("APP_IA_BASE_URL"),
                System.getenv("APP_IA_MODELE"), 180, 5, 900, List.of());
        JsonMapper mapper = JsonMapper.builder().build();
        ClientModeleIa client = new ClientModeleIa(props, mapper);
        DialogueAnalyseIa dialogue = new DialogueAnalyseIa(mapper);

        List<String> rapport = new ArrayList<>();
        int reussis = 0;
        for (Cas cas : CAS) {
            ContextePreControle contexte = contexte(cas);
            List<Marche> lignes = dialogue.lignesAnalysees(contexte);
            String inventaire = dialogue.inventaire(contexte, lignes);
            // Une passe par type, comme en production : c'est la forme que la mesure a imposée.
            List<SignalementDetecte> pistes = new ArrayList<>();
            Set<String> clesVues = new java.util.LinkedHashSet<>();
            for (TypeSignalement type : TypeSignalement.typesDeLAssistant()) {
                String reponse = client.generer(
                        List.of(new ClientModeleIa.Message("system", dialogue.consigne(type)),
                                new ClientModeleIa.Message("user", inventaire)),
                        fragment -> { }, () -> false);
                pistes.addAll(dialogue.lire(reponse, type, contexte, lignes, clesVues).pistes());
            }

            boolean ok = juger(cas, pistes);
            reussis += ok ? 1 : 0;
            rapport.add((ok ? "OK   " : "RATÉ ") + cas.intitule()
                    + "\n      attendu : " + (cas.typeAttendu() == null ? "aucune piste"
                            : cas.typeAttendu().name() + " sur " + cas.lignesVisees())
                    + "\n      rendu   : " + resume(pistes)
                    + "\n      synthèse : " + dialogue.synthese(pistes, contexte));
        }

        double taux = (double) reussis / CAS.size();
        System.out.println("\n══ Batterie du pré-contrôle assisté — modèle " + props.modele()
                + " — " + reussis + "/" + CAS.size() + " ══\n" + String.join("\n", rapport) + "\n");
        assertThat(taux)
                .as("taux de réussite de la batterie (rapport ci-dessus)")
                .isGreaterThanOrEqualTo(TAUX_MINIMAL);
    }

    /**
     * Un cas est réussi si l'assistant a rendu le type attendu sur au moins une des lignes visées — ou,
     * pour un cas de silence, s'il n'a rien rendu du tout.
     */
    private static boolean juger(Cas cas, List<SignalementDetecte> pistes) {
        if (cas.typeAttendu() == null) {
            return pistes.isEmpty();
        }
        return pistes.stream()
                .filter(p -> p.type() == cas.typeAttendu())
                .anyMatch(p -> numeros(p).stream().anyMatch(cas.lignesVisees()::contains));
    }

    private static List<Integer> numeros(SignalementDetecte piste) {
        if (piste.idDetail() != null) {
            return List.of(piste.idDetail());
        }
        return piste.lignes().stream().map(SignalementDetecte.LigneVisee::idDetail).toList();
    }

    private static String resume(List<SignalementDetecte> pistes) {
        if (pistes.isEmpty()) {
            return "aucune piste";
        }
        return pistes.stream()
                .map(p -> p.type().name() + " sur " + numeros(p) + " — " + p.description())
                .collect(Collectors.joining("\n                "));
    }

    // ------------------------------------------------------------------ fixture

    /** Un contexte minimal : ce que le dialogue lit, et rien d'autre (aucun seuil, aucun lot, aucune date). */
    private static ContextePreControle contexte(Cas cas) {
        Ppm ppm = new Ppm();
        ppm.setIdPpm(1);
        ppm.setIdDossier(1);
        ppm.setExercice(2026);
        ppm.setIdLocalite("ANT");

        Map<Integer, Nature> natures = Map.of(
                1, new Nature(1, "Travaux", null),
                2, new Nature(2, "Fournitures", null),
                3, new Nature(3, "Services", null));

        List<Marche> lignes = new ArrayList<>();
        Map<Integer, List<ServiceBeneficiaire>> beneficiaires = new java.util.LinkedHashMap<>();
        for (LigneFixture f : cas.lignes()) {
            Marche m = new Marche();
            m.setIdDetail(f.numero());
            m.setIdPpm(1);
            m.setIdDossier(1);
            m.setDesignationMarche(f.objet());
            m.setMontEstim(new BigDecimal(f.montant()));
            m.setFinancement("RPI");
            m.setFormeMarche(FormeMarche.QUANTITE_FIXE);
            m.setIdNature(natures.values().stream()
                    .filter(n -> n.getLibelle().equals(f.nature())).findFirst().orElseThrow().getIdNature());
            lignes.add(m);

            ServiceBeneficiaire b = new ServiceBeneficiaire();
            b.setIdBenef(f.numero() * 10);
            b.setIdDetail(f.numero());
            b.setNumCompte(f.compte());
            beneficiaires.put(f.numero(), List.of(b));
        }

        return new ContextePreControle(ppm, "ANT",
                new SeuilsEnVigueur(LocalDate.of(2026, 1, 1), List.of()),
                lignes, beneficiaires, Map.of(), Map.of(), natures, Map.of(), Map.of());
    }
}
