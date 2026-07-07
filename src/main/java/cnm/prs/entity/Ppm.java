package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code t_ppm}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_ppm", indexes = {
        @Index(name = "idx_ppm_dossier", columnList = "ID_DOSSIER"),
        @Index(name = "idx_ppm_prmp", columnList = "ID_PRMP"),
        @Index(name = "idx_ppm_localite", columnList = "ID_LOCALITE")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ppm {

    @Id
    @Column(name = "ID_PPM", nullable = false)
    private Integer idPpm;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "EXERCICE", nullable = false)
    private Integer exercice;

    @Column(name = "SIGNATAIRE", nullable = false, length = 150)
    private String signataire;

    @Column(name = "DATE_SIGNATURE", nullable = false)
    private LocalDate dateSignature;

    @Column(name = "DATE_PPM_INIT")
    private LocalDate datePpmInit;

    @Column(name = "NUM_MAJ_PREC")
    private Integer numMajPrec;

    @Column(name = "DATE_MAJ_PREC")
    private LocalDate dateMajPrec;

    @Column(name = "NUM_MAJ")
    private Integer numMaj;

    @Column(name = "DATE_MAJ")
    private LocalDate dateMaj;

    @Column(name = "REFERENCE", nullable = false, length = 100)
    private String reference;

    @Column(name = "LIBELLE", length = 200)
    private String libelle;

    @Column(name = "DATE_RECEPTION_CNM")
    private LocalDate dateReceptionCnm;

    @Column(name = "ID_LOCALITE", length = 5)
    private String idLocalite;

    @Column(name = "VU", length = 100)
    private String vu;

    @Column(name = "ID_PRMP", length = 10)
    private String idPrmp;

    @Column(name = "MOTIF_MAJ", length = 500)
    private String motifMaj;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LOCALITE", insertable = false, updatable = false)
    @JsonIgnore
    private Localite localite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PRMP", insertable = false, updatable = false)
    @JsonIgnore
    private Prmp prmp;
}
