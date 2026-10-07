package cnm.prs.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V79 (évaluation des offres, lot 2, §B1-§B2) — l'attribution d'un lot d'une procédure, après le rapport d'évaluation signé : son
 * état, l'offre proposée, le dossier de marché (famille {@code DDM}) soumis au contrôle de la Commission et le projet de marché
 * produit par le serveur (arbitrage Q1 du pilote).
 */
@Entity
@Table(name = "t_attribution")
@IdClass(Attribution.Cle.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Attribution {

    public static final String AU_CONTROLE = "AU_CONTROLE";

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Id
    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "ID_OFFRE_PROPOSEE", length = 36)
    private String idOffreProposee;

    @Column(name = "ID_DOSSIER")
    private Integer idDossier;

    @Column(name = "DOSSIER_CREE_LE")
    private LocalDateTime dossierCreeLe;

    @Column(name = "DOSSIER_CREE_PAR", length = 100)
    private String dossierCreePar;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PROJET_PDF")
    private byte[] projetPdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PROJET_DOCX")
    private byte[] projetDocx;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private Long idDmc;
        private Integer lot;
    }
}
