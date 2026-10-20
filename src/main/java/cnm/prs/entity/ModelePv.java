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
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — un modèle de PV (ou de lettre de renvoi), rédigé dans
 * Word par l'Administrateur. Un modèle archivé ne se rattache plus.
 */
@Entity
@Table(name = "t_modele_pv")
@Getter
@Setter
@NoArgsConstructor
public class ModelePv {

    public static final String ACTIF = "ACTIF";
    public static final String ARCHIVE = "ARCHIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_MODELE", nullable = false)
    private Long idModele;

    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @Column(name = "LIBELLE", nullable = false)
    private String libelle;

    /** {@code PV} ou {@code LETTRE_RENVOI}. */
    @Column(name = "NATURE", nullable = false, length = 20)
    private String nature;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "CREE_LE", nullable = false)
    private LocalDateTime creeLe;

    @Column(name = "CREE_PAR")
    private String creePar;

    @Column(name = "ARCHIVE_LE")
    private LocalDateTime archiveLe;

    @Column(name = "MOTIF_ARCHIVAGE")
    private String motifArchivage;
}
