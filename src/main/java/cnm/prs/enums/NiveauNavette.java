package cnm.prs.enums;

/**
 * ⚠️ <strong>Étage courant de la navette à deux niveaux</strong> (spec pilote du 2026-09-04,
 * colonne {@code t_pv_examen.NIVEAU_NAVETTE}).
 *
 * <p>Sur un dossier central passé par le Président PUIS par le Chef de commission, la navette du
 * projet de PV suit le même chemin, à deux étages. Le statut du PV ne suffit pas à savoir où il en
 * est : il vaut {@code PROJET_SOUMIS} aussi bien quand le Membre vient de soumettre au CC que quand
 * le CC l'a transmis au Président. Ce niveau lève l'ambiguïté — c'est lui qui décide QUI peut
 * accepter, retourner et viser.</p>
 *
 * <pre>
 *  Membre ──soumettre──▶ [CC] ──accepter──▶ [PRESIDENT] ──viser──▶ PROJET_ACCEPTE
 *     ▲                    │  ◀──retourner──────┘
 *     └──retourner (CC)────┘
 * </pre>
 *
 * <p><strong>{@code null} n'est pas une troisième valeur de cet enum</strong> : c'est l'absence de
 * niveau — navette simple (un seul étage, contrat d'avant le 2026-09-04), ou projet qui n'est pas
 * dans la navette P/CC (brouillon, en rectification chez le Membre, ou PV déjà visé).</p>
 */
public enum NiveauNavette {

    /** Le projet est chez le <strong>Chef de commission</strong> : il retourne au Membre, ou accepte et transmet. */
    CC,

    /** Le projet est chez le <strong>Président</strong> : il retourne au CC, ou vise. */
    PRESIDENT
}
