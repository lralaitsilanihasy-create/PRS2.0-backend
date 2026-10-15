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
 * ⚠️ V120 (2026-10-15, demande front « modeles-guide-cao », §B5 ; décision du pilote n° 16) — un rapport d'évaluation retiré avant
 * sa dernière signature par le président de la commission (ou son représentant désigné pour le rapport), avec son motif : la
 * commission refuse de valider et fait reprendre l'évaluation. Le rapport (PDF, Word) et les signatures déjà posées sont gardés.
 */
@Entity
@Table(name = "t_evaluation_rapport_retire")
@Getter
@Setter
@NoArgsConstructor
public class EvaluationRapportRetire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "PRODUIT_LE", nullable = false)
    private LocalDateTime produitLe;

    @Column(name = "PRODUIT_PAR", length = 100)
    private String produitPar;

    @Column(name = "RETIRE_LE", nullable = false)
    private LocalDateTime retireLe;

    /** L'identifiant du membre qui a retiré le rapport. */
    @Column(name = "RETIRE_PAR", nullable = false, length = 20)
    private String retirePar;

    @Column(name = "MOTIF", nullable = false)
    private String motif;

    /** Les signatures déjà posées, lisibles (« nom le date », « nom (empêché : motif) »), séparées par « ; ». */
    @Column(name = "SIGNATURES")
    private String signatures;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PDF")
    private byte[] pdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "DOCX")
    private byte[] docx;
}
