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
 * ⚠️ R4 (manuel révisé pour l'application, lot 1, §B5 ; V104, 2026-10-13) — un <strong>instantané</strong> figé à la soumission
 * d'un dossier : le JSON du DTO de lecture d'un écran ({@code SEANCE}, {@code EVALUATION}, {@code AMI}, {@code ATTRIBUTION},
 * {@code CAO}, {@code AVIS}, {@code PLAN}, {@code ACTE}, {@code DSS}) tel qu'il était servi à cet instant, son empreinte SHA-256,
 * le numéro de la soumission (1 à la soumission, +1 à chaque resoumission). Immuable (trigger).
 */
@Entity
@Table(name = "t_dossier_instantane")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DossierInstantane {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_INSTANTANE")
    private Long idInstantane;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "NUMERO_SOUMISSION", nullable = false)
    private Integer numeroSoumission;

    @Column(name = "ECRAN", nullable = false, length = 20)
    private String ecran;

    /** Le JSON du DTO de lecture, tel que servi à la soumission. */
    @Column(name = "CONTENU", nullable = false, columnDefinition = "text")
    private String contenu;

    /** SHA-256 du contenu, en hexadécimal. */
    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Column(name = "FIGE_LE", nullable = false)
    private LocalDateTime figeLe;

    @Column(name = "FIGE_PAR", length = 100)
    private String figePar;
}
