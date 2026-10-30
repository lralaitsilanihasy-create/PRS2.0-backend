package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V154 (2026-10-30, demande front « pieces-ddm-donnees », §B2 ; décision du pilote sur Q1) — la preuve de la première publication de
 * l'avis, déposée une fois sur la procédure : le scan du journal ({@link #JOURNAL}) ou, en prestations intellectuelles, des lettres
 * d'invitation avec accusé ({@link #LETTRES_INVITATION}), et ses mentions. Une nouvelle publication remplace la précédente.
 */
@Entity
@Table(name = "t_publication_avis")
@Getter
@Setter
@NoArgsConstructor
public class PublicationAvis {

    public static final String JOURNAL = "JOURNAL";
    public static final String LETTRES_INVITATION = "LETTRES_INVITATION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PUBLICATION")
    private Long idPublication;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "NATURE", nullable = false, length = 20)
    private String nature;

    @Column(name = "JOURNAL", length = 200)
    private String journal;

    @Column(name = "NUMERO", length = 50)
    private String numero;

    @Column(name = "DATE_PARUTION", nullable = false)
    private LocalDate dateParution;

    @Column(name = "NOM_FICHIER", nullable = false, length = 255)
    private String nomFichier;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU", nullable = false)
    private byte[] contenu;

    @Column(name = "TAILLE", nullable = false)
    private Long taille;

    @Column(name = "DEPOSE_PAR", length = 100)
    private String deposePar;

    @Column(name = "DEPOSE_LE", nullable = false)
    private LocalDateTime deposeLe;

    @Column(name = "REMPLACEE_LE")
    private LocalDateTime remplaceeLe;
}
