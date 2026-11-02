package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ V155 (demande front « appel-offres-restreint », §B2) — le motif du recours à l'appel d'offres restreint d'une version de fiche.
 *
 * @param motif         {@code URGENCE_AVEREE}, {@code CONFIDENTIEL}, {@code PRESTATAIRE_DEFAILLANT} ou {@code PETIT_NOMBRE}
 * @param libelle       le libellé du cas (« Urgence avérée »…), servi par le serveur
 * @param justification le texte libre de la PRMP
 * @param saisiLe       la dernière écriture ; nul en entrée
 */
public record MotifRestreintDto(String motif, String libelle, String justification, LocalDateTime saisiLe) {
}
