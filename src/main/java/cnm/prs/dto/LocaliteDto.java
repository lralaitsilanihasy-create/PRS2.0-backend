package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Localite}.
 *
 * <p>⚠️ Champs {@code referencement} puis {@code localite} (code max 3) <strong>retirés du contrat</strong>
 * (2026-07-17) : colonnes héritées du MLD sans aucune sémantique — jamais lues par la génération de
 * références, les documents ni les jobs ; valeurs dupliquant/dérivant la PK. Les colonnes BD sont
 * dépréciées (rendues nullables, conservées). Le contrat se réduit à <strong>id / libellé</strong>.
 * NB : le segment localité des références officielles (« CRM-ANT ») est bâti sur la
 * <strong>PK {@code idLocalite}</strong>.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocaliteDto {

    private String idLocalite;

    @NotBlank
    @Size(max = 50)
    private String libelleLocalite;

    /**
     * ⚠️ Ajouté (2026-08-03) — <strong>chef-lieu</strong> de la localité (ville de siège de la
     * Commission régionale, lieu porté par les documents officiels). Facultatif : à défaut, les
     * documents retombent sur {@code libelleLocalite}.
     */
    @Size(max = 50)
    private String chefLieu;

    /**
     * ⚠️ <strong>Dérivé serveur (lecture seule)</strong>, ajouté le 2026-09-03 : {@code true} pour la
     * localité <strong>centrale</strong> (Commission nationale, segment « CNM » des références).
     *
     * <p>Calculé au mapping depuis {@code Localite.estCentrale(idLocalite)} — <strong>pas de colonne</strong>.
     * Le front s'en sert pour les règles propres à la centrale (le pré-dispatch y relève du seul
     * Président) au lieu d'un identifiant codé en dur : si la constante change un jour côté serveur, le
     * front suit sans redéploiement coordonné. Toute valeur envoyée en écriture est ignorée.</p>
     */
    private Boolean estCentrale;
}
