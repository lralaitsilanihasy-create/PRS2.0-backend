package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Reception;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Secretariat et dispatch : file a receptionner, reception (dates, localite du dossier,
 * reference figee), puis dispatch (garde de l'attributaire, association du CC, dates,
 * avancement du dossier).
 */
class ReceptionDispatchIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Dispatch — garde de l'attributaire : Secrétaire (aucune paire → Membre) → 409 ; matricule inconnu "
            + "→ 409 ; CC refusé quand la paire CC → Membre est désactivée, accepté quand elle est réactivée "
            + "(data-driven) ; même garde au PUT")
    void dispatch_gardeAttributaireMembre() throws Exception {
        dossierRepository.save(dossier(4602, "PRET_DISPATCH"));
        receptionRepository.save(reception(5602, 4602, "CTRSEC", true)); // ANT
        // Secrétaire attributaire : aucune paire Secrétaire → Membre dans la table → dossier inexaminable → 409.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"CTRSEC\",\"interimDispatch\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("inexaminable")));
        // Matricule inconnu → refus explicite.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"ZZZ999\",\"interimDispatch\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("aucun contrôleur")));
        // L'Admin DÉSACTIVE la paire 5 (CC → Membre) → le CC ne peut plus être attributaire → 409.
        mvc.perform(put("/api/delegation-profils/5").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":5,\"idProfileDelegant\":3,\"idProfileDelegue\":5,\"actif\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isConflict());
        // RÉACTIVÉE → le même dispatch (auto-attribution du CC) passe, SANS changement de code.
        mvc.perform(put("/api/delegation-profils/5").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":5,\"idProfileDelegant\":3,\"idProfileDelegue\":5,\"actif\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRCC1"));
        // Même garde à la correction (PUT) : re-cibler un Secrétaire est refusé.
        mvc.perform(put("/api/dispatchs/5602").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"CTRSEC\",\"interimDispatch\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("inexaminable")));
    }

    @Test
    @DisplayName("Dispatch — association CC seulement quand le Président dispatche à un Membre : CC auto-attribué → "
            + "sans imCtrlCc ; CC → Membre → imCtrlCc client ignoré ; Président → Membre → association + copie "
            + "DISPATCH_CC conservées ; Président → lui-même → pas d'association")
    void dispatch_associationCcSelonDispatcheur() throws Exception {
        // 1) Le CC s'auto-dispatche → aucune association CC (une seule apparition : Rôle Membre).
        dossierRepository.save(dossier(4603, "PRET_DISPATCH"));
        receptionRepository.save(reception(5603, 4603, "CTRSEC", true)); // ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5603,\"idReception\":5603,\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlCc").value(nullValue()));
        // 2) Le CC dispatche à un Membre, imCtrlCc = lui-même envoyé par le client → IGNORÉ (forcé à null).
        dossierRepository.save(dossier(4604, "PRET_DISPATCH"));
        receptionRepository.save(reception(5604, 4604, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5604,\"idReception\":5604,\"imCtrlCc\":\"CTRCC1\","
                        + "\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlCc").value(nullValue()));
        // Aucune copie DISPATCH_CC émise pour ces deux dispatchs (le CC est l'acteur du dispatch).
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='DISPATCH_CC')]", hasSize(0)));
        // 3) Président → Membre → comportement conservé : CC de la localité auto-associé + copie DISPATCH_CC.
        dossierRepository.save(dossier(4605, "PRET_DISPATCH"));
        receptionRepository.save(reception(5605, 4605, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5605,\"idReception\":5605,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='DISPATCH_CC')]", hasSize(1)));
        // 4) Président → LUI-MÊME (auto-attribution) → pas d'association (copie sans objet).
        dossierRepository.save(dossier(4606, "PRET_DISPATCH"));
        receptionRepository.save(reception(5606, 4606, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5606,\"idReception\":5606,\"imCtrlMembre\":\"CTRPRE\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlCc").value(nullValue()));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='DISPATCH_CC')]", hasSize(1))); // toujours une seule
        // Même règle au PUT : le CC corrige son dispatch en renvoyant imCtrlCc = lui-même → ignoré.
        mvc.perform(put("/api/dispatchs/5604").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5604,\"idReception\":5604,\"imCtrlCc\":\"CTRCC1\","
                        + "\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imCtrlCc").value(nullValue()));
    }

    @Test
    @DisplayName("Reprise Flyway V4 — association CC : IM_CTRL_CC effacé quand il désigne l'attributaire (doublon "
            + "auto-attribution) ou le dispatcheur lui-même ; les associations légitimes sont conservées")
    void migration_associationCcInvalide() {
        dossierRepository.save(dossier(4607, "DISPATCHE"));
        receptionRepository.save(reception(5607, 4607, "CTRSEC", true));
        dossierRepository.save(dossier(4608, "DISPATCHE"));
        receptionRepository.save(reception(5608, 4608, "CTRSEC", true));
        dossierRepository.save(dossier(4609, "DISPATCHE"));
        receptionRepository.save(reception(5609, 4609, "CTRSEC", true));
        // Doublon historique : CC auto-attribué ET associé à son propre dispatch (cas 00002/PPM/CNM/2026).
        dispatchRepository.save(dispatch(5607, 5607, "CTRCC1", "CTRCC1"));
        // CC dispatcheur associé à lui-même (copie de son propre dispatch).
        Dispatch avecDispatcheur = dispatch(5608, 5608, "CTRCC1", "CTRMEM");
        avecDispatcheur.setImCtrlDispatch("CTRCC1");
        dispatchRepository.save(avecDispatcheur);
        // Association légitime (Président → Membre, CC tiers) : conservée.
        Dispatch legitime = dispatch(5609, 5609, "CTRCC1", "CTRMEM");
        legitime.setImCtrlDispatch("CTRPRE");
        dispatchRepository.save(legitime);

        executerMigrationFlyway("V4__reprise_association_cc_dispatch.sql");

        org.junit.jupiter.api.Assertions.assertNull(
                dispatchRepository.findById(5607).orElseThrow().getImCtrlCc(),
                "auto-attribution : l'association CC (doublon Membre+CC) doit être effacée");
        org.junit.jupiter.api.Assertions.assertNull(
                dispatchRepository.findById(5608).orElseThrow().getImCtrlCc(),
                "dispatcheur CC : la copie de son propre dispatch doit être effacée");
        org.junit.jupiter.api.Assertions.assertEquals("CTRCC1",
                dispatchRepository.findById(5609).orElseThrow().getImCtrlCc(),
                "Président → Membre : l'association légitime est conservée");
    }

    @Test
    @DisplayName("Dispatch : interdit au Membre (403)")
    void dispatch_interditAuMembre() throws Exception {
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":9,\"idReception\":1,\"interimDispatch\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Enregistrement secrétariat : la date de réception comporte l'heure (yyyy-MM-dd HH:mm)")
    void enregistrement_liste_ok() throws Exception {
        // La réception 1 (localité ANT) est seedée à 2026-06-02 10:30.
        mvc.perform(get("/api/receptions").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idReception==1)].dateReception", hasItem("2026-06-02 10:30")));
    }

    @Test
    @DisplayName("Enregistrement secrétariat : dateSoumission présente et non nulle pour un dossier récent")
    void enregistrement_soumission_ok() throws Exception {
        // Dossier récent (ANT) avec une date/heure de soumission, et sa réception (CC ANT).
        Dossier d = dossier(150, "SOUMIS");
        d.setIdLocalite("ANT");
        d.setIdPrmp("PRMP001");
        d.setDateSoumission(LocalDateTime.of(2026, 6, 20, 9, 15));
        dossierRepository.save(d);
        receptionRepository.save(reception(150, 150, "CTRCC1", true));

        mvc.perform(get("/api/receptions/150").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateReception").value("2026-06-02 10:30"))
                .andExpect(jsonPath("$.dateSoumission").value("2026-06-20 09:15"));
    }

    @Test
    @DisplayName("Réception — dateReception « yyyy-MM-dd » sans heure → 201 (plus d'erreur de parsing index 10)")
    void reception_creation_date_simple_ok() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(300, "SOUMIS");
        d.setIdLocalite("ANT");
        d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":300,\"numPassage\":1,\"typePassage\":\"INITIAL\",\"complet\":true,"
                        + "\"dateReception\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateReception", org.hamcrest.Matchers.startsWith("2026-06-30")));
    }

    @Test
    @DisplayName("Réception — reference persistée (snapshot immuable) : GET la renvoie et elle survit à la mutation de dossier.refeDossier")
    void reception_reference_persistee_immuable() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(330, "SOUMIS");
        d.setIdLocalite("ANT");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM");
        dossierRepository.save(d);

        // POST : la réponse porte la référence structurée <seq>/PPM/CNM/<annee> (segment = sous-type ;
        // dossier de la localité centrale ANT → CNM, ⚠️ règle corrigée 2026-08-04).
        String resp = mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":330,\"numPassage\":1,\"typePassage\":\"INITIAL\",\"complet\":true,"
                        + "\"dateReception\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", org.hamcrest.Matchers.matchesPattern("\\d{5}/PPM/CNM/\\d{4}")))
                .andReturn().getResponse().getContentAsString();
        int idRec = com.jayway.jsonpath.JsonPath.read(resp, "$.idReception");
        String refRecept = com.jayway.jsonpath.JsonPath.read(resp, "$.reference");

        // GET liste : la référence est bien PERSISTÉE sur t_reception (plus null).
        mvc.perform(get("/api/receptions").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idReception==" + idRec + ")].reference", hasItem(refRecept)));

        // Mutation de dossier.refeDossier (simule la restauration de la réf PPM après retrait accepté).
        Dossier maj = dossierRepository.findById(330).orElseThrow();
        maj.setRefeDossier("00007/DGB/PPM/2026");
        dossierRepository.save(maj);

        // La référence de la réception ne bouge pas (snapshot immuable, indépendant du dossier).
        mvc.perform(get("/api/receptions/" + idRec).header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(refRecept))
                .andExpect(jsonPath("$.reference", org.hamcrest.Matchers.not("00007/DGB/PPM/2026")));
    }

    @Test
    @DisplayName("Réception — parsing : date simple → 30/06/2026 (heure complétée) ; date-heure préservée")
    void reception_date_stockee_correctement() {
        // Date seule « yyyy-MM-dd » : jour correct, heure complétée par le serveur (non nulle).
        java.time.LocalDateTime dSimple = cnm.prs.mapper.ReceptionMapper.toLocalDateTime("2026-06-30");
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDate.of(2026, 6, 30), dSimple.toLocalDate());
        // Une date-heure complète « yyyy-MM-dd HH:mm » reste correctement parsée (heure conservée).
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDateTime.of(2026, 6, 30, 14, 30),
                cnm.prs.mapper.ReceptionMapper.toLocalDateTime("2026-06-30 14:30"));
    }

    @Test
    @DisplayName("Dispatch — dateDispatch « yyyy-MM-dd » sans heure → 201 (heure complétée, plus d'erreur index 10)")
    void dispatch_date_simple_acceptee() throws Exception {
        dossierRepository.save(dossier(310, "PRET_DISPATCH"));
        receptionRepository.save(reception(410, 310, "CTRSEC", true));   // CTRSEC = localité ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":310,\"idReception\":410,\"imCtrlMembre\":\"CTRMEM\","
                        + "\"interimDispatch\":false,\"dateDispatch\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateDispatch", org.hamcrest.Matchers.startsWith("2026-06-30 ")));
    }

    @Test
    @DisplayName("Dispatch — parsing : date simple → 30/06/2026 (heure complétée) ; date-heure préservée")
    void dispatch_date_parsing_ok() {
        java.time.LocalDateTime dSimple = cnm.prs.mapper.DispatchMapper.toLocalDateTime("2026-06-30");
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDate.of(2026, 6, 30), dSimple.toLocalDate());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDateTime.of(2026, 6, 30, 14, 30),
                cnm.prs.mapper.DispatchMapper.toLocalDateTime("2026-06-30 14:30"));
    }

    @Test
    @DisplayName("Dispatch — la liste exclut les dossiers BROUILLON (dispatch orphelin après retrait accepté)")
    void dispatch_liste_exclut_brouillon() throws Exception {
        // Dossier redevenu BROUILLON mais qui conserve un dispatch (cas du retrait accepté).
        dossierRepository.save(dossier(320, "BROUILLON"));
        receptionRepository.save(reception(420, 320, "CTRSEC", true));   // CTRSEC = ANT
        dispatchRepository.save(dispatch(320, 420, "CTRCC1", "CTRMEM"));
        mvc.perform(get("/api/dispatchs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                // Le dispatch du dossier BROUILLON est exclu ; aucun dossier BROUILLON dans la liste.
                .andExpect(jsonPath("$[?(@.idDispatch==320)]", hasSize(0)));
    }

    @Test
    @DisplayName("DispatchDto : dateDispatch comporte l'heure (yyyy-MM-dd HH:mm)")
    void dispatch_dto_datetime_ok() throws Exception {
        // Le dispatch 1 (localité ANT) est seedé à 2026-06-03 14:45.
        mvc.perform(get("/api/dispatchs").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDispatch==1)].dateDispatch", hasItem("2026-06-03 14:45")));
    }

    @Test
    @DisplayName("DispatchDto : datePredispatch = date/heure de réception du dossier par le secrétaire")
    void dispatch_dto_predispatch_ok() throws Exception {
        // Dispatch 1 → réception 1 (dossier 1), seedée à 2026-06-02 10:30.
        mvc.perform(get("/api/dispatchs").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDispatch==1)].datePredispatch", hasItem("2026-06-02 10:30")));
    }

    @Test
    @DisplayName("DispatchDto : datePredispatch = null si le dossier n'a aucune réception datée")
    void dispatch_dto_predispatch_null_ok() throws Exception {
        // Réception sans date (dossier 161) + son dispatch → datePredispatch null.
        Dossier d = dossier(161, "DISPATCHE");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Reception r = new Reception();
        r.setIdReception(161);
        r.setIdDossier(161);
        r.setNumPassage(1);
        r.setTypePassage("INITIAL");
        r.setImCtrlRecept("CTRCC1");
        r.setComplet(false); // dateReception laissée à null
        receptionRepository.save(r);
        dispatchRepository.save(dispatch(161, 161, "CTRCC1", "CTRMEM"));

        mvc.perform(get("/api/dispatchs/161").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datePredispatch").value(nullValue()));
    }

    @Test
    @DisplayName("Réceptions : filtre ?idDossier scopé et test /existe (déjà réceptionné) sans charger l'historique")
    void receptions_parDossierEtExiste() throws Exception {
        // Dossier ANT déjà réceptionné = dossier 1 (réception 1, CTRCC1). Dossier ANT sans réception = 220.
        dossierRepository.save(dossierLoc(220, "SOUMIS", "ANT", "PRMP001"));

        // CC ANT : ?idDossier=1 ne renvoie que la réception du dossier 1.
        mvc.perform(get("/api/receptions").header("Authorization", tokenCc).param("idDossier", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // /existe : dossier 1 → reçu ; dossier 220 (aucune réception) → non reçu.
        mvc.perform(get("/api/receptions/dossier/1/existe").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.recu").value(true));
        mvc.perform(get("/api/receptions/dossier/220/existe").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.recu").value(false));
        // Isolation localité : CC ANT n'obtient pas les réceptions du dossier 2 (TMS).
        mvc.perform(get("/api/receptions").header("Authorization", tokenCc).param("idDossier", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // La PRMP (ressource interne) → liste vide même par dossier.
        mvc.perform(get("/api/receptions").header("Authorization", tokenPrmp).param("idDossier", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("[Auto] Réception complète → dossier PRET_DISPATCH")
    void auto_pretDispatch() throws Exception {
        mvc.perform(put("/api/receptions/1").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PRET_DISPATCH"));

        // [Auto] Notification PRET_DISPATCH adressée au Président et au CC de la localité.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='PRET_DISPATCH')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PRET_DISPATCH')].destinataireIm", hasItem("CTRPRE")))
                .andExpect(jsonPath("$[?(@.typeNotif=='PRET_DISPATCH')].destinataireIm", hasItem("CTRCC1")));
    }

    @Test
    @DisplayName("Dispatch → dossier DISPATCHE ; examen refusé tant que le dossier n'est pas dispatché")
    void dispatch_avanceDossierADispatche() throws Exception {
        // A) Dossier PRET_DISPATCH avec un dispatch SEEDÉ en direct (le dossier reste PRET_DISPATCH) :
        //    l'examen est refusé car le dossier n'est pas DISPATCHE.
        dossierRepository.save(dossier(15, "PRET_DISPATCH"));
        receptionRepository.save(reception(25, 15, "CTRSEC", true));
        dispatchRepository.save(dispatch(45, 25, "CTRCC1", "CTRMEM"));
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":45,\"idDispatch\":45,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isConflict());

        // B) Dispatch VIA L'API → le dossier passe à DISPATCHE, et l'examen devient alors permis.
        dossierRepository.save(dossier(16, "PRET_DISPATCH"));
        receptionRepository.save(reception(26, 16, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":46,\"idReception\":26,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/16").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("DISPATCHE"));
        // L'examen est permis pour le Membre attributaire (CTRMEM).
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":46,\"idDispatch\":46,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Garde réception : la 1ʳᵉ réception doit se faire dans la localité du dossier (via ID_LOCALITE)")
    void receptionDansLocaliteDuDossier() throws Exception {
        // Dossier estampillé TMS, aucune réception préalable.
        // ⚠️ Audit 2026-08-27 (lot B) — le statut de la fixture était « RECU », qui n'existe pas dans
        // StatutDossier : la liste blanche des statuts réceptionnables le refuserait à juste titre.
        // SOUMIS est l'état réel d'un dossier qui attend sa première réception.
        Dossier d = dossier(9, "SOUMIS");
        d.setIdLocalite("TMS");
        dossierRepository.save(d);

        // Le Président (toutes localités) peut réceptionner (succès d'abord → pas de rollback-only).
        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":9,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":false}"))
                .andExpect(status().isCreated());
        // Un contrôleur d'ANT (CC, délégué Secrétaire) ne peut pas réceptionner un dossier TMS → 403.
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":9,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("File à réceptionner : dossiers SOUMIS de la localité sans réception (Secrétaire) ; cloisonnement et exclusions")
    void fileAReceptionner() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // SOUMIS ANT sans réception → à réceptionner.
        Dossier a = dossier(110, "SOUMIS"); a.setIdLocalite("ANT"); dossierRepository.save(a);
        // BROUILLON ANT → exclu.
        Dossier b = dossier(111, "BROUILLON"); b.setIdLocalite("ANT"); dossierRepository.save(b);
        // SOUMIS TMS → pas pour le Secrétaire d'ANT.
        Dossier c = dossier(112, "SOUMIS"); c.setIdLocalite("TMS"); dossierRepository.save(c);
        // SOUMIS ANT déjà réceptionné → exclu.
        Dossier d = dossier(113, "SOUMIS"); d.setIdLocalite("ANT"); dossierRepository.save(d);
        receptionRepository.save(reception(113, 113, "CTRSEC", false));

        // Secrétaire d'ANT : seul le 110.
        mvc.perform(get("/api/dossiers/a-receptionner").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==110)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==111)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==112)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==113)]", hasSize(0)));
        // Le Président voit toutes les localités (110 ANT + 112 TMS).
        mvc.perform(get("/api/dossiers/a-receptionner").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idDossier==110)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==112)]", hasSize(1)));
        // Un Membre n'y a pas accès → 403.
        mvc.perform(get("/api/dossiers/a-receptionner").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Gardes d'état de la réception (⚠️ audit 2026-08-27, lot B — constat 4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Réception (lot B) — un dossier qui a quitté le secrétariat n'est plus réceptionnable : "
            + "EN_VERIFICATION et CLOTURE → 409, et aucune régression vers PRET_DISPATCH")
    void reception_statutsAval_refuses() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        dossierRepository.save(dossierLoc(340, "EN_VERIFICATION", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(341, "CLOTURE", "ANT", "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":340,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":341,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(status().isConflict());
        // Le dossier en vérification n'a pas régressé (avant le correctif : PRET_DISPATCH).
        mvc.perform(get("/api/dossiers/340").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
    }

    @Test
    @DisplayName("Réception (lot B) — le PUT rejoue la précondition d'état : corriger une réception d'un dossier "
            + "passé PV_SIGNE → 409, statut inchangé")
    void reception_put_rejoueLaGardeDEtat() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(342, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        String resp = mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":342,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idRec = com.jayway.jsonpath.JsonPath.read(resp, "$.idReception");

        // Le circuit avance jusqu'au PV signé : la réception n'est plus corrigeable.
        Dossier avance = dossierRepository.findById(342).orElseThrow();
        avance.setStatut("PV_SIGNE");
        dossierRepository.save(avance);
        mvc.perform(put("/api/receptions/" + idRec).header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":342,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/dossiers/342").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("PV_SIGNE"));
    }

    @Test
    @DisplayName("Réception (lot B) — un seul passage INITIAL par dossier : le second → 409 ; un RETOUR reste accepté")
    void reception_antiDoublonPassageInitial() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(343, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdTypeDossier("DDP"); d.setIdSousType("PPM");
        dossierRepository.save(d);

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":343,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":343,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":false}"))
                .andExpect(status().isConflict());
        // NON-RÉGRESSION : un passage RETOUR (2e) reste enregistrable.
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":343,\"numPassage\":2,\"typePassage\":\"RETOUR\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":false}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Réception (lot B) — la référence officielle du dossier est produite au PREMIER passage seulement : "
            + "un RETOUR la reprend telle quelle et ne consomme pas la séquence")
    void reception_referenceGenereeAuPremierPassageSeulement() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(344, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdTypeDossier("DDP"); d.setIdSousType("PPM");
        dossierRepository.save(d);
        ppmRepository.save(ppm(344, 344, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":344,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        // Passage RETOUR : même référence, le dossier n'est pas renommé.
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":344,\"numPassage\":2,\"typePassage\":\"RETOUR\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        mvc.perform(get("/api/dossiers/344").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.refeDossier").value("00001/PPM/CNM/2026"));

        // La séquence de l'année n'a pas été consommée par le retour : le dossier suivant prend 00002.
        Dossier suivant = dossier(345, "SOUMIS");
        suivant.setIdLocalite("ANT"); suivant.setIdTypeDossier("DDP"); suivant.setIdSousType("PPM");
        dossierRepository.save(suivant);
        ppmRepository.save(ppm(345, 345, "PRMP001"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":345,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("00002/PPM/CNM/2026"));
    }

    @Test
    @DisplayName("Réception interdite si le dossier est en BROUILLON → 409")
    void receptionBrouillon_interdite() throws Exception {
        Dossier d = dossier(67, "BROUILLON");
        d.setIdLocalite("ANT");
        d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":67,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":false}"))
                .andExpect(status().isConflict());
    }
}
