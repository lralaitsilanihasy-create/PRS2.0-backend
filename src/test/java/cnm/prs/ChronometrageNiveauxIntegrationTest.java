package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
 * ⚠️ <strong>Un passage par ÉTAGE, un passage par DÉSIGNÉ</strong> (constats de la recette réelle du
 * cycle à deux niveaux, 2026-09-04 — dossier 100285, PV 12 ; revus le 2026-09-12).
 *
 * <p>Le défaut d'origine : le chronométrage supposait <strong>une étape = une personne</strong>. Cette
 * hypothèse tombe dès que la navette a deux étages (VISA passe du CC au Président) ou que la
 * co-signature compte deux désignés — le temps de l'un se mêlait à celui de l'autre.</p>
 *
 * <p>⚠️ <strong>2026-09-12</strong> — la moitié « garde » de ces constats (409 nominal, 403 du
 * non-attributaire) a disparu avec la prise en charge elle-même : il n'y a plus de tâche à ouvrir, donc
 * plus personne à verrouiller. Ce qui reste, et qui compte toujours, c'est la <strong>découpe</strong> :
 * chaque étage et chaque désigné laisse SON passage, avec son acteur et sa durée propre — et l'entrée de
 * l'un est la fin de l'autre.</p>
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

    private List<TacheDossier> taches(int idDossier, EtapeCircuit etape) {
        return tacheRepository.findParDossier(idDossier).stream()
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
    // 1 — Un passage de VISA par étage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1 — Deux niveaux : « accepter » clôt le VISA du CC, le visa du Président clôt le sien — "
            + "deux passages, deux acteurs, et l'entrée du second est la fin du premier")
    void visa_unPassageParNiveau() throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(9701);

        // ① Le CC transmet au Président : son passage se clôt, le PV reste à l'étape VISA mais change
        // d'étage. Sans cette fin, un seul passage aurait porté les deux acteurs et mêlé leurs durées.
        mvc.perform(post("/api/pv-examens/9701/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"transmis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveauNavette").value("PRESIDENT"));
        List<TacheDossier> visas = taches(1, EtapeCircuit.VISA);
        Assertions.assertEquals(1, visas.size());
        Assertions.assertEquals("CTRCC1", visas.get(0).getImActeur());

        // ② Le visa du Président clôt le SIEN.
        viserAvec(9701, tokenPresident, "CTRMEM").andExpect(status().isOk());
        visas = taches(1, EtapeCircuit.VISA);
        Assertions.assertEquals(2, visas.size(), "un passage par étage, pas un pour deux");
        Assertions.assertEquals("CTRCC1", visas.get(0).getImActeur());
        Assertions.assertEquals("CTRPRE", visas.get(1).getImActeur());
        Assertions.assertEquals(1, visas.get(0).getOccurrence());
        Assertions.assertEquals(2, visas.get(1).getOccurrence());
        Assertions.assertTrue(visas.get(1).getDateFin().isAfter(visas.get(0).getDateFin())
                || visas.get(1).getDateFin().isEqual(visas.get(0).getDateFin()),
                "l'étage du Président commence là où celui du CC s'arrête");
    }

    // ------------------------------------------------------------------
    // 2 — Un passage de COSIGNATURE par désigné
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2 — Visa P + CC + Membre : chaque signature laisse SON passage de co-signature, nominatif")
    void cosignature_unPassageParDesigne() throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(9702);
        mvc.perform(post("/api/pv-examens/9702/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"transmis\"}"))
                .andExpect(status().isOk());
        viserAvec(9702, tokenPresident, "CTRCC1", "CTRMEM").andExpect(status().isOk());

        // La co-signature est la seule étape où plusieurs personnes travaillent de front : chacune y
        // laisse sa part. Attribuer les deux à un seul acteur aurait effacé ce que l'étape a d'unique.
        signer(9702, tokenCc, "CC").andExpect(status().isOk());
        Assertions.assertEquals(List.of("CTRCC1"), taches(1, EtapeCircuit.COSIGNATURE).stream()
                .map(TacheDossier::getImActeur).toList(), "seule la part du CC est posée");

        signer(9702, tokenMembre, "MEMBRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));
        Assertions.assertEquals(List.of("CTRCC1", "CTRMEM"), taches(1, EtapeCircuit.COSIGNATURE).stream()
                .map(TacheDossier::getImActeur).toList(), "un désigné, un passage");
        Assertions.assertTrue(taches(1, EtapeCircuit.COSIGNATURE).stream()
                .allMatch(t -> t.getDateFin() != null), "un passage n'existe que clos");
    }

    // ------------------------------------------------------------------
    // 3 — Le nom du CC désigné
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3 — « nomCcCoSignataire » est peuplé après un visa désignant le CC (le front ne replie plus "
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
