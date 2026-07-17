package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code tr_localite}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 *
 * <p>⚠️ La colonne {@code REFERENCEMENT} (héritée du MLD, sans sémantique — jamais lue par la génération
 * de références ni les documents) est <strong>dépréciée</strong> : retirée de l'entité et du contrat API
 * (2026-07-17), conservée en base et rendue nullable (migration
 * {@code 2026-07-17_localite_referencement_deprecie.sql}).</p>
 */
@Entity
@Table(name = "tr_localite")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Localite {

    @Id
    @Column(name = "ID_LOCALITE", nullable = false, length = 5)
    private String idLocalite;

    @Column(name = "LIBELLE_LOCALITE", nullable = false, length = 50)
    private String libelleLocalite;

    @Column(name = "LOCALITE", nullable = false, length = 3)
    private String localite;
}
