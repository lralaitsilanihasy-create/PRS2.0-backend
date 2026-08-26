package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code tr_compte}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_compte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Compte {

    @Id
    @Column(name = "NUM_COMPTE", nullable = false, length = 20)
    private String numCompte;

    @Column(name = "LIBELLE", length = 100)
    private String libelle;

    @Column(name = "ID_CAT_COMPTE", length = 10)
    private String idCatCompte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CAT_COMPTE", insertable = false, updatable = false)
    @JsonIgnore
    private CatCompte catCompte;
}
