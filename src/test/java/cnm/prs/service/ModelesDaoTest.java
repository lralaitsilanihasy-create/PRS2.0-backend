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
        assertThat(dao.modele("DPAC-CC").conditions()).hasSize(14);
        assertThat(dao.modele("AE-CC").conditions()).hasSize(53);
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
            assertThat(brut.texte()).doesNotContain("CONDITION").contains("{{SI:");
            List<GenerateurDocumentsFiche.Fichier> fichiers = generateur.generer(brut);
            for (GenerateurDocumentsFiche.Fichier f : fichiers) {
                Files.write(dossier.resolve(e.getKey() + "." + f.extension()), f.contenu());
            }
        }
        assertThat(dossier.resolve("DPAC-CC.docx")).exists();
        assertThat(dossier.resolve("AE-CC.docx")).exists();
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
                "B10-RS-02", "3", "B04-CP-02", "2026-04-10T10:00", "B04-VO-01", "90", "B02-OB-01", "Fournitures de bureau")));
        fiche.setValeursPpm(new HashMap<>(v("B01-AC-13", "Appel d'offres ouvert", "B01-AC-01", "Ministère X")));
        String dpac = FormulairesCandidat.rendreModele("DPAC", null, fiche, champs(), dao.modele("DPAC-CC"), null).texte();
        assertThat(dpac).contains("à un seul titulaire (mono-attributaire)", "remis en compétition au fur et à mesure des besoins",
                "Le contrat-cadre est conclu à prix unitaires.", "La période de validité du contrat n’est pas reconductible.",
                "Sans objet.", "La transmission de dossiers par voie électronique n’est pas admise",
                "DATE ET HEURE LIMITES DE REMISE DES OFFRES : 10/04/2026 10:00", "exprimées en Ariary. Si")
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
                "B04-SE-02", "https://depot.cnm.mg")));
        fiche.setValeursPpm(new HashMap<>(v("B01-AC-13", "Appel d'offres restreint")));
        fiche.setValeursCadrage(new HashMap<>(v("B08-AV-02", "15", "B02-LV-05", "2")));
        String dpac = FormulairesCandidat.rendreModele("DPAC", null, fiche, champs(), dao.modele("DPAC-CC"), null).texte();
        assertThat(dpac).contains("à plusieurs titulaires (multi attributaire)", "selon le calendrier fixé ci-après",
                "chaque trimestre", "Cette période de validité peut être reconduite",
                "La transmission par voie électronique est admise dans les conditions suivantes",
                "[[CLAUSE À FOURNIR PAR LE JURISTE : conditions de la transmission électronique — plateforme (https://depot.cnm.mg)")
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

    // ------------------------------------------------------------------ outils

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
        return m;
    }

    @SuppressWarnings("unused")
    private static final Set<String> RIEN = Set.of();
}
