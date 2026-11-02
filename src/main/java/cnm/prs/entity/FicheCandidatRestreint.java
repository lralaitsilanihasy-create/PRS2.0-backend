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

/** ⚠️ V156 (« appel-offres-restreint », §B3) — un candidat de la liste restreinte saisie d'une version de fiche, à son rang. */
@Entity
@Table(name = "t_fiche_candidat_restreint")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheCandidatRestreint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Integer id;

    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "RANG", nullable = false)
    private Integer rang;

    @Column(name = "NOM", nullable = false, length = 255)
    private String nom;

    @Column(name = "ADRESSE", nullable = false, length = 1000)
    private String adresse;

    @Column(name = "EMAIL", length = 150)
    private String email;

    @Column(name = "ID_CANDIDAT", length = 10)
    private String idCandidat;

    @Column(name = "NIF", length = 30)
    private String nif;
}
