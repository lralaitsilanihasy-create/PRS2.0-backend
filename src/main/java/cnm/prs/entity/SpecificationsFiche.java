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

/**
 * ⚠️ V73 (demande front du 2026-10-06, DAO complet, §B2) — les <strong>spécifications techniques</strong> d'une version de fiche
 * DAO : un {@code .docx} joint par la PRMP ou l'UGPM, inséré au rang 5 bis du DAO complet. Figé à la validation, recopié à la
 * révision, supprimé avec la version.
 */
@Entity
@Table(name = "t_specifications_fiche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpecificationsFiche {

    @Id
    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "NOM_FICHIER", nullable = false, length = 255)
    private String nomFichier;

    @Column(name = "TAILLE_OCTETS", nullable = false)
    private Long tailleOctets;

    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU", nullable = false)
    private byte[] contenu;

    @Column(name = "DEPOSE_LE", nullable = false)
    private LocalDateTime deposeLe;

    @Column(name = "DEPOSE_PAR", length = 255)
    private String deposePar;
}
