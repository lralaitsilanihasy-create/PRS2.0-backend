package cnm.prs.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Données nécessaires au remplissage du modèle Word du Projet de PV (découplé des entités/repositories
 * pour rester testable). Les noms {@code nomPresident}/{@code nomChefCommission} sont {@code null}/vides
 * tant que le rôle n'a pas signé → la ligne correspondante du bloc « Étaient présents » est retirée.
 */
public record PvDocumentContexte(
        LocalDate dateExamen,
        String refPv,
        LocalDate dateReception,
        String entiteContractante,
        Integer anneeExercice,
        String localite,
        /** ⚠️ 2026-08-04 — chef-lieu (ville de siège) : lieu porté par « A … , le … » ; repli localité. */
        String chefLieu,
        String nomPresident,
        String nomChefCommission,
        String nomMembre,
        /**
         * ⚠️ Refonte du bloc VISA (arbitrage du pilote, 2026-09-01) — ligne nommant le VISEUR :
         * « Visé par : NOM Prénoms, qualité », suffixée « — par intérim » sur un PV de localité
         * <strong>non centrale</strong> visé par intérim. Présente sur TOUS les PV (R1) ; jamais de
         * mention en Centrale, intérim compris (R2). La qualité est reprise mot pour mot du bloc
         * « Étaient présents » du même document, pour n'introduire aucun vocabulaire nouveau dans un
         * formulaire officiel.
         */
        String ligneViseur,
        /**
         * ⚠️ 2026-08-05 (versionnement des PPM) — numéro de mise à jour du PPM ({@code null} ou 0 =
         * plan INITIAL). Détermine la nature annoncée à la ligne « NATURE ET INTITULE DU DOSSIER » :
         * « INITIAL » pour le plan d'origine, « MODIFICATIF N°n » pour une version postérieure.
         */
        Integer numMaj,
        List<Observation> observations) {

    /**
     * Une ligne de l'ANNEXE : point de contrôle non conforme et sa correction (au lieu de / lire),
     * OU (⚠️ règle ajoutée 2026-08-01) une <strong>pièce jointe non conforme</strong>
     * ({@code piece = true}) : {@code auLieuDe} porte alors le texte libre de l'observation,
     * {@code lire} est vide et les libellés « Au lieu de : / Lire : » sont retirés de la ligne.
     */
    public record Observation(String pointControle, String auLieuDe, String lire, boolean piece) {

        /** Observation d'un point de contrôle (compatibilité : {@code piece = false}). */
        public Observation(String pointControle, String auLieuDe, String lire) {
            this(pointControle, auLieuDe, lire, false);
        }
    }
}
