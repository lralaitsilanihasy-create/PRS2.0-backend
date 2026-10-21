package cnm.prs.entity;

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
 * ⚠️ 2026-10-21 (demande front « modele-pv-page », §B3 ; V139) — une image des pages de modèles de PV : PNG ou JPEG de 2 Mo au
 * plus, type et dimensions lus sur son contenu. {@code code} nomme une image fournie par l'application ({@code EMBLEME}).
 */
@Entity
@Table(name = "t_image_modele_pv")
@Getter
@Setter
@NoArgsConstructor
public class ImageModelePv {

    public static final String EMBLEME = "EMBLEME";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_IMAGE", nullable = false)
    private Long idImage;

    @Column(name = "CODE", length = 30)
    private String code;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU", nullable = false, columnDefinition = "bytea")
    private byte[] contenu;

    @Column(name = "LARGEUR", nullable = false)
    private Integer largeur;

    @Column(name = "HAUTEUR", nullable = false)
    private Integer hauteur;

    @Column(name = "TAILLE", nullable = false)
    private Long taille;

    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Column(name = "DEPOSEE_LE", nullable = false)
    private LocalDateTime deposeeLe;

    @Column(name = "DEPOSEE_PAR", length = 100)
    private String deposeePar;
}
