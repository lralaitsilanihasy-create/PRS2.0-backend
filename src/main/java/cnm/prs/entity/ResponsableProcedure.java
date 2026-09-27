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
 * ⚠️ V50 (2026-09-27, remise électronique, §B5 ; ADR-0010) — le rôle nominatif <strong>« Responsable de la
 * procédure »</strong> ({@code t_responsable_procedure}) : par DMC, <strong>un seul titulaire actif</strong>
 * ({@code DATE_RETRAIT} nulle, index partiel unique), désigné et retiré par l'Administrateur. Ce n'est ni un
 * {@code ProfilUtilisateur} (rôle de session), ni une délégation de profil, ni un intérim (ADR-0008) : le rôle porte
 * des <strong>droits exclusifs</strong> — lire et écrire les paramètres internes de cette procédure — qu'aucun profil
 * n'a, l'Administrateur compris. L'historique des titulaires reste (une ligne par désignation).
 */
@Entity
@Table(name = "t_responsable_procedure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponsableProcedure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    /** Matricule du titulaire ({@code tr_controleur.IM_CONTROLEUR}). */
    @Column(name = "IM_RESPONSABLE", nullable = false, length = 10)
    private String imResponsable;

    /** « NOM Prénoms » au moment de la désignation. */
    @Column(name = "NOM_RESPONSABLE", length = 200)
    private String nomResponsable;

    @Column(name = "DESIGNE_PAR", length = 10)
    private String designePar;

    @Column(name = "DATE_DESIGNATION", nullable = false)
    private LocalDateTime dateDesignation;

    @Column(name = "RETIRE_PAR", length = 10)
    private String retirePar;

    /** Nulle tant que la désignation est active. */
    @Column(name = "DATE_RETRAIT")
    private LocalDateTime dateRetrait;
}
