package cnm.prs.entity;

import java.math.BigDecimal;
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
 * ⚠️ V125 (lot MS-2) — la réponse d'un titulaire à une remise en concurrence (décret n° 2019-1064, art. 27) : un devis, une facture pro
 * forma ou le document convenu au contrat-cadre, déposé en ligne ou enregistré depuis le papier (loi, art. 33) ; une par titulaire,
 * remplacée tant que l'échéance n'est pas passée ; sa conformité décidée par la CAO.
 */
@Entity
@Table(name = "t_remise_reponse")
@Getter
@Setter
@NoArgsConstructor
public class RemiseReponse {

    public static final String EN_LIGNE = "EN_LIGNE";
    public static final String PAPIER = "PAPIER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_REMISE", nullable = false)
    private Long idRemise;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "NATURE", nullable = false, length = 30)
    private String nature;

    @Column(name = "VOIE", nullable = false, length = 10)
    private String voie;

    @Column(name = "MONTANT_HT", nullable = false, precision = 18, scale = 2)
    private BigDecimal montantHt;

    @Column(name = "MONTANT_TTC", precision = 18, scale = 2)
    private BigDecimal montantTtc;

    @Column(name = "DELAI", length = 100)
    private String delai;

    @Column(name = "NOM", nullable = false, length = 255)
    private String nom;

    @Column(name = "FORMAT", nullable = false, length = 100)
    private String format;

    @Column(name = "TAILLE", nullable = false)
    private Long taille;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU", nullable = false)
    private byte[] contenu;

    @Column(name = "RECUE_LE", nullable = false)
    private LocalDateTime recueLe;

    @Column(name = "DEPOSEE_PAR", length = 100)
    private String deposeePar;

    @Column(name = "CONFORME")
    private Boolean conforme;

    @Column(name = "MOTIF")
    private String motif;
}
