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
 * <p>⚠️ Les colonnes {@code REFERENCEMENT} puis {@code LOCALITE} (code max 3) — héritées du MLD, sans
 * sémantique : jamais lues par la génération de références (segment = PK {@code ID_LOCALITE}) ni par les
 * documents ({@code LIBELLE_LOCALITE}) — sont <strong>dépréciées</strong> : retirées de l'entité et du
 * contrat API (2026-07-17), conservées en base et rendues nullables (migrations
 * {@code 2026-07-17_localite_referencement_deprecie.sql} et {@code 2026-07-17_localite_code_deprecie.sql}).</p>
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
}
