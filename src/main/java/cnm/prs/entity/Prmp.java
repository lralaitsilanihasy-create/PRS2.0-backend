package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code t_prmp}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_prmp")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Prmp {

    /** Identifiant = matricule de la PRMP (unifié sur le matricule, comme les contrôleurs). */
    @Id
    @Column(name = "ID_PRMP", nullable = false, length = 10)
    private String idPrmp;

    @Column(name = "NOM_PRMP", nullable = false, length = 50)
    private String nomPrmp;

    @Column(name = "PRENOMS_PRMP", nullable = false, length = 100)
    private String prenomsPrmp;

    @Column(name = "ARRETE_NOMIN", nullable = false, length = 100)
    private String arreteNomin;

    @Column(name = "DATE_NOMIN", nullable = false)
    private LocalDate dateNomin;

    @Column(name = "CIN", nullable = false, length = 12)
    private String cin;

    @Column(name = "DATE_CIN", nullable = false)
    private LocalDate dateCin;

    @Column(name = "LIEU_CIN", nullable = false, length = 50)
    private String lieuCin;

    @Column(name = "EMAIL_PRMP", nullable = false, length = 100)
    private String emailPrmp;

    @Column(name = "TEL_PRMP", nullable = false, length = 20)
    private String telPrmp;
}
