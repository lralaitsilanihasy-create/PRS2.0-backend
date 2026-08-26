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
 * ⚠️ Règle ajoutée (2026-08-01) — entité JPA mappée sur {@code t_examen_piece} (table additive
 * ddl-auto) : résultat d'examen d'une <strong>pièce jointe</strong> du dossier, une par une (miroir de
 * {@link ExamenDetail} pour les lignes de marché). {@code conforme} = RAS ; sinon {@code observation}
 * porte le constat. Unicité applicative du couple ({@code idExamen}, {@code idPiece}).
 */
@Entity
@Table(name = "t_examen_piece")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExamenPiece {

    @Id
    @Column(name = "ID_EXAMEN_PIECE", nullable = false)
    private Integer idExamenPiece;

    @Column(name = "ID_EXAMEN", nullable = false)
    private Integer idExamen;

    /** Pièce jointe examinée ({@code t_piece_jointe_dossier.ID_PIECE}). */
    @Column(name = "ID_PIECE", nullable = false)
    private Integer idPiece;

    @Column(name = "CONFORME", nullable = false)
    private Boolean conforme;

    /** Observation si non conforme (texte libre). */
    @Column(name = "OBSERVATION", length = 500)
    private String observation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_EXAMEN", insertable = false, updatable = false)
    @JsonIgnore
    private Examen examen;
}
