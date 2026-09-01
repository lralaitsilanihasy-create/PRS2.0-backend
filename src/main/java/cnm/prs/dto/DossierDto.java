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

    /**
     * ⚠️ Rattachements (2026-09-01) — Vérificateur <strong>cible</strong> de ce dossier : le rattaché du
     * Membre qui l'a EXAMINÉ (jamais le co-signataire du PV). Résolu serveur, en lot.
     *
     * <p><strong>Cible, pas titulaire exclusif</strong> : le front s'en sert pour distinguer « les
     * miens » du reste de la localité et afficher « à vérifier par X ». Tout Vérificateur de la
     * localité peut agir (arbitrage 1). {@code null} = chaîne incomplète, le repli localité s'applique
     * et le front n'affiche aucun badge.</p>
     */
    private String imVerificateurCible;

    /** Nom lisible du Vérificateur cible ; {@code null} en repli. */
    private String nomVerificateurCible;

    /**
     * ⚠️ Rattachements (2026-09-01) — Assistant <strong>cible</strong> pour l'archivage : le rattaché du
     * Vérificateur ayant EFFECTIVEMENT transmis à SIGMP, sinon celui du Vérificateur cible. Résolu
     * serveur ; {@code null} en repli.
     */
    private String imAssistantCible;

    /** Nom lisible de l'Assistant cible ; {@code null} en repli. */
    private String nomAssistantCible;

    /**
     * ⚠️ Verrou optimiste (cf. {@code docs/plan-conflit-version.md}) — numéro de version de la ligne.
     * <strong>Toujours renseigné en sortie</strong> (GET, POST, PUT), le PUT rendant la version
     * <em>incrémentée</em>. En entrée de PUT : comparé à la version courante, et s'il en diffère
     * l'écriture n'a pas lieu (409 {@code CONFLIT_VERSION}). <strong>Absent/null : toléré</strong> —
     * comportement historique (dernier écrit gagne), par compatibilité ascendante ; d'où l'absence
     * volontaire de {@code @NotNull}. Ignoré en création.
     */
    private Integer version;
}
