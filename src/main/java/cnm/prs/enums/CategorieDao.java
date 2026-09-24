package cnm.prs.enums;

import java.util.Arrays;

import cnm.prs.exception.BadRequestException;

/**
 * ⚠️ Fiche DAO, lot 5 (demande front du 2026-09-24) — la <strong>catégorie</strong> d'une fiche DAO, second axe du
 * référentiel à côté du type de marché. Dérivée de la nature de la ligne du plan ({@code tr_nature.CATEGORIE_DAO},
 * administrable), jamais une réponse de cadrage. Liste fermée : un enum, comme {@link TypeMarcheDao}.
 */
public enum CategorieDao {

    FOURNITURES_SERVICES("Fournitures et services"),
    TRAVAUX("Travaux et réhabilitation"),
    PRESTATIONS_INTELLECTUELLES("Prestations intellectuelles");

    private final String libelle;

    CategorieDao(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /** Toutes les catégories, en liste séparée par des virgules (valeur des colonnes {@code CATEGORIES}). */
    public static String toutes() {
        return String.join(",", Arrays.stream(values()).map(Enum::name).toList());
    }

    /** Code API → enum : vide → {@code null} ; inconnu → 400 nominatif. */
    public static CategorieDao depuisCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Catégorie de fiche DAO inconnue : « " + code
                    + " » — valeurs acceptées : FOURNITURES_SERVICES, TRAVAUX, PRESTATIONS_INTELLECTUELLES.");
        }
    }
}
