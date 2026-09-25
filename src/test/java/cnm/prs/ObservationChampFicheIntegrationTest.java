package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>Une observation d'examen qui pointe une information de la fiche DAO</strong> (demande front du
 * 2026-09-25, V44) — {@code idDmc} et {@code champFiche} sur la ligne « Au lieu de / Lire », libellé et valeur figés à
 * l'observation, recopiés au périmètre du PV.
 *
 * <p>Jeu : la fiche d'une ligne à commande en trois lots (plan 9900, ligne 9902), validée, est celle du dossier 1
 * (EXAMINE, examen 1 de CTRMEM) ; une seconde fiche (ligne 9901) n'est celle d'aucun dossier. Grille : un point de
 * portée DOSSIER et un de portée SUPPRESSION.</p>
 */
class ObservationChampFicheIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final int PT_DOSSIER = 8501;
    private static final int PT_SUPPRESSION = 8502;

    @Autowired private ChampFicheMarcheService champService;

    private Long idDmc;
    private Long autreDmc;

    @BeforeEach
    void jeu() throws Exception {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        dossierRepository.save(dossierLoc(9900, "CLOTURE", "ANT", "PRMP001"));
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setIdEntiteContract(1);
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9900, 9900, "PRMP001"));
        receptionRepository.save(reception(9900, 9900, "CTRCC1", true));
        dispatchRepository.save(dispatch(9900, 9900, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9900, 9900, "CTRMEM"));
        seedPvSigne(9900, 9900);
        ligne(9901);
        ligne(9902);
        for (int n = 1; n <= 3; n++) {
            Lot lot = new Lot();
            lot.setIdLot(9910 + n);
            lot.setIdDossier(9900);
            lot.setIdDetail(9902);
            lot.setDesignationLot("Lot " + n);
            lotRepository.save(lot);
        }
        champService.importerCsv(new ClassPathResource("fiche-marche/referentiel-champs-fiche-marche-fournitures.csv")
                .getFile().toPath());

        idDmc = creerDmc(9902);
        cadrage(idDmc, "OUI");
        remplirObligatoiresEtValider(idDmc, "A_COMMANDE", "FOURNITURES_SERVICES", montants(2000000));
        autreDmc = creerDmc(9901);

        Dossier examine = dossierRepository.findById(1).orElseThrow();
        examine.setIdSousType("DAO");
        examine.setIdDmc(idDmc);
        dossierRepository.save(examine);
        point(PT_DOSSIER, "Validité des offres conforme au code", PorteePointCtrl.DOSSIER);
        point(PT_SUPPRESSION, "Constat de retrait", PorteePointCtrl.SUPPRESSION);
    }

    @Test
    @DisplayName("1 — Une ligne vise B05-TP-02#2 (clé normalisée) : relue avec libellé, valeur figée telle qu'imprimée, lot 2 "
            + "et idDmc, par les deux lectures ; une information commune : lot null")
    void ancrageRelu() throws Exception {
        String corps = resultat(PT_DOSSIER, ligneFiche(idDmc, "b05-tp-02#2", "{}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(corps, "$.observations[0].champFiche")).isEqualTo("B05-TP-02#2");
        assertThat(JsonPath.<Number>read(corps, "$.observations[0].idDmc").longValue()).isEqualTo(idDmc);
        assertThat(JsonPath.<String>read(corps, "$.observations[0].libelleChampFiche"))
                .isEqualTo("Montant minimum annuel du marché (Ariary)");
        assertThat(JsonPath.<String>read(corps, "$.observations[0].valeurChampFiche")).startsWith("4 000 000 Ariary (");
        assertThat(JsonPath.<Integer>read(corps, "$.observations[0].lot")).isEqualTo(2);

        int idResultat = JsonPath.read(corps, "$.idDetailExamen");
        mvc.perform(get("/api/observation-controles").param("detail", String.valueOf(idResultat))
                .header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].champFiche").value("B05-TP-02#2"))
                .andExpect(jsonPath("$[0].lot").value(2));

        mvc.perform(post("/api/observation-controles").header("Authorization", tokenMembre).contentType(JSON)
                .content("{\"idDetail\":" + idResultat + ",\"ordre\":2,\"auLieuDe\":\"12\",\"lire\":\"24\","
                        + "\"idDmc\":" + idDmc + ",\"champFiche\":\"B02-AU-04\",\"valeurChampFiche\":\"inventée\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelleChampFiche").value("Durée de validité du marché à commande (mois)"))
                .andExpect(jsonPath("$.valeurChampFiche").value("30"))
                .andExpect(jsonPath("$.lot").doesNotExist());
    }

    @Test
    @DisplayName("2 — Refus : sans idDmc (400 idDmc), fiche d'un autre dossier (409 FICHE_HORS_DOSSIER), champ inconnu ou "
            + "d'une autre forme, clé nue d'un champ par lot, rang hors du plan, rang sur un champ commun, cellule et "
            + "information à la fois, point SUPPRESSION (400 champFiche)")
    void refus() throws Exception {
        resultat(PT_DOSSIER, "{\"ordre\":1,\"auLieuDe\":\"a\",\"lire\":\"b\",\"champFiche\":\"B02-AU-04\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("observations[0].idDmc"));
        resultat(PT_DOSSIER, ligneFiche(autreDmc, "B02-AU-04", "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_HORS_DOSSIER"));
        for (String cle : List.of("B99-XX-01", "B06-EO-11", "B05-TP-02", "B05-TP-02#4", "B02-AU-04#1")) {
            resultat(PT_DOSSIER, ligneFiche(idDmc, cle, "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erreurs[0].champ").value("observations[0].champFiche"));
        }
        resultat(PT_DOSSIER, ligneFiche(idDmc, "B02-AU-04", "{\"champ\":\"mode\",\"idMarcheCible\":9902}"))
                .andExpect(status().isBadRequest());
        resultat(PT_SUPPRESSION, ligneFiche(idDmc, "B02-AU-04", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("observations[0].champFiche"));
    }

    @Test
    @DisplayName("3 — Après révision de la fiche : la ligne réenregistrée garde la valeur observée ; une nouvelle ligne sur "
            + "la même information fige la valeur de la nouvelle version")
    void valeurFigeeApresRevision() throws Exception {
        String corps = resultat(PT_DOSSIER, ligneFiche(idDmc, "B05-TP-02#2", "{}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idResultat = JsonPath.read(corps, "$.idDetailExamen");

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":" + new tools.jackson.databind.ObjectMapper().writeValueAsString(blocB05(9000000)) + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        mvc.perform(put("/api/examen-details/" + idResultat).header("Authorization", tokenMembre).contentType(JSON)
                .content(corpsResultat(PT_DOSSIER, ligneFiche(idDmc, "B05-TP-02#2", "{}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations[0].valeurChampFiche").value(org.hamcrest.Matchers.startsWith("4 000 000")));
        mvc.perform(post("/api/observation-controles").header("Authorization", tokenMembre).contentType(JSON)
                .content("{\"idDetail\":" + idResultat + ",\"ordre\":2,\"auLieuDe\":\"a\",\"lire\":\"b\",\"idDmc\":"
                        + idDmc + ",\"champFiche\":\"B05-TP-02#2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valeurChampFiche").value(org.hamcrest.Matchers.startsWith("18 000 000")));
    }

    @Test
    @DisplayName("4 — Signature FAVR : observations-pv sert idDmc, champFiche, libellé, valeur figée et lot")
    void recopieAuPv() throws Exception {
        resultat(PT_DOSSIER, ligneFiche(idDmc, "B05-TP-03#3", "{}")).andExpect(status().isCreated());
        signerPvAvecAvis(9301, "FAVR");
        String corps = mvc.perform(get("/api/observations-pv").header("Authorization", tokenPrmp).param("dossier", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> visees = JsonPath.read(corps, "$[?(@.champFiche=='B05-TP-03#3')]");
        assertThat(visees).hasSize(1);
        assertThat(visees.get(0)).containsEntry("libelleChampFiche", "Montant maximum annuel du marché (Ariary)")
                .containsEntry("lot", 3);
        assertThat(((Number) visees.get(0).get("idDmc")).longValue()).isEqualTo(idDmc);
        assertThat((String) visees.get(0).get("valeurChampFiche")).startsWith("30 000 000 Ariary");
    }

    // ------------------------------------------------------------------ outils

    /** Montants et délais des trois lots : minimum {@code base × n}, maximum {@code 5 × base × n}. */
    private static Map<String, String> montants(int base) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int n = 1; n <= 3; n++) {
            m.put("B05-TP-02#" + n, String.valueOf(base * n));
            m.put("B05-TP-03#" + n, String.valueOf(5 * base * n));
        }
        return m;
    }

    /** Les valeurs actuelles du bloc B05 de la fiche, montants des lots remplacés. */
    private Map<String, String> blocB05(int base) throws Exception {
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, String> b05 = new LinkedHashMap<>();
        JsonPath.<Map<String, String>>read(fiche, "$.valeurs").forEach((k, v) -> {
            if (k.startsWith("B05-")) {
                b05.put(k, v);
            }
        });
        b05.putAll(montants(base));
        return b05;
    }

    private static String ligneFiche(Long dmc, String cle, String extra) {
        String e = extra.substring(1, extra.length() - 1);
        return "{\"ordre\":1,\"auLieuDe\":\"75 jours\",\"lire\":\"90 jours\",\"idDmc\":" + dmc + ",\"champFiche\":\""
                + cle + "\"" + (e.isBlank() ? "" : "," + e) + "}";
    }

    private static String corpsResultat(int point, String observation) {
        return "{\"idExamen\":1,\"idPtControle\":" + point + ",\"conforme\":false,\"observations\":[" + observation + "]}";
    }

    private ResultActions resultat(int point, String observation) throws Exception {
        return mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(JSON)
                .content(corpsResultat(point, observation)));
    }

    private void point(int id, String libelle, PorteePointCtrl portee) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id);
        p.setLibelPointCtrl(libelle);
        p.setObligatoire(true);
        p.setIdTypeDossier("DDP");
        p.setPortee(portee);
        p.setOrdrePointCtrl(id);
        pointsCtrlRepository.save(p);
    }

    private void cadrage(Long dmc, String alloti) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + dmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"alloti\":\"" + alloti + "\",\"variantes\":\"NON\",\"groupement\":\"NON\","
                        + "\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\",\"prixRevisable\":\"NON\","
                        + "\"garantieSoumission\":\"NON\",\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void ligne(int idDetail) {
        Marche l = marcheDao(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(FormeMarche.A_COMMANDE);
        l.setDesignationMarche("Acquisition de matériels informatiques " + idDetail);
        marcheRepository.save(l);
    }
}
