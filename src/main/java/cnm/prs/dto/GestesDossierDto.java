package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ <strong>Page dossier</strong> — réponse de {@code GET /api/dossiers/{id}/gestes} (refonte ergonomique, lot
 * L4-B1, plan du 2026-09-15 §6). Ce que le connecté peut faire sur <strong>ce</strong> dossier, et le délai de son
 * étape en cours : le calcul de l'accueil « À faire » rejoué sur un seul dossier, rien de recalculé à part.
 *
 * <p>Toutes les clés sont toujours présentes, à {@code null} quand elles ne s'appliquent pas, comme
 * {@link AFaireDto}.</p>
 *
 * @param idDossier     le dossier demandé
 * @param profil        profil du connecté
 * @param genereLe      instant du calcul (horloge du serveur)
 * @param etapeCourante délai de l'étape en cours ; {@code null} sur un statut hors des statuts actifs de l'appelant
 *                      (CLOTURE, RETIRE, REMPLACE, PV_SIGNE ; BROUILLON pour un contrôleur). Servi même quand
 *                      {@code taches} est vide
 * @param taches        <strong>toutes</strong> les lignes du connecté sur ce dossier, titulaire et non titulaire,
 *                      de la forme exacte de {@link AFaireDto.Tache} : lignes {@code TITULAIRE} d'abord, puis les
 *                      autres, chaque groupe dans l'ordre d'« À faire » ; {@code rang} à partir de 1 sur toute la
 *                      liste
 */
public record GestesDossierDto(
        Integer idDossier,
        String profil,
        LocalDateTime genereLe,
        EtapeCourante etapeCourante,
        List<AFaireDto.Tache> taches) {

    /**
     * Étape en cours du dossier, indépendamment des gestes du connecté.
     *
     * @param urgence mêmes seuils que les lignes chronométrées : {@code EN_RETARD}, {@code BIENTOT},
     *                {@code DANS_LES_DELAIS} ; {@code SANS_DELAI} si l'entrée est inconnue ; {@code EN_PAUSE} sur un
     *                statut suspensif ; {@code HORS_DELAI} pour un brouillon ; jamais {@code SUIVI}
     * @param delai   {@link AFaireDto.Delai} lu dans {@code ChronometrageService.delaiCourant}, champs du chronomètre
     *                compris
     */
    public record EtapeCourante(String urgence, AFaireDto.Delai delai) {
    }
}
