package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ 2026-11-01 (demande front « avis-specifique-public-donnees », §B1) — les mentions de l'avis spécifique <strong>publié</strong>, pour
 * l'espace candidat : lues sur la version de la fiche de la dernière impression (la même que le PDF public), avec leur valeur telle que
 * l'avis imprimé l'écrit. {@code numero} : celui imprimé à l'avis ({@code B02-OE-01} pour un contrat-cadre, puis {@code B02-OB-03}).
 * Sans les contrôles de la Commission sur le gabarit, sans les mentions de publication (arbitrage Q2).
 */
public record AvisPublicDto(Long idDmc, Integer versionFiche, String gabarit, String numero, String autoriteContractante,
        List<Mention> mentions) {

    /** Une mention du modèle type, dans l'ordre du manuel ; {@code valeur} nulle si elle n'est pas renseignée. */
    public record Mention(String code, String libelle, boolean sansObjet, String valeur) {
    }
}
