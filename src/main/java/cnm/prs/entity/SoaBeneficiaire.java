package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code tr_soa_beneficiaire}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_soa_beneficiaire")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SoaBeneficiaire {

    @Id
    @Column(name = "SOA_CODE", nullable = false, length = 25)
    private String soaCode;

    @Column(name = "LIBELLE", length = 100)
    private String libelle;
}
