package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-06 (demande front « le retrait du DAO après paiement des frais », §B2, §B3) — un <strong>reçu du paiement des frais de
 * dossier</strong>. {@code lots} : {@code null} = tout le dossier. {@code etat} ∈ {@code EN_ATTENTE} · {@code VALIDE} ·
 * {@code REFUSE} ; {@code decidePar} : la fonction ({@code PRMP}, {@code UGPM}), pas le nom. Côté PRMP et UGPM seulement :
 * {@code entreprise}, {@code compte} (l'adresse du déposant), {@code fraisAttendus} (la somme des frais des lots couverts) et
 * {@code montantInsuffisant} ; {@code null} pour le candidat.
 */
public record RecuDto(Integer idRecu, List<Integer> lots, BigDecimal montant, String referencePaiement, LocalDate datePaiement, String banque,
        String nomFichier, LocalDateTime dateDepot, String etat, String motifRefus, LocalDateTime dateDecision, String decidePar,
        Entreprise entreprise, String compte, BigDecimal fraisAttendus, Boolean montantInsuffisant) {

    public record Entreprise(String raisonSociale, String nif) {
    }

    /** La partie {@code data} du dépôt (multipart, à côté de {@code fichier}). */
    public record Corps(List<Integer> lots, BigDecimal montant, String referencePaiement, LocalDate datePaiement, String banque) {
    }

    /** {@code POST …/refuser}. */
    public record Refus(String motif) {
    }
}
