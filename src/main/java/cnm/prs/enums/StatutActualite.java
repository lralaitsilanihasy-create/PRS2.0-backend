package cnm.prs.enums;

/**
 * Statuts d'une actualité ({@code t_actualite.STATUT}) — spec « Actualités » du 2026-08-18.
 *
 * <p>{@code INACTIF} à la création (activation = acte délibéré de l'Administrateur) ;
 * {@code ACTIF}/{@code INACTIF} par le PUT ; {@code ARCHIVE} par le DELETE (archivage logique,
 * jamais de suppression physique) ou automatiquement à l'expiration.</p>
 */
public enum StatutActualite {
    ACTIF,
    INACTIF,
    ARCHIVE
}
