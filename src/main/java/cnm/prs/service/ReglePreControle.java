package cnm.prs.service;

import java.util.List;

import cnm.prs.enums.TypeSignalement;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2) — une <strong>règle</strong> du
 * pré-contrôle.
 *
 * <p>Une règle lit le {@link ContextePreControle} et rend des constats. Elle <strong>n'écrit rien</strong>,
 * ne connaît ni la base ni le modèle de langage, et rend deux fois la même réponse sur le même plan : la
 * reproductibilité est du côté de la règle (plan, §4, lot 3, 3.a). C'est ce qui rend un signalement
 * opposable dans un contrôle de marché public — et ce qui rend chaque règle testable seule.</p>
 *
 * <p>Une règle ne lève jamais d'exception pour une donnée manquante : elle <strong>s'abstient</strong>.
 * Un plan incomplet doit produire moins de signalements, pas une erreur qui priverait la PRMP de tous
 * les autres.</p>
 */
public interface ReglePreControle {

    /**
     * La règle, telle qu'elle s'écrit dans {@code t_regle_anomalie.CODE_REGLE} : c'est par ce code que
     * l'Administrateur l'active ou l'éteint, sans redéploiement.
     */
    TypeSignalement type();

    /** Les constats sur ce plan, dans un ordre stable. Liste vide = rien à signaler. */
    List<SignalementDetecte> examiner(ContextePreControle contexte);
}
