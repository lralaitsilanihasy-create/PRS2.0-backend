package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V136 (demande front du 2026-10-19, « copie-offre-candidat », §B1) — la <strong>copie de son offre</strong> que le candidat
 * chiffre sur son poste avec sa phrase de relecture : le récapitulatif de sa saisie, sous AES-256-GCM, clé dérivée par
 * PBKDF2-SHA-256. Le serveur la garde comme l'enveloppe d'une clé de détenteur ({@link CleDetenteur}) : il ne peut pas la lire.
 * <ul>
 *   <li>Une ligne par offre ; un nouveau dépôt remplace l'enveloppe.</li>
 *   <li>{@code effaceeLe} : la séance qui ouvre l'offre est close, l'enveloppe est vidée ({@code env*} nuls) ; la ligne reste
 *       pour dire pourquoi la copie n'est plus là.</li>
 * </ul>
 */
@Entity
@Table(name = "t_copie_offre")
@Getter
@Setter
@NoArgsConstructor
public class CopieOffre {

    @Id
    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "ENV_CHIFFRE", columnDefinition = "text")
    private String envChiffre;

    @Column(name = "ENV_IV", length = 64)
    private String envIv;

    @Column(name = "ENV_SEL", length = 64)
    private String envSel;

    @Column(name = "ENV_ITERATIONS")
    private Integer envIterations;

    @Column(name = "ENV_KDF", length = 30)
    private String envKdf;

    @Column(name = "ENV_ALGORITHME", length = 30)
    private String envAlgorithme;

    @Column(name = "VERSION")
    private Integer version;

    @Column(name = "ENREGISTREE_LE", nullable = false)
    private LocalDateTime enregistreeLe;

    @Column(name = "EFFACEE_LE")
    private LocalDateTime effaceeLe;

    /** Vide l'enveloppe : la séance qui ouvre l'offre est close. */
    public void effacer(LocalDateTime le) {
        envChiffre = null;
        envIv = null;
        envSel = null;
        envIterations = null;
        envKdf = null;
        envAlgorithme = null;
        version = null;
        effaceeLe = le;
    }
}
