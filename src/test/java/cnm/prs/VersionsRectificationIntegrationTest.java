package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Nature;
import cnm.prs.entity.SnapshotRectifLigne;
import cnm.prs.entity.VersionDossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.SnapshotRectifLigneRepository;
import cnm.prs.repository.VersionDossierRepository;

/**
 * ⚠️ <strong>Versions archivées d'un dossier à la rectification</strong> (demande pilote du 2026-09-06,
 * relayée par le front — {@code docs/demande-backend-2026-09-06-versions-rectification.md}).
 *
 * <p>La rectification corrige le PPM en place ; jusqu'ici seul l'instantané du dernier cycle survivait,
 * pour le seul diff. Le pilote veut l'historique : chaque version remplacée est archivée, immuable, et
 * consultable. Les trois tests attendus par la demande sont ici, plus la reprise dégradée d'une version
 * d'avant la V18 (collections reconstituées depuis les empreintes).</p>
 *
 * <p>Le dossier est posé directement en {@code EN_ATTENTE_DECISION_PRMP} (comme les tests de rectification
 * de {@code VerificationIntegrationTest}) : ce qui est éprouvé ici, c'est l'archivage au PUT et sa lecture,
 * pas le circuit qui y mène — celui-ci est couvert par {@code AuthentificationHabilitationIntegrationTest}.</p>
 */
class VersionsRectificationIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER = 700;
    private static final int PPM = 700;
    private static final int LIGNE = 7001;
    private static final String VERSIONS = "/api/dossiers/" + DOSSIER + "/versions-archivees";

    @Autowired
    private VersionDossierRepository versionDossierRepository;
    @Autowired
    private SnapshotRectifLigneRepository snapshotRectifLigneRepository;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void dossierEnAttenteDecisionPrmp() {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        Dossier d = dossierLoc(DOSSIER, "EN_ATTENTE_DECISION_PRMP", "ANT", "PRMP001");
        d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        ppmRepository.save(ppm(PPM, DOSSIER, "PRMP001"));
        Marche m = marche(LIGNE, DOSSIER, PPM);
        m.setMontEstim(new BigDecimal("100"));
        marcheRepository.save(m);
    }

    // ------------------------------------------------------------------ 1. deux cycles → deux versions

    @Test
    @DisplayName("Test attendu n° 1 — deux cycles de rectification successifs → deux versions archivées + la "
            + "courante ; chaque version restitue SES montants et SES lignes ; le diff du dernier cycle est inchangé")
    void deuxCycles_deuxVersionsArchivees_chacuneAvecSonContenu() throws Exception {
        // Cycle 1 : le PREMIER PUT archive l'état initial (100) comme version 1 ; le second, même cycle,
        // n'archive rien (le diff compare toujours à l'état d'AVANT la première correction).
        rectifier(corps("200", "Marche 7001 v1", false)).andExpect(status().isOk());
        rectifier(corps("250", "Marche 7001 v1bis", true)).andExpect(status().isOk());
        mvc.perform(get(VERSIONS).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // La PRMP resoumet (clôt le cycle 1) ; le vérificateur maintient à nouveau → cycle 2.
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        Dossier d = dossierRepository.findById(DOSSIER).orElseThrow();
        d.setStatut("EN_ATTENTE_DECISION_PRMP");
        dossierRepository.save(d);

        // Cycle 2 : le premier PUT archive l'état de fin de cycle 1 (250, avec ses enfants) comme version 2.
        rectifier(corps("300", "Marche 7001 v2", false)).andExpect(status().isOk());

        // Liste : deux versions archivées, de la plus ancienne à la plus récente, signées par la PRMP.
        mvc.perform(get(VERSIONS).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].cycle").value(1))
                .andExpect(jsonPath("$[0].origine").value("RECTIFICATION"))
                .andExpect(jsonPath("$[0].idPrmpAuteur").value("PRMP001"))
                .andExpect(jsonPath("$[0].auteur").value("PRMP001"))
                .andExpect(jsonPath("$[0].nbLignes").value(1))
                .andExpect(jsonPath("$[0].reference").value("PPM-REF-700"))
                .andExpect(jsonPath("$[0].dateVersion").exists())
                .andExpect(jsonPath("$[1].numero").value(2))
                .andExpect(jsonPath("$[1].cycle").value(2))
                .andExpect(jsonPath("$[1].reference").value("PPM-700"));

        // Version 1 = l'état INITIAL : montant 100, désignation d'origine, aucune collection.
        String v1 = mvc.perform(get(VERSIONS + "/1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.numero").value(1))
                .andExpect(jsonPath("$.lignes", hasSize(1)))
                .andExpect(jsonPath("$.lignes[0].idDetail").value(LIGNE))
                .andExpect(jsonPath("$.lignes[0].designationMarche").value("Marche " + LIGNE))
                .andExpect(jsonPath("$.lignes[0].beneficiaires", hasSize(0)))
                .andExpect(jsonPath("$.lignes[0].lots", hasSize(0)))
                .andExpect(jsonPath("$.lignes[0].processus", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        assertThat(montant(v1, "$.lignes[0].montEstim")).isEqualByComparingTo("100");

        // Version 2 = l'état de fin de cycle 1 : montant 250 et les collections posées par le second PUT.
        String v2 = mvc.perform(get(VERSIONS + "/2").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.numero").value(2))
                .andExpect(jsonPath("$.lignes[0].designationMarche").value("Marche 7001 v1bis"))
                .andExpect(jsonPath("$.lignes[0].formeMarche").value("QUANTITE_FIXE"))
                .andExpect(jsonPath("$.lignes[0].beneficiaires", hasSize(1)))
                .andExpect(jsonPath("$.lignes[0].beneficiaires[0].soaCode").value("SOA-A"))
                .andExpect(jsonPath("$.lignes[0].lots", hasSize(1)))
                .andExpect(jsonPath("$.lignes[0].lots[0].designationLot").value("Lot Unique"))
                .andExpect(jsonPath("$.lignes[0].lots[0].qteLot").value(2))
                .andExpect(jsonPath("$.lignes[0].lots[0].uniteLot").value("u"))
                .andExpect(jsonPath("$.lignes[0].processus", hasSize(1)))
                .andExpect(jsonPath("$.lignes[0].processus[0].idCapm").value(1))
                .andExpect(jsonPath("$.lignes[0].processus[0].ordre").value(1))
                .andExpect(jsonPath("$.lignes[0].processus[0].dateDebut").value("2026-03-01"))
                .andExpect(jsonPath("$.lignes[0].processus[0].dateFin").value("2026-03-15"))
                .andReturn().getResponse().getContentAsString();
        assertThat(montant(v2, "$.lignes[0].montEstim")).isEqualByComparingTo("250");
        assertThat(montant(v2, "$.lignes[0].beneficiaires[0].nouvMontBenef")).isEqualByComparingTo("250");
        assertThat(montant(v2, "$.lignes[0].lots[0].montLot")).isEqualByComparingTo("250");

        // La version COURANTE est le dossier lui-même : 300.
        assertThat(marcheRepository.findById(LIGNE).orElseThrow().getMontEstim()).isEqualByComparingTo("300");

        // Le diff du DERNIER cycle est inchangé : dernière version archivée (250) vs courante (300).
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/diff-rectification").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fige").value(false))
                .andExpect(jsonPath("$.recap.modifiees").value(1))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='montEstim')].avant", hasItem("250")))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='montEstim')].apres", hasItem("300")));

        // Le circuit lit aussi l'historique (vérificateur de la localité) ; une PRMP étrangère non.
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(get(VERSIONS).header("Authorization", tokenVer)).andExpect(status().isOk());
        String tokenAutrePrmp = bearer("PRMP999", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP999", "ANT");
        mvc.perform(get(VERSIONS).header("Authorization", tokenAutrePrmp)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 2. immuabilité

    @Test
    @DisplayName("Test attendu n° 2 — une version archivée est immuable : toute écriture HTTP répond 405, et "
            + "PostgreSQL refuse tout UPDATE (trigger) ; le contenu archivé reste intact")
    void versionArchivee_immuable() throws Exception {
        rectifier(corps("200", "Marche 7001 v1", false)).andExpect(status().isOk());

        // Côté API : la ressource ne connaît que GET.
        mvc.perform(put(VERSIONS + "/1").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch(VERSIONS + "/1").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(VERSIONS + "/1").header("Authorization", tokenPrmp))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post(VERSIONS).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(VERSIONS).header("Authorization", tokenAdmin))
                .andExpect(status().isMethodNotAllowed());

        // Côté base : le trigger fn_version_archivee_immuable refuse tout UPDATE, en-tête comme lignes.
        exigerUpdateRefuse("UPDATE public.t_version_dossier SET \"NOM_AUTEUR\" = 'falsifie' WHERE \"ID_DOSSIER\" = "
                + DOSSIER);
        exigerUpdateRefuse("UPDATE public.t_snapshot_rectif_ligne SET \"MONT_ESTIM\" = 1 WHERE \"ID_DOSSIER\" = "
                + DOSSIER);

        // Rien n'a bougé.
        String v1 = mvc.perform(get(VERSIONS + "/1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.idPrmpAuteur").value("PRMP001"))
                .andReturn().getResponse().getContentAsString();
        assertThat(montant(v1, "$.lignes[0].montEstim")).isEqualByComparingTo("100");
    }

    // ------------------------------------------------------------------ 3. jamais rectifié

    @Test
    @DisplayName("Test attendu n° 3 — dossier jamais rectifié : liste VIDE (200), version inconnue → 404, et le "
            + "/diff-rectification garde son 409 « Aucune rectification »")
    void jamaisRectifie_listeVide_sansRegressionDuDiff() throws Exception {
        mvc.perform(get(VERSIONS).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(VERSIONS + "/1").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/diff-rectification").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Aucune rectification")));
        mvc.perform(get("/api/dossiers/999999/versions-archivees").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 4. reprise d'avant la V18

    @Test
    @DisplayName("Reprise (V18) — une version d'avant la V18 n'a que des EMPREINTES de collections : elles sont "
            + "reconstituées à la lecture (mode dégradé documenté), et le diff s'en sert toujours")
    void versionReprise_collectionsReconstituees() throws Exception {
        VersionDossier v = new VersionDossier();
        v.setIdDossier(DOSSIER);
        v.setNumero(1);
        v.setOrigine("RECTIFICATION");
        v.setCycle(1);
        v.setDateVersion(LocalDateTime.of(2026, 8, 20, 10, 0));
        v.setIdPrmpAuteur("PRMP001");
        v.setAuteur("PRMP001");
        v.setNbLignes(1);
        v = versionDossierRepository.save(v);
        SnapshotRectifLigne s = new SnapshotRectifLigne();
        s.setIdVersion(v.getIdVersion());
        s.setIdDossier(DOSSIER);
        s.setCycle(1);
        s.setIdDetail(LIGNE);
        s.setDesignationMarche("Marche 7001 (reprise)");
        s.setMontEstim(new BigDecimal("1500"));
        s.setEmpBeneficiaires("SOA-B:1500");
        s.setEmpLots("lot b, tranche 1:700:3,lot c:800:");
        s.setEmpProcessus("2:2026-02-10:2026-02-20,1:2026-02-01:");
        s.setDateSnapshot(LocalDateTime.of(2026, 8, 20, 10, 0));
        snapshotRectifLigneRepository.save(s);

        String corps = mvc.perform(get(VERSIONS + "/1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.exercice").doesNotExist())
                .andExpect(jsonPath("$.lignes[0].beneficiaires", hasSize(1)))
                .andExpect(jsonPath("$.lignes[0].beneficiaires[0].soaCode").value("SOA-B"))
                .andExpect(jsonPath("$.lignes[0].beneficiaires[0].numCompte").doesNotExist())
                .andExpect(jsonPath("$.lignes[0].lots", hasSize(2)))
                .andExpect(jsonPath("$.lignes[0].lots[0].designationLot").value("lot b, tranche 1"))
                .andExpect(jsonPath("$.lignes[0].lots[0].qteLot").value(3))
                .andExpect(jsonPath("$.lignes[0].lots[1].designationLot").value("lot c"))
                .andExpect(jsonPath("$.lignes[0].lots[1].qteLot").doesNotExist())
                .andExpect(jsonPath("$.lignes[0].processus", hasSize(2)))
                // Ordre d'affichage : le processus 1 (ordre 1 dans t_capm) avant le 2 (inconnu du référentiel).
                .andExpect(jsonPath("$.lignes[0].processus[0].idCapm").value(1))
                .andExpect(jsonPath("$.lignes[0].processus[0].dateDebut").value("2026-02-01"))
                .andExpect(jsonPath("$.lignes[0].processus[0].dateFin").doesNotExist())
                .andExpect(jsonPath("$.lignes[0].processus[1].idCapm").value(2))
                .andExpect(jsonPath("$.lignes[0].processus[1].dateFin").value("2026-02-20"))
                .andReturn().getResponse().getContentAsString();
        assertThat(montant(corps, "$.lignes[0].beneficiaires[0].nouvMontBenef")).isEqualByComparingTo("1500");
        assertThat(montant(corps, "$.lignes[0].lots[0].montLot")).isEqualByComparingTo("700");

        // Le diff du dernier cycle lit cette version reprise comme n'importe quelle autre.
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/diff-rectification").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recap.modifiees").value(1))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='montEstim')].avant", hasItem("1500")));

        // Un PUT du MÊME cycle (le 1, déjà archivé par la reprise) n'archive rien de plus…
        rectifier(corps("200", "Marche 7001 apres reprise", false)).andExpect(status().isOk());
        mvc.perform(get(VERSIONS).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$", hasSize(1)));
        // … et le cycle SUIVANT archive à la suite : numéro 2, cycle 2, sans rien effacer.
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        Dossier d = dossierRepository.findById(DOSSIER).orElseThrow();
        d.setStatut("EN_ATTENTE_DECISION_PRMP");
        dossierRepository.save(d);
        rectifier(corps("300", "Marche 7001 cycle 2", false)).andExpect(status().isOk());
        mvc.perform(get(VERSIONS).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[1].numero").value(2))
                .andExpect(jsonPath("$[1].cycle").value(2));
        String v2 = mvc.perform(get(VERSIONS + "/2").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].designationMarche").value("Marche 7001 apres reprise"))
                .andReturn().getResponse().getContentAsString();
        assertThat(montant(v2, "$.lignes[0].montEstim")).isEqualByComparingTo("200");
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions rectifier(String corps) throws Exception {
        return mvc.perform(put("/api/saisies/ppm/" + DOSSIER).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    /** Corps d'un PUT de rectification (structure figée : la ligne 7001, mise à jour par idDetail). */
    private static String corps(String montEstim, String designation, boolean avecEnfants) {
        String enfants = !avecEnfants ? "" : ",\"beneficiaires\":[{\"soaCode\":\"SOA-A\",\"ancMontBenef\":" + montEstim
                + ",\"nouvMontBenef\":" + montEstim + "}],"
                + "\"lots\":[{\"designationLot\":\"Lot Unique\",\"montLot\":" + montEstim + ",\"qteLot\":2,\"uniteLot\":\"u\"}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-03-01\",\"dateFin\":\"2026-03-15\"}]";
        return "{\"exercice\":2026,\"signataire\":\"PRMP Test\",\"dateSignature\":\"2026-06-01\",\"reference\":\"PPM-700\","
                + "\"marches\":[{\"idDetail\":" + LIGNE + ",\"formeMarche\":\"QUANTITE_FIXE\",\"montEstim\":" + montEstim
                + ",\"idNature\":1,\"statut\":\"PREVU\",\"designationMarche\":\"" + designation + "\"" + enfants + "}]}";
    }

    private static BigDecimal montant(String json, String chemin) {
        Object v = JsonPath.read(json, chemin);
        return new BigDecimal(String.valueOf(v));
    }

    /**
     * Exécute un UPDATE sur la connexion de la transaction de test et exige son refus par le trigger. Un
     * ordre refusé par PostgreSQL abandonne la transaction courante : on l'exécute sous un
     * <em>savepoint</em>, relâché ensuite, pour que le test puisse continuer à lire.
     */
    private void exigerUpdateRefuse(String sql) throws SQLException {
        Connection cnx = DataSourceUtils.getConnection(dataSource);
        Savepoint sp = cnx.setSavepoint();
        try (Statement st = cnx.createStatement()) {
            int n = st.executeUpdate(sql);
            fail("l'UPDATE aurait dû être refusé par le trigger (lignes touchées : " + n + ") : " + sql);
        } catch (SQLException e) {
            assertThat(e.getMessage()).contains("immuable");
        } finally {
            cnx.rollback(sp);
            DataSourceUtils.releaseConnection(cnx, dataSource);
        }
    }
}
