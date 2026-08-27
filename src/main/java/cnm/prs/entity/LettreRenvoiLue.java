package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur {@code t_lettre_renvoi_lue} : trace de lecture d'une lettre de renvoi par un
 * <strong>agent</strong> (une seule entrée par couple lettre/agent). Posée à la consultation du détail
 * d'une lettre SIGNE par la branche propriétaire du dossier (PRMP ou UGPM de sa tutelle) ; sert à ne
 * compter que les lettres non encore lues <em>par l'agent connecté</em>.
 *
 * <p>⚠️ Décision métier 2026-08-27 (migration {@code V7__lettre_renvoi_lue_par_agent.sql}) — l'agent
 * est identifié par son {@code LOGIN_AGENT} ({@code t_compte_auth.LOGIN}, claim {@code sub} du jeton)
 * et non plus par {@code ID_PRMP} : pour une UGPM, {@code ref} porte l'ID_PRMP de sa <em>tutelle</em>,
 * si bien que sa lecture éteignait le badge de la PRMP. {@code idPrmp} est conservé — il documente le
 * périmètre de tutelle de la trace (« qui, dans quelle tutelle, a lu quoi »).</p>
 */
@Entity
@Table(name = "t_lettre_renvoi_lue",
        uniqueConstraints = @UniqueConstraint(name = "uk_lettre_lue_agent", columnNames = {"ID_LETTRE", "LOGIN_AGENT"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LettreRenvoiLue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_LECTURE", nullable = false)
    private Integer idLecture;

    @Column(name = "ID_LETTRE", nullable = false)
    private Integer idLettre;

    /** Périmètre de tutelle de la trace (PRMP du dossier) — conservé, mais ne porte plus l'unicité. */
    @Column(name = "ID_PRMP", nullable = false, length = 10)
    private String idPrmp;

    /** Agent auteur de la lecture : login du compte ({@code t_compte_auth.LOGIN}, claim {@code sub}). */
    @Column(name = "LOGIN_AGENT", nullable = false, length = 100)
    private String loginAgent;

    @Column(name = "DATE_LECTURE", nullable = false)
    private LocalDateTime dateLecture;
}
