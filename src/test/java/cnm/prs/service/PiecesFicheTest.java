package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.PieceExigeeDto;
import cnm.prs.exception.ChampsInvalidesException;

/**
 * ⚠️ V61 (demande front du 2026-10-03, pièces de l'offre des travaux, §B1 et §B2) — la validation de la liste et le texte
 * des jetons {@code {{PIECES.administratives}}} / {@code {{PIECES.offre}}}, sur les exemples du MTP et du MEN, et leur
 * lecture par une condition.
 */
class PiecesFicheTest {

    private static PieceExigeeDto piece(String rubrique, String numero, String libelle, String forme, Integer mois,
            boolean parLot, String modele) {
        return new PieceExigeeDto(null, null, rubrique, numero, libelle, forme, mois, parLot, modele);
    }

    @Test
    @DisplayName("Jetons : une ligne par pièce de la rubrique, numéro en tête, morceaux absents omis avec leur virgule")
    void jetons() {
        Map<String, String> j = PiecesFiche.jetons(List.of(
                piece("ADMINISTRATIVE", "01", "Carte professionnelle 2026", "copie légalisée par le centre fiscal", 3, false, null),
                piece("OFFRE", "06", "Garantie de soumission", "original", null, true, null),
                piece("ADMINISTRATIVE", null, "Extrait du Registre de Commerce", "photocopie certifiée", null, false, null),
                piece("OFFRE", "09", "Planning général", null, null, false, "annexe 5, planning 8-a"),
                piece("ADMINISTRATIVE", null, "Certificat de non-faillite", null, 1, false, null)));
        assertThat(j.get("PIECES.administratives")).isEqualTo("""
                - 01 : Carte professionnelle 2026, copie légalisée par le centre fiscal, datée de moins de 3 mois
                - Extrait du Registre de Commerce, photocopie certifiée
                - Certificat de non-faillite, datée de moins d'un mois""");
        assertThat(j.get("PIECES.offre")).isEqualTo("""
                - 06 : Garantie de soumission, original, une par lot
                - 09 : Planning général, selon le modèle : annexe 5, planning 8-a""");
        assertThat(PiecesFiche.jetons(List.of(piece("OFFRE", null, "Quittance", null, null, false, null))))
                .containsOnlyKeys("PIECES.offre");
    }

    @Test
    @DisplayName("400 nominatifs : rubrique absente ou inconnue, libellé manquant, ancienneté < 1, numéro trop long")
    void validation() {
        assertThatThrownBy(() -> PiecesFiche.valider(List.of(piece(null, null, "X", null, null, false, null),
                piece("AUTRE", "12345678901", " ", null, 0, false, null))))
                .isInstanceOf(ChampsInvalidesException.class)
                .satisfies(e -> assertThat(((ChampsInvalidesException) e).getErreurs()).extracting(x -> x.champ())
                        .containsExactly("pieces[0].rubrique", "pieces[1].rubrique", "pieces[1].numero", "pieces[1].libelle",
                                "pieces[1].ancienneteMaxMois"));
        PiecesFiche.valider(List.of(piece("administrative", "8-a", "Planning", null, 3, false, null)));   // rubrique sans casse
    }

    @Test
    @DisplayName("Conditions : PIECES.offre / PIECES.administratives renseigne, comme MOYENS.x")
    void conditions() {
        String us = "\u001F";
        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "CONDITION\tPIECES-OFFRE" + us + "PIECES.offre renseigne",
                "CONDITION\tPIECES-ADMIN" + us + "PIECES.administratives renseigne",
                "PARA\t{{SI:PIECES-OFFRE}}", "PARA\t1° {{PIECES.offre}}", "PARA\t{{FINSI:PIECES-OFFRE}}",
                "PARA\t{{SI:PIECES-ADMIN}}", "PARA\t2° {{PIECES.administratives}}", "PARA\t{{FINSI:PIECES-ADMIN}}", ""));
        cnm.prs.dto.FicheMarcheDto f = new cnm.prs.dto.FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("QUANTITE_FIXE");
        f.setCategorie("TRAVAUX");
        f.setCadrage(new java.util.LinkedHashMap<>());
        f.setValeurs(new java.util.HashMap<>());
        f.setValeursPpm(new java.util.HashMap<>());
        String rendu = FormulairesCandidat.rendreModele("DPAO", null, f, Map.of(), modele, null,
                PiecesFiche.jetons(List.of(piece("OFFRE", "05", "Quittance de l'ARMP", null, null, false, null)))).texte();
        assertThat(rendu).contains("1° - 05 : Quittance de l'ARMP").doesNotContain("2°");
    }
}
