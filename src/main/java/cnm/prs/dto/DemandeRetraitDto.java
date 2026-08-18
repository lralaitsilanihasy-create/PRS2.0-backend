package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.DemandeRetrait}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemandeRetraitDto {

    private Integer idDemandeRetrait;

    @NotNull
    private Integer idDossier;

    @Size(max = 10)
    private String idPrmp;          // ignoré en entrée (dérivé du JWT) ; peuplé en sortie

    @NotBlank
    private String motifRetrait;

    private LocalDateTime dateDemande;   // ignoré en entrée (posé côté serveur)

    @Size(max = 20)
    private String statut;          // ignoré en entrée (forcé EN_ATTENTE)

    @Size(max = 7)
    private String imCtrlCc;

    private LocalDateTime dateDecision;

    @Size(max = 500)
    private String obsDecision;

    /** Nom de la lettre de demande de retrait jointe — sortie seule ; {@code null} pour les demandes antérieures à l'obligation (le front affiche « — »). */
    private String nomFichier;

    /** Taille de la lettre en octets — sortie seule ; {@code null} si aucune pièce. */
    private Long tailleFichier;
}
