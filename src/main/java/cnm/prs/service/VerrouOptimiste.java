package cnm.prs.service;

import cnm.prs.exception.ConflitVersionException;

/**
 * Garde du <strong>verrou optimiste côté HTTP</strong> (⚠️ chantier « conflit de version », 2026-08-27,
 * cf. {@code docs/plan-conflit-version.md}).
 *
 * <p>Le verrou {@code @Version} posé au LOT 4 (migration {@code V6}) ne voit que l'entrelacement de deux
 * <em>transactions</em>. Il ne voit <strong>pas</strong> deux formulaires ouverts dans deux navigateurs :
 * tous les {@code update()} du circuit travaillent en « charger-puis-modifier » sur une entité
 * <strong>managée</strong>, donc chaque PUT repart de la version courante et deux PUT séquentiels ne se
 * heurtent jamais. C'est ce trou que cette garde ferme, en comparant la version que le client a chargée
 * (portée par le DTO) à celle réellement en base, <strong>avant la première écriture</strong>.</p>
 *
 * <p>⚠️ La comparaison doit rester <strong>explicite</strong> : écrire
 * {@code existing.setVersion(dto.getVersion())} serait un contrôle silencieusement mort — Hibernate
 * ignore l'écriture manuelle d'un champ {@code @Version} sur une entité managée.</p>
 */
public final class VerrouOptimiste {

    private VerrouOptimiste() {
    }

    /**
     * Refuse la mise à jour si le client part d'une version périmée.
     *
     * <p><strong>Version envoyée absente ({@code null}) : accepté sans contrôle</strong> — comportement
     * historique (dernier écrit gagne). C'est un choix de contrat assumé, pas un oubli : l'exiger d'emblée
     * casserait tous les clients existants d'un coup (façade {@code /api/saisies}, scripts, tests).</p>
     *
     * @param versionEnvoyee  version portée par le DTO de la requête ({@code null} accepté)
     * @param versionCourante version de l'entité chargée en base
     * @throws ConflitVersionException (HTTP 409, code {@code CONFLIT_VERSION}) si les deux diffèrent
     */
    public static void exigerVersionCourante(Integer versionEnvoyee, Integer versionCourante) {
        if (versionEnvoyee != null && !versionEnvoyee.equals(versionCourante)) {
            throw new ConflitVersionException();
        }
    }
}
