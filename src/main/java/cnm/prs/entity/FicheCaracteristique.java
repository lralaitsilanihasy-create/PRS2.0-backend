package cnm.prs.entity;

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

/** ⚠️ V45 (2026-09-25) — une caractéristique exigée d'un article du besoin ({@code t_fiche_caracteristique}). */
@Entity
@Table(name = "t_fiche_caracteristique")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheCaracteristique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CARACTERISTIQUE", nullable = false)
    private Integer idCaracteristique;

    @Column(name = "ID_ARTICLE", nullable = false)
    private Integer idArticle;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;

    /** « Mémoire vive ». */
    @Column(name = "LIBELLE", nullable = false, length = 300)
    private String libelle;

    /** « 8 Go au minimum ». */
    @Column(name = "EXIGENCE", nullable = false, length = 500)
    private String exigence;
}
