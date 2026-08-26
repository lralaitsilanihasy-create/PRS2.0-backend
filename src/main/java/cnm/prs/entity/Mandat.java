package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — <strong>mandat</strong> d'une PRMP : l'acte de nomination
 * qui l'habilite à traiter, borné dans le temps (3 ans).
 *
 * <p><strong>Une reconduction est un mandat distinct</strong>, jamais une prolongation : nouvel arrêté
 * ({@code REF_ARRETE}), nouvelles dates, {@code NUMERO_MANDAT = 2}. Le renouvellement est
 * <strong>unique</strong> — une même personne ne peut porter plus de 2 mandats
 * (cf. {@code MandatService#creer}).</p>
 *
 * <p>Le mandat est l'<strong>habilitation</strong>, pas l'attribution : un dossier fige son mandat
 * d'attribution à la création ({@code t_dossier.ID_MANDAT_ATTRIB}) et ne le recalcule jamais, tandis que
 * chaque action porte l'<em>opérateur courant</em> (la PRMP en fonction à la date de l'action).</p>
 */
@Entity
@Table(name = "t_mandat", indexes = {
        @Index(name = "idx_mandat_prmp", columnList = "ID_PRMP"),
        @Index(name = "idx_mandat_statut", columnList = "STATUT")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mandat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_MANDAT", nullable = false)
    private Integer idMandat;

    /** Titulaire du mandat (FK {@code t_prmp}). */
    @Column(name = "ID_PRMP", nullable = false, length = 10)
    private String idPrmp;

    /** Nom du titulaire <strong>figé</strong> à la nomination (l'historique reste lisible même si la fiche change). */
    @Column(name = "TITULAIRE", nullable = false, length = 200)
    private String titulaire;

    /** Prise de fonction (date d'effet de l'arrêté). */
    @Column(name = "DATE_DEBUT", nullable = false)
    private LocalDate dateDebut;

    /** Fin de mandat — par défaut {@code dateDebut + 3 ans - 1 jour} (durée légale de 3 ans). */
    @Column(name = "DATE_FIN", nullable = false)
    private LocalDate dateFin;

    /** Référence de l'arrêté de nomination — <strong>obligatoire et propre à ce mandat</strong>. */
    @Column(name = "REF_ARRETE", nullable = false, length = 100)
    private String refArrete;

    /** Voir {@link cnm.prs.enums.StatutMandat} : dérivé des dates, sauf {@code ABROGE}. */
    @Column(name = "STATUT", nullable = false, length = 20)
    private String statut;

    /** 1 = mandat initial, 2 = reconduction (unique). */
    @Column(name = "NUMERO_MANDAT", nullable = false)
    private Integer numeroMandat;

    /** Renseignés uniquement en cas d'abrogation avant terme. */
    @Column(name = "DATE_ABROGATION")
    private LocalDate dateAbrogation;

    @Column(name = "MOTIF_ABROGATION", length = 255)
    private String motifAbrogation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PRMP", insertable = false, updatable = false)
    @JsonIgnore
    private Prmp prmp;
}
