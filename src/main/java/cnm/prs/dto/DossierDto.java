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
     * ⚠️ Chronométrage (2026-09-01) — <strong>date prévisionnelle d'achèvement</strong> du traitement à
     * la CNM, en jours ouvrés, calculée <strong>entièrement serveur</strong> : aucun calcul de date côté
     * front. {@code null} si le dossier n'est pas dans le circuit (brouillon, clos, retiré, remplacé).
     *
     * <p>Elle <strong>glisse</strong> : une étape en dépassement compte pour zéro dans la somme, si bien
     * que la date recule d'un jour ouvré par jour ouvré de retard au lieu de promettre un rattrapage qui
     * n'aura pas lieu.</p>
     */
    private java.time.LocalDate datePrevisionnelleFin;

    /**
     * ⚠️ Suivi des délais CNM (demande pilote 2026-09-06) — <strong>date d'enregistrement</strong> du
     * dossier à la CNM : la clôture de l'étape {@code RECEPTION} du chronométrage, c'est-à-dire le
     * {@code debutCompteur} de {@code GET /api/dossiers/{id}/chronometrage}, servi ici <strong>en lot</strong>
     * sur les listes (mêmes requêtes que {@link #datePrevisionnelleFin}, aucun appel par dossier).
     * {@code null} tant que le Secrétaire n'a pas enregistré le dossier.
     *
     * <p>Pourquoi sur ce DTO : {@code GET /api/receptions} est <em>vide</em> pour la PRMP (hors de son
     * périmètre, et cela ne change pas) ; l'appel du chronométrage dossier par dossier aurait été un N+1
     * sur son tableau de bord.</p>
     */
    private java.time.LocalDateTime dateEnregistrement;

    /**
     * ⚠️ Chronométrage (2026-09-01) — vrai quand <strong>la balle est chez la PRMP</strong>
     * ({@code EN_ATTENTE_COMPLEMENTS_DEPOT}, {@code EN_ATTENTE_PIECES}, {@code EN_ATTENTE_DECISION_PRMP}).
     * La date prévisionnelle reste calculée, mais elle glissera tant que la PRMP n'aura pas rendu la
     * main : c'est ce drapeau qui autorise le front à le dire.
     *
     * <p><strong>Wrapper et non {@code boolean} primitif</strong> : ce DTO est aussi un corps de
     * <em>requête</em> ({@code POST}/{@code PUT /api/dossiers}), et un primitif y faisait échouer la
     * désérialisation Jackson en 400 sur toute requête ne portant pas le champ — ce qui est le cas de
     * tous les clients, puisqu'il est en lecture seule. Initialisé à {@code false} pour n'être jamais
     * nul en sortie.</p>
     */
    private Boolean attentePrmp = Boolean.FALSE;

    /** ⚠️ Chronométrage (2026-09-01) — étape ouverte du circuit ; {@code null} si aucune tâche CNM ne court. */
    @Size(max = 30)
    private String etapeCourante;

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
