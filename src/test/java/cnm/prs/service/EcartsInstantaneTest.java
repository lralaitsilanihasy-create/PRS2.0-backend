package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.EcartsInstantanesDto.Ecart;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** ⚠️ 2026-11-01 (« ecarts-ecrans-figes », §B1) — la comparaison d'un écran figé avec son état actuel. */
class EcartsInstantaneTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) {
        return mapper.readTree(s.replace('\'', '"'));
    }

    @Test
    @DisplayName("Éléments appariés par identité et nommés en clair, montant formaté ; champs volatils et champs apparus ignorés")
    void ecartEnClair() {
        JsonNode soumis = json("{'lots':[{'lot':1,'offres':[{'numero':1,'evaluation':{'montantEvalue':100}},"
                + "{'numero':2,'evaluation':{'montantEvalue':2939941765}}]}],'alertes':['X'],'peutSigner':true}");
        JsonNode actuel = json("{'lots':[{'lot':1,'offres':[{'numero':2,'evaluation':{'montantEvalue':2914400000}},"
                + "{'numero':1,'evaluation':{'montantEvalue':100}}]}],'alertes':['Y'],'peutSigner':false,'nouveau':'champ'}");
        EcartsInstantane.Resultat r = EcartsInstantane.comparer(soumis, actuel);
        assertThat(r.tronque()).isFalse();
        assertThat(r.ecarts()).hasSize(1);
        Ecart e = r.ecarts().get(0);
        assertThat(e.libelle()).isEqualTo("Lot 1 — Offre n° 2 — évaluation — montant évalué");
        assertThat(e.soumis()).isEqualTo("2 939 941 765 Ar");
        assertThat(e.actuel()).isEqualTo("2 914 400 000 Ar");
    }

    @Test
    @DisplayName("Ajout et retrait d'un élément, date jj/mm/aaaa, écran supprimé, plafond de 50 écarts")
    void ajoutsRetraitsEtPlafond() {
        EcartsInstantane.Resultat r = EcartsInstantane.comparer(json("{'membres':[{'idMembre':1,'nom':'RABE'}],'dateCreation':'2026-08-24'}"),
                json("{'membres':[{'idMembre':2,'nom':'RAKOTO'}],'dateCreation':'2026-08-25'}"));
        assertThat(r.ecarts()).extracting(Ecart::libelle).containsExactlyInAnyOrder("Membre 1", "Membre 2 (ajouté)", "date creation");
        assertThat(r.ecarts()).filteredOn(e -> e.libelle().equals("date creation")).extracting(Ecart::soumis).containsExactly("24/08/2026");
        assertThat(EcartsInstantane.comparer(json("{'a':1}"), null).ecarts()).extracting(Ecart::libelle)
                .containsExactly("Écran supprimé depuis la soumission");
        StringBuilder a = new StringBuilder("{");
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < 60; i++) {
            a.append(i == 0 ? "" : ",").append("'c").append(i).append("':").append(i);
            b.append(i == 0 ? "" : ",").append("'c").append(i).append("':").append(i + 1);
        }
        EcartsInstantane.Resultat beaucoup = EcartsInstantane.comparer(json(a + "}"), json(b + "}"));
        assertThat(beaucoup.ecarts()).hasSize(EcartsInstantane.PLAFOND);
        assertThat(beaucoup.tronque()).isTrue();
    }
}
