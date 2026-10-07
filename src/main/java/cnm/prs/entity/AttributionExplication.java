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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V80 (évaluation des offres, lot 2, tranche 2b, §B4.2, art. 52-II) — une demande d'explication d'un candidat non retenu sur le rejet
 * de son offre, et la réponse écrite de la PRMP (texte, fichier joint facultatif).
 */
@Entity
@Table(name = "t_attribution_explication")
@Getter
@Setter
@NoArgsConstructor
public class AttributionExplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "QUESTION", nullable = false)
    private String question;

    @Column(name = "DEMANDEE_LE", nullable = false)
    private LocalDateTime demandeeLe;

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

    @Column(name = "REPONDUE_PAR", length = 100)
    private String reponduePar;
}
