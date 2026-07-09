package cnm.prs.dto;

import java.util.List;

/**
 * Vue d'une inscription en attente de validation, pour l'Administrateur : identité, entités
 * déclarées (existantes/proposées, avec disponibilité) et métadonnées des pièces. Le contenu des
 * pièces se récupère via l'endpoint de téléchargement dédié.
 *
 * <p>Couvre les inscriptions <strong>PRMP</strong> et <strong>UGPM</strong> ({@code type}). Les
 * champs identité ({@code idPrmp}/{@code nomPrmp}/{@code prenomsPrmp}/{@code emailPrmp}) portent
 * l'identité de l'acteur (l'id/nom de l'UGPM pour une inscription UGPM). Pour une UGPM,
 * {@code idPrmpTutelle} est renseigné et {@code entitesDeclarees} est vide (l'UGPM n'a pas
 * d'entités propres) ; pour une PRMP, {@code idPrmpTutelle} est {@code null}.</p>
 */
public record InscriptionEnAttenteDto(
        String type,
        String login,
        String idPrmp,
        String nomPrmp,
        String prenomsPrmp,
        String emailPrmp,
        String idPrmpTutelle,
        List<DeclarationEntiteDto> entitesDeclarees,
        List<PieceJointeMetaDto> pieces) {
}
