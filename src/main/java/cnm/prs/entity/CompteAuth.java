package cnm.prs.entity;

import java.time.LocalDateTime;

import cnm.prs.enums.StatutCompte;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compte d'authentification (table {@code t_compte_auth}).
 *
 * <p>Table dédiée à la connexion : elle unifie les deux populations qui s'authentifient
 * — contrôleurs ({@code tr_controleur}) et PRMP ({@code t_prmp}) — sans ajouter de mot de
 * passe aux tables métier. {@code refActeur} pointe vers {@code IM_CONTROLEUR} ou
 * {@code ID_PRMP} selon {@code typeActeur}.</p>
 *
 * <p>Le cycle de vie de l'inscription est porté par {@code STATUT}
 * ({@link cnm.prs.enums.StatutCompte}) ; le booléen {@code ACTIF} reste la source du login
 * (invariant {@code ACTIF=true} ⟺ {@code STATUT=ACTIF}).</p>
 */
@Entity
@Table(name = "t_compte_auth")
@Getter
@Setter
@NoArgsConstructor
public class CompteAuth {

    @Id
    @Column(name = "LOGIN", nullable = false, length = 100)
    private String login;

    /** Hash BCrypt du mot de passe (jamais le mot de passe en clair). */
    @Column(name = "MOT_DE_PASSE", nullable = false, length = 100)
    private String motDePasse;

    /** Type d'acteur : CONTROLEUR ou PRMP (cf. {@code cnm.prs.enums.TypeActeur}). */
    @Column(name = "TYPE_ACTEUR", nullable = false, length = 20)
    private String typeActeur;

    /** Matricule du contrôleur ({@code IM_CONTROLEUR}) ou identifiant PRMP ({@code ID_PRMP}). */
    @Column(name = "REF_ACTEUR", nullable = false, length = 10)
    private String refActeur;

    @Column(name = "ACTIF", nullable = false)
    private Boolean actif;

    /** Cycle de vie de l'inscription (cf. {@link cnm.prs.enums.StatutCompte}). */
    @Column(name = "STATUT", length = 20)
    private String statut;

    /** Motif renseigné lorsque l'inscription est refusée par l'Administrateur. */
    @Column(name = "MOTIF_REFUS", length = 500)
    private String motifRefus;

    /** Horodatage de la décision (validation ou refus). */
    @Column(name = "DATE_DECISION")
    private LocalDateTime dateDecision;

    /** Matricule de l'Administrateur ayant pris la décision. */
    @Column(name = "IM_VALIDATEUR", length = 10)
    private String imValidateur;

    /**
     * Constructeur de création usuel : un compte est créé avec son état d'activation, et son
     * {@code STATUT} en découle ({@code ACTIF} → {@link StatutCompte#ACTIF}, sinon
     * {@link StatutCompte#EN_ATTENTE}).
     */
    public CompteAuth(String login, String motDePasse, String typeActeur, String refActeur, Boolean actif) {
        this.login = login;
        this.motDePasse = motDePasse;
        this.typeActeur = typeActeur;
        this.refActeur = refActeur;
        this.actif = actif;
        this.statut = Boolean.TRUE.equals(actif) ? StatutCompte.ACTIF.name() : StatutCompte.EN_ATTENTE.name();
    }
}
