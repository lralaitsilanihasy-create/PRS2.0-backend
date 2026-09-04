package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import cnm.prs.enums.EtapeCircuit;
import cnm.prs.repository.TacheDossierRepository;

/**
 * ⚠️ <strong>Prise en charge : garde d'acteur sur VISA et CO-SIGNATURE</strong> (constat pilote du
 * 2026-09-04, dossier 100286) — suite directe de la garde d'EXAMEN livrée le matin même.
 *
 * <p><strong>Ce qui s'est passé en recette.</strong> Sur un dossier à deux niveaux, le CC a accepté le
 * PV — l'occurrence VISA#1 s'est bien close, la mécanique par niveaux a joué — <em>puis a recliqué
 * « Prendre en charge »</em>. Le serveur a répondu 200 et ouvert <strong>VISA#2 au nom du CC</strong>,
 * alors que cette occurrence revient au Président. Le Président s'est alors retrouvé verrouillé : sa
 * propre prise en charge tombait sur la tâche d'autrui, donc 409, et rien dans l'interface ne pouvait
 * réparer. Déblocage fait en base.</p>
 *
 * <p>La livraison du matin gardait les tâches <em>déjà ouvertes</em> (409 nominal) et l'attribution de
 * l'EXAMEN. Ce qui manquait, c'est la garde sur la <strong>création</strong> d'une occurrence de VISA
 * ou de CO-SIGNATURE : c'est elle qu'on éprouve ici.</p>
 */
class PriseEnChargeVisaCosignatureIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private TacheDossierRepository tacheDossierRepository;

    /** Requalifie le dispatch 1 en deux niveaux : le CC dispatcheur, le Membre attributaire. */
    private void dispatchReattribueParLeCc() {
        var dispatch = dispatchRepository.findById(1).orElseThrow();
        dispatch.setImCtrlDispatch("CTRCC1");
        dispatch.setImCtrlMembre("CTRMEM");
        dispatch.setImCtrlCc(null);
        dispatchRepository.save(dispatch);
    }

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

    private ResultActions pec(String token, int prevision) throws Exception {
        return mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"previsionHeures\":" + prevision + "}"));
    }

    private void accepter(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"transmis\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions chronometrage(String token) throws Exception {
        return mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", token));
    }

    private long taches(EtapeCircuit etape) {
        return tacheDossierRepository.findByIdDossierOrderByDatePriseEnChargeAsc(1).stream()
                .filter(t -> etape.name().equals(t.getEtape())).count();
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("1 — Niveau PRÉSIDENT : le CC (pourtant dispatcheur) est refusé en 403 ; le Président ouvre "
            + "VISA n+1")
    void niveauPresident_leCcNeReprendPasLaMain() throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(9801);
        pec(tokenCc, 3).andExpect(status().isOk());   // VISA#1, à l'étage du CC
        accepter(9801);                                // VISA#1 close, le PV monte

        // ⚠️ LE BUG DE RECETTE, à la lettre : le CC reclique « Prendre en charge ». Il répondait 200 et
        // ouvrait au nom du CC l'occurrence du Président, qui n'avait alors plus aucun recours.
        pec(tokenCc, 4).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", Matchers.containsString("Le visa de ce dossier revient à")));
        Assertions.assertEquals(1, taches(EtapeCircuit.VISA),
                "aucune occurrence ne doit avoir été ouverte par le CC");

        // Le Président, lui, ouvre la sienne — et elle porte bien le rang suivant.
        pec(tokenPresident, 5).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("VISA"))
                .andExpect(jsonPath("$.occurrence").value(2))
                .andExpect(jsonPath("$.imActeur").value("CTRPRE"));
    }

    @Test
    @DisplayName("2 — Niveau CC : le Président est refusé en 403 ; le CC dispatcheur ouvre sa tâche")
    void niveauCc_lePresidentNeDoublePasLeCc() throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(9802);

        // Symétrique du test 1 : à l'étage du bas, c'est le CC qu'on attend. Le Président qui prendrait
        // la tâche ici la retirerait à celui qui doit accepter — le même verrouillage, en miroir.
        pec(tokenPresident, 3).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", Matchers.containsString("Le visa de ce dossier revient à")));
        Assertions.assertEquals(0, taches(EtapeCircuit.VISA));

        pec(tokenCc, 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("VISA"))
                .andExpect(jsonPath("$.imActeur").value("CTRCC1"));
    }

    @Test
    @DisplayName("3 — CO-SIGNATURE : un non-désigné est refusé ; chaque désigné ouvre SA tâche")
    void cosignature_reserveeAuxDesignes() throws Exception {
        controleurRepository.save(controleur("MEMANT6", 5, "ANT"));
        dispatchReattribueParLeCc();
        projetSoumis(9803);
        accepter(9803);
        mvc.perform(post("/api/pv-examens/9803/viser").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"coSignataires\":[\"CTRCC1\",\"CTRMEM\"]}"))
                .andExpect(status().isOk());

        // Un Membre de la localité, éligible au profil porteur, mais NON désigné : le visa ne l'a pas
        // appelé à signer, il n'a donc pas de part à chronométrer.
        String tokenAutreMembre = bearer("MEMANT6", cnm.prs.enums.ProfilUtilisateur.MEMBRE,
                cnm.prs.enums.TypeActeur.CONTROLEUR, "MEMANT6", "ANT");
        pec(tokenAutreMembre, 2).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", Matchers.containsString("désignés du visa")));
        Assertions.assertEquals(0, taches(EtapeCircuit.COSIGNATURE));

        // Les deux désignés ouvrent chacun la leur — la co-signature est la seule étape à plusieurs
        // porteurs, le refus nominal ne doit pas y verrouiller le second par le premier.
        pec(tokenCc, 2).andExpect(status().isOk()).andExpect(jsonPath("$.imActeur").value("CTRCC1"));
        pec(tokenMembre, 2).andExpect(status().isOk()).andExpect(jsonPath("$.imActeur").value("CTRMEM"));
        Assertions.assertEquals(2, taches(EtapeCircuit.COSIGNATURE), "une tâche par désigné");
    }

    @Test
    @DisplayName("4 — « acteursAttendus » reflète chaque cas, et vaut null quand la liste ne peut pas être close")
    void acteursAttendus_refleteLaGarde() throws Exception {
        dispatchReattribueParLeCc();
        projetSoumis(9804);

        // Étage CC : le seul CC dispatcheur.
        chronometrage(tokenAdmin).andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("VISA"))
                .andExpect(jsonPath("$.acteursAttendus", Matchers.contains("CTRCC1")));

        accepter(9804);
        // Étage Président : les Présidents.
        chronometrage(tokenAdmin).andExpect(status().isOk())
                .andExpect(jsonPath("$.acteursAttendus", Matchers.hasItem("CTRPRE")))
                .andExpect(jsonPath("$.acteursAttendus", Matchers.not(Matchers.hasItem("CTRCC1"))));

        mvc.perform(post("/api/pv-examens/9804/viser").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"coSignataires\":[\"CTRCC1\",\"CTRMEM\"]}"))
                .andExpect(status().isOk());
        // Co-signature : les désignés, et eux seuls.
        chronometrage(tokenAdmin).andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("COSIGNATURE"))
                .andExpect(jsonPath("$.acteursAttendus", Matchers.containsInAnyOrder("CTRCC1", "CTRMEM")));
    }

    @Test
    @DisplayName("4 bis — Navette SIMPLE : « acteursAttendus » est null (l'intérim ouvre le visa à tout P/CC "
            + "du périmètre) et la prise en charge reste ouverte")
    void navetteSimple_listeNonClose() throws Exception {
        // Le dispatch de la fixture est direct Président → Membre : navette simple.
        projetSoumis(9805);

        // ⚠️ null n'est PAS « personne ». Une liste vide aurait bloqué tout le monde ; ici l'ensemble
        // n'est simplement pas énumérable, l'intérim admettant tout P/CC du périmètre — le front replie
        // sur le porteur nominal, et c'est le serveur qui tranche au moment du visa.
        chronometrage(tokenAdmin).andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("VISA"))
                .andExpect(jsonPath("$.acteursAttendus").doesNotExist());

        // Et le CC, non dispatcheur, peut toujours prendre l'étape en charge : la garde nominative ne
        // s'applique pas ici. C'est l'anti-régression du contrat d'avant.
        pec(tokenCc, 3).andExpect(status().isOk());
    }
}
