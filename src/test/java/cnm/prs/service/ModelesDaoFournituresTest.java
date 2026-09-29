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
 * ⚠️ <strong>Lot D2</strong> (demande front du 2026-09-29, §B1, §B3, §B4) — les documents des fournitures rendus depuis leurs
 * documents types officiels (DPAO-F, CCAP-F, AE-F), un modèle pour la quantité fixe et à commande. Marqueurs de cellule et
 * de rangée, {@code {{CODE.parLot}}}, clés {@code typeMarche} et {@code categorie}. Pur : ni base, ni Spring.
 */
class ModelesDaoFournituresTest {

    private final ModelesDao dao = new ModelesDao();

    @Test
    @DisplayName("Quantité fixe, non allotie, nationale, prix unitaires fermes, garantie de soumission, sans avance : les rédactions "
            + "retenues et elles seules")
    void quantiteFixeNationale() {
        FicheMarcheDto f = fiche("QUANTITE_FIXE", Map.of("alloti", "NON", "provenance", "NATIONAL", "typePrix", "UNITAIRES",
                "prixRevisable", "NON", "garantieSoumission", "OUI", "avance", "NON", "groupement", "NON", "variantes", "NON"));
        f.setValeurs(new HashMap<>(Map.of("B05-GS-03", "2000000", "B09-LL-01", "Antananarivo, magasin central",
                "B06-EO-11", "60", "B09-DX-01", "60", "B04-VO-01", "90", "B04-LR-03", "2026-11-20")));
        String dpao = rendre("DPAO", "DPAO-F", null, f);
        assertThat(dpao).contains("Les prix sont fermes et non révisables", "Les variantes ne sont pas prises en considération",
                "La destination finale des Fournitures est :", "Antananarivo, magasin central.", "(2 000 000 Ariary).",
                "Le délai de livraison est fixé à 60 jours")
                .doesNotContain("Les prix sont révisables", "1.2 Marché à commandes", "Lot n° 1 :", "{{", "Groupements");
        // la rangée « 1.2 » et la section « Il n'est pas demandé de garantie » (fausse) ne laissent rien, pas même une ligne vide
        assertThat(dpao).doesNotContain("Il n’est pas demandé de garantie de soumission");

        String ccap = rendre("CCAP", "CCAP-F", null, f);
        assertThat(ccap).contains("CAHIER DES PRESCRIPTIONS SPECIALES", "Le délai de livraison est fixé à 60 jours",
                "Les prix sont fermes et non révisables.")
                .doesNotContain("fixé dans le bon de commande", "{{");
        assertThat(occurrences(ccap, "Modèle de garantie bancaire de restitution d'avance")).isEqualTo(1);   // la table seule

        String ae = rendre("AE", "AE-F", null, f);
        assertThat(ae).contains("ACTE D'ENGAGEMENT (A.E)", "Bordereau des prix pour les fournitures locales")
                .doesNotContain("Bordereau des prix des Fournitures à importer", "lot n° ", "{{");
        assertThat(SelectionDocumentsFiche.titre("CCAP", null, "QUANTITE_FIXE", "FOURNITURES_SERVICES"))
                .isEqualTo("Cahier des prescriptions spéciales");
        assertThat(SelectionDocumentsFiche.titre("CCAP", null, "QUANTITE_FIXE", "TRAVAUX"))
                .isEqualTo("Cahier des clauses administratives particulières");
    }

    @Test
    @DisplayName("À commande, allotie en 2 lots, importée CIP, prix révisables, avance 15 % en garantie bancaire, groupement "
            + "conjoint ou solidaire : rangée « 1.2 Marché à commandes », énumérations par lot, annexe de restitution d'avance, un AE "
            + "par lot")
    void commandeAllotieImportee() {
        FicheMarcheDto f = fiche("A_COMMANDE", Map.of("alloti", "OUI", "provenance", "IMPORTEES", "typePrix", "UNITAIRES",
                "prixRevisable", "OUI", "garantieSoumission", "OUI", "avance", "OUI", "tauxAvance", "15", "groupement", "OUI",
                "formeGroupement", "CONJOINT_OU_SOLIDAIRE"));
        f.setNbLots(2);
        f.setSaisieParLot(true);
        Map<String, String> v = new HashMap<>();
        v.put("B05-GS-03#1", "1000000");
        v.put("B05-GS-03#2", "500000");
        v.put("B09-LL-01#1", "Toamasina");
        v.put("B09-LL-01#2", "Mahajanga");
        v.put("B06-EO-12#1", "30");
        v.put("B06-EO-12#2", "45");
        v.put("B05-CP-01", "CIP");
        v.put("B05-CP-05", "Antananarivo, entrepôt de l'acheteur");
        v.put("B08-AV-04", "Garantie bancaire");
        v.put("B02-AU-04", "24");
        f.setValeurs(v);
        String dpao = rendre("DPAO", "DPAO-F", null, f);
        assertThat(dpao).contains("Les prix sont révisables", "1.2 Marché à commandes", "pour une durée de:", "24 mois.",
                "Lot n° 1 : 1 000 000 Ariary ; Lot n° 2 : 500 000 Ariary.", "Lot n° 1 : Toamasina ; Lot n° 2 : Mahajanga.",
                "Les groupements entre Candidats soumissionnant pour des lots distincts peuvent être conjoint ou solidaire")
                .doesNotContain("Les prix sont fermes et non révisables", "Le délai de livraison est fixé à", "{{");

        String ccap = rendre("CCAP", "CCAP-F", null, f);
        assertThat(ccap).contains("fixé dans le bon de commande", "Lot n° 1 : 30 ; Lot n° 2 : 45 jours")
                .doesNotContain("Le délai de livraison est fixé à", "{{");
        assertThat(occurrences(ccap, "Modèle de garantie bancaire de restitution d'avance")).isEqualTo(2);   // table + annexe

        for (int lot = 1; lot <= 2; lot++) {
            String ae = rendre("AE", "AE-F", lot, f);
            assertThat(ae).as("lot " + lot).contains("— lot n° " + lot, "Bordereau des prix des Fournitures à importer")
                    .doesNotContain("Bordereau des prix pour les fournitures locales", "Lot n° 1 :", "{{");
            // dans un document de lot, {{CODE}} donne déjà la valeur du lot
            assertThat(ae).contains(lot == 1 ? "dépasser30 jours" : "dépasser45 jours");
        }
    }

    @Test
    @DisplayName("Moteur : marqueurs de cellule (section interne à la cellule) et de rangée ; la plage historique A3B-NATURES "
            + "reste une plage ; typeMarche et categorie lisibles par les conditions")
    void moteur() {
        FichierCommande.Modele m = FichierCommande.lireModele(String.join("\n",
                "CONDITION\tQF\u001FtypeMarche = QUANTITE_FIXE",
                "CONDITION\tAC\u001FtypeMarche = A_COMMANDE",
                "CONDITION\tFS\u001Fcategorie = FOURNITURES_SERVICES",
                "TABLE\t2",
                "LIGNE\tClause\u001FDonnées",
                "LIGNE\t{{SI:AC}}\u001F",
                "LIGNE\t1.2 Commande\u001Fà commande",
                "LIGNE\t{{FINSI:AC}}\u001F",
                "LIGNE\t6.6\u001Favant\u001E{{SI:QF}}\u001Equantité fixe\u001E{{FINSI:QF}}\u001E{{SI:AC}}\u001Eà commande\u001E{{FINSI:AC}}\u001Eaprès",
                "LIGNE\t{{SI:FS}}\u001F",
                "LIGNE\tFS\u001Ffournitures",
                "LIGNE\t{{FINSI:FS}}\u001F",
                "FIN_TABLE"));
        FicheMarcheDto f = fiche("QUANTITE_FIXE", Map.of());
        DocumentLibre qf = FormulairesCandidat.rendreModele("DPAO", null, f, Map.of(), m, null);
        DocumentLibre.Tableau t = (DocumentLibre.Tableau) qf.elements().get(0);
        assertThat(t.lignes()).hasSize(3);   // en-tête, 6.6, FS : ni la rangée 1.2 ni les rangées-marqueurs
        assertThat(t.lignes().get(1).get(1)).containsExactly("avant", "quantité fixe", "après");
        f.setTypeMarche("A_COMMANDE");
        DocumentLibre.Tableau ac = (DocumentLibre.Tableau) FormulairesCandidat.rendreModele("DPAO", null, f, Map.of(), m, null)
                .elements().get(0);
        assertThat(ac.lignes()).hasSize(4);
        assertThat(ac.lignes().get(1).get(0)).containsExactly("1.2 Commande");
        assertThat(ac.lignes().get(2).get(1)).containsExactly("avant", "à commande", "après");
        assertThat(ConditionsModele.defauts(m.elements(), m.conditions(), java.util.Set.of())).isEmpty();
    }

    @Test
    @DisplayName("29/09 (champs non imprimés, §B2) — CCAP à commande : avec B09-OM-02, la variation « dans la limite de 15 % » ; "
            + "sans, la phrase s'arrête au Bordereau ; la quantité fixe n'a ni l'une ni l'autre")
    void ccapVariationACommande() {
        String debut = "Le Minimum et le Maximum des quantités susceptibles d’être commandées";
        FicheMarcheDto avec = fiche("A_COMMANDE", Map.of("alloti", "NON"));
        avec.setValeurs(new HashMap<>(Map.of("B09-OM-02", "15")));
        String ccap = rendre("CCAP", "CCAP-F", null, avec);
        assertThat(ccap).contains("dans la limite de 15 % en sus de maximum")
                .doesNotContain("donné en Annexe à l’Acte d’Engagement.\n", "………..%");
        assertThat(occurrences(ccap, debut)).isEqualTo(1);

        String sans = rendre("CCAP", "CCAP-F", null, fiche("A_COMMANDE", Map.of("alloti", "NON")));
        assertThat(sans).contains("précisés dans le Bordereau de Prix donné en Annexe à l’Acte d’Engagement.")
                .doesNotContain("dans la limite de");
        assertThat(occurrences(sans, debut)).isEqualTo(1);

        assertThat(rendre("CCAP", "CCAP-F", null, fiche("QUANTITE_FIXE", Map.of("alloti", "NON")))).doesNotContain(debut);
    }

    @Test
    @DisplayName("29/09 (§B2) — CCAP article 3 : les coordonnées de la PRMP dans son bloc, celui du Fournisseur en blanc ; plus "
            + "de télécopie de la PRMP")
    void ccapArticle3() {
        FicheMarcheDto f = fiche("QUANTITE_FIXE", Map.of("alloti", "NON"));
        f.setValeursPpm(new HashMap<>(Map.of("B01-AC-05", "RAKOTO Jean, PRMP", "B01-AC-02", "Rue de l'Indépendance",
                "B01-AC-06", "prmp@ministere.mg", "B01-AC-01", "Ministère X")));
        String ccap = rendre("CCAP", "CCAP-F", null, f);
        int prmp = ccap.indexOf("les coordonnées de la Personne Responsable des Marchés Publics sont les suivantes");
        int fournisseur = ccap.indexOf("les coordonnées du Fournisseur sont les suivantes");
        int fin = ccap.indexOf("Article 4. - Groupements");
        assertThat(prmp).isPositive().isLessThan(fournisseur);
        String blocPrmp = ccap.substring(prmp, fournisseur);
        String blocFournisseur = ccap.substring(fournisseur, fin);
        assertThat(blocPrmp).contains("RAKOTO Jean, PRMP", "Rue de l'Indépendance", "prmp@ministere.mg").doesNotContain("Télécopie");
        assertThat(blocFournisseur).contains("A l’attention de <insérer le nom>", "Télécopie : <insérer le n° >",
                "Adresse électronique : <insérer l’adresse complète>")
                .doesNotContain("RAKOTO", "Indépendance", "prmp@ministere.mg");
    }

    @Test
    @DisplayName("29/09 (§B3) — DPAO 6.3 : les trois niveaux exigés, chacun sous la fiche qu'il précise")
    void dpaoNiveauxDeQualification() {
        FicheMarcheDto f = fiche("QUANTITE_FIXE", Map.of("alloti", "NON"));
        f.setValeurs(new HashMap<>(Map.of("B03-CQ-02", "Deux véhicules de livraison", "B03-CQ-03",
                "Chiffre d'affaires moyen de 100 000 000 Ariary", "B03-CQ-04", "Deux attestations de bonne fin")));
        String dpao = rendre("DPAO", "DPAO-F", null, f);
        assertThat(dpao).contains("Niveau exigé : Deux véhicules de livraison",
                "Niveau exigé : Chiffre d'affaires moyen de 100 000 000 Ariary", "Pièces exigées : Deux attestations de bonne fin");
        assertThat(dpao.indexOf("capacités techniques")).isLessThan(dpao.indexOf("Niveau exigé : Deux véhicules"));
        assertThat(dpao.indexOf("Niveau exigé : Deux véhicules")).isLessThan(dpao.indexOf("capacité financière"));
        assertThat(dpao.indexOf("capacité financière")).isLessThan(dpao.indexOf("Niveau exigé : Chiffre"));
        assertThat(dpao.indexOf("Niveau exigé : Chiffre")).isLessThan(dpao.indexOf("Pièces exigées"));
    }

    // ------------------------------------------------------------------ outils

    private String rendre(String type, String sigle, Integer lot, FicheMarcheDto f) {
        return FormulairesCandidat.rendreModele(type, lot, f, champs(), dao.modele(sigle), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
    }

    private static int occurrences(String texte, String motif) {
        int n = 0;
        for (int i = texte.indexOf(motif); i >= 0; i = texte.indexOf(motif, i + 1)) {
            n++;
        }
        return n;
    }

    private static FicheMarcheDto fiche(String typeMarche, Map<String, String> cadrage) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche(typeMarche);
        f.setCategorie("FOURNITURES_SERVICES");
        f.setCadrage(new LinkedHashMap<>(cadrage));
        f.setValeurs(new HashMap<>());
        f.setValeursPpm(new HashMap<>(Map.of("B01-AC-13", "Appel d'offres ouvert", "B01-AC-01", "Ministère X",
                "B02-OB-01", "Fournitures informatiques")));
        return f;
    }

    private static Map<String, ChampFicheMarche> champs() {
        Map<String, ChampFicheMarche> m = new HashMap<>();
        for (String[] t : List.of(new String[] {"B05-GS-03", "MONTANT", "oui"}, new String[] {"B09-LL-01", "TEXTE_LONG", "oui"},
                new String[] {"B06-EO-12", "NOMBRE", "oui"}, new String[] {"B06-EO-11", "NOMBRE", "non"},
                new String[] {"B09-DX-01", "NOMBRE", "non"}, new String[] {"B04-VO-01", "NOMBRE", "non"},
                new String[] {"B04-LR-03", "DATE", "non"}, new String[] {"B08-AV-02", "POURCENTAGE", "non"})) {
            ChampFicheMarche c = new ChampFicheMarche();
            c.setCode(t[0]);
            c.setType(t[1]);
            c.setParLot("oui".equals(t[2]));
            c.setActif(true);
            m.put(t[0], c);
        }
        return m;
    }
}
