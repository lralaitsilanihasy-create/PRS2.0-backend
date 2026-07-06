package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unité de Gestion de la Passation des Marchés (UGPM) — acteur rattaché à <strong>exactement une</strong>
 * PRMP de tutelle ({@code ID_PRMP_TUTELLE → t_prmp}). Une PRMP chapeaute plusieurs UGPM (1,N). L'UGPM crée
 * et corrige les dossiers (sous le périmètre de sa PRMP) mais <strong>ne les soumet pas</strong>.
 */
@Entity
@Table(name = "t_ugpm")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ugpm {

    @Id
    @Column(name = "ID_UGPM", nullable = false, length = 10)
    private String idUgpm;

    @Column(name = "LIBELLE", length = 100)
    private String libelle;

    @Column(name = "ID_PRMP_TUTELLE", nullable = false, length = 10)
    private String idPrmpTutelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PRMP_TUTELLE", insertable = false, updatable = false)
    private Prmp prmpTutelle;
}
