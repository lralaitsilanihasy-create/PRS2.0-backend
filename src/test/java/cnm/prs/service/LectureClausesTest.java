package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-03 (demande front « lecture par clause », option A) — la passe par clause, sur des paragraphes qui reprennent
 * les tournures des DAO réels (MTP, MEN, 2463) : section des données particulières, catalogue, montants par lot (deux
 * formes, dans l'ordre), passages de listes, garde-fous (déjà lu par le modèle, champ non servi, confiance moyenne).
 */
class LectureClausesTest {

    private static final Map<String, String> TYPES = Map.of("B04-VO-01", "NOMBRE", "B05-GQ-03", "MONTANT", "B05-GS-03", "MONTANT",
            "B09-DL-01", "TEXTE", "B03-QT-14", "MONTANT", "B03-QT-15", "POURCENTAGE", "B04-OV-01", "TEXTE");
    private static final Function<String, LectureDao.InfoChamp> CHAMPS = c -> TYPES.containsKey(c)
            ? new LectureDao.InfoChamp(TYPES.get(c), "SAISIE", null) : null;

    private static LectureDao.Resultat vide(String sigle, List<LectureDao.Proposition> deja) {
        return new LectureDao.Resultat(sigle, 0, 0, deja, List.of(), List.of(), List.of(), List.of(), List.of("B04-VO-01"));
    }

    private static Map<String, String> valeurs(LectureDao.Resultat r) {
        Map<String, String> m = new java.util.TreeMap<>();
        r.propositions().stream().filter(p -> LectureDao.SOURCE_CLAUSE.equals(p.source())).forEach(p -> m.put(p.code(), p.valeur()));
        return m;
    }

    @Test
    @DisplayName("Travaux : délai, validité, garantie, liquidité en %, lieu — dans la section des données particulières seulement")
    void travaux() {
        List<String> doc = List.of(
                "Instructions aux candidats : la garantie de soumission est de 1 000 000 Ariary.",   // hors section : ignoré
                "Les données particulières ci-après complètent les clauses des IC.",
                "6.5 Délai de validité des offres",
                "Le délai de validité des offres est de quatre-vingt-dix (90) jours.",
                "6.8 Garantie de soumission",
                "Montant : 100 500 000 Ariary",
                "Délai d'exécution : Six (06) mois",
                "Liquidité ou ligne de crédit d'au moins 10 % du montant de l'offre",
                "8. Ouverture des plis",
                "Lieu : Salle de réunion du MTP, Anosy");
        LectureDao.Resultat r = LectureDao.completerParClause(vide("DPAO-T", List.of()), doc, "DPAO-T", CHAMPS);
        assertThat(valeurs(r)).containsExactly(Map.entry("B03-QT-15", "10"), Map.entry("B04-OV-01", "Salle de réunion du MTP, Anosy"),
                Map.entry("B04-VO-01", "90"), Map.entry("B05-GQ-03", "100500000"), Map.entry("B09-DL-01", "Six (06) mois"));
        assertThat(r.propositions()).allMatch(p -> p.confiance() == LectureDao.Confiance.MOYENNE);
        assertThat(r.nonTrouves()).doesNotContain("B04-VO-01");
    }

    @Test
    @DisplayName("Par lot : « Lot n° 1 : <en lettres> (Ar 1 600 000) » d'abord ; à défaut « (Ar 99 000 000) pour le lot n°1 »")
    void parLot() {
        List<String> f = List.of("Les données particulières ci-après complètent les IC.", "6.8 Garantie de soumission",
                "Lot n° 1 : UN MILLION SIX CENT MILLE ARIARY (Ar 1 600 000) ; Lot n° 2 : DEUX MILLIONS CENT SOIXANTE-DIX MILLE "
                        + "ARIARY (Ar 2 170 000)");
        assertThat(valeurs(LectureDao.completerParClause(vide("DPAO-F", List.of()), f, "DPAO-F", CHAMPS)))
                .containsExactly(Map.entry("B05-GS-03#1", "1600000"), Map.entry("B05-GS-03#2", "2170000"));
        List<String> t = List.of("Les données particulières ci-après complètent les IC.",
                "f) justifier d'une liquidité d'un montant minimum de : (Ar 99 000 000) pour le lot n°1 ; (Ar 72 000 000) pour le lot n°2");
        assertThat(valeurs(LectureDao.completerParClause(vide("DPAO-T", List.of()), t, "DPAO-T", CHAMPS)))
                .containsExactly(Map.entry("B03-QT-14#1", "99000000"), Map.entry("B03-QT-14#2", "72000000"));
    }

    @Test
    @DisplayName("Second temps (front b5373d1) : validité « de l'offre » et « (75j)jours » (2463) ; garantie à 4 paragraphes "
            + "de l'ancre (MEN) ; passage du personnel « ci-après (CV…) : », fermé par « NB : » (MTP)")
    void secondTemps() {
        List<String> f = List.of("Les données particulières ci-après complètent les IC.",
                "Le délai de validité de l'offre sera de soixante-quinze (75j)jours.");
        assertThat(valeurs(LectureDao.completerParClause(vide("DPAO-F", List.of()), f, "DPAO-F", CHAMPS)))
                .containsExactly(Map.entry("B04-VO-01", "75"));
        List<String> t = List.of("Les données particulières ci-après complètent les IC.", "6.8 Garantie de soumission",
                "Formes admises :", "- garantie bancaire ;", "- chèque de banque.",
                "Montant : 9 900 000 Ar (lot 1) / 7 200 000 Ar (lot 2)",
                "e) proposer le personnel ci-après (CV et copie certifiée des diplômes à l'appui) :",
                "- Conducteur de travaux : ingénieur BTP, 5 ans", "- Chef de chantier : technicien supérieur, 3 ans",
                "NB : les CV sont signés par les intéressés.");
        LectureDao.Resultat r = LectureDao.completerParClause(vide("DPAO-T", List.of()), t, "DPAO-T", CHAMPS);
        assertThat(valeurs(r)).containsExactly(Map.entry("B05-GQ-03#1", "9900000"), Map.entry("B05-GQ-03#2", "7200000"));
        assertThat(r.passages()).filteredOn(p -> "PERSONNEL".equals(p.liste())).singleElement()
                .satisfies(p -> assertThat(p.texte()).isEqualTo("- Conducteur de travaux : ingénieur BTP, 5 ans\n"
                        + "- Chef de chantier : technicien supérieur, 3 ans"));
    }

    @Test
    @DisplayName("Garde-fous : rien sans section ; rien pour un champ déjà lu par le modèle ni pour un champ non servi ; hors DPAO, "
            + "rien ; le passage des pièces jusqu'à la clause 6.3")
    void gardeFous() {
        List<String> doc = List.of("Les données particulières ci-après complètent les IC.", "Délai de validité des offres : 90 jours",
                "6.2. Contenu des offres", "Documents ou pièces à remettre en sus de ceux mentionnés à la clause 6.2. des IC :",
                "- Quittance de l'ARMP", "- Garantie de soumission", "6.3. Capacités et qualifications des candidats");
        LectureDao.Proposition lue = new LectureDao.Proposition("B04-VO-01", "120", "120", LectureDao.Confiance.HAUTE, "…");
        LectureDao.Resultat r = LectureDao.completerParClause(vide("DPAO-F", List.of(lue)), doc, "DPAO-F", CHAMPS);
        assertThat(valeurs(r)).isEmpty();
        assertThat(r.propositions()).containsExactly(lue);
        assertThat(r.passages()).singleElement().satisfies(p -> {
            assertThat(p.liste()).isEqualTo("PIECES");
            assertThat(p.texte()).isEqualTo("- Quittance de l'ARMP\n- Garantie de soumission");
            assertThat(p.paragraphe()).isEqualTo(3);
        });
        assertThat(valeurs(LectureDao.completerParClause(vide("DPAO-F", List.of()), doc, "DPAO-F", c -> null))).isEmpty();
        assertThat(LectureDao.completerParClause(vide("DPAO-F", List.of()), doc.subList(1, doc.size()), "DPAO-F", CHAMPS)
                .passages()).isEmpty();   // sans la phrase d'introduction ni titre numéroté : pas de section
        LectureDao.Resultat ccap = vide("CCAP-T", List.of());
        assertThat(LectureDao.completerParClause(ccap, doc, "CCAP-T", CHAMPS)).isSameAs(ccap);
    }
}
