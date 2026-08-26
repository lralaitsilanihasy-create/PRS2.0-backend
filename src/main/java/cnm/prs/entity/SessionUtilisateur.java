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
 * Entité JPA mappée sur la table {@code t_session_utilisateur}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_session_utilisateur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionUtilisateur {

    @Id
    @Column(name = "ID_SESSION", nullable = false, length = 100)
    private String idSession;

    @Column(name = "IM_CONTROLEUR", length = 7)
    private String imControleur;

    @Column(name = "DATE_CONNEXION")
    private LocalDateTime dateConnexion;

    @Column(name = "DATE_DECONNEXION")
    private LocalDateTime dateDeconnexion;

    @Column(name = "IP_ADRESSE", length = 45)
    private String ipAdresse;

    @Column(name = "USER_AGENT", length = 300)
    private String userAgent;

    @Column(name = "SUCCES")
    private Boolean succes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CONTROLEUR", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur controleur;
}
