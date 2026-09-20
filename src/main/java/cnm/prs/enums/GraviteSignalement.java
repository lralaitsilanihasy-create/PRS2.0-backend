package cnm.prs.enums;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — <strong>sévérité d'un signalement</strong>
 * ({@code t_anomalie.GRAVITE}).
 *
 * <p>Le pré-contrôle n'a <strong>pas</strong> de niveau « bloquant », et c'est délibéré : un signalement
 * qu'on peut écarter n'est par définition pas bloquant, et le mode de passation reste purement saisi —
 * rien n'est déterminé, rien n'est refusé. À ne pas confondre avec {@link GraviteAnomalie}, qui qualifie
 * les anomalies de <em>transcription</em> d'un import de PPM et connaît, elle, un niveau bloquant.</p>
 *
 * <p>La sévérité suit le manuel de contrôle a priori : des lignes homogènes sur un même compte donnent un
 * avertissement (« à fusionner, éventuellement à allotir ») — le manuel en fait une demande, pas un motif
 * de refus. Le signalement devient {@link #PRIORITAIRE} quand le cumul <strong>change la
 * procédure</strong> ou fait passer le marché au-dessus du seuil de contrôle a priori : c'est le cas que
 * visent les articles 27 et 28 du code des marchés publics, fractionner « dans le seul but d'échapper aux
 * règles de mise en concurrence ou de se soustraire aux contrôles ».</p>
 */
public enum GraviteSignalement {

    /** Signalement ordinaire : à regarder, à corriger ou à écarter en motivant. */
    A_VERIFIER,

    /** Le constat change la procédure applicable ou soustrait le marché au contrôle a priori. */
    PRIORITAIRE
}
