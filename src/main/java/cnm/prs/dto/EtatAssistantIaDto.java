package cnm.prs.dto;

import java.util.List;

/**
 * État de l'assistant IA ({@code GET /api/assistant-ia/etat}) : le front n'affiche l'assistant que
 * s'il est {@code actif}, et prévient si le service de calcul ne répond pas.
 *
 * @param actif       l'assistant est activé ({@code app.ia.actif})
 * @param disponible  le serveur d'inférence répond et connaît le modèle (sonde courte)
 * @param modele      modèle utilisé ; {@code null} si l'assistant est inactif
 * @param documents   documents du corpus chargés ; vide si l'assistant est inactif
 */
public record EtatAssistantIaDto(boolean actif, boolean disponible, String modele, List<DocumentIaDto> documents) {

    /** Un document du corpus et le nombre de passages qu'il fournit. */
    public record DocumentIaDto(String libelle, int passages) {
    }

    public static EtatAssistantIaDto inactif() {
        return new EtatAssistantIaDto(false, false, null, List.of());
    }
}
