package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-07 (AMI en ligne, tranche AMI-a, §B1, §B2 ; V82) — l'appel à manifestation d'intérêt d'une procédure de prestations
 * intellectuelles, vu par la PRMP, l'UGPM et la commission : {@code etat} ∈ {@code BROUILLON} · {@code PUBLIE} · {@code DISPENSE} ;
 * {@code lectureOuverte} : la date limite est passée, les expressions se lisent (arbitrage Q2) ; {@code nombreExpressions} est servi
 * à tout moment (le nombre, jamais le contenu).
 */
public record AmiDto(Long idDmc, String etat, String objet, String autoriteContractante, String reference, LocalDateTime dateLimite,
        List<Critere> criteres, List<String> pieces, BigDecimal noteMinimale, Integer nombreRetenus, String motifDispense,
        List<Publication> publications, boolean avisDisponible, LocalDateTime publieLe, String publiePar, boolean lectureOuverte,
        long nombreExpressions) {

    /** Un critère de sélection (art. 42-II : aptitude, références, expérience) : {@code poids} en points sur 100. */
    public record Critere(String code, String libelle, BigDecimal poids, String description) {
    }

    /** Un support de publication déclaré par la PRMP (art. 32-III : journal des marchés de l'ARMP, journal national…). */
    public record Publication(String support, LocalDate date, String reference) {
    }

    /** {@code PUT …/ami} : la préparation, tant que l'AMI n'est pas publié. */
    public record AmiRequest(LocalDateTime dateLimite, List<Critere> criteres, List<String> pieces, BigDecimal noteMinimale,
            Integer nombreRetenus) {
    }

    /** {@code POST …/ami/publier}. */
    public record PublicationRequest(List<Publication> publications) {
    }

    /** {@code POST …/ami/dispense}. */
    public record DispenseRequest(String motif) {
    }

    /** L'AMI publié, tel que le public le lit ({@code GET /api/amis-en-ligne}) : {@code ouvert} tant que la date limite n'est pas passée. */
    public record AmiPublic(Long idDmc, String reference, String objet, String autoriteContractante, LocalDateTime dateLimite,
            List<Critere> criteres, List<String> pieces, Integer nombreRetenus, List<Publication> publications, LocalDateTime publieLe,
            boolean ouvert,
            /** ⚠️ AMI-b (Q5) — la liste restreinte définitive, publiée ; vide avant. */
            List<Retenu> liste) {
    }

    /** Un candidat de la liste restreinte publiée. */
    public record Retenu(Integer rang, String raisonSociale, String nif) {
    }

    // ------------------------------------------------------------------ les expressions d'intérêt (§B2)

    /** Une référence de mission similaire. */
    public record Reference(String intitule, String client, Integer annee, BigDecimal montant, String description) {
    }

    /** Un membre d'un groupement (déclaré au dépôt, comme pour une offre). */
    public record Membre(String nif, String raisonSociale, String role) {
    }

    /** Une pièce jointe : {@code libelle} = l'une des pièces attendues de l'AMI ; {@code fichier} = le nom du fichier envoyé. */
    public record PieceDeclaree(String libelle, String fichier) {
    }

    /**
     * Le corps d'un dépôt (partie {@code expression} du multipart, en JSON) : la lettre de manifestation d'intérêt, les qualifications,
     * les références, le groupement éventuel, et pour chaque pièce attendue le fichier qui la porte (parties {@code fichiers}).
     */
    public record ExpressionRequest(String lettre, String qualifications, List<Reference> references, List<Membre> groupement,
            List<PieceDeclaree> pieces) {
    }

    public record PieceDeposee(Long id, String libelle, String nom, String format, Long taille, String empreinte) {
    }

    /**
     * Une expression d'intérêt : {@code etat} ∈ {@code DEPOSEE} · {@code REMPLACEE} · {@code RETIREE} ; {@code empreinte} = SHA-256 du
     * contenu et des pièces (l'accusé de dépôt).
     */
    public record Expression(String id, Integer numero, String nif, String raisonSociale, String etat, LocalDateTime deposeeLe,
            String empreinte, String lettre, String qualifications, List<Reference> references, List<Membre> groupement,
            List<PieceDeposee> pieces) {
    }
}
