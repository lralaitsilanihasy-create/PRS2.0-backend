package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ 2026-10-16 (demande front « import-specifications », recette) — la lecture des articles par les tableaux, contre la sortie de
 * référence du prototype du front ({@code src/test/resources/import-dao/attendus-articles}, copies de
 * {@code frontendprs2/scripts/import-dao/attendus-articles}) : lot, numéro, désignation, unité, quantités, série et nombre de
 * caractéristiques, et les seuls écarts réels de désignation. Les DAO réels (fichiers du pilote, non versionnés) sont lus dans
 * {@code PRS_DAO_DIR}, à défaut le dossier Téléchargements : absents, la recette est ignorée.
 */
class LectureTableauxRecetteTest {

    private static Path dao(String nom) {
        String env = System.getenv("PRS_DAO_DIR");
        Path dir = env != null && !env.isBlank() ? Path.of(env) : Path.of(System.getProperty("user.home"), "Downloads");
        Path p = dir.resolve(nom);
        return Files.isRegularFile(p) ? p : null;
    }

    private static String texte(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asString();
    }

    private static BigDecimal nombre(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.decimalValue();
    }

    private static boolean egaux(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Parité avec le prototype : chaque article, ses quantités et ses caractéristiques ; les écarts réels seuls")
    @CsvSource({
        "171.DAO_2463_MAT_INFO_VERSION_FINAL.pdf, mesupres-2463.json, 0",
        "297.Projet_de_DAO_Mat_Info_re_corrig_.pdf, paositra-297.json, 0",
        "207.DAOO_6114_VF_2026_VF_CORRIGEE.pdf, mef-6114-207.json, 2",
        "projetdao.DAO_Ankily_et_Mahasoa.pdf, mtp-ankily-mahasoa.json, 1",
        "225.DAOO_6213__CNM_2026_final_corrig_.pdf, mef-6213-225.json, 0" })
    void parite(String pdf, String attendu, int ecarts) throws Exception {
        Path fichier = dao(pdf);
        assumeTrue(fichier != null, "DAO absent (" + pdf + ") : recette ignorée.");
        List<LectureTableaux.Article> lus = LectureTableaux.lire(pdf, Files.readAllBytes(fichier));
        JsonNode ref;
        try (InputStream in = getClass().getResourceAsStream("/import-dao/attendus-articles/" + attendu)) {
            ref = new ObjectMapper().readTree(in);
        }
        List<String> differences = new ArrayList<>();
        assertThat(lus).as("nombre d'articles").hasSize(ref.size());
        for (int i = 0; i < ref.size(); i++) {
            JsonNode r = ref.get(i);
            LectureTableaux.Article a = lus.get(i);
            String cle = "article " + i + " (lot " + texte(r.get("lot")) + ", n° " + texte(r.get("code")) + ")";
            Integer lotRef = r.get("lot") == null || r.get("lot").isNull() ? null : r.get("lot").asInt();
            if (!Objects.equals(lotRef, a.lot)) {
                differences.add(cle + " : lot " + a.lot);
            }
            if (!Objects.equals(texte(r.get("code")), a.code)) {
                differences.add(cle + " : numéro " + a.code);
            }
            if (!Objects.equals(texte(r.get("designation")), a.designation)) {
                differences.add(cle + " : désignation « " + a.designation + " »");
            }
            if (!Objects.equals(texte(r.get("designationSpec")), a.designationSpec)) {
                differences.add(cle + " : désignation des spécifications « " + a.designationSpec + " »");
            }
            if (!Objects.equals(texte(r.get("unite")), a.unite)) {
                differences.add(cle + " : unité « " + a.unite + " »");
            }
            if (!egaux(nombre(r.get("quantite")), a.quantite) || !egaux(nombre(r.get("min")), a.min) || !egaux(nombre(r.get("max")), a.max)) {
                differences.add(cle + " : quantités " + a.quantite + " / " + a.min + " / " + a.max);
            }
            if (!Objects.equals(texte(r.get("serie")), a.serie)) {
                differences.add(cle + " : série " + a.serie);
            }
            if (r.get("caracteristiques").size() != a.caracteristiques.size()) {
                differences.add(cle + " : " + a.caracteristiques.size() + " caractéristiques au lieu de " + r.get("caracteristiques").size());
            }
        }
        assertThat(differences).as(pdf).isEmpty();
        long lusEcarts = lus.stream().filter(a -> a.designation != null && a.designationSpec != null
                && LectureTableaux.ecart(a.designation, a.designationSpec)).count();
        assertThat(lusEcarts).as("écarts de désignation").isEqualTo(ecarts);
    }
}
