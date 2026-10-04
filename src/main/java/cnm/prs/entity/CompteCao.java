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
 * ⚠️ V67 (lot 2a, §B2) — le compte d'un <strong>membre de CAO</strong> (profil {@code MEMBRE_CAO}, hors coquille interne) :
 * créé à la désignation ({@code A_ACTIVER}), activé par invitation ({@code ACTIF}). Une personne, un compte, plusieurs CAO.
 * L'identifiant court ({@code K} + 9 chiffres) est le {@code REF_ACTEUR} de {@code t_compte_auth}, dont le login est
 * l'adresse électronique.
 */
@Entity
@Table(name = "t_compte_cao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompteCao {

    public static final String A_ACTIVER = "A_ACTIVER";
    public static final String ACTIF = "ACTIF";
    public static final String ARCHIVE = "ARCHIVE";

    @Id
    @Column(name = "ID_COMPTE", nullable = false, length = 10)
    private String idCompte;

    @Column(name = "EMAIL", nullable = false, length = 255)
    private String email;

    @Column(name = "NOM", nullable = false, length = 100)
    private String nom;

    @Column(name = "PRENOM", nullable = false, length = 100)
    private String prenom;

    @Column(name = "ETAT", nullable = false, length = 10)
    private String etat;

    @Column(name = "DATE_CREATION", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "DATE_INVITATION")
    private LocalDateTime dateInvitation;

    @Column(name = "DATE_ACTIVATION")
    private LocalDateTime dateActivation;

    @Column(name = "DERNIERE_CONNEXION")
    private LocalDateTime derniereConnexion;
}
