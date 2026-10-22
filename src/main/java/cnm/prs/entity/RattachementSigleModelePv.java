package cnm.prs.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

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
 * ⚠️ 2026-10-22 (demande front « modeles-dao », D1-D3 ; V141) — le rattachement d'un <strong>sigle</strong> de document intégré du
 * DAO ({@code DPAO-F}, {@code AE-T}, {@code AVIS-F}…) à un modèle de document : un modèle au plus par sigle (Q1), qui sert toutes les
 * couvertures du sigle. Comme pour les sous-types, le rattachement courant fait foi ; son historique est au journal.
 */
@Entity
@Table(name = "t_rattachement_sigle_modele_pv")
@IdClass(RattachementSigleModelePv.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class RattachementSigleModelePv {

    /** Clé : (sigle, nature). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private static final long serialVersionUID = 1L;
        private String sigle;
        private String nature;
    }

    @Id
    @Column(name = "SIGLE", nullable = false, length = 20)
    private String sigle;

    @Id
    @Column(name = "NATURE", nullable = false, length = 20)
    private String nature;

    @Column(name = "ID_MODELE", nullable = false)
    private Long idModele;

    @Column(name = "RATTACHE_LE", nullable = false)
    private LocalDateTime rattacheLe;

    @Column(name = "RATTACHE_PAR")
    private String rattachePar;
}
