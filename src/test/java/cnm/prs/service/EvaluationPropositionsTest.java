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
                new SeanceDto.EntrepriseLue("111", "Alpha", null, new EntrepriseCandidatDto.Exclusion(1, "111", "Alpha", "Fraude", "ARMP-77", null, null, true, List.of()), null),
                null, Map.of(), new SeanceDto.Garantie("G1", true, new BigDecimal("100"), "MGA", null), List.of(),
                List.of("Pouvoir du signataire", "Quittance"), List.of(new SeanceDto.Alerte("GARANTIE_INSUFFISANTE", "Garantie de 100 pour 200."),
                        new SeanceDto.Alerte("NON_CONFORME", "Article 2 non conforme."), new SeanceDto.Alerte("EXCLUSION", "Exclusion en cours.")),
                true, null, null, new SeanceDto.FraisDossier(false, null, null), null);
        Map<String, EvaluationService.Proposition> p = EvaluationService.proposer(1L, o, List.of(o), Map.of("o1", Set.of("111")),
                List.of(new OffreDto.PieceAttendue("POUVOIR", "ADMINISTRATIVE", null, "Pouvoir du signataire", null, null, false, null, true, null, null, null)), true);
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

    @Test
    @DisplayName("⚠️ 2026-10-15 (« modeles-guide-cao », §B3.1) — les rubriques du guide : validité comparée au DAO, bordereau rempli en ligne, "
            + "visite des lieux sans objet hors exigence, cotes proposées (C, NC, SO, nulle à examiner)")
    void rubriquesDuGuide() {
        SeanceDto.OffreLue o = new SeanceDto.OffreLue(1, "o1", null, "DEPOSEE", "INTACTE", null, new SeanceDto.EntrepriseLue("111", "Alpha", null, null, null),
                null, Map.of("montantHt", "1000", "validiteJours", "60", "delai", "30", "delaiUnite", "jours"),
                new SeanceDto.Garantie("G1", true, new BigDecimal("100"), "MGA", null), List.of(), List.of(), List.of(), true, null,
                List.of(FormulairesEnLigne.BORDEREAU), null, null);
        Map<String, EvaluationService.Proposition> p = EvaluationService.proposer(1L, o, List.of(o), Map.of(), List.of(), true,
                new EvaluationService.ExigencesDao(90, false, true, false));
        assertThat(p.get("VALIDITE_OFFRE")).extracting(EvaluationService.Proposition::valeur, EvaluationService.Proposition::constat)
                .containsExactly(false, "Validité déclarée : 60 jours ; exigée : 90 jours.");
        assertThat(p.get("VALIDITE_OFFRE").cote()).isEqualTo("NC");
        assertThat(p.get("BORDEREAU").cote()).isEqualTo("C");
        assertThat(p.get("BORDEREAU").constat()).startsWith("Détail quantitatif et estimatif");
        assertThat(p.get("DELAI").constat()).isEqualTo("Délai déclaré : 30 jours.");
        assertThat(p.get("DELAI").cote()).isNull();
        assertThat(p.get("VISITE_LIEUX").cote()).isEqualTo("SO");
        assertThat(p.get("GARANTIE_VALIDITE").cote()).isNull();
        assertThat(p.get("FRAIS_DOSSIER").cote()).isEqualTo("SO");
        assertThat(p).containsOnlyKeys(EvaluationService.VERIFICATIONS.keySet());
        // Visite exigée par le DAO (travaux) : la pièce attendue est cherchée dans l'offre.
        Map<String, EvaluationService.Proposition> v = EvaluationService.proposer(1L, o, List.of(o), Map.of(),
                List.of(new OffreDto.PieceAttendue("VISITE", "ADMINISTRATIVE", null, "Certificat de visite des lieux", null, null, false, null, true,
                        null, null, null)), true, new EvaluationService.ExigencesDao(null, true, true, false));
        assertThat(v.get("VISITE_LIEUX").cote()).isEqualTo("C");
        assertThat(v.get("VALIDITE_OFFRE").cote()).isNull();
    }

    @Test
    @DisplayName("⚠️ §B3.2 — le tableau de conformité technique d'une offre : exigée, proposée, déclaration, cote proposée")
    void specifications() throws Exception {
        cnm.prs.dto.BesoinEnLigneDto.Lot lot = new cnm.prs.dto.BesoinEnLigneDto.Lot(1, "Lot 1", List.of(new cnm.prs.dto.BesoinEnLigneDto.Article(7, 1,
                "Ordinateur", "u", BigDecimal.ONE, null, null, List.of(new cnm.prs.dto.BesoinEnLigneDto.Caracteristique(3, 1, "Mémoire", "16 Go"),
                        new cnm.prs.dto.BesoinEnLigneDto.Caracteristique(4, 2, "Écran", "15 pouces")), null, null, null, null, false, null)),
                null, null, null, null);
        tools.jackson.databind.JsonNode f = tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"conformite\":[{\"idArticle\":7,"
                + "\"marque\":\"HP\",\"modele\":\"X1\",\"caracteristiques\":[{\"idCaracteristique\":3,\"proposee\":\"8 Go\",\"conforme\":false},"
                + "{\"idCaracteristique\":4,\"proposee\":\"15,6 pouces\"}]}]}");
        List<cnm.prs.dto.EvaluationDto.Specification> s = FormulairesEnLigne.specifications(lot, f);
        assertThat(s).extracting(cnm.prs.dto.EvaluationDto.Specification::caracteristique, cnm.prs.dto.EvaluationDto.Specification::exigee,
                cnm.prs.dto.EvaluationDto.Specification::proposee, cnm.prs.dto.EvaluationDto.Specification::coteProposee)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Mémoire", "16 Go", "8 Go", "NC"),
                        org.assertj.core.groups.Tuple.tuple("Écran", "15 pouces", "15,6 pouces", null));
        assertThat(s.get(0).article()).isEqualTo("1. Ordinateur");
        assertThat(s.get(0).marque()).isEqualTo("HP");
        assertThat(FormulairesEnLigne.specifications(lot, tools.jackson.databind.json.JsonMapper.builder().build().readTree("{}"))).isEmpty();
    }
}
