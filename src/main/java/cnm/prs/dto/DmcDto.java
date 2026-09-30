package cnm.prs.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.DossierMec} (dossier de mise en concurrence).
 * {@code typeDmcCode}/{@code typeDmcLibelle} sont dérivés du type (lecture seule).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DmcDto {

    private Long idDmc;
    private Integer idDetail;
    private Long idTypeDmc;
    private String typeDmcCode;
    private String typeDmcLibelle;
    private String reference;
    private String statut;
    private LocalDateTime dateCreation;

    /**
     * ⚠️ Fiche marché (2026-09-22, §B2) — sur la réponse de {@code POST /par-marche/{idDetail}} : les informations
     * reprises de la ligne du PPM, clé = code du champ de source {@code PPM} (B01, B02), valeur telle qu'affichée,
     * et {@code versionPpm} le numéro de version du plan lu. Relues, jamais stockées (H7). {@code null} ailleurs.
     */
    private java.util.Map<String, String> valeursPpm;

    private Integer versionPpm;

    /**
     * ⚠️ Fiche marché, lot 1b (2026-09-23, §B1) — le dossier soumis à la CNM produit par la fiche de ce DMC, ou
     * rattaché à elle ({@code t_dossier.ID_DMC}) ; {@code null} tant qu'il n'existe pas.
     */
    private Integer idDossierSoumis;

    /**
     * ⚠️ 2026-09-27 (statut « Lancé », §B1) — sur la réponse de {@code POST /par-marche/{idDetail}} : le statut de la ligne
     * du plan après la création. ⚠️ Depuis le 2026-09-30, la création ne le change plus : c'est le statut de la ligne
     * tel quel ({@code PREVU} en règle générale) ; elle passe {@code LANCE} à l'impression de son avis spécifique.
     * {@code null} ailleurs.
     */
    private String statutLigne;
}
