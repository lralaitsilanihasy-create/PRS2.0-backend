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

/**
 * ⚠️ V61 (demande front du 2026-10-03, pièces de l'offre des travaux, §B1.2) — une <strong>pièce exigée</strong> d'une
 * version de fiche DAO de travaux ({@code t_fiche_piece}) : rubrique ({@code ADMINISTRATIVE}, 2° de la clause 6.2 du
 * DPAO-T, ou {@code OFFRE}, 1°), numéro donné par le DAO, libellé, forme, ancienneté maximale en mois, une par lot, modèle.
 */
@Entity
@Table(name = "t_fiche_piece")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FichePiece {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PIECE", nullable = false)
    private Integer idPiece;

    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;

    @Column(name = "RUBRIQUE", nullable = false, length = 20)
    private String rubrique;

    @Column(name = "NUMERO", length = 10)
    private String numero;

    @Column(name = "LIBELLE", nullable = false, length = 300)
    private String libelle;

    @Column(name = "FORME", length = 200)
    private String forme;

    @Column(name = "ANCIENNETE_MAX_MOIS")
    private Integer ancienneteMaxMois;

    @Column(name = "PAR_LOT", nullable = false)
    private boolean parLot;

    @Column(name = "MODELE", length = 200)
    private String modele;
}
