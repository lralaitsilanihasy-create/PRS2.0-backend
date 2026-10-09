package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.SousTypeDossier;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M4, §B4 ; V97) — les motifs-types de renvoi et d'avis défavorable : référentiel
 * administrable (écriture Admin seule, contrôles de cohérence), héritage des grilles (DAOR ← DAOO, grille propre sans les communs),
 * motifs d'un dossier (conditionnés servis sans fiche, inactifs écartés), seeder du manuel idempotent.
 */
class MotifsTypesIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private cnm.prs.repository.MotifTypeRepository motifs;
    @Autowired private cnm.prs.seed.MotifsTypesManuelSeeder seeder;

    private int creer(String famille, String sousType, String nature, String libelle, Integer ordre, String categorie) throws Exception {
        String corps = "{\"idTypeDossier\":\"" + famille + "\"," + (sousType == null ? "" : "\"idSousType\":\"" + sousType + "\",")
                + "\"nature\":\"" + nature + "\",\"libelle\":\"" + libelle + "\",\"texte\":\"Texte : " + libelle + "\","
                + "\"ordre\":" + ordre + (categorie == null ? "" : ",\"categorie\":\"" + categorie + "\"") + "}";
        String r = mvc.perform(post("/api/motifs-types").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.idMotif");
    }

    @Test
    @DisplayName("Référentiel administrable, héritage DAOR ← DAOO, grille propre, motifs d'un dossier sans fiche, inactif écarté")
    void motifs() throws Exception {
        motifs.deleteAll();
        SousTypeDossier daor = sousTypeDossierRepository.findById("DAOR").orElseThrow();
        daor.setGrilleBase("DAOO");
        sousTypeDossierRepository.save(daor);
        SousTypeDossier dc = sousTypeDossierRepository.findById("DC").orElseThrow();
        dc.setGrillePropre(true);
        sousTypeDossierRepository.save(dc);

        int commun = creer("DMC", null, "RENVOI", "Commun DMC", 1, null);
        int objet = creer("DMC", "DAOO", "RENVOI", "Objet incohérent", 2, null);
        int travaux = creer("DMC", "DAOO", "RENVOI", "Spécifications travaux", 3, "TRAVAUX");
        int defav = creer("DMC", "DAOR", "AVIS_DEFAVORABLE", "Motif de l'AO restreint", 4, null);
        int propreDc = creer("DMC", "DC", "RENVOI", "TDR incomplets", 5, null);

        // Écriture réservée à l'Administrateur ; contrôles de cohérence.
        mvc.perform(post("/api/motifs-types").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DMC\",\"nature\":\"RENVOI\",\"libelle\":\"x\",\"texte\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/motifs-types").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DMC\",\"nature\":\"AJOURNEMENT\",\"libelle\":\"x\",\"texte\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/motifs-types").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DDP\",\"idSousType\":\"DAOO\",\"nature\":\"RENVOI\",\"libelle\":\"x\",\"texte\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/motifs-types").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DMC\",\"nature\":\"RENVOI\",\"libelle\":\"x\",\"texte\":\"x\",\"categorie\":\"BTP\"}"))
                .andExpect(status().isBadRequest());

        // Héritage : DAOR = communs + DAOO + les siens ; ?nature= filtre ; DC (grille propre) sans les communs.
        String daorMotifs = mvc.perform(get("/api/motifs-types").param("sousType", "DAOR").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(daorMotifs, "$[*].idMotif")).containsExactly(commun, objet, travaux, defav);
        mvc.perform(get("/api/motifs-types").param("sousType", "DAOR").param("nature", "AVIS_DEFAVORABLE")
                .header("Authorization", tokenMembre)).andExpect(status().isOk()).andExpect(jsonPath("$[*].idMotif").value(defav));
        String dcMotifs = mvc.perform(get("/api/motifs-types").param("sousType", "DC").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(dcMotifs, "$[*].idMotif")).containsExactly(propreDc);
        mvc.perform(get("/api/motifs-types").param("nature", "X").header("Authorization", tokenMembre)).andExpect(status().isBadRequest());

        // Désactivé : gardé à l'administration (famille), écarté des listes servies à l'examen.
        mvc.perform(put("/api/motifs-types/" + objet).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DMC\",\"idSousType\":\"DAOO\",\"nature\":\"RENVOI\",\"libelle\":\"Objet incohérent\","
                        + "\"texte\":\"Texte modifié\",\"ordre\":2,\"actif\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false)).andExpect(jsonPath("$.texte").value("Texte modifié"));
        String famille = mvc.perform(get("/api/motifs-types").param("typeDossier", "DMC").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(famille, "$[*].idMotif")).contains(objet);

        // Motifs d'un dossier DAOR sans fiche : le motif conditionné par la catégorie reste servi ; l'inactif est écarté.
        String cree = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOR\",\"idEntiteContract\":1}")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        int id = JsonPath.read(cree, "$.idDossier");
        String duDossier = mvc.perform(get("/api/dossiers/" + id + "/motifs-types").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(duDossier, "$[*].idMotif")).containsExactly(commun, travaux, defav);
        mvc.perform(get("/api/dossiers/" + id + "/motifs-types").param("nature", "RENVOI").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        mvc.perform(delete("/api/motifs-types/" + propreDc).header("Authorization", tokenAdmin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/motifs-types/" + propreDc).header("Authorization", tokenAdmin)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Seeder du manuel : motifs des familles et sous-types présents, une seule fois, jamais réécrits")
    void seeder() throws Exception {
        motifs.deleteAll();
        int crees = seeder.semer();
        assertThat(crees).isGreaterThan(10);
        assertThat(seeder.semer()).isZero();
        assertThat(motifs.findAll()).filteredOn(m -> "DAOO".equals(m.getIdSousType())).hasSize(6);
        assertThat(motifs.findAll()).filteredOn(m -> "DAOO".equals(m.getIdSousType()) && "TRAVAUX".equals(m.getCategorie())).hasSize(2);
        // Les actes de gestion (famille DGC semée par migration) : communs et propres.
        assertThat(motifs.findAll()).filteredOn(m -> "DGC".equals(m.getIdTypeDossier()) && m.getIdSousType() == null).hasSize(3);
        String penal = mvc.perform(get("/api/motifs-types").param("sousType", "PENAL").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(penal, "$[*].libelle")).hasSize(4).contains("Calcul des pénalités non justifié");
        String avn = mvc.perform(get("/api/motifs-types").param("sousType", "AVN").param("nature", "AVIS_DEFAVORABLE")
                .header("Authorization", tokenMembre)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // AVN a sa grille propre (V96) : sans les motifs communs des actes de gestion.
        assertThat(JsonPath.<List<String>>read(avn, "$[*].idSousType")).hasSize(5).containsOnly("AVN");

        // Un motif modifié par l'Administrateur n'est pas réécrit, ni recréé.
        var objet = motifs.findAll().stream().filter(m -> "DAOO".equals(m.getIdSousType())).findFirst().orElseThrow();
        objet.setTexte("Texte de l'Administrateur");
        objet.setActif(false);
        motifs.save(objet);
        assertThat(seeder.semer()).isZero();
        assertThat(motifs.findById(objet.getIdMotif()).orElseThrow().getTexte()).isEqualTo("Texte de l'Administrateur");
    }
}
