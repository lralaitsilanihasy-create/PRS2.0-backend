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
 * Entité JPA mappée sur la table {@code tr_controleur}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_controleur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Controleur {

    @Id
    @Column(name = "IM_CONTROLEUR", nullable = false, length = 7)
    private String imControleur;

    @Column(name = "NOM_CONT", length = 100)
    private String nomCont;

    @Column(name = "PRENOMS_CONT", length = 100)
    private String prenomsCont;

    @Column(name = "EMAIL_CONT", length = 100)
    private String emailCont;

    @Column(name = "TEL_CONT", length = 20)
    private String telCont;

    @Column(name = "ID_PROFILE")
    private Integer idProfile;

    @Column(name = "ID_LOCALITE", length = 5)
    private String idLocalite;

    /**
     * ⚠️ Rattachement (arbitrage du pilote, 2026-09-01) — contrôleur rattaché à celui-ci : un
     * <strong>Vérificateur</strong> si le porteur est Membre, un <strong>Assistant</strong> si le
     * porteur est Vérificateur. C'est la même relation dans les deux cas — « mon rattaché » — dont le
     * sens est fixé par le profil du porteur, d'où une colonne unique : elle impose structurellement
     * « au plus un rattaché par porteur », et le partage reste libre (plusieurs porteurs peuvent
     * désigner le même).
     *
     * <p>Sert au <strong>ciblage de files et de notifications</strong>, jamais à une habilitation
     * (arbitrage 1) : un autre Vérificateur ou Assistant de la localité peut toujours agir. Nul =
     * chaîne incomplète, cas prévu — le repli localité s'applique.</p>
     */
    @Column(name = "IM_RATTACHE", length = 7)
    private String imRattache;

    @Column(name = "ID_SUPERIEUR", length = 7)
    private String idSuperieur;

    @Column(name = "TRANSVERSAL", nullable = false)
    private Boolean transversal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LOCALITE", insertable = false, updatable = false)
    @JsonIgnore
    private Localite localite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PROFILE", insertable = false, updatable = false)
    @JsonIgnore
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_SUPERIEUR", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur superieur;
}
