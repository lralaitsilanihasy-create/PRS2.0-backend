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
        assertThat(e.libelle()).isEqualTo("Lot 1 — Offre n° 2 — Évaluation — Montant évalué");
        assertThat(e.soumis()).isEqualTo("2 939 941 765 Ar");
        assertThat(e.actuel()).isEqualTo("2 914 400 000 Ar");
    }

    @Test
    @DisplayName("⚠️ « ecarts-lisibles » — B1 codes en libellés (états, avis, nature par identifiant), B2 noms de champ en français, "
            + "B3 objets résumés en une phrase, sans identifiant technique")
    void ecartsLisibles() {
        EcartsInstantane.Dictionnaire dico = new EcartsInstantane.Dictionnaire(
                new java.util.HashMap<>(java.util.Map.of("AU_CONTROLE", "Au contrôle", "NOTIFIE", "Notifié", "FAV", "Favorable")),
                java.util.Map.of(3, "Fournitures"), java.util.Map.of());
        JsonNode soumis = json("{'lots':[{'lot':1,'etat':'AU_CONTROLE','dossierMarche':{'avis':null},'idNature':2,'attributaire':null,"
                + "'information':null,'delaiAttente':null,'titulaires':[]}]}");
        JsonNode actuel = json("{'lots':[{'lot':1,'etat':'NOTIFIE','dossierMarche':{'avis':'FAV'},'idNature':3,"
                + "'attributaire':{'idOffre':'1baae434-x','numero':1,'candidat':'SAVA Routes et Ouvrages SA','montant':1127980000},"
                + "'information':{'le':'2026-08-31T10:00:00','par':'IMP001'},'delaiAttente':{'debut':'2026-09-03','fin':'2026-09-13'},"
                + "'titulaires':[{'idOffre':'1baae434-x','candidat':'SAVA Routes et Ouvrages SA','montant':1127980000,'rang':1}]}]}");
        java.util.Map<String, Ecart> parLibelle = new java.util.HashMap<>();
        EcartsInstantane.comparer(soumis, actuel, dico).ecarts().forEach(e -> parLibelle.put(e.libelle(), e));
        assertThat(parLibelle.get("Lot 1 — État").soumis()).isEqualTo("Au contrôle");
        assertThat(parLibelle.get("Lot 1 — État").actuel()).isEqualTo("Notifié");
        assertThat(parLibelle.get("Lot 1 — Dossier de marché — Avis").actuel()).isEqualTo("Favorable");
        assertThat(parLibelle.get("Lot 1 — Nature").actuel()).isEqualTo("Fournitures");
        assertThat(parLibelle.get("Lot 1 — Nature").soumis()).isEqualTo("2");   // identifiant sans libellé connu : tel quel
        assertThat(parLibelle.get("Lot 1 — Attributaire").actuel()).isEqualTo("SAVA Routes et Ouvrages SA — offre n° 1 — 1 127 980 000 Ar");
        assertThat(parLibelle.get("Lot 1 — Information des soumissionnaires").actuel()).isEqualTo("le 31/08/2026 par IMP001");
        assertThat(parLibelle.get("Lot 1 — Délai d'attente").actuel()).isEqualTo("du 03/09/2026 au 13/09/2026 (10 jours)");
        assertThat(parLibelle.get("Lot 1 — Titulaire 1 (ajouté)").actuel()).isEqualTo("SAVA Routes et Ouvrages SA — 1 127 980 000 Ar");
        // À défaut de règle : le texte compact, sans les champs id….
        Ecart autre = EcartsInstantane.comparer(json("{'x':null}"), json("{'x':{'idTruc':'abc','libre':'oui','idOffre':'u'}}")).ecarts().get(0);
        assertThat(autre.actuel()).isEqualTo("libre : oui").doesNotContain("abc");
    }

    @Test
    @DisplayName("Ajout et retrait d'un élément, date jj/mm/aaaa, écran supprimé, plafond de 50 écarts")
    void ajoutsRetraitsEtPlafond() {
        EcartsInstantane.Resultat r = EcartsInstantane.comparer(json("{'membres':[{'idMembre':1,'nom':'RABE'}],'dateCreation':'2026-08-24'}"),
                json("{'membres':[{'idMembre':2,'nom':'RAKOTO'}],'dateCreation':'2026-08-25'}"));
        assertThat(r.ecarts()).extracting(Ecart::libelle).containsExactlyInAnyOrder("Membre RABE", "Membre RAKOTO (ajouté)", "Date de création");
        assertThat(r.ecarts()).filteredOn(e -> e.libelle().equals("Date de création")).extracting(Ecart::soumis).containsExactly("24/08/2026");
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
