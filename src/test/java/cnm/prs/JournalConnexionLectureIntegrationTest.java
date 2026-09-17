package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.SessionUtilisateur;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front §B4) — recette de {@code GET /api/sessions} : le journal des
 * connexions <strong>en lecture seule</strong>, réservé à l'Administrateur.
 *
 * <p>La ressource remplace le CRUD générique {@code /api/session-utilisateurs}, retiré : il laissait
 * l'Administrateur forger une trace de connexion ou effacer la sienne.</p>
 */
class JournalConnexionLectureIntegrationTest extends CnmIntegrationTestSupport {

    private String tokenAdmin;
    private String tokenMembre;
    private String tokenPrmp;

    @BeforeEach
    void jetons() {
        tokenAdmin = bearer("CTRADM", ProfilUtilisateur.ADMINISTRATEUR, TypeActeur.CONTROLEUR, "CTRADM", "ANT");
        tokenMembre = bearer("CTRMEM", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM", "ANT");
        tokenPrmp = bearer("PRMP001", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP001", null);
    }

    private void seedSession(String id, String acteur, String login, LocalDateTime connexion,
            LocalDateTime deconnexion, boolean succes) {
        SessionUtilisateur s = new SessionUtilisateur();
        s.setIdSession(id);
        s.setImControleur(acteur);
        s.setLogin(login);
        s.setDateConnexion(connexion);
        s.setDateDeconnexion(deconnexion);
        s.setIpAdresse("10.0.0.7");
        s.setUserAgent("Mozilla/5.0 PosteCNM");
        s.setSucces(succes);
        sessionUtilisateurRepository.save(s);
    }

    /** Trois lignes : une session fermée, une ouverte, un échec sur un login inconnu. */
    private void seedTrois() {
        seedSession("S-FERMEE", "CTRADM", "CTRADM",
                LocalDateTime.of(2026, 9, 10, 8, 0), LocalDateTime.of(2026, 9, 10, 9, 30), true);
        seedSession("S-OUVERTE", "PRMP001", "PRMP001",
                LocalDateTime.of(2026, 9, 12, 14, 0), null, true);
        seedSession("S-ECHEC", null, "intrus.inconnu",
                LocalDateTime.of(2026, 9, 14, 3, 15), null, false);
    }

    // ------------------------------------------------------------------
    // Accès
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/sessions : anonyme 401, Membre 403, PRMP 403, Administrateur 200")
    void acces_reserveALAdministrateur() throws Exception {
        mvc.perform(get("/api/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sessions").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sessions").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lecture seule : aucune écriture n'est possible, même pour l'Administrateur")
    void aucuneEcriture_possible() throws Exception {
        // Le CRUD d'origine est parti : plus rien ne répond sur /api/session-utilisateurs.
        mvc.perform(get("/api/session-utilisateurs").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // Et la ressource qui le remplace n'expose que GET : les trois autres verbes sont refusés.
        mvc.perform(post("/api/sessions").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"forge\"}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/sessions/S-FERMEE").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"forge\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/sessions/S-FERMEE").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Contenu et filtres
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Forme Page, tri du plus récent au plus ancien, et la durée d'une session fermée")
    void forme_tri_duree() throws Exception {
        seedTrois();

        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin)
                .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0))
                // Le plus récent d'abord : l'échec du 14, puis la session ouverte du 12, puis celle du 10.
                .andExpect(jsonPath("$.content[0].login").value("intrus.inconnu"))
                .andExpect(jsonPath("$.content[0].acteur").doesNotExist())
                .andExpect(jsonPath("$.content[0].succes").value(false))
                .andExpect(jsonPath("$.content[1].acteur").value("PRMP001"))
                .andExpect(jsonPath("$.content[1].dateDeconnexion").doesNotExist())
                .andExpect(jsonPath("$.content[1].dureeSecondes").doesNotExist())
                .andExpect(jsonPath("$.content[2].acteur").value("CTRADM"))
                .andExpect(jsonPath("$.content[2].ipAdresse").value("10.0.0.7"))
                .andExpect(jsonPath("$.content[2].userAgent").value("Mozilla/5.0 PosteCNM"))
                // 08:00 -> 09:30 : une heure et demie.
                .andExpect(jsonPath("$.content[2].dureeSecondes").value(5400));
    }

    @Test
    @DisplayName("L'empreinte du jeton (ID_SESSION) ne sort pas de la base")
    void idSession_nonExpose() throws Exception {
        seedTrois();

        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].idSession").doesNotExist())
                .andExpect(jsonPath("$.content[0].keys()", Matchers.not(Matchers.hasItem("idSession"))));
    }

    @Test
    @DisplayName("?acteur= retient la référence d'acteur ET le login tenté — un échec inconnu n'a que le second")
    void filtre_acteur() throws Exception {
        seedTrois();

        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin).param("acteur", "PRMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].acteur").value("PRMP001"));
        // Le login tenté suffit : sans cela, la ligne qu'on vient regarder serait invisible.
        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin)
                .param("acteur", "intrus.inconnu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].acteur").doesNotExist());
        // Un filtre vide vaut « pas de filtre ».
        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin).param("acteur", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("?succes= sépare les connexions acceptées des tentatives refusées")
    void filtre_succes() throws Exception {
        seedTrois();

        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin).param("succes", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].login").value("intrus.inconnu"));
        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin).param("succes", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("?du= et ?au= bornent sur des journées ENTIÈRES, extrémités comprises")
    void filtre_dates() throws Exception {
        seedTrois();

        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin)
                .param("du", "2026-09-12").param("au", "2026-09-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        // La journée du 14 est incluse en entier, alors que la ligne y est à 03:15.
        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin)
                .param("du", "2026-09-14").param("au", "2026-09-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].login").value("intrus.inconnu"));
        mvc.perform(get("/api/sessions").header("Authorization", tokenAdmin).param("au", "2026-09-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].acteur").value("CTRADM"));
    }
}
