package cnm.prs.dto;

import java.util.List;

/**
 * Bilan des contrôles d'une fiche marché (demande du 2026-09-22, §B4) — recalculé à chaque enregistrement et servi
 * dans {@link FicheMarcheDto} ; {@code POST /controler} le recalcule sans écrire.
 *
 * @param bloquants  contrôles qui empêchent la validation
 * @param avertissements contrôles signalés, sans blocage
 * @param ok         contrôles évalués et satisfaits
 * @param nbSaisis   champs de saisie renseignés (rubriques ouvertes)
 * @param nbAttendus champs de saisie attendus (rubriques ouvertes, référentiel chargé)
 */
public record BilanControlesDto(List<Controle> bloquants, List<Controle> avertissements, List<Controle> ok,
        int nbSaisis, int nbAttendus) {

    /** Un contrôle : la règle du catalogue, les champs concernés, leur bloc, le message servi tel quel. */
    public record Controle(String regle, List<String> champs, String bloc, String message) {
    }
}
