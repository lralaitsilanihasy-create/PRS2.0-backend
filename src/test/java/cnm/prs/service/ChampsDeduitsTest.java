package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-17 (demande front « champs-deduits-du-dao ») — la règle 10 de {@link LectureDao} (attestation par encadrement, §B1), les
 * fiches de renseignements déduites des titres (§B2) et {@code B07-DE-01} sans objet au bilan (§B3, option 2). La mesure sur les DAO
 * réels est {@link ChampsDeduitsRecetteTest}.
 */
class ChampsDeduitsTest {

    private static final List<String> PREAMBULE = List.of(
            "ARTICLE 13 – Opération de vérification et admission des prestations des marchés subséquents",
            "Les opérations de vérification sont effectuées dans les conditions prévues au cahier des clauses administratives générales.",
            "ARTICLE 14 – Garanties contractuelles particulières prévues dans les marchés subséquents",
            "Aucune garantie particulière autre que celles du cahier des clauses administratives générales ne sera exigée.");
    private static final String ARTICLE_16 = "ARTICLE 16 – Assurance des marchés conclus sur la base du présent contrat-cadre";
    private static final String ARTICLE_17 = "ARTICLE 17 – Modifications relatives au titulaire du présent contrat-cadre";
    private static final String OUI = "Le titulaire justifiera d'une assurance couvrant sa responsabilité civile pendant toute la durée du marché.";

    private static FichierCommande.Modele modele() {
        List<String> lignes = new ArrayList<>(List.of("CONDITION\tASSURANCE-NON\u001FB09-AU-01 = NON",
                "CONDITION\tASSURANCE-OUI\u001FB09-AU-01 = OUI"));
        PREAMBULE.forEach(p -> lignes.add("PARA\t" + p));
        lignes.addAll(List.of("PARA\t" + ARTICLE_16, "PARA\t{{SI:ASSURANCE-NON}}", "PARA\tSans objet", "PARA\t{{FINSI:ASSURANCE-NON}}",
                "PARA\t{{SI:ASSURANCE-OUI}}", "PARA\t" + OUI, "PARA\t{{FINSI:ASSURANCE-OUI}}", "PARA\t" + ARTICLE_17));
        return FichierCommande.lireModele(String.join("\n", lignes));
    }

    private static List<String> reponses(String... suite) {
        List<String> doc = new ArrayList<>(PREAMBULE);
        doc.addAll(List.of(suite));
        LectureDao.Resultat r = LectureDao.lire("T", modele(), LectureDao.unitesDocument(doc), c -> null);
        return r.reponsesChamps().stream().map(x -> x.cle() + "=" + x.valeur() + " (" + x.section() + ")").toList();
    }

    @Test
    @DisplayName("§B1 règle 10 : « Sans objet » entre ses deux voisins reconnus atteste ASSURANCE-NON ; un paragraphe de plus, non")
    void encadrement() {
        assertThat(reponses(ARTICLE_16, "Sans objet.", ARTICLE_17)).containsExactly("B09-AU-01=NON (ASSURANCE-NON)");
        assertThat(reponses(ARTICLE_16, "Sans objet", "Les dispositions de l'article 17 du CCAG Fournitures sont applicables", ARTICLE_17))
                .isEmpty();
        assertThat(reponses(ARTICLE_16, OUI, ARTICLE_17)).containsExactly("B09-AU-01=OUI (ASSURANCE-OUI)");
    }

    @Test
    @DisplayName("§B2 : les fiches A1 à A4 par leur titre ; ni le sommaire, ni les IC, ni « A4 » sans son mot ; titres collés en un paragraphe")
    void fiches() {
        CadrageParClause.Fiches f = CadrageParClause.fiches(List.of(
                "SOMMAIRE GÉNÉRAL",
                "A1 - Identification du Candidat", "A2 - Capacités techniques", "A3 - Capacités financières",
                "1.1. - Instructions aux candidats",
                "A4 - Antécédents du Candidat", "Le candidat remplit la fiche A4 des formulaires.",
                "1.3. - Formulaires de soumission",
                "A1 : IDENTIFICATION DU CANDIDAT", "A1 - a) FICHE DE RENSEIGNEMENTS RELATIFS AU CANDIDAT", "Nom du candidat :",
                "A3 : CAPACITES FINANCIERES", "Chiffre d'affaires des trois dernières années",
                "A4 format paysage", "Imprimer en couleur"));
        assertThat(f.codes()).containsExactly("A1", "A3");

        CadrageParClause.Fiches colles = CadrageParClause.fiches(List.of("1.3. - Formulaires de soumission",
                "A. - Modèles de fiches de renseignements A1 - Identification du Candidat A1 : IDENTIFICATION DU CANDIDAT A1 - a) FICHE …",
                "Date : ____", "A2 : CAPACITES TECHNIQUES", "Matériel et outillage", "A1IDENTIFICATION DU CANDIDAT A2 - CAPACITES A3-CAPACITES"));
        assertThat(colles.codes()).containsExactly("A1", "A2");
    }

    @Test
    @DisplayName("§B3, option 2 : B07-DE-01 ne bloque pas quand les marchés subséquents sont fractionnés ; il bloque s'ils sont uniques")
    void delaisSansObjet() {
        assertThat(ControlesFicheMarche.sansObjet("B07-DE-01", "B07-DE-01",
                Map.of("B07-FS-01", "Marchés fractionnés par bons de commande successifs"))).isTrue();
        assertThat(ControlesFicheMarche.sansObjet("B07-DE-01", "B07-DE-01", Map.of("B07-FS-01", "Marchés uniques non fractionnés"))).isFalse();
        assertThat(ControlesFicheMarche.sansObjet("B07-DE-01", "B07-DE-01", Map.of())).isFalse();
        assertThat(ControlesFicheMarche.sansObjet("B07-DE-01", "B07-DE-01#2",
                Map.of("B07-FS-01#2", "Marchés fractionnés par bons de commande successifs"))).isTrue();
        assertThat(ControlesFicheMarche.sansObjet("B07-FS-01", "B07-FS-01", Map.of())).isFalse();
    }
}
