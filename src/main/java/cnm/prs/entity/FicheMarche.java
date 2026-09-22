package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Règle ajoutée (demande front du 2026-09-22, lot 1) — une <strong>version</strong> de la fiche marché d'un DMC
 * de type DAO ({@code t_fiche_marche}, V35) : « une information saisie une fois, les documents générés depuis la
 * fiche » (lot 2). Une ligne par ({@code idDmc}, {@code numeroVersion}).
 *
 * <p>{@code BROUILLON} tant que la PRMP n'a pas validé (enregistrée bloc par bloc, H5) ; {@code VALIDEE} = figée,
 * une modification ouvre une nouvelle version copiée de la dernière validée (H1). Le cadrage — dix questions —
 * est un petit objet JSON en texte : des réponses fermées, lues ensemble. Les 22 informations du PPM ne sont
 * <strong>pas</strong> ici (H7) : elles sont relues à chaque lecture.</p>
 *
 * <p>{@code numeroVersion} et non {@code version} : ce n'est pas le verrou optimiste du projet.</p>
 */
@Entity
@Table(name = "t_fiche_marche", indexes = {
        @Index(name = "idx_fiche_marche_dmc", columnList = "ID_DMC, NUMERO_VERSION")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheMarche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "NUMERO_VERSION", nullable = false)
    private Integer numeroVersion;

    /** {@link cnm.prs.enums.StatutFicheMarche}. */
    @Column(name = "STATUT", nullable = false, length = 20)
    private String statut;

    /** {@link cnm.prs.enums.TypeMarcheDao}, fixé par le cadrage ; nul tant que le cadrage n'est pas posé. */
    @Column(name = "TYPE_MARCHE", length = 20)
    private String typeMarche;

    /** Réponses du cadrage, objet JSON ({@code {"typeMarche":"QUANTITE_FIXE","alloti":"OUI",…}}). */
    @Column(name = "CADRAGE", length = 2000)
    private String cadrage;

    @Column(name = "DATE_CREATION", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "CREE_PAR", length = 100)
    private String creePar;

    @Column(name = "DATE_MAJ")
    private LocalDateTime dateMaj;

    @Column(name = "DATE_VALIDATION")
    private LocalDateTime dateValidation;

    /** La PRMP qui a validé (matricule). */
    @Column(name = "VALIDE_PAR", length = 10)
    private String validePar;
}
