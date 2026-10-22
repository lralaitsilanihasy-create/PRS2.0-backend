package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-dao », D1-D3) — le départ de chacun des 15 documents intégrés du DAO : une page sans point
 * bloquant ni avertissement (le moteur accepte la commande reconstruite, tous les jetons sont connus, rien n'est « non rendu »),
 * conforme au document type, aucun écart.
 */
class DocumentsDaoDepartsIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    void lesQuinzeDepartsSontConformes() throws Exception {
        for (String sigle : new cnm.prs.service.ModelesDao().modeles().keySet()) {
            String cree = mvc.perform(post("/api/modeles-pv").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"DEPART-" + sigle + "\",\"libelle\":\"" + sigle + "\",\"nature\":\"DOCUMENT_DAO\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Object id = JsonPath.read(cree, "$.id");
            mvc.perform(put("/api/modeles-pv/" + id + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sigles\":[\"" + sigle + "\"]}")).andExpect(status().isOk());
            String v = mvc.perform(post("/api/modeles-pv/" + id + "/versions/depart").header("Authorization", tokenAdmin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<List<Object>>read(v, "$.rapport.points[?(@.niveau!='OK')]")).as(sigle).isEmpty();
            assertThat(JsonPath.<List<String>>read(v, "$.rapport.points[*].code")).as(sigle).contains("CONFORME_ARMP", "ESSAI");
            String ecarts = mvc.perform(get("/api/modeles-pv/" + id + "/versions/1/ecarts").header("Authorization", tokenAdmin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<List<Object>>read(ecarts, "$")).as(sigle).isEmpty();
        }
    }
}
