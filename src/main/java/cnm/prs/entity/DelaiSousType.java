package cnm.prs.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5b, §B6 ; V99) — le délai d'une étape surchargé pour un sous-type de dossier
 * ({@code tr_delai_sous_type}) ; sans ligne, le sous-type prend le délai standard de l'étape.
 */
@Entity
@Table(name = "tr_delai_sous_type")
@IdClass(DelaiSousType.Cle.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DelaiSousType {

    @Id
    @Column(name = "ID_SOUS_TYPE", nullable = false, length = 20)
    private String idSousType;

    @Id
    @Column(name = "ETAPE", nullable = false, length = 30)
    private String etape;

    @Column(name = "DELAI_HEURES", nullable = false)
    private Integer delaiHeures;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private static final long serialVersionUID = 1L;
        private String idSousType;
        private String etape;
    }
}
