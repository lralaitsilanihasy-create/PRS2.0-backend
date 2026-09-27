package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V50 (2026-09-27, remise électronique, §B4, Q7) — le <strong>journal dédié</strong> des paramètres internes d'une
 * procédure ({@code t_parametre_interne_journal}) : qui, quand, quel champ, <strong>ancienne et nouvelle valeur</strong>.
 * Servi au seul titulaire du rôle avec l'écran des paramètres internes. Le journal global {@code t_audit_log}, lisible
 * par l'Administrateur, ne reçoit que la route et l'acteur, jamais les valeurs. Le champ {@code responsable} y trace
 * aussi la désignation et le retrait du titulaire (§B5).
 */
@Entity
@Table(name = "t_parametre_interne_journal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParametreInterneJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "IM_ACTEUR", length = 10)
    private String imActeur;

    /** « NOM Prénoms » de l'acteur, résolu à l'écriture. */
    @Column(name = "NOM_ACTEUR", length = 200)
    private String nomActeur;

    /** {@code membresCommission}, {@code quorum}, {@code dateCeremonie} ou {@code responsable}. */
    @Column(name = "CHAMP", nullable = false, length = 50)
    private String champ;

    @Column(name = "ANCIENNE_VALEUR", columnDefinition = "text")
    private String ancienneValeur;

    @Column(name = "NOUVELLE_VALEUR", columnDefinition = "text")
    private String nouvelleValeur;
}
