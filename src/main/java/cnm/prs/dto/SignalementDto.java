package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 3) — un <strong>signalement</strong> servi
 * à l'écran.
 *
 * <p>Deux principes du plan sont ici, dans le contrat, et pas seulement dans l'écran :</p>
 * <ul>
 *   <li><strong>un fait et une piste ne pèsent pas pareil</strong> : {@code source} vaut {@code REGLE} ou
 *       {@code IA}, et l'écran ne doit jamais les présenter de la même façon (3.f, condition 4) ;</li>
 *   <li><strong>l'écartement se lit avec son motif</strong> — c'est ce qui fait la dissuasion. Côté PRMP,
 *       en revanche, un écartement <em>de contrôleur</em> n'est pas servi : c'est une appréciation interne
 *       au contrôle, qui se dit dans le PV.</li>
 * </ul>
 *
 * @param id                identifiant du signalement
 * @param type              code de la règle ({@code FRACTIONNEMENT_COMPTE}…)
 * @param libelleRegle      libellé administrable de la règle ({@code t_regle_anomalie.LIBELLE})
 * @param gravite           {@code A_VERIFIER} ou {@code PRIORITAIRE} — jamais bloquant
 * @param source            {@code REGLE} (fait opposable, avec sa base légale) ou {@code IA} (piste)
 * @param statut            {@code OUVERT}, {@code ECARTE} ou {@code LEVE_MODIFICATION}
 * @param description       le constat, avec le texte qui le fonde
 * @param suggestion        la correction proposée, au format de l'annexe d'un PV
 * @param idDetail          la ligne visée, si le signalement n'en vise qu'une
 * @param designationLigne  désignation de cette ligne, pour l'afficher sans second appel
 * @param lignes            les lignes visées d'un signalement inter-lignes (fractionnement)
 * @param idPointCtrl       point de la grille de contrôle que le signalement éclaire
 * @param libellePointCtrl  son libellé
 * @param dateDetection     dernière détection
 * @param ecartement        l'écartement, s'il y en a un et si le lecteur a le droit de le voir
 * @param fige              vrai depuis la soumission : l'écartement ne se défait plus
 * @param dateLevee         date à laquelle le signalement a cessé de ressortir
 * @param detailLevee       ce qui a changé dans le plan et a fait taire l'alarme
 */
public record SignalementDto(
        Integer id,
        String type,
        String libelleRegle,
        String gravite,
        String source,
        String statut,
        String description,
        String suggestion,
        Integer idDetail,
        String designationLigne,
        List<LigneViseeDto> lignes,
        Integer idPointCtrl,
        String libellePointCtrl,
        LocalDateTime dateDetection,
        EcartementDto ecartement,
        boolean fige,
        LocalDateTime dateLevee,
        String detailLevee) {

    /**
     * Une ligne visée par un signalement inter-lignes.
     *
     * @param idDetail    la ligne
     * @param designation sa désignation
     * @param montant     son montant en vigueur <strong>au moment de la détection</strong> — recopié, pour
     *                    que l'explication reste lisible après modification du plan
     */
    public record LigneViseeDto(Integer idDetail, String designation, BigDecimal montant) {
    }

    /**
     * Un écartement, tel qu'il se lit de l'autre côté du circuit.
     *
     * @param typeActeur {@code PRMP} ou {@code CONTROLEUR} — qui a écarté
     * @param refActeur  sa référence ({@code ID_PRMP} ou {@code IM_CONTROLEUR})
     * @param date       quand
     * @param motif      pourquoi — c'est la pièce maîtresse : sans motif lisible, pas de dissuasion
     */
    public record EcartementDto(String typeActeur, String refActeur, LocalDateTime date, String motif) {
    }
}
