package cnm.prs.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PUT /api/fiches-marche/{idDmc}/cadrage} — les réponses aux dix questions, par clé
 * ({@code typeMarche}, {@code alloti}, {@code nbLots}, {@code variantes}, {@code groupement}, {@code formeGroupement},
 * {@code provenance}, {@code typePrix}, {@code prixRevisable}, {@code garantieSoumission}, {@code avance},
 * {@code tauxAvance}, {@code penalites}, {@code attributaires} — ⚠️ lot 4 : {@code MONO} ou {@code MULTI}). Une clé absente = question sans réponse.
 */
public record CadrageRequest(@NotNull Map<String, Object> cadrage) {
}
