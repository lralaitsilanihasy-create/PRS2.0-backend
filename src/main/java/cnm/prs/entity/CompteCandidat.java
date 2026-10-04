package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V63 (demande front du 2026-10-04, soumission en ligne, lot 1a) — la fiche d'un <strong>candidat</strong> : une
 * entreprise externe inscrite par elle-même. Son identifiant court ({@code C} + 9 chiffres) est la {@code REF_ACTEUR} de
 * son compte de connexion {@link CompteAuth}, dont le login est l'adresse électronique. {@code etat} :
 * {@code A_CONFIRMER}, {@code CONFIRME}, {@code ARCHIVE}.
 */
@Entity
@Table(name = "t_compte_candidat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompteCandidat {

    public static final String A_CONFIRMER = "A_CONFIRMER";
    public static final String CONFIRME = "CONFIRME";
    public static final String ARCHIVE = "ARCHIVE";

    @Id
    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "EMAIL", nullable = false, length = 100)
    private String email;

    @Column(name = "TELEPHONE", nullable = false, length = 30)
    private String telephone;

    @Column(name = "NOM", nullable = false, length = 100)
    private String nom;

    @Column(name = "PRENOM", nullable = false, length = 100)
    private String prenom;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "TELEPHONE_CONFIRME", nullable = false)
    private boolean telephoneConfirme;

    @Column(name = "DATE_INSCRIPTION", nullable = false)
    private LocalDateTime dateInscription;

    @Column(name = "DATE_CONFIRMATION")
    private LocalDateTime dateConfirmation;

    @Column(name = "DERNIERE_CONNEXION")
    private LocalDateTime derniereConnexion;

    @Column(name = "DATE_ARCHIVAGE")
    private LocalDateTime dateArchivage;
}
