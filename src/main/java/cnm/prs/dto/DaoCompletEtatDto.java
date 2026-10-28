package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ 2026-10-28 (demande front « dao-complet-en-file », Q3 ; V144) — où en est le DAO complet de la dernière version validée.
 *
 * @param etat   {@code EN_FILE}, {@code EN_COURS}, {@code PRET}, {@code ECHEC}, ou {@code SANS_WORD} (serveur sans assemblage : les
 *               documents séparés font le DAO)
 * @param rang   la position dans la file (1 = le prochain), {@code null} hors {@code EN_FILE}
 * @param depuis l'entrée dans l'état courant (mise en file, début de l'assemblage, fin, abandon) ; {@code null} en {@code SANS_WORD}
 * @param motif  le dernier échec, en clair : en {@code ECHEC}, et en {@code EN_FILE} quand la version attend d'être reprise
 */
public record DaoCompletEtatDto(String etat, Integer rang, LocalDateTime depuis, String motif) {
}
