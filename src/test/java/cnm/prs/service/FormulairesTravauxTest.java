package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.ArticleBesoinDto;
import cnm.prs.dto.MaterielExigeDto;
import cnm.prs.dto.PersonnelExigeDto;

/** ⚠️ 2026-10-17 (demande front « charte-dao-travaux », T5 à T7) — les formulaires techniques du candidat des travaux. */
class FormulairesTravauxTest {

    private static final TableauxBesoin.Contexte DEUX_LOTS = new TableauxBesoin.Contexte(false, false, "Entretien des routes",
            List.of(new TableauxBesoin.Lot(1, "Ankily – Mahasoa"), new TableauxBesoin.Lot(2, "Mahasoa – Ihosy")));

    private static ArticleBesoinDto article(int lot, int ordre, String prix, String serie, String serieLibelle, String designation,
            boolean sousDetail) {
        ArticleBesoinDto a = new ArticleBesoinDto();
        a.setLot(lot);
        a.setOrdre(ordre);
        a.setNumeroPrix(prix);
        a.setSerie(serie);
        a.setSerieLibelle(serieLibelle);
        a.setDesignation(designation);
        a.setUnite("M3");
        a.setQuantite(BigDecimal.TEN);
        a.setSousDetail(sousDetail);
        return a;
    }

    private static final List<ArticleBesoinDto> ARTICLES = List.of(
            article(1, 1, "001", "000", "Installation de chantier", "Installation et repli de chantier", false),
            article(1, 2, "529", "500", "Ouvrages", "Maçonnerie de moellons", false),
            article(1, 3, "540", "500", "Ouvrages", "Béton B2 dosé à 250 kg/m3", true),
            article(1, 4, "621", "600", "Chaussée", "Réglage de plateforme", false),
            article(2, 1, "001", "000", "Installation de chantier", "Installation et repli de chantier", false),
            article(2, 2, "630", "600", "Chaussée", "Couche de roulement", true));

    private static String texte(byte[] docx) throws Exception {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor x = new XWPFWordExtractor(d)) {
            return x.getText();
        }
    }

    @Test
    @DisplayName("T5 : K1 avec le taux de TVA de la fiche ; sous-détail citant par lot l'installation de chantier et les prix marqués (astérisque)")
    void k1EtSousDetail() throws Exception {
        assertThat(texte(FormulairesTravaux.k1(new BigDecimal("20")))).contains("actuellement de 20 % pour les marchés de travaux",
                "K1 = (1 + A1/100) × (1 + A2/100) / (1 − [A3/100 × (1 + T/100)])", "Frais de siège\ta.9\tA3", "Le candidat");
        String s = texte(FormulairesTravaux.sousDetail(DEUX_LOTS, ARTICLES));
        assertThat(s).contains("Prix à sous-détailler, lot 1 : n° 001, 540*.", "Prix à sous-détailler, lot 2 : n° 001, 630*.",
                "Composante du prix\tNombre\tUnité ou jour\tQuantité totale\tPrix unitaire\tTotal", "Total partiel — matériel",
                "Total déboursés secs = « D »", "Prix de vente à l'unité = (« D » × « K1 ») / « R » =");
        // Sans besoin : les consignes et le cadre, sans liste.
        assertThat(texte(FormulairesTravaux.sousDetail(DEUX_LOTS, List.of()))).doesNotContain("Prix à sous-détailler,").contains("1 / Les candidats");
        // Q2 — l'astérisque au détail quantitatif, retiré à la relecture.
        byte[] dqe = TableauxBesoin.dqe(DEUX_LOTS, ARTICLES, null);
        assertThat(texte(dqe)).contains("540*\tBéton B2", "630*\tCouche de roulement");
        assertThat(LectureTableaux.articles(LectureTableaux.tableauxDocx(dqe))).extracting(a -> a.lot + "#" + a.code)
                .containsExactly("1#001", "1#529", "1#540", "1#621", "2#001", "2#630");
    }

    @Test
    @DisplayName("T6 : personnel pré-rempli (une colonne par lot si exigé par lot), matériel pré-rempli (« — » sans minimum en propre), "
            + "listes vides imprimées en cadre")
    void personnelEtMateriel() throws Exception {
        PersonnelExigeDto chef = new PersonnelExigeDto();
        chef.setPoste("Responsable de chantier");
        chef.setNombre(1);
        chef.setDiplome("Ingénieur des travaux publics");
        chef.setExperienceAnnees(10);
        chef.setDomaineExperience("travaux routiers");
        chef.setParLot(true);
        String p = texte(FormulairesTravaux.personnel(DEUX_LOTS.lots(), List.of(chef)));
        assertThat(p).contains("Poste\tNombre exigé\tDiplôme et expérience exigés\tNom proposé — lot 1\tNom proposé — lot 2",
                "Responsable de chantier\t1\tIngénieur des travaux publics ; 10 ans d'expérience (travaux routiers)",
                "Je certifie sincères et véritables les indications portées ci-dessus.", "MODÈLE DE CURRICULUM VITAE", "Le déclarant");
        chef.setParLot(false);
        assertThat(texte(FormulairesTravaux.personnel(DEUX_LOTS.lots(), List.of(chef)))).contains("Diplôme et expérience exigés\tNom proposé\n")
                .doesNotContain("lot 2");
        assertThat(texte(FormulairesTravaux.personnel(DEUX_LOTS.lots(), List.of()))).contains("Poste\tNombre exigé");

        MaterielExigeDto niveleuse = new MaterielExigeDto();
        niveleuse.setDesignation("Niveleuse");
        niveleuse.setCaracteristique("≥ 140 ch");
        niveleuse.setNombre(1);
        niveleuse.setMinimumEnPropre(1);
        MaterielExigeDto compacteur = new MaterielExigeDto();
        compacteur.setDesignation("Compacteur vibrant");
        compacteur.setCaracteristique("≥ 10 t");
        compacteur.setNombre(1);
        compacteur.setMinimumEnPropre(0);
        assertThat(texte(FormulairesTravaux.materiel(List.of(niveleuse, compacteur)))).contains("Niveleuse\t≥ 140 ch\t1\t1\t\t",
                "Compacteur vibrant\t≥ 10 t\t1\t—\t\t", "Statut proposé (propriété, location longue durée, à louer)\tLocalisation");
        assertThat(texte(FormulairesTravaux.location())).contains("ENGAGEMENT DE LOCATION", "vis-à-vis de l'autorité contractante",
                "Signature et cachet du loueur").doesNotContain("Commune");
    }

    @Test
    @DisplayName("T7 : liste des plans (B04-CD-03, « Néant » sans plan), plannings bornés par le délai du lot (séries en lignes), "
            + "mobilisation pré-remplie, garantie, plan de charge")
    void plansPlanningsEtPlanDeCharge() throws Exception {
        assertThat(texte(FormulairesTravaux.listePlans(null))).contains("Néant.");
        assertThat(texte(FormulairesTravaux.listePlans("1. Plan de situation\n- Profil en long\n\n"))).contains("N°\tTitre du plan",
                "1\tPlan de situation", "2\tProfil en long");

        assertThat(FormulairesTravaux.mois("six (06) mois à compter de l'ordre de service")).isEqualTo(6);
        assertThat(FormulairesTravaux.mois("120 jours calendaires")).isEqualTo(4);
        assertThat(FormulairesTravaux.mois("2 ans")).isEqualTo(24);
        assertThat(FormulairesTravaux.mois("dès notification")).isNull();
        assertThat(FormulairesTravaux.Grille.de(6)).isEqualTo(new FormulairesTravaux.Grille(6, "mois"));
        assertThat(FormulairesTravaux.Grille.de(24)).isEqualTo(new FormulairesTravaux.Grille(8, "trimestre"));
        assertThat(FormulairesTravaux.Grille.de(null)).isEqualTo(new FormulairesTravaux.Grille(12, "mois"));

        PersonnelExigeDto topographe = new PersonnelExigeDto();
        topographe.setPoste("Topographe");
        MaterielExigeDto camion = new MaterielExigeDto();
        camion.setDesignation("Camion benne");
        camion.setCaracteristique("≥ 10 m3");
        Map<Integer, String> delais = new HashMap<>();
        delais.put(1, "six (06) mois");
        delais.put(2, "quatre (04) mois");
        String s = texte(FormulairesTravaux.plannings(DEUX_LOTS, ARTICLES, List.of(topographe), List.of(camion), delais, 12));
        assertThat(s).contains("8-a : Planning général d'exécution des travaux ;", "(1 case = 1 mois)",
                "Lot n° 1 : Ankily – Mahasoa — Délai maximal prévu au DAO : six (06) mois — une colonne par mois.",
                "Série / tâche\tQ\tRendement\t1\t2\t3\t4\t5\t6\n", "000 Installation de chantier", "500 Ouvrages", "600 Chaussée",
                "Repli de chantier", "Série / tâche\tQ\tRendement\t1\t2\t3\t4\n",
                "MODÈLE 8-b : PLANNING DE MOBILISATION DU PERSONNEL ET DU MATÉRIEL", "Topographe", "Camion benne (≥ 10 m3)",
                "MODÈLE 8-c", "Fer à béton", "MODÈLE 8-d : ÉCHÉANCIER DE PAIEMENT", "Mois 6", "MODÈLE 8-e",
                "Délai de garantie prévu au DAO : 12 mois").doesNotContain("DIRTPM", "Mois 7");
        assertThat(texte(FormulairesTravaux.planDeCharge())).contains("Désignation du marché ou du contrat", "indiquer « NEANT »",
                "Le candidat");
    }
}
