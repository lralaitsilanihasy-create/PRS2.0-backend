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
 * Entité JPA mappée sur la table {@code t_message}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @Column(name = "ID_MESSAGE", nullable = false)
    private Integer idMessage;

    @Column(name = "ID_DOSSIER")
    private Integer idDossier;

    @Column(name = "EXPEDITEUR_IM", nullable = false, length = 7)
    private String expediteurIm;

    @Column(name = "DESTINATAIRE_IM", nullable = false, length = 7)
    private String destinataireIm;

    @Column(name = "SUJET", length = 200)
    private String sujet;

    @Column(name = "CORPS", columnDefinition = "text")
    private String corps;

    @Column(name = "DATE_ENVOI")
    private LocalDateTime dateEnvoi;

    @Column(name = "LU")
    private Boolean lu;

    @Column(name = "ID_MESSAGE_PARENT")
    private Integer idMessageParent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DESTINATAIRE_IM", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur destinataire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EXPEDITEUR_IM", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur expediteur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_MESSAGE_PARENT", insertable = false, updatable = false)
    @JsonIgnore
    private Message messageParent;
}
