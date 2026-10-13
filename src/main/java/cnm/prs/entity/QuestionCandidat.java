package cnm.prs.entity;

import java.time.LocalDateTime;

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
 * ⚠️ R7 (manuel révisé pour l'application, lot 2, §B2 ; V109, 2026-10-13) — la question d'un candidat ayant retiré le DAO d'une
 * procédure en ligne (art. 35-II), recevable jusqu'à dix jours avant la date limite de remise des offres, et la réponse de la PRMP,
 * diffusée à sa publication à tous les candidats ayant retiré le dossier.
 */
@Entity
@Table(name = "t_question_candidat")
@Getter
@Setter
@NoArgsConstructor
public class QuestionCandidat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "ID_ENTREPRISE")
    private Integer idEntreprise;

    @Column(name = "QUESTION", nullable = false)
    private String question;

    @Column(name = "POSEE_LE", nullable = false)
    private LocalDateTime poseeLe;

    @Column(name = "REPONSE")
    private String reponse;

    @Column(name = "REPONSE_NOM", length = 255)
    private String reponseNom;

    @Column(name = "REPONSE_FORMAT", length = 50)
    private String reponseFormat;

    @Column(name = "REPONSE_TAILLE")
    private Long reponseTaille;

    @Column(name = "REPONSE_CONTENU")
    private byte[] reponseContenu;

    @Column(name = "REPONDUE_LE")
    private LocalDateTime repondueLe;

    @Column(name = "REPONDUE_PAR", length = 100)
    private String reponduePar;

    /** La diffusion : répondre = diffuser (arbitrage du 13/10), donc égale à {@code repondueLe}. */
    @Column(name = "PUBLIEE_LE")
    private LocalDateTime publieeLe;
}
