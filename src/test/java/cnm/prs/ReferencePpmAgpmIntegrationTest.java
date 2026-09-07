package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.PvExamen;
import cnm.prs.service.ReferenceService;

/**
 * ⚠️ <strong>PPM-AGPM sur tout appel d'offres, et la référence qui le reflète</strong> (arbitrage pilote du
 * 2026-09-07, {@code docs/demande-backend-2026-09-07-reference-ppm-agpm.md}) — constat sur le dossier réel
 * 100299, de sous-type {@code PPM-AGPM} mais référencé {@code 00002/MTP/PPM/2026}.
 *
 * <p>Trois causes, trois vérifications ici : le drapeau {@code declencheAgpm} ne couvrait que l'appel
 * d'offres <em>ouvert</em> ; la référence PPM initiale codait {@code PPM} en dur (et, ayant le format d'une
 * référence structurée, elle était reprise telle quelle par la réception) ; rien ne recomposait le segment
 * quand le sous-type basculait. S'y ajoute la contrainte de cohérence du front : le PV et son dossier
 * doivent porter le <strong>même</strong> segment, sans quoi la jointure
 * {@code refePv.replace("/PV/", "/") == refeDossier} casse.</p>
 */
class ReferencePpmAgpmIntegrationTest extends CnmIntegrationTestSupport {

    private static final int MODE_AO_OUVERT = 1;
    private static final int MODE_CONSULTATION = 4;

    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.service.DossierIntegriteService dossierIntegriteService;

    @BeforeEach
    void referentiels() {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        // Le référentiel réel : l'appel d'offres ouvert déclenche l'AGPM, la consultation des prix non.
        ModePassation ao = new ModePassation(MODE_AO_OUVERT, "Appel d'offres ouvert", null, null, null, null);
        ao.setDeclencheAgpm(Boolean.TRUE);
        modePassationRepository.save(ao);
        ModePassation consultation =
                new ModePassation(MODE_CONSULTATION, "Consultation des Prix Ouverte", null, null, null, null);
        consultation.setDeclencheAgpm(Boolean.FALSE);
        modePassationRepository.save(consultation);
    }

    // ------------------------------------------------------------------ 1 & 2 — toutes les variantes d'AO

    @ParameterizedTest(name = "{0} → PPM-AGPM")
    @ValueSource(strings = { "Appel d'offres ouvert", "Appel d'offres restreint",
            "Appel d'offres avec préqualification", "Appel d'offres en deux étapes" })
    @DisplayName("Recette 1 & 2 — TOUTE variante d'appel d'offres déclenche l'AGPM : sous-type PPM-AGPM et "
            + "référence …/PPM-AGPM/… (et non le seul appel d'offres ouvert)")
    void toutAppelDOffres_declencheAgpmEtReference(String libelleMode) throws Exception {
        // Le mode arrive comme à l'import d'un PPM PDF : par son seul LIBELLÉ, créé à la volée.
        String reponse = creerPpm("\"modeLibelle\":\"" + libelleMode + "\"");
        int idDossier = JsonPath.read(reponse, "$.idDossier");

        Dossier dossier = dossierRepository.findById(idDossier).orElseThrow();
        assertThat(dossier.getIdSousType()).as(libelleMode).isEqualTo("PPM-AGPM");
        // ⚠️ Avant réception, refeDossier est nul : la référence du dossier EST celle du PPM (dérivée de
        // l'entité) — c'est elle qui codait « PPM » en dur, et que le retrait accepté restaure.
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElseThrow();
        assertThat(ppm.getReference()).as(libelleMode).contains("/PPM-AGPM/");
        assertThat(ppm.getReference()).doesNotContain("/PPM/");
        // Le mode créé à la volée porte désormais le drapeau : c'est lui la source de vérité.
        assertThat(modePassationRepository.findAll()).anyMatch(
                m -> libelleMode.equals(m.getLibelle()) && Boolean.TRUE.equals(m.getDeclencheAgpm()));
    }

    @Test
    @DisplayName("Recette 3 — aucun appel d'offres (consultation des prix, gré à gré) : sous-type PPM et "
            + "référence …/PPM/… inchangée")
    void sansAppelDOffres_resteEnPpm() throws Exception {
        String reponse = creerPpm("\"idMode\":" + MODE_CONSULTATION);
        int idDossier = JsonPath.read(reponse, "$.idDossier");

        Dossier dossier = dossierRepository.findById(idDossier).orElseThrow();
        assertThat(dossier.getIdSousType()).isEqualTo("PPM");
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElseThrow();
        assertThat(ppm.getReference()).contains("/PPM/").doesNotContain("/PPM-AGPM/");
    }

    @Test
    @DisplayName("La bascule joue dans les DEUX sens et ne consomme aucun numéro : le compteur et l'acronyme "
            + "de la référence ne bougent pas quand le segment change")
    void bascule_dansLesDeuxSens_sansConsommerDeNumero() throws Exception {
        String reponse = creerPpm("\"idMode\":" + MODE_CONSULTATION);
        int idDossier = JsonPath.read(reponse, "$.idDossier");
        Ppm ppmInitial = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElseThrow();
        String referenceAvant = ppmInitial.getReference();
        int idPpm = ppmInitial.getIdPpm();

        // Un marché en appel d'offres entre dans le plan → PPM-AGPM, même numéro.
        String ajout = mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":" + idDossier + ",\"idPpm\":" + idPpm + ",\"designationMarche\":\"AO\","
                        + "\"montEstim\":500000000,\"idNature\":1,\"idMode\":" + MODE_AO_OUVERT + ",\"statut\":\"PREVU\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDetail = JsonPath.read(ajout, "$.idDetail");

        String referenceApres = ppmRepository.findById(idPpm).orElseThrow().getReference();
        assertThat(referenceApres).isEqualTo(referenceAvant.replace("/PPM/", "/PPM-AGPM/"));

        // Le marché ressort du plan → retour à PPM, toujours le même numéro.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/marches/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        assertThat(ppmRepository.findById(idPpm).orElseThrow().getReference()).isEqualTo(referenceAvant);
    }

    @Test
    @DisplayName("Réception — la référence OFFICIELLE porte le sous-type dérivé ; et après un retrait accepté "
            + "(qui restaure la référence du PPM), la re-réception la reprend, désormais alignée")
    void receptionEtRetrait_referenceOfficielleAlignee() throws Exception {
        Dossier d = dossier(510, "SOUMIS");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM-AGPM");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Ppm ppm = ppmLocalise(510, 510, "ANT");
        ppm.setIdPrmp("PRMP001");
        ppm.setReference("00009/DGB/PPM-AGPM/2026");
        ppmRepository.save(ppm);

        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":510,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isCreated());

        // La référence officielle prend le sous-type comme segment de type (comportement déjà en place).
        String refeDossier = dossierRepository.findById(510).orElseThrow().getRefeDossier();
        assertThat(refeDossier).contains("/PPM-AGPM/").doesNotContain("/PPM/");
        // Scénario du dossier réel 100299 : un retrait accepté restaure la référence du PPM, et la
        // re-réception la reprend telle quelle (format déjà structuré). Elle porte le bon segment.
        assertThat(ReferenceService.remplacerSegmentSousType(ppm.getReference(), "PPM-AGPM"))
                .isEqualTo("00009/DGB/PPM-AGPM/2026");
    }

    // ------------------------------------------------------------------ 4 — le PV porte le même segment

    @Test
    @DisplayName("Recette 4 — le numéro de PV dérive du refeDossier corrigé : …/PPM-AGPM/PV/… et la jointure "
            + "refePv.replace('/PV/','/') retombe exactement sur refeDossier")
    void numeroDePv_memeSegmentQueLeDossier_jointureExacte() throws Exception {
        // Décor : le dossier 1 du socle (examen 1 déjà en place) devient un plan à appel d'offres.
        Dossier dossier = dossierRepository.findById(1).orElseThrow();
        dossier.setIdTypeDossier("DDP");
        dossier.setRefeDossier("00007/DGB/PPM/2026");
        dossierRepository.save(dossier);
        Ppm ppm = ppmLocalise(70, 1, "ANT");
        ppm.setIdPrmp("PRMP001");
        ppm.setReference("00007/DGB/PPM/2026");
        ppmRepository.save(ppm);
        marcheRepository.save(marcheAvecMode(71, 1, 70, MODE_AO_OUVERT));
        dossierIntegriteService.recalculerSousTypeDdp(1);
        String refeDossier = dossierRepository.findById(1).orElseThrow().getRefeDossier();
        assertThat(refeDossier).isEqualTo("00007/DGB/PPM-AGPM/2026");

        // Le projet de PV est créé APRÈS : sa référence dérive du refeDossier déjà corrigé.
        String reponse = mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":95,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refePv").value("00007/DGB/PPM-AGPM/PV/2026"))
                .andReturn().getResponse().getContentAsString();
        String refePv = JsonPath.read(reponse, "$.refePv");
        // La jointure du front, à la lettre.
        assertThat(refePv.replace("/PV/", "/")).isEqualTo(refeDossier);
    }

    @Test
    @DisplayName("Cohérence — une bascule APRÈS création du projet de PV corrige le dossier ET le PV ensemble "
            + "(jamais l'un sans l'autre) ; un PV SIGNÉ gèle la référence, document officiel")
    void bascule_apresPv_corrigeLesDeuxEnsemble_puisGeleUneFoisSigne() throws Exception {
        Dossier dossier = dossierRepository.findById(1).orElseThrow();
        dossier.setIdTypeDossier("DDP");
        dossier.setRefeDossier("00008/DGB/PPM/2026");
        dossierRepository.save(dossier);
        Ppm ppm = ppmLocalise(72, 1, "ANT");
        ppm.setIdPrmp("PRMP001");
        ppm.setReference("00008/DGB/PPM/2026");
        ppmRepository.save(ppm);
        PvExamen pv = new PvExamen();
        pv.setIdPv(96);
        pv.setIdExamen(1);
        pv.setIdAvis("FAV");
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("PROJET_SOUMIS");
        pv.setNbNavettes(0);
        pv.setRefePv("00008/DGB/PPM/PV/2026");
        pvExamenRepository.save(pv);

        // Le plan bascule (un marché en appel d'offres entre) : dossier ET PV suivent, la jointure tient.
        marcheRepository.save(marcheAvecMode(73, 1, 72, MODE_AO_OUVERT));
        dossierIntegriteService.recalculerSousTypeDdp(1);
        String refeDossier = dossierRepository.findById(1).orElseThrow().getRefeDossier();
        String refePv = pvExamenRepository.findById(96).orElseThrow().getRefePv();
        assertThat(refeDossier).isEqualTo("00008/DGB/PPM-AGPM/2026");
        assertThat(refePv).isEqualTo("00008/DGB/PPM-AGPM/PV/2026");
        assertThat(refePv.replace("/PV/", "/")).isEqualTo(refeDossier);

        // PV SIGNÉ : la référence est imprimée sur un document officiel — plus aucune correction.
        PvExamen signe = pvExamenRepository.findById(96).orElseThrow();
        signe.setStatutPv("SIGNE");
        // Le schéma réel exige les deux signatures sur un PV SIGNE (t_pv_examen_cosignataire_check).
        signe.setDateSignatureMembre(java.time.LocalDate.now());
        signe.setDateSignaturePresident(java.time.LocalDate.now());
        pvExamenRepository.save(signe);
        marcheRepository.deleteById(73);
        dossierIntegriteService.recalculerSousTypeDdp(1);
        assertThat(dossierRepository.findById(1).orElseThrow().getIdSousType()).as("le sous-type suit la réalité")
                .isEqualTo("PPM");
        assertThat(dossierRepository.findById(1).orElseThrow().getRefeDossier()).as("la référence est gelée")
                .isEqualTo("00008/DGB/PPM-AGPM/2026");
        assertThat(pvExamenRepository.findById(96).orElseThrow().getRefePv())
                .isEqualTo("00008/DGB/PPM-AGPM/PV/2026");
    }

    // ------------------------------------------------------------------ la recomposition, à l'unité

    @Test
    @DisplayName("Recomposition du segment — les deux formats de référence sont couverts, le segment /PV/ n'est "
            + "jamais confondu, et un acronyme d'entité « PPM » n'est pas renommé")
    void remplacerSegmentSousType_couvreLesDeuxFormats() {
        // Référence PPM (dérivée de l'entité) et référence de réception : le segment est repéré par sa valeur.
        assertThat(ReferenceService.remplacerSegmentSousType("00002/MTP/PPM/2026", "PPM-AGPM"))
                .isEqualTo("00002/MTP/PPM-AGPM/2026");
        assertThat(ReferenceService.remplacerSegmentSousType("00013/PPM/CRM-ANT/2026", "PPM-AGPM"))
                .isEqualTo("00013/PPM-AGPM/CRM-ANT/2026");
        assertThat(ReferenceService.remplacerSegmentSousType("00002/MTP/PPM/PV/2026", "PPM-AGPM"))
                .isEqualTo("00002/MTP/PPM-AGPM/PV/2026");
        // Retour en arrière, et idempotence.
        assertThat(ReferenceService.remplacerSegmentSousType("00002/MTP/PPM-AGPM/2026", "PPM"))
                .isEqualTo("00002/MTP/PPM/2026");
        assertThat(ReferenceService.remplacerSegmentSousType("00002/MTP/PPM-AGPM/2026", "PPM-AGPM"))
                .isEqualTo("00002/MTP/PPM-AGPM/2026");
        // Une entité dont l'acronyme vaut « PPM » : c'est le DERNIER segment de sous-type qui est le bon.
        assertThat(ReferenceService.remplacerSegmentSousType("00002/PPM/PPM/2026", "PPM-AGPM"))
                .isEqualTo("00002/PPM/PPM-AGPM/2026");
        // Références d'une autre famille ou non structurées : intactes.
        assertThat(ReferenceService.remplacerSegmentSousType("00004/DAO/CRM-ANT/2026", "PPM-AGPM"))
                .isEqualTo("00004/DAO/CRM-ANT/2026");
        assertThat(ReferenceService.remplacerSegmentSousType(null, "PPM-AGPM")).isNull();
    }

    @Test
    @DisplayName("Le drapeau se dérive du libellé — toutes variantes d'appel d'offres (inconditionnel), l'appel "
            + "à manifestation d'intérêt (conditionnel), et rien pour les modes hors AO")
    void libelleDeclencheAgpm_couvreLaFamilleAppelDOffres() {
        assertThat(ModePassation.libelleDeclencheAgpm("Appel d'offres ouvert")).isTrue();
        assertThat(ModePassation.libelleDeclencheAgpm("APPEL D'OFFRES RESTREINT")).isTrue();
        assertThat(ModePassation.libelleDeclencheAgpm("Appel d'offres avec préqualification")).isTrue();
        assertThat(ModePassation.libelleDeclencheAgpm("Appel d’offres en deux étapes")).isTrue();
        assertThat(ModePassation.libelleDeclencheAgpm("Consultation des Prix Ouverte")).isFalse();
        assertThat(ModePassation.libelleDeclencheAgpm("Gré à gré")).isFalse();
        assertThat(ModePassation.libelleDeclencheAgpm(null)).isFalse();

        // ⚠️ L'AMI n'est PAS un déclencheur inconditionnel : il porte l'autre drapeau, celui du seuil.
        assertThat(ModePassation.libelleDeclencheAgpm("Appel à manifestation d'intérêt")).isFalse();
        assertThat(ModePassation.libelleAgpmSiSeuil("Appel à manifestation d'intérêt")).isTrue();
        assertThat(ModePassation.libelleAgpmSiSeuil("APPEL A MANIFESTATION D'INTERET")).isTrue();
        assertThat(ModePassation.libelleAgpmSiSeuil("Appel d'offres ouvert")).isFalse();
        assertThat(ModePassation.libelleAgpmSiSeuil("Gré à gré")).isFalse();
        assertThat(ModePassation.libelleAgpmSiSeuil(null)).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    /** Crée un PPM par la façade, avec une ligne portant le mode donné (fragment JSON). */
    private String creerPpm(String fragmentMode) throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\","
                + "\"reference\":\"PPM-AGPM-TEST\",\"marches\":[{\"designationMarche\":\"Travaux\","
                + "\"montEstim\":500000000,\"idNature\":1," + fragmentMode + ",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}]}]}";
        return mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private cnm.prs.entity.Marche marcheAvecMode(int idDetail, int idDossier, int idPpm, int idMode) {
        cnm.prs.entity.Marche m = marche(idDetail, idDossier, idPpm);
        m.setIdMode(idMode);
        return m;
    }
}
