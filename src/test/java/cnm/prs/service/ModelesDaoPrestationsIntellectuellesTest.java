package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.ChampFicheMarche;

/**
 * ⚠️ <strong>Lot D3</strong> (demande front du 2026-09-29, §B1, §B3, §B5) — les DAO de prestations intellectuelles rendus
 * depuis leurs documents types (DPIC-PI, AE-PI, CPS-PI), un par grand cas de sélection et de rémunération. Pur.
 */
class ModelesDaoPrestationsIntellectuellesTest {

    private static final String MS_QUALITE_COUT = "Qualité technique, expérience et proposition financière";
    private static final String MS_BUDGET = "Budget prédéterminé dont le candidat propose la meilleure utilisation";
    private static final String MS_MOINDRE_COUT = "Meilleure proposition financière parmi les candidats ayant obtenu la note "
            + "technique minimale";
    private static final String MS_QUALITE = "Qualité technique exclusivement";

    private final ModelesDao dao = new ModelesDao();

    @Test
    @DisplayName("Qualité-coût, prix forfaitaire, prix révisables, avance : poids T et F avec la virgule, forfait, révision, avance ; "
            + "la formule en « }} » du CPS rendue telle quelle")
    void qualiteCoutForfaitRevisableAvance() {
        FicheMarcheDto f = fiche(Map.of("prixRevisable", "OUI", "avance", "OUI"));
        f.setValeurs(new HashMap<>(Map.of("B02-MS-01", MS_QUALITE_COUT, "B05-PF-01", "Prix forfaitaire", "B06-CS-02", "0.8",
                "B06-CS-03", "0.2", "B08-AI-03", "15")));
        String dpic = rendre("DPIC", "DPIC-PI", f);
        assertThat(dpic).contains("de la qualité technique de la proposition, de l’expérience du candidat",
                "T = 0,8, et", "F = 0,2", "a) Le Marché est rémunéré sur la base d'un prix forfaitaire.", "Les prix sont révisables")
                .doesNotContain("exclusivement de la qualité technique", "le budget disponible est de", "b) Le Marché est rémunéré au temps passé.",
                        "0.8", "{{");
        assertThat(rendre("AE", "AE-PI", f)).contains("par application du prix global forfaitaire")
                .doesNotContain("ventilation de la rémunération au temps", "{{");
        String cps = rendre("CCAP", "CPS-PI", f);
        assertThat(cps).contains("Les prix seront révisés conformément", "Le montant de l'avance forfaitaire est de:",
                "{ou Rl= Rlo X [ 0,15+0,85 Il/Ilo] }}");
        assertThat(cps.replace("{ou Rl= Rlo X [ 0,15+0,85 Il/Ilo] }}", "")).doesNotContain("{{", "}}");
    }

    @Test
    @DisplayName("Qualité technique seule, temps passé, sans avance : ni poids T/F ni forfait ; l'AE rémunère au temps passé")
    void qualiteSeuleTempsPasse() {
        FicheMarcheDto f = fiche(Map.of("prixRevisable", "NON", "avance", "NON"));
        f.setValeurs(new HashMap<>(Map.of("B02-MS-01", MS_QUALITE, "B05-PF-01", "Temps passé")));
        String dpic = rendre("DPIC", "DPIC-PI", f);
        assertThat(dpic).contains("exclusivement de la qualité technique", "b) Le Marché est rémunéré au temps passé.")
                .doesNotContain("T = ", "a) Le Marché est rémunéré sur la base d'un prix forfaitaire.", "Les prix sont révisables", "{{");
        assertThat(rendre("AE", "AE-PI", f)).contains("ventilation de la rémunération au temps")
                .doesNotContain("par application du prix global forfaitaire", "{{");
        assertThat(rendre("CCAP", "CPS-PI", f)).doesNotContain("Le montant de l'avance forfaitaire est de:",
                "Les prix seront révisés conformément");
    }

    @Test
    @DisplayName("Moindre coût, pourcentage au barème de la profession : note technique minimale, barème nommé, score minimum")
    void moindreCoutPourcentageBareme() {
        FicheMarcheDto f = fiche(Map.of("prixRevisable", "NON", "avance", "NON"));
        f.setValeurs(new HashMap<>(Map.of("B02-MS-01", MS_MOINDRE_COUT, "B05-PF-01", "Lié au coût estimatif ou effectif de travaux",
                "B05-PF-10", "Barème de l'Ordre des architectes", "B06-TP-07", "70")));
        String dpic = rendre("DPIC", "DPIC-PI", f);
        assertThat(dpic).contains("de la meilleure proposition financière soumise par les candidats ayant obtenu une note technique minimum.",
                "calculé par application du barème en usage Barème de l'Ordre des architectes", "70 Points")
                .doesNotContain("T = ", "exclusivement de la qualité technique", "{{");
    }

    @Test
    @DisplayName("Budget prédéterminé, prix forfaitaire : le budget disponible (B05-PF-13) imprimé en Ariary, rejet au-delà")
    void budgetPredetermine() {
        FicheMarcheDto f = fiche(Map.of("prixRevisable", "NON", "avance", "NON"));
        f.setValeurs(new HashMap<>(Map.of("B02-MS-01", MS_BUDGET, "B05-PF-01", "Prix forfaitaire", "B05-PF-13", "50000000")));
        String dpic = rendre("DPIC", "DPIC-PI", f);
        assertThat(dpic).contains("d’un budget prédéterminé dont le candidat doit proposer la meilleure utilisation possible",
                "le budget disponible est de: 50 000 000 Ariary", "Les propositions financières d’un montant supérieur au budget "
                        + "disponible seront rejetées")
                .doesNotContain("T = ", "{{");
    }

    @Test
    @DisplayName("Règles (§B2.2.4, §B2.2.5) : plafond des pénalités à 10 % pour les PI, 15 % ailleurs ; intérêts moratoires en "
            + "points (NOMBRE) : au moins un point")
    void reglesDesPrestationsIntellectuelles() {
        ChampFicheMarche taux = champ("B09-XX-01", "POURCENTAGE", "PENALITES_PLAFOND_15:TAUX");
        Map<String, String> v = Map.of("B09-XX-01", "12");
        BilanControlesDto pi = ControlesFicheMarche.bilan(List.of(taux), v, Map.of(), Map.of(), 0, null, null, null,
                "PRESTATIONS_INTELLECTUELLES");
        assertThat(pi.avertissements()).extracting(BilanControlesDto.Controle::message).singleElement().asString()
                .contains("au-delà du plafond de 10 % du CCAG");
        BilanControlesDto fs = ControlesFicheMarche.bilan(List.of(taux), v, Map.of(), Map.of(), 0, null, null, null, null);
        assertThat(fs.avertissements()).isEmpty();
        assertThat(fs.ok()).extracting(BilanControlesDto.Controle::message).contains("Pénalités dans le plafond de 15 % du CCAG.");

        ChampFicheMarche points = champ("B08-IP-01", "NOMBRE", "INTERETS_MORATOIRES_TAUX:TAUX");
        assertThat(ControlesFicheMarche.bilan(List.of(points), Map.of("B08-IP-01", "0.5"), Map.of(), Map.of()).avertissements())
                .extracting(BilanControlesDto.Controle::message).singleElement().asString().contains("au moins un point");
        assertThat(ControlesFicheMarche.bilan(List.of(points), Map.of("B08-IP-01", "2"), Map.of(), Map.of()).ok())
                .extracting(BilanControlesDto.Controle::regle).contains(ControlesFicheMarche.INTERETS_MORATOIRES_TAUX);
    }

    @Test
    @DisplayName("Options d'une liste : « | » quand une option contient une virgule (B02-MS-01 : quatre options, pas cinq)")
    void optionsAvecVirgule() {
        String texte = ChampFicheMarche.optionsTexte(List.of(MS_QUALITE_COUT, MS_BUDGET, MS_MOINDRE_COUT, MS_QUALITE));
        assertThat(texte).contains("|");
        assertThat(ChampFicheMarche.options(texte)).containsExactly(MS_QUALITE_COUT, MS_BUDGET, MS_MOINDRE_COUT, MS_QUALITE);
        assertThat(ChampFicheMarche.options("Français,Français et une seconde langue")).hasSize(2);
        assertThat(ChampFicheMarche.optionsTexte(List.of("A", "B"))).isEqualTo("A,B");
    }

    // ------------------------------------------------------------------ outils

    private String rendre(String type, String sigle, FicheMarcheDto f) {
        return FormulairesCandidat.rendreModele(type, null, f, champs(), dao.modele(sigle), null).texte()
                .replace(' ', ' ').replace(' ', ' ');
    }

    private static FicheMarcheDto fiche(Map<String, String> cadrage) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("QUANTITE_FIXE");
        f.setCategorie("PRESTATIONS_INTELLECTUELLES");
        f.setCadrage(new LinkedHashMap<>(cadrage));
        f.setValeurs(new HashMap<>());
        f.setValeursPpm(new HashMap<>(Map.of("B01-AC-01", "Ministère X", "B02-OB-01", "Étude de faisabilité")));
        return f;
    }

    private static ChampFicheMarche champ(String code, String type, String controle) {
        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode(code);
        c.setCodeRubrique(code.substring(0, code.lastIndexOf('-')));
        c.setType(type);
        c.setSource("SAISIE");
        c.setControle(controle);
        c.setActif(true);
        c.setObligatoire(false);
        return c;
    }

    private static Map<String, ChampFicheMarche> champs() {
        Map<String, ChampFicheMarche> m = new HashMap<>();
        for (String[] t : List.of(new String[] {"B05-PF-13", "MONTANT"}, new String[] {"B06-CS-02", "NOMBRE"},
                new String[] {"B06-CS-03", "NOMBRE"}, new String[] {"B06-TP-07", "NOMBRE"}, new String[] {"B08-AI-03", "POURCENTAGE"})) {
            ChampFicheMarche c = new ChampFicheMarche();
            c.setCode(t[0]);
            c.setType(t[1]);
            c.setActif(true);
            m.put(t[0], c);
        }
        return m;
    }
}
