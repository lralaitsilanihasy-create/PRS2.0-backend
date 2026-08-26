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
 * ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — PÉRIMÈTRE FIGÉ des observations transmises
 * à la PRMP : snapshot des observations de l'EXAMEN telles qu'arrêtées dans le PV (lignes « Au lieu
 * de / Lire » des points non conformes + observations des pièces non conformes), généré à la
 * signature du PV FAVR. Ce périmètre ne peut être élargi à aucun stade du circuit de rectification,
 * par aucun acteur (toute décision hors périmètre est rejetée côté backend). Le statut courant d'une
 * observation (ÉMISE / LEVÉE / MAINTENUE) se déduit de son historique {@link SuiviObservation}
 * (aucun événement = ÉMISE ; LEVÉE est définitive — « levée = acquise »).
 */
@Entity
@Table(name = "t_observation_pv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObservationPv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_OBSERVATION_PV", nullable = false)
    private Integer idObservationPv;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** PV signé (FAVR) dont les observations constituent le périmètre. */
    @Column(name = "ID_PV", nullable = false)
    private Integer idPv;

    /** Origine : {@code POINT} (grille de contrôle) ou {@code PIECE} (pièce jointe). */
    @Column(name = "SOURCE", nullable = false, length = 10)
    private String source;

    /** Ligne d'observation d'origine ({@code t_observation_controle.ID_OBSERVATION}) — POINT seulement. */
    @Column(name = "ID_OBSERVATION_CTRL")
    private Integer idObservationCtrl;

    /** Résultat de pièce d'origine ({@code t_examen_piece.ID_EXAMEN_PIECE}) — PIECE seulement. */
    @Column(name = "ID_EXAMEN_PIECE")
    private Integer idExamenPiece;

    /** Libellé FIGÉ de l'observation (contexte + demande), tel qu'arrêté au PV. */
    @Column(name = "LIBELLE", length = 1000, nullable = false)
    private String libelle;

    @Column(name = "ORDRE")
    private Integer ordre;
}
