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
 * Entité JPA mappée sur la table {@code t_audit_log}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @Column(name = "ID_LOG", nullable = false)
    private Long idLog;

    @Column(name = "DATE_ACTION", nullable = false)
    private LocalDateTime dateAction;

    @Column(name = "IM_ACTEUR", length = 10)
    private String imActeur;

    @Column(name = "NOM_TABLE", length = 50)
    private String nomTable;

    @Column(name = "ID_ENREGISTREMENT", length = 20)
    private String idEnregistrement;

    @Column(name = "TYPE_ACTION", length = 30)
    private String typeAction;

    @Column(name = "CHAMP_MODIFIE", length = 50)
    private String champModifie;

    @Column(name = "ANCIENNE_VALEUR", columnDefinition = "text")
    private String ancienneValeur;

    @Column(name = "NOUVELLE_VALEUR", columnDefinition = "text")
    private String nouvelleValeur;

    @Column(name = "IP_ADRESSE", length = 45)
    private String ipAdresse;

    @Column(name = "SESSION_ID", length = 100)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SESSION_ID", insertable = false, updatable = false)
    @JsonIgnore
    private SessionUtilisateur sessionutilisateur;
}
