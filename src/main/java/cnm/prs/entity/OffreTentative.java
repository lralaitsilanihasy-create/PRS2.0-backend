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
 * ⚠️ V112 (manuel révisé, lot 2, §B4 n° 4 ; art. 44) — une tentative de dépôt refusée hors délai : le geste refusé, horodaté par le
 * serveur, le candidat et ce qui est connu de son dépôt.
 */
@Entity
@Table(name = "t_offre_tentative")
@Getter
@Setter
@NoArgsConstructor
public class OffreTentative {

    public static final String CREATION = "CREATION";
    public static final String MORCEAU = "MORCEAU";
    public static final String SCELLEMENT = "SCELLEMENT";
    public static final String DELAI_DEPASSE = "DELAI_DEPASSE";

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

    @Column(name = "NIF", length = 50)
    private String nif;

    @Column(name = "RAISON_SOCIALE", length = 255)
    private String raisonSociale;

    @Column(name = "LOT")
    private Integer lot;

    @Column(name = "ENVELOPPE", length = 12)
    private String enveloppe;

    @Column(name = "ID_OFFRE", length = 36)
    private String idOffre;

    @Column(name = "GESTE", nullable = false, length = 12)
    private String geste;

    @Column(name = "MOTIF", nullable = false, length = 30)
    private String motif;

    @Column(name = "HORODATAGE", nullable = false)
    private LocalDateTime horodatage;

    @Column(name = "DATE_LIMITE")
    private LocalDateTime dateLimite;
}
