package cnm.prs.enums;

/**
 * Statut d'une version de fiche marché ({@code t_fiche_marche.STATUT}) : enregistrée bloc par bloc en
 * {@link #BROUILLON}, puis {@link #VALIDEE} par la PRMP — figée ; une modification ouvre une nouvelle version.
 */
public enum StatutFicheMarche {
    BROUILLON,
    VALIDEE
}
