package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.SeanceDto;

/** ⚠️ 2026-10-07 (rabais structuré au dépôt, §B1-§B2) — la lecture du rabais (formats 4 et antérieurs), sa phrase, ses contrôles. */
class RabaisOffreTest {

    @Test
    @DisplayName("Format 4 : pourcentage chiffré sur le HT lu, montant, condition de lots ; texte libre des formats 2 et 3 lu tel quel")
    void lire() {
        SeanceDto.RabaisLu p = RabaisOffre.lire(ae("12500000", Map.of("nature", "POURCENTAGE", "valeur", 2, "condition", "AUCUNE")));
        assertThat(p.montant()).isEqualByComparingTo("250000");
        assertThat(p.lecture()).isEqualTo("2 % du montant hors taxes, soit 250 000 Ariary");
        SeanceDto.RabaisLu m = RabaisOffre.lire(ae("12500000", Map.of("nature", "MONTANT", "valeur", "300000")));
        assertThat(m.condition()).isEqualTo("AUCUNE");
        assertThat(m.montant()).isEqualByComparingTo("300000");
        assertThat(m.lecture()).isEqualTo("300 000 Ariary hors taxes");
        SeanceDto.RabaisLu l = RabaisOffre.lire(ae("12500000", Map.of("nature", "POURCENTAGE", "valeur", 2.5, "condition", "LOTS",
                "lots", List.of(1, 2), "libelle", "2,5 % si les deux lots")));
        assertThat(l.montant()).isNull();
        assertThat(l.lots()).containsExactly(1, 2);
        assertThat(l.libelle()).isEqualTo("2,5 % si les deux lots");
        assertThat(l.lecture()).isEqualTo("2,5 % du montant hors taxes, si les lots 1, 2 sont attribués au candidat");
        SeanceDto.RabaisLu t = RabaisOffre.lire(ae("12500000", "2 % en cas d'attribution des deux lots"));
        assertThat(t.nature()).isNull();
        assertThat(t.lecture()).isEqualTo("2 % en cas d'attribution des deux lots");
        assertThat(RabaisOffre.lire(ae("12500000", null))).isNull();
        assertThat(RabaisOffre.lire(ae("12500000", " "))).isNull();
    }

    @Test
    @DisplayName("Contrôles en alertes : RABAIS_INVALIDE (valeur, pourcentage ≥ 100, montant > HT), RABAIS_LOTS ; rien pour un texte libre")
    void controler() {
        BigDecimal ht = new BigDecimal("1000");
        assertThat(types(RabaisOffre.lire(ae("1000", Map.of("nature", "POURCENTAGE", "valeur", 100))), ht, 1, 2)).containsExactly("RABAIS_INVALIDE");
        assertThat(types(RabaisOffre.lire(ae("1000", Map.of("nature", "MONTANT", "valeur", 1500))), ht, 1, 2)).containsExactly("RABAIS_INVALIDE");
        assertThat(types(RabaisOffre.lire(ae("1000", Map.of("nature", "AUTRE", "valeur", 5))), ht, 1, 2)).containsExactly("RABAIS_INVALIDE");
        assertThat(types(RabaisOffre.lire(ae("1000", Map.of("nature", "MONTANT", "valeur", 0))), ht, 1, 2)).containsExactly("RABAIS_INVALIDE");
        assertThat(types(RabaisOffre.lire(ae("1000", Map.of("nature", "POURCENTAGE", "valeur", 2, "condition", "LOTS", "lots", List.of(1)))),
                ht, 1, 2)).containsExactly("RABAIS_LOTS");
        assertThat(types(RabaisOffre.lire(ae("1000", Map.of("nature", "POURCENTAGE", "valeur", 2, "condition", "LOTS", "lots", List.of(2, 3)))),
                ht, 1, 3)).containsExactly("RABAIS_LOTS");   // sans le lot de l'offre
        assertThat(types(RabaisOffre.lire(ae("1000", Map.of("nature", "POURCENTAGE", "valeur", 2, "condition", "LOTS", "lots", List.of(1, 4)))),
                ht, 1, 3)).containsExactly("RABAIS_LOTS");   // lot inconnu
        assertThat(types(RabaisOffre.lire(ae("1000", Map.of("nature", "POURCENTAGE", "valeur", 2, "condition", "LOTS", "lots", List.of(1, 2)))),
                ht, 1, 2)).isEmpty();
        assertThat(types(RabaisOffre.lire(ae("1000", "dix pour cent")), ht, 1, 1)).isEmpty();
        assertThat(types(null, ht, 1, 1)).isEmpty();
    }

    private static List<String> types(SeanceDto.RabaisLu r, BigDecimal ht, Integer lot, int nbLots) {
        return RabaisOffre.controler(r, ht, lot, nbLots).stream().map(SeanceDto.Alerte::type).toList();
    }

    private static Map<String, Object> ae(String ht, Object rabais) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("montantHt", ht);
        m.put("rabais", rabais);
        return m;
    }
}
