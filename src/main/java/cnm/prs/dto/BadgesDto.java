package cnm.prs.dto;

/**
 * ⚠️ Audit front (2026-08-16) — badges de menu agrégés : les compteurs du rôle du connecté en
 * <strong>un seul appel</strong> ({@code GET /api/kpis/badges}), à la place du rejeu des endpoints de
 * liste complets pour lire des {@code .length}.
 *
 * @param profil    profil du connecté ({@code PRMP}, {@code PRESIDENT}, {@code CHEF_COMMISSION},
 *                  {@code SECRETAIRE}, {@code MEMBRE}, {@code VERIFICATEUR}, {@code ASSISTANT_CONTROLEUR},
 *                  {@code CHARGE_PUBLICATION}, {@code ADMINISTRATEUR} — {@code null} si non reconnu)
 * @param compteurs l'objet compteurs du rôle — les <strong>mêmes DTOs</strong> que les endpoints
 *                  {@code /api/kpis/mes-compteurs*} existants ({@code CompteursPrmpDto},
 *                  {@code CompteursDto} pour Président/CC, {@code CompteursSecretaireDto}, …) ;
 *                  objet vide si le profil n'a pas de compteurs
 * @param aFaire    ⚠️ 2026-09-15 — badge de l'accueil « À faire » : {@code compteurs.aFaire} de
 *                  {@code GET /api/dossiers/a-faire}, calculé par le même service (lignes titulaires, hors suivi et
 *                  hors bloc délégation) ; {@code null} pour l'Administrateur, le Chargé de publication et un profil
 *                  non reconnu
 */
public record BadgesDto(String profil, Object compteurs, Integer aFaire) {
}
