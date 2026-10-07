package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V78 (évaluation des offres, §B6) — le rapport d'évaluation d'une procédure, ses signataires appelés et ses deux formats. */
@Entity
@Table(name = "t_evaluation_rapport")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRapport {

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "PRODUIT_LE", nullable = false)
    private LocalDateTime produitLe;

    @Column(name = "PRODUIT_PAR", length = 100)
    private String produitPar;

    @Column(name = "OBSERVATIONS")
    private String observations;

    /** Les identifiants des membres appelés à signer, séparés par des virgules. */
    @Column(name = "SIGNATAIRES", length = 400)
    private String signataires;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PDF")
    private byte[] pdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "DOCX")
    private byte[] docx;

    @Column(name = "SIGNE_LE")
    private LocalDateTime signeLe;
}
