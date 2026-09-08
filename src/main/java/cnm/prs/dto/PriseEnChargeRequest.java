package cnm.prs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Null;

/**
 * Corps de la prise en charge d'une etape — chronometrage des delais, 2026-09-01.
 *
 * <p>⚠️ L'unite est passee du JOUR a l'HEURE ouvree le 2026-09-02 (8 h = 1 jour ouvre). Un client resté
 * sur {@code previsionJours} n'est PAS silencieusement accepte : le champ est declare POUR ETRE
 * INTERDIT, et la reponse est un 400 qui le nomme. Un refus clair vaut mieux qu'une valeur prise pour ce
 * qu'elle n'est pas — 5 « jours » lus comme 5 heures fausseraient la date annoncee a la PRMP sans que
 * personne le voie.</p>
 *
 * <p>⚠️ <strong>Demande pilote (2026-09-08) — la prévision devient OPTIONNELLE.</strong> « Prendre en
 * charge » ne sert qu'à <em>démarrer le chronomètre</em> : absente, la prévision est celle du
 * <strong>référentiel administrable</strong> de l'étape, et l'occurrence est marquée
 * {@code previsionStandard = true}. Le corps entier peut donc manquer. Une valeur explicite reste
 * acceptée — elle vaut alors prévision <em>saisie</em>, et c'est cette distinction que porte le
 * drapeau.</p>
 *
 * <p>La borne {@code @Min(1)} demeure : ce qui change, c'est qu'on peut <strong>ne rien dire</strong> ;
 * dire « zéro heure » reste une erreur, pas un silence.</p>
 *
 * <p>⚠️ <strong>{@code previsionJours} reste explicitement refusé</strong> (2026-09-08). Tant que
 * {@code previsionHeures} était obligatoire, un client resté à l'ancienne unité était refusé
 * <em>par ricochet</em> : sa propriété inconnue était ignorée, le champ requis manquait, 400. En rendant
 * la prévision facultative, ce refus disparaissait — ses « 5 jours » auraient été silencieusement
 * remplacés par le standard, sans que personne le voie. Le champ est donc déclaré <strong>pour être
 * interdit</strong> : le refus qui était un effet de bord devient une règle qui se lit.</p>
 *
 * @param previsionHeures prevision du porteur, en HEURES ouvrees (au moins 1) ; {@code null} = standard
 * @param previsionJours  ancienne unite, abandonnee le 2026-09-02 — doit rester absente
 */
public record PriseEnChargeRequest(
        @Min(value = 1, message = "La prévision doit valoir au moins 1 heure ouvrée.")
        Integer previsionHeures,

        @Null(message = "La prévision se saisit en HEURES ouvrées (previsionHeures), plus en jours : "
                + "8 heures valent 1 jour ouvré.")
        Integer previsionJours) {
}
