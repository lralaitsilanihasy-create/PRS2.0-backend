package cnm.prs.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Ligne de marché saisie via la façade PPM. {@code idDossier} et {@code idPpm} sont renseignés par le
 * service ; le mode est <strong>saisi</strong> (plus de détermination automatique). Pour l'import PPM, la
 * nature / le mode peuvent être fournis par <strong>libellé</strong> ({@code natureLibelle}/{@code modeLibelle})
 * quand l'id est absent — le service les <strong>résout ou les crée à la volée</strong>.
 */
public record SaisieMarcheLigne(

        // idDetail : null à la création (PK allouée par le serveur) ; renseigné pour identifier une
        // ligne existante lors de l'édition d'un brouillon (réconciliation par idDetail).
        Integer idDetail,

        @Size(max = 500)
        String designationMarche,

        // ⚠️ Règle ajoutée (2026-07-18) — forme du marché : A_COMMANDE / CONTRAT_CADRE / QUANTITE_FIXE.
        // Optionnel (absent/vide → défaut QUANTITE_FIXE, code inconnu → 400 ciblé) ; pré-rempli par
        // l'import PPM (détecté dans l'objet, désignation conservée intégrale).
        @Size(max = 20)
        String formeMarche,

        @Size(max = 20)
        String numCompte,

        BigDecimal montEstim,

        // Nouveau montant estimatif du marché (optionnel, rétro-compatible) → t_marche.NOUV_MONT_ESTIM.
        BigDecimal nouvMontEstim,

        @Size(max = 20)
        String financement,

        @Size(max = 20)
        String statut,

        Integer idNature,

        // Libellé de nature (import PPM) : utilisé UNIQUEMENT si idNature est absent → résolu-ou-créé (tr_nature).
        @Size(max = 100)
        String natureLibelle,

        // Bénéficiaires du marché (optionnel) : une ligne t_service_beneficiaire par élément. Si non vide, la
        // cohérence des montants est validée (Σ ancMontBenef = montEstim ; Σ nouvMontBenef = nouvMontEstim si fourni).
        // ⚠️ @Valid sur le paramètre de type (cf. SaisiePpmRequest) — sur la List, il est déprécié.
        List<@Valid SaisieBeneficiaireLigne> beneficiaires,

        // Lots du marché (optionnel, rétro-compatible) : une ligne t_lot par élément (comme les bénéficiaires).
        // Descriptif : aucun contrôle de somme des montants. Absent/vide → aucun lot.
        List<@Valid SaisieLotLigne> lots,

        // Processus de marché + dates prévisionnelles (idCapm, dateDebut, dateFin). @Valid cascade la
        // validation par processus (chemins marches[i].processus[j].champ). « Au moins un processus »
        // est exigé à la CRÉATION et pour toute ligne NOUVELLE à l'édition (SaisieService) — pas via
        // @NotEmpty ici : une ligne mise à jour (idDetail fourni) peut omettre la liste (= conservée) ;
        // fournie, elle REMPLACE les processus existants (vide → 400, invariant ≥1 par marché).
        List<@Valid ProcessusMarche> processus,

        // idMode : mode de passation saisi (facultatif). Conservé tel quel (plus de détermination auto).
        Integer idMode,

        // Libellé de mode (import PPM) : utilisé UNIQUEMENT si idMode est absent → résolu-ou-créé (tr_mode).
        @Size(max = 100)
        String modeLibelle) {
}
