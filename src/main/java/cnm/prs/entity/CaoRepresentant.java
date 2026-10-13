package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ 2026-10-10 (manuel révisé, lot 1, R1, §B2 ; V101 ; arbitrage : désignation par séance) — le représentant du président de la CAO,
 * désigné par lui pour un contexte ({@code SEANCE}, {@code SEANCE_FINANCIERE}, {@code RAPPORT}, {@code AMI}) : sa signature vaut celle du
 * président pour le quorum.
 */
@Entity
@Table(name = "t_cao_representant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CaoRepresentant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "CONTEXTE", nullable = false, length = 30)
    private String contexte;

    /** Le compte ({@code K…}) du membre représentant. */
    @Column(name = "IM", nullable = false, length = 20)
    private String im;

    @Column(name = "DESIGNE_LE", nullable = false)
    private LocalDateTime designeLe;

    @Column(name = "DESIGNE_PAR", length = 100)
    private String designePar;
}
