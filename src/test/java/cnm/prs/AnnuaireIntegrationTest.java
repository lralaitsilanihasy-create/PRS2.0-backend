package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Controleur;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B2) — {@code GET /api/annuaire} :
 * recherche unifiée des personnes (contrôleurs, PRMP, UGPM), réservée à l'Administrateur.
 *
 * <p>Jeu de départ du socle : 9 contrôleurs (dont 6 en localité ANT, 1 en TMS, 2 sans localité),
 * 1 PRMP (PRMP001, deux entités actives), aucune UGPM, et des comptes pour CTRPRE, CTRCC1, CTRMEM,
 * CTRADM et PRMP001 seulement — les autres contrôleurs sont donc {@code SANS_COMPTE}, ce qui fait du
 * socle le cas limite que la demande réclame de couvrir.</p>
 */
class AnnuaireIntegrationTest extends CnmIntegrationTestSupport {

    // ------------------------------------------------------------------
    // Accès : écran d'administration, rien de moins
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Annuaire : 401 anonyme, 403 pour tout profil autre qu'Administrateur, 200 pour lui")
    void annuaire_reserveALAdministrateur() throws Exception {
        mvc.perform(get("/api/annuaire")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/annuaire").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenPublication))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization",
                        bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization",
                        bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization",
                        bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization",
                        bearer("UGPM001", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "UGPM001", "ANT")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Contenu
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Annuaire : les trois populations dans une seule page, chacune avec son type")
    void annuaire_troisPopulations() throws Exception {
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "Randria", "Hanta"));

        String page = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11))   // 9 contrôleurs + 1 PRMP + 1 UGPM
                .andReturn().getResponse().getContentAsString();

        List<String> types = JsonPath.read(page, "$.content[*].type");
        Assertions.assertEquals(9, types.stream().filter("CONTROLEUR"::equals).count());
        Assertions.assertEquals(1, types.stream().filter("PRMP"::equals).count());
        Assertions.assertEquals(1, types.stream().filter("UGPM"::equals).count());
    }

    @Test
    @DisplayName("Annuaire : une personne sans compte sort avec login null et statutCompte SANS_COMPTE")
    void annuaire_personneSansCompte() throws Exception {
        // CTRVER n'a aucun compte dans le socle ; CTRADM en a un, actif.
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRVER"))
                .andExpect(jsonPath("$.content[0].login").doesNotExist())
                .andExpect(jsonPath("$.content[0].statutCompte").value("SANS_COMPTE"))
                .andExpect(jsonPath("$.content[0].profil").value("VERIFICATEUR"))
                .andExpect(jsonPath("$.content[0].localite").value("ANT"));

        String sansCompte = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("statut", "SANS_COMPTE").param("size", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> refs = JsonPath.read(sansCompte, "$.content[*].ref");
        Assertions.assertTrue(refs.contains("CTRVER"), "le contrôleur sans compte est dans la file SANS_COMPTE");
        Assertions.assertFalse(refs.contains("CTRADM"), "un contrôleur pourvu d'un compte n'y est pas");
    }

    @Test
    @DisplayName("Annuaire : les quatre états de compte — actif, désactivé, en attente, sans compte")
    void annuaire_quatreStatuts() throws Exception {
        CompteAuth desactive = compteAuthRepository.findByLogin("CTRMEM").orElseThrow();
        desactive.setActif(false);        // suspension Administrateur : le STATUT reste ACTIF
        compteAuthRepository.save(desactive);
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "Randria", "Hanta"));
        CompteAuth enAttente = new CompteAuth("ugpm.att", "x", "UGPM", "UGPM001", false);
        enAttente.setStatut("EN_ATTENTE");
        compteAuthRepository.save(enAttente);

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRADM"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("ACTIF"))
                .andExpect(jsonPath("$.content[0].login").value("CTRADM"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRMEM"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("DESACTIVE"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "UGPM001"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.content[0].login").value("ugpm.att"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRSEC"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("SANS_COMPTE"));

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("statut", "DESACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRMEM"));
    }

    @Test
    @DisplayName("Annuaire : q ignore la casse et les accents, et cherche aussi la référence et le login")
    void annuaire_recherche_sansCasseNiAccents() throws Exception {
        Controleur accentue = controleur("CTRACC", 5, "ANT");
        accentue.setNomCont("RANDRIANARISOA");
        accentue.setPrenomsCont("Hérivélo");
        controleurRepository.save(accentue);
        // Login volontairement éloigné du matricule : c'est la question « qui est m.rakotomalala ? ».
        compteAuthRepository.save(new CompteAuth("m.rakotomalala", "x", "CONTROLEUR", "CTRSEC", true));

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "herivelo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRACC"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "HÉRIVÉLO"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "randrianarisoa"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRACC"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "rakotomalala"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRSEC"))
                .andExpect(jsonPath("$.content[0].login").value("m.rakotomalala"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "ctracc"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "introuvable"))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("Annuaire : entité de rattachement pour la PRMP, celle de la tutelle pour l'UGPM")
    void annuaire_entiteDeRattachement() throws Exception {
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "Randria", "Hanta"));

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("type", "PRMP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("PRMP001"))
                .andExpect(jsonPath("$.content[0].entite").value("Entite 1 · Entite 2"))
                .andExpect(jsonPath("$.content[0].profil").doesNotExist())
                .andExpect(jsonPath("$.content[0].localite").doesNotExist());

        // Une recherche sur le nom de l'entité rend la PRMP ET l'UGPM qu'elle chapeaute.
        String parEntite = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("q", "entite 1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> refs = JsonPath.read(parEntite, "$.content[*].ref");
        Assertions.assertEquals(List.of("PRMP001", "UGPM001"), refs.stream().sorted().toList());
    }

    @Test
    @DisplayName("Annuaire : filtres type, profil et localité (code insensible à la casse)")
    void annuaire_filtres() throws Exception {
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("profil", "MEMBRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRMEM"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("localite", "ANT"))
                .andExpect(jsonPath("$.totalElements").value(6));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("localite", "ant"))
                .andExpect(jsonPath("$.totalElements").value(6));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("localite", "TMS"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRCC2"));
        // Les filtres se cumulent : un Chef de commission en ANT, pas celui de TMS.
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("profil", "CHEF_COMMISSION").param("localite", "ANT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRCC1"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("type", "CONTROLEUR"))
                .andExpect(jsonPath("$.totalElements").value(9));
    }

    @Test
    @DisplayName("Annuaire : forme Page, tri nom/prénoms/référence, 2ᵉ page sans recouvrement")
    void annuaire_pagination() throws Exception {
        String p0 = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("q", "nomctr").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements").value(9))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andReturn().getResponse().getContentAsString();
        String p1 = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("q", "nomctr").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.number").value(1))
                .andReturn().getResponse().getContentAsString();

        List<String> refs = new java.util.ArrayList<>(JsonPath.<List<String>>read(p0, "$.content[*].ref"));
        refs.addAll(JsonPath.<List<String>>read(p1, "$.content[*].ref"));
        Assertions.assertEquals(9, java.util.Set.copyOf(refs).size(), "aucun doublon entre les deux pages");
        // Les noms du socle sont « Nom<matricule> » : l'ordre de l'annuaire est donc celui des matricules.
        Assertions.assertEquals(
                List.of("CTRADM", "CTRASS", "CTRCC1", "CTRCC2", "CTRMEM", "CTRPRE", "CTRPUB", "CTRSEC", "CTRVER"),
                refs);
    }

    @Test
    @DisplayName("Annuaire : une valeur inconnue d'un critère énuméré est refusée (400), pas ignorée")
    void annuaire_critereInconnu() throws Exception {
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("statut", "SUSPENDU"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("type", "AGENT"))
                .andExpect(status().isBadRequest());
    }
}
