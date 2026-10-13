package cnm.prs.service;

import java.util.Map;

import cnm.prs.entity.Dossier;

/**
 * ⚠️ R5 (manuel révisé pour l'application, lot 1, §B6 ; 2026-10-13) — un <strong>contrôle automatique</strong> du manuel (type A) :
 * une règle codée, sa référence au manuel, son caractère bloquant, les familles de dossier qu'elle concerne, et son évaluation sur
 * le dossier soumis. Chaque règle est un composant Spring : {@link ControlesService} les joue toutes à la soumission.
 */
public interface ControleAutomatique {

    /** Le code stable de la règle ({@code DOCUMENTS_EVALUATION_SIGNES}). */
    String code();

    /** La référence du manuel (« 2-III-A P5 »). */
    String reference();

    String libelle();

    /** Une non-conformité refuse la soumission (400 {@code CONTROLES_BLOQUANTS}) ; sinon elle est signalée et enregistrée. */
    boolean bloquant();

    /** La règle s'applique-t-elle à cette famille de dossier ? Sinon rien n'est évalué ni enregistré. */
    boolean concerne(Dossier dossier);

    /** L'évaluation : le résultat et les valeurs constatées (ce que la PRMP ou le contrôleur doivent lire). */
    Resultat evaluer(Dossier dossier, ControlesContexte contexte);

    /** @param resultat {@code CONFORME}, {@code NON_CONFORME} ou {@code NON_APPLICABLE} */
    record Resultat(String resultat, Map<String, Object> valeurs) {
    }
}
