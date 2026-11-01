package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-11-01 (demande front « ecarts-ecrans-figes », §B1) — les écarts entre les écrans figés d'une soumission et leur état actuel.
 */
public record EcartsInstantanesDto(Integer soumission, LocalDateTime figeLe, List<Ecran> ecrans) {

    /**
     * Un écran figé : {@code identique} si l'état actuel n'en diffère pas ; {@code comparable = false} quand l'état actuel ne se relit
     * pas pour l'appelant (écran calculé pour son périmètre) — {@code identique} est alors nul ; {@code tronque} au-delà de
     * {@value cnm.prs.service.EcartsInstantane#PLAFOND} écarts.
     */
    public record Ecran(String ecran, Boolean identique, boolean comparable, boolean tronque, List<Ecart> ecarts) {
    }

    /** Un écart : le chemin JSON, un libellé en clair, la valeur soumise et l'actuelle telles qu'on les lit ({@code null} : absente). */
    public record Ecart(String chemin, String libelle, String soumis, String actuel) {
    }
}
