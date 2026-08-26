package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — <strong>journal des actions</strong> d'un dossier, horodaté par auteur.
 *
 * <p>Distinct de {@code t_audit_log} (trace technique de toutes les écritures HTTP, réservée à
 * l'Administrateur) : ce journal-ci est <strong>métier et lisible par les profils concernés</strong>. Il
 * répond à une question précise — <em>qui a fait quoi, sous quel mandat</em> — que l'audit technique ne
 * sait pas restituer, faute de connaître l'opérateur au sens des mandats.</p>
 *
 * <p>Chaque ligne fige l'<strong>opérateur courant</strong> : la PRMP en fonction à la date de l'action,
 * qui n'est pas nécessairement la PRMP d'attribution du dossier ({@code t_dossier.ID_PRMP}). C'est
 * exactement ce que la reprise de traitement après changement de PRMP rend observable.</p>
 */
@Entity
@Table(name = "t_action_dossier", indexes = {
        @Index(name = "idx_action_dossier", columnList = "ID_DOSSIER"),
        @Index(name = "idx_action_dossier_date", columnList = "DATE_ACTION")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActionDossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ACTION", nullable = false)
    private Long idAction;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "DATE_ACTION", nullable = false)
    private LocalDateTime dateAction;

    /** Nature de l'action (ex. {@code CREATION}, {@code SOUMISSION}, {@code RESOUMISSION}). */
    @Column(name = "TYPE_ACTION", nullable = false, length = 40)
    private String typeAction;

    /** PRMP en fonction au moment de l'action — l'opérateur, pas l'attributaire. */
    @Column(name = "ID_PRMP_OPERATEUR", length = 10)
    private String idPrmpOperateur;

    /** Nom de l'opérateur figé au moment de l'action. */
    @Column(name = "NOM_OPERATEUR", length = 200)
    private String nomOperateur;

    /** Login réel de l'auteur (PRMP ou agent UGPM agissant sous sa tutelle). */
    @Column(name = "AUTEUR", length = 100)
    private String auteur;

    /** Mandat sous lequel l'action a été faite ({@code null} si mandat implicite, non déclaré). */
    @Column(name = "ID_MANDAT_OPERATEUR")
    private Integer idMandatOperateur;

    @Column(name = "DETAIL", length = 500)
    private String detail;
}
