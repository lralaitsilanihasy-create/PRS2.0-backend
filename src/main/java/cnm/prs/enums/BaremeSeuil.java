package cnm.prs.enums;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — <strong>barème de seuils</strong> applicable
 * à une entité contractante. L'arrêté n° 13 156/2019-MEF en fixe deux (art. 2) : l'un pour l'État, les
 * organismes publics centraux, les établissements publics nationaux, les sociétés à participation
 * majoritaire publique et les entités bénéficiant de financement public ; l'autre, aux montants divisés
 * par deux (sauf 1,5 M et 350 M), pour les organismes déconcentrés, les collectivités décentralisées et
 * leurs établissements publics.
 *
 * <p><strong>Aucun champ nouveau n'a été créé pour le choisir</strong> (arbitrage pilote du
 * 2026-09-18) : toute entité contractante est assignée, à sa création, à l'organisme de contrôle qui
 * traitera ses dossiers — la CNM ou une CRM —, et c'est sa {@code ID_LOCALITE}. La « localité » de PRS
 * est l'organisme de contrôle, <strong>pas un lieu</strong> : {@link cnm.prs.entity.Localite#estCentrale}
 * sert déjà de source unique pour les modèles de PV et de lettre de renvoi ; elle choisit aussi le
 * barème. CNM → {@link #CENTRAL}, CRM → {@link #DECONCENTRE}.</p>
 *
 * <p>Seul cas à surveiller : l'arrêté range les établissements publics nationaux et les sociétés à
 * participation publique au barème central — s'il arrivait que l'un d'eux relève d'une CRM, il faudrait
 * une exception. Rien à prévoir tant que le cas ne se présente pas.</p>
 */
public enum BaremeSeuil {

    /** État, organismes publics centraux, établissements publics nationaux, sociétés à participation majoritaire publique. */
    CENTRAL,

    /** Organismes publics déconcentrés, collectivités décentralisées et leurs établissements publics. */
    DECONCENTRE;

    /**
     * Barème d'une entité, déduit de son organisme de contrôle. Une localité inconnue ou absente donne
     * {@link #DECONCENTRE} : c'est le barème le plus bas, donc celui qui signale le plus — un
     * signalement de trop se lit et s'écarte, un signalement manquant ne se voit pas.
     */
    public static BaremeSeuil pourLocalite(String idLocalite) {
        return cnm.prs.entity.Localite.estCentrale(idLocalite) ? CENTRAL : DECONCENTRE;
    }
}
