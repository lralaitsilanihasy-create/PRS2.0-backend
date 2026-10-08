package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-07 (AMI en ligne, tranche AMI-b, §B3 ; V83) — la présélection de l'AMI par la commission d'appel d'offres :
 * {@code etat} ∈ {@code EN_ATTENTE} (avant la date limite) · {@code NOTATION} · {@code LISTE_ARRETEE} (rapport à signer) ·
 * {@code DEFINITIVE} (rapport signé : liste publiée et notifiée) · {@code INFRUCTUEUX}.
 */
public record PreselectionDto(Long idDmc, String etat, List<Declaration> declarations, List<Expression> expressions, List<Retenu> liste,
        Integer nombreRetenus, BigDecimal noteMinimale, String motifNombre, String observations, Rapport rapport, int nombreRelances,
        String motifInfructueux) {

    public record Declaration(String membre, String nom, boolean president, LocalDateTime signeeLe, Boolean conflit, String precision) {
    }

    /**
     * Une expression déposée et sa notation : {@code complete} = tous les critères notés ; {@code qualifiee} = complète, non écartée, et
     * au moins la note minimale ; {@code rang} parmi les qualifiées (nul sinon), {@code exAequo} à égalité de total.
     */
    public record Expression(String id, Integer numero, String nif, String raisonSociale, List<Note> notes, BigDecimal total, boolean complete,
            boolean qualifiee, boolean ecartee, String motifEcartement, Integer rang, boolean exAequo) {
    }

    /** La note d'un critère : {@code max} = le poids du critère ; {@code note} nulle tant qu'elle n'est pas saisie. */
    public record Note(String code, String libelle, BigDecimal max, BigDecimal note, String motif, String par, String nom, LocalDateTime le) {
    }

    /** Un candidat de la liste restreinte arrêtée. */
    public record Retenu(Integer rang, String idExpression, String idCandidat, String nif, String raisonSociale, BigDecimal note) {
    }

    public record Rapport(LocalDateTime produitLe, boolean signe, LocalDateTime signeLe, List<Signature> signatures, List<Attendue> attendues) {
    }

    public record Signature(String im, String nom, LocalDateTime date, boolean empechement, String motif, String constatePar,
            String observation) {
    }

    public record Attendue(String im, String nom) {
    }

    // ------------------------------------------------------------------ corps des requêtes

    public record DeclarationRequest(Boolean conflit, String precision) {
    }

    /** {@code PUT …/expressions/{id}/notes} : une ou plusieurs notes, chacune motivée. */
    public record NotesRequest(List<NoteSaisie> notes) {
    }

    public record NoteSaisie(String code, BigDecimal note, String motif) {
    }

    /** {@code POST …/expressions/{id}/ecartement} : {@code ecartee} faux rétablit l'expression. */
    public record EcartementRequest(Boolean ecartee, String motif) {
    }

    /**
     * {@code POST …/preselection/arreter} : {@code motifNombre} exigé si moins de candidats qualifiés que le nombre à retenir (Q3) ;
     * {@code ordre} : les identifiants des expressions à égalité au seuil de la liste, de la première à la dernière (départage).
     */
    public record ArretRequest(String motifNombre, String observations, List<String> ordre) {
    }

    public record SignatureRequest(String observation) {
    }

    public record EmpechementRequest(String im, String motif) {
    }

    /** {@code POST …/ami/relancer} : la nouvelle date limite et le motif (Q3). */
    public record RelanceRequest(LocalDateTime dateLimite, String motif) {
    }

    public record InfructueuxRequest(String motif) {
    }
}
