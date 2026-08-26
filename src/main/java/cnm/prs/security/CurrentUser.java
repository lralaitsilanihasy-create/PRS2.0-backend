package cnm.prs.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import cnm.prs.enums.ProfilUtilisateur;

/**
 * Accès à l'utilisateur authentifié (claims du jeton JWT courant).
 *
 * <p>Utilitaire utilisé par les couches service/contrôleur pour appliquer la visibilité et
 * les autorisations. La localité est la claim {@code localite} ; son absence signifie
 * « toutes localités » (Président).</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    private static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        if (auth != null && auth.getPrincipal() instanceof Jwt principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** Login (sujet du jeton). */
    public static Optional<String> login() {
        return jwt().map(Jwt::getSubject);
    }

    /** Matricule contrôleur ou identifiant PRMP. */
    public static Optional<String> ref() {
        return jwt().map(j -> j.getClaimAsString("ref"));
    }

    /** Type d'acteur : {@code CONTROLEUR} ou {@code PRMP} (claim {@code acteurType}). */
    public static Optional<String> acteurType() {
        return jwt().map(j -> j.getClaimAsString("acteurType"));
    }

    /**
     * Profil métier, ou vide si non reconnu.
     *
     * <p>⚠️ Correctif 2026-08-26 — {@code valueOf} était appelé nu : un jeton portant un rôle
     * inconnu levait {@link IllegalArgumentException} (→ 500) au lieu du « vide » promis par le
     * contrat. Le rapprochement reste <strong>strict</strong> sur le nom de la constante (celui
     * qu'émet {@code TokenService}, et dont {@code SecurityConfig} dérive l'autorité
     * {@code ROLE_<role>}) : {@link ProfilUtilisateur#resolve(String)} est délibérément écarté ici
     * — c'est un rapprochement <em>approchant</em> sur le libellé {@code tr_profile.PROFILE}, qui
     * promouvrait un rôle forgé (« SUPER-ADMIN » → ADMINISTRATEUR).</p>
     */
    public static Optional<ProfilUtilisateur> profil() {
        return jwt()
                .map(j -> j.getClaimAsString("role"))
                .filter(r -> r != null && !r.isBlank())
                .flatMap(CurrentUser::profilStrict);
    }

    /** {@code valueOf} tolérant : le rôle inconnu donne {@link Optional#empty()} (aucun privilège). */
    private static Optional<ProfilUtilisateur> profilStrict(String role) {
        try {
            return Optional.of(ProfilUtilisateur.valueOf(role));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Localité de rattachement ; vide = toutes localités (Président). */
    public static Optional<String> localite() {
        return jwt().map(j -> j.getClaimAsString("localite"));
    }

    /** Vrai si l'utilisateur voit toutes les localités (Président, ou pas de filtre). */
    public static boolean voitToutesLocalites() {
        return localite().isEmpty();
    }
}
