package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V66 (demande front du 2026-10-04, soumission en ligne, lot 2, §B2) — la <strong>cérémonie des clés</strong> d'une
 * procédure : {@code A_VENIR} (des clés manquent, jamais close), {@code CLOSE}, {@code A_REFAIRE} (rouverte, §B5.2).
 * {@code premierDepot} est posé par le lot 3 à la première offre scellée.
 */
@Entity
@Table(name = "t_ceremonie_cles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CeremonieCles {

    public static final String A_VENIR = "A_VENIR";
    public static final String CLOSE = "CLOSE";
    public static final String A_REFAIRE = "A_REFAIRE";

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "DATE_CLOTURE")
    private LocalDateTime dateCloture;

    @Column(name = "PREMIER_DEPOT", nullable = false)
    private Boolean premierDepot = Boolean.FALSE;

    @Column(name = "DATE_MAJ", nullable = false)
    private LocalDateTime dateMaj;
}
