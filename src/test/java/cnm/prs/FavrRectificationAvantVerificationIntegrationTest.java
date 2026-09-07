package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ <strong>FAVR : la rectification de la PRMP précède la vérification</strong> (règle pilote du
 * 2026-09-07, {@code docs/demande-backend-2026-09-07-favr-rectification-avant-verification.md}).
 *
 * <p>Le premier passage du vérificateur ne servait qu'à relayer les observations vers la PRMP : il
 * forçait un « tout MAINTENUE » et interdisait la levée. Ce relais se fait désormais <strong>à la
 * co-signature</strong>, sans mobiliser personne — et le vérificateur, qui ne voit plus que des dossiers
 * <em>déjà rectifiés</em>, dispose des deux décisions dès son premier passage.</p>
 *
 * <p>Ce que ces tests protègent, dans l'ordre de la recette de contre-vérification : le
 * <strong>silence total</strong> du côté vérificateur avant la rectification (ni notification, ni
 * liste), la notification de la PRMP <strong>avec les réserves</strong>, l'ouverture de la vérification
 * par la resoumission, le <strong>choix réel</strong> au premier passage, et la <strong>boucle</strong>
 * conservée. Le chemin FAV, lui, ne change pas.</p>
 */
class FavrRectificationAvantVerificationIntegrationTest extends CnmIntegrationTestSupport {

    private String tokenVer() {
        return bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
    }

    // ------------------------------------------------------------------ 1 & 2 — le PV part à la PRMP

    @Test
    @DisplayName("Recette 1 & 2 — à la co-signature d'un FAVR : le dossier attend la rectification de la PRMP, "
            + "qui est notifiée du PV ET de ses réserves ; le vérificateur n'a AUCUNE notification")
    void coSignature_envoieLeDossierALaPrmp_verificateurSilencieux() throws Exception {
        signerPvAvecAvis(940, "FAVR");

        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));

        String notifs = mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                // La PRMP reçoit le PV définitif ET les réserves à rectifier.
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')].destinataireRef", hasItem("PRMP001")))
                .andExpect(jsonPath("$[?(@.typeNotif=='OBSERVATION_VERIFICATION')].destinataireRef", hasItem("PRMP001")))
                // ⚠️ Silence total du côté vérificateur : rien ne lui est adressé avant la rectification.
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VERIFIER')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.destinataireIm=='CTRVER')]", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        // Le corps de la notification PORTE les réserves : c'est ce qui remplace le rappel du vérificateur.
        java.util.List<String> corps = JsonPath.read(notifs, "$[?(@.typeNotif=='OBSERVATION_VERIFICATION')].corps");
        assertThat(corps).hasSize(1);
        assertThat(corps.get(0)).contains("réserve").contains("resoumettez");
    }

    @Test
    @DisplayName("Recette 2 — avant rectification, le dossier n'est NI dans « à vérifier » NI dans « en attente "
            + "PRMP » du vérificateur : il ne le concerne pas encore")
    void avantRectification_absentDesFilesDuVerificateur() throws Exception {
        signerPvAvecAvis(941, "FAVR");
        String tokenVer = tokenVer();

        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(0)));
        mvc.perform(get("/api/dossiers/en-attente-prmp").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(0)));
        mvc.perform(get("/api/kpis/mes-compteurs-verificateur").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.aVerifier").value(0))
                .andExpect(jsonPath("$.enAttentePrmp").value(0));
    }

    // ------------------------------------------------------------------ 3 & 4 — la rectification ouvre tout

    @Test
    @DisplayName("Recette 3 & 4 — la resoumission de la PRMP ouvre la vérification : le vérificateur est notifié "
            + "MAINTENANT, le dossier entre dans sa file, et « Levée » est possible dès ce premier passage")
    void rectification_ouvreLaVerification_etLaLeveeEstPossible() throws Exception {
        signerPvAvecAvis(942, "FAVR");
        String tokenVer = tokenVer();

        prendreEnChargeRectification(1);
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));

        // Notifié maintenant, pas avant.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VERIFIER')].destinataireIm", hasItem("CTRVER")));
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));

        // ⚠️ Le choix est RÉEL dès le premier passage : le dossier est déjà rectifié.
        mvc.perform(get("/api/observations-pv").header("Authorization", tokenVer).param("dossier", "1"))
                .andExpect(jsonPath("$[0].leveePossible").value(true));
    }

    @Test
    @DisplayName("Recette 5 — LEVÉE au premier passage → OBSERVATIONS_LEVEES puis SIGMP : le circuit nominal ne "
            + "passe plus par un « rappel »")
    void premierPassage_leveeDirecte_versSigmp() throws Exception {
        signerPvAvecAvis(943, "FAVR");
        String tokenVer = tokenVer();
        passageObservationDossier1(tokenVer, "LEVEE", null);   // resoumet puis lève, en un geste

        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("OBSERVATIONS_LEVEES"));
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("DECISION_TRANSMISE_SIGMP"));
    }

    @Test
    @DisplayName("La BOUCLE est conservée — MAINTENUE au premier passage renvoie le dossier à la PRMP, qui "
            + "rectifie de nouveau ; il réapparaît alors dans les deux files du vérificateur")
    void maintenue_renvoieALaPrmp_boucleConservee() throws Exception {
        signerPvAvecAvis(944, "FAVR");
        String tokenVer = tokenVer();
        passageObservationDossier1(tokenVer, "MAINTENUE", "insuffisant");

        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
        // Cette fois le vérificateur a déjà statué : le dossier reste sous ses yeux, en lecture seule.
        mvc.perform(get("/api/dossiers/en-attente-prmp").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));

        // Second tour : la PRMP rectifie, le vérificateur lève.
        passageObservationDossier1(tokenVer, "LEVEE", null);
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("OBSERVATIONS_LEVEES"));
    }

    // ------------------------------------------------------------------ le chemin FAV ne change pas

    @Test
    @DisplayName("Cas FAV sans réserves — INCHANGÉ (confirmation demandée) : le dossier va directement en "
            + "vérification, le vérificateur est notifié « décision à transmettre », la PRMP reçoit le PV")
    void avisFavorableSansReserves_inchange() throws Exception {
        signerPvAvecAvis(945, "FAV");

        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='DECISION_A_TRANSMETTRE')].destinataireIm", hasItem("CTRVER")))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')].destinataireRef", hasItem("PRMP001")))
                // Aucune réserve : rien n'est envoyé à rectifier.
                .andExpect(jsonPath("$[?(@.typeNotif=='OBSERVATION_VERIFICATION')]", hasSize(0)));
        // Et la transmission SIGMP directe reste ouverte, sans passage de vérification.
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
    }
}
