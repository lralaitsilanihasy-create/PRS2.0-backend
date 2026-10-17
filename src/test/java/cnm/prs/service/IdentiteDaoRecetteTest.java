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

import cnm.prs.dto.ImportDaoResult;

/**
 * ⚠️ 2026-10-17 (demande front « identite-dao-importe », mesure, condition de livraison) — le verdict d'identité sur les DAO réels :
 * <ol>
 *   <li>sur sa propre ligne, chaque DAO est {@code CONFORME} ou {@code NON_PROUVE}, jamais {@code ETRANGER} ;</li>
 *   <li>sur une autre des lignes 303336 à 303339 (MEF, même autorité, même mode, contrats-cadres aux objets voisins), chaque DAO de
 *       ces lignes est {@code ETRANGER}, la voisine nommée étant sa propre ligne.</li>
 * </ol>
 * Les lignes et leurs voisines sont celles du plan 200343 de DBPRS20 (17/10) ; les lignes des autres DAO sont reconstituées de leur
 * page de garde, comme un plan les écrirait. Les DAO (fichiers du pilote, non versionnés) sont cherchés dans {@code PRS_DAO_DIR}, les
 * Téléchargements et {@code frontendprs2/PDF_generes} ; absents, leur cas est ignoré. Le détail des critères est imprimé.
 */
class IdentiteDaoRecetteTest {

    private static final String MEF = "MINISTERE DE L'ECONOMIE ET DES FINANCES";
    private static final String AOO = "Appel d'offres ouvert";
    private static final List<String> AUTRES = List.of("JIRO SY RANO MALAGASY", "MINISTERE DE L'ENSEIGNEMENT SUPERIEUR ET DE LA RECHERCHE "
            + "SCIENTIFIQUE", "MESupReS", "MINISTERE DES TRAVAUX PUBLICS", "MINISTERE DU TOURISME ET DE L'ARTISANAT",
            "DIRECTION GÉNÉRALE DE LA SÉCURITÉ ROUTIÈRE", "COMMISSION ELECTORALE NATIONALE INDÉPENDANTE", MEF);

    /** Le plan 200343 du MEF (exercice 2026), lignes 303336 à 303358. */
    private static final Map<Integer, String> PLAN_MEF = new LinkedHashMap<>();

    static {
        PLAN_MEF.put(303336, "Entretien et réparation des véhicules administratifs pour la Direction Administrative et Financière (CONTRAT - CADRE)");
        PLAN_MEF.put(303337, "Fourniture d’articles de bureau (CONTRAT - CADRE)");
        PLAN_MEF.put(303338, "Fourniture de produits d’entretien et des petits matériels (Contrat cadre)");
        PLAN_MEF.put(303339, "Fourniture de consomptibles informatiques (CONTRAT - CADRE)");
        PLAN_MEF.put(303340, "Entretien et réparation des matériels et mobiliers de bureau pour le CSC");
        PLAN_MEF.put(303341, "Maintenance des réseaux, logiciels et systèmes informatiques de la DSI");
        PLAN_MEF.put(303342, "Entretien et maintenance du Data Center du MEF");
        PLAN_MEF.put(303343, "Documentation et Abonnement réparties en deux (02) lots : - Lot 01 : pour la DRH - Lot 02 : pour le SAD");
        PLAN_MEF.put(303344, "Impression, reliures, insertions, publicité et promotion pour le CSC");
        PLAN_MEF.put(303345, "Colloques, séminaires, conférences pour le CSC");
        PLAN_MEF.put(303346, "Fourniture de mobiliers scolaires pour l'EPP Miadana sise dans le Commune Rurale Alasora");
        PLAN_MEF.put(303347, "Fourniture d'Instruments spécialisés réparties en deux (02) lots : - Lot 01 : pour la DRH - Lot 02 : pour le SAU");
        PLAN_MEF.put(303348, "d'atelier pour la DSI");
        PLAN_MEF.put(303349, "Entretien et réparation du véhicule administratif Immatriculé 8476 TAN de marque Chevrolet AVEO");
        PLAN_MEF.put(303350, "Entretien et réparation du véhicule administratif Immatriculé 8478 TAN de marque Chevrolet AVEO");
        PLAN_MEF.put(303351, "Entretien et réparation du véhicule administratif Immatriculé 8475 TAN de marque Chevrolet AVEO");
        PLAN_MEF.put(303352, "Entretien et réparation du véhicule administratif Immatriculé 5557 TAD de marque Renault KANGOO");
        PLAN_MEF.put(303353, "Entretien et réparation du véhicule administratif immatriculé 6304 TAJ de marque Ford Ranger auprès du concessionnaire");
        PLAN_MEF.put(303354, "Entretien et réparation du véhicule administratif immatriculé 6305 TAJ de marque Ford Ranger auprès du concessionnaire");
        PLAN_MEF.put(303355, "Entretien et réparation de véhicule administratif immatriculé 5182 TU de marque Nissan SW");
        PLAN_MEF.put(303356, "Entretien et réparation du véhicule administratif immatriculé 6304 TAJ de marque Ford Ranger");
        PLAN_MEF.put(303357, "Entretien et réparation du véhicule administratif immatriculé 6305 TAJ de marque Ford Ranger");
        PLAN_MEF.put(303358, "Entretien et réparation du véhicule administratif N°5501 TAC marque RENAULT CLIO");
    }

    /** Les DAO des lignes MEF : fichier → sa ligne. */
    private static final Map<String, Integer> DAO_MEF = new LinkedHashMap<>();

    static {
        DAO_MEF.put("207.DAOO_6114_VF_2026_VF_CORRIGEE.pdf", 303338);
        DAO_MEF.put("225.DAOO_6213__CNM_2026_final_corrig_.pdf", 303336);
        DAO_MEF.put("DAO_CC_303339_consomptibles_informatiques_charte_application.pdf", 303339);
        DAO_MEF.put("DAO_CC_303337_articles_bureau_v3.pdf", 303337);
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

    private static IdentiteDao.Texte texte(Path p) throws Exception {
        LecturePdf.Pagines t = LecturePdf.paragraphesPagines(Files.readAllBytes(p));
        return new IdentiteDao.Texte(t.paragraphes(), t.pages());
    }

    private static IdentiteDao.Plan ligneMef(int idDetail) {
        return new IdentiteDao.Plan(PLAN_MEF.get(idDetail), List.of(MEF), List.of(), AOO, "Contrat cadre", 1, List.of());
    }

    private static List<IdentiteDao.Voisine> voisinesMef(int idDetail) {
        return PLAN_MEF.entrySet().stream().filter(e -> e.getKey() != idDetail).map(e -> new IdentiteDao.Voisine(e.getKey(), e.getValue(), List.of()))
                .toList();
    }

    private static List<String> autresQue(String propre) {
        boolean mesupres = propre.contains("ENSEIGNEMENT");
        return AUTRES.stream().filter(a -> !IdentiteDao.cle(a).equals(IdentiteDao.cle(propre)))
                .filter(a -> !(mesupres && "MESupReS".equals(a))).toList();
    }

    private static String rapport(String titre, ImportDaoResult.Identite v) {
        StringBuilder sb = new StringBuilder(titre).append(" → ").append(v.verdict());
        if (v.voisine() != null) {
            sb.append(" (voisine ").append(v.voisine().idDetail()).append(')');
        }
        sb.append('\n');
        v.criteres().forEach(c -> sb.append("    ").append(c.critere()).append(' ').append(c.etat()).append(c.page() == null ? "" : " p." + c.page())
                .append(c.extrait() == null ? "" : " « " + c.extrait() + " »").append('\n'));
        sb.append("    ").append(v.motif()).append('\n');
        return sb.toString();
    }

    @Test
    @DisplayName("Mesure 1 : sur sa propre ligne, jamais ETRANGER")
    void surSaLigne() throws Exception {
        StringBuilder sortie = new StringBuilder();
        List<String> etrangers = new ArrayList<>();
        int mesures = 0;
        for (Map.Entry<String, Integer> e : DAO_MEF.entrySet()) {
            Path p = dao(e.getKey());
            if (p == null) {
                continue;
            }
            mesures++;
            ImportDaoResult.Identite v = IdentiteDao.juger(texte(p), ligneMef(e.getValue()), voisinesMef(e.getValue()), autresQue(MEF), Set.of());
            sortie.append(rapport(e.getKey() + " sur " + e.getValue(), v));
            if (IdentiteDao.ETRANGER.equals(v.verdict())) {
                etrangers.add(e.getKey());
            }
        }
        Map<String, IdentiteDao.Plan> autres = new LinkedHashMap<>();
        autres.put("171.DAO_2463_MAT_INFO_VERSION_FINAL.pdf", new IdentiteDao.Plan(
                "Fourniture et livraison des matériels informatiques répartis en cinq (5) lots (à commande)",
                List.of("MINISTERE DE L'ENSEIGNEMENT SUPERIEUR ET DE LA RECHERCHE SCIENTIFIQUE"), List.of("MESupReS"), AOO, null, 5,
                List.of("ORDINATEURS ET DIVERS POUR LE MINISTERE", "ORDINATEUR POUR AMBATONDRAZAKA", "DIVERS MATERIELS POUR AMBATONDRAZAKA",
                        "ORDINATEUR POUR FORT DAUPHIN", "DIVERS MATERIELS POUR FORT DAUPHIN")));
        autres.put("297.Projet_de_DAO_Mat_Info_re_corrig_.pdf", new IdentiteDao.Plan("Fourniture de matériels informatiques répartis en seize (16) lots",
                List.of("PAOSITRA MALAGASY"), List.of(), AOO, null, 16, List.of("Ordinateurs portables", "Ordinateurs de bureau", "Scanners",
                        "Imprimantes N&B", "Imprimantes couleurs", "Photocopieuses", "Dupli copieur", "Imprimante code à barre DTRA",
                        "Imprimantes étiquettes code à barre", "Tablettes", "Serveurs", "Serveurs de stockage en réseau (NAS)", "Écrans développeurs",
                        "Onduleurs", "Caméras de surveillance", "Écran interactif")));
        autres.put("projetdao.DAO_Ankily_et_Mahasoa.pdf", new IdentiteDao.Plan("Travaux d'entretien progressif des routes reliant Ankily et Mahasoa",
                List.of("MINISTERE DES TRAVAUX PUBLICS"), List.of(), AOO, null, 1, List.of()));
        for (Map.Entry<String, IdentiteDao.Plan> e : autres.entrySet()) {
            Path p = dao(e.getKey());
            if (p == null) {
                continue;
            }
            mesures++;
            ImportDaoResult.Identite v = IdentiteDao.juger(texte(p), e.getValue(), List.of(), autresQue(e.getValue().autorites().get(0)), Set.of());
            sortie.append(rapport(e.getKey() + " sur sa ligne", v));
            if (IdentiteDao.ETRANGER.equals(v.verdict())) {
                etrangers.add(e.getKey());
            }
        }
        System.out.println(sortie);
        assumeTrue(mesures > 0, "aucun DAO réel disponible : mesure ignorée");
        assertThat(etrangers).as("DAO jugés étrangers sur leur propre ligne").isEmpty();
    }

    @Test
    @DisplayName("Mesure 2 : sur une autre des lignes 303336 à 303339, ETRANGER, la voisine nommée étant sa propre ligne")
    void surUneAutreLigne() throws Exception {
        StringBuilder sortie = new StringBuilder();
        List<String> manques = new ArrayList<>();
        int mesures = 0;
        for (Map.Entry<String, Integer> e : DAO_MEF.entrySet()) {
            Path p = dao(e.getKey());
            if (p == null) {
                continue;
            }
            IdentiteDao.Texte t = texte(p);
            for (int cible = 303336; cible <= 303339; cible++) {
                if (cible == e.getValue()) {
                    continue;
                }
                mesures++;
                ImportDaoResult.Identite v = IdentiteDao.juger(t, ligneMef(cible), voisinesMef(cible), autresQue(MEF), Set.of());
                sortie.append(rapport(e.getKey() + " sur " + cible, v));
                if (!IdentiteDao.ETRANGER.equals(v.verdict()) || v.voisine() == null || !e.getValue().equals(v.voisine().idDetail())) {
                    manques.add(e.getKey() + " sur " + cible + " : " + v.verdict() + (v.voisine() == null ? "" : " (voisine " + v.voisine().idDetail() + ")"));
                }
            }
        }
        System.out.println(sortie);
        assumeTrue(mesures > 0, "aucun DAO réel disponible : mesure ignorée");
        assertThat(manques).as("DAO non jugés étrangers sur une autre ligne").isEmpty();
    }
}
