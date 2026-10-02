package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.ChampFicheMarche;

/**
 * ⚠️ <strong>Lot D4</strong> (demande front du 2026-09-29, §B1, §B3, §B5) — les DAO de travaux rendus depuis leurs documents
 * types (DPAO-T, AE-T, CCAP-T et ses annexes), et le contrat-cadre de travaux sur le document type commun (DPAC-CC, AE-CC)
 * avec ses rédactions « CCAG Travaux ». Pur.
 */
class ModelesDaoTravauxTest {

    // Le corps de chaque annexe : leurs intitulés figurent aussi, sans condition, dans la liste des pièces du CCAP.
    private static final String GBE_BANCAIRE = "le Titulaire doit remettre au Bénéficiaire une garantie bancaire de bonne exécution";
    private static final String GBE_CAUTION = "en lettres de la garantie de bonne exécution prévue par le Marché";
    private static final String AVANCE_BANCAIRE = "le Titulaire doit remettre au Bénéficiaire une garantie de restitution d’avance";
    private static final String AVANCE_CAUTION = "en lettres de l’avance prévue par le marché";
    private static final String REVISION = "REV = X + (a) T/To";
    private static final String BORDEREAU = "Cadre de bordereau des prix et";

    private final ModelesDao dao = new ModelesDao();

    @Test
    @DisplayName("Prix unitaires sans tranche, ferme, sans avance ni garantie de bonne exécution : rémunération aux prix unitaires, "
            + "départ à l'ordre de service ; ni tranche, ni annexe de garantie, d'avance ou de révision")
    void prixUnitairesSansTranche() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "prixRevisable", "NON", "avance", "NON"));
        f.getValeurs().putAll(Map.of("B09-DT-01", "À la notification de l'ordre de service de commencer les travaux",
                "B05-GE-01", "NON"));
        String ae = rendre("AE", "AE-T", f);
        assertThat(ae).contains("par application des prix unitaires qui résultent du bordereau",
                "à compter du lendemain de la date de notification de l'ordre de service de commencer les travaux",
                "MARCHE A PRIX UNITAIRES")
                .doesNotContain("par application du prix forfaitaire", "La tranche ferme prend effet", "MARCHE A PRIX FORFAITAIRE",
                        "COMPRENANT DES PRIX PARTIELS", "{{");
        String ccap = rendre("CCAP", "CCAP-T", f);
        assertThat(ccap).contains("Aucune garantie d'exécution n'est requise.", BORDEREAU)
                .doesNotContain("Les travaux sont décomposés en tranches", GBE_BANCAIRE, GBE_CAUTION, AVANCE_BANCAIRE, AVANCE_CAUTION,
                        REVISION, "{{");
        assertThat(rendre("DPAO", "DPAO-T", f)).doesNotContain("{{");
    }

    @Test
    @DisplayName("Forfaitaire à deux tranches conditionnelles : les trois tranches au CCAP, l'AE au forfait par tranche et ses "
            + "deux délais d'affermissement (B02-LT-06, B02-LT-07)")
    void forfaitaireDeuxTranches() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "FORFAITAIRE", "tranches", "OUI", "prixRevisable", "NON", "avance", "NON"));
        f.getValeurs().putAll(Map.of("B02-LT-03", "Gros œuvre — 400 000 000 Ariary", "B02-LT-04", "Second œuvre — 150 000 000 Ariary",
                "B02-LT-05", "Voirie — 90 000 000 Ariary", "B02-LT-06", "six mois après la notification",
                "B02-LT-07", "douze mois après la notification"));
        String ccap = rendre("CCAP", "CCAP-T", f);
        assertThat(ccap).contains("Les travaux sont décomposés en tranches", "- tranche ferme Gros œuvre — 400 000 000 Ariary",
                "- tranche conditionnelle 1 Second œuvre — 150 000 000 Ariary", "Voirie — 90 000 000 Ariary");
        String ae = rendre("AE", "AE-T", f);
        assertThat(ae).contains("par application du prix forfaitaire résultant du détail quantitatif et estimatif",
                "La tranche ferme prend effet à compter de sa notification au titulaire.",
                "six mois après la notification; douze mois après la notification", "MARCHE A PRIX FORFAITAIRE")
                .doesNotContain("par application des prix unitaires", "de l'ordre de service de commencer les travaux :", "{{");
        f.getValeurs().remove("B02-LT-05");
        assertThat(rendre("AE", "AE-T", f)).contains("six mois après la notification")
                .doesNotContain("douze mois après la notification");
    }

    @Test
    @DisplayName("Alloti, révisable, avance avec restitution, garantie bancaire de bonne exécution libérée à 50 % : lot nommé, "
            + "révision et son annexe, annexes de bonne exécution bancaire et de restitution d'avance ; pas de caution")
    void allotiRevisableAvanceGarantieBancaire() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "prixRevisable", "OUI", "avance", "OUI",
                "alloti", "OUI", "nbLots", "2"));
        f.getValeurs().putAll(Map.of("B05-GE-01", "OUI", "B05-GE-03", "Garantie bancaire",
                "B05-GE-04", "Libérée à 50 % puis au terme du délai de garantie", "B05-GA-01", "OUI"));
        String ae = FormulairesCandidat.rendreModele("AE", 2, f, champs(), dao.modele("AE-T"), null).texte();
        assertThat(ae).contains("Construction d'un lycée — lot n° 2");
        String ccap = rendre("CCAP", "CCAP-T", f);
        assertThat(ccap).contains("Les prix seront révisés conformément", REVISION,
                "- soit de garantie bancaire conformément au modèle figurant en Annexe", GBE_BANCAIRE, AVANCE_BANCAIRE, AVANCE_CAUTION)
                .doesNotContain(GBE_CAUTION, "Aucune garantie d'exécution n'est requise.");
    }

    @Test
    @DisplayName("Travaux de bâtiment (B09-BT-01) : CPC, TBM et normalisation des bâtiments, assurance décennale ; absents sinon")
    void batiment() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "FORFAITAIRE", "tranches", "NON", "prixRevisable", "NON", "avance", "NON"));
        f.getValeurs().putAll(Map.of("B09-BT-01", "OUI", "B09-AC-03", "Police décennale souscrite auprès d'un assureur agréé",
                "B05-GE-01", "OUI", "B05-GE-03", "Caution personnelle et solidaire"));
        String ccap = rendre("CCAP", "CCAP-T", f);
        assertThat(ccap).contains("Cahier des prescriptions communes (CPC)", "dit TBM",
                "Police décennale souscrite auprès d'un assureur agréé", GBE_CAUTION)
                .doesNotContain(GBE_BANCAIRE, AVANCE_BANCAIRE);
        f.getValeurs().put("B09-BT-01", "NON");
        assertThat(rendre("CCAP", "CCAP-T", f)).doesNotContain("Cahier des prescriptions communes (CPC)", "dit TBM",
                "Police décennale souscrite");
    }

    @Test
    @DisplayName("§B6.1 (2026-09-30) — fin de validité de l'offre dans l'AE-T : date de remise lue sur B04-OV-02 (date-heure, "
            + "sa date) + B04-VO-01 jours ; sans date de remise, les pointillés")
    void finDeValiditeDesTravaux() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "prixRevisable", "NON", "avance", "NON"));
        f.getValeurs().putAll(Map.of("B04-OV-02", "2026-11-16T10:00", "B04-VO-01", "120"));
        assertThat(rendre("AE", "AE-T", f)).contains("des offres fixée dans les Données Particulières de l'Appel d'Offres, "
                + "jusqu'au 16/03/2027.");
        f.getValeurs().remove("B04-OV-02");
        assertThat(rendre("AE", "AE-T", f)).contains("jusqu'au " + FormulairesCandidat.POINTILLES + ".");
    }

    @Test
    @DisplayName("Prix mixtes (§B2.1, option MIXTE de typePrix) : l'annexe 1 des prix partiels et forfaitaires, seule")
    void prixMixtes() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "MIXTE", "tranches", "NON", "prixRevisable", "NON", "avance", "NON"));
        assertThat(rendre("AE", "AE-T", f)).contains("MARCHE A PRIX UNITAIRES COMPRENANT DES PRIX PARTIELS ET FORFAITAIRES")
                .doesNotContain("MARCHE A PRIX FORFAITAIRE", "CADRE DU BORDEREAU DES PRIX ET\nCADRE DU DETAIL QUANTITATIF ET "
                        + "ESTIMATIF\n(l'Entrepreneur");
    }

    @Test
    @DisplayName("Contrat-cadre de travaux (§B3) : les sept rédactions « CCAG Travaux » à la place des « CCAG Fournitures » ; un "
            + "reflet des fournitures cité par le modèle (nombre de lots, taux d'avance) se lit sur le cadrage")
    void contratCadreTravaux() {
        FicheMarcheDto f = fiche(Map.of("attributaires", "MONO", "alloti", "OUI", "nbLots", "3", "avance", "OUI",
                "tauxAvance", "10"));
        f.setTypeMarche("CONTRAT_CADRE");
        f.getValeurs().putAll(Map.of("B07-PE-01", "Fixées dans le contrat-cadre", "B07-PE-02",
                "Conformément à l'article 12 du CCAG", "B09-VA-01", "Dispositions du CCAG applicables", "B09-GP-01", "OUI",
                "B09-GP-05", "OUI"));
        String dpac = rendre("DPAC", "DPAC-CC", f);
        assertThat(dpac).contains("REGLEMENT DE LA CONSULTATION(1) (contrat-cadre)")
                .doesNotContain("(contrat-cadre marché de fournitures et services)");
        String ae = rendre("AE", "AE-CC", f);
        assertThat(ae).contains("applicables aux marchés de travaux.", "l’article 20 du CCAG Travaux",
                "l’article 2.4 du CCAG Travaux", "articles 41, 42 et 43 du CCAG Travaux", "l’article 44 du CCAG Travaux",
                "articles 46 du CCAG Travaux", "réparties en 3 lots", "fixé à 10 % du montant TTC")
                .doesNotContain("CCAG Fournitures", "marchés publics de Fournitures courantes");

        f.setCategorie("FOURNITURES_SERVICES");
        assertThat(rendre("AE", "AE-CC", f)).contains("l’article 12 du CCAG Fournitures et Services", "l’article 5.2 du CCAG Fournitures")
                .doesNotContain("CCAG Travaux");
        assertThat(rendre("DPAC", "DPAC-CC", f)).contains("(contrat-cadre marché de fournitures et services)");
    }

    @Test
    @DisplayName("2026-10-01 (DAO de travaux du MEN, §B1, §B2.3) — alloti en deux lots, garantie : période de référence en "
            + "lettres (« cinq (5) »), pièces administratives saisies, seuil, garantie, délai et liquidité par lot, personnel clé ; "
            + "sans personnel clé ni liquidité, les paragraphes (e) et (f) ne s'impriment pas")
    void daoDuMen() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "alloti", "OUI", "nbLots", "2",
                "garantieSoumission", "OUI"));
        f.setNbLots(2);
        f.setSaisieParLot(true);
        f.getValeurs().putAll(Map.of("B03-QT-12", "5", "B03-CQ-01", "une photocopie certifiée de la carte statistique\nun certificat de non faillite",
                "B03-QT-08#1", "247 500 000 Ariary", "B03-QT-08#2", "180 000 000 Ariary", "B05-GQ-03#1", "9900000",
                "B05-GQ-03#2", "7200000", "B09-DL-01#1", "six mois", "B09-DL-01#2", "cinq mois"));
        f.getValeurs().putAll(Map.of("B03-QT-13", "Conducteur de travaux : ingénieur BTP, 3 ans", "B03-QT-14#1", "99000000",
                "B03-QT-14#2", "72000000"));
        // §B4.1 — limite de lots (lots divisibles), offres anormales, formes de garantie à choix multiples (« contient »).
        f.getValeurs().putAll(Map.of("B02-LT-02", "Divisible", "B02-AU-07", "2", "B06-EO-07", "Moyenne des offres + 20 %",
                "B05-GQ-02", "Garantie bancaire,Chèque de banque"));
        String dpao = FormulairesCandidat.rendreModele("DPAO", null, f, champsMen(), dao.modele("DPAO-T"), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
        assertThat(dpao).contains("réalisés au cours des cinq dernières années", "au cours des cinq (5) dernières années",
                "une photocopie certifiée de la carte statistique\nun certificat de non faillite",
                "Lot n° 1 : 247 500 000 Ariary ; Lot n° 2 : 180 000 000 Ariary",
                "Lot n° 1 : 9 900 000 Ariary ; Lot n° 2 : 7 200 000 Ariary",
                "ne doit pas dépasser Lot n° 1 : six mois ; Lot n° 2 : cinq mois à compter",
                "(e) proposer le personnel clé suivant : Conducteur de travaux : ingénieur BTP, 3 ans",
                "d’un montant minimum de : Lot n° 1 : 99 000 000 Ariary ; Lot n° 2 : 72 000 000 Ariary",
                "mais ne peut prétendre qu’à deux (2) lots", "9.4.5. Offres anormalement basses ou anormalement hautes	Moyenne des offres + 20 %",
                "- Soit une garantie bancaire", "- Soit un chèque de banque")
                .doesNotContain("- Soit une caution personnelle et solidaire")
                .doesNotContain("{{", "trois (5)", "carte professionnelle de l'année", "< par exemple >");

        f.getValeurs().remove("B03-QT-13");
        f.getValeurs().remove("B03-QT-14#1");
        f.getValeurs().remove("B03-QT-14#2");
        f.getCadrage().put("alloti", "NON");
        f.setNbLots(null);
        f.setSaisieParLot(false);
        f.getValeurs().put("B05-GQ-03", "5000000");
        String unique = FormulairesCandidat.rendreModele("DPAO", null, f, champsMen(), dao.modele("DPAO-T"), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
        assertThat(unique).contains("cinq millions ariary (5 000 000 Ariary).")
                .doesNotContain("(e) proposer le personnel clé", "(f) justifier d’une liquidité", "mais ne peut prétendre");   // non alloti
    }

    @Test
    @DisplayName("2026-10-02 (demande « trous et distinctif », règle 7) — CCAP-T avec avance, sans garantie de bonne exécution : "
            + "« ATTENDU QUE » et « <nom du Titulaire> » de l'annexe de restitution d'avance fusionnés redonnent l'en-tête de "
            + "l'annexe bancaire de bonne exécution, mais un trou ne rend plus un paragraphe distinctif : B05-GE-01 n'est pas déduit")
    void fusionAttenduQueNAttestePasLaBonneExecution() {
        // Prix révisables : l'annexe de révision précède immédiatement les annexes de garantie, la lecture y est à pied d'œuvre
        // (avec des prix fermes, l'annexe de bonne exécution se cherche trop loin pour que la fusion soit essayée).
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "prixRevisable", "OUI", "avance", "OUI"));
        f.getValeurs().putAll(Map.of("B05-GE-01", "NON", "B05-GA-01", "OUI"));
        List<String> lignes = new java.util.ArrayList<>(List.of(rendre("CCAP", "CCAP-T", f).split("\n")));
        int i = lignes.indexOf("ATTENDU QUE");
        assertThat(i).as("l'en-tête de l'annexe de restitution d'avance").isPositive();
        assertThat(lignes.get(i + 1)).isEqualTo("<nom du Titulaire>");
        lignes.set(i, "ATTENDU QUE <nom du Titulaire>");   // une autre mise en page : les deux paragraphes réunis
        lignes.remove(i + 1);

        LectureDao.Resultat r = LectureDao.lire("CCAP-T", dao.modele("CCAP-T"), LectureDao.unitesDocument(lignes), c -> null);
        assertThat(r.reponsesChamps()).extracting(LectureDao.Reponse::cle).doesNotContain("B05-GE-01");
        assertThat(r.cadrage()).extracting(x -> x.cle() + "=" + x.valeur()).contains("avance=OUI");
    }

    // ------------------------------------------------------------------ outils

    /** {@link #champs()} et ⚠️ 2026-10-01 les champs du DAO du MEN : période (NOMBRE), seuil, garantie, délai, liquidité par lot. */
    private static Map<String, ChampFicheMarche> champsMen() {
        Map<String, ChampFicheMarche> m = new HashMap<>(champs());
        for (String[] t : List.of(new String[] {"B03-QT-12", "NOMBRE", "non"}, new String[] {"B03-QT-08", "TEXTE_LONG", "oui"},
                new String[] {"B05-GQ-03", "MONTANT", "oui"}, new String[] {"B09-DL-01", "TEXTE_LONG", "oui"},
                new String[] {"B03-QT-14", "MONTANT", "oui"}, new String[] {"B03-QT-13", "TEXTE_LONG", "non"},
                new String[] {"B02-AU-07", "NOMBRE", "non"}, new String[] {"B05-GQ-02", "LISTE_MULTIPLE", "non"})) {
            ChampFicheMarche c = new ChampFicheMarche();
            c.setCode(t[0]);
            c.setType(t[1]);
            c.setSource("SAISIE");
            c.setParLot("oui".equals(t[2]));
            c.setActif(true);
            m.put(t[0], c);
        }
        return m;
    }

    private String rendre(String type, String sigle, FicheMarcheDto f) {
        return FormulairesCandidat.rendreModele(type, null, f, champs(), dao.modele(sigle), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
    }

    private static FicheMarcheDto fiche(Map<String, String> cadrage) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("QUANTITE_FIXE");
        f.setCategorie("TRAVAUX");
        f.setCadrage(new LinkedHashMap<>(cadrage));
        f.setValeurs(new HashMap<>());
        f.setValeursPpm(new HashMap<>(Map.of("B01-AC-01", "Ministère X", "B02-OB-01", "Construction d'un lycée")));
        return f;
    }

    /** Les champs dont le type ou le reflet compte au rendu ; les reflets des fournitures cités par le contrat-cadre. */
    private static Map<String, ChampFicheMarche> champs() {
        Map<String, ChampFicheMarche> m = new HashMap<>();
        for (String[] t : List.of(new String[] {"B02-LV-05", "NOMBRE", "nbLots"}, new String[] {"B08-AV-02", "POURCENTAGE", "tauxAvance"},
                new String[] {"B05-GE-05", "POURCENTAGE", null})) {
            ChampFicheMarche c = new ChampFicheMarche();
            c.setCode(t[0]);
            c.setType(t[1]);
            c.setSource(t[2] == null ? "SAISIE" : "CADRAGE");
            c.setCleCadrage(t[2]);
            c.setActif(true);
            m.put(t[0], c);
        }
        return m;
    }
}
