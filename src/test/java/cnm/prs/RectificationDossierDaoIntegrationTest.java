package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
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
import cnm.prs.entity.ObservationControle;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.TypeDmc;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.LettreRenvoiService;

/**
 * ⚠️ <strong>Lot C — la rectification d'un dossier DAO après le PV</strong> (demande front du 2026-09-26, V49) : la
 * rectification est une <em>révision validée</em> de la fiche. B1 la fiche verrouillée tant que la Commission tient le
 * dossier, {@code versionSoumise} ; B2 la validation de la révision remplace les pièces produites (précédentes
 * conservées) et le journal le dit ; B3 la resoumission et les compléments exigent la révision validée ; B4 la valeur
 * actuelle de l'information observée ; B5 le périmètre de réexamen d'un DAO ; B6 la lettre nomme l'information.
 *
 * <p>Jeu : celui de {@code ObservationChampFicheIntegrationTest} — plan 9900 signé, ligne 9902 à commande en trois lots,
 * fiche validée (v1) qui est celle du dossier 1 du socle (EXAMINE, examen 1 de CTRMEM), référentiel des fournitures
 * importé, type de pièce {@code DAO_COMPLET} présent (le dossier 1 porte les PDF de la v1), un point de portée DOSSIER.</p>
 */
class RectificationDossierDaoIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final int PT_DOSSIER = 8601;

    @Autowired private ChampFicheMarcheService champService;

    private Long idDmc;
    private int typeDao;

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
        // ⚠️ 2026-09-29 — B05-TP-02 est retiré de la fiche des fournitures (demande front « champs non imprimés ») ; ce test
        // l'emploie comme exemple de champ par lot du mécanisme éprouvé ici, pas du référentiel : il le réactive.
        reactiverMontantMinimum();
        typeDao = seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 1);
        TypePieceJointe t = typePieceJointeRepository.findById(typeDao).orElseThrow();
        t.setCode("DAO_COMPLET");
        typePieceJointeRepository.save(t);

        // Le dossier 1 du socle devient le dossier soumis de la fiche : lié AVANT la validation de la v1 et brouillon le
        // temps qu'elle lui joigne ses PDF, puis remis en examen avec la version 1 soumise et examinée.
        idDmc = creerDmc(9902);
        cadrage(idDmc);
        Dossier examine = dossierRepository.findById(1).orElseThrow();
        examine.setIdSousType("DAOO");
        examine.setIdDmc(idDmc);
        examine.setStatut("BROUILLON");
        dossierRepository.saveAndFlush(examine);
        remplirObligatoiresEtValider(idDmc, "A_COMMANDE", "FOURNITURES_SERVICES", montants(2000000));
        statutDossier1("EXAMINE");
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setVersionFicheSoumise(1);
        d.setVersionFicheExaminee(1);
        dossierRepository.saveAndFlush(d);
        point(PT_DOSSIER, "Validité des offres conforme au code", PorteePointCtrl.DOSSIER);
    }

    // ------------------------------------------------------------------ B1

    @Test
    @DisplayName("B1 — La fiche d'un dossier que la Commission tient ne se révise pas : 409 DOSSIER_EN_EXAMEN (idDossier, "
            + "details.statut) sur dix statuts ; rendue à la PRMP (EN_ATTENTE_DECISION_PRMP), la révision s'ouvre")
    void ficheVerrouilleePendantLExamen() throws Exception {
        for (String statut : List.of("EXAMINE", "SOUMIS", "PRET_DISPATCH", "DISPATCHE", "A_REEXAMINER", "PV_SIGNE",
                "EN_VERIFICATION", "OBSERVATIONS_LEVEES", "DECISION_TRANSMISE_SIGMP", "CLOTURE")) {
            statutDossier1(statut);
            mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DOSSIER_EN_EXAMEN"))
                    .andExpect(jsonPath("$.idDossier").value(1))
                    .andExpect(jsonPath("$.details.statut").value(statut));
        }
        statutDossier1("EN_ATTENTE_DECISION_PRMP");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    // ------------------------------------------------------------------ B1 — versionSoumise à la soumission

    @Test
    @DisplayName("B1 — Un dossier DAO produit par la fiche : ficheMarche.versionSoumise nul en brouillon, posé à la soumission "
            + "(version 1) ; dès lors la fiche est verrouillée (SOUMIS)")
    void versionSoumisePoseeALaSoumission() throws Exception {
        Long autre = creerDmc(9901);
        cadrage(autre);
        remplirObligatoiresEtValider(autre, "A_COMMANDE", "FOURNITURES_SERVICES", Map.of());
        String cree = mvc.perform(post("/api/fiches-marche/" + autre + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ficheMarche.version").value(1))
                .andExpect(jsonPath("$.ficheMarche.versionSoumise").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(cree, "$.idDossier");
        mvc.perform(post("/api/dossiers/" + idDossier + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"))
                .andExpect(jsonPath("$.ficheMarche.versionSoumise").value(1));
        mvc.perform(post("/api/fiches-marche/" + autre + "/reviser").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOSSIER_EN_EXAMEN"))
                .andExpect(jsonPath("$.idDossier").value(idDossier))
                .andExpect(jsonPath("$.details.statut").value("SOUMIS"));
    }

    // ------------------------------------------------------------------ B2, B3, B4 — chemin FAVR

    @Test
    @DisplayName("Chemin FAVR — B3 : resoumettre exige une révision VALIDÉE postérieure à la version soumise (409 "
            + "FICHE_NON_REVISEE : versionSoumise, versionCourante, statutFiche) ; B2 : la validation de la v2 ajoute ses "
            + "PDF en version corrigée, ceux de la v1 conservés, journal FICHE_REVISEE ; B4 : l'observation du PV dit la "
            + "valeur observée (v1) et la valeur actuelle (v2) ; la resoumission avance versionSoumise")
    void cheminFavr() throws Exception {
        resultat(PT_DOSSIER, ligneFiche(idDmc, "B05-TP-02#2")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.observations[0].versionFiche").value(1));
        signerPvAvecAvis(9401, "FAVR");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"))
                .andExpect(jsonPath("$.ficheMarche.versionSoumise").value(1));
        String obs = mvc.perform(get("/api/observations-pv").param("dossier", "1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> visees = JsonPath.read(obs, "$[?(@.champFiche=='B05-TP-02#2')]");
        assertThat(visees).hasSize(1);
        assertThat(visees.get(0)).containsEntry("versionFicheObservee", 1).containsEntry("versionFicheActuelle", 1);
        assertThat((String) visees.get(0).get("valeurChampFiche")).startsWith("4 000 000 Ariary");
        assertThat((String) visees.get(0).get("valeurChampFicheActuelle")).startsWith("4 000 000 Ariary");

        // B3 — rien de révisé : refus nominatif.
        resoumettre().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FICHE_NON_REVISEE"))
                .andExpect(jsonPath("$.idDossier").value(1))
                .andExpect(jsonPath("$.details.versionSoumise").value(1))
                .andExpect(jsonPath("$.details.versionCourante").value(1))
                .andExpect(jsonPath("$.details.statutFiche").value("VALIDEE"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        resoumettre().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FICHE_NON_REVISEE"))
                .andExpect(jsonPath("$.details.versionCourante").value(2))
                .andExpect(jsonPath("$.details.statutFiche").value("BROUILLON"));

        // B2 — la révision corrige le minimum du lot 2 et se valide : les PDF de la v2 rejoignent le dossier en version
        // corrigée, ceux de la v1 restent.
        int avant = piecesProduites().size();
        assertThat(avant).isGreaterThan(0);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":" + new tools.jackson.databind.ObjectMapper().writeValueAsString(blocB05(9000000)) + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        List<Map<String, Object>> pieces = piecesProduites();
        List<Map<String, Object>> v2 = pieces.stream().filter(p -> String.valueOf(p.get("nomFichier")).contains("_v2.")).toList();
        List<Map<String, Object>> v1 = pieces.stream().filter(p -> String.valueOf(p.get("nomFichier")).contains("_v1.")).toList();
        String docs = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").param("version", "2")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int pdfV2 = JsonPath.<List<Object>>read(docs, "$[?(@.extension=='pdf')]").size();
        assertThat(v1).as("les pièces de la version examinée sont conservées").hasSize(avant);
        assertThat(v2).as("chaque PDF de la v2 est joint").hasSize(pdfV2);
        assertThat(v2).allSatisfy(p -> assertThat(p).containsEntry("versionCorrigee", true));
        assertThat(v1).allSatisfy(p -> assertThat(p.get("versionCorrigee")).isNotEqualTo(true));
        String journal = mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> details = JsonPath.read(journal, "$[?(@.typeAction=='FICHE_REVISEE')].detail");
        assertThat(details).containsExactly("Fiche marché version 2 validée, 1 information(s) modifiée(s), " + pdfV2 + " pièce(s) remplacée(s)");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.ficheMarche.version").value(2))
                .andExpect(jsonPath("$.ficheMarche.versionSoumise").value(1));

        // B4 — l'observation garde la valeur observée et dit la valeur actuelle.
        obs = mvc.perform(get("/api/observations-pv").param("dossier", "1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        visees = JsonPath.read(obs, "$[?(@.champFiche=='B05-TP-02#2')]");
        assertThat(visees.get(0)).containsEntry("versionFicheObservee", 1).containsEntry("versionFicheActuelle", 2);
        assertThat((String) visees.get(0).get("valeurChampFiche")).startsWith("4 000 000 Ariary");
        assertThat((String) visees.get(0).get("valeurChampFicheActuelle")).startsWith("18 000 000 Ariary");

        // B3 — la révision validée ouvre la resoumission ; versionSoumise avance.
        resoumettre().andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"))
                .andExpect(jsonPath("$.ficheMarche.versionSoumise").value(2));
    }

    // ------------------------------------------------------------------ B3, B5 — chemin lettre de renvoi

    @Test
    @DisplayName("Chemin lettre de renvoi — B3 : transmettre-complements exige la révision validée (pas la pièce déposée) ; "
            + "B2 : les PDF de la v2 sont des compléments (après lettre de renvoi, version corrigée) ; B5 : en A_REEXAMINER, "
            + "perimetre-examen sert ficheDao (v1 → v2, l'information changée avant/après)")
    void cheminLettreDeRenvoi() throws Exception {
        statutDossier1("EN_ATTENTE_PIECES");
        transmettre().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_NON_REVISEE"));
        mvc.perform(get("/api/dossiers/1/perimetre-examen").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ficheDaoAExaminer").value(false))
                .andExpect(jsonPath("$.ficheDao").value(nullValue()));

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":" + new tools.jackson.databind.ObjectMapper().writeValueAsString(blocB05(9000000)) + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        List<Map<String, Object>> v2 = piecesProduites().stream()
                .filter(p -> String.valueOf(p.get("nomFichier")).contains("_v2.")).toList();
        assertThat(v2).isNotEmpty().allSatisfy(p -> assertThat(p).containsEntry("apresLettreRenvoi", true).containsEntry("versionCorrigee", true));

        transmettre().andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("A_REEXAMINER"))
                .andExpect(jsonPath("$.ficheMarche.versionSoumise").value(2));
        mvc.perform(get("/api/dossiers/1/perimetre-examen").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miseAJour").value(false))
                .andExpect(jsonPath("$.ficheDaoAExaminer").value(true))
                .andExpect(jsonPath("$.ficheDao.versionExaminee").value(1))
                .andExpect(jsonPath("$.ficheDao.versionCourante").value(2))
                .andExpect(jsonPath("$.ficheDao.informations.length()").value(1))
                .andExpect(jsonPath("$.ficheDao.informations[0].champFiche").value("B05-TP-02#2"))
                .andExpect(jsonPath("$.ficheDao.informations[0].lot").value(2))
                .andExpect(jsonPath("$.ficheDao.informations[0].libelle").value("Montant minimum annuel du marché (Ariary)"))
                .andExpect(jsonPath("$.ficheDao.informations[0].avant").value(startsWith("4 000 000 Ariary")))
                .andExpect(jsonPath("$.ficheDao.informations[0].apres").value(startsWith("18 000 000 Ariary")));
    }

    // ------------------------------------------------------------------ B6

    @Test
    @DisplayName("B6 — Le corps de la lettre nomme, pour chaque observation ancrée, « Information de la fiche DAO : libellé — "
            + "lot n » sous sa ligne « au lieu de / lire » ; sans observation ancrée, le corps est rendu tel quel")
    void lettreNommeLInformation() {
        ObservationControle ancree = new ObservationControle();
        ancree.setAuLieuDe("2 170 000 Ariary");
        ancree.setLire("2 000 000 Ariary");
        ancree.setChampFiche("B05-GS-03#2");
        ancree.setLibelleChampFiche("Montant de la garantie de soumission (Ariary)");
        ObservationControle commune = new ObservationControle();
        commune.setAuLieuDe("75 jours");
        commune.setLire("90 jours");
        commune.setChampFiche("B04-VO-01");
        commune.setLibelleChampFiche("Délai de validité des offres (jours)");
        String corps = LettreRenvoiService.corpsAvecInformationsFiche("Veuillez rectifier le dossier.", List.of(ancree, commune));
        assertThat(corps).startsWith("Veuillez rectifier le dossier.\n\nInformations de la fiche du dossier d'appel d'offres")
                .contains("– Au lieu de « 2 170 000 Ariary », lire « 2 000 000 Ariary »\n   Information de la fiche DAO : "
                        + "Montant de la garantie de soumission (Ariary) — lot 2")
                .contains("– Au lieu de « 75 jours », lire « 90 jours »\n   Information de la fiche DAO : Délai de validité des offres (jours)")
                .doesNotContain("(jours) — lot");
        assertThat(LettreRenvoiService.corpsAvecInformationsFiche("Tel quel.", List.of())).isEqualTo("Tel quel.");
        assertThat(LettreRenvoiService.corpsAvecInformationsFiche(null, List.of())).isEmpty();
    }

    // ------------------------------------------------------------------ outils

    private ResultActions resoumettre() throws Exception {
        return mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motifRectification\":\"Garantie du lot 2 corrigée dans la fiche.\"}"));
    }

    private ResultActions transmettre() throws Exception {
        return mvc.perform(post("/api/dossiers/1/transmettre-complements").header("Authorization", tokenPrmp));
    }

    private List<Map<String, Object>> piecesProduites() throws Exception {
        String pieces = mvc.perform(get("/api/piece-jointe-dossiers").param("dossier", "1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(pieces, "$[?(@.idDocumentFiche)]");
    }

    private void statutDossier1(String statut) {
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setStatut(statut);
        dossierRepository.saveAndFlush(d);
    }

    /** Montants et délais des trois lots : minimum {@code base × n}, maximum {@code 5 × base × n}. */
    private static Map<String, String> montants(int base) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int n = 1; n <= 3; n++) {
            m.put("B05-TP-02#" + n, String.valueOf(base * n));
            m.put("B05-TP-03#" + n, String.valueOf(5 * base * n));
        }
        return m;
    }

    /** Les valeurs actuelles du bloc B05 de la fiche, le minimum du lot 2 remplacé. */
    private Map<String, String> blocB05(int base) throws Exception {
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, String> b05 = new LinkedHashMap<>();
        JsonPath.<Map<String, String>>read(fiche, "$.valeurs").forEach((k, v) -> {
            if (k.startsWith("B05-")) {
                b05.put(k, v);
            }
        });
        b05.put("B05-TP-02#2", String.valueOf(base * 2));
        return b05;
    }

    private static String ligneFiche(Long dmc, String cle) {
        return "{\"ordre\":1,\"auLieuDe\":\"4 000 000\",\"lire\":\"18 000 000\",\"idDmc\":" + dmc + ",\"champFiche\":\"" + cle + "\"}";
    }

    private ResultActions resultat(int point, String observation) throws Exception {
        return mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(JSON)
                .content("{\"idExamen\":1,\"idPtControle\":" + point + ",\"conforme\":false,\"observations\":[" + observation + "]}"));
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

    private void cadrage(Long dmc) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + dmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"alloti\":\"OUI\",\"variantes\":\"NON\",\"groupement\":\"NON\","
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

    @Autowired private cnm.prs.repository.ChampFicheMarcheRepository champsRepo;

    /** ⚠️ 2026-09-29 — le champ par lot de l'exemple (B05-TP-02, retiré des fournitures le 29/09) remis actif pour ce test. */
    private void reactiverMontantMinimum() {
        cnm.prs.entity.ChampFicheMarche c = champsRepo.findById("B05-TP-02").orElseThrow();
        c.setActif(true);
        champsRepo.save(c);
    }
}
