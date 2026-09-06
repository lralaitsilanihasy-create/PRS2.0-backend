package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Versions archivées (demande pilote du 2026-09-06) — en-tête d'une <strong>version archivée</strong>
 * du contenu d'un dossier de planification, figée au moment où une rectification la remplace.
 *
 * <p>Une rectification corrige le PPM <em>en place</em> : sans archive, chaque cycle effaçait l'état
 * précédent. Désormais, au premier {@code PUT /api/saisies/ppm/{id}} de chaque cycle, l'état remplacé
 * devient la version n° {@code NUMERO} du dossier (ordre d'archivage), avec sa date, son auteur (PRMP
 * opératrice + login réel, comme le journal {@code t_action_dossier}), son itération de rectification
 * ({@code CYCLE} = resoumissions + 1 au gel) et l'en-tête du PPM tel qu'il était. Ses lignes sont les
 * {@link SnapshotRectifLigne} rattachées par {@code ID_VERSION}.</p>
 *
 * <p><strong>Immuable</strong> : {@link Immutable} côté Hibernate (aucun UPDATE émis), trigger
 * {@code fn_version_archivee_immuable} côté PostgreSQL (tout UPDATE refusé). Seule la purge du circuit
 * (retrait accepté, annulation de dispatch, suppression du dossier) la supprime, avec le reste de
 * l'historique.</p>
 */
@Entity
@Table(name = "t_version_dossier")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class VersionDossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_VERSION", nullable = false)
    private Integer idVersion;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** Numéro d'ordre d'archivage dans le dossier (1, 2, …) — unique par dossier. */
    @Column(name = "NUMERO", nullable = false)
    private Integer numero;

    /** Nom de {@link cnm.prs.enums.OrigineVersion}. */
    @Column(name = "ORIGINE", nullable = false, length = 20)
    private String origine;

    /** Itération de rectification (resoumissions du dossier + 1 au moment du gel). */
    @Column(name = "CYCLE")
    private Integer cycle;

    @Column(name = "DATE_VERSION", nullable = false)
    private LocalDateTime dateVersion;

    /** PRMP opératrice au moment du gel (celle du jeton, ou la PRMP de tutelle d'un agent UGPM). */
    @Column(name = "ID_PRMP_AUTEUR", length = 10)
    private String idPrmpAuteur;

    /** « Prénoms Nom » de la PRMP opératrice, figé au moment du gel. */
    @Column(name = "NOM_AUTEUR", length = 200)
    private String nomAuteur;

    /** Login réel de l'auteur (PRMP ou agent UGPM agissant sous sa tutelle). */
    @Column(name = "AUTEUR", length = 100)
    private String auteur;

    // --- En-tête du PPM tel qu'il était au gel (nuls pour une version reprise d'avant la V18) ---

    @Column(name = "EXERCICE")
    private Integer exercice;

    @Column(name = "REFERENCE", length = 100)
    private String reference;

    @Column(name = "SIGNATAIRE", length = 210)
    private String signataire;

    @Column(name = "DATE_SIGNATURE")
    private LocalDate dateSignature;

    @Column(name = "NB_LIGNES", nullable = false)
    private Integer nbLignes;
}
