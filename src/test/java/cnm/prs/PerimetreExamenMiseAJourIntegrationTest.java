package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.ChangementLigne;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.PorteePointCtrl;

/**
 * ⚠️ <strong>Examen d'une mise à jour : n'examiner que les lignes changées</strong> (demande pilote du
 * 2026-09-10).
 *
 * <p>L'examen d'une version de PPM réclamait, comme celui d'un plan initial, l'évaluation de chaque
 * point de portée LIGNE sur <em>chaque</em> marché. Sur une version qui corrige trois lignes d'un plan
 * qui en compte soixante, la Commission réexaminait l'intégralité d'un plan qu'elle avait déjà validé —
 * et le PV rendait compte de tout, noyant les corrections dans l'inchangé.</p>
 *
 * <p>Ce que ces tests protègent : le <strong>périmètre servi</strong> (le type de changement de chaque
 * ligne et ce qu'il implique), la <strong>complétude réduite</strong> qui s'y conforme, le
 * <strong>constat</strong> exigé sur chaque ligne retirée et porté par son propre {@code idDetail}, la
 * <strong>FICHE et l'AGPM</strong> qui ne se rouvrent que si une ligne qui les concerne a bougé, et la
 * <strong>non-régression</strong> d'un dossier initial, qui ne change en rien.</p>
 */
class PerimetreExamenMiseAJourIntegrationTest extends CnmIntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.ChangementLigneRepository changementLigneRepository;

    private static final int PARENT = 640;
    private static final int VERSION = 641;
    /** Points de la grille DDP, créés ici : le référentiel des tests est vide (cf. seeder gardé). */
    private static final int POINT_LIGNE = 4201;
    private static final int POINT_DOSSIER = 4202;
    private static final int POINT_CONSTAT = 4203;

    /** Lignes de la version : reprises du parent par {@code idLigneOrigine}. */
    private static final int L_INCHANGEE = 6411;
    private static final int L_MODIFIEE = 6412;
    private static final int L_RETIREE = 6413;
    private static final int L_NOUVELLE = 6414;

    @BeforeEach
    void planParentEtVersion() {
        creerPoint(POINT_LIGNE, "Objet du marché", PorteePointCtrl.LIGNE);
        creerPoint(POINT_DOSSIER, "Absence de fractionnement", PorteePointCtrl.DOSSIER);
        creerPoint(POINT_CONSTAT, "Constat de suppression de la ligne", PorteePointCtrl.SUPPRESSION);

        // Le plan déjà examiné : trois lignes.
        dossier(PARENT, "CLOTURE", null);
        ligne(PARENT, 6401, 6401, "Fournitures de bureau", "100", false);
        ligne(PARENT, 6402, 6402, "Réfection toiture", "200", false);
        ligne(PARENT, 6403, 6403, "Matériel informatique", "300", false);

        // La version : une ligne à l'identique, une corrigée, une retirée, une ajoutée.
        dossier(VERSION, "DISPATCHE", PARENT);
        ligne(VERSION, L_INCHANGEE, 6401, "Fournitures de bureau", "100", false);
        ligne(VERSION, L_MODIFIEE, 6402, "Réfection toiture", "250", false);
        ligne(VERSION, L_RETIREE, 6403, "Matériel informatique", "300", true);
        ligne(VERSION, L_NOUVELLE, L_NOUVELLE, "Climatisation", "400", false);

        // ⚠️ La TRACE figée à la soumission de la version : c'est elle qui fait foi, et la seule qui
        // existe au moment de l'examen. Le périmètre la lit plutôt que de refaire la comparaison —
        // deux dérivations de « cette ligne a changé » auraient fini par se contredire.
        trace(6401, "INCHANGEE", "Fournitures de bureau");
        trace(6402, "MODIFIEE", "Réfection toiture");
        trace(6403, "SUPPRIMEE", "Matériel informatique");
        trace(L_NOUVELLE, "NOUVELLE", "Climatisation");

        // Le circuit : la version est dispatchée au Membre, qui l'examine.
        receptionRepository.save(reception(VERSION, VERSION, "CTRCC1", true));
        dispatchRepository.save(dispatch(VERSION, VERSION, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(VERSION, VERSION, "CTRMEM"));
    }

    /** Une ligne de la trace figée, telle que la soumission de la version l'écrit. */
    private void trace(int idLigneOrigine, String type, String designation) {
        ChangementLigne c = new ChangementLigne();
        c.setIdChangement(changementLigneRepository.nextIdChangement().intValue());
        c.setIdDossier(VERSION);
        c.setIdLigneOrigine(idLigneOrigine);
        c.setTypeChangement(type);
        c.setDesignation(designation);
        changementLigneRepository.save(c);
    }

    private void creerPoint(int id, String libelle, PorteePointCtrl portee) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id);
        p.setLibelPointCtrl(libelle);
        p.setObligatoire(true);
        p.setIdTypeDossier("DDP");
        p.setPortee(portee);
        p.setOrdrePointCtrl(id);
        pointsCtrlRepository.save(p);
    }

    private void dossier(int id, String statut, Integer parent) {
        Dossier d = dossierLoc(id, statut, "ANT", "PRMP001");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM");
        d.setIdDossierParent(parent);
        dossierRepository.save(d);
        ppmRepository.save(ppm(id, id, "PRMP001"));
    }

    /** Une ligne du plan ; {@code origine} porte l'identité inter-versions, {@code retiree} le retrait. */
    private void ligne(int idDossier, int idDetail, int origine, String designation, String montant,
            boolean retiree) {
        Marche m = marche(idDetail, idDossier, idDossier);
        m.setIdLigneOrigine(origine);
        m.setDesignationMarche(designation);
        m.setMontEstim(new BigDecimal(montant));
        m.setSupprimee(retiree);
        marcheRepository.save(m);
    }

    /** Pose une évaluation conforme d'un point sur une ligne ({@code idDetail} nul = point du dossier). */
    private void evaluer(int idDetailExamen, Integer idDetail, int idPoint) throws Exception {
        String ligne = idDetail == null ? "" : ",\"idDetail\":" + idDetail;
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":" + idDetailExamen + ",\"idExamen\":" + VERSION
                        + ",\"idPtControle\":" + idPoint + ",\"conforme\":true" + ligne + "}"))
                .andExpect(status().isCreated());
    }

    private String soumettre(org.springframework.test.web.servlet.ResultMatcher attendu) throws Exception {
        return soumettre(attendu, "FAV");
    }

    /** Avis explicite : un examen qui porte une observation ne peut pas être FAVORABLE sans réserve. */
    private String soumettre(org.springframework.test.web.servlet.ResultMatcher attendu, String avis)
            throws Exception {
        return mvc.perform(post("/api/examens/" + VERSION + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"" + avis + "\"}"))
                .andExpect(attendu)
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ 1. le périmètre servi

    @Test
    @DisplayName("Le périmètre annonce le type de changement de CHAQUE ligne et ce qu'il implique : "
            + "l'inchangée sort, la retirée n'attend qu'un constat, les autres sont à examiner")
    void perimetre_servi_ligneAligne() throws Exception {
        mvc.perform(get("/api/dossiers/" + VERSION + "/perimetre-examen").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miseAJour").value(true))
                .andExpect(jsonPath("$.idDossierParent").value(PARENT))
                .andExpect(jsonPath("$.dossierAExaminer").value(true))
                .andExpect(jsonPath("$.lignes", hasSize(4)))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_INCHANGEE + ")].typeChangement",
                        hasItem("INCHANGEE")))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_INCHANGEE + ")].aExaminer", hasItem(false)))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_INCHANGEE + ")].constatRequis", hasItem(false)))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_MODIFIEE + ")].typeChangement",
                        hasItem("MODIFIEE")))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_MODIFIEE + ")].aExaminer", hasItem(true)))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_NOUVELLE + ")].typeChangement",
                        hasItem("NOUVELLE")))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_NOUVELLE + ")].aExaminer", hasItem(true)))
                // La ligne retirée : pas « à examiner », mais elle n'est pas oubliée pour autant.
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_RETIREE + ")].typeChangement",
                        hasItem("SUPPRIMEE")))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_RETIREE + ")].aExaminer", hasItem(false)))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==" + L_RETIREE + ")].constatRequis", hasItem(true)));
    }

    @Test
    @DisplayName("Un dossier INITIAL n'est pas concerné : miseAJour=false et tout son plan est à examiner "
            + "— la règle ne se remarque que là où elle s'applique")
    void dossierInitial_perimetreComplet() throws Exception {
        mvc.perform(get("/api/dossiers/" + PARENT + "/perimetre-examen").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miseAJour").value(false))
                .andExpect(jsonPath("$.idDossierParent").doesNotExist())
                .andExpect(jsonPath("$.lignes", hasSize(3)))
                .andExpect(jsonPath("$.lignes[?(@.aExaminer==true)]", hasSize(3)))
                .andExpect(jsonPath("$.lignes[?(@.constatRequis==true)]", hasSize(0)))
                .andExpect(jsonPath("$.lignes[0].typeChangement").doesNotExist())
                .andExpect(jsonPath("$.lignes[1].typeChangement").doesNotExist())
                .andExpect(jsonPath("$.lignes[2].typeChangement").doesNotExist());
    }

    // ------------------------------------------------------------------ 2. la complétude s'y conforme

    @Test
    @DisplayName("Complétude RÉDUITE — évaluer les deux lignes changées, le constat de la retirée et le "
            + "point du dossier SUFFIT : l'inchangée n'est jamais réclamée")
    void completude_reduiteAuxLignesChangees() throws Exception {
        evaluer(7001, L_MODIFIEE, POINT_LIGNE);
        evaluer(7002, L_NOUVELLE, POINT_LIGNE);
        evaluer(7003, L_RETIREE, POINT_CONSTAT);
        evaluer(7004, null, POINT_DOSSIER);

        soumettre(status().isCreated());
    }

    @Test
    @DisplayName("Complétude — une ligne CHANGÉE non évaluée bloque toujours : le périmètre réduit ce qui "
            + "est exigé, il ne dispense de rien")
    void completude_ligneChangeeManquante_refusee() throws Exception {
        evaluer(7011, L_MODIFIEE, POINT_LIGNE);
        evaluer(7012, L_RETIREE, POINT_CONSTAT);
        evaluer(7013, null, POINT_DOSSIER);
        // Il manque la ligne NOUVELLE.
        String erreur = soumettre(status().isBadRequest());
        assertThat(erreur).contains("Climatisation");
        assertThat(erreur).doesNotContain("Fournitures de bureau");   // l'inchangée n'est pas réclamée
    }

    @Test
    @DisplayName("⚠️ Le CONSTAT de la ligne retirée est exigé, et porté par l'idDetail de la ligne "
            + "elle-même : sans lui, la soumission est refusée en nommant la ligne")
    void constatDeSuppression_exige() throws Exception {
        evaluer(7021, L_MODIFIEE, POINT_LIGNE);
        evaluer(7022, L_NOUVELLE, POINT_LIGNE);
        evaluer(7023, null, POINT_DOSSIER);
        // Il manque le constat de la ligne retirée.
        String erreur = soumettre(status().isBadRequest());
        assertThat(erreur).contains("Matériel informatique");
        assertThat(erreur).contains("ligne retirée");

        evaluer(7024, L_RETIREE, POINT_CONSTAT);
        soumettre(status().isCreated());
    }

    @Test
    @DisplayName("Le constat ne s'applique QU'aux lignes retirées — les points du plan ne sont jamais "
            + "réclamés sur elles, et le constat jamais sur les autres")
    void constat_etPointsDuPlan_sExcluent() throws Exception {
        evaluer(7031, L_MODIFIEE, POINT_LIGNE);
        evaluer(7032, L_NOUVELLE, POINT_LIGNE);
        evaluer(7033, L_RETIREE, POINT_CONSTAT);
        evaluer(7034, null, POINT_DOSSIER);

        // Aucun point LIGNE n'est réclamé sur la ligne retirée, aucun constat sur les lignes du plan :
        // les deux ensembles sont disjoints, et la soumission passe sans rien de plus.
        soumettre(status().isCreated());
    }

    // ------------------------------------------------------------------ 3. le PV suit le périmètre

    @Test
    @DisplayName("Le PV ne rapporte pas ce qui est hors périmètre — une observation posée sur une ligne "
            + "INCHANGÉE reste en base mais ne sort pas dans le document")
    void pv_neRapportePasLesLignesInchangees() throws Exception {
        // L'examinateur statue tout le périmètre, et pose EN PLUS une non-conformité sur l'inchangée.
        evaluer(7041, L_MODIFIEE, POINT_LIGNE);
        evaluer(7042, L_NOUVELLE, POINT_LIGNE);
        evaluer(7043, L_RETIREE, POINT_CONSTAT);
        evaluer(7044, null, POINT_DOSSIER);
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":7045,\"idExamen\":" + VERSION + ",\"idPtControle\":" + POINT_LIGNE
                        + ",\"conforme\":false,\"idDetail\":" + L_INCHANGEE
                        + ",\"observations\":[{\"ordre\":1,\"auLieuDe\":\"x\",\"lire\":\"y\"}]}"))
                .andExpect(status().isCreated());

        // Elle est acceptée — l'exigence tombe, pas la possibilité de statuer (règle du 2026-09-04) —
        // et la soumission passe : rien n'est réclamé sur l'inchangée.
        soumettre(status().isCreated(), "FAVR");
        assertThat(examenDetailRepository.findByIdExamen(VERSION))
                .as("l'évaluation hors périmètre n'est pas effacée, elle ne sort que du document")
                .anyMatch(d -> Integer.valueOf(L_INCHANGEE).equals(d.getIdDetail()));
    }

    // ------------------------------------------------------------------ 4. la fiche et l'AGPM

    @Test
    @DisplayName("La FICHE ne se rouvre pas si aucune ligne qui la concerne n'a changé — et « on ne "
            + "contrôle pas le vide » reste la première borne")
    void fiche_nonRouverteSiRienDeConcerneNaChange() throws Exception {
        creerPoint(4204, "Listes de la fiche cohérentes avec le plan", PorteePointCtrl.FICHE);
        // Aucune ligne de la version n'alimente la fiche (ni dérogatoire, ni contrat-cadre, ni délai
        // aménagé) : le document est vide, il n'est pas exigé — la règle du 2026-09-04 tient en amont.
        mvc.perform(get("/api/dossiers/" + VERSION + "/perimetre-examen").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.ficheAExaminer").value(false))
                .andExpect(jsonPath("$.agpmAExaminer").value(false));

        evaluer(7051, L_MODIFIEE, POINT_LIGNE);
        evaluer(7052, L_NOUVELLE, POINT_LIGNE);
        evaluer(7053, L_RETIREE, POINT_CONSTAT);
        evaluer(7054, null, POINT_DOSSIER);
        soumettre(status().isCreated());
    }

    @Test
    @DisplayName("Message de refus — il nomme la ligne retirée comme les autres : un examinateur doit "
            + "savoir ce qu'on attend de lui, pas seulement qu'il manque quelque chose")
    void messageDeRefus_nommeCeQuiManque() throws Exception {
        String erreur = soumettre(status().isBadRequest());
        mvc.perform(get("/api/dossiers/" + VERSION + "/perimetre-examen").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        assertThat(erreur).contains("Examen incomplet");
        assertThat(erreur).contains("Réfection toiture");
        assertThat(erreur).contains("Climatisation");
        assertThat(erreur).contains("Matériel informatique");
        // L'inchangée n'y figure jamais : elle n'est pas au périmètre.
        assertThat(erreur).doesNotContain("Fournitures de bureau");
    }

    @Test
    @DisplayName("Le périmètre est lisible par le CC et le Président — le diff leur était déjà ouvert, "
            + "mais c'est le périmètre qui dit ce que la garde exigera")
    void perimetre_lisibleParLeCircuit() throws Exception {
        for (String jeton : new String[] { tokenCc, tokenPresident, tokenPrmp }) {
            mvc.perform(get("/api/dossiers/" + VERSION + "/perimetre-examen").header("Authorization", jeton))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.miseAJour").value(true));
        }
        mvc.perform(get("/api/dossiers/" + VERSION + "/perimetre-examen"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Dossier introuvable → 404, comme toute lecture de dossier")
    void perimetre_dossierInconnu() throws Exception {
        mvc.perform(get("/api/dossiers/999888/perimetre-examen").header("Authorization", tokenMembre))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("introuvable")));
    }
}
