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

/**
 * ⚠️ V65 (demande front du 2026-10-04, soumission en ligne, lot 1c, §B8) — un <strong>retrait du DAO</strong> : un
 * document téléchargé par un candidat (compte, entreprise si déclarée, document, version de la fiche, date).
 */
@Entity
@Table(name = "t_retrait_dao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetraitDao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_RETRAIT", nullable = false)
    private Integer idRetrait;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "ID_ENTREPRISE")
    private Integer idEntreprise;

    @Column(name = "CODE_DOCUMENT", nullable = false, length = 255)
    private String codeDocument;

    @Column(name = "VERSION_FICHE", nullable = false)
    private Integer versionFiche;

    @Column(name = "DATE_RETRAIT", nullable = false)
    private LocalDateTime dateRetrait;
}
