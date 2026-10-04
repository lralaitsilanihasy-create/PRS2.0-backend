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
 * ⚠️ V64 (demande front du 2026-10-04, soumission en ligne, lot 1b) — un <strong>rapprochement</strong> entre deux comptes candidats (même téléphone, même
 * signataire, même adresse) : une alerte pour la commission (lot 4), jamais un refus. {@code idCandidatA} < {@code idCandidatB}.
 */
@Entity
@Table(name = "t_rapprochement_candidat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RapprochementCandidat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_RAPPROCHEMENT", nullable = false)
    private Integer idRapprochement;

    @Column(name = "ID_CANDIDAT_A", nullable = false, length = 10)
    private String idCandidatA;

    @Column(name = "ID_CANDIDAT_B", nullable = false, length = 10)
    private String idCandidatB;

    @Column(name = "CRITERE", nullable = false, length = 20)
    private String critere;

    @Column(name = "VALEUR", nullable = false, length = 300)
    private String valeur;

    @Column(name = "DATE_CALCUL", nullable = false)
    private LocalDateTime dateCalcul;
}
