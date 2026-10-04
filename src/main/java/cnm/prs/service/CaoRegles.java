package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cnm.prs.dto.CaoDto;
import cnm.prs.entity.Cao;
import cnm.prs.entity.CaoMembre;
import cnm.prs.entity.CompteCao;

/**
 * ⚠️ V67 (demande front du 2026-10-04, soumission en ligne, lot 2a, §B1, §B3) — ce qu'une <strong>commission d'appel d'offres
 * constituée</strong> veut dire, en règles pures : une décision (référence, date), au moins deux membres de qualité
 * {@code MEMBRE}, exactement un président parmi eux. Lu par le bilan de la fiche (règle 13 {@code SE_CAO}) et par l'écran de
 * la PRMP ({@code CaoDto.etat}, {@code anomalies}).
 */
final class CaoRegles {

    static final String ABSENTE = "ABSENTE";
    static final String INCOMPLETE = "INCOMPLETE";
    static final String COMPLETE = "COMPLETE";

    static final String CAO_INCOMPLETE = "CAO_INCOMPLETE";
    static final String DECISION_SANS_FICHIER = "DECISION_SANS_FICHIER";
    static final String COMPTES_NON_ACTIVES = "COMPTES_NON_ACTIVES";

    private CaoRegles() {
    }

    /** Les membres de qualité {@code MEMBRE} (siègent, détiennent une part). */
    static List<CaoMembre> membres(List<CaoMembre> tous) {
        return tous.stream().filter(CaoMembre::estMembre).toList();
    }

    /** La CAO est-elle constituée (règle 13) ? Décision présente, deux {@code MEMBRE} au moins, un président parmi eux. */
    static boolean constituee(Cao cao, List<CaoMembre> tous) {
        if (cao == null || cao.getDecisionReference() == null || cao.getDecisionReference().isBlank() || cao.getDecisionDate() == null) {
            return false;
        }
        List<CaoMembre> membres = membres(tous);
        return membres.size() >= 2 && membres.stream().filter(m -> Boolean.TRUE.equals(m.getPresident())).count() == 1;
    }

    /** {@code ABSENTE} sans ligne, {@code COMPLETE} si constituée, {@code INCOMPLETE} sinon. */
    static String etat(Cao cao, List<CaoMembre> tous) {
        if (cao == null) {
            return ABSENTE;
        }
        return constituee(cao, tous) ? COMPLETE : INCOMPLETE;
    }

    /**
     * Ce qui manque, puis ce qui n'empêche pas la validation mais compte pour la cérémonie : le PDF de la décision (question
     * 4, non bloquant) et les comptes {@code MEMBRE_CAO} non activés ({@code comptes} : par identifiant court).
     */
    static List<CaoDto.Anomalie> anomalies(Cao cao, List<CaoMembre> tous, Map<String, CompteCao> comptes) {
        List<CaoDto.Anomalie> out = new ArrayList<>();
        if (cao == null) {
            out.add(new CaoDto.Anomalie(CAO_INCOMPLETE, "La commission d'appel d'offres n'est pas encore désignée."));
            return out;
        }
        List<CaoMembre> membres = membres(tous);
        if (membres.size() < 2) {
            out.add(new CaoDto.Anomalie(CAO_INCOMPLETE, "Au moins deux membres sont attendus."));
        }
        long presidents = membres.stream().filter(m -> Boolean.TRUE.equals(m.getPresident())).count();
        if (presidents == 0) {
            out.add(new CaoDto.Anomalie(CAO_INCOMPLETE, "Aucun président n'est désigné parmi les membres."));
        } else if (presidents > 1) {
            out.add(new CaoDto.Anomalie(CAO_INCOMPLETE, "Un seul président est attendu."));
        }
        if (cao.getDecisionFichier() == null && cao.getDecisionTaille() == null) {
            out.add(new CaoDto.Anomalie(DECISION_SANS_FICHIER, "La décision de nomination n'est pas jointe."));
        }
        long nonActives = membres.stream().filter(m -> m.getIdCompte() == null || comptes.get(m.getIdCompte()) == null
                || !CompteCao.ACTIF.equals(comptes.get(m.getIdCompte()).getEtat())).count();
        if (nonActives > 0) {
            out.add(new CaoDto.Anomalie(COMPTES_NON_ACTIVES, nonActives + (nonActives > 1 ? " membres n'ont pas activé leur compte."
                    : " membre n'a pas activé son compte.")));
        }
        return out;
    }
}
