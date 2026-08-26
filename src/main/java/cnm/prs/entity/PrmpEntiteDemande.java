package cnm.prs.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Déclaration d'entité contractante faite par une PRMP à l'inscription (table
 * {@code t_prmp_entite_demande}, §3.1).
 *
 * <p>Distincte des affectations effectives ({@code t_prmp_entite}) : une déclaration est
 * <strong>en attente</strong> tant que l'Administrateur n'a pas validé l'inscription. À la
 * validation, chaque déclaration acceptée devient une affectation active ({@code PrmpEntite}).</p>
 *
 * <p>Deux cas : soit l'entité existe déjà ({@code ID_ENTITE_CONTRACT} renseigné), soit la PRMP
 * propose une entité <strong>non listée</strong> ({@code ID_ENTITE_CONTRACT} null + champs
 * {@code *_PROPOSE}), que l'Administrateur crée à la validation.</p>
 */
@Entity
@Table(name = "t_prmp_entite_demande")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrmpEntiteDemande {

    @Id
    @Column(name = "ID_DEMANDE", nullable = false)
    private Integer idDemande;

    /** Login de l'inscription propriétaire de la déclaration. */
    @Column(name = "LOGIN", nullable = false, length = 100)
    private String login;

    /** Entité existante déclarée ({@code null} si entité proposée non listée). */
    @Column(name = "ID_ENTITE_CONTRACT")
    private Integer idEntiteContract;

    /** Proposition (entité non listée) : libellé. */
    @Column(name = "LIBELLE_PROPOSE", length = 50)
    private String libellePropose;

    /** Proposition : adresse. */
    @Column(name = "ADRESSE_PROPOSE", length = 200)
    private String adressePropose;

    /** Proposition : localité. */
    @Column(name = "ID_LOCALITE_PROPOSE", length = 5)
    private String idLocalitePropose;

    /** Proposition : catégorie. */
    @Column(name = "CATEGORIE_PROPOSE", length = 20)
    private String categoriePropose;

    /** État de la déclaration (cf. {@code cnm.prs.enums.StatutDemandeEntite}). */
    @Column(name = "STATUT_DEMANDE", nullable = false, length = 20)
    private String statutDemande;

    /** Motif renseigné si la déclaration est refusée (ex. entité déjà rattachée à une autre PRMP). */
    @Column(name = "MOTIF", length = 500)
    private String motif;

    @Column(name = "DATE_DECLARATION")
    private LocalDate dateDeclaration;
}
