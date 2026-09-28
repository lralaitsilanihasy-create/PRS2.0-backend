package cnm.prs.dto;

import java.util.Map;

/**
 * ⚠️ Import du DAO (demande front du 2026-09-28, §B2) — corps de {@code PUT /api/fiches-marche/{idDmc}/import/appliquer} :
 * uniquement les lignes que la PRMP a retenues. {@code cadrage} : réponses par clé (les autres restent) ; {@code valeurs} :
 * valeurs par code de champ, tous blocs confondus (un code absent n'est pas effacé) ; {@code fichier} et
 * {@code empreinte} : ceux de la lecture, pour le journal.
 */
public record ImportDaoAppliquerRequest(Map<String, Object> cadrage, Map<String, Object> valeurs, String fichier,
        String empreinte) {
}
