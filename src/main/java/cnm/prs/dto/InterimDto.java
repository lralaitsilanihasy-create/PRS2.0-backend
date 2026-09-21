package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lecture d'un intérim désigné ({@link cnm.prs.entity.Interim}) — demande front du 2026-09-21, §B1.
 *
 * <p>{@code statut} est <strong>dérivé à la date du jour</strong> ({@link cnm.prs.enums.StatutInterim}),
 * jamais reçu. {@code pieceDisponible} dit si la pièce PDF est téléchargeable
 * ({@code GET /api/interims/{id}/piece}). {@code avertissements} n'est renseigné qu'en réponse à la
 * création (cumul signalé, §B3) ; {@code null} en lecture.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterimDto {

    private Integer idInterim;

    private String imTitulaire;

    private String nomTitulaire;

    private String profilTitulaire;

    private String idLocaliteTitulaire;

    private String imInterimaire;

    private String nomInterimaire;

    private String profilInterimaire;

    private String idLocaliteInterimaire;

    private LocalDate dateDebut;

    private LocalDate dateFin;

    private String motif;

    private String reference;

    private String pieceNom;

    private Boolean pieceDisponible;

    private String designePar;

    private String nomDesignePar;

    private LocalDateTime dateDesignation;

    private String statut;

    private LocalDate dateRevocation;

    private String motifRevocation;

    private String revoquePar;

    /** Cumuls signalés à la création (déjà intérimaire d'un autre, déjà attributaire de dossiers) ; {@code null} en lecture. */
    private List<String> avertissements;
}
