package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-17 (demande front « cadrage-depuis-dao », mesure) — les réponses de cadrage lues par clause sur les DAO réels, toutes clés
 * cherchées, contre les réponses relevées à la main dans leurs données particulières, acte d'engagement et CCAP (l'attendu ci-dessous).
 * Le critère bloquant : <strong>aucune réponse fausse</strong> ; une réponse non trouvée n'est pas un échec. Les DAO (fichiers du
 * pilote, non versionnés) sont cherchés dans {@code PRS_DAO_DIR}, les Téléchargements et {@code frontendprs2/PDF_generes} ; absents,
 * leur cas est ignoré. Le détail est imprimé.
 */
class CadrageParClauseRecetteTest {

    /** Par DAO, les réponses justes connues ; une réponse lue absente d'ici est fausse. */
    private static final Map<String, Map<String, String>> ATTENDU = new LinkedHashMap<>();

    static {
        // 2463 : le cadrage vérifié du jeu rejoué (docs/export/2463/verification.md).
        ATTENDU.put("171.DAO_2463_MAT_INFO_VERSION_FINAL.pdf", Map.of("prixRevisable", "NON", "provenance", "NATIONAL", "variantes", "NON",
                "garantieSoumission", "OUI"));
        ATTENDU.put("297.Projet_de_DAO_Mat_Info_re_corrig_.pdf", Map.of("variantes", "NON"));
        ATTENDU.put("207.DAOO_6114_VF_2026_VF_CORRIGEE.pdf", Map.of("prixRevisable", "NON", "garantieSoumission", "OUI"));
        ATTENDU.put("225.DAOO_6213__CNM_2026_final_corrig_.pdf", Map.of("prixRevisable", "NON"));
        ATTENDU.put("projetdao.DAO_Ankily_et_Mahasoa.pdf", Map.of("prixRevisable", "NON", "garantieSoumission", "OUI"));
        ATTENDU.put("DAO_CC_303337_articles_bureau_v3.pdf", Map.of("prixRevisable", "NON"));
        ATTENDU.put("DAO_CC_303339_consomptibles_informatiques_charte_application.pdf", Map.of("prixRevisable", "NON"));
    }

    private static Path dao(String nom) {
        List<Path> dossiers = new ArrayList<>();
        String env = System.getenv("PRS_DAO_DIR");
        if (env != null && !env.isBlank()) {
            dossiers.add(Path.of(env));
        }
        dossiers.add(Path.of(System.getProperty("user.home"), "Downloads"));
        dossiers.add(Path.of(System.getProperty("user.home"), "frontendprs2", "PDF_generes"));
        return dossiers.stream().map(d -> d.resolve(nom)).filter(Files::isRegularFile).findFirst().orElse(null);
    }

    @Test
    @DisplayName("Mesure : les réponses lues par clause sur les DAO réels, aucune fausse")
    void mesure() throws Exception {
        StringBuilder sortie = new StringBuilder();
        List<String> fausses = new ArrayList<>();
        int mesures = 0;
        for (Map.Entry<String, Map<String, String>> e : ATTENDU.entrySet()) {
            Path p = dao(e.getKey());
            if (p == null) {
                continue;
            }
            mesures++;
            List<String> paragraphes = LecturePdf.paragraphesDOrigine(Files.readAllBytes(p));
            CadrageParClause.Resultat r = CadrageParClause.lire(paragraphes, Set.copyOf(CadrageParClause.CLES));
            sortie.append("===== ").append(e.getKey()).append('\n');
            for (CadrageParClause.Reponse x : r.reponses()) {
                boolean juste = x.valeur().equals(e.getValue().get(x.cle()));
                sortie.append("  ").append(x.cle()).append(" = ").append(x.valeur()).append(juste ? " (juste)" : " (FAUSSE ?)")
                        .append(" § ").append(x.paragraphe()).append(" [").append(x.partie()).append("]").append(" « ").append(x.extrait()).append(" »\n");
                if (!juste) {
                    fausses.add(e.getKey() + " : " + x.cle() + " = " + x.valeur());
                }
            }
            r.conflits().forEach((k, v) -> sortie.append("  CONFLIT ").append(k).append(' ').append(v).append('\n'));
        }
        System.out.println(sortie);
        assumeTrue(mesures > 0, "aucun DAO réel disponible : mesure ignorée");
        assertThat(fausses).as("réponses fausses").isEmpty();
    }
}
