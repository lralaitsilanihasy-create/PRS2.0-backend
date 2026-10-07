package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V76 (§B2, §B4) — une demande adressée par la PRMP au candidat d'une offre : une précision (art. 35-VI, examen préliminaire)
 * ou une justification de prix (art. 48, tranche suivante), avec son délai, et la réponse du candidat (texte et fichier joint).
 */
@Entity
@Table(name = "t_evaluation_demande")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDemande {

    public static final String PRECISION = "PRECISION";
    public static final String JUSTIFICATION = "JUSTIFICATION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "TYPE", nullable = false, length = 15)
    private String type;

    @Column(name = "QUESTION", nullable = false)
    private String question;

    @Column(name = "DELAI_JOURS", nullable = false)
    private Integer delaiJours;

    @Column(name = "ECHEANCE", nullable = false)
    private LocalDateTime echeance;

    @Column(name = "DEMANDEE_LE", nullable = false)
    private LocalDateTime demandeeLe;

    @Column(name = "DEMANDEE_PAR", length = 100)
    private String demandeePar;

    @Column(name = "REPONSE")
    private String reponse;

    @Column(name = "REPONSE_NOM", length = 255)
    private String reponseNom;

    @Column(name = "REPONSE_FORMAT", length = 50)
    private String reponseFormat;

    @Column(name = "REPONSE_TAILLE")
    private Long reponseTaille;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "REPONSE_CONTENU")
    private byte[] reponseContenu;

    @Column(name = "REPONDUE_LE")
    private LocalDateTime reponduLe;
}
