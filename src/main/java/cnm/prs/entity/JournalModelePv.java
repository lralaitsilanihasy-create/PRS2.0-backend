package cnm.prs.entity;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.config.MentionHorlogeRecette;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — le journal d'un modèle de PV : créé, déposé, déclaré,
 * mis en vigueur, rattaché, dupliqué, archivé. Il nourrit l'historique de la fiche du modèle.
 */
@Entity
@EntityListeners(MentionHorlogeRecette.class)
@Table(name = "t_journal_modele_pv")
@Getter
@Setter
@NoArgsConstructor
public class JournalModelePv implements MentionHorlogeRecette.Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_JOURNAL", nullable = false)
    private Long idJournal;

    @Column(name = "ID_MODELE", nullable = false)
    private Long idModele;

    @Column(name = "NUMERO_VERSION")
    private Integer numeroVersion;

    @Column(name = "ACTION", nullable = false, length = 30)
    private String action;

    @Column(name = "DETAIL")
    private String detail;

    @Column(name = "PAR")
    private String par;

    @Column(name = "LE", nullable = false)
    private LocalDateTime le;

    /** ⚠️ 2026-10-24 (« horloge-recette », R1) — sous horloge décalée, l'heure réelle au détail. */
    @Override
    public void mentionnerHeureReelle() {
        detail = HorlogeRecette.mentionner(detail, 2000);
    }
}
