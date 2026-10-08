package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 3, §B4 ; ADR-0013) — une <strong>offre déposée en ligne</strong>, telle
 * que le candidat la voit. {@code etat} ∈ {@code EN_COURS} · {@code DEPOSEE} · {@code REMPLACEE} · {@code RETIREE} · {@code ECARTEE}
 * (posé par le lot 4). Le serveur ne connaît rien du contenu : ni les pièces, ni les montants.
 */
public record OffreDto(String idOffre, Long idDmc, String reference, String objet, Integer lot, String etat, LocalDateTime dateCreation,
        LocalDateTime dateDepot, LocalDateTime dateRetrait, Integer numero, long taille, int nombreMorceaux, int recus, String empreinte,
        String remplace, String remplaceePar,
        /** ⚠️ V86 (PI-b) — {@code TECHNIQUE} · {@code FINANCIERE} ; nul pour une offre ordinaire. */
        String enveloppe) {

    /**
     * Le corps de {@code POST /api/candidat/offres}. {@code enTete} est une <strong>chaîne</strong> : le JSON de l'en-tête tel que le
     * navigateur l'a sérialisé et haché (ADR §4) — le serveur le relit, le contrôle et le garde tel quel. {@code groupementNifs} :
     * les NIF des membres d'un groupement, pour le seul contrôle des exclusions (le groupement reste dans le manifeste).
     */
    public record Creation(Long idDmc, Integer lot, String enTete, String remplace, List<String> groupementNifs,
            /** ⚠️ V86 (lot 3 PI, PI-b) — {@code TECHNIQUE} ou {@code FINANCIERE} pour une proposition de prestations intellectuelles ; absent sinon. */
            String enveloppe) {
    }

    /** La réponse à un morceau reçu. */
    public record Recu(int rang, int taille, int recus) {
    }

    /** Le corps de {@code POST …/sceller} : l'empreinte calculée par le navigateur (SHA-256 de l'en-tête puis des morceaux). */
    public record Scellement(String empreinte) {
    }

    /** L'accusé de réception : l'offre, l'entreprise, et ce pour quoi elle est scellée. */
    public record Accuse(OffreDto offre, Entreprise entreprise, int n, int quorum, List<String> empreintesDetenteurs,
            /** ⚠️ V86 (PI-b) — l'autre enveloppe de la proposition, si elle est déjà scellée (son empreinte figure ainsi sur l'accusé). */
            OffreDto jumelle) {
    }

    public record Entreprise(String nif, String raisonSociale) {
    }

    /** {@code GET /api/horloge} : l'heure du serveur, à la seconde, et son fuseau. */
    public record Horloge(String maintenant, String fuseau) {
    }

    /**
     * Une pièce attendue de l'offre ({@code GET /api/procedures-en-ligne/{idDmc}/pieces}) ; {@code code} est la clé du manifeste.
     * ⚠️ 2026-10-05 (lot 5, §B1.3) — {@code formulaire} : {@code null}, ou le formulaire qui la remplace quand la fiche a un besoin
     * ({@code BORDEREAU}, {@code CONFORMITE}, {@code CALENDRIER}, {@code DQE}, {@code SOUS_DETAIL}, {@code K1}, {@code CAPACITES},
     * {@code PERSONNEL}, {@code MATERIEL}) ; elle n'est alors plus exigée en fichier ({@code obligatoire = false}), une pièce
     * justificative peut toujours être jointe. ⚠️ 2026-10-06 (retrait après paiement, §B5) — {@code dejaFourni} (sur {@code RECU-DAO}
     * seulement, pour un dossier payant) : l'entreprise du candidat connecté a un reçu validé ; {@code null} ailleurs.
     */
    public record PieceAttendue(String code, String rubrique, String numero, String libelle, String forme, Integer ancienneteMaxMois,
            boolean parLot, String modele, boolean obligatoire, String formulaire, Boolean dejaFourni,
            /** ⚠️ 2026-10-08 (lot 3 PI, H-PI-1 du front) — l'enveloppe d'une proposition de prestations intellectuelles : {@code TECHNIQUE}
             * ou {@code FINANCIERE} ; nul hors PI. */
            String enveloppe) {
    }

    /**
     * {@code GET /api/fiches-marche/{idDmc}/depots} : avant la date limite, le nombre seul ; après, le registre des dépôts
     * ({@code depots} : les offres déposées par rang d'arrivée, puis les retirées et remplacées).
     */
    public record Depots(boolean clos, long nombre, LocalDateTime dateLimite, List<Depot> depots) {
    }

    public record Depot(Integer numero, String entreprise, String nif, Integer lot, LocalDateTime dateDepot, LocalDateTime dateRetrait,
            String empreinte, long taille, String etat,
            /** ⚠️ V86 (PI-b) — {@code TECHNIQUE} · {@code FINANCIERE} ; nul pour une offre ordinaire. */
            String enveloppe) {
    }

    /** Le résumé porté par {@code FicheMarcheDto.depots} (mode électronique seul). */
    public record Resume(long nombre, boolean clos) {
    }
}
