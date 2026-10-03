package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;

/** ⚠️ Import du DAO (2026-09-28, ADR-0012) — la lecture par modèle inversé, sans base ni Spring. */
class LectureDaoTest {

    @Test
    @DisplayName("norm : apostrophes, tirets, guillemets, espaces insécables et blancs ramenés à une forme")
    void normalisation() {
        assertThat(LectureDao.norm("  L’autorité contractante – « prix »\n\tfin ")).isEqualTo("L'autorité contractante - \" prix \" fin");
    }

    @Test
    @DisplayName("valeurSaisie : dates JJ/MM/AAAA → ISO, date-heure, montants et pourcentages → nombre, pointillés → rien")
    void valeursDeSaisie() {
        assertThat(LectureDao.valeurSaisie("20/11/2026", "DATE", null)).isEqualTo("2026-11-20");
        assertThat(LectureDao.valeurSaisie("20/11/2026 10:00", "DATE_HEURE", null)).isEqualTo("2026-11-20T10:00");
        assertThat(LectureDao.valeurSaisie("250 000 000 Ariary", "MONTANT", null)).isEqualTo("250000000");
        assertThat(LectureDao.valeurSaisie("12,5 %", "POURCENTAGE", null)).isEqualTo("12.5");
        assertThat(LectureDao.valeurSaisie("1 500", null, "chiffres")).isEqualTo("1500");
        assertThat(LectureDao.valeurSaisie("………", "TEXTE", null)).isNull();
        assertThat(LectureDao.valeurSaisie("trente jours", "NOMBRE", null)).isNull();
        assertThat(LectureDao.valeurSaisie("Texte libre", null, null)).isEqualTo("Texte libre");
    }

    @Test
    @DisplayName("2026-10-01 (front 0afc489, règle 6) : un terme « contient » d'une section retenue ajoute une option ENTIÈRE à la "
            + "liste du champ, dans l'ordre du référentiel ; un fragment ne dit rien ; un terme « = » contraire est un conflit")
    void reponseDeduiteDUnTermeContient() {
        String modele = String.join("\n",
                "CONDITION\tGARANTIE-BANCAIRE\u001FgarantieSoumission = OUI et B05-GQ-02 contient Garantie bancaire",
                "CONDITION\tGARANTIE-CAUTION\u001FgarantieSoumission = OUI et B05-GQ-02 contient Caution personnelle et solidaire",
                "CONDITION\tGARANTIE-CHEQUE\u001FgarantieSoumission = OUI et B05-GQ-02 contient Chèque de banque",
                "CONDITION\tFRAGMENT\u001FB05-GE-03 contient bancaire",
                "PARA\tUne garantie de soumission doit être fournie dans l'une des formes suivantes :",
                "PARA\t{{SI:GARANTIE-BANCAIRE}}",
                "PARA\t- Soit une garantie bancaire émise par une banque primaire agréée",
                "PARA\t{{FINSI:GARANTIE-BANCAIRE}}",
                "PARA\t{{SI:GARANTIE-CAUTION}}",
                "PARA\t- Soit une caution personnelle et solidaire d'un organisme agréé",
                "PARA\t{{FINSI:GARANTIE-CAUTION}}",
                "PARA\t{{SI:GARANTIE-CHEQUE}}",
                "PARA\t- Soit un chèque de banque libellé au nom du Trésor public",
                "PARA\t{{FINSI:GARANTIE-CHEQUE}}",
                "PARA\t{{SI:FRAGMENT}}",
                "PARA\tLa garantie de bonne exécution est remise sous forme bancaire auprès du comptable",
                "PARA\t{{FINSI:FRAGMENT}}",
                "PARA\tFin de la clause sur les garanties");
        // L'ordre du référentiel (chèque avant garantie bancaire) n'est pas celui du modèle ni du document.
        List<String> options = List.of("Caution personnelle et solidaire", "Chèque de banque", "Garantie bancaire");
        java.util.function.Function<String, LectureDao.InfoChamp> champs = c -> switch (c) {
            case "B05-GQ-02" -> new LectureDao.InfoChamp("LISTE_MULTIPLE", "SAISIE", null, options);
            case "B05-GE-03" -> new LectureDao.InfoChamp("LISTE_MULTIPLE", "SAISIE", null, List.of("Garantie bancaire", "Chèque de banque"));
            default -> null;
        };
        List<String> doc = LectureDao.unitesDocument(List.of("Une garantie de soumission doit être fournie dans l'une des formes suivantes :",
                "- Soit une garantie bancaire émise par une banque primaire agréée",
                "- Soit un chèque de banque libellé au nom du Trésor public",
                "La garantie de bonne exécution est remise sous forme bancaire auprès du comptable",
                "Fin de la clause sur les garanties"));
        LectureDao.Resultat r = LectureDao.lire("T", FichierCommande.lireModele(modele), doc, champs);
        assertThat(r.reponsesChamps()).extracting(x -> x.cle() + "=" + x.valeur())
                .contains("B05-GQ-02=Chèque de banque,Garantie bancaire")   // ordre du référentiel, pas du document
                .noneMatch(x -> x.startsWith("B05-GE-03"));   // « contient bancaire » : un fragment
        assertThat(r.cadrage()).extracting(x -> x.cle() + "=" + x.valeur()).contains("garantieSoumission=OUI");

        // Un terme « = » qui dit autre chose : conflit, le champ n'est pas proposé.
        String contraire = modele.replace("CONDITION\tFRAGMENT\u001FB05-GE-03 contient bancaire",
                "CONDITION\tFRAGMENT\u001FB05-GQ-02 = Garantie bancaire");
        LectureDao.Resultat r2 = LectureDao.lire("T", FichierCommande.lireModele(contraire), doc, champs);
        assertThat(r2.conflits()).extracting(LectureDao.Conflit::code).contains("B05-GQ-02");
        assertThat(r2.reponsesChamps()).noneMatch(x -> x.cle().equals("B05-GQ-02"));
    }

    @Test
    @DisplayName("2026-10-01 (front eed6bc4, DAO travaux du MEN) : « MOTS (n) » vaut n ; des pointillés autour d'une unité "
            + "seule sont une case en blanc (usage privé ignoré) ; le point final du modèle est facultatif après du texte fixe")
    void reglesDuDaoDuMen() {
        assertThat(LectureDao.valeurSaisie("CENT VINGT (120)", "NOMBRE", null)).isEqualTo("120");
        assertThat(LectureDao.valeurSaisie("Cinq (05)", "NOMBRE", null)).isEqualTo("05");
        assertThat(LectureDao.valeurSaisie("neuf cent mille Ariary (Ar 9 900 000)", "MONTANT", null)).isEqualTo("9900000");
        assertThat(LectureDao.valeurSaisie("trois (3) ou cinq (5)", "NOMBRE", null)).as("un autre chiffre précède").isNull();
        assertThat(LectureDao.valeurSaisie("CENT VINGT (120)", "TEXTE", null)).isEqualTo("CENT VINGT (120)");
        assertThat(LectureDao.valeurSaisie("........ Jours ….", "NOMBRE", null)).isNull();
        assertThat(LectureDao.valeurSaisie("Jours …. ", "TEXTE", null)).isNull();
        assertThat(LectureDao.valeurSaisie("Jours ouvrables", "TEXTE", null)).isEqualTo("Jours ouvrables");

        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "PARA\tLe délai de validité des offres sera de {{B04-VO-01}} jours.",
                "PARA\tLe soumissionnaire indique {{B04-LR-01}}."));
        List<String> doc = LectureDao.unitesDocument(List.of("Le délai de validité des offres sera de CENT VINGT (120) jours",
                "Le soumissionnaire indique le lieu"));
        LectureDao.Resultat r = LectureDao.lire("T", modele, doc,
                c -> "B04-VO-01".equals(c) ? new LectureDao.InfoChamp("NOMBRE", "SAISIE", null) : null);
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur() + ":" + p.confiance().libelle())
                .contains("B04-VO-01=120:haute")
                .noneMatch(s -> s.startsWith("B04-LR-01"));   // un jeton qui finit le paragraphe garde son point
    }

    @Test
    @DisplayName("implications : les termes « = » d'une conjonction ; rien pour « ou » ; « != », contient, renseigne n'impliquent rien")
    void implications() {
        assertThat(ConditionsModele.implications("attributaires = MULTI et B02-PC-02 = Au fur et à mesure des besoins"))
                .containsExactly(Map.entry("attributaires", "MULTI"), Map.entry("B02-PC-02", "Au fur et à mesure des besoins"));
        assertThat(ConditionsModele.implications("attributaires = MONO ou B05-PM-02 = OUI")).isEmpty();
        assertThat(ConditionsModele.implications("attributaires = MULTI et B05-PM-05 != OUI"))
                .containsExactly(Map.entry("attributaires", "MULTI"));
        assertThat(ConditionsModele.implications("B05-UM-01 renseigne")).isEmpty();
    }

    @Test
    @DisplayName("lecture : texte fixe reconnu (haute si borné ou typé), paragraphe collé coupé (moyenne), jeton seul entre voisins, "
            + "section attestée → réponse de cadrage, deux jetons seuls dans un intervalle → ambigu")
    void lecture() {
        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "CONDITION\tMONO\u001Fattributaires = MONO",
                "CONDITION\tMULTI\u001Fattributaires = MULTI",
                "TITRE\tDOCUMENT",
                "PARA\tDate limite : {{B04-CP-01}} à dix heures.",
                "PARA\tNom du Responsable : {{B04-DS-07}}",
                "PARA\tFonction : {{B04-DS-08}}",
                "SOUS_TITRE\tArticle 2 : Délais d'exécution",
                "PARA\t{{B07-DE-02}}",
                "PARA\t{{B07-DE-03}}",
                "SOUS_TITRE\tArticle 3 : Attribution",
                "PARA\t{{SI:MULTI}}",
                "PARA\tLe contrat-cadre est conclu avec plusieurs titulaires.",
                "PARA\t{{FINSI:MULTI}}",
                "PARA\tMontant : {{B05-MT-01}}"));
        List<String> doc = LectureDao.unitesDocument(List.of("DOCUMENT", "Date limite : 20/11/2026 à dix heures.",
                "Nom du Responsable : RAKOTO Jean Fonction : PRMP", "Article 2 : Délais d’exécution",
                "Trente jours.", "Article 3 : Attribution", "Le contrat-cadre est conclu avec plusieurs titulaires.",
                "Montant : 1 000 Ariary"));
        Map<String, LectureDao.InfoChamp> champs = Map.of("B04-CP-01", new LectureDao.InfoChamp("DATE", "SAISIE", null),
                "B05-MT-01", new LectureDao.InfoChamp("MONTANT", "SAISIE", null));
        LectureDao.Resultat r = LectureDao.lire("T", modele, doc, champs::get);

        // B5 règle 2 (2026-09-29) : « Montant : » n'a que 7 lettres de texte fixe — jamais haute, même typée.
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur() + ":" + p.confiance().libelle())
                .containsExactly("B04-CP-01=2026-11-20:haute", "B04-DS-07=RAKOTO Jean:moyenne", "B04-DS-08=PRMP:moyenne",
                        "B05-MT-01=1000:moyenne");
        assertThat(r.cadrage()).extracting(c -> c.cle() + "=" + c.valeur() + "@" + c.section()).containsExactly("attributaires=MULTI@MULTI");
        assertThat(r.ambigus()).singleElement().satisfies(a -> {
            assertThat(a.candidats()).containsExactly("B07-DE-02", "B07-DE-03");
            assertThat(a.texte()).isEqualTo("Trente jours.");
        });
        assertThat(r.nonTrouves()).containsExactly("B07-DE-02", "B07-DE-03");
    }

    @Test
    @DisplayName("Lot D3 (§B4, 2026-09-29) : un jumeau n'est jamais haut ; la coupe vaut après un texte fixe final (la valeur "
            + "n'avale plus la phrase suivante) ; plusieurs jetons séparés de ponctuation seule : rien n'est proposé")
    void reglesDuLotD3() {
        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "PARA\tDemandes d'éclaircissement : délais de la procédure de consultation",
                "PARA\t{{B04-EP-03}} jours avant la date limite fixée pour la remise des propositions.",
                "PARA\t{{B04-EP-04}} jours avant la date limite fixée pour la remise des propositions.",
                "PARA\tL'objet du marché est {{B02-OB-02}}.",
                "PARA\tLe délai d'exécution est fixé à {{B09-DX-01}} jours calendaires.",
                "PARA\tIntitulé de la consultation et référence du marché",
                "PARA\t{{B02-OB-03}} — {{B02-OB-04}}",
                "PARA\tFin de la section des données particulières"));
        Map<String, LectureDao.InfoChamp> champs = Map.of("B04-EP-03", new LectureDao.InfoChamp("NOMBRE", "SAISIE", null),
                "B04-EP-04", new LectureDao.InfoChamp("NOMBRE", "SAISIE", null),
                "B09-DX-01", new LectureDao.InfoChamp("NOMBRE", "SAISIE", null));
        // Le jumeau B04-EP-04 est absent du document : B04-EP-03 est lu, mais seulement en moyenne (l'ordre, pas le texte,
        // les distingue).
        List<String> doc = LectureDao.unitesDocument(List.of(
                "Demandes d'éclaircissement : délais de la procédure de consultation",
                "15 jours avant la date limite fixée pour la remise des propositions.",
                "L'objet du marché est Étude de faisabilité. Le délai d'exécution est fixé à 90 jours calendaires.",
                "Intitulé de la consultation et référence du marché",
                "AOO-12/2026 — Étude de faisabilité du barrage",
                "Fin de la section des données particulières"));
        LectureDao.Resultat r = LectureDao.lire("T", modele, doc, champs::get);
        assertThat(r.propositions()).noneMatch(p -> p.confiance() == LectureDao.Confiance.HAUTE && p.code().startsWith("B04-EP"));
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur() + ":" + p.confiance().libelle())
                .contains("B02-OB-02=Étude de faisabilité:moyenne", "B09-DX-01=90:haute")
                .noneMatch(s -> s.contains("Le délai d'exécution"));
        assertThat(r.propositions()).extracting(LectureDao.Proposition::code).doesNotContain("B02-OB-03", "B02-OB-04");
    }

    @Test
    @DisplayName("Lot D4 (§B6.3, 2026-09-30) — cas communs avec le front (test_reprojection.mjs) : la typographie d'origine "
            + "est rendue (m³, ’, « », –, ligature, insécable seule gardée), seule l'étendue de la valeur, blancs multiples → "
            + "une espace, longueurs changeantes (… , ﬁ) sans décalage, ligne absente → null")
    void reprojectionCasCommuns() {
        assertThat(reprise("Réservoir semi-enterré de 500 m³", null)).isEqualTo("Réservoir semi-enterré de 500 m³");
        assertThat(reprise("Assurance de l’entreprise", null)).isEqualTo("Assurance de l’entreprise");
        assertThat(reprise("Le « Lot 1 » seul", null)).isEqualTo("Le « Lot 1 » seul");
        assertThat(reprise("Campagne 2026–2027", null)).isEqualTo("Campagne 2026–2027");
        assertThat(reprise("Pièces du dossier : ﬁche A1", null)).isEqualTo("Pièces du dossier : ﬁche A1");
        assertThat(reprise("Montant : 500 Ariary", null)).isEqualTo("Montant : 500 Ariary");
        assertThat(reprise("Objet : l’aménagement de la RN7 — tranche 1.", "l'aménagement de la RN7 - tranche 1"))
                .isEqualTo("l’aménagement de la RN7 — tranche 1");
        assertThat(reprise("A  \t B", null)).isEqualTo("A B");
        assertThat(LectureDao.Carte.de("autre chose").reprojeter("introuvable")).isNull();
        assertThat(reprise("Voir… l’annexe ﬁnale, puis « B »", "l'annexe finale, puis \" B \""))
                .isEqualTo("l’annexe ﬁnale, puis « B »");
    }

    @Test
    @DisplayName("Lot D4 (§B6.3, §B6) — lecture avec les paragraphes d'origine : valeur de texte reprise (m³, —), valeur typée "
            + "jamais reprise, ponctuation de tête retirée (« : » de B02-LT-04) ; sans origines, la valeur reste normalisée")
    void reprojectionALaLecture() {
        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "PARA\tArticle premier - Objet du marché et désignation des travaux",
                "PARA\tL'objet du présent marché est {{B02-OB-02}}.",
                "PARA\t- tranche conditionnelle 1 {{B02-LT-04}}",
                "PARA\tLe montant de la garantie est de {{B05-GQ-03}} Ariary.",
                "PARA\tArticle 2 - Pièces constitutives du marché"));
        List<String> origines = LectureDao.unitesDocumentOrigine(List.of(
                "Article premier – Objet du marché et désignation des travaux",
                "L’objet du présent marché est la construction d’un réservoir de 500 m³ — lot 2.",
                "- tranche conditionnelle 1 : Lot 2 — stockage et distribution",
                "Le montant de la garantie est de 2 000 000 Ariary.",
                "Article 2 – Pièces constitutives du marché"));
        List<String> doc = origines.stream().map(LectureDao::norm).toList();
        Map<String, LectureDao.InfoChamp> champs = Map.of("B05-GQ-03", new LectureDao.InfoChamp("MONTANT", "SAISIE", null));
        LectureDao.Resultat r = LectureDao.lire("T", modele, doc, champs::get, origines);
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur())
                .contains("B02-OB-02=la construction d’un réservoir de 500 m³ — lot 2",
                        "B02-LT-04=Lot 2 — stockage et distribution", "B05-GQ-03=2000000");
        assertThat(LectureDao.lire("T", modele, doc, champs::get).propositions()).extracting(p -> p.code() + "=" + p.valeur())
                .contains("B02-OB-02=la construction d'un réservoir de 500 m3 - lot 2", "B02-LT-04=Lot 2 - stockage et distribution");
    }

    private static String reprise(String origine, String valeurNormalisee) {
        LectureDao.Carte carte = LectureDao.Carte.de(origine);
        assertThat(carte).as("carte reconstruite pour « " + origine + " »").isNotNull();
        return carte.reprojeter(valeurNormalisee == null ? LectureDao.norm(origine) : valeurNormalisee);
    }

    @Test
    @DisplayName("Lot D4 (§B6.2, 2026-09-30) : lire un modèle presque absent du document (le CCAP-T dans un DPAO de travaux) "
            + "reste rapide — le profil du modèle est calculé une fois, pas à chaque paragraphe du document (13,8 s avant)")
    void lectureRapideDUnModeleAbsent() {
        ModelesDao dao = new ModelesDao();
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("QUANTITE_FIXE");
        f.setCategorie("TRAVAUX");
        f.setCadrage(new LinkedHashMap<>(Map.of("typePrix", "UNITAIRES", "tranches", "OUI", "avance", "OUI")));
        f.setValeurs(new HashMap<>(Map.of("B02-LT-03", "Tranche ferme", "B04-VO-01", "120")));
        f.setValeursPpm(new HashMap<>(Map.of("B02-OB-01", "Réhabilitation du réseau d'eau potable")));
        // Le chemin de production : le DPAO-T rendu en .docx, puis extrait comme un fichier importé.
        DocumentLibre dpao = FormulairesCandidat.rendreModele("DPAO", null, f, Map.of(), dao.modele("DPAO-T"), null);
        byte[] docx = new GenerateurDocumentsFiche().generer(dpao).stream().filter(x -> "docx".equals(x.extension()))
                .findFirst().orElseThrow().contenu();
        List<String> doc = ImportDaoService.paragraphes("DPAO.docx", docx);
        assertThat(doc).hasSizeGreaterThan(80);   // 03/10 (V61) : 89 — les 1° et 2° vides de la clause 6.2 ne s'impriment plus
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            for (String s : List.of("DPAO-T", "CCAP-T", "AE-T")) {
                LectureDao.lire(s, dao.modele(s), doc, c -> null);
            }
        });
    }

    @Test
    @DisplayName("Lot D4 (R-a, 2026-09-30) : un texte que le modèle répète (« Non applicable ») ne se cherche qu'à 3 paragraphes "
            + "du curseur — absent, il ne se raccroche plus à celui d'un article plus loin, et l'article sauté est relu")
    void regleTexteRepete() {
        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "PARA\tArticle 7 - Garanties de bonne exécution du marché",
                "PARA\tNon applicable",
                "PARA\tArticle 8 - Assurances des entrepreneurs et des ouvrages",
                "PARA\t{{B09-AC-01}}",
                "PARA\tArticle 9 - Obligation de discrétion et mesures de sécurité",
                "PARA\tNon applicable",
                "PARA\tArticle 10 - Fin des clauses particulières"));
        List<String> doc = LectureDao.unitesDocument(List.of(
                "Article 7 - Garanties de bonne exécution du marché",
                "Une garantie de bonne exécution de 5 % est exigée.",
                "Article 8 - Assurances des entrepreneurs et des ouvrages",
                "Police tous risques chantier souscrite par l'entrepreneur",
                "Article 9 - Obligation de discrétion et mesures de sécurité",
                "Non applicable",
                "Article 10 - Fin des clauses particulières"));
        LectureDao.Resultat r = LectureDao.lire("T", modele, doc, c -> null);
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur())
                .contains("B09-AC-01=Police tous risques chantier souscrite par l'entrepreneur");
        assertThat(r.nonTrouves()).doesNotContain("B09-AC-01");
    }

    @Test
    @DisplayName("Lot D4 (R-b, 2026-09-30) : les jetons seuls d'une section absente (aucun de ses paragraphes distinctifs "
            + "reconnu) ne rendent plus l'intervalle ambigu ; le jeton restant est lu en confiance basse")
    void regleSectionAbsente() {
        String modele = String.join("\n",
                "CONDITION\tALLOTI\u001Falloti = OUI",
                "PARA\tArticle 2 - Objet du marché et description des travaux",
                "PARA\t{{B02-OT-02}}.",
                "PARA\t{{SI:ALLOTI}}",
                "PARA\tLes travaux sont répartis en lots désignés comme suit dans le présent marché :",
                "PARA\t{{B02-LV-02}}",
                "PARA\t{{FINSI:ALLOTI}}",
                "PARA\tArticle 3 - Pièces constitutives du marché");
        List<String> nonAlloti = LectureDao.unitesDocument(List.of(
                "Article 2 - Objet du marché et description des travaux",
                "Construction d'une école primaire à Antsirabe.",
                "Article 3 - Pièces constitutives du marché"));
        LectureDao.Resultat r = LectureDao.lire("T", FichierCommande.lireModele(modele), nonAlloti, c -> null);
        assertThat(r.ambigus()).isEmpty();
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur() + ":" + p.confiance().libelle())
                .containsExactly("B02-OT-02=Construction d'une école primaire à Antsirabe:basse");

        // La section présente (son paragraphe distinctif reconnu) : chaque jeton a son propre intervalle, rien n'est écarté.
        List<String> alloti = LectureDao.unitesDocument(List.of(
                "Article 2 - Objet du marché et description des travaux",
                "Construction de deux écoles primaires.",
                "Les travaux sont répartis en lots désignés comme suit dans le présent marché :",
                "Lot 1 : Antsirabe ; Lot 2 : Betafo",
                "Article 3 - Pièces constitutives du marché"));
        LectureDao.Resultat r2 = LectureDao.lire("T", FichierCommande.lireModele(modele), alloti, c -> null);
        assertThat(r2.propositions()).extracting(LectureDao.Proposition::code).contains("B02-OT-02", "B02-LV-02");
    }

    @Test
    @DisplayName("2026-10-01 (DPAC-CC) : deux rédactions d'une même phrase, la plus contrainte gagne — non alloti : montant "
            + "lu en lettres et chiffres, alloti = NON sans conflit ; alloti : la rédaction par lot, alloti = OUI")
    void varianteLaPlusContrainte() {
        String modele = String.join("\n",
                "CONDITION\tALLOTI\u001Falloti = OUI",
                "CONDITION\tNON-ALLOTI\u001Falloti = NON",
                "CONDITION\tMONTANT-LOTS\u001Falloti = OUI",
                "CONDITION\tMONTANT-UNIQUE\u001Falloti != OUI",
                "PARA\tModalités d'acquisition du dossier de consultation",
                "PARA\t{{SI:MONTANT-LOTS}}",
                "PARA\tLe dossier est retiré moyennant le paiement d'un montant non remboursable de {{B04-DS-05.parLot}} libellé au nom de l'Agent comptable.",
                "PARA\t{{FINSI:MONTANT-LOTS}}",
                "PARA\t{{SI:MONTANT-UNIQUE}}",
                "PARA\tLe dossier est retiré moyennant le paiement d'un montant non remboursable de {{B04-DS-05.lettres}} ({{B04-DS-05}}) libellé au nom de l'Agent comptable.",
                "PARA\t{{FINSI:MONTANT-UNIQUE}}",
                "PARA\t{{SI:NON-ALLOTI}}",
                "PARA\tLe contrat-cadre n'est pas alloti et forme un ensemble unique.",
                "PARA\t{{FINSI:NON-ALLOTI}}",
                "PARA\t{{SI:ALLOTI}}",
                "PARA\tLe contrat-cadre est alloti et chaque lot fait l'objet d'une offre distincte.",
                "PARA\t{{FINSI:ALLOTI}}",
                "PARA\tModification du dossier de consultation");
        java.util.function.Function<String, LectureDao.InfoChamp> champs =
                c -> "B04-DS-05".equals(c) ? new LectureDao.InfoChamp("MONTANT", "FICHE", null) : null;
        List<String> unique = LectureDao.unitesDocument(List.of(
                "Modalités d'acquisition du dossier de consultation",
                "Le dossier est retiré moyennant le paiement d'un montant non remboursable de cinquante mille ariary (50 000 Ariary) libellé au nom de l'Agent comptable.",
                "Le contrat-cadre n'est pas alloti et forme un ensemble unique.",
                "Modification du dossier de consultation"));
        LectureDao.Resultat r = LectureDao.lire("T", FichierCommande.lireModele(modele), unique, champs);
        assertThat(r.conflits()).isEmpty();
        assertThat(r.cadrage()).extracting(c -> c.cle() + "=" + c.valeur()).containsExactly("alloti=NON");
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur()).containsExactly("B04-DS-05=50000");

        List<String> lots = LectureDao.unitesDocument(List.of(
                "Modalités d'acquisition du dossier de consultation",
                "Le dossier est retiré moyennant le paiement d'un montant non remboursable de Lot n° 1 : 100 000 Ariary ; Lot n° 2 : 150 000 Ariary libellé au nom de l'Agent comptable.",
                "Le contrat-cadre est alloti et chaque lot fait l'objet d'une offre distincte.",
                "Modification du dossier de consultation"));
        LectureDao.Resultat r2 = LectureDao.lire("T", FichierCommande.lireModele(modele), lots, champs);
        assertThat(r2.conflits()).isEmpty();
        assertThat(r2.cadrage()).extracting(c -> c.cle() + "=" + c.valeur()).containsExactly("alloti=OUI");
        assertThat(r2.propositions()).extracting(p -> p.code() + "=" + p.valeur())
                .containsExactlyInAnyOrder("B04-DS-05#1=100000", "B04-DS-05#2=150000");
    }

    @Test
    @DisplayName("Lot D2 (B5, 2026-09-29) : « {{CODE}}. » est un jeton seul, jamais un motif ; un libellé présent dans deux "
            + "rédactions n'atteste aucune section ; marqueurs de cellule et de rangée ; {{CODE.parLot}} → CODE#1, CODE#2")
    void reglesDuLotD2() {
        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "CONDITION\tFERME\u001FprixRevisable = NON",
                "CONDITION\tREVISABLE\u001FprixRevisable = OUI",
                "CONDITION\tIMPORTEES\u001Fprovenance = IMPORTEES",
                "TABLE\t2",
                "LIGNE\tClause des instructions aux candidats\u001FDonnées particulières de l'appel",
                "LIGNE\t6.6.3. Caractère ferme ou révisable des prix\u001F{{SI:FERME}}\u001ELes prix sont fermes et non révisables"
                        + " pendant toute la durée\u001E{{FINSI:FERME}}\u001E{{SI:REVISABLE}}\u001ELes prix sont révisables selon la"
                        + " formule du CCAP\u001E{{FINSI:REVISABLE}}",
                "LIGNE\t{{SI:IMPORTEES}}\u001F",
                "LIGNE\t6.7. Monnaie des fournitures importées\u001FDevise des fournitures importées",
                "LIGNE\t{{FINSI:IMPORTEES}}\u001F",
                "LIGNE\t6.8. Garantie de soumission\u001FLe montant de la garantie de soumission est de :\u001E{{B05-GS-03.parLot}}.",
                "LIGNE\t6.9. Devise\u001F{{B05-MO-02}}.",
                "LIGNE\t7.1. Forme des plis\u001FOutre l'original de l'offre, les plis comprennent des copies",
                "FIN_TABLE",
                "PARA\t{{SI:FERME}}",
                "PARA\tFerme",
                "PARA\t{{FINSI:FERME}}",
                "PARA\t{{SI:REVISABLE}}",
                "PARA\tFerme",
                "PARA\t{{FINSI:REVISABLE}}"));
        List<String> doc = LectureDao.unitesDocument(List.of(
                "Clause des instructions aux candidats\tDonnées particulières de l'appel",
                "6.6.3. Caractère ferme ou révisable des prix\tLes prix sont fermes et non révisables pendant toute la durée",
                "6.8. Garantie de soumission\tLe montant de la garantie de soumission est de :\u001ELot n° 1 : 1 000 000 Ariary ; Lot n° 2 : 500 000 Ariary.",
                "6.9. Devise\tEuro.",
                "7.1. Forme des plis\tOutre l'original de l'offre, les plis comprennent des copies",
                "Ferme"));
        Map<String, LectureDao.InfoChamp> champs = Map.of("B05-GS-03", new LectureDao.InfoChamp("MONTANT", "SAISIE", null));
        LectureDao.Resultat r = LectureDao.lire("T", modele, doc, champs::get);

        // la rédaction « fermes » (36 lettres, dans FERME seule) atteste FERME ; « Ferme », présent dans FERME et REVISABLE,
        // n'atteste rien ; la rangée IMPORTEES, absente du document, n'est pas attestée
        assertThat(r.cadrage()).extracting(c -> c.cle() + "=" + c.valeur()).containsExactly("prixRevisable=NON");
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur())
                .contains("B05-GS-03#1=1000000", "B05-GS-03#2=500000", "B05-MO-02=Euro");   // le point du modèle n'est pas la valeur
        // « {{B05-MO-02}}. » : jeton seul, lu entre ses voisins (jamais haute)
        assertThat(r.propositions()).filteredOn(p -> p.code().equals("B05-MO-02")).singleElement()
                .satisfies(p -> assertThat(p.confiance()).isNotEqualTo(LectureDao.Confiance.HAUTE));
        assertThat(r.nonTrouves()).doesNotContain("B05-GS-03");
    }
}
