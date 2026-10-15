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
 * ⚠️ V118 (2026-10-15, demande front « pièces de l'AMI et publication de l'AGPM », §B2) — la publication de l'AGPM déposée sur un plan
 * de passation contrôlé : le scan du journal et ses mentions (journal, numéro, date de parution, référence de l'AGPM). Une nouvelle
 * publication remplace la précédente ({@code remplaceeLe} posé).
 */
@Entity
@Table(name = "t_publication_agpm")
@Getter
@Setter
@NoArgsConstructor
public class PublicationAgpm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PUBLICATION")
    private Long idPublication;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "JOURNAL", nullable = false, length = 200)
    private String journal;

    @Column(name = "NUMERO", length = 50)
    private String numero;

    @Column(name = "DATE_PARUTION", nullable = false)
    private LocalDate dateParution;

    @Column(name = "REFERENCE_AGPM", nullable = false, length = 100)
    private String referenceAgpm;

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
