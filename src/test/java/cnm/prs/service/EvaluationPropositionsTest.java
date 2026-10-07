package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.dto.OffreDto;
import cnm.prs.dto.SeanceDto;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, §B2) — la grille de l'examen préliminaire pré-remplie depuis la lecture de la séance : des
 * constats (P2), « non satisfait » pour une offre altérée ou sans frais réglés (arbitrage du 07/10, Q6, Q7), nul quand c'est sans objet.
 */
class EvaluationPropositionsTest {

    @Test
    @DisplayName("Alertes de séance → vérifications non satisfaites, avec leur message ; sans objet → nul")
    void alertes() {
        SeanceDto.OffreLue o = new SeanceDto.OffreLue(1, "o1", null, "DEPOSEE", "LECTURE_IMPOSSIBLE", "conteneur illisible",
                new SeanceDto.EntrepriseLue("111", "Alpha", null, new EntrepriseCandidatDto.Exclusion(1, "111", "Alpha", "Fraude", "ARMP-77", null, null, true, List.of())),
                null, Map.of(), new SeanceDto.Garantie("G1", true, new BigDecimal("100"), "MGA", null), List.of(),
                List.of("Pouvoir du signataire", "Quittance"), List.of(new SeanceDto.Alerte("GARANTIE_INSUFFISANTE", "Garantie de 100 pour 200."),
                        new SeanceDto.Alerte("NON_CONFORME", "Article 2 non conforme."), new SeanceDto.Alerte("EXCLUSION", "Exclusion en cours.")),
                true, null, null, new SeanceDto.FraisDossier(false, null, null), null);
        Map<String, EvaluationService.Proposition> p = EvaluationService.proposer(1L, o, List.of(o), Map.of("o1", Set.of("111")),
                List.of(new OffreDto.PieceAttendue("POUVOIR", "ADMINISTRATIVE", null, "Pouvoir du signataire", null, null, false, null, true, null, null)), true);
        assertThat(p.get("AE_PRIX").valeur()).isFalse();
        assertThat(p.get("GARANTIE")).extracting(EvaluationService.Proposition::valeur, EvaluationService.Proposition::constat)
                .containsExactly(false, "Garantie de 100 pour 200.");
        assertThat(p.get("EXCLUSION").valeur()).isFalse();
        assertThat(p.get("POUVOIRS").valeur()).isFalse();
        assertThat(p.get("PIECES").constat()).contains("Pouvoir du signataire", "Quittance");
        assertThat(p.get("CONFORMITE_TECHNIQUE")).extracting(EvaluationService.Proposition::valeur, EvaluationService.Proposition::constat)
                .containsExactly(false, "Article 2 non conforme.");
        assertThat(p.get("INTEGRITE").constat()).isEqualTo("Offre LECTURE_IMPOSSIBLE : conteneur illisible.");
        assertThat(p.get("FRAIS_DOSSIER").valeur()).isFalse();
        assertThat(p.get("OFFRE_UNIQUE").valeur()).isTrue();
        assertThat(p).containsOnlyKeys(EvaluationService.VERIFICATIONS.keySet());
        // Garantie non exigée par le DAO : sans objet.
        assertThat(EvaluationService.proposer(1L, o, List.of(o), Map.of(), List.of(), false).get("GARANTIE").valeur()).isNull();
    }
}
