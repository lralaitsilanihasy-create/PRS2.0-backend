package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unité de Gestion de la Passation des Marchés (UGPM) — acteur rattaché à <strong>exactement une</strong>
 * PRMP de tutelle ({@code ID_PRMP_TUTELLE → t_prmp}). Une PRMP chapeaute plusieurs UGPM (1,N). L'UGPM crée
 * et corrige les dossiers (sous le périmètre de sa PRMP) mais <strong>ne les soumet pas</strong>.
 */
@Entity
@Table(name = "t_ugpm")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ugpm {

    /** Identifiant = matricule de l'UGPM (unifié sur le matricule, comme les contrôleurs). */
    @Id
    @Column(name = "ID_UGPM", nullable = false, length = 10)
    private String idUgpm;

    @Column(name = "LIBELLE", length = 100)
    private String libelle;

    @Column(name = "ID_PRMP_TUTELLE", nullable = false, length = 10)
    private String idPrmpTutelle;

    // Identité (mêmes champs que la PRMP, sauf arreteNomin/dateNomin).
    @Column(name = "NOM_UGPM", nullable = false, length = 50)
    private String nomUgpm;

    @Column(name = "PRENOMS_UGPM", nullable = false, length = 100)
    private String prenomsUgpm;

    @Column(name = "CIN", nullable = false, length = 12)
    private String cin;

    @Column(name = "DATE_CIN", nullable = false)
    private LocalDate dateCin;

    @Column(name = "LIEU_CIN", nullable = false, length = 50)
    private String lieuCin;

    @Column(name = "EMAIL_UGPM", nullable = false, length = 100)
    private String emailUgpm;

    @Column(name = "TEL_UGPM", nullable = false, length = 20)
    private String telUgpm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PRMP_TUTELLE", insertable = false, updatable = false)
    private Prmp prmpTutelle;
}
