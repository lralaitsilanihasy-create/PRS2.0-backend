package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ⚠️ R5 (manuel révisé pour l'application, lot 1, §B6 ; V105, 2026-10-13) — une ligne de
 * {@code GET /api/dossiers/{id}/controles} : un contrôle automatique ({@code auteur = SERVEUR}) ou un contrôle B d'un contrôleur.
 *
 * @param reference   la référence du manuel (« 2-III-A P5 »), ou {@code null} (point de grille)
 * @param idPointCtrl le point de grille d'un contrôle B, ou {@code null}
 * @param regle       le code de la règle
 * @param valeurs     les valeurs constatées
 * @param resultat    {@code CONFORME}, {@code NON_CONFORME}, {@code NON_APPLICABLE}
 * @param bloquant    une non-conformité refuse la soumission
 * @param observation l'observation du contrôleur (obligatoire quand il constate une non-conformité)
 */
public record ControleResultatDto(Long idControle, Integer numeroSoumission, String reference, Integer idPointCtrl, String regle,
        String libelle, Map<String, Object> valeurs, String resultat, boolean bloquant, String auteur, LocalDateTime dateControle,
        String observation) {

    /** Le corps de {@code POST /api/dossiers/{id}/controles} (contrôle B du Membre) : un point de grille ou une règle, un résultat. */
    public record Request(Integer idPointCtrl, String regle, String reference, String libelle, String resultat, String observation,
            Boolean bloquant) {
    }
}
