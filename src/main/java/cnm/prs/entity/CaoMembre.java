package cnm.prs.entity;

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
 * ⚠️ V67 (lot 2a, §B1) — un membre de la CAO : qualité {@code MEMBRE} (siège, détient une part, compte {@code MEMBRE_CAO})
 * ou {@code EXPERT_ADJOINT} (évalue, pas de part, pas de compte) ; un {@code MEMBRE} a une origine ({@code ENTITE_CONTRACTANTE}
 * avec son service, {@code EXPERT_OBJET} avec son organisme et son domaine) ; un président parmi les {@code MEMBRE}.
 */
@Entity
@Table(name = "t_cao_membre")
@Getter
@Setter
@NoArgsConstructor
public class CaoMembre {

    public static final String MEMBRE = "MEMBRE";
    public static final String EXPERT_ADJOINT = "EXPERT_ADJOINT";
    public static final String ENTITE_CONTRACTANTE = "ENTITE_CONTRACTANTE";
    public static final String EXPERT_OBJET = "EXPERT_OBJET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_MEMBRE", nullable = false)
    private Long idMembre;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "RANG", nullable = false)
    private Integer rang = 0;

    @Column(name = "NOM", nullable = false, length = 100)
    private String nom;

    @Column(name = "PRENOM", nullable = false, length = 100)
    private String prenom;

    @Column(name = "EMAIL", nullable = false, length = 255)
    private String email;

    @Column(name = "TELEPHONE", length = 30)
    private String telephone;

    @Column(name = "QUALITE", nullable = false, length = 15)
    private String qualite;

    @Column(name = "ORIGINE", length = 20)
    private String origine;

    @Column(name = "FONCTION", length = 200)
    private String fonction;

    @Column(name = "SERVICE", length = 200)
    private String service;

    @Column(name = "ORGANISME", length = 200)
    private String organisme;

    @Column(name = "DOMAINE", length = 200)
    private String domaine;

    @Column(name = "PRESIDENT", nullable = false)
    private Boolean president = Boolean.FALSE;

    /** L'identifiant court du compte {@code MEMBRE_CAO} ({@code K} + 9 chiffres) ; {@code null} pour un expert adjoint. */
    @Column(name = "ID_COMPTE", length = 10)
    private String idCompte;

    public boolean estMembre() {
        return MEMBRE.equals(qualite);
    }
}
