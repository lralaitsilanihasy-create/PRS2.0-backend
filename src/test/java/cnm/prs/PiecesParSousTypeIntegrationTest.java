package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.PieceSousType;

/**
 * ⚠️ 2026-10-08 (manuel de contrôle a priori, tranche M2, §B2 ; V95) — les pièces exigées par sous-type : la liste propre du sous-type
 * remplace celle de la famille ; une pièce conditionnée (catégorie, forme) est servie sans obligation quand le dossier n'a pas de fiche ;
 * la soumission exige les pièces obligatoires de la liste.
 */
class PiecesParSousTypeIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private cnm.prs.repository.PieceSousTypeRepository associations;

    @Test
    @DisplayName("Liste du sous-type DAOO : obligation résolue sans fiche, servie par ?sousType= et par dossier, exigée à la soumission")
    void listeDuSousType() throws Exception {
        int fiche = seedTypePiece("Fiche de présentation datée et signée", false, null, 0);
        int bordereau = seedTypePiece("Bordereau des prix estimatifs signé", false, null, 0);
        int dqe = seedTypePiece("Devis quantitatif et estimatif signé", false, null, 0);
        int canevas = seedTypePiece("Canevas de rapport d'évaluation", false, null, 0);
        seedTypePiece("Pièce de la famille DMC, écartée par la liste du sous-type", true, "DMC", 1);
        associations.save(new PieceSousType(fiche, "DAOO", true, 1, null, null));
        associations.save(new PieceSousType(canevas, "DAOO", true, 2, null, "AUTRE"));
        associations.save(new PieceSousType(bordereau, "DAOO", true, 3, "FOURNITURES_SERVICES", "AUTRE"));
        associations.save(new PieceSousType(dqe, "DAOO", true, 3, "TRAVAUX", "AUTRE"));

        String ref = mvc.perform(get("/api/type-piece-jointes").param("sousType", "DAOO").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(ref, "$[*].idTypePiece")).containsExactly(fiche, canevas, bordereau, dqe);
        assertThat(JsonPath.<List<String>>read(ref, "$[?(@.idTypePiece==" + dqe + ")].categorie")).containsExactly("TRAVAUX");

        String cree = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOO\",\"idEntiteContract\":1}")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        int id = JsonPath.read(cree, "$.idDossier");
        String exigees = mvc.perform(get("/api/dossiers/" + id + "/pieces-exigees").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // Sans fiche : la fiche de présentation reste exigée ; les pièces conditionnées sont servies sans obligation.
        assertThat(JsonPath.<List<Integer>>read(exigees, "$[?(@.obligatoire==true)].idTypePiece")).containsExactly(fiche);
        assertThat(JsonPath.<List<Integer>>read(exigees, "$[?(@.obligatoire==false)].idTypePiece")).containsExactly(canevas, bordereau, dqe);
        mvc.perform(post("/api/dossiers/" + id + "/soumettre").header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[*].message").value(org.hamcrest.Matchers.hasItem(
                        "La pièce 'Fiche de présentation datée et signée' est obligatoire.")))
                .andExpect(jsonPath("$.erreurs[*].message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                        "La pièce 'Pièce de la famille DMC, écartée par la liste du sous-type' est obligatoire."))));
        // Un sous-type sans liste propre garde les pièces de sa famille.
        String famille = mvc.perform(get("/api/type-piece-jointes").param("sousType", "DAOR").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(famille, "$[*].libellePiece")).contains("Pièce de la famille DMC, écartée par la liste du sous-type");
    }

    @Test
    @DisplayName("⚠️ R2 (manuel révisé, V102) — condition de ligne : hors ligne tout est exigé ; en ligne (dossier né d'une fiche) la pièce "
            + "PAPIER disparaît et la pièce FACULTATIVE_EN_LIGNE perd son obligation")
    void conditionProcedureEnLigne() throws Exception {
        int toujours = seedTypePiece("Fiche de présentation", false, null, 0);
        int papier = seedTypePiece("PV de la Commission sur le DAO", false, null, 0);
        int sigmp = seedTypePiece("Projet d'avis spécifique (imprimé du SIGMP)", false, null, 0);
        associations.save(new PieceSousType(toujours, "DAOO", true, 1, null, null));
        PieceSousType p = new PieceSousType(papier, "DAOO", true, 2, null, null);
        p.setCondition(PieceSousType.PAPIER);
        associations.save(p);
        PieceSousType s = new PieceSousType(sigmp, "DAOO", true, 3, null, null);
        s.setCondition(PieceSousType.FACULTATIVE_EN_LIGNE);
        associations.save(s);

        // Le référentiel sert la condition, non résolue.
        String ref = mvc.perform(get("/api/type-piece-jointes").param("sousType", "DAOO").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(ref, "$[?(@.idTypePiece==" + papier + ")].condition")).containsExactly("PAPIER");
        assertThat(JsonPath.<List<String>>read(ref, "$[?(@.idTypePiece==" + sigmp + ")].condition")).containsExactly("FACULTATIVE_EN_LIGNE");

        // Hors ligne (dossier saisi, sans fiche) : les trois pièces, toutes obligatoires.
        String cree = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOO\",\"idEntiteContract\":1}")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        int id = JsonPath.read(cree, "$.idDossier");
        String horsLigne = mvc.perform(get("/api/dossiers/" + id + "/pieces-exigees").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(horsLigne, "$[?(@.obligatoire==true)].idTypePiece")).containsExactly(toujours, papier, sigmp);

        // En ligne : le même dossier, rattaché à une fiche (ID_DMC).
        dossierRepository.save(dossierLoc(97500, "CLOTURE", "ANT", "PRMP001"));
        ppmRepository.save(ppm(97500, 97500, "PRMP001"));
        cnm.prs.entity.Marche ligne = marche(97501, 97500, 97500);
        ligne.setIdNature(natureFournitures());
        marcheRepository.save(ligne);
        cnm.prs.entity.DossierMec dmc = new cnm.prs.entity.DossierMec();
        dmc.setIdDetail(97501);
        dmc.setIdTypeDmc(typeDmcRepository.findByCode("DAO").orElseThrow().getIdTypeDmc());
        dmc.setDateCreation(java.time.LocalDateTime.now());
        dmc = dossierMecRepository.save(dmc);
        cnm.prs.entity.Dossier d = dossierRepository.findById(id).orElseThrow();
        d.setIdDmc(dmc.getIdDmc());
        dossierRepository.save(d);
        String enLigne = mvc.perform(get("/api/dossiers/" + id + "/pieces-exigees").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(enLigne, "$[*].idTypePiece")).containsExactly(toujours, sigmp);
        assertThat(JsonPath.<List<Integer>>read(enLigne, "$[?(@.obligatoire==true)].idTypePiece")).containsExactly(toujours);
        // À la soumission, seule la fiche de présentation manque : ni le PV « papier », ni l'imprimé facultatif.
        mvc.perform(post("/api/dossiers/" + id + "/soumettre").header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[*].message").value(org.hamcrest.Matchers.hasItem("La pièce 'Fiche de présentation' est obligatoire.")))
                .andExpect(jsonPath("$.erreurs[*].message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                        "La pièce 'PV de la Commission sur le DAO' est obligatoire."))))
                .andExpect(jsonPath("$.erreurs[*].message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                        "La pièce 'Projet d'avis spécifique (imprimé du SIGMP)' est obligatoire."))));
    }
}
