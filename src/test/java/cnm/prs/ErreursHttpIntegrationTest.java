package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * ⚠️ Recette de bout en bout (2026-08-27) — <strong>deux 500 qui n'en étaient pas</strong>.
 *
 * <p>{@code GlobalExceptionHandler} traitait déjà le corps de requête illisible
 * ({@code HttpMessageNotReadableException}) mais pas ses deux voisines, qui tombaient donc dans le
 * filet {@code Exception} : un <strong>paramètre d'URL mal typé</strong>
 * ({@code MethodArgumentTypeMismatchException}) et un <strong>chemin inconnu</strong>
 * ({@code NoResourceFoundException}). Dans les deux cas la faute est celle de l'appelant, et il
 * recevait « Une erreur interne est survenue. » — message qui l'envoie chercher la panne du mauvais
 * côté, et qui remplit les journaux serveur de piles pour rien.</p>
 *
 * <p>Attendu : <strong>400</strong> nommant le paramètre fautif, <strong>404</strong> pour le
 * chemin inconnu.</p>
 *
 * <p>⚠️ Recette du 2026-08-28 — <strong>trois de plus</strong>, même diagnostic : les exceptions MVC
 * standard que Spring lève AVANT d'entrer dans le contrôleur n'ont de handler que si on l'écrit
 * ({@code GlobalExceptionHandler} n'étend pas {@code ResponseEntityExceptionHandler}). Restaient au
 * filet : <strong>verbe non monté</strong> ({@code HttpRequestMethodNotSupportedException} →
 * <strong>405</strong> + {@code Allow}), <strong>paramètre requis absent</strong>
 * ({@code MissingServletRequestParameterException} → <strong>400</strong>) et <strong>type de contenu
 * non supporté</strong> ({@code HttpMediaTypeNotSupportedException} → <strong>415</strong>).</p>
 */
class ErreursHttpIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Recette — GET /api/audit-logs?du=<datetime> : 400 nommant « du », pas 500")
    void parametreDate_malforme_400() throws Exception {
        // Repro exacte de la recette : un datetime là où le contrat attend un LocalDate (AAAA-MM-JJ).
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin)
                        .param("page", "0").param("du", "2026-08-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Paramètre « du » invalide : format de date attendu AAAA-MM-JJ."))
                .andExpect(jsonPath("$.erreurs[0].champ").value("du"));

        // La borne bien formée passe : le handler ne masque pas le cas nominal.
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin)
                        .param("page", "0").param("du", "2026-08-01"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Recette — paramètre numérique malformé sur une autre ressource : 400 nommant le paramètre")
    void parametreNumerique_malforme_400() throws Exception {
        mvc.perform(get("/api/observations-pv").header("Authorization", tokenCc)
                        .param("dossier", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Paramètre « dossier » invalide : valeur numérique attendue."))
                .andExpect(jsonPath("$.erreurs[0].champ").value("dossier"));
    }

    @Test
    @DisplayName("Recette — GET /api/auth/moi (route inexistante) : 404, pas 500")
    void cheminInconnu_404() throws Exception {
        // Repro exacte de la recette : /api/auth/** est en permitAll, la requête va jusqu'au
        // DispatcherServlet, qui ne trouve ni contrôleur ni ressource statique.
        mvc.perform(get("/api/auth/moi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ressource introuvable."));

        // Même traitement derrière l'authentification (le chemin est inconnu, pas interdit).
        mvc.perform(get("/api/ressource-qui-nexiste-pas").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ressource introuvable."));
    }

    @Test
    @DisplayName("Recette — PATCH /api/audit-logs (verbe non monté) : 405 + en-tête Allow, pas 500")
    void methodeNonAutorisee_405() throws Exception {
        // Repro exacte de la recette : le chemin existe (GET, POST), PATCH n'y est pas monté.
        mvc.perform(patch("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET, POST"))
                .andExpect(jsonPath("$.message").value(
                        "Méthode PATCH non autorisée sur cette ressource. Méthodes permises : GET, POST."));

        // Le verbe monté sur le même chemin passe : le handler ne masque pas le cas nominal.
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Recette — GET /api/observations-pv sans ?dossier : 400 nommant le paramètre, pas 500")
    void parametreRequisAbsent_400() throws Exception {
        // Repro exacte de la recette : le paramètre est déclaré obligatoire, la liaison échoue avant
        // l'entrée dans la méthode du contrôleur.
        mvc.perform(get("/api/observations-pv").header("Authorization", tokenCc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Paramètre « dossier » manquant : ce paramètre est obligatoire."))
                .andExpect(jsonPath("$.erreurs[0].champ").value("dossier"));

        // Le paramètre fourni passe : le handler ne masque pas le cas nominal.
        mvc.perform(get("/api/observations-pv").header("Authorization", tokenCc).param("dossier", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Recette — POST /api/audit-logs en text/plain : 415 nommant le type reçu, pas 500")
    void typeContenuNonSupporte_415() throws Exception {
        // Repro exacte de la recette : aucun convertisseur ne sait lire text/plain vers le DTO.
        mvc.perform(post("/api/audit-logs").header("Authorization", tokenAdmin)
                        .contentType(MediaType.TEXT_PLAIN).content("pas du JSON"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(containsString("text/plain")))
                .andExpect(jsonPath("$.message").value(containsString("non supporté par cette ressource")));
    }
}
