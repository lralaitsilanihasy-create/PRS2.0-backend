package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_notification}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @Column(name = "ID_NOTIFICATION", nullable = false)
    private Integer idNotification;

    @Column(name = "ID_DOSSIER")
    private Integer idDossier;

    @Column(name = "TYPE_NOTIF", nullable = false, length = 30)
    private String typeNotif;

    @Column(name = "DESTINATAIRE_IM", length = 7)
    private String destinataireIm;

    @Column(name = "DESTINATAIRE_EMAIL", length = 100)
    private String destinataireEmail;

    @Column(name = "TITRE", length = 200)
    private String titre;

    @Column(name = "CORPS", columnDefinition = "text")
    private String corps;

    @Column(name = "DATE_ENVOI")
    private LocalDateTime dateEnvoi;

    @Column(name = "LU")
    private Boolean lu;

    @Column(name = "DATE_LECTURE")
    private LocalDateTime dateLecture;

    @Column(name = "CANAL", length = 20)
    private String canal;

    /** Destinataire unifié : matricule contrôleur ou identifiant PRMP (selon {@code DESTINATAIRE_TYPE}). */
    @Column(name = "DESTINATAIRE_REF", length = 10)
    private String destinataireRef;

    /** Type du destinataire : {@code CONTROLEUR} ou {@code PRMP} (cf. {@code cnm.prs.enums.TypeActeur}). */
    @Column(name = "DESTINATAIRE_TYPE", length = 20)
    private String destinataireType;

    /** Identifiant de l'objet métier concerné (selon {@code TYPE_OBJET}). */
    @Column(name = "ID_OBJET")
    private Integer idObjet;

    /** Type d'objet concerné : {@code DOSSIER} / {@code PV} / {@code MESSAGE} (cf. {@code cnm.prs.enums.TypeObjet}). */
    @Column(name = "TYPE_OBJET", length = 20)
    private String typeObjet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DESTINATAIRE_IM", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur destinataire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;
}
