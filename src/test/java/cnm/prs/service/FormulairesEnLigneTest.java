package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.BesoinEnLigneDto;
import cnm.prs.dto.MaterielExigeDto;
import cnm.prs.dto.PersonnelExigeDto;
import cnm.prs.dto.SeanceDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** ⚠️ 2026-10-05 (lot 5b, §B3.2) — les contrôles des capacités, du personnel, du matériel et du sous-détail : des alertes, jamais un refus. */
class FormulairesEnLigneTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int AN = Year.now().getValue();

    private static JsonNode lire(String json) {
        return JSON.readTree(json);
    }

    private static List<String> types(List<SeanceDto.Alerte> a) {
        return a.stream().map(SeanceDto.Alerte::type).toList();
    }

    @Test
    @DisplayName("Capacités : CA moyen des meilleures années de la période, liquidité (le plus exigeant du montant et du % du TTC), références cumulées")
    void capacites() {
        BesoinEnLigneDto.Qualification q = new BesoinEnLigneDto.Qualification(new BigDecimal("100"), new BigDecimal("10"),
                new BesoinEnLigneDto.ChiffreAffaires(new BigDecimal("1000"), 3, 2, "travaux"),
                new BesoinEnLigneDto.References(new BigDecimal("500"), 2, 5, true));
        String ok = "{\"capacites\":{\"chiffresAffaires\":[{\"annee\":" + (AN - 1) + ",\"montant\":1200},{\"annee\":" + (AN - 2)
                + ",\"montant\":900},{\"annee\":" + (AN - 3) + ",\"montant\":100},{\"annee\":" + (AN - 9) + ",\"montant\":99999}],"
                + "\"liquidite\":{\"montant\":300},\"references\":[{\"annee\":" + (AN - 1) + ",\"montant\":300},{\"annee\":" + (AN - 2)
                + ",\"montant\":250},{\"annee\":" + (AN - 20) + ",\"montant\":9999}]}}";
        List<SeanceDto.Alerte> a = new ArrayList<>();
        FormulairesEnLigne.capacites(lire(ok), q, new BigDecimal("2000"), a);   // CA (1200+900)/2 = 1050 ; liquidité ≥ max(100, 200) ; réf. 550
        assertThat(a).isEmpty();
        String faible = "{\"capacites\":{\"chiffresAffaires\":[{\"annee\":" + (AN - 1) + ",\"montant\":800},{\"annee\":" + (AN - 9)
                + ",\"montant\":99999}],\"liquidite\":{\"montant\":150},\"references\":[{\"annee\":" + (AN - 1) + ",\"montant\":300}]}}";
        FormulairesEnLigne.capacites(lire(faible), q, new BigDecimal("2000"), a);
        assertThat(types(a)).containsExactly("CA_INSUFFISANT", "LIQUIDITE_INSUFFISANTE", "REFERENCES_INSUFFISANTES");
        a.clear();
        FormulairesEnLigne.capacites(lire("{\"bordereau\":[]}"), q, new BigDecimal("2000"), a);   // lot 5a : rien à contrôler
        assertThat(a).isEmpty();
    }

    @Test
    @DisplayName("Personnel (nombre et expérience) et matériel (nombre et en propre) ; sous-détail à plus de 1 % du bordereau")
    void moyensEtSousDetail() {
        BesoinEnLigneDto b = new BesoinEnLigneDto(1L, "TRAVAUX", "QUANTITE_FIXE", true, null, "MGA", List.of(),
                List.of(new MaterielExigeDto(17, 1, "Niveleuse", null, 2, 1, true)),
                List.of(new PersonnelExigeDto(41, 1, "Directeur des travaux", 1, "Ingénieur", 10, null, null, true)));
        List<SeanceDto.Alerte> a = new ArrayList<>();
        FormulairesEnLigne.moyens(lire("{\"personnel\":[{\"idPersonnel\":41,\"experienceAnnees\":12}],"
                + "\"materiel\":[{\"idMateriel\":17,\"nombre\":2,\"enPropre\":1}]}"), b, a);
        assertThat(a).isEmpty();
        FormulairesEnLigne.moyens(lire("{\"personnel\":[{\"idPersonnel\":41,\"experienceAnnees\":4}],"
                + "\"materiel\":[{\"idMateriel\":17,\"nombre\":2,\"enPropre\":0}]}"), b, a);
        assertThat(types(a)).containsExactly("PERSONNEL_INCOMPLET", "MATERIEL_INCOMPLET");
        a.clear();
        BesoinEnLigneDto.Lot lot = new BesoinEnLigneDto.Lot(null, null, List.of(new BesoinEnLigneDto.Article(901, 1, "Déblais", "m3",
                BigDecimal.TEN, null, null, List.of(), "201", "200", "Terrassements", null, true, null)), null, null, null, null);
        FormulairesEnLigne.sousDetails(lire("{\"sousDetails\":[{\"idArticle\":901,\"prixCalcule\":10050}]}"),
                java.util.Map.of(901, new BigDecimal("10000")), lot, a);
        assertThat(a).isEmpty();   // 0,5 %
        FormulairesEnLigne.sousDetails(lire("{\"sousDetails\":[{\"idArticle\":901,\"prixCalcule\":10200}]}"),
                java.util.Map.of(901, new BigDecimal("10000")), lot, a);
        assertThat(types(a)).containsExactly("SOUS_DETAIL_INCOHERENT");
        assertThat(a.get(0).message()).contains("prix n° 201", "Déblais");
    }
}
