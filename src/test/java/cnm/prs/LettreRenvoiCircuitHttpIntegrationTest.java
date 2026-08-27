package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ Audit 2026-08-27 (lot C, volet 5) — {@code LettreRenvoiIntegrationTest} couvre déjà la création,
 * la signature et la lecture, mais {@code soumettre} et {@code archiver} n'étaient <strong>jamais
 * appelés par HTTP</strong> : les tests existants passent directement au statut {@code SOUMIS} par
 * seed repository. Scénario complet ici, bout en bout, par les quatre endpoints réels : création →
 * soumission → signature → archivage, profils corrects post-lot B (CC/Président de la clôture de
 * navette pour la création/soumission/signature ; Assistant contrôleur de la localité du dossier pour
 * l'archivage).
 */
class LettreRenvoiCircuitHttpIntegrationTest extends CnmIntegrationTestSupport {

    private String tokenAss() {
        return bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
    }

    @Test
    @DisplayName("Circuit complet par HTTP : création -> soumission -> signature -> archivage, statuts et effets vérifiés à chaque étape")
    void circuitComplet_creationSoumissionSignatureArchivage() throws Exception {
        // 1) Création (CC, clôture de navette de l'examen 1 — localité ANT).
        String rep = mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(rep, "$.idLettre");

        // 2) Soumission par HTTP (jamais appelée jusqu'ici) : BROUILLON -> SOUMIS.
        mvc.perform(post("/api/lettre-renvois/" + id + "/soumettre").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
        // Effet vérifié en base ET par relecture.
        org.junit.jupiter.api.Assertions.assertEquals("SOUMIS",
                lettreRenvoiRepository.findById(id).orElseThrow().getStatut());
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("SOUMIS"));

        // 3) Signature (patron existant, ré-exercé ici comme étape du même circuit) : SOUMIS -> SIGNE.
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNE"))
                .andExpect(jsonPath("$.imSignataire").value("CTRCC1"));

        // 4) Archivage par HTTP (jamais appelé jusqu'ici), par l'Assistant contrôleur de la localité
        // du dossier (ANT) : le statut métier reste SIGNE (pas d'état ARCHIVE dans le cycle), mais
        // dateArchivage/imArchiveur sont désormais renseignés.
        mvc.perform(post("/api/lettre-renvois/" + id + "/archiver").header("Authorization", tokenAss()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNE"))
                .andExpect(jsonPath("$.dateArchivage").isNotEmpty())
                .andExpect(jsonPath("$.imArchiveur").value("CTRASS"));
        cnm.prs.entity.LettreRenvoi archivee = lettreRenvoiRepository.findById(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(archivee.getDateArchivage(), "date d'archivage posée en base");
        org.junit.jupiter.api.Assertions.assertEquals("CTRASS", archivee.getImArchiveur());
    }

    @Test
    @DisplayName("Soumettre : réservé Président/CC (403 au Membre) ; refusé (409) hors BROUILLON")
    void soumettre_casDErreur() throws Exception {
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated());

        // Profil non autorisé (garde de contrôleur, avant même la lettre visée).
        int idSoumise = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + idSoumise + "/soumettre").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Déjà SOUMIS : re-soumission refusée (409).
        mvc.perform(post("/api/lettre-renvois/" + idSoumise + "/soumettre").header("Authorization", tokenCc))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Archiver : réservé à l'Assistant contrôleur (403 au Membre) ; localité du dossier requise (403 hors localité) ; refusé (409) hors SIGNE")
    void archiver_casDErreur() throws Exception {
        int idSoumise = seedLettreSoumise();

        // Profil non autorisé.
        mvc.perform(post("/api/lettre-renvois/" + idSoumise + "/archiver").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Pas encore SIGNE : archivage refusé (409), même pour l'Assistant habilité.
        mvc.perform(post("/api/lettre-renvois/" + idSoumise + "/archiver").header("Authorization", tokenAss()))
                .andExpect(status().isConflict());

        // Signée, puis archivage par un Assistant d'une AUTRE localité (TMS) que celle du dossier (ANT) -> 403.
        mvc.perform(post("/api/lettre-renvois/" + idSoumise + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        String tokenAssTms = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR,
                "CTRASS", "TMS");
        mvc.perform(post("/api/lettre-renvois/" + idSoumise + "/archiver").header("Authorization", tokenAssTms))
                .andExpect(status().isForbidden());

        // L'Assistant de la BONNE localité (ANT) archive avec succès.
        mvc.perform(post("/api/lettre-renvois/" + idSoumise + "/archiver").header("Authorization", tokenAss()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateArchivage").isNotEmpty());

        // Déjà archivée : un second archivage est refusé (409).
        mvc.perform(post("/api/lettre-renvois/" + idSoumise + "/archiver").header("Authorization", tokenAss()))
                .andExpect(status().isConflict());
    }
}
