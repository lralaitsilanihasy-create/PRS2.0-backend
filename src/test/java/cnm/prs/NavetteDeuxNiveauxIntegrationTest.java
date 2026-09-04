package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import cnm.prs.entity.PvNavette;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.SensNavette;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.PvNavetteRepository;

/**
 * ⚠️ <strong>Navette du PV à DEUX NIVEAUX</strong> (spec pilote du 2026-09-04) — « pour le dossier de
 * dispatch à deux niveaux (Président vers CC, puis CC vers Membre), la navette du projet de PV se fait
 * à deux niveaux aussi ».
 *
 * <p>Ce que cette classe éprouve tient en une phrase : <strong>rien ne saute d'étage</strong>. Ni à la
 * montée (le Président ne vise pas avant que le CC ait transmis), ni à la descente (le Président ne
 * renvoie pas au Membre par-dessus le CC qui avait accepté), ni dans les rôles (le CC n'arrête pas
 * l'avis, le Président ne se substitue pas au CC).</p>
 *
 * <p>Le socle est le dossier 1 de la fixture — dispatché en centrale, avec son PPM et ses lignes de
 * marché : c'est le seul qui permette d'observer AUSSI ce que le document imprimera (test 8). Il est
 * requalifié en deux-niveaux par {@link #dispatchReattribueParLeCc()}, qui reproduit ce que fait une
 * réattribution réelle : le CC devient le dispatcheur courant, le Membre reste l'attributaire.</p>
 */
class NavetteDeuxNiveauxIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private PvNavetteRepository navetteRepository;

    /** Un second Membre de la CENTRALE — la combinaison « Président + un autre Membre » (arbitrage 3). */
    private static final String AUTRE_MEMBRE_ANT = "MEMANT8";

    /** Un Membre d'une AUTRE localité — il ne peut pas co-signer un PV de la centrale (§3.3). */
    private static final String MEMBRE_TMS = "MEMTMS8";

    @BeforeEach
    void membresSupplementaires() {
        controleurRepository.save(controleur(AUTRE_MEMBRE_ANT, 5, "ANT"));
        controleurRepository.save(controleur(MEMBRE_TMS, 5, "TMS"));
    }

    /**
     * Requalifie le dispatch 1 en <strong>deux niveaux</strong> : le CC en devient le dispatcheur
     * courant, l'attributaire reste le Membre.
     *
     * <p>C'est exactement l'état que laisse une réattribution réelle — {@code DispatchService.update}
     * réécrit {@code IM_CTRL_DISPATCH} depuis le jeton de l'acteur, sur la même ligne. Passer par
     * l'API demanderait de rejouer tout l'amont (réception, dispatch initial, purge de l'examen) pour
     * un état que deux champs décrivent entièrement.</p>
     */
    private void dispatchReattribueParLeCc() {
        var dispatch = dispatchRepository.findById(1).orElseThrow();
        dispatch.setImCtrlDispatch("CTRCC1");
        dispatch.setImCtrlMembre("CTRMEM");
        dispatch.setImCtrlCc(null);   // le CC dispatcheur n'est pas « en copie » de son propre dispatch
        dispatchRepository.save(dispatch);
    }

    /** Crée le projet de PV sur l'examen 1 et le SOUMET (le Membre est CTRMEM). */
    private void projetSoumis(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(status().isOk());
    }

    /** Projet soumis SUR un circuit à deux niveaux (l'ordre compte : la soumission pose l'étage). */
    private void projetSoumisDeuxNiveaux(int idPv) throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(idPv);
    }

    /** {@code POST /{id}/accepter}. */
    private ResultActions accepter(int idPv, String token, String commentaire) throws Exception {
        return mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commentaire\":\"" + commentaire + "\"}"));
    }

    /** {@code POST /{id}/retourner}. */
    private ResultActions retourner(int idPv, String token, String commentaire) throws Exception {
        return mvc.perform(post("/api/pv-examens/" + idPv + "/retourner").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commentaire\":\"" + commentaire + "\"}"));
    }

    /** {@code POST /{id}/viser} avec la LISTE de co-signataires (contrat du 2026-09-04). */
    private ResultActions viserAvec(int idPv, String token, String avis, String... coSignataires) throws Exception {
        String liste = String.join("\",\"", coSignataires);
        return mvc.perform(post("/api/pv-examens/" + idPv + "/viser").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"" + avis + "\",\"coSignataires\":["
                        + (coSignataires.length == 0 ? "" : "\"" + liste + "\"") + "]}"));
    }

    /** {@code POST /{id}/signer}. */
    private ResultActions signer(int idPv, String token, String role) throws Exception {
        return mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"));
    }

    private List<PvNavette> navettes(int idPv, SensNavette sens) {
        return navetteRepository.findAll().stream()
                .filter(n -> n.getIdPv() != null && n.getIdPv() == idPv)
                .filter(n -> sens.name().equals(n.getSens())).toList();
    }

    private String niveau(int idPv) {
        return pvExamenRepository.findById(idPv).orElseThrow().getNiveauNavette();
    }

    // ------------------------------------------------------------------
    // 1 — Le verrou par niveau, à la montée
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1 — Soumission à deux niveaux : le CC est notifié ; le Président n'accepte pas avant lui, "
            + "et le CC ne vise pas")
    void soumission_notifieLeCc_etLesGestesHorsEtageSontRefuses() throws Exception {
        projetSoumisDeuxNiveaux(9601);

        Assertions.assertEquals("CC", niveau(9601), "le projet part à l'étage du CC, pas du Président");
        // Le destinataire est le CC parce qu'il est le dispatcheur COURANT — c'est déjà ce que fait
        // notifierPvAValider ; la règle du 04/09 ne le change pas, elle le rend intentionnel.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')]", Matchers.hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')].idObjet", Matchers.hasItem(9601)));

        // Le Président ne peut pas accepter à la place du CC : l'acceptation est l'acte de l'étage du bas.
        accepter(9601, tokenPresident, "je prends la main")
                .andExpect(status().isForbidden());
        // Et le CC ne vise pas : son accord n'arrête pas l'avis, c'est une étape, pas la clôture.
        viserAvec(9601, tokenCc, "FAV", "CTRMEM")
                .andExpect(status().isForbidden());
        // Rien n'a bougé.
        Assertions.assertEquals("CC", niveau(9601));
        mvc.perform(get("/api/pv-examens/9601").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
    }

    // ------------------------------------------------------------------
    // 2 — La transmission du CC au Président
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2 — Le CC accepte : navette TRANSMISSION_PRESIDENT, le Président est notifié, niveau = PRESIDENT")
    void ccAccepte_transmetAuPresident() throws Exception {
        projetSoumisDeuxNiveaux(9602);

        accepter(9602, tokenCc, "conforme, je transmets")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveauNavette").value("PRESIDENT"))
                // ⚠️ L'acceptation du CC n'est PAS un visa : le statut ne bouge pas, aucun avis n'est
                // arrêté, aucune part n'est signée. C'est toute la distinction de l'arbitrage 2.
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"))
                .andExpect(jsonPath("$.dateSignatureCc").doesNotExist())
                .andExpect(jsonPath("$.dateAcceptation").doesNotExist());

        Assertions.assertEquals(1, navettes(9602, SensNavette.TRANSMISSION_PRESIDENT).size(),
                "la transmission laisse sa propre trace de navette");
        Assertions.assertEquals(0, navettes(9602, SensNavette.ACCEPTATION).size(),
                "et surtout : ce n'est pas une ACCEPTATION, qui signifierait la navette close");
        Assertions.assertEquals("conforme, je transmets",
                navettes(9602, SensNavette.TRANSMISSION_PRESIDENT).get(0).getCommentaire());

        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')].idObjet", Matchers.hasItem(9602)));
    }

    // ------------------------------------------------------------------
    // 3 — Le retour, étage par étage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3 — Le Président retourne AU CC (RETOUR_CC, niveau CC) ; le CC seul redescend au Membre")
    void retour_descendEtageParEtage() throws Exception {
        projetSoumisDeuxNiveaux(9603);
        accepter(9603, tokenCc, "transmis").andExpect(status().isOk());

        // ① Le Président retourne : le projet redescend d'UN étage. Il reste soumis — il change de
        // main, pas d'état. Le Membre n'est pas encore concerné.
        retourner(9603, tokenPresident, "revoir le montant du lot 2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveauNavette").value("CC"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
        Assertions.assertEquals(1, navettes(9603, SensNavette.RETOUR_CC).size(),
                "le retour au CC a son propre sens : ce n'est pas un retour au Membre");
        Assertions.assertEquals(0, navettes(9603, SensNavette.RETOUR_RECTIF).size());

        // ② Le CC arbitre. S'il redescend, ALORS seulement le projet part en rectification.
        retourner(9603, tokenCc, "corrige le lot 2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));
        Assertions.assertEquals(1, navettes(9603, SensNavette.RETOUR_RECTIF).size());
        Assertions.assertNull(niveau(9603), "hors navette P/CC, le projet n'est à aucun étage");
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_RECTIFIER')].idObjet", Matchers.hasItem(9603)));

        // ③ Le Membre re-soumet : l'étage du bas est reposé, jamais celui du haut. Un projet corrigé
        // repasse par le CC — sans quoi le Président reverrait un texte que son CC n'a pas relu.
        mvc.perform(post("/api/pv-examens/9603/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"corrigé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveauNavette").value("CC"));
    }

    @Test
    @DisplayName("3 bis — Aucun geste hors de son étage : le CC ne retourne pas au niveau Président, "
            + "le Président ne redescend pas au Membre")
    void retour_neSauteJamaisUnEtage() throws Exception {
        projetSoumisDeuxNiveaux(9604);
        // Au niveau CC, le Président ne peut pas retourner au Membre : le CC n'aurait pas su que ce
        // qu'il s'apprêtait à transmettre a été refusé.
        retourner(9604, tokenPresident, "trop tôt")
                .andExpect(status().isForbidden());
        accepter(9604, tokenCc, "transmis").andExpect(status().isOk());
        // Au niveau Président, le CC n'a plus la main : le projet ne lui appartient plus.
        retourner(9604, tokenCc, "je reprends")
                .andExpect(status().isForbidden());
        Assertions.assertEquals("PRESIDENT", niveau(9604));
    }

    @Test
    @DisplayName("3 ter — Le Président ne vise pas un projet resté à l'étage du CC (409)")
    void visa_avantTransmission_409() throws Exception {
        projetSoumisDeuxNiveaux(9605);
        viserAvec(9605, tokenPresident, "FAV", "CTRMEM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", Matchers.containsString("niveau")));
    }

    // ------------------------------------------------------------------
    // 4 — Co-signature à trois
    // ------------------------------------------------------------------

    @Test
    @DisplayName("4 — Visa Président + CC + Membre : trois parts à poser, SIGNE seulement à la troisième")
    void visa_troisSignataires_signeALaDerniere() throws Exception {
        projetSoumisDeuxNiveaux(9606);
        accepter(9606, tokenCc, "transmis").andExpect(status().isOk());

        viserAvec(9606, tokenPresident, "FAV", "CTRCC1", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"))
                .andExpect(jsonPath("$.imCtrlPresident").value("CTRPRE"))
                .andExpect(jsonPath("$.imCcCoSignataire").value("CTRCC1"))
                .andExpect(jsonPath("$.imMembreCoSignataire").value("CTRMEM"))
                // La navette est close : plus aucun étage ne l'attend.
                .andExpect(jsonPath("$.niveauNavette").doesNotExist());

        // Les DEUX désignés sont prévenus — n'en notifier qu'un laisserait le PV attendre une part
        // que son signataire ignore devoir poser.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_COSIGNER')].idObjet", Matchers.hasItem(9606)));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_COSIGNER')].idObjet", Matchers.hasItem(9606)));

        // ⚠️ Le cœur du test : après la part du CC, le PV n'est PAS signé. L'ancienne règle (« le
        // Membre a signé, et l'un des deux P/CC aussi ») l'aurait clos ici, en laissant une part
        // ouverte sur un PV définitif.
        signer(9606, tokenCc, "CC")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"))
                .andExpect(jsonPath("$.dateSignatureCc").isNotEmpty());
        signer(9606, tokenMembre, "MEMBRE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));
    }

    @Test
    @DisplayName("4 bis — La part CC n'appartient qu'au CC désigné : un tiers est refusé, une part déjà posée aussi")
    void partCc_reserveeAuDesigne() throws Exception {
        projetSoumisDeuxNiveaux(9607);
        accepter(9607, tokenCc, "transmis").andExpect(status().isOk());
        viserAvec(9607, tokenPresident, "FAV", "CTRCC1").andExpect(status().isOk());

        // Le Membre examinateur n'a pas été désigné : sa part n'est pas ouverte (ordre B inchangé).
        signer(9607, tokenMembre, "MEMBRE").andExpect(status().isConflict());
        signer(9607, tokenCc, "CC").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));
        // Verrou « une signature par rôle » : la seconde tentative part en 409.
        signer(9607, tokenCc, "CC").andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // 5 — Les gardes de la désignation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5 — Un AUTRE Membre de la centrale est admis ; un Membre d'ailleurs et le Président lui-même, non")
    void coSignataires_gardes() throws Exception {
        projetSoumisDeuxNiveaux(9608);
        accepter(9608, tokenCc, "transmis").andExpect(status().isOk());

        // Un Membre d'une autre localité ne siège pas dans cette commission (§3.3).
        viserAvec(9608, tokenPresident, "FAV", MEMBRE_TMS)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString(MEMBRE_TMS)));
        // Le Président signe déjà en visant : s'ajouter à la liste ne ferait pas deux personnes.
        viserAvec(9608, tokenPresident, "FAV", "CTRPRE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("vous-même")));
        // Aucun désigné du tout : 400 de contrat, comme avant le 2026-09-04.
        viserAvec(9608, tokenPresident, "FAV")
                .andExpect(status().isBadRequest());

        // Un autre Membre de la CENTRALE, en revanche, est une combinaison prévue par l'arbitrage 3.
        viserAvec(9608, tokenPresident, "FAV", AUTRE_MEMBRE_ANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imMembreCoSignataire").value(AUTRE_MEMBRE_ANT));
        String tokenAutreMembre = bearer(AUTRE_MEMBRE_ANT, ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR,
                AUTRE_MEMBRE_ANT, "ANT");
        signer(9608, tokenAutreMembre, "MEMBRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));
    }

    // ------------------------------------------------------------------
    // 6 — Rétro-compatibilité
    // ------------------------------------------------------------------

    @Test
    @DisplayName("6 — Rétro-compat : « imMembreCoSignataire » seul vaut une liste d'un élément (navette simple intacte)")
    void retroCompatibilite_ancienChampSeul() throws Exception {
        // Navette SIMPLE : le dispatch de la fixture est Président → Membre, on n'y touche pas.
        projetSoumis(9609);
        viser(9609, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imMembreCoSignataire").value("CTRMEM"))
                .andExpect(jsonPath("$.imCcCoSignataire").doesNotExist())
                .andExpect(jsonPath("$.niveauNavette").doesNotExist());
        signer(9609, tokenMembre, "MEMBRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));
    }

    // ------------------------------------------------------------------
    // 7 — Le périmètre : le chemin réel, pas la localité
    // ------------------------------------------------------------------

    @Test
    @DisplayName("7 — Dispatch DIRECT du Président au Membre, même en centrale : la navette simple est inchangée")
    void dispatchDirect_navetteSimpleInchangee() throws Exception {
        projetSoumis(9610);   // dispatch 1 : dispatcheur CTRPRE, attributaire CTRMEM

        Assertions.assertNull(niveau(9610), "un dossier à un seul niveau n'a pas d'étage");
        // « accepter » y reste RETIRÉ : sur ce circuit, clore la navette c'est viser.
        accepter(9610, tokenPresident, "j'accepte").andExpect(status().isGone());
        // Et le visa reste celui du dispatcheur, sans passage par un CC.
        viser(9610, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        signer(9610, tokenMembre, "MEMBRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));
    }

    @Test
    @DisplayName("7 bis — Le CC qui examine LUI-MÊME le dossier reçu du Président reste à UN niveau")
    void ccAutoAttributaire_resteAUnNiveau() throws Exception {
        // Le Président a dispatché au CC, qui garde le dossier : il soumettrait « au CC », c'est-à-dire
        // à lui-même. Le troisième terme du discriminant est là pour ça.
        var dispatch = dispatchRepository.findById(1).orElseThrow();
        dispatch.setImCtrlDispatch("CTRCC1");
        dispatch.setImCtrlMembre("CTRCC1");
        dispatch.setImCtrlCc(null);
        dispatchRepository.save(dispatch);
        var examen = examenRepository.findById(1).orElseThrow();
        examen.setImCtrlMembre("CTRCC1");
        examenRepository.save(examen);

        mvc.perform(post("/api/pv-examens").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":9611,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRCC1\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/9611/soumettre").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"go\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveauNavette").doesNotExist());
        accepter(9611, tokenCc, "je transmets").andExpect(status().isGone());
    }

    // ------------------------------------------------------------------
    // 8 — Le document
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8 — PV signé Président + CC SEUL : le bloc de signatures ne porte AUCUNE ligne « Membre »")
    void document_sansMembre_aucuneLigneMembre() throws Exception {
        projetSoumisDeuxNiveaux(9612);
        accepter(9612, tokenCc, "transmis").andExpect(status().isOk());
        viserAvec(9612, tokenPresident, "FAV", "CTRCC1").andExpect(status().isOk());
        signer(9612, tokenCc, "CC").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));

        var ctx = pvDocumentService.contexte(pvExamenRepository.findById(9612).orElseThrow());
        Assertions.assertNotNull(ctx, "le dossier 1 porte un PPM : le contexte doit être constructible");
        // ⚠️ Le nom nul est ce qui FAIT RETIRER le paragraphe à la génération (même mécanique que les
        // lignes Président / CC depuis l'origine). Imprimer un nom sous une signature absente aurait
        // fait porter au document officiel un signataire qui n'a rien signé.
        Assertions.assertNull(ctx.nomMembre(),
                "aucun Membre n'a co-signé : sa ligne ne doit pas être imprimée");
        Assertions.assertNotNull(ctx.nomChefCommission(), "le CC, lui, a signé : sa ligne reste");
        Assertions.assertNotNull(ctx.nomPresident());
    }

    @Test
    @DisplayName("8 bis — Dès qu'un Membre est désigné, sa ligne revient : c'est l'absence de désignation qui la retire")
    void document_avecMembre_ligneConservee() throws Exception {
        projetSoumisDeuxNiveaux(9613);
        accepter(9613, tokenCc, "transmis").andExpect(status().isOk());
        viserAvec(9613, tokenPresident, "FAV", "CTRCC1", "CTRMEM").andExpect(status().isOk());

        var ctx = pvDocumentService.contexte(pvExamenRepository.findById(9613).orElseThrow());
        Assertions.assertNotNull(ctx.nomMembre(), "un Membre est désigné : sa ligne doit être imprimée");
    }
}
