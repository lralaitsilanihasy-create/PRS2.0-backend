package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/**
 * ⚠️ <strong>Le journal du dossier raconte le traitement JUSQU'AU BOUT</strong> (règle du pilote,
 * 2026-09-04 — « le journal s'arrête à la réattribution de 05:21, alors que le chronométrage va
 * jusqu'à la co-signature de 12:13 »).
 *
 * <p>Les événements de traitement sont <strong>dérivés à la lecture</strong> des données qui les
 * portent déjà — navettes, dates du PV, passages de vérification, transmissions SIGMP — et non écrits
 * au fil de l'eau. C'est ce qui rend complets les dossiers <em>déjà</em> traités, et le constat portait
 * justement sur un dossier ancien : une écriture à la source n'aurait raconté que l'avenir.</p>
 */
class JournalTraitementIntegrationTest extends CnmIntegrationTestSupport {

    /** Requalifie le dispatch 1 en deux niveaux : le CC dispatcheur, le Membre attributaire. */
    private void dispatchReattribueParLeCc() {
        var dispatch = dispatchRepository.findById(1).orElseThrow();
        dispatch.setImCtrlDispatch("CTRCC1");
        dispatch.setImCtrlMembre("CTRMEM");
        dispatch.setImCtrlCc(null);
        dispatchRepository.save(dispatch);
    }

    private void creerProjet(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
    }

    private void soumettre(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"prêt\"}"))
                .andExpect(status().isOk());
    }

    /** Types du journal du dossier 1, dans l'ordre où le serveur les sert. */
    private List<String> types() throws Exception {
        String resp = mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$[*].typeAction");
    }

    private String journalBrut() throws Exception {
        return mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** Position du premier événement d'un type, ou -1 — sert à éprouver l'ORDRE, pas le contenu. */
    private int position(List<String> types, String type) {
        return types.indexOf(type);
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("1 — Cycle à deux niveaux complet : le journal raconte de la soumission d'examen au PV signé, "
            + "dans l'ordre")
    void cycleDeuxNiveaux_racontéEnEntier() throws Exception {
        dispatchReattribueParLeCc();
        creerProjet(9950);
        soumettre(9950);
        // Un retour, puis une resoumission : la boucle de navette doit se lire dans le bon sens.
        mvc.perform(post("/api/pv-examens/9950/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"revoir le lot 2\"}"))
                .andExpect(status().isOk());
        soumettre(9950);
        mvc.perform(post("/api/pv-examens/9950/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"transmis\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/9950/viser").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"coSignataires\":[\"CTRCC1\",\"CTRMEM\"]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/9950/signer").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CC\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/9950/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());

        List<String> types = types();
        // Le journal ne s'arrête plus à la réattribution : toute la suite y est.
        for (String attendu : List.of("SOUMISSION_EXAMEN", "RETOUR_RECTIFICATION", "TRANSMISSION_PRESIDENT",
                "VISA", "SIGNATURE", "PV_SIGNE")) {
            Assertions.assertTrue(types.contains(attendu),
                    "le journal doit porter " + attendu + " — types servis : " + types);
        }
        // ⚠️ L'ORDRE est la règle, pas seulement la présence : c'est lui qui raconte le chemin.
        Assertions.assertTrue(position(types, "SOUMISSION_EXAMEN") < position(types, "RETOUR_RECTIFICATION"),
                "on soumet avant de se faire retourner : " + types);
        Assertions.assertTrue(position(types, "RETOUR_RECTIFICATION") < position(types, "TRANSMISSION_PRESIDENT"),
                "le CC transmet APRÈS la boucle de rectification : " + types);
        Assertions.assertTrue(position(types, "TRANSMISSION_PRESIDENT") < position(types, "VISA"),
                "le Président vise ce que le CC lui a transmis : " + types);
        Assertions.assertTrue(position(types, "VISA") < position(types, "SIGNATURE"),
                "aucune part ne se signe avant le visa : " + types);
        Assertions.assertTrue(position(types, "SIGNATURE") < position(types, "PV_SIGNE"),
                "le PV n'est définitif qu'après ses parts : " + types);

        // Deux soumissions et trois parts signées — la boucle et chaque signataire comptent.
        Assertions.assertEquals(2, types.stream().filter("SOUMISSION_EXAMEN"::equals).count());
        Assertions.assertEquals(3, types.stream().filter("SIGNATURE"::equals).count(),
                "Président, CC et Membre : trois parts, trois lignes");

        // Le visa porte ce qu'il a arrêté : l'avis et les désignés, nommés.
        String brut = journalBrut();
        Assertions.assertTrue(brut.contains("avis FAV"), "l'avis arrêté doit figurer au détail du visa");
        Assertions.assertTrue(brut.contains("co-signataire(s)"), "les désignés aussi");
    }

    @Test
    @DisplayName("2 — Boucle FAVR menée jusqu'à la clôture : vérification, transmission SIGMP et archivage "
            + "entrent au journal")
    void boucleAval_verificationSigmpArchivage() throws Exception {
        String tokenVer = bearer("CTRVER", cnm.prs.enums.ProfilUtilisateur.VERIFICATEUR,
                cnm.prs.enums.TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        // Le circuit RÉEL jusqu'à CLOTURE — PV signé, observations maintenues puis levées, transmission
        // SIGMP, archivage. Fabriquer les lignes à la main aurait éprouvé mes fixtures, pas le journal.
        cloturerDossier1(9951, tokenVer);

        List<String> types = types();
        for (String attendu : List.of("PV_SIGNE", "DECISION_VERIFICATION", "TRANSMISSION_SIGMP", "ARCHIVAGE")) {
            Assertions.assertTrue(types.contains(attendu),
                    "le journal doit porter " + attendu + " — types servis : " + types);
        }
        // L'ordre de la fin de parcours tient au RANG du circuit : ces actes ne portent qu'une date,
        // et se retrouvent donc tous au même instant de fin de journée.
        Assertions.assertTrue(position(types, "PV_SIGNE") < position(types, "DECISION_VERIFICATION"),
                "la vérification suit le PV définitif : " + types);
        Assertions.assertTrue(position(types, "DECISION_VERIFICATION") < position(types, "TRANSMISSION_SIGMP"),
                "on transmet à SIGMP ce que la vérification a décidé : " + types);
        Assertions.assertTrue(position(types, "TRANSMISSION_SIGMP") < position(types, "ARCHIVAGE"),
                "l'archivage clôt, il ne précède pas : " + types);

        // Les deux passages de vérification sont là, avec le SENS de chaque décision — c'est ce qui
        // distingue un aller-retour d'un dossier passé du premier coup.
        Assertions.assertEquals(2, types.stream().filter("DECISION_VERIFICATION"::equals).count(),
                "un passage maintenu puis un passage levé : deux décisions");
        String brut = journalBrut();
        Assertions.assertTrue(brut.contains("observations maintenues"), brut);
        Assertions.assertTrue(brut.contains("observations levées"), brut);
    }

    @Test
    @DisplayName("3 — ⚠️ Ordre chronologique STRICT, et aucune régression sur les types déjà consignés")
    void ordreStrict_etTypesExistantsIntacts() throws Exception {
        dispatchReattribueParLeCc();
        creerProjet(9952);
        soumettre(9952);

        String resp = journalBrut();
        List<String> dates = JsonPath.read(resp, "$[*].dateAction");
        // Aucune ligne ne remonte le temps — c'est la seule promesse que le front peut afficher telle
        // quelle, sans retrier.
        for (int i = 1; i < dates.size(); i++) {
            Assertions.assertTrue(dates.get(i - 1).compareTo(dates.get(i)) <= 0,
                    "journal non chronologique en position " + i + " : " + dates);
        }

        // Les gestes de dispatch, eux, n'ont pas bougé : ce sont des lignes STOCKÉES, et les dérivés ne
        // les remplacent pas. Le dossier 1 porte son dispatch de fixture.
        List<String> types = types();
        Assertions.assertTrue(types.contains("SOUMISSION_EXAMEN"), types.toString());
        // Un opérateur nommé partout : une ligne anonyme ne raconterait rien.
        List<String> noms = JsonPath.read(resp, "$[?(@.typeAction=='SOUMISSION_EXAMEN')].nomOperateur");
        Assertions.assertFalse(noms.isEmpty(), "l'événement dérivé doit nommer son opérateur");
        Assertions.assertNotNull(noms.get(0));
    }
}
