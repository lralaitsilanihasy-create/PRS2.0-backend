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

    @Test
    @DisplayName("2026-10-02 (recette du DAO du MEN, §B1) — libération à 100 % à la réception provisoire : la rédaction de la "
            + "provisoire, jamais celle de la définitive ; chiffre d'affaires imprimé seulement renseigné ; délai par lot au CCAP-T")
    void recetteDuMen() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "prixRevisable", "NON", "avance", "NON",
                "alloti", "OUI", "nbLots", "2"));
        f.setNbLots(2);
        f.setSaisieParLot(true);
        f.getValeurs().putAll(Map.of("B05-GE-01", "OUI", "B05-GE-03", "Garantie bancaire",
                "B05-GE-04", "Libérée à 100 % à la réception provisoire", "B09-DL-01#1", "cent vingt (120) jours",
                "B09-DL-01#2", "quatre-vingt-dix (90) jours"));
        String ccap = FormulairesCandidat.rendreModele("CCAP", null, f, champsMen(), dao.modele("CCAP-T"), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
        assertThat(ccap).contains("libérée à 100% dans les 30 jours suivant la date de la réception provisoire",
                "Lot n° 1 : cent vingt (120) jours ; Lot n° 2 : quatre-vingt-dix (90) jours")
                .doesNotContain("suivant la date de la réception définitive", "Annexe <numéro>", "{{");
        f.getValeurs().put("B05-GE-04", "Libérée à 100 % à la réception définitive");
        assertThat(FormulairesCandidat.rendreModele("CCAP", null, f, champsMen(), dao.modele("CCAP-T"), null).texte())
                .contains("suivant la date de la réception définitive").doesNotContain("suivant la date de la réception provisoire");

        String sansCa = rendre("DPAO", "DPAO-T", f);
        f.getValeurs().put("B03-QT-07", "500000000");
        String avecCa = rendre("DPAO", "DPAO-T", f);
        assertThat(avecCa.length()).isGreaterThan(sansCa.length());
        assertThat(avecCa).contains("500000000");   // rendre() ne type pas B03-QT-07 : la valeur brute
        assertThat(sansCa).doesNotContain("500000000");
    }

    @Test
    @DisplayName("2026-10-02 (§B3.1) — {{CODE.heure}} « 09 h 30 » ; {{DERIVE.date-prix}} = date limite de remise − 15 jours ; "
            + "{{DERIVE.date-dao}} = date de validation de la version ; sans date ni validation : pointillés")
    void heureEtDerivesDuMen() {
        FichierCommande.Modele m = FichierCommande.lireModele(
                "PARA\tHeure : {{B04-OV-02.heure}} ; prix au {{DERIVE.date-prix}} ; DAO du {{DERIVE.date-dao}}");
        FicheMarcheDto f = fiche(Map.of());
        f.getValeurs().put("B04-OV-02", "2026-11-16T09:30");
        Map<String, ChampFicheMarche> champs = new HashMap<>(champs());
        ChampFicheMarche ov = new ChampFicheMarche();
        ov.setCode("B04-OV-02");
        ov.setType("DATE_HEURE");
        ov.setActif(true);
        champs.put("B04-OV-02", ov);
        assertThat(FormulairesCandidat.rendreModele("DPAO", null, f, champs, m, java.time.LocalDateTime.of(2026, 10, 2, 11, 0)).texte())
                .startsWith("Heure : 09 h 30 ; prix au 01/11/2026 ; DAO du 02/10/2026");
        f.getValeurs().remove("B04-OV-02");
        assertThat(FormulairesCandidat.rendreModele("DPAO", null, f, champs, m, null).texte())
                .startsWith("Heure : ……… ; prix au ……… ; DAO du ………");
    }

    @Test
    @DisplayName("2026-10-02 (§B5.2, modèles rebranchés) — bénéficiaire des chèques, copie(s), heure d'ouverture, date du DAO "
            + "et des prix, plafonds de la régie et des pénalités, délai du décompte, découpage du forfait, actualisation "
            + "sous ACTUALISATION : aucun des blancs de B3 ne reste")
    void blancsDeB3Remplis() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "FORFAITAIRE", "tranches", "NON", "prixRevisable", "NON", "avance", "NON",
                "garantieSoumission", "OUI"));
        f.getValeurs().putAll(Map.of("B05-GQ-02", "Chèque de banque", "B05-GQ-04", "Receveur Général d'Antananarivo",
                "B04-FP-01", "1", "B04-OV-02", "2026-11-16T09:30", "B05-GE-01", "OUI", "B05-GE-03", "Chèque de banque"));
        f.getValeurs().putAll(Map.of("B08-RE-04", "10", "B08-MR-05", "5",
                "B09-PE-02", "un millième", "B09-PE-03", "10", "B09-RP-03", "20", "B05-VR-02", "Indice TP01, Bulletin officiel",
                "B08-RE-02", "OUI"));   // la clause de la régie (12.2) est sous REGIE-OUI
        java.time.LocalDateTime validation = java.time.LocalDateTime.of(2026, 10, 2, 11, 0);
        String dpao = FormulairesCandidat.rendreModele("DPAO", null, f, champsMen(), dao.modele("DPAO-T"), validation).texte();
        assertThat(dpao).contains("libéllé au nom de Receveur Général d'Antananarivo", "1 copie(s)", "Heure : 09 h 30");
        String ae = FormulairesCandidat.rendreModele("AE", null, f, champsMen(), dao.modele("AE-T"), validation).texte();
        assertThat(ae).contains(" du 02/10/2026 et, en particulier", "pour la remise des offres, soit le 01/11/2026");
        // ⚠️ 03/10 (V59, §B1.5) — l'article 16 imprime les séries du DQE ({{BESOIN.series}}), plus B08-MR-06.
        Map<String, String> series = Map.of(FormulairesCandidat.JETON_SERIES, "0 — Installation de chantier : ……… %\n1 — Terrassement : ……… %");
        String ccap = FormulairesCandidat.rendreModele("CCAP", null, f, champsMen(), dao.modele("CCAP-T"), validation, series).texte()
                .replace(' ', ' ').replace(' ', ' ');
        assertThat(ccap).contains("établi à l'ordre de Receveur Général d'Antananarivo.", "atteint 10 % du montant du Marché",
                "au plus tard 5 jours ouvrables", "0 — Installation de chantier : ……… %\n1 — Terrassement : ……… %",
                "dans la limite de 10 % du montant global", "Indice TP01, Bulletin officiel", "est de 20 jours.")
                .doesNotContain("<à préciser>", "<pourcentage>", "< nombre de jours>", "<indiquer la nature des indices", "<n°>",
                        "{{");
        f.getValeurs().remove("B05-VR-02");   // prix fermes, sans actualisation : le paragraphe disparaît
        assertThat(FormulairesCandidat.rendreModele("CCAP", null, f, champsMen(), dao.modele("CCAP-T"), validation, series).texte())
                .doesNotContain("Indice TP01").contains("Les prix sont fermes et non révisables.");
    }

    @Test
    @DisplayName("2026-10-02 (règle 8, DAO routier du MTP) — un sommaire avant le texte : la première accroche y tombe, la "
            + "fenêtre de 60 paragraphes ne rejoint plus le vrai CCAP ; après cinq paragraphes distinctifs manqués, la lecture "
            + "réancre dans tout le reste et retrouve la suite")
    void sommaireAvantLeTexte() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "prixRevisable", "OUI", "avance", "OUI"));
        f.getValeurs().putAll(Map.of("B05-GE-01", "OUI", "B05-GE-03", "Garantie bancaire", "B05-GA-01", "OUI"));
        List<String> lignes = List.of(rendre("CCAP", "CCAP-T", f).split("\n"));
        LectureDao.Resultat propre = LectureDao.lire("CCAP-T", dao.modele("CCAP-T"), LectureDao.unitesDocument(lignes), c -> null);

        List<String> avecSommaire = new java.util.ArrayList<>(lignes.subList(0, 3));   // le sommaire reprend les titres
        for (int i = 1; i <= 80; i++) {
            avecSommaire.add("Instructions aux candidats, clause " + i + " : dispositions générales sans rapport avec le CCAP");
        }
        avecSommaire.addAll(lignes);
        LectureDao.Resultat r = LectureDao.lire("CCAP-T", dao.modele("CCAP-T"), LectureDao.unitesDocument(avecSommaire), c -> null);
        assertThat(propre.reconnues()).isGreaterThan(250);
        // Sans la règle : 2 paragraphes reconnus ; avec : 265 sur 290 (ceux manqués avant le réancrage sont perdus).
        assertThat(r.reconnues()).as("reconnus avec le sommaire").isGreaterThan(propre.reconnues() * 8 / 10);
    }

    // ------------------------------------------------------------------ outils

    /** {@link #champs()} et ⚠️ 2026-10-01 les champs du DAO du MEN : période (NOMBRE), seuil, garantie, délai, liquidité par lot. */
    @Test
    @DisplayName("2026-10-03 (V59, §B2.4, variantes validées par le pilote) — clause 6.3 du DPAO-T : chiffre d'affaires moyen "
            + "des meilleures années dans un domaine, références cumulées, liquidité en pourcentage de l'offre ; sans ces "
            + "seuils, la rédaction d'origine, domaine par défaut compris")
    void seuilsCalculesDuDpao() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "avance", "NON", "garantieSoumission", "NON"));
        Map<String, ChampFicheMarche> champs = new HashMap<>(champsMen());
        for (String[] t : List.of(new String[] {"B03-QT-07", "MONTANT", "non"}, new String[] {"B03-QT-15", "POURCENTAGE", "oui"},
                new String[] {"B03-QT-16", "NOMBRE", "non"}, new String[] {"B03-QT-17", "NOMBRE", "non"},
                new String[] {"B03-QT-18", "TEXTE", "non"}, new String[] {"B03-QT-19", "NOMBRE", "non"},
                new String[] {"B03-QT-20", "MONTANT", "oui"})) {
            ChampFicheMarche c = new ChampFicheMarche();
            c.setCode(t[0]);
            c.setType(t[1]);
            c.setSource("SAISIE");
            c.setParLot("oui".equals(t[2]));
            c.setActif(true);
            champs.put(t[0], c);
        }
        f.getValeurs().putAll(Map.of("B03-QT-07", "5000000000", "B03-QT-16", "3", "B03-QT-17", "5", "B03-QT-18", "travaux routiers",
                "B03-QT-12", "10", "B03-QT-19", "3", "B03-QT-20", "2500000000", "B03-QT-15", "10"));
        String dpao = FormulairesCandidat.rendreModele("DPAO", null, f, champs, dao.modele("DPAO-T"), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
        assertThat(dpao).contains("a) avoir réalisé un chiffre d’affaires annuel moyen, calculé sur les trois (3) meilleures des "
                        + "cinq (5) dernières années, pour des travaux routiers, d’un montant équivalant à 5 000 000 000 Ariary",
                "au cours des dix (10) dernières années, au plus trois (3) marchés de nature et de complexité comparables à "
                        + "celles des Travaux, d’un montant cumulé d’au moins 2 500 000 000 Ariary, et comprenant :",
                "d’un montant minimum égal à 10 % du montant de son offre")
                .doesNotContain("au moins un projet de nature", "un montant minimum de : ");
        // Sans les seuils calculés : la rédaction d'origine, avec le domaine par défaut.
        for (String code : List.of("B03-QT-15", "B03-QT-16", "B03-QT-17", "B03-QT-19", "B03-QT-20")) {
            f.getValeurs().remove(code);
        }
        f.getValeurs().put("B03-QT-18", "travaux de construction");
        f.getValeurs().put("B03-QT-14", "99000000");
        String origine = FormulairesCandidat.rendreModele("DPAO", null, f, champs, dao.modele("DPAO-T"), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
        assertThat(origine).contains("a) avoir réalisé un chiffre d’affaires annuel, pour des travaux de construction, d’un montant",
                "au moins un projet de nature et de complexité comparables", "d’un montant minimum de : ")
                .doesNotContain("annuel moyen", "montant cumulé", "du montant de son offre");
    }

    @Test
    @DisplayName("2026-10-03 (V60, texte validé par le pilote) — clause 6.3 (c) et (e) du DPAO-T : les listes du matériel et "
            + "du personnel, puis le texte de B03-QT-09 / B03-QT-13 s'il est saisi ; texte seul : ni pointillés ni « (e) » vide")
    void materielEtPersonnelDuDpao() {
        FicheMarcheDto f = fiche(Map.of("typePrix", "UNITAIRES", "tranches", "NON", "avance", "NON", "garantieSoumission", "NON"));
        f.getValeurs().put("B03-QT-09", "Propriété ou location justifiée");
        Map<String, ChampFicheMarche> champs = new HashMap<>(champsMen());
        String texteSeul = FormulairesCandidat.rendreModele("DPAO", null, f, champs, dao.modele("DPAO-T"), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
        assertThat(texteSeul).containsSubsequence("des gros matériels et équipements essentiels ci-après :",
                "Propriété ou location justifiée", "(d) proposer un directeur")
                .doesNotContain("(e) proposer le personnel clé suivant", "ci-après : " + FormulairesCandidat.POINTILLES);
        Map<String, String> jetons = MoyensFiche.jetons(
                List.of(new cnm.prs.dto.MaterielExigeDto(null, null, "Camions bennes", "≥ 10 000 kg", 6, 4, false),
                        new cnm.prs.dto.MaterielExigeDto(null, null, "Niveleuse", null, 1, 1, false)),
                List.of(new cnm.prs.dto.PersonnelExigeDto(null, null, "Conducteur de travaux", 1, "Ingénieur BTP ou génie civil", 5,
                        "travaux routiers", "CV et diplôme certifié", false)));
        String listes = FormulairesCandidat.rendreModele("DPAO", null, f, champs, dao.modele("DPAO-T"), null, jetons).texte()
                .replace(' ', ' ').replace(' ', ' ');
        assertThat(listes).containsSubsequence("ci-après :", "- Camions bennes ≥ 10 000 kg : 6, dont au moins 4 en propre",
                "- Niveleuse : 1, en propre", "Propriété ou location justifiée", "(d) proposer un directeur",
                "(e) proposer le personnel clé suivant :", "- Conducteur de travaux (1) : ingénieur BTP ou génie civil ; au moins 5 "
                        + "ans d'expérience en travaux routiers ; justificatifs : CV et diplôme certifié");
    }

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
