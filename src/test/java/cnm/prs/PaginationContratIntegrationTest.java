package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;

/**
 * ⚠️ Audit 2026-08-27 (lot C, volet 6) — endpoints paginés jamais testés en contrat : forme
 * {@link org.springframework.data.domain.Page} de Spring (content, totalElements, number, size),
 * cohérence d'une 2ᵉ page, et périmètre de visibilité respecté (une PRMP ne voit que ses dossiers/
 * ppms/marchés dans la page). {@code /api/dossiers}, {@code /api/ppms} et {@code /api/marches}
 * routent vers leur variante paginée dès que {@code ?page=} est présent (même chemin, pas de
 * ressource dédiée) ; {@code /api/actualites} de même, réservé à l'Administrateur.
 */
class PaginationContratIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("GET /api/dossiers?page= : forme Page, 2ᵉ page cohérente, PRMP ne voit que SES dossiers")
    void dossiers_pagine_formeEtPerimetre() throws Exception {
        prmpRepository.save(prmp("PRMP900", "TMS"));
        for (int i = 0; i < 3; i++) {
            dossierRepository.save(dossierLoc(6001 + i, "BROUILLON", "ANT", "PRMP001"));
        }
        for (int i = 0; i < 2; i++) {
            dossierRepository.save(dossierLoc(6101 + i, "BROUILLON", "TMS", "PRMP900"));
        }

        // ⚠️ Filtre statut=BROUILLON : le dossier 1 du seed standard (statut EXAMINE, idPrmp NULL)
        // est visible de TOUTE PRMP en lecture (dossier « sans propriétaire connu », lu par tolérance
        // — cf. DossierService) ; le filtrer isole proprement les 3 fixtures de ce test.
        String p0 = mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp)
                        .param("page", "0").param("size", "2").param("statut", "BROUILLON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andReturn().getResponse().getContentAsString();

        String p1 = mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp)
                        .param("page", "1").param("size", "2").param("statut", "BROUILLON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(1))
                .andReturn().getResponse().getContentAsString();

        List<Integer> idsP0 = JsonPath.read(p0, "$.content[*].idDossier");
        List<Integer> idsP1 = JsonPath.read(p1, "$.content[*].idDossier");
        Set<Integer> tousLesIds = java.util.stream.Stream.concat(idsP0.stream(), idsP1.stream())
                .collect(Collectors.toSet());
        // 2ᵉ page cohérente : pas de recouvrement avec la 1ʳᵉ, ensemble = les 3 dossiers de PRMP001.
        org.junit.jupiter.api.Assertions.assertEquals(3, tousLesIds.size(), "pas de doublon entre les 2 pages");
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(6001, 6002, 6003), tousLesIds);
        // Jamais les dossiers de la PRMP étrangère (périmètre de visibilité, §1).
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Collections.disjoint(tousLesIds, Set.of(6101, 6102)));
    }

    @Test
    @DisplayName("GET /api/ppms?page= : forme Page, 2ᵉ page cohérente, PRMP ne voit que SES ppms")
    void ppms_pagine_formeEtPerimetre() throws Exception {
        // ⚠️ findVisiblesParPrmp (PpmRepository) exclut les PPM dont le dossier est BROUILLON : les
        // dossiers porteurs doivent être au-delà du brouillon pour être visibles dans cette liste.
        // Le PPM 1 du seed standard (dossier 1, PRMP001, statut EXAMINE) serait donc lui aussi visible :
        // basculé en BROUILLON ici (transaction isolée à ce test) pour isoler proprement les fixtures.
        Dossier d1 = dossierRepository.findById(1).orElseThrow();
        d1.setStatut("BROUILLON");
        dossierRepository.save(d1);
        prmpRepository.save(prmp("PRMP901", "TMS"));
        for (int i = 0; i < 3; i++) {
            Dossier d = dossier(7001 + i, "SOUMIS");
            d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT");
            dossierRepository.save(d);
            Ppm p = ppm(7001 + i, 7001 + i, "PRMP001");
            ppmRepository.save(p);
        }
        for (int i = 0; i < 2; i++) {
            Dossier d = dossier(7101 + i, "SOUMIS");
            d.setIdPrmp("PRMP901"); d.setIdLocalite("TMS");
            dossierRepository.save(d);
            ppmRepository.save(ppm(7101 + i, 7101 + i, "PRMP901"));
        }

        String p0 = mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp)
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andReturn().getResponse().getContentAsString();
        String p1 = mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.number").value(1))
                .andReturn().getResponse().getContentAsString();

        List<Integer> idsP0 = JsonPath.read(p0, "$.content[*].idPpm");
        List<Integer> idsP1 = JsonPath.read(p1, "$.content[*].idPpm");
        Set<Integer> tous = java.util.stream.Stream.concat(idsP0.stream(), idsP1.stream()).collect(Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(7001, 7002, 7003), tous);
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Collections.disjoint(tous, Set.of(7101, 7102)));
    }

    @Test
    @DisplayName("GET /api/marches?page= : forme Page, 2ᵉ page cohérente, PRMP ne voit que SES marchés")
    void marches_pagine_formeEtPerimetre() throws Exception {
        prmpRepository.save(prmp("PRMP902", "TMS"));
        for (int i = 0; i < 3; i++) {
            Dossier d = dossier(8001 + i, "BROUILLON");
            d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT");
            dossierRepository.save(d);
            ppmRepository.save(ppm(8001 + i, 8001 + i, "PRMP001"));
            marcheRepository.save(marche(8001 + i, 8001 + i, 8001 + i));
        }
        for (int i = 0; i < 2; i++) {
            Dossier d = dossier(8101 + i, "BROUILLON");
            d.setIdPrmp("PRMP902"); d.setIdLocalite("TMS");
            dossierRepository.save(d);
            ppmRepository.save(ppm(8101 + i, 8101 + i, "PRMP902"));
            marcheRepository.save(marche(8101 + i, 8101 + i, 8101 + i));
        }

        String p0 = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp)
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andReturn().getResponse().getContentAsString();
        String p1 = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.number").value(1))
                .andReturn().getResponse().getContentAsString();

        List<Integer> idsP0 = JsonPath.read(p0, "$.content[*].idDetail");
        List<Integer> idsP1 = JsonPath.read(p1, "$.content[*].idDetail");
        Set<Integer> tous = java.util.stream.Stream.concat(idsP0.stream(), idsP1.stream()).collect(Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(8001, 8002, 8003), tous);
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Collections.disjoint(tous, Set.of(8101, 8102)));
    }

    @Test
    @DisplayName("Lot D §3 — la page est découpée EN SQL : demander 2 lignes sur 25 ne charge pas "
            + "les 25 (dossiers, ppms, marchés)")
    void pagination_decoupeeEnSql_neChargePasToutLeScope() throws Exception {
        // ⚠️ Audit 2026-08-27 (lot D §3) : Pagination.depuisListe(findAll(), pageable) chargeait et
        // mappait la table scopée ENTIÈRE à chaque page. Le compteur d'entités chargées par Hibernate
        // le prouve directement — c'est la seule mesure qui distingue les deux implémentations, la
        // forme de la réponse étant, elle, volontairement identique.
        for (int i = 0; i < 25; i++) {
            Dossier d = dossier(9301 + i, "SOUMIS");
            d.setIdPrmp("PRMP001");
            d.setIdLocalite("ANT");
            dossierRepository.save(d);
            ppmRepository.save(ppm(9301 + i, 9301 + i, "PRMP001"));
            marcheRepository.save(marche(9301 + i, 9301 + i, 9301 + i));
        }

        assertPageDecoupeeEnSql("/api/dossiers", "statut", "SOUMIS");
        assertPageDecoupeeEnSql("/api/ppms", null, null);
        assertPageDecoupeeEnSql("/api/marches", null, null);
    }

    /**
     * Demande 2 lignes de l'endpoint et vérifie que le serveur n'a pas chargé tout le périmètre pour
     * les servir. Le seuil (12) laisse la place aux entités techniques de la requête (compte, acteur,
     * référentiels) tout en restant très en dessous des 25 lignes du périmètre.
     */
    private void assertPageDecoupeeEnSql(String url, String param, String valeur) throws Exception {
        org.hibernate.stat.Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        entityManager.flush();
        entityManager.clear();   // le cache de premier niveau masquerait les chargements
        stats.clear();

        var requete = get(url).header("Authorization", tokenPrmp).param("page", "0").param("size", "2");
        if (param != null) {
            requete = requete.param(param, valeur);
        }
        mvc.perform(requete)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.size").value(2));

        long charges = stats.getEntityLoadCount();
        org.junit.jupiter.api.Assertions.assertTrue(charges > 0, "statistiques Hibernate actives (" + url + ")");
        org.junit.jupiter.api.Assertions.assertTrue(charges <= 12,
                url + " : " + charges + " entités chargées pour 2 lignes demandées — le périmètre "
                        + "entier (25 lignes) est encore chargé côté serveur");
    }

    @Test
    @DisplayName("GET /api/actualites?page= : forme Page (Administrateur), 2ᵉ page cohérente")
    void actualites_pagine_forme() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"titre\":\"Actu " + i + "\",\"contenuMd\":\"corps\",\"profilsCibles\":[\"MEMBRE\"]}"))
                    .andExpect(status().isCreated());
        }

        String p0 = mvc.perform(get("/api/actualites").header("Authorization", tokenAdmin)
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andReturn().getResponse().getContentAsString();
        String p1 = mvc.perform(get("/api/actualites").header("Authorization", tokenAdmin)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.number").value(1))
                .andReturn().getResponse().getContentAsString();

        List<Integer> idsP0 = JsonPath.read(p0, "$.content[*].idActualite");
        List<Integer> idsP1 = JsonPath.read(p1, "$.content[*].idActualite");
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Collections.disjoint(idsP0, idsP1),
                "pas de recouvrement entre les 2 pages");

        // Réservé à l'Administrateur (non-régression de la garde existante).
        mvc.perform(get("/api/actualites").header("Authorization", tokenMembre).param("page", "0"))
                .andExpect(status().isForbidden());
    }
}
