package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_document_public}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_document_public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPublic {

    @Id
    @Column(name = "ID_DOC_PUBLIC", nullable = false)
    private Integer idDocPublic;

    @Column(name = "ID_PUBLICATION", nullable = false)
    private Integer idPublication;

    @Column(name = "TYPE_DOC", length = 30)
    private String typeDoc;

    @Column(name = "LIBELLE_DOC", length = 200)
    private String libelleDoc;

    @Column(name = "CHEMIN_FICHIER", length = 500)
    private String cheminFichier;

    @Column(name = "FORMAT", length = 10)
    private String format;

    @Column(name = "TAILLE_OCTETS")
    private Long tailleOctets;

    @Column(name = "DATE_DEPOT")
    private LocalDateTime dateDepot;

    @Column(name = "HASH_SHA256", length = 64)
    private String hashSha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PUBLICATION", insertable = false, updatable = false)
    @JsonIgnore
    private Publication publication;
}
