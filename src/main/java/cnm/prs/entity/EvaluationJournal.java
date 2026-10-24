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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V76 (§B7) — le journal de l'évaluation : chaque décision et chaque réouverture, avec son auteur et son détail. */
@Entity
@EntityListeners(MentionHorlogeRecette.class)
@Table(name = "t_evaluation_journal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationJournal implements MentionHorlogeRecette.Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "ACTEUR", length = 100)
    private String acteur;

    @Column(name = "ACTION", nullable = false, length = 30)
    private String action;

    @Column(name = "DETAIL")
    private String detail;

    /** ⚠️ 2026-10-24 (« horloge-recette », R1) — sous horloge décalée, l'heure réelle au détail. */
    @Override
    public void mentionnerHeureReelle() {
        detail = HorlogeRecette.mentionner(detail, 0);
    }
}
