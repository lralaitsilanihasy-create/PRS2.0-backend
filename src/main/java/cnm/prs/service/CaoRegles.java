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
 * la PRMP ({@code CaoDto.etat}, {@code anomalies}). ⚠️ 2026-10-28 (arbitrage du pilote) : la décision n'est complète qu'avec son
 * PDF signé.
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

    /**
     * La CAO est-elle constituée (règle 13) ? Décision complète (référence, date et, ⚠️ depuis le 2026-10-28, son PDF signé), deux
     * {@code MEMBRE} au moins, un président parmi eux.
     */
    static boolean constituee(Cao cao, List<CaoMembre> tous) {
        return manques(cao, tous).isEmpty();
    }

    /**
     * ⚠️ 2026-10-28 (demande front « decision-cao-obligatoire », §B1, §B2 ; arbitrage du pilote) — ce qui empêche la CAO d'être
     * constituée, une anomalie par cause, dans l'ordre de l'écran : la décision (référence, date, PDF signé), puis les membres.
     * Vide = constituée. Le PDF, « non bloquant » depuis V67 (question 4), en fait désormais partie.
     */
    static List<CaoDto.Anomalie> manques(Cao cao, List<CaoMembre> tous) {
        List<CaoDto.Anomalie> out = new ArrayList<>();
        if (cao == null) {
            out.add(new CaoDto.Anomalie(CAO_INCOMPLETE, "La commission d'appel d'offres n'est pas encore désignée."));
            return out;
        }
        if (cao.getDecisionReference() == null || cao.getDecisionReference().isBlank()) {
            out.add(new CaoDto.Anomalie(CAO_INCOMPLETE, "La décision de nomination n'a pas de référence."));
        }
        if (cao.getDecisionDate() == null) {
            out.add(new CaoDto.Anomalie(CAO_INCOMPLETE, "La décision de nomination n'a pas de date."));
        }
        if (cao.getDecisionFichier() == null && cao.getDecisionTaille() == null) {
            out.add(new CaoDto.Anomalie(DECISION_SANS_FICHIER, "La décision de nomination de la commission n'est pas jointe (PDF signé)."));
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
        return out;
    }

    /** {@code ABSENTE} sans ligne, {@code COMPLETE} si constituée, {@code INCOMPLETE} sinon. */
    static String etat(Cao cao, List<CaoMembre> tous) {
        if (cao == null) {
            return ABSENTE;
        }
        return constituee(cao, tous) ? COMPLETE : INCOMPLETE;
    }

    /**
     * Ce qui manque ({@link #manques}, PDF de la décision compris depuis le 2026-10-28), puis ce qui n'empêche pas la validation
     * mais compte pour la cérémonie : les comptes {@code MEMBRE_CAO} non activés ({@code comptes} : par identifiant court).
     */
    static List<CaoDto.Anomalie> anomalies(Cao cao, List<CaoMembre> tous, Map<String, CompteCao> comptes) {
        List<CaoDto.Anomalie> out = manques(cao, tous);
        if (cao == null) {
            return out;
        }
        List<CaoMembre> membres = membres(tous);
        long nonActives = membres.stream().filter(m -> m.getIdCompte() == null || comptes.get(m.getIdCompte()) == null
                || !CompteCao.ACTIF.equals(comptes.get(m.getIdCompte()).getEtat())).count();
        if (nonActives > 0) {
            out.add(new CaoDto.Anomalie(COMPTES_NON_ACTIVES, nonActives + (nonActives > 1 ? " membres n'ont pas activé leur compte."
                    : " membre n'a pas activé son compte.")));
        }
        return out;
    }
}
