package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — observation du PÉRIMÈTRE FIGÉ du PV avec
 * son statut courant (ÉMISE / LEVÉE / MAINTENUE — déduit de l'historique, LEVÉE = acquise) et son
 * historique de décisions par itération (traçabilité §4).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObservationPvDto {

    private Integer idObservationPv;
    private Integer idDossier;
    private Integer idPv;
    /** {@code POINT} (grille de contrôle) ou {@code PIECE} (pièce jointe). */
    private String source;
    /** Résultat de pièce d'origine ({@code t_examen_piece}) — PIECE seulement : permet au front de
     * relier l'observation à la pièce jointe concernée (rectification par nouvelle version). */
    private Integer idExamenPiece;
    /** Libellé figé de l'observation, tel qu'arrêté au PV. */
    private String libelle;
    private Integer ordre;

    /** Statut courant : {@code EMISE} / {@code LEVEE} / {@code MAINTENUE}. */
    private String statut;
    /** Dernière précision du vérificateur (« ce qui manque »), si MAINTENUE. */
    private String precision;
    /** Dernière itération où l'observation a été statuée ({@code null} si jamais). */
    private Integer iteration;
    /**
     * ⚠️ Décision produit (2026-08-15) — {@code true} dès qu'une <strong>resoumission de la PRMP</strong>
     * est intervenue depuis la signature du PV : la décision LEVÉE serait acceptée au prochain passage.
     * {@code false} avant la première rectification (le front grise « Levée » en miroir — le premier
     * passage est un rappel, tout est MAINTENUE). Même valeur pour toutes les observations du dossier.
     */
    private Boolean leveePossible;
    /** Historique complet des décisions (ordre chronologique). */
    private List<SuiviObservationDto> historique;

    /** Une ligne d'historique : décision d'une itération (auteur + horodatage). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuiviObservationDto {
        private Integer iteration;
        private String decision;
        private String precision;
        private String imVerificateur;
        private LocalDateTime dateDecision;
    }
}
