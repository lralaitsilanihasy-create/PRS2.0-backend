package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Lettre de renvoi « lue » : suivi <strong>par agent</strong> (login), plus par tutelle (⚠️ décision
 * métier 2026-08-27, cf. {@code docs/plan-lettre-lue-par-agent.md} §T2). Preuve que la consultation
 * d'une UGPM n'éteint plus le badge de sa PRMP de tutelle, et réciproquement ; idempotence par agent ;
 * absence de trace pour les contrôleurs (conformité LOT 3a).
 *
 * <p>⚠️ Piège de fixture (signalé au commit) — {@code tokenPrmp} du socle a {@code login == ref ==
 * "PRMP001"} : un jeton UGPM qui répéterait ce schéma ne prouverait rien, le bug historique ne se
 * manifestant QUE lorsque {@code login} et {@code ref} diffèrent. Le jeton UGPM ci-dessous suit le
 * précédent de {@code UgpmIntegrationTest} : {@code login="UGPM1"} (identifie l'agent),
 * {@code ref="PRMP001"} (ID_PRMP de la tutelle, périmètre partagé avec la PRMP).</p>
 */
class LettreRenvoiLueParAgentIntegrationTest extends CnmIntegrationTestSupport {

    private static final String LOGIN_UGPM = "UGPM1";

    private String tokenUgpm() {
        return bearer(LOGIN_UGPM, ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
    }

    @Test
    @DisplayName("Suivi par agent : la lecture d'une UGPM n'éteint pas le badge de sa PRMP de tutelle (et réciproquement)")
    void lecture_ugpm_ninfluence_pas_prmp_et_reciproquement() throws Exception {
        int lettreA = seedLettreSignee();
        int lettreB = seedLettreSignee();

        // Avant toute lecture : 2 lettres SIGNE du dossier 1 (PPM de PRMP001), aucune lue par PRMP001.
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(2));

        // 1) L'UGPM (login UGPM1, ref/tutelle PRMP001) consulte lettreA → marquée lue POUR ELLE.
        mvc.perform(get("/api/lettre-renvois/" + lettreA).header("Authorization", tokenUgpm()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lue").value(true));

        // 2) La PRMP de la même tutelle : lettreA reste "non lue" pour ELLE, et son compteur ne bouge pas.
        String mesLettres = mvc.perform(get("/api/lettre-renvois/mes-lettres").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<?> lueA = com.jayway.jsonpath.JsonPath.read(mesLettres, "$[?(@.idLettre==" + lettreA + ")].lue");
        assertEquals(List.of(false), lueA, "la lecture de l'UGPM ne doit pas marquer la lettre lue pour la PRMP");
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(2)); // inchangé : toujours 2 non lues PAR LA PRMP

        // 3) La PRMP consulte à son tour lettreA → son flag passe à true et son compteur décrémente.
        mvc.perform(get("/api/lettre-renvois/" + lettreA).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lue").value(true));
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(1)); // reste lettreB, non lue

        // 4) Symétrie : la PRMP consulte lettreB (jamais vue par l'UGPM) — son compteur tombe à 0...
        mvc.perform(get("/api/lettre-renvois/" + lettreB).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lue").value(true));
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(0));
        // ... et ne pose AUCUNE trace pour l'agent UGPM (contrôle direct au dépôt, sans nouvel appel
        // qui la poserait lui-même) : la lecture de la PRMP ne marque pas pour l'UGPM.
        assertFalse(lueRepository.existsByIdLettreAndLoginAgent(lettreB, LOGIN_UGPM),
                "la lecture de la PRMP ne doit pas marquer la lettre lue pour l'UGPM");
    }

    @Test
    @DisplayName("Idempotence par agent : deux consultations du même agent ne posent qu'une seule trace")
    void idempotence_par_agent() throws Exception {
        int lettre = seedLettreSignee();

        // Deux GET successifs du même agent (UGPM), puis deux du même agent (PRMP) : 1 trace chacun.
        mvc.perform(get("/api/lettre-renvois/" + lettre).header("Authorization", tokenUgpm()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + lettre).header("Authorization", tokenUgpm()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + lettre).header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + lettre).header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        long total = lueRepository.findAll().stream().filter(l -> l.getIdLettre() == lettre).count();
        assertEquals(2, total, "une trace par AGENT distinct (UGPM1, PRMP001), pas par consultation");
    }

    @Test
    @DisplayName("Conformité LOT 3a : un contrôleur du périmètre consulte sans poser de trace ; une PRMP non propriétaire reste 403")
    void controleur_ne_pose_pas_de_trace_prmp_non_proprietaire_403() throws Exception {
        int lettre = seedLettreSignee();

        // Un Membre du périmètre (localité ANT) consulte : accès autorisé, aucune trace posée.
        mvc.perform(get("/api/lettre-renvois/" + lettre).header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        assertTrue(lueRepository.findAll().stream().noneMatch(l -> l.getIdLettre() == lettre),
                "un contrôleur qui consulte ne doit poser aucune trace (LOT 3a)");

        // Une PRMP étrangère au dossier reste hors périmètre → 403 (aucune trace non plus).
        String tokenAutrePrmp = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", null);
        mvc.perform(get("/api/lettre-renvois/" + lettre).header("Authorization", tokenAutrePrmp))
                .andExpect(status().isForbidden());
        assertTrue(lueRepository.findAll().stream().noneMatch(l -> l.getIdLettre() == lettre),
                "une PRMP non propriétaire n'obtient pas d'accès, donc aucune trace");
    }
}
