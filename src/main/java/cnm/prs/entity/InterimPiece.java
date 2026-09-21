package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pièce PDF d'un intérim désigné (table {@code t_interim_piece}, V34) — la note de service ou la décision
 * qui désigne l'intérimaire, <strong>obligatoire</strong> (arbitrage Q4 du pilote, 2026-09-21).
 *
 * <p>Stockage <strong>dédié</strong>, clé primaire partagée avec {@link Interim} : {@code t_interim} est
 * lue à chaque requête d'un CC ou d'un Membre (contexte d'intérim, garde centrale, accueil « À faire ») et
 * ne doit jamais charger le binaire — même raisonnement que {@link PieceDemandeRetrait}. En base et non
 * sur le FSX, comme la note d'intérim de V11 : la désignation est un geste atomique (multipart), un
 * rollback ne doit pas laisser de fichier orphelin.</p>
 */
@Entity
@Table(name = "t_interim_piece")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InterimPiece {

    /** L'intérim propriétaire (une pièce par intérim, clé partagée). */
    @Id
    @Column(name = "ID_INTERIM", nullable = false)
    private Integer idInterim;

    /** Contenu binaire (PostgreSQL {@code bytea}). Jamais sérialisé en JSON. */
    @Column(name = "CONTENU", nullable = false)
    @JsonIgnore
    private byte[] contenu;
}
