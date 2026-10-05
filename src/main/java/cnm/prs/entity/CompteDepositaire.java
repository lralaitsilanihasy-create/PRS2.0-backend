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
 * ⚠️ V71 (demande front du 2026-10-05, dépositaire de la part de secours, §B1) — le compte d'un <strong>dépositaire</strong>
 * (profil {@code DEPOSITAIRE}, hors coquille interne) : créé à la désignation ({@code A_ACTIVER}), activé par invitation
 * ({@code ACTIF}). Une adresse, un compte, plusieurs procédures. L'identifiant court ({@code D} + 9 chiffres) est le
 * {@code REF_ACTEUR} de {@code t_compte_auth}, dont le login est l'adresse électronique.
 */
@Entity
@Table(name = "t_compte_depositaire")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompteDepositaire {

    public static final String A_ACTIVER = "A_ACTIVER";
    public static final String ACTIF = "ACTIF";
    public static final String ARCHIVE = "ARCHIVE";

    @Id
    @Column(name = "ID_COMPTE", nullable = false, length = 10)
    private String idCompte;

    @Column(name = "EMAIL", nullable = false, length = 255)
    private String email;

    @Column(name = "NOM", nullable = false, length = 200)
    private String nom;

    @Column(name = "TELEPHONE", length = 50)
    private String telephone;

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
