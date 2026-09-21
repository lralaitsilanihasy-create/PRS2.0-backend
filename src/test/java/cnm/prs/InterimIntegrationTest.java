package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Notification;
import cnm.prs.entity.PvExamen;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.repository.InterimRepository;
import cnm.prs.repository.NotificationRepository;

/**
 * ⚠️ <strong>Intérim désigné — lot 1 : Président et Chef de commission</strong> (demande front du 2026-09-21,
 * arbitrages du pilote du même jour). Les douze cas de la recette backend de la demande, dans l'ordre.
 *
 * <p>Décor, en plus du socle : la Centrale (ANT) a un second CC ({@code CTRCC3}) et un second Membre
 * ({@code CTRMEM3}) ; la commission régionale de Toamasina (TMS, CC {@code CTRCC2}) a deux Membres
 * ({@code CTRMEM2}, {@code CTRMEM4}). Toutes les dates sont relatives à {@code LocalDate.now()}.</p>
 */
class InterimIntegrationTest extends CnmIntegrationTestSupport {

    private static final LocalDate AUJOURDHUI = LocalDate.now();

    @Autowired private InterimRepository interimRepository;
    @Autowired private NotificationRepository notificationRepository;

    private String tokenCcTms;
    private String tokenMembreTms;
    private String tokenCcCentrale2;

    @BeforeEach
    void decor() {
        controleurRepository.save(controleur("CTRCC3", 3, "ANT"));    // second CC de la Centrale
        controleurRepository.save(controleur("CTRMEM3", 5, "ANT"));   // second Membre de la Centrale
        controleurRepository.save(controleur("CTRMEM2", 5, "TMS"));   // Membre de Toamasina
        controleurRepository.save(controleur("CTRMEM4", 5, "TMS"));   // autre Membre de Toamasina
        tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        tokenMembreTms = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "TMS");
        tokenCcCentrale2 = bearer("CTRCC3", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC3", "ANT");
    }

    // ================================================================== 1 — le Président désigne un CC

    @Test
    @DisplayName("1 — Le Président désigne un CC d'une autre localité : 201, A_VENIR avant dateDebut, ACTIF pendant")
    void president_designeCc_aVenirPuisActif() throws Exception {
        designer(tokenPresident, "CTRPRE", "CTRCC2", AUJOURDHUI.plusDays(10), AUJOURDHUI.plusDays(12), "MISSION", pdfMinimal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("A_VENIR"))
                .andExpect(jsonPath("$.profilTitulaire").value("PRESIDENT"))
                .andExpect(jsonPath("$.idLocaliteTitulaire").isEmpty())
                .andExpect(jsonPath("$.nomTitulaire").value("NomCTRPRE Prenoms"))
                .andExpect(jsonPath("$.pieceDisponible").value(true))
                .andExpect(jsonPath("$.designePar").value("CTRPRE"));
        String actif = designer(tokenPresident, "CTRPRE", "CTRCC2", AUJOURDHUI, AUJOURDHUI.plusDays(2), "CONGE", pdfMinimal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIF"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(actif, "$.idInterim");
        mvc.perform(get("/api/interims/" + id + "/piece").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        // Le titulaire le voit en « subi », l'intérimaire en « exercés » ; l'intérim à venir figure chez les deux.
        mvc.perform(get("/api/interims/mes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.subi.idInterim").value(id))
                .andExpect(jsonPath("$.exerces", hasSize(0)))
                .andExpect(jsonPath("$.aVenir", hasSize(1)));
        mvc.perform(get("/api/interims/mes").header("Authorization", tokenCcTms))
                .andExpect(jsonPath("$.exerces", hasSize(1)))
                .andExpect(jsonPath("$.exerces[0].imTitulaire").value("CTRPRE"))
                .andExpect(jsonPath("$.subi").isEmpty())
                .andExpect(jsonPath("$.aVenir", hasSize(1)));
        // L'annuaire porte les deux liens (§B5).
        mvc.perform(get("/api/controleurs/CTRPRE").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.interimEnCours.imInterimaire").value("CTRCC2"))
                .andExpect(jsonPath("$.interimPour", hasSize(0)));
        mvc.perform(get("/api/controleurs/CTRCC2").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.interimEnCours").isEmpty())
                .andExpect(jsonPath("$.interimPour[0].imTitulaire").value("CTRPRE"));
    }

    // ================================================================== 2 — CC régional

    @Test
    @DisplayName("2 — CC régional : un CC d'une autre localité est refusé (409 nominatif) ; un Membre de sa localité passe (201)")
    void ccRegional_designe() throws Exception {
        designer(tokenCcTms, "CTRCC2", "CTRCC1", AUJOURDHUI, AUJOURDHUI.plusDays(3), "CONGE", pdfMinimal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("NomCTRCC1 Prenoms")))
                .andExpect(jsonPath("$.message", containsString("TMS")));
        designer(tokenCcTms, "CTRCC2", "CTRMEM2", AUJOURDHUI, AUJOURDHUI.plusDays(3), "CONGE", pdfMinimal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profilInterimaire").value("MEMBRE"))
                .andExpect(jsonPath("$.idLocaliteTitulaire").value("TMS"));
    }

    // ================================================================== 3 — CC de la Centrale

    @Test
    @DisplayName("3 — CC de la Centrale : un autre CC de la Centrale passe (201) ; un Membre régional est refusé (409)")
    void ccCentrale_designe() throws Exception {
        designer(tokenCc, "CTRCC1", "CTRCC3", AUJOURDHUI, AUJOURDHUI.plusDays(3), "MISSION", pdfMinimal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profilInterimaire").value("CHEF_COMMISSION"));
        designer(tokenCcCentrale2, "CTRCC3", "CTRMEM2", AUJOURDHUI, AUJOURDHUI.plusDays(3), "MISSION", pdfMinimal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("NomCTRMEM2 Prenoms")));
    }

    // ================================================================== 4 — chevauchement

    @Test
    @DisplayName("4 — Deux intérims qui se chevauchent pour le même titulaire : 409 ; sans chevauchement : 201")
    void chevauchement() throws Exception {
        designer(tokenCcTms, "CTRCC2", "CTRMEM2", AUJOURDHUI, AUJOURDHUI.plusDays(5), "CONGE", pdfMinimal())
                .andExpect(status().isCreated());
        designer(tokenCcTms, "CTRCC2", "CTRMEM4", AUJOURDHUI.plusDays(5), AUJOURDHUI.plusDays(8), "MISSION", pdfMinimal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("chevauche")));
        designer(tokenCcTms, "CTRCC2", "CTRMEM4", AUJOURDHUI.plusDays(6), AUJOURDHUI.plusDays(8), "MISSION", pdfMinimal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("A_VENIR"));
        // Le cumul côté intérimaire est signalé, pas interdit : CTRMEM4 supplée déjà CTRCC2 sur cette période.
        String cumul = designer(tokenCcCentrale2, "CTRCC3", "CTRMEM3", AUJOURDHUI, AUJOURDHUI.plusDays(2), "AUTRE", pdfMinimal())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(cumul, "$.avertissements")).isEmpty();
    }

    // ================================================================== 5 — refus de forme

    @Test
    @DisplayName("5 — Titulaire = intérimaire : 409 ; sans pièce : 400 ; dateFin absente hors VACANCE_POSTE : 400 ; "
            + "VACANCE_POSTE sans fin : 201 ; un tiers qui désigne pour un autre : 403 nominatif ; l'Admin en repli : 201")
    void refus_de_forme() throws Exception {
        designer(tokenCcTms, "CTRCC2", "CTRCC2", AUJOURDHUI, AUJOURDHUI.plusDays(3), "CONGE", pdfMinimal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("propre intérimaire")));
        designer(tokenCcTms, "CTRCC2", "CTRMEM2", AUJOURDHUI, AUJOURDHUI.plusDays(3), "CONGE", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Pièce requise")));
        designer(tokenCcTms, "CTRCC2", "CTRMEM2", AUJOURDHUI, AUJOURDHUI.plusDays(3), "CONGE",
                "pas un pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("PDF")));
        designer(tokenCcTms, "CTRCC2", "CTRMEM2", AUJOURDHUI, null, "CONGE", pdfMinimal())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("date de fin")));
        // Un CC ne désigne pas pour un autre : seul le titulaire (ou l'Administrateur) déclare une absence.
        designer(tokenCcCentrale2, "CTRCC2", "CTRMEM2", AUJOURDHUI, AUJOURDHUI.plusDays(3), "CONGE", pdfMinimal())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Seul NomCTRCC2 Prenoms")));
        // Un Membre ne déclare pas d'absence dans ce lot.
        designer(tokenAdmin, "CTRMEM", "CTRMEM3", AUJOURDHUI, AUJOURDHUI.plusDays(3), "CONGE", pdfMinimal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("ne déclare pas d'absence")));
        designer(tokenCcTms, "CTRCC2", "CTRMEM2", AUJOURDHUI, null, "VACANCE_POSTE", pdfMinimal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateFin").isEmpty())
                .andExpect(jsonPath("$.statut").value("ACTIF"));
        // L'Administrateur désigne en repli, pour un titulaire qui n'a rien déclaré.
        designer(tokenAdmin, "CTRCC1", "CTRMEM", AUJOURDHUI, AUJOURDHUI.plusDays(1), "MALADIE", pdfMinimal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designePar").value("CTRADM"));
    }

    // ================================================================== 6 — le Membre intérimaire dispatche

    @Test
    @DisplayName("6 — Membre intérimaire d'un CC : dispatch dans la localité SANS interimDispatch → 201, interimDe servi, "
            + "journal « par intérim de X », chrono acteur = le Membre au profil du CC")
    void membreInterimaire_dispatche() throws Exception {
        interimActif("CTRCC2", "CTRMEM2", tokenCcTms);
        dossierPretADispatcher(9700, "TMS", "CTRCC2");
        String dispatch = dispatcher(tokenMembreTms, 9700, "CTRMEM4")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlDispatch").value("CTRCC2"))
                .andExpect(jsonPath("$.interimDe").value("CTRCC2"))
                .andExpect(jsonPath("$.idInterim").isNumber())
                .andExpect(jsonPath("$.interimDispatch").value(false))
                .andExpect(jsonPath("$.imCtrlCc").isEmpty())
                .andReturn().getResponse().getContentAsString();
        int idInterim = JsonPath.read(dispatch, "$.idInterim");

        String journal = mvc.perform(get("/api/dossiers/9700/journal").header("Authorization", tokenCcTms))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='DISPATCH')].interimDe")).containsExactly("CTRCC2");
        assertThat(JsonPath.<List<Integer>>read(journal, "$[?(@.typeAction=='DISPATCH')].idInterim")).containsExactly(idInterim);
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='DISPATCH')].nomOperateur")).containsExactly("NomCTRMEM2 Prenoms");
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='DISPATCH')].detail").get(0))
                .contains("par intérim de NomCTRCC2 Prenoms");

        String chrono = mvc.perform(get("/api/dossiers/9700/chronometrage").header("Authorization", tokenCcTms))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(chrono, "$.etapes[?(@.etape=='DISPATCH')].imActeur")).containsExactly("CTRMEM2");
        assertThat(JsonPath.<List<String>>read(chrono, "$.etapes[?(@.etape=='DISPATCH')].interimDe")).containsExactly("CTRCC2");
        assertThat(JsonPath.<List<String>>read(chrono, "$.etapes[?(@.etape=='DISPATCH')].profil")).containsExactly("CHEF_COMMISSION");
        // Le Membre attributaire a été notifié ; le CC titulaire n'est pas concerné par cette notification-là.
        assertThat(notificationRepository.findPourControleur("CTRMEM4")).hasSize(1);
    }

    // ================================================================== 7 — le Membre intérimaire vise

    @Test
    @DisplayName("7 — Ce Membre vise un PV dispatché par le CC → 200 viseParInterim, idInterim, sans note ; "
            + "un PV dont il est l'attributaire → 409 nominatif")
    void membreInterimaire_vise() throws Exception {
        int idInterim = interimActif("CTRCC2", "CTRMEM2", tokenCcTms);
        projetSoumis(9701, "TMS", "CTRCC2", "CTRMEM4", 9711);
        viser(9711, tokenMembreTms, "CTRMEM2", "FAV", null, "CTRMEM4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"))
                .andExpect(jsonPath("$.viseParInterim").value(true))
                .andExpect(jsonPath("$.idInterim").value(idInterim))
                .andExpect(jsonPath("$.interimDe").value("CTRCC2"))
                .andExpect(jsonPath("$.noteInterimDisponible").value(false))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRMEM2"))
                .andExpect(jsonPath("$.imDispatcheur").value("CTRCC2"));
        String journal = mvc.perform(get("/api/dossiers/9701/journal").header("Authorization", tokenCcTms))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='VISA')].interimDe")).containsExactly("CTRCC2");
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='VISA')].detail").get(0))
                .contains("par intérim de NomCTRCC2 Prenoms");
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='SIGNATURE')].interimDe")).containsExactly("CTRCC2");
        // La note d'intérim n'existe pas : la désignation est la justification.
        mvc.perform(get("/api/pv-examens/9711/note-interim").header("Authorization", tokenCcTms))
                .andExpect(status().isNotFound());

        // Il est l'attributaire de ce second dossier : celui qui examine ne vise pas, pas même par intérim.
        projetSoumis(9702, "TMS", "CTRCC2", "CTRMEM2", 9712);
        viser(9712, tokenMembreTms, "CTRMEM2", "FAV", null, "CTRMEM4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Vous êtes l'attributaire de ce dossier")));
    }

    // ================================================================== 8 — le CC intérimaire du Président

    @Test
    @DisplayName("8 — CC intérimaire du Président : pré-dispatch central → 201 ; VISA#2 et part Président → 200 ; "
            + "part CC déjà signée → la part Président attend (409 nominatif)")
    void ccInterimaireDuPresident() throws Exception {
        int idInterim = interimActif("CTRPRE", "CTRCC2", tokenPresident);
        // Pré-dispatch d'un dossier CENTRAL par le CC de Toamasina, au nom du Président.
        dossierPretADispatcher(9703, "ANT", "CTRCC1");
        dispatcher(tokenCcTms, 9703, "CTRMEM")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlDispatch").value("CTRPRE"))
                .andExpect(jsonPath("$.interimDe").value("CTRPRE"))
                .andExpect(jsonPath("$.idInterim").value(idInterim))
                // Le Président dispatche à un Membre : le CC de la localité est associé, comme pour lui.
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
        // Il voit le dossier central, comme le Président.
        mvc.perform(get("/api/dossiers/9703").header("Authorization", tokenCcTms)).andExpect(status().isOk());

        // VISA#2 : navette à deux niveaux (central, dispatcheur CC ≠ attributaire), projet transmis au Président.
        projetSoumis(9704, "ANT", "CTRCC1", "CTRMEM", 9714);
        PvExamen pv = pvExamenRepository.findById(9714).orElseThrow();
        pv.setNiveauNavette("PRESIDENT");
        pvExamenRepository.save(pv);
        viser(9714, tokenCcTms, "CTRCC2", "FAV", null, "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imCtrlPresident").value("CTRCC2"))
                .andExpect(jsonPath("$.dateSignaturePresident").isNotEmpty())
                .andExpect(jsonPath("$.viseParInterim").value(true))
                .andExpect(jsonPath("$.interimDe").value("CTRPRE"));

        // Cas résiduel Q3 : il a déjà signé la part CC de ce PV — la part Président attend le Président.
        projetSoumis(9705, "ANT", "CTRCC1", "CTRMEM", 9715);
        PvExamen dejaSigne = pvExamenRepository.findById(9715).orElseThrow();
        dejaSigne.setNiveauNavette("PRESIDENT");
        dejaSigne.setImCcCoSignataire("CTRCC2");
        dejaSigne.setImCtrlCc("CTRCC2");
        dejaSigne.setDateSignatureCc(AUJOURDHUI);
        pvExamenRepository.save(dejaSigne);
        viser(9715, tokenCcTms, "CTRCC2", "FAV", null, "CTRMEM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("déjà signé une autre part")));
    }

    // ================================================================== 9 — la chaîne n'est pas transitive

    @Test
    @DisplayName("9 — Chaîne : le CC intérimaire du Président désigne un Membre de sa localité pour SON rôle de CC ; "
            + "ce Membre signe la part CC, et un acte du Président lui est refusé (403)")
    void chaine_nonTransitive() throws Exception {
        interimActif("CTRPRE", "CTRCC2", tokenPresident);
        interimActif("CTRCC2", "CTRMEM2", tokenCcTms);
        // Part CC déléguée : le Président a désigné CTRCC2 co-signataire ; CTRCC2 absent, CTRMEM2 signe à sa place.
        projetSoumis(9706, "TMS", "CTRPRE", "CTRMEM4", 9716);
        PvExamen pv = pvExamenRepository.findById(9716).orElseThrow();
        pv.setStatutPv("PROJET_ACCEPTE");
        pv.setImCtrlPresident("CTRPRE");
        pv.setDateSignaturePresident(AUJOURDHUI);
        pv.setImCcCoSignataire("CTRCC2");
        pv.setImMembreCoSignataire("CTRMEM4");
        pvExamenRepository.save(pv);
        mvc.perform(post("/api/pv-examens/9716/signer").header("Authorization", tokenMembreTms)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM2\",\"role\":\"CC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imCtrlCc").value("CTRMEM2"))
                .andExpect(jsonPath("$.dateSignatureCc").isNotEmpty());
        String chrono = mvc.perform(get("/api/dossiers/9706/chronometrage").header("Authorization", tokenCcTms))
                .andReturn().getResponse().getContentAsString();
        // Le passage CLOS de la part CC porte le titulaire ; l'occurrence encore ouverte (part Membre) ne porte rien.
        assertThat(JsonPath.<List<String>>read(chrono, "$.etapes[?(@.etape=='COSIGNATURE' && @.enCours==false)].interimDe"))
                .containsExactly("CTRCC2");
        // Non transitif : CTRMEM2 supplée CTRCC2, jamais le Président — un dossier central lui est refusé.
        dossierPretADispatcher(9707, "ANT", "CTRCC1");
        dispatcher(tokenMembreTms, 9707, "CTRMEM")
                .andExpect(status().isForbidden());
    }

    // ================================================================== 10 — copie des notifications

    @Test
    @DisplayName("10 — Notification émise au titulaire pendant l'intérim : deux lignes (titulaire, intérimaire avec "
            + "interimDe) ; hors période : une seule")
    void notifications_copiees() throws Exception {
        int idInterim = interimActif("CTRCC2", "CTRMEM2", tokenCcTms);
        notificationService.emettre(2, TypeNotification.PRET_DISPATCH, "CTRCC2", "ctrcc2@cnm.mg",
                "Dossier prêt", "Le dossier 2 est prêt à dispatcher.");
        List<Notification> titulaire = notificationRepository.findPourControleur("CTRCC2");
        List<Notification> interimaire = notificationRepository.findPourControleur("CTRMEM2");
        assertThat(titulaire).hasSize(1);
        assertThat(titulaire.get(0).getInterimDe()).isNull();
        assertThat(interimaire).hasSize(1);
        assertThat(interimaire.get(0).getInterimDe()).isEqualTo("CTRCC2");
        assertThat(interimaire.get(0).getIdInterim()).isEqualTo(idInterim);
        assertThat(interimaire.get(0).getTypeNotif()).isEqualTo("PRET_DISPATCH");
        assertThat(interimaire.get(0).getDestinataireEmail()).isEqualTo("ctrmem2@cnm.mg");
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembreTms))
                .andExpect(jsonPath("$[0].interimDe").value("CTRCC2"));

        // Hors période (intérim à venir sur CTRCC3) : une seule ligne.
        designer(tokenCcCentrale2, "CTRCC3", "CTRMEM3", AUJOURDHUI.plusDays(3), AUJOURDHUI.plusDays(4), "CONGE", pdfMinimal())
                .andExpect(status().isCreated());
        notificationService.emettre(1, TypeNotification.PRET_DISPATCH, "CTRCC3", "ctrcc3@cnm.mg", "Dossier prêt", "…");
        assertThat(notificationRepository.findPourControleur("CTRCC3")).hasSize(1);
        assertThat(notificationRepository.findPourControleur("CTRMEM3")).isEmpty();
    }

    // ================================================================== 11 — révocation

    @Test
    @DisplayName("11 — Révocation : la requête suivante de l'intérimaire sur un acte du titulaire → 403 ; « mes » ne le sert plus ; "
            + "déjà révoqué → 409 ; date antérieure à aujourd'hui → 400")
    void revocation() throws Exception {
        int idInterim = interimActif("CTRCC2", "CTRMEM2", tokenCcTms);
        dossierPretADispatcher(9708, "TMS", "CTRCC2");
        mvc.perform(get("/api/interims/mes").header("Authorization", tokenMembreTms))
                .andExpect(jsonPath("$.exerces", hasSize(1)));
        // Un tiers ne révoque pas.
        mvc.perform(post("/api/interims/" + idInterim + "/revoquer").header("Authorization", tokenCcCentrale2)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"retour anticipé\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/interims/" + idInterim + "/revoquer").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"retour anticipé\",\"dateRevocation\":\"" + AUJOURDHUI.minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/interims/" + idInterim + "/revoquer").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"retour anticipé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REVOQUE"))
                .andExpect(jsonPath("$.dateRevocation").value(AUJOURDHUI.toString()))
                .andExpect(jsonPath("$.revoquePar").value("CTRCC2"));
        mvc.perform(post("/api/interims/" + idInterim + "/revoquer").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"encore\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/interims/mes").header("Authorization", tokenMembreTms))
                .andExpect(jsonPath("$.exerces", hasSize(0)));
        // Les droits sont tombés : le Membre ne dispatche plus.
        dispatcher(tokenMembreTms, 9708, "CTRMEM4").andExpect(status().isForbidden());
        // Et l'historique reste : la révocation ne défait rien.
        mvc.perform(get("/api/interims?titulaire=CTRCC2").header("Authorization", tokenCcTms))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].statut").value("REVOQUE"));
        mvc.perform(get("/api/interims?actifs=true").header("Authorization", tokenCcTms))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ================================================================== 12 — PRMP et accueil « À faire »

    @Test
    @DisplayName("12 — PRMP : GET /api/interims et /piece → 403 ; « À faire » de l'intérimaire : tâches du titulaire en "
            + "delegations.taches mode INTERIM avec interimDe ; celles du titulaire inchangées")
    void prmp_et_aFaire() throws Exception {
        int idInterim = interimActif("CTRCC2", "CTRMEM2", tokenCcTms);
        mvc.perform(get("/api/interims").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/interims/mes").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/interims/" + idInterim + "/piece").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        // L'annuaire ne dit rien à la PRMP (règle C2).
        mvc.perform(get("/api/controleurs/CTRCC2").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.interimEnCours").isEmpty());

        dossierPretADispatcher(9709, "TMS", "CTRCC2");
        String titulaire = mvc.perform(get("/api/dossiers/a-faire").header("Authorization", tokenCcTms))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(titulaire, "$.taches[?(@.dossier.idDossier==9709)].mode"))
                .containsExactly("TITULAIRE");

        String interimaire = mvc.perform(get("/api/dossiers/a-faire?delegations=true").header("Authorization", tokenMembreTms))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(interimaire, "$.taches[?(@.dossier.idDossier==9709)]")).isEmpty();
        assertThat(JsonPath.<List<String>>read(interimaire, "$.delegations.taches[?(@.dossier.idDossier==9709)].mode"))
                .containsExactly("INTERIM");
        assertThat(JsonPath.<List<String>>read(interimaire, "$.delegations.taches[?(@.dossier.idDossier==9709)].geste"))
                .containsExactly("DISPATCHER");
        assertThat(JsonPath.<List<String>>read(interimaire, "$.delegations.taches[?(@.dossier.idDossier==9709)].interimDe"))
                .containsExactly("CTRCC2");
        assertThat(JsonPath.<List<Integer>>read(interimaire, "$.delegations.taches[?(@.dossier.idDossier==9709)].idInterim"))
                .containsExactly(idInterim);
        // Et la page dossier dit la même chose (invariant de parité).
        String gestes = mvc.perform(get("/api/dossiers/9709/gestes").header("Authorization", tokenMembreTms))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(gestes, "$.taches[*].mode")).containsExactly("INTERIM");
        assertThat(JsonPath.<List<String>>read(gestes, "$.taches[*].interimDe")).containsExactly("CTRCC2");
    }

    // ================================================================== décor et gestes

    /** Désignation par multipart : partie {@code data} (JSON) + partie {@code piece} (PDF, {@code null} = absente). */
    private ResultActions designer(String token, String titulaire, String interimaire, LocalDate debut, LocalDate fin,
            String motif, byte[] piece) throws Exception {
        String json = "{\"imTitulaire\":\"" + titulaire + "\",\"imInterimaire\":\"" + interimaire + "\","
                + "\"dateDebut\":\"" + debut + "\"" + (fin == null ? "" : ",\"dateFin\":\"" + fin + "\"")
                + ",\"motif\":\"" + motif + "\",\"reference\":\"NS-2026-" + titulaire + "\"}";
        MockMultipartHttpServletRequestBuilder requete = MockMvcRequestBuilders.multipart("/api/interims")
                .file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (piece != null) {
            requete = requete.file(new MockMultipartFile("piece", "designation.pdf", MediaType.APPLICATION_PDF_VALUE, piece));
        }
        return mvc.perform(requete.header("Authorization", token));
    }

    /** Un intérim ACTIF (d'aujourd'hui à dans trois jours), désigné par le titulaire ; renvoie son identifiant. */
    private int interimActif(String titulaire, String interimaire, String tokenTitulaire) throws Exception {
        String corps = designer(tokenTitulaire, titulaire, interimaire, AUJOURDHUI, AUJOURDHUI.plusDays(3), "CONGE", pdfMinimal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIF"))
                .andReturn().getResponse().getContentAsString();
        assertThat(interimRepository.findById(JsonPath.<Integer>read(corps, "$.idInterim"))).isPresent();
        return JsonPath.read(corps, "$.idInterim");
    }

    /** Un dossier PRET_DISPATCH d'une localité, reçu (complet) par le contrôleur donné ; réception = même numéro. */
    private void dossierPretADispatcher(int id, String localite, String imRecept) {
        dossierRepository.save(dossierLoc(id, "PRET_DISPATCH", localite, "PRMP001"));
        receptionRepository.save(reception(id, id, imRecept, true));
    }

    private ResultActions dispatcher(String token, int idReception, String membre) throws Exception {
        return mvc.perform(post("/api/dispatchs").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":" + idReception + ",\"imCtrlMembre\":\"" + membre + "\","
                        + "\"interimDispatch\":false}"));
    }

    /** Dossier EXAMINE d'une localité, dispatché par {@code dispatcheur} à {@code membre}, projet de PV soumis. */
    private void projetSoumis(int idDossier, String localite, String dispatcheur, String membre, int idPv) {
        dossierRepository.save(dossierLoc(idDossier, "EXAMINE", localite, "PRMP001"));
        receptionRepository.save(reception(idDossier, idDossier,
                "TMS".equals(localite) ? "CTRCC2" : "CTRCC1", true));
        dispatchRepository.save(dispatch(idDossier, idDossier, null, membre, dispatcheur));
        examenRepository.save(examen(idDossier, idDossier, membre));
        PvExamen pv = new PvExamen();
        pv.setIdPv(idPv);
        pv.setIdExamen(idDossier);
        pv.setIdAvis("FAV");
        pv.setImCtrlMembre(membre);
        pv.setStatutPv("PROJET_SOUMIS");
        pv.setNbNavettes(0);
        pvExamenRepository.save(pv);
    }
}
