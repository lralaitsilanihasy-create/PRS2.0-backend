package cnm.prs;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import cnm.prs.entity.Avis;
import cnm.prs.entity.Dossier;
import java.util.List;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.EntiteContract;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypePieceJointe;

/**
 * Facade de saisie et d'edition d'un PPM : en-tete, lignes de marche, beneficiaires, lots,
 * processus previsionnels, forme du marche, referentiels resolus ou crees a la volee, PPM-AGPM,
 * et suppression en cascade des brouillons.
 */
class SaisiePpmIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("POST /api/marches : PK idDetail générée serveur (seq_marche) — id client ignoré, deux PRMP → PK distinctes, aucune collision")
    void marche_pkServeur_ignoreClient_pasDeCollisionEntreDeuxPrmp() throws Exception {
        // Référentiels + règle : montEstim 500M / Travaux / ANT → mode 2 (AOR), pour garantir un 201.
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));

        // Deux brouillons PPM, un par PRMP (même localité ANT).
        Dossier d1 = dossier(60, "BROUILLON");
        d1.setIdTypeDossier("DDP"); d1.setIdPrmp("PRMP001"); d1.setIdLocalite("ANT");
        dossierRepository.save(d1);
        ppmRepository.save(ppm(60, 60, "PRMP001"));

        prmpRepository.save(prmp("PRMP002", "ANT"));
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        Dossier d2 = dossier(61, "BROUILLON");
        d2.setIdTypeDossier("DDP"); d2.setIdPrmp("PRMP002"); d2.setIdLocalite("ANT");
        dossierRepository.save(d2);
        ppmRepository.save(ppm(61, 61, "PRMP002"));

        // Les deux PRMP envoient le MÊME idDetail client (99001) — il doit être ignoré des deux côtés.
        String r1 = mvc.perform(post("/api/marches").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":99001,\"idDossier\":60,\"idPpm\":60,\"montEstim\":500000000,"
                        + "\"idNature\":1,\"statut\":\"PREVU\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/marches").header("Authorization", tokenPrmp2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":99001,\"idDossier\":61,\"idPpm\":61,\"montEstim\":500000000,"
                        + "\"idNature\":1,\"statut\":\"PREVU\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int id1 = com.jayway.jsonpath.JsonPath.read(r1, "$.idDetail");
        int id2 = com.jayway.jsonpath.JsonPath.read(r2, "$.idDetail");
        // Réponse : idDetail généré présent, id client (99001) ignoré des deux côtés, PK distinctes (aucune collision).
        org.junit.jupiter.api.Assertions.assertNotEquals(99001, id1);
        org.junit.jupiter.api.Assertions.assertNotEquals(99001, id2);
        org.junit.jupiter.api.Assertions.assertTrue(id1 >= 300001 && id2 >= 300001);
        org.junit.jupiter.api.Assertions.assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("Suppression marché — dossier BROUILLON avec prévisions → 204, marché + prévisions supprimés")
    void marche_delete_brouillonAvecPrevisions_supprime() throws Exception {
        Dossier d = dossier(180, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(280, 180, "PRMP001"));
        marcheRepository.save(marche(380, 180, 280));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        marchePrevisionRepository.save(new MarchePrevision(480, 380, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));
        marchePrevisionRepository.save(new MarchePrevision(481, 380, 1, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 30), null, null));

        mvc.perform(delete("/api/marches/380").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(380));
        org.junit.jupiter.api.Assertions.assertTrue(marchePrevisionRepository.findByIdDetail(380).isEmpty());
    }

    @Test
    @DisplayName("Suppression marché — dossier SOUMIS → 409 (pas un brouillon)")
    void marche_delete_dossierSoumis_409() throws Exception {
        Dossier d = dossier(181, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(281, 181, "PRMP001"));
        marcheRepository.save(marche(381, 181, 281));
        mvc.perform(delete("/api/marches/381").header("Authorization", tokenPrmp)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Suppression PPM — BROUILLON propriétaire avec marchés → 204, cascade marchés + prévisions")
    void ppm_delete_brouillonProprioAvecMarches_cascade() throws Exception {
        Dossier d = dossier(182, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(282, 182, "PRMP001"));
        marcheRepository.save(marche(382, 182, 282));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        marchePrevisionRepository.save(new MarchePrevision(482, 382, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));

        mvc.perform(delete("/api/ppms/282").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(ppmRepository.existsById(282));
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(382));
        org.junit.jupiter.api.Assertions.assertTrue(marchePrevisionRepository.findByIdDetail(382).isEmpty());
    }

    @Test
    @DisplayName("Suppression PPM — non propriétaire → 403")
    void ppm_delete_nonProprietaire_403() throws Exception {
        prmpRepository.save(prmp("PRMP002", "ANT"));
        Dossier d = dossier(183, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP002"); dossierRepository.save(d);
        ppmRepository.save(ppm(283, 183, "PRMP002"));
        mvc.perform(delete("/api/ppms/283").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Suppression PPM — dossier SOUMIS → 409")
    void ppm_delete_dossierSoumis_409() throws Exception {
        Dossier d = dossier(184, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(284, 184, "PRMP001"));
        mvc.perform(delete("/api/ppms/284").header("Authorization", tokenPrmp)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Suppression — portée limitée : autre marché du même PPM et autre PPM de la même PRMP restent intacts")
    void suppression_voisinsIntacts() throws Exception {
        Dossier d = dossier(170, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(200, 170, "PRMP001"));
        ppmRepository.save(ppm(201, 170, "PRMP001"));               // PPM voisin
        marcheRepository.save(marche(300, 170, 200));
        marcheRepository.save(marche(301, 170, 200));               // marché voisin (même PPM)
        marcheRepository.save(marche(302, 170, 201));               // marché du PPM voisin
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        marchePrevisionRepository.save(new MarchePrevision(400, 300, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));
        marchePrevisionRepository.save(new MarchePrevision(401, 301, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));
        marchePrevisionRepository.save(new MarchePrevision(402, 302, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));

        // Supprime le marché 300 → 300 + prévision 400 partis ; 301/401 et 302/402 intacts.
        mvc.perform(delete("/api/marches/300").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(300));
        org.junit.jupiter.api.Assertions.assertTrue(marchePrevisionRepository.findByIdDetail(300).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(marcheRepository.existsById(301));
        org.junit.jupiter.api.Assertions.assertFalse(marchePrevisionRepository.findByIdDetail(301).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(marcheRepository.existsById(302));

        // Supprime le PPM 200 → 200 + marché restant 301 + prévision 401 partis ; PPM 201 + marché 302 + prévision 402 intacts.
        mvc.perform(delete("/api/ppms/200").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(ppmRepository.existsById(200));
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(301));
        org.junit.jupiter.api.Assertions.assertTrue(ppmRepository.existsById(201));
        org.junit.jupiter.api.Assertions.assertTrue(marcheRepository.existsById(302));
        org.junit.jupiter.api.Assertions.assertFalse(marchePrevisionRepository.findByIdDetail(302).isEmpty());
    }

    @Test
    @DisplayName("Façade saisie PPM : dossier BROUILLON + PPM + marché (mode auto), invisible des contrôleurs puis visible après soumission")
    void saisiePpm_facade() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");

        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        // Localité dérivée de l'entité 1 (= ANT) ; AUCUN id (dossier/PPM/marché) dans le corps → alloués serveur.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,"
                + "\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-60\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("DDP"))
                .andExpect(jsonPath("$.idLocalite").value("ANT"))
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"))
                .andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        org.junit.jupiter.api.Assertions.assertTrue(idDoss >= 100001);   // PK serveur (séquence), pas de collision avec les seeds
        // La ligne de marché conserve le mode SAISI (2) — plus de détermination automatique.
        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + ")].idMode", hasItem(2)));
        // Le brouillon est invisible du Secrétaire.
        mvc.perform(get("/api/dossiers/" + idDoss).header("Authorization", tokenSec))
                .andExpect(status().isForbidden());
        // Soumission → SOUMIS → devient visible.
        mvc.perform(post("/api/dossiers/" + idDoss + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SOUMIS"));
        mvc.perform(get("/api/dossiers/" + idDoss).header("Authorization", tokenSec))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Saisie PPM — nature/mode par libellé : résolus (dédup normalisée) ou créés à la volée (tr_nature/tr_mode)")
    void saisiePpm_natureModeALaVolee() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));   // référentiel existant
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        long naturesAvant = natureRepository.count();
        long modesAvant = modePassationRepository.count();

        // Marché A : natureLibelle « TRAVAUX » (≈ existant → résolu, aucun doublon) + modeLibelle « Achat Direct » (créé).
        // Marché B : natureLibelle « Fournitures et services » (créé). Aucun idNature/idMode fourni.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-AV\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"natureLibelle\":\"TRAVAUX\",\"modeLibelle\":\"Achat Direct\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"B\",\"montEstim\":2000000,\"natureLibelle\":\"Fournitures et services\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        // Dédup : « TRAVAUX » ne crée PAS de doublon → +1 nature seulement (Fournitures et services) ; +1 mode (Achat Direct).
        org.junit.jupiter.api.Assertions.assertEquals(naturesAvant + 1, natureRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(modesAvant + 1, modePassationRepository.count());

        // Les marchés portent des ids résolus (A.idNature = Travaux existant = 1 ; A.idMode et B.idNature non nuls).
        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='A')].idNature", hasItem(1)))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='A')].idMode",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='B')].idNature",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));
    }

    @Test
    @DisplayName("Saisie PPM — mode + suffixe de source (RPI/PIP) collé au libellé : résolu au noyau, jamais RPI→PIP, aucun doublon")
    void saisiePpm_modeSuffixeSource() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        modePassationRepository.save(new ModePassation(4, "Consultation des Prix Ouverte", null, null, null, null));
        modePassationRepository.save(new ModePassation(8, "CONSULTATION DE PRIX OUVERTE PIP", null, null, null, null));
        long modesAvant = modePassationRepository.count();   // 3 : aucun ne doit être créé à la volée

        // Trois marchés dont le modeLibelle porte un suffixe de source collé (cas réel PDF/ré-import).
        // CPO-RPI : suffixe RPI (aucune variante RPI) → mode BASE idMode=4 (JAMAIS idMode=8 « … PIP »).
        // CPO-PIP : suffixe PIP (source exacte) → variante distincte idMode=8.
        // AOO-RPI : coquille singulier + RPI → idMode=1 déclencheur d'AGPM, résolu (pas de création sans le drapeau).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-SRC\","
                + "\"marches\":["
                + "{\"designationMarche\":\"CPO-RPI\",\"montEstim\":500000000,\"idNature\":1,\"modeLibelle\":\"CONSULTATION DE PRIX OUVERTE RPI\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"CPO-PIP\",\"montEstim\":400000000,\"idNature\":1,\"modeLibelle\":\"CONSULTATION DE PRIX OUVERTE PIP\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"AOO-RPI\",\"montEstim\":600000000,\"idNature\":1,\"modeLibelle\":\"APPEL D'OFFRE OUVERT RPI\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        // Aucun mode créé à la volée : les trois libellés se résolvent au référentiel existant.
        org.junit.jupiter.api.Assertions.assertEquals(modesAvant, modePassationRepository.count());

        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='CPO-RPI')].idMode", hasItem(4)))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='CPO-PIP')].idMode", hasItem(8)))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='AOO-RPI')].idMode", hasItem(1)));
    }

    @Test
    @DisplayName("Saisie PPM — numCompte absent de tr_compte : créé à la volée (pas de 409 FK sur t_marche.NUM_COMPTE)")
    void saisiePpm_compteALaVolee() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        // numCompte « 9999-NEW » absent de tr_compte → sans résolution-ou-création, l'INSERT du marché viole la FK (409).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-CPT\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":2,\"numCompte\":\"9999-NEW\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // Le compte a été créé à la volée dans tr_compte (résolution, jamais suppression).
        org.junit.jupiter.api.Assertions.assertTrue(compteRepository.existsById("9999-NEW"));
    }

    @Test
    @DisplayName("Saisie PPM — bénéficiaires cohérents : 1 ligne t_service_beneficiaire par élément + soa/compte à la volée")
    void saisiePpm_beneficiaires_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        long benefAvant = serviceBeneficiaireRepository.count();

        // montEstim 3 000 000 = 1 000 000 + 2 000 000 ; soaCode/numCompte inexistants → créés à la volée.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-BEN\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":3000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"00-21-0-J00-00000\",\"numCompte\":\"C-A\",\"ancMontBenef\":1000000},"
                + "{\"soaCode\":\"00-21-0-J00-11111\",\"numCompte\":\"C-B\",\"ancMontBenef\":2000000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        org.junit.jupiter.api.Assertions.assertEquals(benefAvant + 2, serviceBeneficiaireRepository.count());
        org.junit.jupiter.api.Assertions.assertTrue(soaBeneficiaireRepository.existsById("00-21-0-J00-00000"));
        org.junit.jupiter.api.Assertions.assertTrue(soaBeneficiaireRepository.existsById("00-21-0-J00-11111"));
        org.junit.jupiter.api.Assertions.assertTrue(compteRepository.existsById("C-A"));
        org.junit.jupiter.api.Assertions.assertTrue(compteRepository.existsById("C-B"));
    }

    @Test
    @DisplayName("Saisie PPM — bénéficiaires incohérents : Σ ancMontBenef ≠ montEstim → 400 ciblé marches[0].beneficiaires")
    void saisiePpm_beneficiaires_incoherent_400() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        // montEstim 3 000 000 mais Σ = 1 000 000 + 1 500 000 = 2 500 000 → 400.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-KO\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":3000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"S1\",\"numCompte\":\"C1\",\"ancMontBenef\":1000000},"
                + "{\"soaCode\":\"S2\",\"numCompte\":\"C2\",\"ancMontBenef\":1500000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].beneficiaires"));
    }

    @Test
    @DisplayName("Auto-PK : un id envoyé par le client est IGNORÉ ; le serveur attribue depuis la séquence")
    void autopk_idClientIgnore() throws Exception {
        String resp = mvc.perform(post("/api/dossiers").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":777,\"statut\":\"BROUILLON\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        org.junit.jupiter.api.Assertions.assertNotEquals(777, id);          // id client ignoré
        org.junit.jupiter.api.Assertions.assertTrue(id >= 100001);          // PK serveur (séquence seq_dossier)
    }

    @Test
    @DisplayName("Édition d'un brouillon PPM : en-tête mis à jour + lignes réconciliées (maj/ajout/retrait), mode recalculé")
    void editionPpm_facade() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "AOO", null, null, null, null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        modePassationRepository.save(new ModePassation(4, "Cotation", null, null, null, null));

        // Saisie initiale (sans id) : marché 150M (mode SAISI 4) et 500M (mode SAISI 2), entité 1 (ANT).
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        String creation = "{\"idEntiteContract\":1,\"exercice\":2026,"
                + "\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-120-v1\","
                + "\"marches\":[{\"montEstim\":150000000,\"idNature\":1,\"idMode\":4,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"montEstim\":500000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String cresp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(creation))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(cresp, "$.idDossier");

        // Le frontend lit les marchés du brouillon pour connaître leurs PK serveur (réconciliation par idDetail).
        String m1 = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(m1, "$[?(@.idDossier==" + idDoss + ")].idDetail");
        int idM150 = Math.min(ids.get(0), ids.get(1));   // créé en premier (150M)
        int idM500 = Math.max(ids.get(0), ids.get(1));   // créé en second (500M)
        mvc.perform(get("/api/marches/" + idM150).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.idMode").value(4));
        mvc.perform(get("/api/marches/" + idM500).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.idMode").value(2));

        // Édition : en-tête + idM150 → 1,5 Md (mode SAISI 1), idM500 retiré, nouvelle ligne 500M ajoutée (mode SAISI 2).
        // Règle corrigée : une ligne NOUVELLE à l'édition exige ≥1 processus (comme au POST) ; la ligne
        // mise à jour idM150 omet la liste → ses processus existants sont conservés.
        String edition = "{\"exercice\":2027,\"signataire\":\"RABE Maj\",\"dateSignature\":\"2026-02-01\",\"reference\":\"PPM-120-v2\","
                + "\"marches\":[{\"idDetail\":" + idM150 + ",\"montEstim\":1500000000,\"idNature\":1,\"idMode\":1,\"statut\":\"PREVU\"},"
                + "{\"montEstim\":500000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
        // En-tête mis à jour (brouillon lu par son id — hors liste « Mes PPM & marchés »).
        int idPpm120 = ppmRepository.findByIdDossier(idDoss).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + idPpm120).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("PPM-120-v2"))
                .andExpect(jsonPath("$.exercice").value(2027));
        // idM150 : mode saisi 1 conservé ; idM500 supprimé → 404 ; la nouvelle ligne 500M (PK ≠ idM500) a le mode saisi 2.
        mvc.perform(get("/api/marches/" + idM150).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.idMode").value(1));
        mvc.perform(get("/api/marches/" + idM500).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        String m2 = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        List<Integer> idsV2 = com.jayway.jsonpath.JsonPath.read(m2, "$[?(@.idDossier==" + idDoss + ")].idDetail");
        int idNew = idsV2.get(0).intValue() == idM150 ? idsV2.get(1) : idsV2.get(0);
        org.junit.jupiter.api.Assertions.assertNotEquals(idM500, idNew);
        mvc.perform(get("/api/marches/" + idNew).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.idMode").value(2));
    }

    @Test
    @DisplayName("Édition de brouillon : gardes — dossier soumis → 409 ; non-propriétaire → 403")
    void editionPpm_gardes() throws Exception {
        String tokenAutrePrmp = bearer("PRMPZZ", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPZZ", "ANT");
        String edition = "{\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\",\"marches\":[]}";
        // Brouillon PPM (121) de PRMP001 — pour le test de propriété.
        String r121 = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R121\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss121 = com.jayway.jsonpath.JsonPath.read(r121, "$.idDossier");
        // Brouillon PPM (122) de PRMP001 — soumis ensuite (donc non éditable).
        String r122 = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R122\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss122 = com.jayway.jsonpath.JsonPath.read(r122, "$.idDossier");
        int idPpm122 = ppmRepository.findByIdDossier(idDoss122).get(0).getIdPpm();
        marcheRepository.save(marche(1220, idDoss122, idPpm122)); // un PPM doit comporter au moins un marché avant soumission
        mvc.perform(post("/api/dossiers/" + idDoss122 + "/soumettre").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        // Dossier soumis → non éditable.
        mvc.perform(put("/api/saisies/ppm/" + idDoss122).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isConflict());
        // Brouillon d'une autre PRMP → 403.
        mvc.perform(put("/api/saisies/ppm/" + idDoss121).header("Authorization", tokenAutrePrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Édition PPM — ré-import complet (régression) : les sous-objets des lignes (bénéficiaires, lots, processus) sont créés comme au POST")
    void editionPpm_reImportComplet_sousObjetsCrees() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "OUVERTURE", 2, null, null));
        // Brouillon initial (import v1) : 1 ligne complète (bénéficiaire + lot + processus).
        String creation = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-RI\","
                + "\"marches\":[{\"designationMarche\":\"Ancienne ligne\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":1000000}],"
                + "\"lots\":[{\"designationLot\":\"Lot ancien\"}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(creation))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int ancienId = marcheRepository.findByIdDossier(idDoss).get(0).getIdDetail();

        // Ré-import front : PUT avec lignes SANS idDetail et sous-objets complets (payload front inchangé).
        // ⚠️ Fiche de présentation (2026-09-01) — la ligne réimportée est un CONTRAT-CADRE : la liste ③
        // de la fiche devient non vide, ce qui rend la justification globale obligatoire. Ajoutée ici,
        // le test portant sur les sous-objets et non sur la garde (couverte par FicheJustificationsIntegrationTest).
        String edition = "{\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-RI\","
                + "\"justificationFiche\":\"Contrat cadre motive\","
                + "\"marches\":[{\"designationMarche\":\"Ligne reimportee (contrat cadre)\",\"formeMarche\":\"CONTRAT_CADRE\","
                + "\"montEstim\":3000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":1000000},"
                + "{\"soaCode\":\"00-62-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":2000000}],"
                + "\"lots\":[{\"designationLot\":\"Lot 1\"},{\"designationLot\":\"Lot 2\"}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"},"
                + "{\"idCapm\":2,\"dateDebut\":\"2026-03-02\",\"dateFin\":\"2026-04-01\"}]}]}";
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isOk());

        List<Marche> marches = marcheRepository.findByIdDossier(idDoss);
        org.junit.jupiter.api.Assertions.assertEquals(1, marches.size());
        int nouvelId = marches.get(0).getIdDetail();
        org.junit.jupiter.api.Assertions.assertNotEquals(ancienId, nouvelId);
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.FormeMarche.CONTRAT_CADRE,
                marches.get(0).getFormeMarche());
        // Les sous-objets de la nouvelle ligne existent (le bug les laissait tous à zéro).
        org.junit.jupiter.api.Assertions.assertEquals(2, marchePrevisionRepository.findByIdDetail(nouvelId).size());
        org.junit.jupiter.api.Assertions.assertEquals(2, lotRepository.findByIdDetail(nouvelId).size());
        org.junit.jupiter.api.Assertions.assertEquals(2, serviceBeneficiaireRepository.findAll().stream()
                .filter(b -> b.getIdDetail() != null && b.getIdDetail().intValue() == nouvelId).count());
        // Ceux de l'ancienne ligne retirée ont bien disparu (cascade).
        org.junit.jupiter.api.Assertions.assertTrue(marchePrevisionRepository.findByIdDetail(ancienId).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(lotRepository.findByIdDetail(ancienId).isEmpty());
    }

    @Test
    @DisplayName("Édition PPM — ligne mise à jour : listes absentes → enfants conservés ; fournies → remplacement ; validations actives (Σ, processus)")
    void editionPpm_majPartielle_semantiqueEnfants() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        String creation = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-MP\","
                + "\"marches\":[{\"designationMarche\":\"Ligne\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":1000000}],"
                + "\"lots\":[{\"designationLot\":\"Lot unique\"}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(creation))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idDetail = marcheRepository.findByIdDossier(idDoss).get(0).getIdDetail();

        // 1) MAJ sans aucune liste (undefined) → enfants CONSERVÉS.
        String enTete = "{\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-MP\",";
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"idDetail\":" + idDetail
                        + ",\"designationMarche\":\"Ligne MAJ\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\"}]}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(1, marchePrevisionRepository.findByIdDetail(idDetail).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, lotRepository.findByIdDetail(idDetail).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, serviceBeneficiaireRepository.findAll().stream()
                .filter(b -> b.getIdDetail() != null && b.getIdDetail().intValue() == idDetail).count());

        // 2) Bénéficiaires fournis (2, Σ ok) + lots fournis VIDES → bénéficiaires REMPLACÉS (pas dupliqués),
        //    lots tous retirés, processus (absents) conservés.
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"idDetail\":" + idDetail
                        + ",\"designationMarche\":\"Ligne MAJ\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                        + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":400000},"
                        + "{\"soaCode\":\"00-62-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":600000}],"
                        + "\"lots\":[]}]}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(2, serviceBeneficiaireRepository.findAll().stream()
                .filter(b -> b.getIdDetail() != null && b.getIdDetail().intValue() == idDetail).count());
        org.junit.jupiter.api.Assertions.assertEquals(0, lotRepository.findByIdDetail(idDetail).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, marchePrevisionRepository.findByIdDetail(idDetail).size());

        // 3) Validations actives (le PUT ne les saute plus) : Σ bénéficiaires ≠ montant → 400 ciblé.
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"idDetail\":" + idDetail
                        + ",\"designationMarche\":\"Ligne MAJ\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                        + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":999}]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].beneficiaires"));

        // 4) Remplacement des processus par une liste VIDE → 400 (invariant ≥1 processus par marché).
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"idDetail\":" + idDetail
                        + ",\"designationMarche\":\"Ligne MAJ\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                        + "\"processus\":[]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].processus"));

        // 5) Ligne NOUVELLE sans processus → 400 (même règle qu'au POST).
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"designationMarche\":\"Nouvelle sans processus\","
                        + "\"montEstim\":500000,\"idNature\":1,\"statut\":\"PREVU\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].processus"));
    }

    @Test
    @DisplayName("Erreur de validation : corps expose erreurs[].champ/message")
    void validation_erreurs_format() throws Exception {
        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs").isArray())
                .andExpect(jsonPath("$.erreurs[0].champ").exists())
                .andExpect(jsonPath("$.erreurs[0].message").exists());
    }

    @Test
    @DisplayName("Suppression cohérente : supprimer le dernier PPM d'un brouillon supprime aussi le dossier")
    void suppression_coherente() throws Exception {
        Dossier d = dossier(190, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(290, 190, "PRMP001"));
        marcheRepository.save(marche(390, 190, 290));

        mvc.perform(delete("/api/ppms/290").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(dossierRepository.existsById(190));
        // Absent de « Mes brouillons » (GET /api/dossiers?statut=BROUILLON) ET de GET /api/dossiers.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "BROUILLON"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.idDossier==190)]", hasSize(0)));
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==190)]", hasSize(0)));
    }

    @Test
    @DisplayName("Suppression PPM d'un brouillon AVEC historique (réception) → PPM supprimé (204), dossier conservé (traces FK)")
    void suppression_brouillonAvecHistorique_conserveDossier() throws Exception {
        Dossier d = dossier(191, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(291, 191, "PRMP001"));
        receptionRepository.save(reception(591, 191, "CTRSEC", true)); // trace de circuit → pas de hard delete

        mvc.perform(delete("/api/ppms/291").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(ppmRepository.existsById(291));
        org.junit.jupiter.api.Assertions.assertTrue(dossierRepository.existsById(191)); // conservé (porte une réception)
    }

    @Test
    @DisplayName("Suppression dossier — BROUILLON propriétaire → 204, cascade PPM/marché, absent de Mes brouillons")
    void suppression_brouillon_ok() throws Exception {
        Dossier d = dossier(600, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(600, 600, "PRMP001"));
        marcheRepository.save(marche(600, 600, 600));

        mvc.perform(delete("/api/dossiers/600").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(dossierRepository.existsById(600));
        org.junit.jupiter.api.Assertions.assertFalse(ppmRepository.existsById(600));
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(600));
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "BROUILLON"))
                .andExpect(jsonPath("$[?(@.idDossier==600)]", hasSize(0)));
    }

    @Test
    @DisplayName("Suppression dossier — BROUILLON AVEC historique (réception+retrait+notif) → 204, cascade historique")
    void suppression_brouillon_avec_historique_ok() throws Exception {
        Dossier d = dossier(603, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        receptionRepository.save(reception(603, 603, "CTRSEC", true));
        demandeRetraitRepository.save(demandeRetrait(0, 603, "PRMP001"));
        notificationService.emettre(603, TypeNotification.PRET_DISPATCH, "CTRMEM", null, "Titre", "Corps");

        mvc.perform(delete("/api/dossiers/603").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(dossierRepository.existsById(603));
        org.junit.jupiter.api.Assertions.assertFalse(receptionRepository.existsByIdDossier(603));
        org.junit.jupiter.api.Assertions.assertFalse(demandeRetraitRepository.existsByIdDossier(603));
    }

    @Test
    @DisplayName("Suppression dossier — statut SOUMIS → 409")
    void suppression_hors_brouillon_409() throws Exception {
        Dossier d = dossier(601, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        mvc.perform(delete("/api/dossiers/601").header("Authorization", tokenPrmp)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Suppression dossier — autre PRMP → 403")
    void suppression_autre_prmp_403() throws Exception {
        prmpRepository.save(prmp("PRMP002", "ANT"));
        Dossier d = dossier(602, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP002"); dossierRepository.save(d);
        mvc.perform(delete("/api/dossiers/602").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Suppression dossier — id inexistant → 404")
    void suppression_inexistant_404() throws Exception {
        mvc.perform(delete("/api/dossiers/99999").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Saisie PPM — signataire auto depuis le profil PRMP (prenoms + nom)")
    void ppm_signataire_depuis_prmp() throws Exception {
        EntiteContract e = entite(703, 1, "ANT"); e.setLibelleEntite("Direction Générale du Budget");
        entiteContractRepository.save(e);
        prmpEntiteRepository.save(prmpEntite(703, "PRMP001", 703, true));
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":703,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idPpm = ppmRepository.findByIdDossier(idDoss).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.signataire").value("Prenoms Nom"));
    }

    @Test
    @DisplayName("Saisie PPM — marché sans processus → 400 (marches[0].processus)")
    void marche_sans_processus_400() throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\"}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus')].message",
                        hasItem("Au moins un processus est obligatoire.")));
    }

    @Test
    @DisplayName("Saisie PPM — processus avec idCapm inexistant → 400 (marches[0].processus[0].idCapm)")
    void processus_idCapm_invalide_400() throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":999,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[0].idCapm')]").exists());
    }

    @Test
    @DisplayName("Saisie PPM — processus sans dateDebut → 400 (marches[0].processus[0].dateDebut)")
    void processus_sans_dateDebut_400() throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[0].dateDebut')].message",
                        hasItem("La date de début est obligatoire.")));
    }

    @Test
    @DisplayName("Saisie PPM — marché + processus complets → 201 + prévisions triées par ordre CAPM")
    void brouillon_avec_processus_ok() throws Exception {
        // Mode déterminable (évite la notif MODE_NON_DETERMINE, hors sujet) : 500M → AOR (mode 2).
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(3, "OUVERTURE", 3, null, null));
        // Processus envoyés dans le désordre (3 puis 1) → la lecture doit les trier par ordre (1 avant 3).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":3,\"dateDebut\":\"2026-03-01\",\"dateFin\":\"2026-03-31\"},"
                + "{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-02-28\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        String m = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        int idDetail = ((List<Integer>) com.jayway.jsonpath.JsonPath.read(m,
                "$[?(@.idDossier==" + idDoss + ")].idDetail")).get(0);
        // 2 prévisions triées par t_capm.ORDRE ASC → idCapm 1 (ordre 1) avant idCapm 3 (ordre 3).
        mvc.perform(get("/api/marche-previsions?marche=" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idCapm").value(1))
                .andExpect(jsonPath("$[0].ordre").value(1))
                .andExpect(jsonPath("$[0].dateDebut").value("2026-02-01"))
                .andExpect(jsonPath("$[1].idCapm").value(3))
                .andExpect(jsonPath("$[1].ordre").value(3));
    }

    @Test
    @DisplayName("Saisie PPM — processus dateDebut >= dateFin → 400 (cohérence interne)")
    void processus_datefin_avant_datedebut_400() throws Exception {
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-06-30\",\"dateFin\":\"2026-06-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[0].dateFin')].message",
                        hasItem("La date de fin doit être postérieure à la date de début.")));
    }

    @Test
    @DisplayName("Saisie PPM — chevauchement entre processus consécutifs → 400 (séquence)")
    void processus_sequence_chevauchement_400() throws Exception {
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "DAO", 2, null, null));
        // processus[1] (DAO) commence 02-15, avant la fin de processus[0] (LANCEMENT) le 03-01 → chevauchement.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"},"
                + "{\"idCapm\":2,\"dateDebut\":\"2026-02-15\",\"dateFin\":\"2026-04-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[1].dateDebut')]").exists());
    }

    @Test
    @DisplayName("Saisie PPM — dates cohérentes et ordonnées → 201")
    void processus_sequence_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "DAO", 2, null, null));
        // dateDebut[2] = dateFin[1] (03-01) → contiguïté autorisée (>=).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"},"
                + "{\"idCapm\":2,\"dateDebut\":\"2026-03-01\",\"dateFin\":\"2026-04-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Saisie PPM — corps mal formé (date JJ/MM/AAAA, id libellé) → 400 avec le champ fautif")
    void saisie_corps_illisible_400() throws Exception {
        // dateSignature non-ISO → 400 + champ dateSignature
        String dateKo = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"23/06/2026\","
                + "\"marches\":[{\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(dateKo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='dateSignature')]").exists());

        // idEntiteContract = libellé (string) → 400 + champ idEntiteContract
        String idKo = "{\"idEntiteContract\":\"Direction Générale du Budget\",\"exercice\":2026,\"dateSignature\":\"2026-06-23\","
                + "\"marches\":[{\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(idKo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='idEntiteContract')]").exists());
    }

    @Test
    @DisplayName("ServiceBeneficiaire : numCompte (FK tr_compte) exposé + SOA_CODE de 17 car. accepté (round-trip API)")
    void serviceBeneficiaire_numCompteEtSoaCodeLong_ok() throws Exception {
        // Chaîne marché (FK ID_DETAIL) + référentiels (FK NUM_COMPTE / SOA_CODE).
        dossierRepository.save(dossier(800, "BROUILLON"));
        ppmRepository.save(ppm(800, 800, "PRMP001"));
        marcheRepository.save(marche(9700, 800, 800));
        compteRepository.save(new cnm.prs.entity.Compte("CPT-BENEF-01", "Compte bénéficiaire", null, null));
        // SOA_CODE de 17 caractères (> ancien maximum 15) — prouve l'allongement à 25.
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-21-0-J00-00000", "SOA test"));

        String body = "{\"idBenef\":9700,\"idDetail\":9700,\"soaCode\":\"00-21-0-J00-00000\","
                + "\"numCompte\":\"CPT-BENEF-01\",\"ancMontBenef\":1000000,\"nouvMontBenef\":1200000}";
        mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numCompte").value("CPT-BENEF-01"))
                .andExpect(jsonPath("$.soaCode").value("00-21-0-J00-00000"));

        // Relecture : compte + code SOA long persistés et exposés.
        mvc.perform(get("/api/service-beneficiaires/9700").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numCompte").value("CPT-BENEF-01"))
                .andExpect(jsonPath("$.soaCode").value("00-21-0-J00-00000"));
    }

    @Test
    @DisplayName("DELETE /api/marches/{id} : cascade les bénéficiaires (t_service_beneficiaire) — plus de 409 FK")
    void suppressionMarche_cascadeBeneficiaires() throws Exception {
        Dossier d = dossierLoc(810, "BROUILLON", "ANT", "PRMP001"); d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        ppmRepository.save(ppm(810, 810, "PRMP001"));
        marcheRepository.save(marche(9810, 810, 810));
        compteRepository.save(new cnm.prs.entity.Compte("CPT-810", "Compte", null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-21-0-J00-00000", "SOA"));
        // Bénéficiaire rattaché au marché 9810 → sans cascade, DELETE renverrait 409 (FK).
        mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idBenef\":9810,\"idDetail\":9810,\"soaCode\":\"00-21-0-J00-00000\","
                        + "\"numCompte\":\"CPT-810\",\"ancMontBenef\":1000000}"))
                .andExpect(status().isCreated());

        // Suppression du marché → 204 (cascade en transaction), pas de 409.
        mvc.perform(delete("/api/marches/9810").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        // Le bénéficiaire a été supprimé en cascade ; le marché a disparu.
        mvc.perform(get("/api/service-beneficiaires/9810").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/marches/9810").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Saisie PPM — bénéficiaire par soaLibelle (sans code) : SOA résolu-ou-créé par libellé, dédupliqué, code SOA dérivé")
    void saisiePpm_soaBeneficiaireParLibelle() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        long soaAvant = soaBeneficiaireRepository.count();

        // 3 marchés : A et B portent le MÊME service textuel (dédup) ; C un autre → 2 SOA créés.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-SOA\","
                + "\"marches\":["
                + "{\"designationMarche\":\"A\",\"montEstim\":2480000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaLibelle\":\"Tout Service\",\"numCompte\":\"6471\",\"ancMontBenef\":2480000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"B\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaLibelle\":\"TOUT SERVICE\",\"numCompte\":\"6472\",\"ancMontBenef\":1000000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"C\",\"montEstim\":500000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaLibelle\":\"Autre Service\",\"numCompte\":\"6473\",\"ancMontBenef\":500000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // 2 SOA créés (« Tout Service » dédupliqué malgré la casse, + « Autre Service »).
        org.junit.jupiter.api.Assertions.assertEquals(soaAvant + 2, soaBeneficiaireRepository.count());
        List<cnm.prs.entity.SoaBeneficiaire> soas = soaBeneficiaireRepository.findAll();
        cnm.prs.entity.SoaBeneficiaire tout = soas.stream()
                .filter(s -> "Tout Service".equals(s.getLibelle())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(tout.getSoaCode());   // code dérivé du libellé
        org.junit.jupiter.api.Assertions.assertTrue(tout.getSoaCode().length() <= 25);
        org.junit.jupiter.api.Assertions.assertEquals(1, soas.stream()
                .filter(s -> "Tout Service".equals(s.getLibelle())).count());
        org.junit.jupiter.api.Assertions.assertEquals(1, soas.stream()
                .filter(s -> "Autre Service".equals(s.getLibelle())).count());
        // Les lignes bénéficiaires de A et B référencent le MÊME code SOA (dédup par libellé).
        List<cnm.prs.entity.ServiceBeneficiaire> benefs = serviceBeneficiaireRepository.findAll();
        long distinctsToutService = benefs.stream()
                .filter(b -> tout.getSoaCode().equals(b.getSoaCode())).count();
        org.junit.jupiter.api.Assertions.assertEquals(2, distinctsToutService);
    }

    @Test
    @DisplayName("Saisie à la volée — « APPEL D'OFFRE OUVERT » résout le mode canonique (pas de doublon) → sous-type PPM-AGPM (contournement AGPM fermé)")
    void saisie_modeSingulier_fermeContournementAgpm() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        long modesAvant = modePassationRepository.count();

        // La coquille du PDF (singulier) part telle quelle à la création — avant : quasi-doublon sans declencheAgpm.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"designationMarche\":\"Route Tsiatosika\",\"montEstim\":23100000000,\"idNature\":1,"
                + "\"modeLibelle\":\"APPEL D'OFFRE OUVERT\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                // Sous-type dérivé PPM-AGPM : la règle AGPM s'applique malgré la coquille.
                .andExpect(jsonPath("$.idSousType").value("PPM-AGPM"))
                .andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        // Résolu sur le mode canonique 1 — AUCUN doublon créé.
        org.junit.jupiter.api.Assertions.assertEquals(1,
                marcheRepository.findByIdDossier(idDoss).get(0).getIdMode());
        org.junit.jupiter.api.Assertions.assertEquals(modesAvant, modePassationRepository.count());
    }

    @Test
    @DisplayName("Saisie à la volée — libellé réellement nouveau toujours créé ; libellé proche d'un mode AGPM créé MAIS signalé (audit)")
    void saisie_modeNouveau_creeEtSignaleSiProcheAgpm() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);

        // (1) Libellé réellement nouveau → créé à la volée (garde inchangée).
        String b1 = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"designationMarche\":\"M1\",\"montEstim\":1000,\"idNature\":1,"
                + "\"modeLibelle\":\"Concours d'architecture\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(b1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSousType").value("PPM"));   // pas déclencheur
        org.junit.jupiter.api.Assertions.assertTrue(modePassationRepository.findAll().stream()
                .anyMatch(m -> "Concours d'architecture".equals(m.getLibelle())), "mode nouveau créé");

        // (2) Libellé non résolu mais PROCHE du mode AGPM (« ouver » tronqué, distance 1) → créé + signal audit.
        String b2 = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"designationMarche\":\"M2\",\"montEstim\":1000,\"idNature\":1,"
                + "\"modeLibelle\":\"Appel d'offre ouver\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(b2))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION_MODE_PROCHE_AGPM')]", hasSize(1)));
    }

    @Test
    @DisplayName("Saisie PPM — formeMarche : explicite conservée, absente → défaut QUANTITE_FIXE, code inconnu → 400 ciblé")
    void saisiePpm_formeMarche() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-FM\","
                + "\"marches\":[{\"designationMarche\":\"Fourniture de carburant\",\"formeMarche\":\"A_COMMANDE\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"Construction batiment\",\"montEstim\":2000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss
                        + " && @.designationMarche=='Fourniture de carburant')].formeMarche", hasItem("A_COMMANDE")))
                // Absente à la saisie → défaut serveur QUANTITE_FIXE (jamais null).
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss
                        + " && @.designationMarche=='Construction batiment')].formeMarche", hasItem("QUANTITE_FIXE")));

        // Code inconnu → 400 ciblé.
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"A_COMMANDE\"", "\"FORFAIT\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Forme de marché inconnue")));
    }

    @Test
    @DisplayName("Reprise Flyway V3 — forme du marché : lignes historiques (colonne NULL) reprises depuis la désignation, idempotente")
    void formeMarcheMigration_repriseHistorique() throws Exception {
        Dossier d = dossier(9601, "SOUMIS");
        d.setIdLocalite("ANT");
        d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(9601, 9601, "PRMP001"));
        // Lignes « historiques » : colonne FORME_MARCHE à NULL (état d'avant l'ajout du champ).
        Marche cadre = marche(96010, 9601, 9601);
        cadre.setDesignationMarche("Travaux d'amenagement de la voie rapide (Contrat cadre)");
        cadre.setFormeMarche(null);
        Marche commande = marche(96011, 9601, 9601);
        commande.setDesignationMarche("Fourniture de carburant, marche a commande, pour le parc");
        commande.setFormeMarche(null);
        Marche fixe = marche(96012, 9601, 9601);
        fixe.setDesignationMarche("Construction d'un batiment administratif");
        fixe.setFormeMarche(null);
        marcheRepository.saveAll(java.util.List.of(cadre, commande, fixe));
        org.junit.jupiter.api.Assertions.assertEquals(3, marcheRepository.findByFormeMarcheIsNull().size());

        executerMigrationFlyway("V3__reprise_forme_marche.sql");

        // Formes dérivées des désignations (mêmes motifs que l'import) ; plus aucune ligne à reprendre.
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.FormeMarche.CONTRAT_CADRE,
                marcheRepository.findById(96010).orElseThrow().getFormeMarche());
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.FormeMarche.A_COMMANDE,
                marcheRepository.findById(96011).orElseThrow().getFormeMarche());
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.FormeMarche.QUANTITE_FIXE,
                marcheRepository.findById(96012).orElseThrow().getFormeMarche());
        org.junit.jupiter.api.Assertions.assertTrue(marcheRepository.findByFormeMarcheIsNull().isEmpty());
    }

    @Test
    @DisplayName("PPM-AGPM : declencheAgpm exposé/persisté sur le mode ; agpmRequis dérivé sur le PPM (true si ≥1 marché en appel d'offres ouvert, sinon false)")
    void ppmAgpm_marqueurMode_etAgpmRequisDerive() throws Exception {
        // Le marqueur « appel d'offres ouvert » est administrable et persisté sur le mode (write + read).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":1,\"libelle\":\"Appel d'offres ouvert\",\"declencheAgpm\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.declencheAgpm").value(true));
        // Mode ordinaire (non déclencheur) : declencheAgpm null = false.
        modePassationRepository.save(new ModePassation(4, "Cotation", null, null, null, null));

        // PPM 9500 : un marché en appel d'offres ouvert (mode 1) → agpmRequis = true.
        // Dossiers SOUMIS (non brouillon) pour figurer dans « Mes PPM » (findVisiblesParPrmp exclut les BROUILLON).
        Dossier d1 = dossier(9500, "SOUMIS");
        d1.setIdTypeDossier("DDP"); d1.setIdPrmp("PRMP001"); d1.setIdLocalite("ANT");
        dossierRepository.save(d1);
        ppmRepository.save(ppm(9500, 9500, "PRMP001"));
        Marche m1 = marche(95001, 9500, 9500); m1.setIdMode(1); marcheRepository.save(m1);

        // PPM 9501 : uniquement un marché ordinaire (mode 4) → agpmRequis = false.
        Dossier d2 = dossier(9501, "SOUMIS");
        d2.setIdTypeDossier("DDP"); d2.setIdPrmp("PRMP001"); d2.setIdLocalite("ANT");
        dossierRepository.save(d2);
        ppmRepository.save(ppm(9501, 9501, "PRMP001"));
        Marche m2 = marche(95011, 9501, 9501); m2.setIdMode(4); marcheRepository.save(m2);

        // Le front lit agpmRequis sur le PPM (dérivé serveur, non recalculé côté front).
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==9500 && @.agpmRequis==true)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==9501 && @.agpmRequis==false)]", hasSize(1)));
    }

    @Test
    @DisplayName("PPM-AGPM : soumission d'un PPM en appel d'offres ouvert SANS pièce AGPM → 400 {piecesJointes} ; avec AGPM → SOUMIS ; PPM ordinaire non concerné")
    void ppmAgpm_soumission_exigeAgpmConditionnel() throws Exception {
        // Pièce AGPM au référentiel : repérée par son code stable, OBLIGATOIRE statique = false (conditionnelle).
        int idAgpm = seedTypePieceCode("Avis Général de Passation de Marché", "AGPM", false, "DDP",6);
        // Mode déclencheur (appel d'offres ouvert) + mode ordinaire.
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        modePassationRepository.save(new ModePassation(4, "Cotation", null, null, null, null));

        // (1) PPM avec un marché en appel d'offres ouvert, AGPM non fournie → soumission refusée (400).
        Dossier d = dossier(9502, "BROUILLON");
        d.setRefeDossier(null); d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(9502, 9502, "PRMP001"));
        Marche m = marche(95021, 9502, 9502); m.setIdMode(1); marcheRepository.save(m);

        mvc.perform(post("/api/dossiers/9502/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='piecesJointes')]", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[?(@.champ=='piecesJointes')].message",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("AGPM"))));

        // Dépôt de la pièce AGPM (PRMP propriétaire) via le mécanisme existant, puis soumission → 200 SOUMIS.
        byte[] pdf = "%PDF-1.4 AGPM".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/piece-jointe-dossiers")
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"idDossier\":9502,\"idTypePiece\":" + idAgpm + "}").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("fichier", "agpm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dossiers/9502/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));

        // (2) PPM ordinaire (aucun marché en appel d'offres ouvert) → AGPM non requise, soumission OK sans AGPM.
        Dossier d2 = dossier(9503, "BROUILLON");
        d2.setRefeDossier(null); d2.setIdTypeDossier("DDP"); d2.setIdPrmp("PRMP001"); d2.setIdLocalite("ANT");
        dossierRepository.save(d2);
        ppmRepository.save(ppm(9503, 9503, "PRMP001"));
        Marche m2 = marche(95031, 9503, 9503); m2.setIdMode(4); marcheRepository.save(m2);
        mvc.perform(post("/api/dossiers/9503/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
    }

    /**
     * Comme {@link #seedTypePiece} mais avec un {@code code} stable (ex. {@code AGPM}) — support de
     * l'obligation conditionnelle. Renvoie la PK générée.
     */
    private int seedTypePieceCode(String libelle, String code, boolean obligatoire, String typeDossier, int ordre) {
        cnm.prs.entity.TypePieceJointe t = new cnm.prs.entity.TypePieceJointe();
        t.setLibellePiece(libelle);
        t.setCode(code);
        t.setObligatoire(obligatoire);
        t.setIdTypeDossier(typeDossier);
        t.setOrdre(ordre);
        return typePieceJointeRepository.save(t).getIdTypePiece();
    }

    @Test
    @DisplayName("Saisie PPM — lots[] : une ligne t_lot par lot, rattachée au marché ; sans lots[] → aucun lot (rétro-compat)")
    void saisiePpm_lots() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        // Marché A : 2 lots ; marché B : aucun lot (rétro-compat).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-LOTS\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":3000000,\"natureLibelle\":\"Travaux\",\"modeLibelle\":\"Appel d'offres ouvert\",\"statut\":\"PREVU\","
                + "\"lots\":[{\"designationLot\":\"Lot 1 - Gros oeuvre\",\"montLot\":2000000,\"qteLot\":1,\"uniteLot\":\"U\"},"
                + "{\"designationLot\":\"Lot 2 - Finitions\",\"montLot\":1000000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"B\",\"montEstim\":500000,\"natureLibelle\":\"Travaux\",\"modeLibelle\":\"Appel d'offres ouvert\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        List<cnm.prs.entity.Marche> marches = marcheRepository.findByIdDossier(idDoss);
        cnm.prs.entity.Marche a = marches.stream().filter(m -> "A".equals(m.getDesignationMarche())).findFirst().orElseThrow();
        cnm.prs.entity.Marche b = marches.stream().filter(m -> "B".equals(m.getDesignationMarche())).findFirst().orElseThrow();

        // Marché A : 2 lots t_lot, rattachés au marché + dossier.
        List<cnm.prs.entity.Lot> lotsA = lotRepository.findByIdDetail(a.getIdDetail());
        org.junit.jupiter.api.Assertions.assertEquals(2, lotsA.size());
        org.junit.jupiter.api.Assertions.assertTrue(lotsA.stream().allMatch(l -> idDoss == l.getIdDossier()));
        org.junit.jupiter.api.Assertions.assertTrue(lotsA.stream()
                .anyMatch(l -> "Lot 1 - Gros oeuvre".equals(l.getDesignationLot())
                        && new java.math.BigDecimal("2000000").compareTo(l.getMontLot()) == 0
                        && Integer.valueOf(1).equals(l.getQteLot()) && "U".equals(l.getUniteLot())));
        // Marché B : aucun lot (rétro-compat).
        org.junit.jupiter.api.Assertions.assertTrue(lotRepository.findByIdDetail(b.getIdDetail()).isEmpty());

        // Suppression du dossier BROUILLON (avec lots) → cascade partagée retire aussi les t_lot (pas d'orphelin FK).
        mvc.perform(delete("/api/dossiers/" + idDoss).header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(lotRepository.findByIdDetail(a.getIdDetail()).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(a.getIdDetail()));
    }

    @Test
    @DisplayName("GET /api/lots/par-marche/{idDetail} : lots d'une ligne de marché ; aucun/inconnu → liste vide")
    void lot_parMarche() throws Exception {
        marcheRepository.save(marche(9800, 1, 1));
        for (int k = 1; k <= 2; k++) {
            cnm.prs.entity.Lot l = new cnm.prs.entity.Lot();
            l.setIdLot(8000 + k);
            l.setIdDossier(1);
            l.setIdDetail(9800);
            l.setDesignationLot("Lot " + k);
            l.setMontLot(new java.math.BigDecimal(k + "000000"));
            lotRepository.save(l);
        }

        mvc.perform(get("/api/lots/par-marche/9800").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].designationLot", containsInAnyOrder("Lot 1", "Lot 2")))
                .andExpect(jsonPath("$[?(@.idDetail==9800)]", hasSize(2)));

        // Marché sans lot / inconnu → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/lots/par-marche/99999").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/lots/par-dossier/{idDossier} : agrège les lots de toutes les lignes de marché ; aucun/inconnu → liste vide")
    void lot_parDossier() throws Exception {
        // Dossier 7777 : marché 9810 (2 lots) + marché 9811 (1 lot).
        dossierRepository.save(dossier(7777, "BROUILLON"));   // FK t_lot.ID_DOSSIER → t_dossier
        marcheRepository.save(marche(9810, 7777, 1));
        marcheRepository.save(marche(9811, 7777, 1));
        int[][] seed = { {8101, 9810}, {8102, 9810}, {8103, 9811} };
        for (int[] s : seed) {
            cnm.prs.entity.Lot l = new cnm.prs.entity.Lot();
            l.setIdLot(s[0]);
            l.setIdDossier(7777);
            l.setIdDetail(s[1]);
            l.setDesignationLot("Lot " + s[0]);
            lotRepository.save(l);
        }

        mvc.perform(get("/api/lots/par-dossier/7777").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.idDetail==9810)]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.idDetail==9811)]", hasSize(1)));

        // Dossier sans lot / inconnu → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/lots/par-dossier/88888").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Processus prévisionnels : dateFin optionnelle — saisie/prevision sans dateFin → 201 ; séquence non contrainte si dateFin précédente absente ; dateFin présente toujours contrôlée")
    void processus_dateFin_optionnelle() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "DAO", 2, null, null));

        // Saisie : p0 (capm1) SANS dateFin ; p1 (capm2) démarre AVANT p0 → séquence non contrainte (skip) → 201.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-DFOPT\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"natureLibelle\":\"Travaux\",\"modeLibelle\":\"Appel d'offres ouvert\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"},"
                + "{\"idCapm\":2,\"dateDebut\":\"2026-01-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        cnm.prs.entity.Marche a = marcheRepository.findByIdDossier(idDoss).get(0);
        // La prévision du processus capm1 a bien DATE_FIN null.
        cnm.prs.entity.MarchePrevision p0 = marchePrevisionRepository.findByIdDetail(a.getIdDetail()).stream()
                .filter(p -> Integer.valueOf(1).equals(p.getIdCapm())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(p0.getDateFin());

        // POST /api/marche-previsions sans dateFin → 201.
        dossierRepository.save(dossier(7778, "BROUILLON"));
        marcheRepository.save(marche(9830, 7778, 1));
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrevision\":990020,\"idDetail\":9830,\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateFin").value(org.hamcrest.Matchers.nullValue()));

        // Régression : dateFin PRÉSENTE reste contrôlée (dateDebut ≥ dateFin → 400).
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrevision\":990021,\"idDetail\":9830,\"idCapm\":2,\"dateDebut\":\"2026-05-01\",\"dateFin\":\"2026-04-01\"}"))
                .andExpect(status().isBadRequest());
    }
}
