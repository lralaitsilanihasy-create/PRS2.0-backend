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
 * ⚠️ V144 (demande front du 2026-10-28, « dao-complet-en-file », Q1-Q3) — la place d'une version validée dans la file d'assemblage
 * du DAO complet par Word. Une ligne par version ({@code idFiche}) ; l'ouvrier unique ({@code DaoCompletFileService}) la fait passer
 * de {@code EN_FILE} à {@code EN_COURS}, puis à {@code PRET}, ou la remet {@code EN_FILE} après un échec ({@code reprendreApres}),
 * jusqu'à {@code ECHEC} au dernier essai.
 */
@Entity
@Table(name = "t_dao_complet_file")
@Getter
@Setter
@NoArgsConstructor
public class DaoCompletFile {

    public static final String EN_FILE = "EN_FILE";
    public static final String EN_COURS = "EN_COURS";
    public static final String PRET = "PRET";
    public static final String ECHEC = "ECHEC";

    @Id
    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ETAT", nullable = false, length = 10)
    private String etat;

    /** L'ordre d'arrivée : la mise en file (la validation), jamais reportée par un échec. */
    @Column(name = "ENTREE_LE", nullable = false)
    private LocalDateTime entreeLe;

    /** L'entrée dans l'état courant. */
    @Column(name = "DEPUIS", nullable = false)
    private LocalDateTime depuis;

    /** En tête de file : l'avis spécifique attend ce DAO complet. */
    @Column(name = "PRIORITAIRE", nullable = false)
    private boolean prioritaire;

    @Column(name = "ESSAIS", nullable = false)
    private int essais;

    /** Après un échec : pas de reprise avant cette heure. */
    @Column(name = "REPRISE_APRES")
    private LocalDateTime repriseApres;

    @Column(name = "MOTIF", length = 1000)
    private String motif;

    public DaoCompletFile(Integer idFiche, Long idDmc, LocalDateTime maintenant) {
        this.idFiche = idFiche;
        this.idDmc = idDmc;
        this.etat = EN_FILE;
        this.entreeLe = maintenant;
        this.depuis = maintenant;
    }
}
