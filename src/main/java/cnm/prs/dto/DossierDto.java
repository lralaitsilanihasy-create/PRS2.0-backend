package cnm.prs.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Dossier}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DossierDto {

    private Integer idDossier;

    @Size(max = 10)
    private String idTypeDossier;

    /**
     * ⚠️ Règle ajoutée — sous-type du dossier (référentiel {@code /api/sous-type-dossiers}), la famille
     * ({@code idTypeDossier}) s'en déduit. Famille DDP : dérivé serveur ({@code PPM} / {@code PPM-AGPM}),
     * toute valeur envoyée est ignorée ; familles DMC/DDM : choisi à la saisie.
     */
    @Size(max = 20)
    private String idSousType;

    private Integer idDossierParent;

    @Size(max = 100)
    private String refeDossier;

    private LocalDate dateRef;

    @Size(max = 20)
    private String statut;

    @Size(max = 5)
    private String idLocalite;

    @Size(max = 10)
    private String idPrmp;

    /**
     * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — mandat d'attribution, figé à la création et jamais
     * recalculé. Lecture seule : toute valeur envoyée par le client est ignorée.
     */
    private Integer idMandatAttrib;

    private Integer idEntiteContract;

    /**
     * ⚠️ Demande front (2026-08-19) — traçabilité de la saisie : <strong>login</strong> de l'acteur
     * ayant créé le dossier (PRMP ou UGPM de tutelle). Lecture seule : posé serveur à la création,
     * toute valeur envoyée par le client est ignorée.
     */
    @Size(max = 100)
    private String creePar;

    /** Login de l'acteur ayant soumis le dossier (PRMP seule). Lecture seule, posé serveur. */
    @Size(max = 100)
    private String soumisPar;

    /**
     * Nom lisible « Prénoms Nom » correspondant à {@link #creePar}, résolu serveur (le login n'est
     * pas l'identifiant de l'acteur, et le répertoire des UGPM n'est pas ouvert à tous les profils).
     * {@code null} si le compte ou l'acteur est introuvable — le front garde alors le login brut.
     */
    private String creeParNom;

    /** Nom lisible « Prénoms Nom » correspondant à {@link #soumisPar}, résolu serveur ; {@code null} si non résolvable. */
    private String soumisParNom;
}
