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
 * ⚠️ 2026-10-17 (demande front « champs-deduits-du-dao », mesure) — sur les DAO réels :
 * <ul>
 *   <li>B1 : les termes de champ des sections attestées <strong>par encadrement</strong> (règle 10 de {@link LectureDao} : sections
 *       faites seulement de texte court, « Sans objet. ») ;</li>
 *   <li>B2 : {@code B04-CD-01} déduit des titres des fiches de renseignements ({@link CadrageParClause#fiches}).</li>
 * </ul>
 * L'attendu est relevé à la main dans chaque DAO ; une proposition absente de l'attendu est fausse (critère bloquant). Les DAO
 * (fichiers du pilote, non versionnés) sont cherchés dans {@code PRS_DAO_DIR}, les Téléchargements et {@code frontendprs2/PDF_generes}.
 */
class ChampsDeduitsRecetteTest {

    private record Cas(String forme, String categorie, Map<String, String> attendu) {
    }

    private static final Map<String, Cas> CAS = new LinkedHashMap<>();

    static {
        CAS.put("171.DAO_2463_MAT_INFO_VERSION_FINAL.pdf", new Cas("A_COMMANDE", "FOURNITURES_SERVICES", Map.of("B04-CD-01", "A1,A2,A3,A4")));
        CAS.put("297.Projet_de_DAO_Mat_Info_re_corrig_.pdf", new Cas("QUANTITE_FIXE", "FOURNITURES_SERVICES", Map.of("B05-MO-01", "Ariary", "B04-CD-01", "A1,A2,A3,A4")));
        CAS.put("207.DAOO_6114_VF_2026_VF_CORRIGEE.pdf", new Cas("CONTRAT_CADRE", "FOURNITURES_SERVICES", Map.of("B09-EA-01", "NON", "B04-CD-01", "A1,A2,A3,A4")));
        CAS.put("225.DAOO_6213__CNM_2026_final_corrig_.pdf", new Cas("CONTRAT_CADRE", "FOURNITURES_SERVICES", Map.of("B09-AU-01", "NON", "B04-CD-01", "A1,A2,A3,A4")));
        CAS.put("projetdao.DAO_Ankily_et_Mahasoa.pdf", new Cas("QUANTITE_FIXE", "TRAVAUX", Map.of("B04-CD-01", "A1,A2,A3,A4")));
        CAS.put("DAO_CC_303337_articles_bureau_v3.pdf", new Cas("CONTRAT_CADRE", "FOURNITURES_SERVICES", Map.of("B09-AU-01", "NON", "B04-CD-01", "A1,A2,A3,A4")));
        CAS.put("DAO_CC_303339_consomptibles_informatiques_charte_application.pdf", new Cas("CONTRAT_CADRE", "FOURNITURES_SERVICES", Map.of("B09-EA-01", "NON", "B09-AU-01", "NON", "B04-CD-01", "A1,A2,A3,A4")));
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

    /** Les sections d'un modèle faites seulement de texte court sans jeton : celles que la règle 10 peut attester. */
    private static Set<String> courtes(FichierCommande.Modele modele) {
        Map<String, Boolean> courte = new LinkedHashMap<>();
        for (LectureDao.Unite u : LectureDao.unites(modele.elements())) {
            boolean c = !u.texte().contains("{{") && LectureDao.norm(u.texte()).length() < 40;
            for (String s : u.sections()) {
                courte.merge(s, c, Boolean::logicalAnd);
            }
        }
        Set<String> out = new java.util.LinkedHashSet<>();
        courte.forEach((s, c) -> {
            if (c) {
                out.add(s);
            }
        });
        return out;
    }

    @Test
    @DisplayName("Mesure : propositions de B1 (encadrement) et de B2 (fiches A1-A4) sur les DAO réels, aucune fausse")
    void mesure() throws Exception {
        ModelesDao modeles = new ModelesDao();
        StringBuilder sortie = new StringBuilder();
        List<String> fausses = new ArrayList<>();
        int mesures = 0;
        for (Map.Entry<String, Cas> e : CAS.entrySet()) {
            Path p = dao(e.getKey());
            if (p == null) {
                continue;
            }
            mesures++;
            List<String> origines = LecturePdf.paragraphesDOrigine(Files.readAllBytes(p));
            List<String> paragraphes = origines.stream().map(LectureDao::norm).toList();
            Map<String, String> lues = new LinkedHashMap<>();
            sortie.append("===== ").append(e.getKey()).append('\n');
            for (ModelesDao.Couverture c : ModelesDao.couvertures(e.getValue().forme(), e.getValue().categorie())) {
                FichierCommande.Modele modele = modeles.modele(c.sigle());
                Set<String> courtes = courtes(modele);
                LectureDao.Resultat r = LectureDao.lire(c.sigle(), modele, paragraphes, x -> null, origines, Map.of());
                for (LectureDao.Reponse rc : r.reponsesChamps()) {
                    if (rc.section() != null && courtes.contains(rc.section())) {
                        lues.put(rc.cle(), rc.valeur());
                        sortie.append("  B1 ").append(c.sigle()).append(' ').append(rc.cle()).append(" = ").append(rc.valeur())
                                .append(" (section ").append(rc.section()).append(")\n");
                    }
                }
            }
            CadrageParClause.Fiches f = CadrageParClause.fiches(origines);
            if (!f.codes().isEmpty()) {
                lues.put("B04-CD-01", String.join(",", f.codes()));
                sortie.append("  B2 B04-CD-01 = ").append(String.join(",", f.codes())).append(" « ").append(f.extrait()).append(" »\n");
            }
            lues.forEach((k, v) -> {
                if (!v.equals(e.getValue().attendu().get(k))) {
                    fausses.add(e.getKey() + " : " + k + " = " + v);
                }
            });
        }
        System.out.println(sortie);
        assumeTrue(mesures > 0, "aucun DAO réel disponible : mesure ignorée");
        assertThat(fausses).as("propositions fausses").isEmpty();
    }
}
