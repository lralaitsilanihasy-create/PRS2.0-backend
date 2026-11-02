package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ V156 (demande front « appel-offres-restreint », §B3) — la liste restreinte saisie d'une version de fiche.
 *
 * @param source        {@code APPEL_CANDIDATURES}, {@code BASE_AUTORITE}, {@code PREMIER_APPEL_OFFRES}, {@code REGISTRE_ARMP} ou
 *                      {@code ORGANISME_PROFESSIONNEL}
 * @param libelleSource son libellé, servi par le serveur
 * @param candidats     dans l'ordre (le rang est la position) ; trois au moins
 * @param saisiLe       la dernière écriture ; nul en entrée
 */
public record ListeRestreinteDto(String source, String libelleSource, List<Candidat> candidats, LocalDateTime saisiLe) {

    /**
     * Un candidat : nom ou raison sociale et adresse (obligatoires), adresse électronique et compte candidat (facultatifs : l'un ou
     * l'autre ouvre la procédure en ligne à l'invité), NIF quand il est connu ; {@code rang} servi, ignoré en entrée.
     */
    public record Candidat(Integer rang, String nom, String adresse, String email, String idCandidat, String nif) {
    }
}
