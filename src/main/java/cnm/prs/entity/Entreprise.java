package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V64 (demande front du 2026-10-04, soumission en ligne, lot 1b) — l'<strong>entreprise</strong> d'un compte candidat (un compte = une entreprise) : NIF, STAT et RCS stockés normalisés
 * et uniques ; l'adresse normalisée sert aux rapprochements ; la vérification du NIF est portée par la ligne.
 */
@Entity
@Table(name = "t_entreprise")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Entreprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ENTREPRISE", nullable = false)
    private Integer idEntreprise;

    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "RAISON_SOCIALE", nullable = false, length = 200)
    private String raisonSociale;

    @Column(name = "NIF", nullable = false, length = 30)
    private String nif;

    @Column(name = "STAT", length = 30)
    private String stat;

    @Column(name = "RCS", length = 50)
    private String rcs;

    @Column(name = "ADRESSE", nullable = false, length = 300)
    private String adresse;

    @Column(name = "ADRESSE_NORMALISEE", nullable = false, length = 300)
    private String adresseNormalisee;

    @Column(name = "REP_NOM", nullable = false, length = 100)
    private String repNom;

    @Column(name = "REP_PRENOM", nullable = false, length = 100)
    private String repPrenom;

    @Column(name = "REP_FONCTION", length = 100)
    private String repFonction;

    @Column(name = "VERIF_STATUT", nullable = false, length = 30)
    private String verifStatut;

    @Column(name = "VERIF_SOURCE", length = 20)
    private String verifSource;

    @Column(name = "VERIF_DATE")
    private LocalDateTime verifDate;

    @Column(name = "VERIF_ACTEUR", length = 100)
    private String verifActeur;

    @Column(name = "VERIF_MOTIF", length = 500)
    private String verifMotif;

    @Column(name = "DATE_CREATION", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "DATE_MAJ", nullable = false)
    private LocalDateTime dateMaj;
}
