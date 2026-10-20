package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — une condition déclarée d'une version, dans l'écran
 * (Q1) : le nom employé dans le Word ({@code {{SI:NOM}}}) et son expression, dans la grammaire de {@code ConditionsModele}.
 */
@Entity
@Table(name = "t_condition_modele_pv")
@Getter
@Setter
@NoArgsConstructor
public class ConditionModelePv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CONDITION", nullable = false)
    private Long idCondition;

    @Column(name = "ID_VERSION", nullable = false)
    private Long idVersion;

    @Column(name = "NOM", nullable = false, length = 60)
    private String nom;

    @Column(name = "EXPRESSION", nullable = false)
    private String expression;

    @Column(name = "LIBELLE")
    private String libelle;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;
}
