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
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — le rattachement d'un sous-type de dossier à un modèle :
 * un sous-type a au plus un modèle par nature (PV, lettre de renvoi). Pas de dates « du / au » : le rattachement courant fait foi,
 * son historique est au journal du modèle.
 */
@Entity
@Table(name = "t_rattachement_modele_pv")
@IdClass(RattachementModelePv.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class RattachementModelePv {

    /** Clé : (sous-type, nature). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private static final long serialVersionUID = 1L;
        private String idSousType;
        private String nature;
    }

    @Id
    @Column(name = "ID_SOUS_TYPE", nullable = false, length = 20)
    private String idSousType;

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
