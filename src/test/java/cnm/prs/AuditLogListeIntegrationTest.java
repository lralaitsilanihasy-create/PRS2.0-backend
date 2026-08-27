package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ Audit 2026-08-27 (lot D §4) — <strong>lectures non bornées</strong> du journal d'audit.
 *
 * <p>{@code t_audit_log} reçoit une ligne à chaque écriture de l'application : sa croissance est
 * monotone et sans fin, et l'écran d'administration en demandait la <strong>totalité</strong>
 * ({@code findAll()}). Après quelques mois d'exploitation, cela revient à télécharger des années de
 * journal pour en regarder les vingt dernières lignes.</p>
 *
 * <p>Deux réponses, vérifiées ici : la liste historique est <strong>plafonnée</strong> (500 entrées,
 * les plus récentes) pour que l'écran existant reste utilisable, et une variante
 * <strong>paginée et filtrée en SQL</strong> ({@code ?page=&size=}, {@code table}, {@code acteur},
 * {@code du}, {@code au}) devient le chemin d'accès complet. Toute la ressource reste réservée à
 * l'Administrateur.</p>
 */
class AuditLogListeIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Lot D §4 — GET /api/audit-logs?page= : forme Page, tri dateAction décroissant")
    void auditLogs_pagine_formeEtTri() throws Exception {
        semer(3, "t_dossier", "CTRCC1", LocalDateTime.of(2026, 3, 1, 8, 0));

        String corps = mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin)
                        .param("page", "0").param("size", "2").param("table", "t_dossier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andReturn().getResponse().getContentAsString();

        // Tri imposé par le serveur : la plus récente d'abord (3ᵉ entrée semée, puis la 2ᵉ).
        List<String> dates = com.jayway.jsonpath.JsonPath.read(corps, "$.content[*].dateAction");
        org.junit.jupiter.api.Assertions.assertTrue(dates.get(0).compareTo(dates.get(1)) > 0,
                "journal servi du plus récent au plus ancien, quel que soit le tri demandé");
    }

    @Test
    @DisplayName("Lot D §4 — filtres table / acteur / du-au appliqués en SQL, bornes de dates incluses")
    void auditLogs_pagine_filtres() throws Exception {
        semer(2, "t_dossier", "CTRCC1", LocalDateTime.of(2026, 3, 10, 8, 0));
        semer(2, "t_marche", "CTRCC1", LocalDateTime.of(2026, 3, 20, 8, 0));
        semer(2, "t_dossier", "CTRMEM", LocalDateTime.of(2026, 4, 5, 8, 0));

        // Filtre table.
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin)
                        .param("page", "0").param("size", "20").param("table", "t_marche"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        // Filtre acteur, cumulé au filtre table.
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin)
                        .param("page", "0").param("size", "20")
                        .param("table", "t_dossier").param("acteur", "CTRMEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        // Fenêtre de dates : « au » inclut la journée entière (les entrées du 20/03 à 8h et 9h).
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin)
                        .param("page", "0").param("size", "20")
                        .param("du", "2026-03-20").param("au", "2026-03-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        // Un filtre vide vaut « pas de filtre » (le front envoie volontiers une chaîne vide).
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin)
                        .param("page", "0").param("size", "20").param("table", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    @DisplayName("Lot D §4 — la liste historique est plafonnée à 500 entrées, les plus récentes")
    void auditLogs_listeHistorique_plafonnee() throws Exception {
        semer(505, "t_dossier", "CTRCC1", LocalDateTime.of(2026, 1, 1, 0, 0));

        String corps = mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(500)))
                .andReturn().getResponse().getContentAsString();

        // Ce sont bien les PLUS RÉCENTES : la 1ʳᵉ entrée semée (la plus ancienne) est hors de la liste.
        List<String> dates = com.jayway.jsonpath.JsonPath.read(corps, "$[*].dateAction");
        org.junit.jupiter.api.Assertions.assertTrue(dates.get(0).compareTo(dates.get(499)) > 0,
                "liste historique servie du plus récent au plus ancien");
        org.junit.jupiter.api.Assertions.assertFalse(dates.contains("2026-01-01T00:00:00"),
                "les entrées les plus anciennes sont écartées par le plafond, pas les plus récentes");
    }

    @Test
    @DisplayName("Lot D §4 — la variante paginée reste réservée à l'Administrateur (403 sinon)")
    void auditLogs_pagine_reserveAdministrateur() throws Exception {
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenCc).param("page", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenPrmp).param("page", "0"))
                .andExpect(status().isForbidden());
    }

    /** Insère {@code combien} entrées d'audit, espacées d'une heure à partir de {@code depart}. */
    private void semer(int combien, String nomTable, String acteur, LocalDateTime depart) {
        List<Object[]> lignes = new ArrayList<>();
        for (int i = 0; i < combien; i++) {
            lignes.add(new Object[] { jdbcTemplate.queryForObject("select nextval('seq_audit_log')", Long.class),
                    Timestamp.valueOf(depart.plusHours(i)), acteur, nomTable, "MODIFICATION" });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO public.t_audit_log (\"ID_LOG\", \"DATE_ACTION\", \"IM_ACTEUR\", \"NOM_TABLE\", "
                        + "\"TYPE_ACTION\") VALUES (?, ?, ?, ?, ?)",
                lignes);
    }
}
