package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.PointsCtrlRepository;

/**
 * ⚠️ 2026-10-18 (demande front « grille-contrat-cadre ») — B1 : la grille de {@code DAOO-CC} ne sert que les variantes contrat-cadre
 * des points de {@code DAOO} (V133) ; B2 : un point ne cite que des codes présents pour ses formes et catégories (400
 * {@code CODES_ABSENTS} à l'enregistrement, bilan {@code /api/points-ctrls/controle-codes}).
 */
class GrilleContratCadreIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private ChampFicheMarcheRepository champs;
    @Autowired private PointsCtrlRepository points;

    private void champ(String code, String typesMarche, String categories, boolean actif) {
        jdbcTemplate.update("INSERT INTO tr_rubrique_fiche_marche (\"CODE\", \"CODE_BLOC\", \"CODE_COURT\", \"LIBELLE\", \"RANG\", "
                + "\"CATEGORIES\") VALUES (?, 'B09', ?, 'Rubrique d''essai', 99, 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES') "
                + "ON CONFLICT DO NOTHING", code.substring(0, 6), code.substring(4, 6));
        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode(code);
        c.setCodeRubrique(code.substring(0, 6));
        c.setRang(Integer.parseInt(code.substring(7)));
        c.setLibelle("Champ " + code);
        c.setType("TEXTE");
        c.setSource("SAISIE");
        c.setDocumentMaitre("DPAO");
        c.setTypesMarche(typesMarche);
        c.setCategories(categories);
        c.setObligatoire(false);
        c.setActif(actif);
        champs.save(c);
    }

    private PointsCtrl point(int id, String libelle, String description, int ordre, String forme, String categorie) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id);
        p.setLibelPointCtrl(libelle);
        p.setDecriptPointCtrl(description);
        p.setOrdrePointCtrl(ordre);
        p.setObligatoire(true);
        p.setIdTypeDossier("DMC");
        p.setIdSousType("DAOO");
        p.setPortee(PorteePointCtrl.DOSSIER);
        p.setForme(forme);
        p.setCategorie(categorie);
        return points.save(p);
    }

    private String corps(String libelle, String description, String forme, String categorie) {
        return "{\"libelPointCtrl\":\"" + libelle + "\",\"decriptPointCtrl\":\"" + description + "\",\"ordrePointCtrl\":90,"
                + "\"obligatoire\":true,\"idTypeDossier\":\"DMC\",\"idSousType\":\"DAOO\",\"portee\":\"DOSSIER\","
                + "\"forme\":" + (forme == null ? "null" : "\"" + forme + "\"") + ","
                + "\"categorie\":" + (categorie == null ? "null" : "\"" + categorie + "\"") + "}";
    }

    @Test
    @DisplayName("B2 : 400 CODES_ABSENTS à l'enregistrement (forme, retrait, catégorie), bilan sur l'existant")
    void controleDesCodes() throws Exception {
        champ("B09-ZZ-01", "QUANTITE_FIXE,A_COMMANDE", "FOURNITURES_SERVICES", true);
        champ("B09-ZZ-02", "CONTRAT_CADRE", "FOURNITURES_SERVICES,TRAVAUX", true);
        champ("B09-ZZ-03", "QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE", "FOURNITURES_SERVICES", false);
        champ("B09-YY-01", "QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE", "TRAVAUX", true);

        // Un point contrat-cadre qui cite un code des seules autres formes.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("Montants", "Montants (B09-ZZ-01) ?", "CONTRAT_CADRE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODES_ABSENTS"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("B09-ZZ-01 absent pour le contrat-cadre")));
        // Un point sans forme vaut pour toutes : le code du seul contrat-cadre manque aux autres formes.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("Montants", "Montants (B09-ZZ-02) ?", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.ecarts[0].motif").value("HORS_FORME"))
                .andExpect(jsonPath("$.details.ecarts[0].forme").value("AUTRE"));
        // Une catégorie que le code ne connaît pas.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("Visite", "Visite (B09-YY-01) ?", null, "FOURNITURES_SERVICES")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.ecarts[0].motif").value("HORS_CATEGORIE"));
        // Conforme : le code du contrat-cadre, une rubrique, un code d'une catégorie du point.
        String cree = mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("Montants", "Montants (B09-ZZ-02, B09-ZZ, B09-YY-01) ?", "CONTRAT_CADRE", null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(cree, "$.idPointCtrl");
        // La modification aussi : un code retiré, un code inconnu.
        mvc.perform(put("/api/points-ctrls/" + id).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("Montants", "Montants (B09-ZZ-03, B09-XX-09) ?", "CONTRAT_CADRE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("B09-ZZ-03 retiré du référentiel")))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("B09-XX-09 inconnu du référentiel")));

        // Le bilan : le point enregistré avant le contrôle est en écart, le point conforme ne l'est pas.
        PointsCtrl ancien = point(97001, "Garantie", "Garantie (B09-ZZ-01, B09-ZZ-03) ?", 4, null, null);
        String bilan = mvc.perform(get("/api/points-ctrls/controle-codes").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(bilan, "$.points[*].idPointCtrl")).contains(ancien.getIdPointCtrl()).doesNotContain(id);
        assertThat(JsonPath.<List<String>>read(bilan, "$.points[?(@.idPointCtrl==97001)].ecarts[*].motif"))
                .containsExactly("HORS_FORME", "INACTIF");
        assertThat(JsonPath.<List<String>>read(bilan, "$.points[?(@.idPointCtrl==97001)].ecarts[0].forme")).containsExactly("CONTRAT_CADRE");
        assertThat(JsonPath.<Integer>read(bilan, "$.pointsCites")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("B3 : un champ hors de sa rubrique — HORS_RUBRIQUE au contrôle des codes, 400 à l'écriture du champ ; V134 ouvre B04-CD")
    void champHorsDeSaRubrique() throws Exception {
        // Une rubrique des seules autres formes, un champ ouvert au contrat-cadre (comme B04-CD-01 avant V134).
        jdbcTemplate.update("INSERT INTO tr_rubrique_fiche_marche (\"CODE\", \"CODE_BLOC\", \"CODE_COURT\", \"LIBELLE\", \"RANG\", "
                + "\"TYPES_MARCHE\", \"CATEGORIES\") VALUES ('B09-WW', 'B09', 'WW', 'Rubrique fermée', 99, 'QUANTITE_FIXE,A_COMMANDE', "
                + "'FOURNITURES_SERVICES') ON CONFLICT DO NOTHING");
        champ("B09-WW-01", "QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE", "FOURNITURES_SERVICES", true);

        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("Composition", "Fiches jointes (B09-WW-01) ?", "CONTRAT_CADRE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.ecarts[0].motif").value("HORS_RUBRIQUE"))
                .andExpect(jsonPath("$.details.ecarts[0].forme").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("sa rubrique n'est pas ouverte au contrat-cadre")));
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("Composition", "Fiches jointes (B09-WW-01) ?", "AUTRE", null)))
                .andExpect(status().isCreated());

        // L'Administrateur ne peut plus ouvrir un champ au-delà de sa rubrique.
        String champ = "{\"code\":\"B09-WW-02\",\"libelle\":\"Modèle joint\",\"type\":\"TEXTE\",\"source\":\"SAISIE\","
                + "\"documentMaitre\":\"DPAO\",\"typesMarche\":[\"A_COMMANDE\",\"CONTRAT_CADRE\"],\"obligatoire\":false}";
        mvc.perform(post("/api/champs-fiche-marche").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(champ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("typesMarche"))
                .andExpect(jsonPath("$.erreurs[0].message").value(Matchers.containsString("CONTRAT_CADRE")));
        mvc.perform(post("/api/champs-fiche-marche").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(champ.replace("\"typesMarche\":[\"A_COMMANDE\",\"CONTRAT_CADRE\"]", "\"typesMarche\":[\"A_COMMANDE\"],"
                        + "\"categories\":[\"TRAVAUX\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("categories"));
        mvc.perform(post("/api/champs-fiche-marche").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(champ.replace("\"A_COMMANDE\",\"CONTRAT_CADRE\"", "\"A_COMMANDE\"")))
                .andExpect(status().isCreated());

        // V134 : la rubrique B04-CD s'ouvre au contrat-cadre, une fois.
        jdbcTemplate.update("UPDATE tr_rubrique_fiche_marche SET \"TYPES_MARCHE\" = 'QUANTITE_FIXE,A_COMMANDE' WHERE \"CODE\" = 'B04-CD'");
        executerMigrationFlyway("V134__rubrique_composition_contrat_cadre.sql");
        executerMigrationFlyway("V134__rubrique_composition_contrat_cadre.sql");
        assertThat(jdbcTemplate.queryForList("SELECT \"TYPES_MARCHE\" FROM tr_rubrique_fiche_marche WHERE \"CODE\" = 'B04-CD'", String.class))
                .allMatch("QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE"::equals);
    }

    @Test
    @DisplayName("« grille-travaux » : rubrique sans champ servie, alerte SANS_EQUIVALENT_CATEGORIE par notion, mention « non saisi », filtre par catégorie ; V135")
    void grilleTravaux() throws Exception {
        champ("B09-VV-01", "QUANTITE_FIXE,A_COMMANDE", "FOURNITURES_SERVICES", true);
        champ("B09-VV-02", "QUANTITE_FIXE,A_COMMANDE", "TRAVAUX", true);
        // Une rubrique sans champ (comme le DQE, B12-DQ) : servie là où elle est ouverte.
        jdbcTemplate.update("INSERT INTO tr_rubrique_fiche_marche (\"CODE\", \"CODE_BLOC\", \"CODE_COURT\", \"LIBELLE\", \"RANG\", "
                + "\"TYPES_MARCHE\", \"CATEGORIES\") VALUES ('B09-UU', 'B09', 'UU', 'DQE d''essai', 99, 'QUANTITE_FIXE,A_COMMANDE', "
                + "'TRAVAUX') ON CONFLICT DO NOTHING");
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("DQE", "Quantités (B09-UU) ?", "AUTRE", null)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps("DQE", "Quantités (B09-UU) ?", "CONTRAT_CADRE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.ecarts[0].motif").value("HORS_FORME"));

        // Trois notions : la première ne mène à rien en travaux ; la deuxième l'assume en fournitures ; la troisième a ses deux codes.
        PointsCtrl p = point(97201, "Délais", "Remise (B09-VV-01) ; visite (B09-VV-02 ; fournitures : non saisi) ; "
                + "ouverture (B09-VV-01 ; travaux : B09-VV-02) ?", 5, "AUTRE", null);
        String bilan = mvc.perform(get("/api/points-ctrls/controle-codes").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(bilan, "$.points[*].idPointCtrl")).doesNotContain(p.getIdPointCtrl());
        assertThat(JsonPath.<List<List<String>>>read(bilan, "$.alertes[?(@.idPointCtrl==97201)].codes")).containsExactly(List.of("B09-VV-01"));
        assertThat(JsonPath.<List<String>>read(bilan, "$.alertes[?(@.idPointCtrl==97201)].categorie")).containsExactly("TRAVAUX");
        assertThat(JsonPath.<List<String>>read(bilan, "$.alertes[?(@.idPointCtrl==97201)].forme")).containsExactly("AUTRE");
        assertThat(JsonPath.<List<String>>read(bilan, "$.alertes[?(@.idPointCtrl==97201)].motif")).containsExactly("SANS_EQUIVALENT_CATEGORIE");
        String fournitures = mvc.perform(get("/api/points-ctrls/controle-codes").param("categorie", "FOURNITURES_SERVICES")
                .header("Authorization", tokenAdmin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(fournitures, "$.alertes[*].idPointCtrl")).doesNotContain(97201);
        mvc.perform(get("/api/points-ctrls/controle-codes").param("categorie", "AUTRE").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());

        // V135 : le texte de V133 est remplacé (deux passes), un texte modifié par l'Administrateur ne l'est pas.
        PointsCtrl garantie = point(97202, "Garantie de soumission", "Garantie exigée et forme de remise (B05-GS-01, B05-GS-10) conformes "
                + "au CMP ; montant (B05-GS-03 ; travaux : B05-GQ-03) entre 1 et 2 % du montant indicatif ; modèle joint (B04-CD-02) ; "
                + "validité des offres (B04-VO-01) ?", 4, "CONTRAT_CADRE", null);
        PointsCtrl modifie = point(97203, "Exécution et livraison", "Texte revu par l'Administrateur (B09-VV-01) ?", 10, "AUTRE", null);
        executerMigrationFlyway("V135__grille_travaux.sql");
        executerMigrationFlyway("V135__grille_travaux.sql");
        assertThat(points.findById(garantie.getIdPointCtrl()).orElseThrow().getDecriptPointCtrl())
                .contains("(B05-GS-01, B05-GS-10 ; travaux : non saisi)");
        assertThat(points.findById(modifie.getIdPointCtrl()).orElseThrow().getDecriptPointCtrl())
                .isEqualTo("Texte revu par l'Administrateur (B09-VV-01) ?");
        assertThat(jdbcTemplate.queryForObject("SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE table_name = 'tr_points_ctrl' AND column_name = 'DECRIPT_POINT_CTRL'", Integer.class)).isEqualTo(1000);
    }

    @Test
    @DisplayName("B1 : V133 crée la variante contrat-cadre, l'original passe en AUTRE ; la grille de DAOO-CC ne sert que la variante")
    void variantesContratCadre() throws Exception {
        PointsCtrl allotissement = point(97101, "Allotissement", "Lots (B02-AU-02) ; garantie (B09-DG-01, B09-DG-02) ?", 2, null, null);
        allotissement.setObligatoire(false);
        points.save(allotissement);
        PointsCtrl specifications = point(97102, "Spécifications techniques", "Articles (B12) ?", 11, null, null);
        PointsCtrl recours = point(97103, "Recours au contrat-cadre justifié", "Besoin répétitif ?", 46, "CONTRAT_CADRE", null);

        executerMigrationFlyway("V133__grille_contrat_cadre.sql");
        executerMigrationFlyway("V133__grille_contrat_cadre.sql");   // seconde passe : sans effet

        List<PointsCtrl> daoo = points.findAll().stream().filter(p -> "DAOO".equals(p.getIdSousType())).toList();
        List<PointsCtrl> variantes = daoo.stream().filter(p -> "Allotissement".equals(p.getLibelPointCtrl())
                && "CONTRAT_CADRE".equals(p.getForme())).toList();
        assertThat(variantes).hasSize(1);
        PointsCtrl variante = variantes.get(0);
        assertThat(variante.getDecriptPointCtrl()).contains("B02-AL-02").doesNotContain("B02-AU-02");
        assertThat(variante.getOrdrePointCtrl()).isEqualTo(2);
        assertThat(variante.getObligatoire()).isFalse();
        assertThat(variante.getPortee()).isEqualTo(PorteePointCtrl.DOSSIER);
        PointsCtrl original = points.findById(97101).orElseThrow();
        assertThat(original.getForme()).isEqualTo("AUTRE");
        assertThat(original.getDecriptPointCtrl()).contains("(B09-DG-01, B09-DG-04)");
        assertThat(points.findById(97102).orElseThrow().getForme()).isNull();
        assertThat(daoo).filteredOn(p -> "Spécifications techniques".equals(p.getLibelPointCtrl())).hasSize(1);

        String grilleCc = mvc.perform(get("/api/points-ctrls").param("sousType", "DAOO-CC").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(grilleCc, "$[*].idPointCtrl"))
                .contains(variante.getIdPointCtrl(), specifications.getIdPointCtrl(), recours.getIdPointCtrl())
                .doesNotContain(original.getIdPointCtrl());
        String grilleDaoo = mvc.perform(get("/api/points-ctrls").param("sousType", "DAOO").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // DAOO : conditions servies non résolues (la forme se lit sur la fiche du dossier).
        assertThat(JsonPath.<List<Integer>>read(grilleDaoo, "$[*].idPointCtrl"))
                .contains(variante.getIdPointCtrl(), original.getIdPointCtrl(), specifications.getIdPointCtrl());
    }
}
