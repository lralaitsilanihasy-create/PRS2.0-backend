package cnm.prs.service;

import java.util.List;
import java.util.Optional;

import cnm.prs.dto.DelaiLegalDto;

/**
 * ⚠️ R6a (manuel révisé pour l'application, lot 2, §B1 ; arbitrage Q6 du pilote du 10/10 ; 2026-10-13) — les <strong>neuf délais de
 * la loi n° 2016-055</strong>, vérifiés dans le texte (version CNLEGIS) : une table en code, pas en base, parce que ce sont des valeurs
 * légales. Convention de décompte : le jour de départ n'est pas compté, la date limite tombe au plus tôt le 31e (46e, 11e…) jour.
 * Les autres délais (restreint, prestations intellectuelles, AMI, pré-qualification, délai aménagé…) sont des paramètres administrables
 * ({@code t_delai_parametre}), signalés et non bloquants.
 *
 * <p>⚠️ 2026-10-15 (demande front « marchés subséquents », §B0.1) — deux délais du <strong>décret n° 2019-1064</strong> (art. 10, contrat-cadre)
 * rejoignent la table, n° 10 et 11 : ce sont des valeurs réglementaires, confirmées par le pilote, et non plus le paramètre
 * {@code PUBLICITE_CONTRAT_CADRE} (30 jours « à confirmer », désactivé par V121).</p>
 */
public final class DelaisLegaux {

    public static final String PUBLICITE_AOO_NATIONAL = "PUBLICITE_AOO_NATIONAL";
    public static final String PUBLICITE_AOO_INTERNATIONAL = "PUBLICITE_AOO_INTERNATIONAL";
    public static final String QUESTIONS_CANDIDATS = "QUESTIONS_CANDIDATS";
    public static final String PUBLICITE_CONSULTATION = "PUBLICITE_CONSULTATION";
    public static final String PLAN_REPUTE_APPROUVE = "PLAN_REPUTE_APPROUVE";
    public static final String ATTENTE_SIGNATURE = "ATTENTE_SIGNATURE";
    public static final String PIECES_ATTRIBUTAIRE = "PIECES_ATTRIBUTAIRE";
    public static final String AVIS_ATTRIBUTION = "AVIS_ATTRIBUTION";
    public static final String SANS_SUITE_AVIS = "SANS_SUITE_AVIS";
    /** ⚠️ 2026-10-15 — décret n° 2019-1064, art. 10 : le contrat-cadre par appel d'offres, puis par consultation. */
    public static final String PUBLICITE_CONTRAT_CADRE_AO = "PUBLICITE_CONTRAT_CADRE_AO";
    public static final String PUBLICITE_CONTRAT_CADRE_CONSULTATION = "PUBLICITE_CONTRAT_CADRE_CONSULTATION";

    public static final String MINIMUM = "MINIMUM";
    public static final String MAXIMUM = "MAXIMUM";

    public static final List<DelaiLegalDto> TABLE = List.of(
            new DelaiLegalDto(1, PUBLICITE_AOO_NATIONAL, 30, "JOURS", MINIMUM,
                    "Publicité de l'appel d'offres ouvert national → remise des offres",
                    "date de publication de l'avis spécifique (plusieurs supports : la plus tardive)", "art. 35-II",
                    "bloquant : date limite < publication + 30 j refusée (fiche, impression de l'avis)", true),
            new DelaiLegalDto(2, PUBLICITE_AOO_INTERNATIONAL, 45, "JOURS", MINIMUM,
                    "Publicité de l'appel d'offres ouvert international → remise des offres",
                    "date de publication de l'avis spécifique", "art. 35-II",
                    "bloquant : date limite < publication + 45 j refusée (fiche, impression de l'avis)", true),
            new DelaiLegalDto(3, QUESTIONS_CANDIDATS, 10, "JOURS", MAXIMUM,
                    "Demandes de renseignements des candidats recevables jusqu'à J−10",
                    "à rebours depuis la date limite de remise des offres", "art. 35-II",
                    "fermeture automatique du formulaire de questions à J−10 (lot 2, B2)", true),
            new DelaiLegalDto(4, PUBLICITE_CONSULTATION, 10, "JOURS", MINIMUM,
                    "Consultation ouverte (sous le seuil) → remise des offres", "date d'affichage de l'avis", "art. 41-II",
                    "bloquant : date limite < affichage + 10 j refusée (fiche, impression de l'avis)", true),
            new DelaiLegalDto(5, PLAN_REPUTE_APPROUVE, 10, "JOURS_OUVRABLES", MAXIMUM,
                    "Avis de l'organe de contrôle sur le plan de passation", "dépôt du plan (horodatage de la soumission)", "art. 26-IV",
                    "à l'expiration sans réponse : plan réputé approuvé, déblocage automatique (lot 2, R6b)", false),
            new DelaiLegalDto(6, ATTENTE_SIGNATURE, 10, "JOURS_FRANCS", MINIMUM,
                    "Information des candidats non retenus → signature du marché",
                    "la plus tardive des réceptions de l'information par les non-retenus", "art. 52-I et 58",
                    "bloquant : signature au plus tôt le 11e jour (délai d'attente, existant)", true),
            new DelaiLegalDto(7, PIECES_ATTRIBUTAIRE, 15, "JOURS", MAXIMUM,
                    "Dépôt des pièces fiscales (≤ 6 mois) et sociales (≤ 3 mois) de l'attributaire",
                    "réception de la notification de l'attribution", "art. 20-I",
                    "à J+15 sans dépôt : retrait proposé (existant) ; validité des pièces à la notification (lot 2, R6c)", true),
            new DelaiLegalDto(8, AVIS_ATTRIBUTION, 30, "JOURS", MAXIMUM,
                    "Envoi de l'avis d'attribution", "notification du marché (réception par le titulaire)", "art. 53",
                    "non bloquant : alerte à J+20, tâche obligatoire avant la clôture du dossier de marché (lot 2, R6c)", false),
            new DelaiLegalDto(9, SANS_SUITE_AVIS, 5, "JOURS", MAXIMUM,
                    "Avis de l'organe de contrôle sur une déclaration sans suite", "saisine (dépôt des motifs)", "art. 55-II",
                    "relance à J+5 ; blocage maintenu jusqu'à l'avis exprès (existant ; relance : lot 2, R6c)", true),
            new DelaiLegalDto(10, PUBLICITE_CONTRAT_CADRE_AO, 10, "JOURS", MINIMUM,
                    "Contrat-cadre par appel d'offres : publicité → remise de la soumission (candidature, offres technique et financière)",
                    "date de publication de l'avis spécifique", "décret n° 2019-1064, art. 10",
                    "bloquant : date limite < publication + 10 j refusée (fiche, impression de l'avis)", true),
            new DelaiLegalDto(11, PUBLICITE_CONTRAT_CADRE_CONSULTATION, 5, "JOURS", MINIMUM,
                    "Contrat-cadre par consultation : publicité → remise de la soumission", "date d'affichage de l'avis",
                    "décret n° 2019-1064, art. 10", "bloquant : date limite < affichage + 5 j calendaires refusée (fiche, impression de l'avis)", true));

    private DelaisLegaux() {
    }

    public static Optional<DelaiLegalDto> par(String code) {
        return TABLE.stream().filter(d -> d.code().equals(code)).findFirst();
    }
}
