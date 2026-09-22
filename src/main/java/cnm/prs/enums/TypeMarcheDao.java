package cnm.prs.enums;

/**
 * Type de marché d'un appel d'offres de fournitures (esquisse du pilote, 2026-09-22) — première réponse du
 * cadrage de la fiche marché. Le lot 1 ne traite que {@link #QUANTITE_FIXE} ; les deux autres arrivent avec
 * leurs lots (3 : à commande ; 4 : contrat-cadre) et ne sont admis, en lot 1, que comme filtre du référentiel.
 */
public enum TypeMarcheDao {
    QUANTITE_FIXE,
    A_COMMANDE,
    CONTRAT_CADRE
}
