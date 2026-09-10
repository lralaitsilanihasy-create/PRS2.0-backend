package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.PrmpEntite;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ <strong>Décision pilote 2026-09-10</strong> — la création d'un <strong>ministère</strong>, d'un
 * <strong>organigramme</strong> et d'une <strong>entité contractante</strong> est ouverte à l'<strong>UGPM
 * </strong>, en plus de la PRMP. Une UGPM qui importe un PPM dont l'autorité manque au référentiel se
 * heurtait à un <strong>403</strong> et ne pouvait pas préparer son dossier.
 *
 * <p>C'est cohérent avec « l'UGPM saisit, la PRMP engage » : créer une entité absente est un acte de
 * <strong>saisie</strong>, borné par l'ADMIN qui seul active le rattachement. La frontière tient
 * ailleurs — la <strong>soumission</strong> reste réservée à la PRMP, et n'est pas touchée
 * (cf. {@code UgpmIntegrationTest}, « ne peut PAS soumettre (403) »).</p>
 *
 * <p>⚠️ <strong>Le rattachement en attente cible la PRMP de tutelle sans aucune dérivation ajoutée</strong>,
 * et ce test l'établit : le claim {@code ref} d'une UGPM <em>est</em> l'ID de sa PRMP de tutelle, posé à
 * l'authentification. Le vérifier ici évite qu'on « corrige » plus tard un code déjà juste.</p>
 */
class CreationEntiteParUgpmIntegrationTest extends CnmIntegrationTestSupport {

    /** Jeton UGPM : login propre à l'unité, {@code ref} = la PRMP de tutelle (convention AuthService). */
    private String tokenUgpm() {
        return bearer("UGPM002", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
    }

    private int creer(String chemin, String corps, String token) throws Exception {
        var res = mvc.perform(post(chemin).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn();
        Assertions.assertEquals(201, res.getResponse().getStatus(),
                chemin + " refuse -- corps : " + res.getResponse().getContentAsString());
        return (int) (Integer) JsonPath.read(res.getResponse().getContentAsString(),
                chemin.contains("ministeres") ? "$.idMinistere"
                        : chemin.contains("organigrammes") ? "$.idOrganigramme" : "$.idEntiteContract");
    }

    @Test
    @DisplayName("UGPM : crée ministère + organigramme + entité (201) ; le rattachement en attente cible la PRMP de tutelle")
    void ugpm_creeMinistereOrganigrammeEntite_rattachementSurLaTutelle() throws Exception {
        String token = tokenUgpm();

        int idMinistere = creer("/api/ministeres",
                "{\"libelleMinistere\":\"Ministere de l'Energie et des Hydrocarbures\",\"sigle\":\"MEH\"}", token);
        int idOrganigramme = creer("/api/organigrammes",
                "{\"idMinistere\":" + idMinistere + ",\"libelle\":\"Organigramme MEH\",\"actif\":true}", token);
        int idEntite = creer("/api/entite-contracts",
                "{\"libelleEntite\":\"JIRO SY RANO MALAGASY\",\"adresse\":\"Antananarivo\",\"idOrganigramme\":"
                        + idOrganigramme + ",\"idLocalite\":\"ANT\"}", token);

        // ⚠️ Le cœur de la demande : le lien vise la PRMP de tutelle, PAS l'UGPM — sinon l'entité
        // n'entrerait jamais dans le périmètre que la PRMP et ses UGPM partagent.
        PrmpEntite lien = prmpEntiteRepository.findAll().stream()
                .filter(l -> Integer.valueOf(idEntite).equals(l.getIdEntiteContract()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucun rattachement cree pour l'entite " + idEntite));
        Assertions.assertEquals("PRMP001", lien.getIdPrmp(), "le rattachement doit cibler la PRMP de tutelle");
        Assertions.assertEquals(Boolean.FALSE, lien.getActif(),
                "le rattachement reste EN ATTENTE : l'ADMIN seul l'active");
    }

    @Test
    @DisplayName("La PRMP garde ces trois créations ; un contrôleur reste refusé (403) — l'ouverture ne vaut que pour PRMP/UGPM/Admin")
    void prmpConserve_controleurRefuse() throws Exception {
        // La PRMP n'a rien perdu.
        int idMinistere = creer("/api/ministeres",
                "{\"libelleMinistere\":\"Ministere des Travaux Publics\",\"sigle\":\"MTP\"}", tokenPrmp);
        Assertions.assertTrue(ministereRepository.existsById(idMinistere));

        // Un contrôleur (ici le Secrétaire) n'est pas concerné par l'ouverture : les trois restent fermées.
        String tokenSecretaire = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(post("/api/ministeres").header("Authorization", tokenSecretaire)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"libelleMinistere\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/organigrammes").header("Authorization", tokenSecretaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idMinistere\":1,\"libelle\":\"X\",\"actif\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenSecretaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelleEntite\":\"X\",\"adresse\":\"Y\",\"idOrganigramme\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'ouverture ne déborde pas : PUT et DELETE de ces référentiels restent Administrateur (UGPM → 403)")
    void ugpm_neModifieNiNeSupprime() throws Exception {
        String token = tokenUgpm();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/entite-contracts/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelleEntite\":\"Renommee\",\"adresse\":\"Z\",\"idOrganigramme\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/entite-contracts/1").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/ministeres/1").header("Authorization", token))
                .andExpect(status().isForbidden());
    }
}
