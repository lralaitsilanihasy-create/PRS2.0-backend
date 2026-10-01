package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.ChampFicheMarche;

/**
 * ⚠️ <strong>Lot D — le DAO complet</strong> (demande front du 2026-09-28, §B1, §B2, §B5) : la grammaire des conditions
 * déclarées, le chargement des deux modèles du contrat-cadre (14 et 53 conditions, aucune non déclarée), le rendu de deux
 * fiches types, et le <strong>rendu brut</strong> écrit dans {@code target/modeles-dao/} pour le comparateur du front
 * ({@code node verifier.mjs DPAC-CC AE-CC --dossier=…} depuis {@code scripts/modeles-dao}). Pur : ni base, ni Spring.
 */
class ModelesDaoTest {

    private static Map<String, String> v(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static boolean vraie(String expression, Map<String, String> valeurs) {
        return ConditionsModele.vraie(expression, valeurs::get);
    }

    @Test
    @DisplayName("Grammaire : =, !=, contient, renseigne, vide ; et avant ou ; une valeur peut contenir « et » ; casse, blancs "
            + "et apostrophes confondus ; = faux et != vrai sur une valeur absente")
    void grammaire() {
        Map<String, String> f = v("attributaires", "MULTI", "B02-PC-02", "Au fur et à mesure des besoins",
                "B01-AC-13", "Appel d'offres ouvert", "B07-MA-04", "Titulaires des lots correspondant à l’objet du marché");
        assertThat(vraie("attributaires = MULTI", f)).isTrue();
        assertThat(vraie("attributaires = multi", f)).isTrue();
        assertThat(vraie("attributaires != MONO", f)).isTrue();
        assertThat(vraie("attributaires = MULTI et B02-PC-02 = Au fur et à mesure des besoins", f)).isTrue();
        assertThat(vraie("B02-PC-02 = Au  fur et à mesure des besoins", f)).isTrue();
        assertThat(vraie("B07-MA-04 = Titulaires des lots correspondant à l'objet du marché", f)).isTrue();   // ’ = '
        assertThat(vraie("B01-AC-13 contient ouvert", f)).isTrue();
        assertThat(vraie("B01-AC-13 contient restreint", f)).isFalse();
        assertThat(vraie("B05-UM-01 vide", f)).isTrue();
        assertThat(vraie("B05-UM-01 renseigne", f)).isFalse();
        assertThat(vraie("B05-PM-05 = OUI", f)).isFalse();
        assertThat(vraie("B05-PM-05 != OUI", f)).isTrue();
        // et prioritaire : (MONO et X) ou (MULTI et contient ouvert)
        assertThat(vraie("attributaires = MONO et B05-UM-01 vide ou attributaires = MULTI et B01-AC-13 contient ouvert", f)).isTrue();
        assertThat(vraie("attributaires = MONO ou B05-UM-01 renseigne et attributaires = MULTI", f)).isFalse();
        assertThat(ConditionsModele.lisible("attributaires = MONO ou B05-PM-02 = OUI")).isTrue();
        assertThat(ConditionsModele.lisible("attributaires MONO")).isFalse();
        assertThat(ConditionsModele.lisible("")).isFalse();
    }

    @Test
    @DisplayName("Chargement : DPAC-CC déclare 14 conditions, AE-CC 53, aucune section non déclarée ; une condition non déclarée, "
            + "illisible ou une section mal refermée fait échouer le chargement, nommément")
    void chargement() {
        ModelesDao dao = new ModelesDao();
        assertThat(dao.modele("DPAC-CC").conditions()).hasSize(18);   // D4 : + CCAG-FOURNITURES, CCAG-TRAVAUX ; 01/10 : + MONTANT-LOTS/-UNIQUE
        assertThat(dao.modele("AE-CC").conditions()).hasSize(55);
        // ⚠️ Lot D2 (2026-09-29) — les trois documents des fournitures, un modèle pour la quantité fixe et à commande.
        assertThat(dao.modele("DPAO-F").conditions()).hasSize(42);
        assertThat(dao.modele("AE-F").conditions()).hasSize(22);
        assertThat(dao.modele("CCAP-F").conditions()).hasSize(67);   // 29/09 : + VARIATION-COMMANDE, SANS-VARIATION-COMMANDE
        // ⚠️ Lot D3 (2026-09-29) — les trois documents des prestations intellectuelles.
        assertThat(dao.modele("DPIC-PI").conditions()).hasSize(33);
        assertThat(dao.modele("AE-PI").conditions()).hasSize(17);
        assertThat(dao.modele("CPS-PI").conditions()).hasSize(30);
        // ⚠️ Lot D4 (2026-09-29) — les trois documents des travaux.
        assertThat(dao.modele("DPAO-T").conditions()).hasSize(34);
        assertThat(dao.modele("AE-T").conditions()).hasSize(30);
        assertThat(dao.modele("CCAP-T").conditions()).hasSize(71);   // 30/09 : + PLANS (B04-CD-03)
        // ⚠️ 2026-09-30 — les deux avis spécifiques (fournitures, travaux), imprimés à la demande, hors des couvertures.
        assertThat(dao.modele("AVIS-F").conditions()).hasSize(22);   // 01/10 : aligné sur un avis réel (§B7)
        assertThat(dao.modele("AVIS-T").conditions()).hasSize(24);
        assertThat(ModelesDao.COUVERTURES).noneMatch(c -> c.sigle().startsWith("AVIS"));
        assertThat(dao.modeles()).hasSize(13);
        assertThatThrownBy(() -> ModelesDao.charger("/modeles/dao/X.txt", "CONDITION\tA\u001Fattributaires = MONO\n"
                + "PARA\t{{SI:A}}\nPARA\t{{SI:B}}\nPARA\ttexte\nPARA\t{{FINSI:B}}\nPARA\t{{FINSI:A}}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("/modeles/dao/X.txt")
                .hasMessageContaining("condition B utilisée sans être déclarée");
        assertThatThrownBy(() -> ModelesDao.charger("/x", "CONDITION\tA\u001Fattributaires MONO\nPARA\t{{SI:A}}\nPARA\t{{FINSI:A}}"))
                .hasMessageContaining("condition A illisible");
        assertThatThrownBy(() -> ModelesDao.charger("/x", "CONDITION\tA\u001Fattributaires = MONO\nPARA\t{{SI:A}}\nPARA\ttexte"))
                .hasMessageContaining("non refermée");
        assertThatThrownBy(() -> ModelesDao.charger("/x", "CONDITION\tA\u001Fx = 1\nCONDITION\tA\u001Fx = 2"))
                .hasMessageContaining("déclarée deux fois");
        // Les formulaires du candidat gardent leurs trois noms historiques, sans déclaration.
        assertThat(ConditionsModele.defauts(FichierCommande.lire("PARA\t{{SI:A1B}}\nPARA\tx\nPARA\t{{FINSI:A1B}}"), Map.of(),
                FormulairesCandidat.SECTIONS_HISTORIQUES)).isEmpty();
        assertThat(new ModelesCandidat().modeles()).hasSize(6);
    }

    @Test
    @DisplayName("Sections imbriquées : une section fausse omet ses sections internes ; une vraie les évalue")
    void imbrication() {
        FichierCommande.Modele m = FichierCommande.lireModele("CONDITION\tMONO\u001Fattributaires = MONO\n"
                + "CONDITION\tPAPIER\u001FmodeRemise = PAPIER\n"
                + "PARA\t{{SI:MONO}}\nPARA\tmono\nPARA\t{{SI:PAPIER}}\nPARA\tmono papier\nPARA\t{{FINSI:PAPIER}}\nPARA\t{{FINSI:MONO}}\n"
                + "PARA\t{{SI:PAPIER}}\nPARA\tpapier\nPARA\t{{FINSI:PAPIER}}");
        FicheMarcheDto fiche = fiche(Map.of("attributaires", "MULTI"));
        assertThat(FormulairesCandidat.rendreModele("X", null, fiche, Map.of(), m, null).texte()).isEqualTo("papier");
        fiche.getCadrage().put("attributaires", "MONO");
        assertThat(FormulairesCandidat.rendreModele("X", null, fiche, Map.of(), m, null).texte())
                .isEqualTo("mono\nmono papier\npapier");   // modeRemise absent = PAPIER
    }

    @Test
    @DisplayName("Rendu brut des deux modèles (jetons et marqueurs non substitués) écrit dans target/modeles-dao : la recette "
            + "du lot par le comparateur du front ; les déclarations CONDITION ne s'impriment pas")
    void renduBrut() throws Exception {
        ModelesDao dao = new ModelesDao();
        GenerateurDocumentsFiche generateur = new GenerateurDocumentsFiche();
        Path dossier = Path.of("target", "modeles-dao");
        Files.createDirectories(dossier);
        for (Map.Entry<String, FichierCommande.Modele> e : dao.modeles().entrySet()) {
            DocumentLibre brut = FormulairesCandidat.brut(e.getKey(), e.getValue().elements());
            assertThat(brut.texte()).contains("{{SI:");
            // les déclarations CONDITION ne s'impriment pas (le mot peut, lui, figurer dans le texte du document type)
            e.getValue().conditions().values().forEach(x -> assertThat(brut.texte()).as(e.getKey()).doesNotContain(x));
            List<GenerateurDocumentsFiche.Fichier> fichiers = generateur.generer(brut);
            for (GenerateurDocumentsFiche.Fichier f : fichiers) {
                Files.write(dossier.resolve(e.getKey() + "." + f.extension()), f.contenu());
            }
        }
        assertThat(dossier.resolve("DPAC-CC.docx")).exists();
        assertThat(dossier.resolve("AE-CC.docx")).exists();
        assertThat(dossier.resolve("DPAO-F.docx")).exists();
        assertThat(dossier.resolve("AE-F.docx")).exists();
        assertThat(dossier.resolve("CCAP-F.docx")).exists();
        assertThat(dossier.resolve("DPIC-PI.docx")).exists();
        assertThat(dossier.resolve("AE-PI.docx")).exists();
        assertThat(dossier.resolve("CPS-PI.docx")).exists();
    }

    @Test
    @DisplayName("Mono-attributaire, non alloti, papier, non reconductible : les rédactions retenues et elles seules")
    void renduMonoPapier() {
        ModelesDao dao = new ModelesDao();
        FicheMarcheDto fiche = fiche(Map.of("attributaires", "MONO", "alloti", "NON", "typePrix", "UNITAIRES", "avance", "NON",
                "groupement", "NON"));
        fiche.setValeurs(new HashMap<>(v("B02-DC-03", "NON", "B02-PC-02", "Au fur et à mesure des besoins",
                "B07-FS-01", "Marchés uniques non fractionnés", "B07-DE-01", "Fixés dans les marchés subséquents",
                "B07-PE-01", "Fixées dans les marchés subséquents", "B05-PM-01", "Prix unitaires", "B09-GP-01", "NON",
                "B10-RS-02", "3", "B04-CP-02", "2026-04-10T10:00", "B04-VO-01", "90", "B02-OB-01", "Fournitures de bureau",
                "B04-DS-05", "50000")));
        fiche.setValeursPpm(new HashMap<>(v("B01-AC-13", "Appel d'offres ouvert", "B01-AC-01", "Ministère X")));
        // 01/10 (front 547e48b) : le prix du DAO se verse sur le compte de l'ARMP, rendu par l'appelant ({{PARAM.compte-dao}})
        String dpac = FormulairesCandidat.rendreModele("DPAC", null, fiche, champs(), dao.modele("DPAC-CC"), null,
                Map.of(FormulairesCandidat.JETON_COMPTE_DAO, "BNI Madagascar, compte n° 123 au nom de ARMP")).texte();
        assertThat(dpac).contains("à un seul titulaire (mono-attributaire)", "remis en compétition au fur et à mesure des besoins",
                "Le contrat-cadre est conclu à prix unitaires.", "La période de validité du contrat n’est pas reconductible.",
                "Sans objet.", "La transmission de dossiers par voie électronique n’est pas admise",
                "DATE ET HEURE LIMITES DE REMISE DES OFFRES : 10/04/2026 10:00", "exprimées en Ariary. Si",
                "montant non remboursable de cinquante mille ariary (50 000 Ariary) à verser sur le compte bancaire de l’ARMP : "
                        + "BNI Madagascar, compte n° 123 au nom de ARMP.")
                .doesNotContain("multi attributaire", "selon le calendrier fixé ci-après", "{{", "CLAUSE À FOURNIR",
                        "Cette période de validité peut être reconduite");
        String ae = FormulairesCandidat.rendreModele("AE", null, fiche, champs(), dao.modele("AE-CC"), null).texte();
        assertThat(ae).contains("CONTRAT-CADRE Valant acte d’engagement et CCAP UNIQUE",
                "Le contrat-cadre n’est pas alloti. Il est mono-attributaire.",
                "Appel d’offres ouvert, en application des articles 35 à 37", "Marchés uniques non fractionnés.",
                "Le contrat-cadre n’est pas reconductible.", "– 3 mois avant la date anniversaire",
                "Aucune garantie contractuelle particulière ne sera demandée.",
                "Les délais d’exécution seront fixés dans les marchés subséquents",
                "Les pénalités de retard seront fixées dans les marchés conclus sur la base du contrat-cadre.",
                "détaillé dans le bordereau de prix unitaires joint au présent contrat-cadre",
                "notifiée dans un délai de 90 jours calendaires", "jusqu'au 09/07/2026")   // 10/04 + 90 jours
                .doesNotContain("LOT n°", "multi-attributaire.", "Appel d’offres restreint", "OU\n", "{{", "10.1 – Versement");
    }

    @Test
    @DisplayName("Multi-attributaire, alloti en 2 lots, électronique, reconductible : un AE par lot « LOT n° 1 » / « LOT n° 2 », "
            + "la clause du juriste, les rédactions multi")
    void renduMultiAllotiElectronique() {
        ModelesDao dao = new ModelesDao();
        FicheMarcheDto fiche = fiche(Map.of("attributaires", "MULTI", "alloti", "OUI", "typePrix", "UNITAIRES", "avance", "OUI",
                "tauxAvance", "15", "modeRemise", "ELECTRONIQUE", "groupement", "OUI"));
        fiche.setNbLots(2);
        fiche.setSaisieParLot(true);
        fiche.setValeurs(new HashMap<>(v("B02-DC-03", "OUI", "B07-DU-04", "2", "B07-DU-05", "4", "B07-DU-07", "3",
                "B02-PC-02", "Selon le calendrier fixé ci-après", "B02-PC-03", "chaque trimestre",
                "B07-MA-04", "Titulaires de tous les lots", "B05-PM-02", "OUI", "B05-PM-05", "OUI", "B05-PM-04", "5",
                "B09-GP-01", "OUI", "B09-GP-04", "À partir de la date de mise en service", "B09-GP-03", "12",
                "B04-SE-02", "https://depot.cnm.mg", "B04-DS-05#1", "100000", "B04-DS-05#2", "150000")));
        fiche.setValeursPpm(new HashMap<>(v("B01-AC-13", "Appel d'offres restreint")));
        fiche.setValeursCadrage(new HashMap<>(v("B08-AV-02", "15", "B02-LV-05", "2")));
        String dpac = FormulairesCandidat.rendreModele("DPAC", null, fiche, champs(), dao.modele("DPAC-CC"), null).texte();
        assertThat(dpac).contains("à plusieurs titulaires (multi attributaire)", "selon le calendrier fixé ci-après",
                "chaque trimestre", "Cette période de validité peut être reconduite",
                "La transmission par voie électronique est admise dans les conditions suivantes",
                "[[CLAUSE À FOURNIR PAR LE JURISTE : conditions de la transmission électronique — plateforme (https://depot.cnm.mg)",
                // 01/10 : le montant du DAO par lot (DPAC-CC du front, 40f2b4a)
                "montant non remboursable de Lot n° 1 : 100 000 Ariary ; Lot n° 2 : 150 000 Ariary à verser sur le compte bancaire de "
                        + "l’ARMP : " + FormulairesCandidat.POINTILLES + ".")   // compte non réglé : pointillés
                .doesNotContain("n’est pas admise", "mono-attributaire", "Sans objet.", "{{");
        for (int lot = 1; lot <= 2; lot++) {
            String ae = FormulairesCandidat.rendreModele("AE", lot, fiche, champs(), dao.modele("AE-CC"), null).texte();
            assertThat(ae).as("lot " + lot).contains("CONTRAT-CADRE Valant acte d’engagement et CCAP LOT n°" + lot,
                    "Le contrat-cadre est alloti et multi-attributaire.", "Le présent contrat-cadre est passé pour le lot n° " + lot + ".",
                    "réparties en 2 lots", "Appel d’offres restreint", "des titulaires de tous les lots",
                    "selon la périodicité suivante : chaque trimestre", "reconductible 2 fois", "excéder 4 ans",
                    "catalogue joint au présent accord", "remise sur les prix « catalogue »", "fixé à 15 % du montant TTC",   // AE-CC corrigé (front e0be911) : .chiffres
                    "12 mois à partir de la date de mise en service", "Le groupement d’entrepreneurs solidaire/ conjoint")
                    .contains("notifiée dans un délai")   // PRIX-CRITERE : multi et le prix est un critère (B05-PM-02 = OUI)
                    .doesNotContain("CCAP UNIQUE", "n’est pas alloti", "de l’admission de la prestation", "{{", "% %");
        }
    }

    @Test
    @DisplayName("PDF : les 15 cases « ❏ » et les 2 flèches « ➢ » de l'AE s'impriment en ZapfDingbats (Helvetica ne les porte "
            + "pas) ; le docx garde le caractère Unicode ; le texte autour reste en Helvetica")
    void dingbatsAuPdf() throws Exception {
        com.lowagie.text.Font helvetica = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 10);
        com.lowagie.text.Paragraph p = GenerateurDocumentsFiche.paragraphePdf("❏ Représentant légal ➢ fin", helvetica);
        List<String> morceaux = ((List<?>) p.getChunks()).stream()
                .map(o -> (com.lowagie.text.Chunk) o).map(c -> c.getFont().getFamilyname() + ":" + c.getContent()).toList();
        assertThat(morceaux).containsExactly("ZapfDingbats:o", "Helvetica: Représentant légal ", "ZapfDingbats:â",
                "Helvetica: fin");
        assertThat(GenerateurDocumentsFiche.paragraphePdf("sans case", helvetica).getChunks()).hasSize(1);

        ModelesDao dao = new ModelesDao();
        long cases = 0;
        long fleches = 0;
        for (DocumentLibre.Element e : dao.modele("AE-CC").elements()) {
            if (e instanceof DocumentLibre.Paragraphe par) {
                cases += par.texte().chars().filter(c -> c == '❏').count();
                fleches += par.texte().chars().filter(c -> c == '➢').count();
            }
        }
        assertThat(cases).isEqualTo(15);
        assertThat(fleches).isEqualTo(2);
        List<GenerateurDocumentsFiche.Fichier> fichiers = new GenerateurDocumentsFiche()
                .generer(FormulairesCandidat.brut("AE-CC", dao.modele("AE-CC").elements()));
        assertThat(new String(fichiers.get(1).contenu(), java.nio.charset.StandardCharsets.ISO_8859_1)).contains("/ZapfDingbats");
        // ⚠️ 2026-09-28 (contre-recette du front) — le CODE écrit dans le flux, pas seulement la police : dans la police
        // ZapfDingbats (sans /Encoding, encodage intégré), 111 = a74 = ❏ et 226 = a173 = ➢.
        assertThat(octetsZapf(fichiers.get(1).contenu())).isEqualTo(Map.of(111, 15, 226, 2));
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(fichiers.get(0).contenu()));
                org.apache.poi.xwpf.extractor.XWPFWordExtractor ex = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            assertThat(ex.getText().chars().filter(c -> c == '❏').count()).isEqualTo(15);
        }
    }

    // ------------------------------------------------------------------ outils

    /**
     * Les octets écrits en ZapfDingbats dans un PDF, avec leur nombre : pour chaque page, la police ZapfDingbats de ses
     * ressources (on vérifie qu'elle ne déclare aucun /Encoding), puis les chaînes des opérateurs de texte qui la suivent.
     */
    private static Map<Integer, Integer> octetsZapf(byte[] pdf) throws Exception {
        Map<Integer, Integer> compte = new java.util.TreeMap<>();
        com.lowagie.text.pdf.PdfReader r = new com.lowagie.text.pdf.PdfReader(pdf);
        for (int p = 1; p <= r.getNumberOfPages(); p++) {
            com.lowagie.text.pdf.PdfDictionary polices = r.getPageN(p).getAsDict(com.lowagie.text.pdf.PdfName.RESOURCES)
                    .getAsDict(com.lowagie.text.pdf.PdfName.FONT);
            String zapf = null;
            for (com.lowagie.text.pdf.PdfName n : polices.getKeys()) {
                com.lowagie.text.pdf.PdfDictionary f = (com.lowagie.text.pdf.PdfDictionary)
                        com.lowagie.text.pdf.PdfReader.getPdfObject(polices.get(n));
                if (f.get(com.lowagie.text.pdf.PdfName.BASEFONT).toString().contains("ZapfDingbats")) {
                    assertThat(f.get(com.lowagie.text.pdf.PdfName.ENCODING)).as("ZapfDingbats sans /Encoding").isNull();
                    zapf = n.toString();
                }
            }
            if (zapf == null) {
                continue;
            }
            String flux = new String(r.getPageContent(p), java.nio.charset.StandardCharsets.ISO_8859_1);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(zapf)
                    + " [0-9.]+ Tf(.*?)(?=/F\\d+ [0-9.]+ Tf|ET)", java.util.regex.Pattern.DOTALL).matcher(flux);
            while (m.find()) {
                java.util.regex.Matcher t = java.util.regex.Pattern.compile("\\(((?:\\\\.|[^\\\\)])*)\\)").matcher(m.group(1));
                while (t.find()) {
                    for (char ch : t.group(1).toCharArray()) {
                        if (ch != '\\') {
                            compte.merge((int) ch, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return compte;
    }

    private static FicheMarcheDto fiche(Map<String, String> cadrage) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("CONTRAT_CADRE");
        f.setCategorie("FOURNITURES_SERVICES");
        f.setCadrage(new LinkedHashMap<>(cadrage));
        f.setValeurs(new HashMap<>());
        return f;
    }

    private static Map<String, ChampFicheMarche> champs() {
        Map<String, ChampFicheMarche> m = new HashMap<>();
        for (String[] t : List.of(new String[] {"B04-CP-02", "DATE_HEURE"}, new String[] {"B05-MT-01", "MONTANT"},
                new String[] {"B08-AV-02", "POURCENTAGE"}, new String[] {"B05-PM-04", "POURCENTAGE"})) {
            ChampFicheMarche c = new ChampFicheMarche();
            c.setCode(t[0]);
            c.setType(t[1]);
            c.setActif(true);
            m.put(t[0], c);
        }
        ChampFicheMarche ds05 = new ChampFicheMarche();   // 01/10 : le montant du DAO, saisi par lot (§B7.4)
        ds05.setCode("B04-DS-05");
        ds05.setType("MONTANT");
        ds05.setActif(true);
        ds05.setParLot(true);
        m.put("B04-DS-05", ds05);
        return m;
    }

    @SuppressWarnings("unused")
    private static final Set<String> RIEN = Set.of();
}
