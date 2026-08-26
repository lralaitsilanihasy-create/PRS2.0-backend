package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * Entité JPA mappée sur la table {@code t_organigramme}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_organigramme")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Organigramme {

    @Id
    @Column(name = "ID_ORGANIGRAMME", nullable = false)
    private Integer idOrganigramme;

    @Column(name = "ID_MINISTERE", nullable = false)
    private Integer idMinistere;

    @Column(name = "LIBELLE", length = 200)
    private String libelle;

    @Column(name = "VERSION", length = 20)
    private String version;

    @Column(name = "DATE_VALIDATION")
    private LocalDate dateValidation;

    @Column(name = "ACTIF", nullable = false)
    private Boolean actif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_MINISTERE", insertable = false, updatable = false)
    @JsonIgnore
    private Ministere ministere;
}
