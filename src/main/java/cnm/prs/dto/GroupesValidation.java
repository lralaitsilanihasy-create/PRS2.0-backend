package cnm.prs.dto;

/**
 * Groupes de validation {@code jakarta.validation} partagés par les DTO.
 *
 * <p>Existe pour une raison précise : sur {@code PATCH /api/{ppms,marches}/{id}/rectifier}, les
 * <strong>champs d'identité</strong> de la ressource (rattachement au dossier / au PPM) sont
 * <strong>figés côté serveur</strong> et n'ont donc pas à être envoyés. Historiquement, cela avait
 * été obtenu en retirant purement et simplement {@code @Valid} du corps de la rectification — ce qui
 * désactivait <em>toutes</em> les contraintes, y compris celles portant sur le contenu
 * ({@code @Size}, contraintes numériques). Un contenu trop long partait alors jusqu'à la base et
 * revenait en 409 opaque, là où le même contenu en {@code PUT} donnait un 400 nommant le champ.</p>
 *
 * <p>Le découpage en groupes rétablit la symétrie : les contraintes de <strong>contenu</strong>
 * restent dans le groupe {@code Default} (donc validées partout, rectification comprise), les
 * contraintes d'<strong>identité</strong> partent dans {@link Identite}, que seules la création et
 * la mise à jour complète activent.</p>
 */
public final class GroupesValidation {

    private GroupesValidation() {
    }

    /**
     * Champs d'<strong>identité</strong> d'une ressource : rattachement figé, exigé à la création
     * ({@code POST}) et à la mise à jour complète ({@code PUT}), <strong>non exigé</strong> en
     * rectification ({@code PATCH .../rectifier}) où le serveur conserve la valeur existante.
     */
    public interface Identite {
    }
}
