package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-d1, §B3 ; V88) — la seconde séance d'ouverture : {@code etat} ∈ {@code OUVERTE} ·
 * {@code DECHIFFREE} · {@code CLOSE} ; les enveloppes financières à ouvrir (propositions qualifiées techniquement, ou le seul premier
 * classé selon la méthode) et celles qui ne s'ouvrent jamais, avec leur motif.
 */
public record SeanceFinanciereDto(Long idDmc, String etat, String methode, Integer quorum, List<Enveloppe> aOuvrir, List<NonOuverte> nonOuvertes,
        List<String> presents, List<SeanceDto.Autre> autres, boolean secoursEmploye, LocalDateTime ouverteLe, LocalDateTime dechiffreeLe,
        LocalDateTime closeLe, boolean pvDisponible) {

    /**
     * Une enveloppe financière à ouvrir : la proposition, sa note technique et son rang, les parts reçues ; après le déchiffrement, son
     * intégrité et l'acte d'engagement lu (montants).
     */
    public record Enveloppe(String idOffre, Integer numero, String raisonSociale, Integer lot, BigDecimal noteTechnique, Integer rangTechnique,
            int partsRecues, String integrite, Map<String, Object> acteEngagement) {
    }

    /** Une enveloppe financière qui ne s'ouvre pas : éliminée techniquement, ou hors du premier rang selon la méthode. */
    public record NonOuverte(Integer numero, String raisonSociale, Integer lot, String motif) {
    }

    /** {@code POST …/financiere/cloturer} : les présents (membres de la CAO), les autres présents (candidats…), les observations. */
    public record Cloture(List<String> presents, List<SeanceDto.Autre> autres, String observations) {
    }
}
