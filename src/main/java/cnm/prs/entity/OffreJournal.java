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

/** ⚠️ V68 (lot 3, §B6) — le journal des offres : création, morceaux, scellement, remplacement, retrait, purge. Sans contenu. */
@Entity
@Table(name = "t_offre_journal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OffreJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_CANDIDAT", length = 10)
    private String idCandidat;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "ACTION", nullable = false, length = 30)
    private String action;

    @Column(name = "DETAIL")
    private String detail;
}
