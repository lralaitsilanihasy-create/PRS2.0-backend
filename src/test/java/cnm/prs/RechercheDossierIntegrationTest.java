package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ Audit 2026-08-27 (lot D §6) — {@code GET /api/dossiers/recherche?q=}, résolution d'une référence
 * pour la barre de recherche de la topbar.
 *
 * <p>Le front téléchargeait la liste COMPLÈTE des dossiers <em>et</em> celle des PPM à chaque
 * recherche, puis retrouvait la ligne en JavaScript ({@code main-layout.ts:138}). Cet endpoint fait
 * le travail en base et rend au plus 10 résultats allégés.</p>
 *
 * <p>Un <strong>seul</strong> endpoint, et non un second pour les PPM : la topbar ne cherche pas des
 * PPM, elle cherche le dossier derrière une référence — laquelle est celle du dossier après
 * réception, celle de son PPM avant. La requête couvre les deux, ce que vérifie ce test.</p>
 */
class RechercheDossierIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Lot D §6 — résout la référence du dossier ET celle du PPM, insensible à la casse")
    void recherche_surLesDeuxReferences_etCasseIndifferente() throws Exception {
        // Dossier réceptionné : la topbar affiche sa REFE_DOSSIER.
        Dossier receptionne = dossierLoc(9401, "SOUMIS", "ANT", "PRMP001");
        receptionne.setRefeDossier("00042/DDP/CRM-ANT/2026");
        receptionne.setIdTypeDossier("DDP");
        dossierRepository.save(receptionne);
        ppmRepository.save(ppm(9401, 9401, "PRMP001"));
        // Brouillon jamais réceptionné : pas de REFE_DOSSIER, la topbar affiche la référence du PPM.
        Dossier brouillon = dossierLoc(9402, "BROUILLON", "ANT", "PRMP001");
        brouillon.setRefeDossier(null);
        brouillon.setIdTypeDossier("DDP");
        dossierRepository.save(brouillon);
        Ppm p = ppm(9402, 9402, "PRMP001");
        p.setReference("00077/DGB/PPM/2026");
        ppmRepository.save(p);

        // 1) Référence du dossier, saisie en minuscules — la recherche est insensible à la casse.
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp).param("q", "00042/ddp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9401))
                .andExpect(jsonPath("$[0].reference").value("00042/DDP/CRM-ANT/2026"))
                .andExpect(jsonPath("$[0].refeDossier").value("00042/DDP/CRM-ANT/2026"))
                .andExpect(jsonPath("$[0].idTypeDossier").value("DDP"))
                .andExpect(jsonPath("$[0].statut").value("SOUMIS"));

        // 2) Référence du PPM : le dossier est trouvé, et « reference » rend celle qui est AFFICHÉE.
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp).param("q", "00077/DGB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9402))
                .andExpect(jsonPath("$[0].refeDossier").doesNotExist())
                .andExpect(jsonPath("$[0].reference").value("00077/DGB/PPM/2026"))
                .andExpect(jsonPath("$[0].statut").value("BROUILLON"));

        // 3) Aucune correspondance → liste vide (le front affiche « Aucun dossier pour … »).
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp).param("q", "ZZZZZZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Lot D §6 — périmètre : une PRMP ne résout jamais la référence d'une autre")
    void recherche_perimetreRespecte() throws Exception {
        prmpRepository.save(prmp("PRMP940", "TMS"));
        Dossier mien = dossierLoc(9411, "SOUMIS", "ANT", "PRMP001");
        mien.setRefeDossier("00500/DDP/CRM-ANT/2026");
        dossierRepository.save(mien);
        ppmRepository.save(ppm(9411, 9411, "PRMP001"));
        Dossier autrui = dossierLoc(9412, "SOUMIS", "TMS", "PRMP940");
        autrui.setRefeDossier("00500/DDP/CRM-TMS/2026");
        dossierRepository.save(autrui);
        ppmRepository.save(ppm(9412, 9412, "PRMP940"));

        // La PRMP propriétaire ne voit QUE le sien, alors que les deux références partagent « 00500 ».
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp).param("q", "00500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9411));

        // La PRMP étrangère ne résout que le sien.
        String tokenAutre = bearer("PRMP940", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP940", "TMS");
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenAutre).param("q", "00500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9412));

        // Le Chef de commission d'ANT reste borné à sa localité (le dossier de TMS lui échappe).
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenCc).param("q", "00500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9411));

        // L'Administrateur résout les deux (même périmètre que la liste).
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenAdmin).param("q", "00500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Lot D §6 — q de moins de 2 caractères (ou absent) → 400 ; jamais de recherche « tout »")
    void recherche_borneDeTaille() throws Exception {
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp).param("q", "a"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp).param("q", " "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Lot D §6 — au plus 10 résultats, quel que soit le nombre de correspondances")
    void recherche_plafonneeADixResultats() throws Exception {
        for (int i = 0; i < 15; i++) {
            Dossier d = dossierLoc(9421 + i, "SOUMIS", "ANT", "PRMP001");
            d.setRefeDossier("0090" + i + "/DDP/CRM-ANT/2026");
            dossierRepository.save(d);
            ppmRepository.save(ppm(9421 + i, 9421 + i, "PRMP001"));
        }

        String corps = mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp)
                        .param("q", "/DDP/CRM-ANT/2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andReturn().getResponse().getContentAsString();

        // Les plus récents d'abord : le dernier dossier créé ouvre la liste.
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(corps, "$[*].idDossier");
        org.junit.jupiter.api.Assertions.assertEquals(9435, ids.get(0),
                "résultats servis du plus récent au plus ancien");
    }

    @Test
    @DisplayName("Lot D §6 — les jokers SQL saisis par l'utilisateur ne sont pas interprétés")
    void recherche_jokersEchappes() throws Exception {
        Dossier d = dossierLoc(9441, "SOUMIS", "ANT", "PRMP001");
        d.setRefeDossier("00600/DDP/CRM-ANT/2026");
        dossierRepository.save(d);
        ppmRepository.save(ppm(9441, 9441, "PRMP001"));

        // « %% » chercherait TOUT si les jokers n'étaient pas échappés ; ici, c'est une sous-chaîne
        // littérale, absente de toutes les références.
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp).param("q", "%%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/dossiers/recherche").header("Authorization", tokenPrmp).param("q", "00_00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
