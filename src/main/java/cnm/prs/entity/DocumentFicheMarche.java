package cnm.prs.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
 * ⚠️ Fiche marché, lot 2a (demande front du 2026-09-23) — un document généré à la <strong>validation</strong> d'une
 * version de la fiche ({@code t_document_fiche_marche}, V38) : {@code DPAO}, {@code CCAP}, {@code AE} (ou {@code DPAC}),
 * en {@code docx} et en {@code pdf}. Clé {@code idFiche} : c'est la version qui porte ses documents. Jamais modifié,
 * jamais effacé.
 */
@Entity
@Table(name = "t_document_fiche_marche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFicheMarche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_DOCUMENT", nullable = false)
    private Integer idDocument;

    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    /** {@code DPAO} · {@code DPAC} · {@code AE} · {@code CCAP}. */
    @Column(name = "TYPE", nullable = false, length = 10)
    private String type;

    /** {@code docx} ou {@code pdf}. */
    @Column(name = "EXTENSION", nullable = false, length = 5)
    private String extension;

    @Column(name = "NOM_FICHIER", nullable = false, length = 255)
    private String nomFichier;

    @Column(name = "TAILLE_OCTETS", nullable = false)
    private Long tailleOctets;

    /** SHA-256 du contenu, en hexadécimal. */
    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Column(name = "DATE_GENERATION", nullable = false)
    private LocalDateTime dateGeneration;

    @Column(name = "CONTENU", nullable = false)
    @JsonIgnore
    private byte[] contenu;

    /** ⚠️ 2026-09-25 (V43) — rang du lot d'un document établi par lot ; {@code null} : document commun. */
    @Column(name = "LOT")
    private Integer lot;
}
