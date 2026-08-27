package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/**
 * ⚠️ Audit 2026-08-27 (lot C, volet 2) — référentiels <strong>sans aucun test</strong> :
 * {@code aviss}, {@code natures}, {@code type-dossiers}, {@code profiles}, {@code ministeres},
 * {@code organigrammes}, {@code cat-comptes}, {@code regle-alertes}, {@code regle-anomalies},
 * {@code type-dmc}. Un test paramétré unique prouve, pour chacun : lecture ouverte à un profil
 * non-admin (200), écriture (POST/PUT/DELETE) refusée à ce même profil (403), cycle CRUD complet
 * pour l'Administrateur (2xx) avec vérification de l'effet du PUT.
 *
 * <p><strong>Trouvaille</strong> — aucun de ces contrôleurs ne porte de {@code @PreAuthorize} : la
 * garde d'écriture vit entièrement dans {@code SecurityConfig} (tableaux {@code REFERENTIELS} /
 * {@code REFERENTIELS_ID}, appariement chemin + méthode HTTP), pas au niveau du contrôleur. C'est
 * la garde réelle vérifiée ici — {@code AnomalieController} est le seul de la liste d'origine à
 * porter un {@code @PreAuthorize} explicite, et il est traité à part (déjà partiellement testé).</p>
 *
 * <p>Complète aussi les référentiels <strong>partiellement</strong> testés : {@code points-ctrls}
 * (PUT/DELETE/GET unitaire), {@code localites} (PUT/DELETE/GET unitaire), {@code anomalies}
 * (POST/PUT — le GET et le DELETE-refusé étaient déjà couverts par
 * {@code SecuriteCrudIntegrationTest}).</p>
 */
class ReferentielMatriceIntegrationTest extends CnmIntegrationTestSupport {

    /** Un cas de référentiel : chemin, champ portant l'identifiant, corps POST, et corps/champ du PUT. */
    private record CasReferentiel(String nom, String chemin, String champId, String corpsPost,
            Function<String, String> corpsPut, String champModifie, String valeurModifiee) {
    }

    private static Stream<Arguments> cas() {
        return Stream.of(
                new CasReferentiel("aviss", "/api/aviss", "idAvis",
                        "{\"idAvis\":\"AV9\",\"libelleAvis\":\"Avis test\"}",
                        id -> "{\"idAvis\":\"" + id + "\",\"libelleAvis\":\"Avis modifie\"}",
                        "libelleAvis", "Avis modifie"),
                new CasReferentiel("natures", "/api/natures", "idNature",
                        "{\"idNature\":9001,\"libelle\":\"Nature test\",\"description\":\"desc\"}",
                        id -> "{\"idNature\":" + id + ",\"libelle\":\"Nature modifiee\",\"description\":\"desc\"}",
                        "libelle", "Nature modifiee"),
                new CasReferentiel("type-dossiers", "/api/type-dossiers", "idTypeDossier",
                        "{\"idTypeDossier\":\"ZZ9\",\"libelleType\":\"Type test\"}",
                        id -> "{\"idTypeDossier\":\"" + id + "\",\"libelleType\":\"Type modifie\"}",
                        "libelleType", "Type modifie"),
                new CasReferentiel("profiles", "/api/profiles", "idProfile",
                        "{\"idProfile\":9001,\"profile\":\"Profil test\"}",
                        id -> "{\"idProfile\":" + id + ",\"profile\":\"Profil modifie\"}",
                        "profile", "Profil modifie"),
                new CasReferentiel("ministeres", "/api/ministeres", "idMinistere",
                        "{\"idMinistere\":9001,\"libelleMinistere\":\"Ministere test\",\"sigle\":\"MT\"}",
                        id -> "{\"idMinistere\":" + id + ",\"libelleMinistere\":\"Ministere modifie\",\"sigle\":\"MT\"}",
                        "libelleMinistere", "Ministere modifie"),
                new CasReferentiel("organigrammes", "/api/organigrammes", "idOrganigramme",
                        "{\"idOrganigramme\":9001,\"idMinistere\":1,\"actif\":true,\"libelle\":\"Organigramme test\"}",
                        id -> "{\"idOrganigramme\":" + id
                                + ",\"idMinistere\":1,\"actif\":true,\"libelle\":\"Organigramme modifie\"}",
                        "libelle", "Organigramme modifie"),
                new CasReferentiel("cat-comptes", "/api/cat-comptes", "idCatCompte",
                        "{\"idCatCompte\":\"CC9\",\"catCompte\":\"Categorie test\"}",
                        id -> "{\"idCatCompte\":\"" + id + "\",\"catCompte\":\"Categorie modifiee\"}",
                        "catCompte", "Categorie modifiee"),
                new CasReferentiel("regle-alertes", "/api/regle-alertes", "idRegleAlerte",
                        "{\"idRegleAlerte\":9001,\"typeJalon\":\"LANCEMENT\",\"joursAvant\":7,\"actif\":true}",
                        id -> "{\"idRegleAlerte\":" + id + ",\"typeJalon\":\"LANCEMENT\",\"joursAvant\":14,\"actif\":true}",
                        "joursAvant", "14"),
                new CasReferentiel("regle-anomalies", "/api/regle-anomalies", "idRegleAnomalie",
                        "{\"idRegleAnomalie\":9001,\"codeRegle\":\"REG_TEST\",\"libelle\":\"Regle test\",\"actif\":true}",
                        id -> "{\"idRegleAnomalie\":" + id
                                + ",\"codeRegle\":\"REG_TEST\",\"libelle\":\"Regle modifiee\",\"actif\":true}",
                        "libelle", "Regle modifiee"),
                new CasReferentiel("type-dmc", "/api/type-dmc", "idTypeDmc",
                        "{\"code\":\"TD9\",\"libelle\":\"Type DMC test\",\"actif\":true}",
                        id -> "{\"idTypeDmc\":" + id + ",\"code\":\"TD9\",\"libelle\":\"Type DMC modifie\",\"actif\":true}",
                        "libelle", "Type DMC modifie"))
                .map(c -> Arguments.of(c));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cas")
    @DisplayName("Référentiel : lecture ouverte (200), écriture refusée au non-admin (403), CRUD complet Admin (2xx)")
    void referentiel_matriceProfil(CasReferentiel cas) throws Exception {
        // Lecture ouverte à un profil non-admin (Membre).
        mvc.perform(get(cas.chemin()).header("Authorization", tokenMembre))
                .andExpect(status().isOk());

        // Écriture refusée au non-admin : POST, PUT et DELETE (id quelconque — l'autorisation est
        // vérifiée avant toute résolution de la ressource, cf. SecurityConfig).
        mvc.perform(post(cas.chemin()).header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(cas.corpsPost()))
                .andExpect(status().isForbidden());
        mvc.perform(put(cas.chemin() + "/999999").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(cas.corpsPut().apply("999999")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(cas.chemin() + "/999999").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Cycle CRUD complet pour l'Administrateur.
        String repPost = mvc.perform(post(cas.chemin()).header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(cas.corpsPost()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Object valeurId = JsonPath.read(repPost, "$." + cas.champId());
        String id = String.valueOf(valeurId);

        mvc.perform(get(cas.chemin() + "/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk());

        mvc.perform(put(cas.chemin() + "/" + id).header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(cas.corpsPut().apply(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + cas.champModifie()).value(cas.valeurModifiee()));

        mvc.perform(delete(cas.chemin() + "/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        mvc.perform(get(cas.chemin() + "/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Trouvaille : ministeres/organigrammes — POST exceptionnellement ouvert à la PRMP
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TROUVAILLE : POST /api/ministeres et /api/organigrammes sont EXCEPTIONNELLEMENT ouverts a la PRMP (import PPM), pas seulement a l'Admin")
    void ministeresEtOrganigrammes_postOuvertPrmpAussi() throws Exception {
        // SecurityConfig (lignes 139-143) place ces deux matchers AVANT la règle générique REFERENTIELS :
        // la PRMP peut déclarer un ministère/organigramme manquant au référentiel lors d'un import PPM.
        mvc.perform(post("/api/ministeres").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idMinistere\":9002,\"libelleMinistere\":\"Ministere via PRMP\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/organigrammes").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idOrganigramme\":9002,\"idMinistere\":9002,\"actif\":true}"))
                .andExpect(status().isCreated());

        // PUT/DELETE restent Administrateur seul (règle générique REFERENTIELS_ID, pas d'exception).
        mvc.perform(put("/api/ministeres/9002").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idMinistere\":9002,\"libelleMinistere\":\"Pirate\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/organigrammes/9002").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // points-ctrls — complète PUT/DELETE/GET unitaire (jamais testés)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("points-ctrls : GET unitaire, PUT et DELETE — écriture Admin seule, 403 au non-admin")
    void pointsCtrls_putDeleteGetUnitaire() throws Exception {
        String corps = "{\"idPointCtrl\":9001,\"libelPointCtrl\":\"Point test\",\"obligatoire\":true,\"idTypeDossier\":\"DDP\"}";
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/points-ctrls/9001").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelPointCtrl").value("Point test"));

        String corpsPut = "{\"idPointCtrl\":9001,\"libelPointCtrl\":\"Point modifie\",\"obligatoire\":false,\"idTypeDossier\":\"DDP\"}";
        mvc.perform(put("/api/points-ctrls/9001").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsPut))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/points-ctrls/9001").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsPut))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelPointCtrl").value("Point modifie"))
                .andExpect(jsonPath("$.obligatoire").value(false));

        mvc.perform(delete("/api/points-ctrls/9001").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/points-ctrls/9001").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/points-ctrls/9001").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // localites — complète PUT/DELETE/GET unitaire (POST/GET liste déjà testés ailleurs)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("localites : GET unitaire, PUT et DELETE — écriture Admin seule, 403 au non-admin")
    void localites_putDeleteGetUnitaire() throws Exception {
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idLocalite\":\"MAJ\",\"libelleLocalite\":\"Mahajanga\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/localites/MAJ").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelleLocalite").value("Mahajanga"));

        String corpsPut = "{\"idLocalite\":\"MAJ\",\"libelleLocalite\":\"Mahajanga renomme\",\"chefLieu\":\"Mahajanga\"}";
        mvc.perform(put("/api/localites/MAJ").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsPut))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/localites/MAJ").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsPut))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelleLocalite").value("Mahajanga renomme"))
                .andExpect(jsonPath("$.chefLieu").value("Mahajanga"));

        mvc.perform(delete("/api/localites/MAJ").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/localites/MAJ").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/localites/MAJ").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // anomalies — complète POST/PUT (GET et DELETE-refusé déjà couverts par SecuriteCrudIntegrationTest)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("anomalies : POST/PUT réservés à l'Administrateur (403 au Président, qui lit sans écrire) ; cycle complet Admin")
    void anomalies_postPut_adminSeul() throws Exception {
        // FK réelle : idRegleAnomalie doit référencer une règle existante.
        cnm.prs.entity.RegleAnomalie regle = new cnm.prs.entity.RegleAnomalie();
        regle.setIdRegleAnomalie(9001);
        regle.setCodeRegle("REG_ANOM_TEST");
        regleAnomalieRepository.save(regle);

        String corps = "{\"idAnomalie\":9001,\"idRegleAnomalie\":9001,\"typeAnomalie\":\"TEST\",\"statut\":\"DETECTEE\"}";
        // Le Président lit (déjà couvert) mais n'écrit pas.
        mvc.perform(post("/api/anomalies").header("Authorization", tokenPresident)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/anomalies").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("DETECTEE"));

        String corpsPut = "{\"idAnomalie\":9001,\"idRegleAnomalie\":9001,\"typeAnomalie\":\"TEST\",\"statut\":\"TRAITEE\"}";
        mvc.perform(put("/api/anomalies/9001").header("Authorization", tokenPresident)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsPut))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/anomalies/9001").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsPut))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TRAITEE"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.RegleAnomalieRepository regleAnomalieRepository;
}
