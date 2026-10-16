package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-4 ; V127) — une réception d'un bon de commande par la commission de
 * réception : provisoire (après la livraison déclarée) ou définitive (au terme du délai de garantie).
 *
 * @param etat               {@code A_ETABLIR} → {@code PV_ETABLI} → {@code SIGNE}
 * @param decision           {@code RECEPTION}, {@code AJOURNEMENT}, {@code REFACTION} ou {@code REJET} (CCAG fournitures, art. 22) ;
 *                           une réception définitive : {@code RECEPTION} ou {@code AJOURNEMENT}
 * @param echeanceLivraison  le terme du délai du bon (lendemain de la notification + délai) ; nul si le délai ne se lit pas en nombre
 * @param penaliteIndicative à titre indicatif, jamais retenue : montant du bon × taux journalier × jours de retard, plafonné ;
 *                           {@code penaliteNote} dit pourquoi elle est nulle le cas échéant
 * @param attendus           les signataires du PV : chaque membre, et la PRMP quand elle préside
 */
public record ReceptionCommandeDto(Long id, Long idBon, Integer numeroBon, Long idContrat, Long idDmc, Integer lot, String type, String etat,
        LocalDate dateLivraison, String livraisonObservations, LocalDateTime declareeLe, String declareePar, String decision,
        LocalDate dateReception, String motif, String reserves, BigDecimal montantBon, BigDecimal montantRefaction, BigDecimal montantReceptionne,
        LocalDate echeanceLivraison, Integer joursRetard, BigDecimal tauxPenalite, BigDecimal plafondPenalite, BigDecimal penaliteIndicative,
        String penaliteNote, LocalDateTime etabliLe, String etabliPar, boolean pvDisponible, List<Attendu> attendus, LocalDateTime signeLe) {

    /** Un signataire attendu : {@code signataire} = l'identifiant du compte du membre, ou {@code PRMP}. */
    public record Attendu(String signataire, String nom, boolean president, LocalDateTime signeLe) {
    }

    /** {@code POST /api/receptions-commandes/bons/{idBon}/livraison} (et l'espace du titulaire). */
    public record LivraisonRequest(LocalDate dateLivraison, String observations) {
    }

    /**
     * {@code POST /api/receptions-commandes/{id}/pv} : la décision du président. {@code tauxJournalier} en pour mille du montant du bon
     * par jour de retard (⚠️ H : 1 ‰ à défaut) ; {@code montantRefaction} pour une réfaction.
     */
    public record PvRequest(String decision, LocalDate dateReception, String motif, String reserves, BigDecimal montantRefaction,
            BigDecimal tauxJournalier) {
    }

    /**
     * Les réceptions d'un bon : {@code etatReception} ∈ {@code A_LIVRER}, {@code EN_RECEPTION}, {@code AJOURNE}, {@code REJETE},
     * {@code RECU_PROVISOIRE} (garantie en cours), {@code EN_RECEPTION_DEFINITIVE}, {@code RECU_DEFINITIF}, {@code ANNULE} ;
     * {@code alertes} : {@code RECEPTION_DEFINITIVE_A_PRONONCER} (le délai de garantie est écoulé).
     */
    public record DuBon(Long idBon, Integer numero, String objet, String titulaire, String etatReception, Integer garantieMois,
            LocalDate garantieJusquau, List<String> alertes, List<ReceptionCommandeDto> receptions) {
    }

    /** Une réception à traiter par un membre de la commission ({@code GET /api/receptions-commandes/miennes}). */
    public record Mienne(ReceptionCommandeDto reception, String objetBon, String titulaire, boolean president, boolean aEtablir, boolean aSigner) {
    }

    /** La commission de réception d'un contrat ({@code GET/PUT /api/contrats-en-vigueur/{id}/commission-reception}). */
    public record Commission(Long idContrat, String decisionReference, LocalDate decisionDate, boolean presideeParPrmp, List<Membre> membres,
            LocalDateTime majLe, String majPar) {
    }

    public record Membre(Long id, String nom, String prenom, String email, String fonction, boolean president, String idCompte,
            boolean compteActif) {
    }

    public record CommissionCorps(Decision decision, Boolean presideeParPrmp, List<MembreCorps> membres) {
    }

    public record Decision(String reference, LocalDate date) {
    }

    public record MembreCorps(String nom, String prenom, String email, String fonction, Boolean president) {
    }
}
