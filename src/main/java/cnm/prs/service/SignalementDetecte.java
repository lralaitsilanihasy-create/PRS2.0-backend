package cnm.prs.service;

import java.math.BigDecimal;
import java.util.List;

import cnm.prs.enums.GraviteSignalement;
import cnm.prs.enums.TypeSignalement;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2) — ce qu'une <strong>règle
 * produit</strong>, avant tout enregistrement.
 *
 * <p>Une règle ne touche pas la base : elle rend des constats, et {@link PreControlePpmService} les
 * rapproche de ce qui est déjà enregistré. C'est ce qui rend les règles testables une par une, et ce qui
 * garantit qu'une exécution ne perd jamais un écartement motivé.</p>
 *
 * @param type        la règle qui a détecté
 * @param gravite     sévérité de ce constat-ci — une règle peut relever sa gravité par défaut (le cumul
 *                    d'un fractionnement qui change la procédure devient prioritaire)
 * @param cle         <strong>identité stable</strong> du constat dans son PPM : elle ne doit dépendre que
 *                    de la règle et de l'objet visé, jamais du contenu du message ni d'un numéro de ligne
 *                    volatil — c'est par elle qu'une nouvelle exécution retrouve un signalement déjà
 *                    écarté, avec son motif
 * @param idDetail    la ligne visée quand le constat n'en vise qu'une, {@code null} sinon
 * @param lignes      les lignes visées d'un constat <strong>inter-lignes</strong> (fractionnement), avec
 *                    leur montant du moment ; vide pour un constat d'une seule ligne
 * @param description le constat, en français, <strong>avec le texte qui le fonde</strong> : c'est ce qui
 *                    le rend opposable
 * @param suggestion  la correction proposée, rédigée comme l'annexe d'un PV (« Au lieu de : … Lire : … »)
 *                    pour que le contrôleur puisse la reprendre, la modifier ou l'ignorer
 */
public record SignalementDetecte(
        TypeSignalement type,
        GraviteSignalement gravite,
        String cle,
        Integer idDetail,
        List<LigneVisee> lignes,
        String description,
        String suggestion) {

    /** Une ligne visée par un constat inter-lignes, avec son montant en vigueur au moment de la détection. */
    public record LigneVisee(Integer idDetail, BigDecimal montant) {
    }

    /** Constat portant sur une seule ligne. */
    public static SignalementDetecte surLigne(TypeSignalement type, GraviteSignalement gravite, String cle,
            Integer idDetail, String description, String suggestion) {
        return new SignalementDetecte(type, gravite, cle, idDetail, List.of(), description, suggestion);
    }

    /** Constat inter-lignes : aucune ligne unique, mais la liste de celles qu'il vise. */
    public static SignalementDetecte surPlusieursLignes(TypeSignalement type, GraviteSignalement gravite,
            String cle, List<LigneVisee> lignes, String description, String suggestion) {
        return new SignalementDetecte(type, gravite, cle, null, List.copyOf(lignes), description, suggestion);
    }
}
