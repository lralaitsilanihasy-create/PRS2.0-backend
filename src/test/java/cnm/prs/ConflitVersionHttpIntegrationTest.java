package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.PvExamen;
import cnm.prs.exception.ConflitVersionException;

/**
 * Tests HTTP permanents du chantier « conflit de version » (cf. {@code docs/plan-conflit-version.md},
 * tâche Q1) : le contrat §3/§4 vérifié bout en bout par MockMvc, sur deux ressources représentatives
 * (PPM et PV d'examen — les deux endpoints suggérés par le plan). La classe temporaire du développeur
 * backend a été supprimée ; ceci en est le remplacement permanent.
 *
 * <p>Pour chacune des deux ressources, les quatre cas du contrat :</p>
 * <ul>
 *   <li>GET rend le champ {@code version} ;</li>
 *   <li>PUT avec la version courante → 200, réponse portant la version <strong>incrémentée</strong> ;</li>
 *   <li>PUT avec une version périmée → 409 {@code CONFLIT_VERSION} (message et {@code path} exacts),
 *       la donnée en base n'est <strong>pas</strong> écrasée ;</li>
 *   <li>PUT sans {@code version} → 200 (compatibilité ascendante, comportement historique).</li>
 * </ul>
 *
 * <p>Complété par un contrôle de non-régression sur les gardes {@code @PreAuthorize} existantes (le
 * chantier ne les touche pas) : un profil qui n'a pas le droit d'éditer la ressource reste rejeté (403).</p>
 */
class ConflitVersionHttpIntegrationTest extends CnmIntegrationTestSupport {

    // ------------------------------------------------------------------
    // PPM — PUT /api/ppms/{id} : PRMP, UGPM, ADMINISTRATEUR (plan §4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/ppms/{id} rend le champ version")
    void ppm_get_rendVersion() throws Exception {
        int id = creerPpmModifiable(300);

        mvc.perform(get("/api/ppms/" + id).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("PUT /api/ppms/{id} avec la version courante -> 200, version incrémentée dans la réponse")
    void ppm_put_versionCourante_incrementeLaVersion() throws Exception {
        int id = creerPpmModifiable(301);

        mvc.perform(put("/api/ppms/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPpm(id, "Signataire V1", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signataire").value("Signataire V1"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/ppms/{id} avec une version périmée -> 409 CONFLIT_VERSION, donnée non écrasée")
    void ppm_put_versionPerimee_rendConflit409_donneeNonEcrasee() throws Exception {
        int id = creerPpmModifiable(302);

        // Premier PUT, version courante (0) : accepté, la version passe à 1.
        mvc.perform(put("/api/ppms/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPpm(id, "Signataire retenu", 0)))
                .andExpect(status().isOk());

        // Second PUT, encore avec version=0 (désormais périmée) : refusé avant toute écriture.
        mvc.perform(put("/api/ppms/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPpm(id, "Signataire qui ne doit JAMAIS apparaître", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ConflitVersionException.CODE))
                .andExpect(jsonPath("$.message").value(ConflitVersionException.MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/ppms/" + id));

        // La donnée en base est celle du PUT accepté, pas celle du PUT en conflit.
        mvc.perform(get("/api/ppms/" + id).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signataire").value("Signataire retenu"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/ppms/{id} sans version -> 200 (compatibilité ascendante, comportement historique)")
    void ppm_put_sansVersion_ecraseSansControle() throws Exception {
        int id = creerPpmModifiable(303);

        mvc.perform(put("/api/ppms/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPpm(id, "Signataire sans version", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signataire").value("Signataire sans version"));
    }

    @Test
    @DisplayName("PUT /api/ppms/{id} par un profil non autorisé (MEMBRE) reste refusé (403) — garde inchangée par ce chantier")
    void ppm_put_profilNonAutorise_refuse403() throws Exception {
        int id = creerPpmModifiable(304);

        mvc.perform(put("/api/ppms/" + id).header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPpm(id, "Ne doit pas passer", 0)))
                .andExpect(status().isForbidden());
    }

    /** Dossier BROUILLON (famille DDP) + PPM rattaché, propriété de PRMP001 — modifiable par tokenPrmp. */
    private int creerPpmModifiable(int id) {
        Dossier d = dossier(id, "BROUILLON");
        d.setIdLocalite("ANT");
        d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(id, id, "PRMP001"));
        return id;
    }

    private String corpsPpm(int idDossier, String signataire, Integer version) {
        String champVersion = version == null ? "" : ",\"version\":" + version;
        // idPrmp ré-envoyé (comme un client qui repart de l'objet chargé) : le PUT écrase le champ avec
        // ce que porte le corps (PpmService#update n'a pas de garde de propriété dédiée), un corps qui
        // l'omettrait viderait l'identité PRMP du PPM et casserait la visibilité sur les lectures suivantes.
        return "{\"idDossier\":" + idDossier + ",\"exercice\":2026,\"signataire\":\"" + signataire
                + "\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-REF-" + idDossier
                + "\",\"idPrmp\":\"PRMP001\"" + champVersion + "}";
    }

    // ------------------------------------------------------------------
    // PV d'examen — PUT /api/pv-examens/{id} : MEMBRE (@perm.peutExercer) (plan §4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/pv-examens/{id} rend le champ version")
    void pvExamen_get_rendVersion() throws Exception {
        int id = creerPvBrouillon(400);

        mvc.perform(get("/api/pv-examens/" + id).header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("PUT /api/pv-examens/{id} avec la version courante -> 200, version incrémentée dans la réponse")
    void pvExamen_put_versionCourante_incrementeLaVersion() throws Exception {
        int id = creerPvBrouillon(401);

        mvc.perform(put("/api/pv-examens/" + id).header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPvExamen("Synthèse V1", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syntheseObservations").value("Synthèse V1"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/pv-examens/{id} avec une version périmée -> 409 CONFLIT_VERSION, donnée non écrasée")
    void pvExamen_put_versionPerimee_rendConflit409_donneeNonEcrasee() throws Exception {
        int id = creerPvBrouillon(402);

        mvc.perform(put("/api/pv-examens/" + id).header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPvExamen("Synthèse retenue", 0)))
                .andExpect(status().isOk());

        mvc.perform(put("/api/pv-examens/" + id).header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPvExamen("Synthèse qui ne doit JAMAIS apparaître", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ConflitVersionException.CODE))
                .andExpect(jsonPath("$.message").value(ConflitVersionException.MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/pv-examens/" + id));

        mvc.perform(get("/api/pv-examens/" + id).header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syntheseObservations").value("Synthèse retenue"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/pv-examens/{id} sans version -> 200 (compatibilité ascendante, comportement historique)")
    void pvExamen_put_sansVersion_ecraseSansControle() throws Exception {
        int id = creerPvBrouillon(403);

        mvc.perform(put("/api/pv-examens/" + id).header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPvExamen("Synthèse sans version", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syntheseObservations").value("Synthèse sans version"));
    }

    @Test
    @DisplayName("PUT /api/pv-examens/{id} par un profil non autorisé (PRMP) reste refusé (403) — garde inchangée par ce chantier")
    void pvExamen_put_profilNonAutorise_refuse403() throws Exception {
        int id = creerPvBrouillon(404);

        mvc.perform(put("/api/pv-examens/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsPvExamen("Ne doit pas passer", 0)))
                .andExpect(status().isForbidden());
    }

    /** PV BROUILLON sur l'examen 1 (dispatch CTRMEM, localité ANT), tel que seedé par CnmIntegrationTestSupport. */
    private int creerPvBrouillon(int id) {
        PvExamen pv = new PvExamen();
        pv.setIdPv(id);
        pv.setIdExamen(1);
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("BROUILLON");
        pv.setNbNavettes(0);
        pvExamenRepository.save(pv);
        return id;
    }

    private String corpsPvExamen(String syntheseObservations, Integer version) {
        String champVersion = version == null ? "" : ",\"version\":" + version;
        return "{\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\",\"statutPv\":\"BROUILLON\",\"nbNavettes\":0,"
                + "\"syntheseObservations\":\"" + syntheseObservations + "\"" + champVersion + "}";
    }

    // ------------------------------------------------------------------
    // Dossier — PUT /api/dossiers/{id} : ADMINISTRATEUR seul (plan §4)
    // ⚠️ Audit 2026-08-27 (lot C, volet 3) — ressource manquante du contrat (5 promises, 2 testées).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/dossiers/{id} rend le champ version")
    void dossier_get_rendVersion() throws Exception {
        int id = creerDossierModifiable(500);

        mvc.perform(get("/api/dossiers/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("PUT /api/dossiers/{id} avec la version courante -> 200, version incrémentée dans la réponse")
    void dossier_put_versionCourante_incrementeLaVersion() throws Exception {
        int id = creerDossierModifiable(501);

        mvc.perform(put("/api/dossiers/" + id).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDossier("BROUILLON", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/dossiers/{id} avec une version périmée -> 409 CONFLIT_VERSION, donnée non écrasée")
    void dossier_put_versionPerimee_rendConflit409_donneeNonEcrasee() throws Exception {
        int id = creerDossierModifiable(502);

        mvc.perform(put("/api/dossiers/" + id).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDossier("SOUMIS", 0)))
                .andExpect(status().isOk());

        mvc.perform(put("/api/dossiers/" + id).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDossier("CLOTURE", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ConflitVersionException.CODE))
                .andExpect(jsonPath("$.message").value(ConflitVersionException.MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/dossiers/" + id));

        mvc.perform(get("/api/dossiers/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/dossiers/{id} sans version -> 200 (compatibilité ascendante, comportement historique)")
    void dossier_put_sansVersion_ecraseSansControle() throws Exception {
        int id = creerDossierModifiable(503);

        mvc.perform(put("/api/dossiers/" + id).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDossier("SOUMIS", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
    }

    @Test
    @DisplayName("PUT /api/dossiers/{id} par un profil non autorisé (PRMP) reste refusé (403) — garde inchangée par ce chantier")
    void dossier_put_profilNonAutorise_refuse403() throws Exception {
        int id = creerDossierModifiable(504);

        mvc.perform(put("/api/dossiers/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDossier("SOUMIS", 0)))
                .andExpect(status().isForbidden());
    }

    /** Dossier BROUILLON minimal (famille DDP), sans PRMP/localité — l'Admin n'a pas de garde de propriété. */
    private int creerDossierModifiable(int id) {
        dossierRepository.save(dossier(id, "BROUILLON"));
        return id;
    }

    private String corpsDossier(String statut, Integer version) {
        String champVersion = version == null ? "" : ",\"version\":" + version;
        return "{\"idTypeDossier\":\"DDP\",\"statut\":\"" + statut + "\"" + champVersion + "}";
    }

    // ------------------------------------------------------------------
    // Marche — PUT /api/marches/{id} : PRMP/UGPM propriétaire, dossier BROUILLON (plan §4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/marches/{id} rend le champ version")
    void marche_get_rendVersion() throws Exception {
        int id = creerMarcheModifiable(600);

        mvc.perform(get("/api/marches/" + id).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("PUT /api/marches/{id} avec la version courante -> 200, version incrémentée dans la réponse")
    void marche_put_versionCourante_incrementeLaVersion() throws Exception {
        int id = creerMarcheModifiable(601);

        mvc.perform(put("/api/marches/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsMarche(id, "Marche V1", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designationMarche").value("Marche V1"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/marches/{id} avec une version périmée -> 409 CONFLIT_VERSION, donnée non écrasée")
    void marche_put_versionPerimee_rendConflit409_donneeNonEcrasee() throws Exception {
        int id = creerMarcheModifiable(602);

        mvc.perform(put("/api/marches/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsMarche(id, "Marche retenue", 0)))
                .andExpect(status().isOk());

        mvc.perform(put("/api/marches/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsMarche(id, "Marche qui ne doit JAMAIS apparaître", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ConflitVersionException.CODE))
                .andExpect(jsonPath("$.message").value(ConflitVersionException.MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/marches/" + id));

        mvc.perform(get("/api/marches/" + id).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designationMarche").value("Marche retenue"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/marches/{id} sans version -> 200 (compatibilité ascendante, comportement historique)")
    void marche_put_sansVersion_ecraseSansControle() throws Exception {
        int id = creerMarcheModifiable(603);

        mvc.perform(put("/api/marches/" + id).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsMarche(id, "Marche sans version", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designationMarche").value("Marche sans version"));
    }

    @Test
    @DisplayName("PUT /api/marches/{id} par un profil non autorisé (MEMBRE) reste refusé (403) — garde inchangée par ce chantier")
    void marche_put_profilNonAutorise_refuse403() throws Exception {
        int id = creerMarcheModifiable(604);

        mvc.perform(put("/api/marches/" + id).header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsMarche(id, "Ne doit pas passer", 0)))
                .andExpect(status().isForbidden());
    }

    /** Dossier BROUILLON (famille DDP) + PPM + Marche rattachés, propriété de PRMP001 — modifiable par tokenPrmp. */
    private int creerMarcheModifiable(int id) {
        Dossier d = dossier(id, "BROUILLON");
        d.setIdLocalite("ANT");
        d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(id, id, "PRMP001"));
        Marche m = marche(id, id, id);
        marcheRepository.save(m);
        return id;
    }

    private String corpsMarche(int idDossier, String designation, Integer version) {
        String champVersion = version == null ? "" : ",\"version\":" + version;
        return "{\"idDossier\":" + idDossier + ",\"idPpm\":" + idDossier + ",\"designationMarche\":\"" + designation
                + "\"" + champVersion + "}";
    }

    // ------------------------------------------------------------------
    // Lettre de renvoi — PUT /api/lettre-renvois/{id} : Chef de commission, lettre BROUILLON (plan §4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/lettre-renvois/{id} rend le champ version")
    void lettreRenvoi_get_rendVersion() throws Exception {
        int id = creerLettreBrouillon();

        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("PUT /api/lettre-renvois/{id} avec la version courante -> 200, version incrémentée dans la réponse")
    void lettreRenvoi_put_versionCourante_incrementeLaVersion() throws Exception {
        int id = creerLettreBrouillon();

        mvc.perform(put("/api/lettre-renvois/" + id).header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLettreRenvoi("Corps V1", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corpsLettre").value("Corps V1"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/lettre-renvois/{id} avec une version périmée -> 409 CONFLIT_VERSION, donnée non écrasée")
    void lettreRenvoi_put_versionPerimee_rendConflit409_donneeNonEcrasee() throws Exception {
        int id = creerLettreBrouillon();

        mvc.perform(put("/api/lettre-renvois/" + id).header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLettreRenvoi("Corps retenu", 0)))
                .andExpect(status().isOk());

        mvc.perform(put("/api/lettre-renvois/" + id).header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLettreRenvoi("Corps qui ne doit JAMAIS apparaître", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ConflitVersionException.CODE))
                .andExpect(jsonPath("$.message").value(ConflitVersionException.MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/lettre-renvois/" + id));

        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corpsLettre").value("Corps retenu"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PUT /api/lettre-renvois/{id} sans version -> 200 (compatibilité ascendante, comportement historique)")
    void lettreRenvoi_put_sansVersion_ecraseSansControle() throws Exception {
        int id = creerLettreBrouillon();

        mvc.perform(put("/api/lettre-renvois/" + id).header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLettreRenvoi("Corps sans version", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corpsLettre").value("Corps sans version"));
    }

    @Test
    @DisplayName("PUT /api/lettre-renvois/{id} par un profil non autorisé (MEMBRE) reste refusé (403) — garde inchangée par ce chantier")
    void lettreRenvoi_put_profilNonAutorise_refuse403() throws Exception {
        int id = creerLettreBrouillon();

        mvc.perform(put("/api/lettre-renvois/" + id).header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLettreRenvoi("Ne doit pas passer", 0)))
                .andExpect(status().isForbidden());
    }

    /** Lettre BROUILLON créée par HTTP (CC de la localité ANT, examen 1 du dossier 1 — seed standard). */
    private int creerLettreBrouillon() throws Exception {
        String rep = mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString();
        return Integer.parseInt(rep.replaceAll(".*\"idLettre\":(\\d+).*", "$1"));
    }

    private String corpsLettreRenvoi(String corpsLettre, Integer version) {
        String champVersion = version == null ? "" : ",\"version\":" + version;
        return "{\"idExamen\":1,\"corpsLettre\":\"" + corpsLettre + "\"" + champVersion + "}";
    }
}
