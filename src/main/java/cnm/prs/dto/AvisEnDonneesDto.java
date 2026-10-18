package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ 2026-10-18 (demande front « avis-specifique-en-donnees », §B1) — le projet d'avis spécifique (ou de lettre d'invitation) d'un
 * dossier né d'une fiche validée, en données ({@code GET /api/dossiers/{id}/avis-specifique}) : les mentions du modèle type, dans
 * l'ordre du manuel, avec leur valeur telle que le document l'imprime, sur la version dont le PDF est joint au dossier (la version
 * soumise, à défaut la dernière validée). Le PDF reste la pièce.
 *
 * @param idDossier            le dossier
 * @param idDmc                la fiche
 * @param versionFiche         la version lue
 * @param gabarit              {@code AVIS-F}, {@code AVIS-T}, {@code LETTRE-PI} ou {@code LETTRE-AOR}
 * @param lettre               une lettre d'invitation (prestations intellectuelles, appel d'offres restreint) plutôt qu'un avis
 * @param autoriteContractante {@code B01-AC-01}, ou {@code null}
 * @param numero               le numéro imprimé sous le titre ({@code B02-OB-03}), ou {@code null}
 * @param documentComplet      chaque mention figure au gabarit et est renseignée (ou sans objet)
 * @param mentions             le détail ; {@code valeur} nulle pour une mention non renseignée (Q2 : l'écran écrit « à compléter »)
 */
public record AvisEnDonneesDto(Integer idDossier, Long idDmc, Integer versionFiche, String gabarit, boolean lettre,
        String autoriteContractante, String numero, boolean documentComplet, List<MentionsDto.Mention> mentions) {
}
