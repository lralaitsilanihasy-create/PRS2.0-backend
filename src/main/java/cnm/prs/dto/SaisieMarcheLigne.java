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
        @Valid
        List<SaisieBeneficiaireLigne> beneficiaires,

        // Processus de marché + dates prévisionnelles (idCapm, dateDebut, dateFin). @Valid cascade la
        // validation par processus (chemins marches[i].processus[j].champ). « Au moins un processus »
        // est exigé à la CRÉATION (SaisieService) — pas via @NotEmpty ici, pour ne pas casser l'édition
        // qui partage ce DTO (EditionPpmRequest).
        @Valid
        List<ProcessusMarche> processus,

        // idMode : mode de passation saisi (facultatif). Conservé tel quel (plus de détermination auto).
        Integer idMode,

        // Libellé de mode (import PPM) : utilisé UNIQUEMENT si idMode est absent → résolu-ou-créé (tr_mode).
        @Size(max = 100)
        String modeLibelle) {
}
