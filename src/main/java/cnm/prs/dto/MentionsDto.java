package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ R3c (manuel révisé pour l'application, lot 1, §B3 ; 2026-10-13) — réponse de
 * {@code GET /api/fiches-marche/{idDmc}/avis-specifique/mentions} et {@code …/lettres-invitation/mentions} : les mentions du
 * modèle type, vérifiées sur le gabarit et sur le document rendu pour la version courante de la fiche.
 *
 * @param gabarit         le modèle vérifié ({@code AVIS-F}, {@code AVIS-T}, {@code LETTRE-PI}, {@code LETTRE-AOR})
 * @param gabaritComplet  chaque mention figure au gabarit
 * @param documentComplet chaque mention figure au gabarit et est renseignée sur la fiche (ou sans objet pour elle)
 * @param mentions        le détail, dans l'ordre du manuel
 */
public record MentionsDto(String gabarit, boolean gabaritComplet, boolean documentComplet, List<Mention> mentions) {

    /**
     * @param presenteAuGabarit la mention figure au gabarit (jeton ou phrase)
     * @param renseignee        la valeur rendue n'est pas en pointillés ; {@code null} si la mention est absente du gabarit ou sans objet
     * @param sansObjet         la mention ne s'applique pas à cette fiche (lot unique, pas de visite des lieux…)
     * @param detail            l'écart nommé, ou {@code null}
     * @param valeur            ⚠️ 2026-10-18 (« avis-specifique-en-donnees », §B1, Q2) — la valeur telle que le document l'imprime
     *                          (« lot unique indivisible », « garantie de soumission n'est pas requise » compris) ; {@code null}
     *                          en pointillés, absente du gabarit ou sans rien d'imprimé
     */
    public record Mention(String code, String libelle, boolean presenteAuGabarit, Boolean renseignee, boolean sansObjet, String detail,
            String valeur) {
    }
}
