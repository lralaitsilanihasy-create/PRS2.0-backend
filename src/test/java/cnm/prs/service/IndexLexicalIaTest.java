package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Recherche lexicale de l'assistant IA : normalisation du français et classement BM25. */
class IndexLexicalIaTest {

    @Test
    @DisplayName("Normalisation : accents, pluriel et racine à sept lettres rapprochent les formes d'un mot")
    void termes_normalisesEtRacinises() {
        assertThat(IndexLexicalIa.termes("Fractionnement illicite des marchés"))
                .containsExactly("fractio", "illicit", "marche");
        assertThat(IndexLexicalIa.termes("fractionnés")).containsExactly("fractio");
        assertThat(IndexLexicalIa.termes("Le marché")).containsExactly("marche");
        assertThat(IndexLexicalIa.termes("Délais")).containsExactly(IndexLexicalIa.termes("délai").get(0));
    }

    @Test
    @DisplayName("Normalisation : mots vides écartés, nombres gardés (un seuil ou un délai se cherche)")
    void termes_motsVidesEcartes_nombresGardes() {
        assertThat(IndexLexicalIa.termes("Quel est le délai de 30 jours pour les offres ?"))
                .containsExactly("delai", "30", "jour", "offre");
        assertThat(IndexLexicalIa.termes("  ")).isEmpty();
        assertThat(IndexLexicalIa.termes(null)).isEmpty();
    }

    @Test
    @DisplayName("BM25 : le passage qui traite de la question passe en tête, un passage sans terme commun n'est pas rendu")
    void rechercher_classeParPertinence() {
        Map<String, String> textes = new LinkedHashMap<>();
        textes.put("garantie", "La garantie de soumission est comprise entre 1 et 2 % du montant estimatif.");
        textes.put("fraction", "Aucun marché ne peut être fractionné illicitement pour échapper aux seuils. "
                + "Le fractionnement s'apprécie par compte PCOP.");
        textes.put("avenant", "Un avenant modifie une disposition du marché initial.");
        IndexLexicalIa index = IndexLexicalIa.construire(textes);

        List<IndexLexicalIa.Resultat> r = index.rechercher("Qu'est-ce que le fractionnement d'un marché ?", 5);

        assertThat(r).extracting(IndexLexicalIa.Resultat::identifiant).first().isEqualTo("fraction");
        assertThat(r).extracting(IndexLexicalIa.Resultat::identifiant).doesNotContain("garantie");
        assertThat(r).allSatisfy(x -> assertThat(x.score()).isPositive());
    }

    @Test
    @DisplayName("BM25 : le nombre de résultats est borné, une question vide ne rend rien")
    void rechercher_borneEtQuestionVide() {
        Map<String, String> textes = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            textes.put("p" + i, "Contrôle a priori des marchés publics, passage " + i);
        }
        IndexLexicalIa index = IndexLexicalIa.construire(textes);

        assertThat(index.rechercher("contrôle des marchés", 3)).hasSize(3);
        assertThat(index.rechercher("le la les", 3)).isEmpty();
        assertThat(index.rechercher("contrôle", 0)).isEmpty();
        assertThat(IndexLexicalIa.construire(Map.of()).rechercher("contrôle", 3)).isEmpty();
    }
}
