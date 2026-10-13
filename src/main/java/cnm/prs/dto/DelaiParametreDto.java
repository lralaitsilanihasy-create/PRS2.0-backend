package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ R6a (manuel révisé, lot 2, §B1 ; V106) — un délai de publicité administrable ({@code GET/PUT /api/delais-parametres}) : signalé,
 * jamais bloquant tant que le pilote ne l'a pas confirmé.
 */
public record DelaiParametreDto(String code, Integer valeur, String unite, String texte, boolean actif, LocalDateTime dateMaj, String imActeur) {

    /** Le corps du {@code PUT} : un champ absent garde sa valeur. */
    public record Request(Integer valeur, String unite, String texte, Boolean actif) {
    }
}
