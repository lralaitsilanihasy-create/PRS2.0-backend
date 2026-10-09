package cnm.prs.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5a, §B1 et §B5 ; V98) — un acte de gestion contractuelle (dossier DGC) et le
 * marché (dossier DDM au PV favorable) qu'il concerne. Porte le rang de l'avenant, son montant, et les faits du marché que la PRMP
 * déclare quand le serveur ne les connaît pas (montant initial, catégorie, réceptions, solde).
 */
@Entity
@Table(name = "t_acte_gestion")
@Getter
@Setter
@NoArgsConstructor
public class ActeGestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ACTE", nullable = false)
    private Integer idActe;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ID_DOSSIER_MARCHE", nullable = false)
    private Integer idDossierMarche;

    @Column(name = "SOUS_TYPE", nullable = false, length = 20)
    private String sousType;

    /** Rang de l'avenant (1, 2…) ; nul hors avenant. */
    @Column(name = "RANG")
    private Integer rang;

    /** Montant HT de l'avenant (hausse positive, baisse négative) ; nul hors avenant ou sans incidence financière. */
    @Column(name = "MONTANT_HT", precision = 18, scale = 2)
    private BigDecimal montantHt;

    @Column(name = "MONTANT_INITIAL_HT", precision = 18, scale = 2)
    private BigDecimal montantInitialHt;

    @Column(name = "CATEGORIE", length = 40)
    private String categorie;

    @Column(name = "DATE_RECEPTION_PROVISOIRE")
    private LocalDate dateReceptionProvisoire;

    @Column(name = "DATE_RECEPTION_DEFINITIVE")
    private LocalDate dateReceptionDefinitive;

    @Column(name = "DATE_SOLDE")
    private LocalDate dateSolde;

    @Column(name = "CREE_LE", nullable = false)
    private LocalDateTime creeLe;

    @Column(name = "CREE_PAR", length = 50)
    private String creePar;
}
