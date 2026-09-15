package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ⚠️ <strong>Accueil « À faire »</strong> — réponse de {@code GET /api/dossiers/a-faire} (demande front du
 * 2026-09-14). Les gestes attendus du connecté, calculés par le serveur : des sections par geste, des lignes
 * triées par urgence, une action principale par ligne et un délai en heures ouvrées.
 *
 * <p><strong>Codes, pas de libellés</strong> : sections, gestes, modes et urgences sont des noms de constantes
 * ({@code SectionAFaire}, {@code GesteAFaire}, {@code ModeTache}, {@code UrgenceTache}) ; le front porte les
 * libellés. Toutes les clés sont toujours présentes, à {@code null} quand elles ne s'appliquent pas.</p>
 *
 * @param profil      profil du connecté
 * @param genereLe    instant du calcul (horloge du serveur)
 * @param compteurs   lignes {@code TITULAIRE} seulement ; le bloc délégation n'y entre jamais
 * @param sections    sections non vides de la liste principale, dans l'ordre du circuit
 * @param taches      lignes {@code TITULAIRE}, triées, {@code rang} à partir de 1
 * @param delegations bloc délégation (intérim, collègue, suppléance, délégation) : totaux toujours servis, lignes
 *                    seulement sur {@code ?delegations=true}
 */
public record AFaireDto(
        String profil,
        LocalDateTime genereLe,
        Compteurs compteurs,
        List<Section> sections,
        List<Tache> taches,
        Delegations delegations) {

    /**
     * Compteurs de la liste principale. Invariants : CNM, {@code aFaire = enRetard + bientot + dansLesDelais +
     * sansDelai} ; PRMP et UGPM, {@code aFaire = enPause + sansDelai}. {@code sansDelai} regroupe les urgences
     * {@code SANS_DELAI} et {@code HORS_DELAI} ; {@code enPause} et {@code suivi} comptent aussi les sections de
     * suivi ({@code EN_ATTENTE_PRMP}, {@code EN_COURS_CNM}), exclues de {@code aFaire}.
     */
    public record Compteurs(int aFaire, int enRetard, int bientot, int dansLesDelais, int sansDelai, int enPause,
            int suivi) {
    }

    /** Une section non vide : son code, son nombre de lignes, le délai standard de son étape ({@code null} sinon). */
    public record Section(String code, int total, Integer standardHeures) {
    }

    /** Total d'une section du bloc délégation. */
    public record SectionTotal(String code, int total) {
    }

    /** Bloc délégation : total, totaux par section (ordre du circuit), lignes sur demande. */
    public record Delegations(int total, List<SectionTotal> parSection, List<Tache> taches) {
    }

    /**
     * Une ligne : clé ({@code dossier.idDossier}, {@code section}). Un même dossier peut produire deux lignes pour
     * une même personne (un retrait et un visa, par exemple).
     */
    public record Tache(
            String section,
            String geste,
            List<String> gestesSecondaires,
            String mode,
            String urgence,
            int rang,
            Dossier dossier,
            Delai delai,
            Faits faits,
            Refs refs) {
    }

    /**
     * Le dossier, de quoi construire l'aperçu sans appel supplémentaire. {@code niveauNavette} et
     * {@code acteursEtapes} sont internes à la CNM : {@code null} pour la PRMP et l'UGPM (règle C2).
     */
    public record Dossier(
            Integer idDossier,
            String refeDossier,
            LocalDateTime dateSoumission,
            String idTypeDossier,
            String idSousType,
            Integer idEntiteContract,
            String libelleEntite,
            String idLocalite,
            String libelleLocalite,
            String statut,
            String statutPv,
            String niveauNavette,
            Map<String, LocalDateTime> datesEtapes,
            Map<String, String> acteursEtapes) {
    }

    /**
     * Délai de la ligne, lu dans {@code ChronometrageService.delaiCourant} (rien n'est recalculé ici).
     * {@code etape} est toujours l'étape courante du dossier. Pour une lettre ou un retrait (geste non
     * chronométré), {@code entree} est la date de la lettre ou de la demande et les champs du chronomètre
     * ({@code standardHeures}, {@code ecouleHeures}, {@code restantHeures}, {@code echeance}) sont {@code null}.
     */
    public record Delai(
            String etape,
            LocalDateTime entree,
            Integer standardHeures,
            Long ecouleHeures,
            Long restantHeures,
            LocalDateTime echeance,
            LocalDateTime pauseDepuis,
            Long pauseHeures,
            LocalDate datePrevisionnelleFin) {
    }

    /**
     * Faits utiles à l'aperçu. {@code consigneDispatch}, {@code dernierRetourNavette} et {@code partsAttendues}
     * sont internes à la CNM : {@code null} pour la PRMP et l'UGPM (règle C2).
     *
     * @param nbLignes             lignes de marché non supprimées ; {@code null} si le dossier n'en a aucune
     * @param montantTotal         somme des montants estimés de ces lignes ; {@code null} sans ligne
     * @param nbPieces             pièces jointes du dossier
     * @param idAvis               avis du PV le plus récent ; {@code null} sans PV
     * @param nbObservations       observations du périmètre figé du PV ; {@code null} s'il n'y en a pas
     * @param consigneDispatch     consigne du dispatch courant
     * @param dernierRetourNavette commentaire du dernier retour (au Membre ou au CC) du PV le plus récent
     * @param motifRetrait         motif de la demande de retrait en attente
     * @param examenEntame         vrai si un examen existe sur le dispatch courant ; {@code null} sans dispatch
     * @param partsAttendues       PV visé : rôles désignés ({@code MEMBRE}, {@code CC}) dont la part n'est pas datée
     */
    public record Faits(
            Integer nbLignes,
            BigDecimal montantTotal,
            Integer nbPieces,
            String idAvis,
            Integer nbObservations,
            String consigneDispatch,
            String dernierRetourNavette,
            String motifRetrait,
            Boolean examenEntame,
            List<String> partsAttendues) {
    }

    /**
     * Identifiants pour ouvrir l'écran qui porte le geste. {@code idDispatch} est interne à la CNM : {@code null}
     * pour la PRMP et l'UGPM. {@code idLettre} n'est renseigné que sur une ligne de lettre.
     */
    public record Refs(
            Integer idReception,
            Integer idDispatch,
            Integer idExamen,
            Integer idPv,
            Integer idLettre,
            Integer idDemandeRetrait) {
    }
}
