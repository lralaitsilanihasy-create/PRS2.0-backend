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

    /**
     * Identifiant de la localité <strong>CENTRALE</strong> (Commission Nationale des Marchés) — source
     * unique : références officielles (segment {@code CNM} au lieu de {@code CRM-<id>}), choix du modèle
     * de PV (variante centrale) et signature des lettres de renvoi.
     */
    public static final String ID_CENTRALE = "ANT";

    /** Vrai si l'identifiant désigne la localité centrale. */
    public static boolean estCentrale(String idLocalite) {
        return ID_CENTRALE.equals(idLocalite);
    }

    @Id
    @Column(name = "ID_LOCALITE", nullable = false, length = 5)
    private String idLocalite;

    @Column(name = "LIBELLE_LOCALITE", nullable = false, length = 50)
    private String libelleLocalite;

    /**
     * ⚠️ Colonne ajoutée (2026-08-03) — <strong>chef-lieu</strong> de la localité : ville où siège la
     * Commission (régionale) et lieu d'établissement porté par les documents officiels
     * (« A &lt;chef-lieu&gt;, le … »). Facultatif ; à défaut, le libellé de la localité est utilisé.
     */
    @Column(name = "CHEF_LIEU", length = 50)
    private String chefLieu;
}
