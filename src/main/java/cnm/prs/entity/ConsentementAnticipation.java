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

/** ⚠️ V160 (« appel-offres-restreint », §B7) — la réponse écrite d'un invité à la demande d'ouverture anticipée. */
@Entity
@Table(name = "t_consentement_anticipation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsentementAnticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "NOM", length = 255)
    private String nom;

    /** {@code null} tant que l'invité n'a pas répondu. */
    @Column(name = "ACCORD")
    private Boolean accord;

    @Column(name = "REPONDU_LE")
    private LocalDateTime reponduLe;

    @Column(name = "REPONDU_PAR", length = 100)
    private String reponduPar;
}
