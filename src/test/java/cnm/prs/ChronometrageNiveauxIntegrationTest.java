package cnm.prs;

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

import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.repository.TacheDossierRepository;

/**
 * ⚠️ <strong>Prise en charge : garde d'acteur et occurrences par niveau</strong> (constats de la
 * recette réelle du cycle à deux niveaux, 2026-09-04 — dossier 100285, PV 12).
 *
 * <p>Ces trois défauts avaient un point commun : le chronométrage supposait <strong>une étape = une
 * personne</strong>. Cette hypothèse tombe dès que la navette a deux étages (VISA passe du CC au
 * Président) ou que la co-signature compte deux désignés. Là où elle tenait encore — l'examen —, elle
 * n'était pas assez ferme : n'importe quel porteur de profil pouvait ouvrir la tâche d'autrui, et
 * l'assignataire s'en trouvait verrouillé sans recours. Trois réassignations SQL ont été nécessaires
 * pour terminer la recette ; c'est ce que ces tests empêchent de revivre.</p>
 */
class ChronometrageNiveauxIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private TacheDossierRepository tacheRepository;

    @BeforeEach
    void autreMembreDeLaCentrale() {
        controleurRepository.save(controleur("MEMANT9", 5, "ANT"));
    }

    /** Requalifie le dispatch 1 en deux niveaux : le CC dispatcheur, le Membre attributaire. */
    private void dispatchReattribueParLeCc() {
        var dispatch = dispatchRepository.findById(1).orElseThrow();
        dispatch.setImCtrlDispatch("CTRCC1");
        dispatch.setImCtrlMembre("CTRMEM");
        dispatch.setImCtrlCc(null);
        dispatchRepository.save(dispatch);
    }

    private ResultActions pec(int idDossier, String token, int prevision) throws Exception {
        return mvc.perform(post("/api/dossiers/" + idDossier + "/prise-en-charge")
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"previsionHeures\":" + prevision + "}"));
    }

    private List<TacheDossier> taches(int idDossier, EtapeCircuit etape) {
        return tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(idDossier).stream()
                .filter(t -> etape.name().equals(t.getEtape())).toList();
    }

    /** Crée le projet de PV sur l'examen 1 et le soumet — le dossier 1 passe alors à l'étape VISA. */
    private void projetSoumis(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions viserAvec(int idPv, String token, String... coSignataires) throws Exception {
        String liste = String.join("\",\"", coSignataires);
        return mvc.perform(post("/api/pv-examens/" + idPv + "/viser").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"coSignataires\":[\"" + liste + "\"]}"));
    }

    private ResultActions signer(int idPv, String token, String role) throws Exception {
        return mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"));
    }

    // ------------------------------------------------------------------
    // 1 — Le replay n'appartient qu'à son auteur
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1 — Une étape tenue par A : B reçoit 409 nominal ; A la rejoue et corrige SA prévision")
    void pecParUnAutre_409Nominal() throws Exception {
        // Dossier PRET_DISPATCH : l'étape courante est DISPATCH, portée par le CC (le Président
        // l'exerce aussi, par délégation — c'est bien pour cela que deux acteurs peuvent s'y croiser).
        var d = dossierRepository.findById(1).orElseThrow();
        d.setStatut("PRET_DISPATCH");
        dossierRepository.save(d);

        pec(1, tokenCc, 4).andExpect(status().isOk())
                .andExpect(jsonPath("$.imActeur").value("CTRCC1"))
                .andExpect(jsonPath("$.occurrence").value(1));

        // ⚠️ Le cœur du constat : ce POST répondait 200 et écrasait la prévision du CC. Il refuse
        // désormais, EN NOMMANT celui qui tient l'étape — sans le nom, l'appelant n'a personne à qui
        // s'adresser pour se débloquer, et c'est exactement ce qui a mené aux corrections SQL.
        pec(1, tokenPresident, 9).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", Matchers.containsString("déjà prise en charge par")))
                .andExpect(jsonPath("$.message", Matchers.containsString("NomCTRCC1")));

        // La prévision du CC est intacte : le refus n'a rien touché.
        Assertions.assertEquals(1, taches(1, EtapeCircuit.DISPATCH).size(), "aucune occurrence de plus");
        Assertions.assertEquals(4, taches(1, EtapeCircuit.DISPATCH).get(0).getPrevisionHeures());

        // Le titulaire, lui, rejoue et corrige : le replay n'a pas disparu, il s'est restreint.
        pec(1, tokenCc, 6).andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrence").value(1))
                .andExpect(jsonPath("$.previsionHeures").value(6));
        Assertions.assertEquals(1, taches(1, EtapeCircuit.DISPATCH).size());
    }

    // ------------------------------------------------------------------
    // 2 — L'examen se prend par son attributaire
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2 — PEC d'EXAMEN : refusée au dispatcheur et au CC (403), ouverte au seul attributaire")
    void pecExamen_reserveeALAttributaire() throws Exception {
        var d = dossierRepository.findById(1).orElseThrow();
        d.setStatut("DISPATCHE");
        dossierRepository.save(d);

        // Le dispatcheur (Président) et le CC en copie exercent tous deux le profil MEMBRE par
        // délégation : la garde de profil les laissait passer. Ils ouvraient donc une tâche sur le
        // travail de quelqu'un d'autre — et, avec la garde du test 1, l'y verrouillaient.
        pec(1, tokenPresident, 8).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", Matchers.containsString("attribué à")));
        pec(1, tokenCc, 8).andExpect(status().isForbidden());
        Assertions.assertTrue(taches(1, EtapeCircuit.EXAMEN).isEmpty(),
                "aucune tâche ne doit avoir été ouverte par un non-attributaire");

        // L'attributaire, lui, passe.
        pec(1, tokenMembre, 8).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("EXAMEN"))
                .andExpect(jsonPath("$.imActeur").value("CTRMEM"));
    }

    // ------------------------------------------------------------------
    // 3 — Une occurrence de VISA par étage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3 — Deux niveaux : le CC tient VISA#1, « accepter » la clôt, le Président ouvre VISA#2 "
            + "que son visa clôt")
    void visa_uneOccurrenceParNiveau() throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(9701);

        // ① Le CC prend l'étage du bas.
        pec(1, tokenCc, 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("VISA"))
                .andExpect(jsonPath("$.occurrence").value(1))
                .andExpect(jsonPath("$.imActeur").value("CTRCC1"));

        // ② Il transmet. Son occurrence se clôt : le PV reste à l'étape VISA, mais plus chez lui.
        mvc.perform(post("/api/pv-examens/9701/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"transmis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveauNavette").value("PRESIDENT"));
        List<TacheDossier> visas = taches(1, EtapeCircuit.VISA);
        Assertions.assertEquals(1, visas.size());
        Assertions.assertNotNull(visas.get(0).getDateFin(), "l'occurrence du CC doit être close");
        Assertions.assertEquals("CTRCC1", visas.get(0).getImActeur());

        // ③ Le Président ouvre la SIENNE — et n'est plus verrouillé par celle du CC, puisqu'elle est
        // close. C'est le déblocage que la recette avait dû faire en SQL.
        pec(1, tokenPresident, 5).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("VISA"))
                .andExpect(jsonPath("$.occurrence").value(2))
                .andExpect(jsonPath("$.imActeur").value("CTRPRE"));

        // ④ Le visa clôt VISA#2. Chaque étage a sa tâche, sa prévision et sa durée.
        viserAvec(9701, tokenPresident, "CTRMEM").andExpect(status().isOk());
        visas = taches(1, EtapeCircuit.VISA);
        Assertions.assertEquals(2, visas.size(), "une occurrence par étage, pas une pour deux");
        Assertions.assertTrue(visas.stream().allMatch(t -> t.getDateFin() != null),
                "les deux occurrences sont closes");
        Assertions.assertEquals(3, visas.get(0).getPrevisionHeures(), "la prévision du CC lui reste");
        Assertions.assertEquals(5, visas.get(1).getPrevisionHeures(), "celle du Président aussi");
    }

    // ------------------------------------------------------------------
    // 4 — Une tâche de COSIGNATURE par désigné
    // ------------------------------------------------------------------

    @Test
    @DisplayName("4 — Visa P + CC + Membre : deux tâches COSIGNATURE, chacune close par SA signature")
    void cosignature_uneTacheParDesigne() throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(9702);
        mvc.perform(post("/api/pv-examens/9702/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"transmis\"}"))
                .andExpect(status().isOk());
        viserAvec(9702, tokenPresident, "CTRCC1", "CTRMEM").andExpect(status().isOk());

        // ⚠️ La co-signature est la SEULE étape à plusieurs porteurs : le 409 du test 1 n'y joue pas,
        // sans quoi le second désigné serait verrouillé par le premier — l'autre moitié du constat.
        pec(1, tokenCc, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("COSIGNATURE"))
                .andExpect(jsonPath("$.imActeur").value("CTRCC1"));
        pec(1, tokenMembre, 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("COSIGNATURE"))
                .andExpect(jsonPath("$.imActeur").value("CTRMEM"));
        Assertions.assertEquals(2, taches(1, EtapeCircuit.COSIGNATURE).size(),
                "un désigné, une tâche");

        // Chaque signature ne clôt que la sienne. Fermer « la » tâche ouverte aurait clos celle de
        // l'autre, et le PV se serait terminé avec une tâche ouverte au nom d'un signataire.
        signer(9702, tokenCc, "CC").andExpect(status().isOk());
        var parActeur = taches(1, EtapeCircuit.COSIGNATURE).stream()
                .collect(java.util.stream.Collectors.toMap(TacheDossier::getImActeur, t -> t));
        Assertions.assertNotNull(parActeur.get("CTRCC1").getDateFin(), "le CC a signé : sa tâche est close");
        Assertions.assertNull(parActeur.get("CTRMEM").getDateFin(), "celle du Membre reste ouverte");

        signer(9702, tokenMembre, "MEMBRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));
        Assertions.assertTrue(taches(1, EtapeCircuit.COSIGNATURE).stream()
                .allMatch(t -> t.getDateFin() != null), "aucune tâche ne survit au PV signé");
    }

    // ------------------------------------------------------------------
    // 5 — Le nom du CC désigné
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5 — « nomCcCoSignataire » est peuplé après un visa désignant le CC (le front ne replie plus "
            + "sur le matricule)")
    void nomCcCoSignataire_peuple() throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(9703);
        mvc.perform(post("/api/pv-examens/9703/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"transmis\"}"))
                .andExpect(status().isOk());

        viserAvec(9703, tokenPresident, "CTRCC1", "CTRMEM").andExpect(status().isOk())
                .andExpect(jsonPath("$.imCcCoSignataire").value("CTRCC1"))
                .andExpect(jsonPath("$.nomCcCoSignataire").value("Prenoms NomCTRCC1"))
                .andExpect(jsonPath("$.nomMembreCoSignataire").value("Prenoms NomCTRMEM"));

        // Et en lecture, pas seulement dans la réponse du visa : c'est là que le front le lit.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/pv-examens/9703").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomCcCoSignataire").value("Prenoms NomCTRCC1"));
    }
}
