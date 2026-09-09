package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.repository.StatutMarcheRepository;

/**
 * ⚠️ <strong>Référentiel « Statut de marché »</strong> (demande pilote du 2026-09-09) — CRUD sur le
 * moule des natures et des modes de passation, et validation du code à l'écriture d'un marché.
 *
 * <p>{@code t_marche.STATUT} stockait un <strong>texte libre</strong> : toujours {@code PREVU} en
 * pratique, mais rien ne l'imposait et l'Administrateur ne pouvait rien y ajouter.</p>
 *
 * <p>Ce que ces tests protègent : la <strong>lecture ouverte</strong> et l'<strong>écriture réservée à
 * l'Administrateur</strong>, le <strong>seed {@code PREVU}</strong> que portent les lignes existantes, le
 * <strong>défaut serveur</strong> à l'omission, le <strong>400 nommant les valeurs possibles</strong>
 * pour un code inconnu, et deux règles qui protègent le référentiel de lui-même : le défaut ne se
 * supprime pas, et un statut désactivé reste acceptable à l'écriture.</p>
 */
class StatutMarcheReferentielIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private StatutMarcheRepository statutMarcheRepository;

    /** Un dossier DDP en brouillon, propriété de la PRMP : le seul contexte où un marché s'ajoute. */
    @BeforeEach
    void brouillonDdp() {
        natureRepository.save(new cnm.prs.entity.Nature(1, "Travaux", null));   // FK de t_marche.ID_NATURE
        Dossier d = dossier(620, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(620, 620, "PRMP001"));
    }

    private org.springframework.test.web.servlet.ResultActions creerMarche(String statutJson) throws Exception {
        return mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":620,\"idPpm\":620,\"montEstim\":500000000,\"idNature\":1"
                        + statutJson + "}"));
    }

    // ------------------------------------------------------------------ 1. le référentiel lui-même

    @Test
    @DisplayName("Le référentiel est servi à tout profil authentifié et porte PREVU dès la migration : "
            + "c'est le code que toutes les lignes existantes portent déjà")
    void referentiel_luParTous_etSeedeAvecPrevu() throws Exception {
        for (String jeton : new String[] { tokenPrmp, tokenMembre, tokenCc, tokenAdmin }) {
            mvc.perform(get("/api/statut-marches").header("Authorization", jeton))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.code=='PREVU')]", hasSize(1)))
                    .andExpect(jsonPath("$[?(@.code=='PREVU')].actif", hasItem(true)));
        }
        mvc.perform(get("/api/statut-marches/PREVU").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Prévu"));
        // Lecture ouverte n'est pas lecture publique : sans jeton, rien.
        mvc.perform(get("/api/statut-marches")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Écriture réservée à l'Administrateur — POST/PUT/DELETE refusés à la PRMP comme au "
            + "Membre (403) ; l'Admin crée, corrige et supprime")
    void ecriture_reserveeALAdministrateur() throws Exception {
        String corps = "{\"code\":\"ATTRIBUE\",\"libelle\":\"Attribué\",\"ordre\":20}";
        mvc.perform(post("/api/statut-marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/statut-marches").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/statut-marches").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ATTRIBUE"))
                .andExpect(jsonPath("$.actif").value(true));   // absent = actif

        mvc.perform(put("/api/statut-marches/ATTRIBUE").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ATTRIBUE\",\"libelle\":\"Attribué (notifié)\",\"ordre\":5,\"actif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Attribué (notifié)"))
                .andExpect(jsonPath("$.actif").value(false));

        // Ordre d'affichage : le rang saisi décide, PREVU (10) passe après ATTRIBUE (5).
        mvc.perform(get("/api/statut-marches").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[0].code").value("ATTRIBUE"))
                .andExpect(jsonPath("$[1].code").value("PREVU"));

        mvc.perform(delete("/api/statut-marches/ATTRIBUE").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/statut-marches/ATTRIBUE").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        assertThat(statutMarcheRepository.existsById("ATTRIBUE")).isFalse();
    }

    @Test
    @DisplayName("⚠️ Le DÉFAUT est indestructible — supprimer PREVU laisserait les écritures sans repli : "
            + "409 qui propose la désactivation à la place")
    void leDefaut_neSeSupprimePas() throws Exception {
        mvc.perform(delete("/api/statut-marches/PREVU").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Désactivez-le")));
        assertThat(statutMarcheRepository.existsById("PREVU")).isTrue();
    }

    // ------------------------------------------------------------------ 2. l'écriture d'un marché

    @Test
    @DisplayName("Écriture d'un marché — statut OMIS : le serveur applique le défaut PREVU ; un code du "
            + "référentiel est accepté tel quel")
    void ecritureMarche_defautEtCodeConnu() throws Exception {
        creerMarche("")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("PREVU"));

        mvc.perform(post("/api/statut-marches").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ANNULE\",\"libelle\":\"Annulé\",\"ordre\":90}"))
                .andExpect(status().isCreated());
        creerMarche(",\"statut\":\"ANNULE\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ANNULE"));
    }

    @Test
    @DisplayName("Écriture d'un marché — code INCONNU refusé en 400, et le refus ÉNUMÈRE les valeurs "
            + "possibles : sans cela l'appelant n'a aucun moyen de savoir ce qu'on attend de lui")
    void ecritureMarche_codeInconnu_400Nomme() throws Exception {
        creerMarche(",\"statut\":\"EN_COURS\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("EN_COURS")))
                .andExpect(jsonPath("$.message", containsString("PREVU")));
    }

    @Test
    @DisplayName("⚠️ Un statut DÉSACTIVÉ reste accepté à l'écriture — des marchés le portent, et refuser "
            + "leur code interdirait de les ré-enregistrer : désactiver guide la saisie, ne réécrit pas l'histoire")
    void statutDesactive_resteAcceptableALEcriture() throws Exception {
        mvc.perform(post("/api/statut-marches").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"OBSOLETE\",\"libelle\":\"Obsolète\",\"actif\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actif").value(false));

        String rep = creerMarche(",\"statut\":\"OBSOLETE\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("OBSOLETE"))
                .andReturn().getResponse().getContentAsString();

        // Et il se ré-enregistre : c'est précisément ce qu'une garde sur l'activité aurait cassé.
        int idDetail = JsonPath.read(rep, "$.idDetail");
        mvc.perform(put("/api/marches/" + idDetail).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":620,\"idPpm\":620,\"montEstim\":600000000,\"idNature\":1,"
                        + "\"statut\":\"OBSOLETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("OBSOLETE"));
    }

    @Test
    @DisplayName("Garde du code — POST en doublon refusé, libellé vide refusé, code introuvable → 404")
    void gardesDuReferentiel() throws Exception {
        mvc.perform(post("/api/statut-marches").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"PREVU\",\"libelle\":\"Doublon\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/statut-marches").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"VIDE\",\"libelle\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/statut-marches/INEXISTANT").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }
}
