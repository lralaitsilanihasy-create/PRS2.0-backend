package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ V50 (2026-09-27, remise électronique, §B4) — les paramètres internes d'une procédure, servis au <strong>seul
 * titulaire</strong> du rôle « Responsable de la procédure » ({@code GET /api/fiches-marche/{idDmc}/parametres-internes}).
 *
 * @param idDmc             la procédure (DMC)
 * @param membresCommission INT-SE-01 — les membres détenteurs d'une part de clé
 * @param nombreParts       INT-SE-02 — calculé : la taille de {@code membresCommission}
 * @param quorum            INT-SE-03 — quorum de déchiffrement ; proposé depuis {@code FICHE_SE_QUORUM_DEFAUT} tant que rien
 *                          n'est enregistré
 * @param dateCeremonie     INT-SE-04 — date et heure de la cérémonie des clés
 * @param responsable       INT-SE-05 — le titulaire du rôle (automatique), {@code null} sans titulaire
 * @param etat              {@code COMPLETS} (membres ≥ 2, quorum et date renseignés, règles 6 et 8 satisfaites) ou
 *                          {@code INCOMPLETS} ; {@code ABSENTS} tant que rien n'est enregistré
 * @param anomalies         ce qui manque ou ce que les règles 6 et 8 refusent, {@code [{ regle, message }]}
 * @param journal           le journal dédié, avec les valeurs (Q7), du plus ancien au plus récent
 */
public record ParametresInternesDto(Long idDmc, List<CompteDesignableDto> membresCommission, int nombreParts,
        Integer quorum, LocalDateTime dateCeremonie, ResponsableProcedureDto responsable, String etat,
        List<Anomalie> anomalies, List<EntreeJournal> journal,
        /** ⚠️ V66 (lot 2, §B1) — le dépositaire de la part de secours et l'état de cette part ; hors {@code nombreParts}. */
        CeremonieDto.PartDeSecours partDeSecours,
        /** ⚠️ V66 (lot 2, §B3) — les avertissements, distincts des anomalies qui restent les refus : {@code SE_QUORUM_MARGE}. */
        List<Anomalie> avertissements) {

    public record Anomalie(String regle, String message) {
    }

    public record EntreeJournal(LocalDateTime date, String acteur, String nomActeur, String champ, String ancienneValeur,
            String nouvelleValeur) {
    }
}
