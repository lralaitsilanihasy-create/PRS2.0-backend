package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 3) — ce que rend une vérification de plan
 * ({@code POST /api/pre-controle/ppm/{id}/verifier}) ou sa relecture ({@code GET}).
 *
 * <p>Les compteurs sont servis par le serveur plutôt que recomptés par l'écran : ce sont eux qui écrivent
 * la phrase du bandeau (« 3 points à regarder, dont 1 prioritaire ; 2 levés par vos corrections »), et deux
 * écrans ne doivent pas compter différemment.</p>
 *
 * @param idPpm            le plan vérifié
 * @param exercice         son exercice
 * @param dateVerification horodatage de cette vérification
 * @param nbOuverts        signalements ouverts — ce qu'il reste à regarder
 * @param nbPrioritaires   parmi les ouverts, ceux dont le constat change la procédure ou soustrait le
 *                         marché au contrôle a priori
 * @param nbEcartes        signalements écartés avec motif
 * @param nbLeves          signalements qui ne ressortent plus, conservés
 * @param signalements     la liste, ouverts d'abord, prioritaires en tête
 */
public record ResumePreControleDto(
        Integer idPpm,
        Integer exercice,
        LocalDateTime dateVerification,
        int nbOuverts,
        int nbPrioritaires,
        int nbEcartes,
        int nbLeves,
        List<SignalementDto> signalements) {
}
