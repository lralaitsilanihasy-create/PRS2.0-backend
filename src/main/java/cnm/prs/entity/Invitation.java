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
 * ⚠️ V84 (lot 3 PI, tranche PI-a, §B1) — un invité de la consultation restreinte : un candidat de la liste de l'AMI ({@code AMI}, son
 * compte), ou saisi par la PRMP ({@code SAISIE}, son adresse électronique, par laquelle son compte lui est rattaché) ; son rang et sa
 * lettre d'invitation (le PDF). Réécrit à chaque impression des lettres.
 */
@Entity
@Table(name = "t_invitation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Invitation {

    public static final String AMI = "AMI";
    public static final String SAISIE = "SAISIE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "RANG", nullable = false)
    private Integer rang;

    @Column(name = "SOURCE", nullable = false, length = 10)
    private String source;

    @Column(name = "ID_CANDIDAT", length = 10)
    private String idCandidat;

    @Column(name = "EMAIL", length = 150)
    private String email;

    @Column(name = "NOM", nullable = false, length = 255)
    private String nom;

    @Column(name = "ID_DOCUMENT")
    private Integer idDocument;

    @Column(name = "INVITE_LE", nullable = false)
    private LocalDateTime inviteLe;
}
