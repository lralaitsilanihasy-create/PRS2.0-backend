package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ⚠️ Versions archivées (demande pilote du 2026-09-06) — en-tête d'une version archivée d'un dossier,
 * telle que listée par {@code GET /api/dossiers/{id}/versions-archivees}.
 *
 * <p>Le numéro de la <strong>version courante</strong> (celle du dossier) n'est pas dans la liste : il
 * vaut « nombre de versions archivées + 1 », le front pose lui-même le badge « version courante ».</p>
 *
 * @param idDossier     le dossier dont c'est une version
 * @param numero        numéro d'ordre d'archivage (1, 2, …) — clé de {@code /versions-archivees/{numero}}
 * @param origine       {@code RECTIFICATION} (extensible à {@code MISE_A_JOUR}, cf. {@link cnm.prs.enums.OrigineVersion})
 * @param cycle         itération de rectification (resoumissions + 1 au moment du gel)
 * @param dateVersion   date du gel (premier PUT du cycle qui a remplacé cette version)
 * @param idPrmpAuteur  PRMP opératrice (celle du jeton, ou la PRMP de tutelle d'un agent UGPM)
 * @param nomAuteur     « Prénoms Nom » de la PRMP opératrice, figé
 * @param auteur        login réel de l'auteur du geste
 * @param nbLignes      nombre de lignes de marché figées
 * @param exercice      en-tête du PPM au moment du gel ({@code null} pour une version reprise d'avant la V18)
 * @param reference     idem
 * @param signataire    idem
 * @param dateSignature idem
 */
public record VersionArchiveeDto(
        Integer idDossier,
        Integer numero,
        String origine,
        Integer cycle,
        LocalDateTime dateVersion,
        String idPrmpAuteur,
        String nomAuteur,
        String auteur,
        Integer nbLignes,
        Integer exercice,
        String reference,
        String signataire,
        LocalDate dateSignature) {
}
