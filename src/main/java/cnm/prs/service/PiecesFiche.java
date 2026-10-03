package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PieceExigeeDto;
import cnm.prs.entity.FichePiece;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.FichePieceRepository;

/**
 * ⚠️ V61 (demande front du 2026-10-03, pièces de l'offre des travaux) — la liste des <strong>pièces exigées</strong> d'une
 * version de fiche DAO de travaux (clause 6.2 du DPAO-T) : pièces administratives (2°) et autres pièces de l'offre (1°).
 * Comme les moyens ({@link MoyensFiche}) : remplacée en bloc (l'ordre est la position), figée à la validation, copiée à
 * la révision ; une seule liste, la rubrique sur chaque pièce. Les gardes sont celles de {@link FicheMarcheService}.
 *
 * <p>Impression : {@code {{PIECES.administratives}}} et {@code {{PIECES.offre}}}, une ligne par pièce ({@link #jetons}) ;
 * lisibles par une condition ({@code PIECES.offre renseigne}).</p>
 */
@Component
@Transactional
public class PiecesFiche {

    public static final String ADMINISTRATIVE = "ADMINISTRATIVE";
    public static final String OFFRE = "OFFRE";
    static final Set<String> RUBRIQUES = Set.of(ADMINISTRATIVE, OFFRE);
    public static final String JETON_ADMINISTRATIVES = "PIECES.administratives";
    public static final String JETON_OFFRE = "PIECES.offre";

    private final FichePieceRepository pieceRepository;

    public PiecesFiche(FichePieceRepository pieceRepository) {
        this.pieceRepository = pieceRepository;
    }

    @Transactional(readOnly = true)
    public List<PieceExigeeDto> pieces(Integer idFiche) {
        if (idFiche == null) {
            return List.of();
        }
        return pieceRepository.findByIdFicheOrderByOrdreAsc(idFiche).stream().map(p -> new PieceExigeeDto(p.getIdPiece(),
                p.getOrdre(), p.getRubrique(), p.getNumero(), p.getLibelle(), p.getForme(), p.getAncienneteMaxMois(),
                p.isParLot(), p.getModele())).toList();
    }

    /** §B1.2 — rubrique connue, libellé obligatoire (≤ 300), textes bornés, ancienneté ≥ 1 ; 400 {@code pieces[i].…}. */
    public static void valider(List<PieceExigeeDto> lignes) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            PieceExigeeDto p = lignes.get(i);
            String q = "pieces[" + i + "].";
            if (p == null) {
                erreurs.add(new ErrorResponse.FieldError("pieces[" + i + "]", "Ligne vide."));
                continue;
            }
            if (p.getRubrique() == null || p.getRubrique().isBlank()) {
                erreurs.add(new ErrorResponse.FieldError(q + "rubrique", "La rubrique est obligatoire (ADMINISTRATIVE ou OFFRE)."));
            } else if (!RUBRIQUES.contains(p.getRubrique().trim().toUpperCase())) {
                erreurs.add(new ErrorResponse.FieldError(q + "rubrique", "Rubrique inconnue : « " + p.getRubrique()
                        + " » (ADMINISTRATIVE ou OFFRE)."));
            }
            facultatif(erreurs, q + "numero", p.getNumero(), 10, "Le numéro");
            if (p.getLibelle() == null || p.getLibelle().isBlank()) {
                erreurs.add(new ErrorResponse.FieldError(q + "libelle", "Le libellé est obligatoire."));
            } else {
                facultatif(erreurs, q + "libelle", p.getLibelle(), 300, "Le libellé");
            }
            facultatif(erreurs, q + "forme", p.getForme(), 200, "La forme");
            if (p.getAncienneteMaxMois() != null && p.getAncienneteMaxMois() < 1) {
                erreurs.add(new ErrorResponse.FieldError(q + "ancienneteMaxMois", "L'ancienneté maximale est d'au moins 1 mois."));
            }
            facultatif(erreurs, q + "modele", p.getModele(), 200, "Le modèle");
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
    }

    public void remplacer(Integer idFiche, List<PieceExigeeDto> lignes) {
        pieceRepository.deleteByIdFiche(idFiche);
        pieceRepository.flush();
        int ordre = 0;
        for (PieceExigeeDto p : lignes) {
            pieceRepository.save(new FichePiece(null, idFiche, ++ordre, p.getRubrique().trim().toUpperCase(), vide(p.getNumero()),
                    p.getLibelle().trim(), vide(p.getForme()), p.getAncienneteMaxMois(), Boolean.TRUE.equals(p.getParLot()),
                    vide(p.getModele())));
        }
    }

    /** La révision copie la liste de la version précédente. */
    public void copier(Integer idFicheSource, Integer idFicheCible) {
        for (PieceExigeeDto p : pieces(idFicheSource)) {
            pieceRepository.save(new FichePiece(null, idFicheCible, p.getOrdre(), p.getRubrique(), p.getNumero(), p.getLibelle(),
                    p.getForme(), p.getAncienneteMaxMois(), Boolean.TRUE.equals(p.getParLot()), p.getModele()));
        }
    }

    /** Le nombre de pièces d'une rubrique. */
    public static long compter(List<PieceExigeeDto> pieces, String rubrique) {
        return pieces.stream().filter(p -> rubrique.equals(p.getRubrique())).count();
    }

    /**
     * §B2 — les valeurs de {@code {{PIECES.administratives}}} et {@code {{PIECES.offre}}}, une ligne par pièce de la
     * rubrique, dans l'ordre ; une rubrique vide n'en donne pas (pointillés, condition fausse).
     */
    public static Map<String, String> jetons(List<PieceExigeeDto> pieces) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String[] r : List.of(new String[] {ADMINISTRATIVE, JETON_ADMINISTRATIVES}, new String[] {OFFRE, JETON_OFFRE})) {
            List<String> lignes = (pieces == null ? List.<PieceExigeeDto>of() : pieces).stream()
                    .filter(p -> r[0].equals(p.getRubrique())).map(PiecesFiche::ligne).toList();
            if (!lignes.isEmpty()) {
                m.put(r[1], String.join("\n", lignes));
            }
        }
        return m;
    }

    /**
     * « - 01 : Carte professionnelle 2026, copie légalisée par le centre fiscal, datée de moins de 3 mois » ; sans numéro :
     * « - Extrait du Registre de Commerce, photocopie certifiée » ; puis « une par lot » et « selon le modèle : … ». Les
     * morceaux absents disparaissent avec leur virgule.
     */
    static String ligne(PieceExigeeDto p) {
        List<String> morceaux = new ArrayList<>();
        morceaux.add(p.getLibelle());
        if (p.getForme() != null && !p.getForme().isBlank()) {
            morceaux.add(p.getForme().trim());
        }
        if (p.getAncienneteMaxMois() != null) {
            morceaux.add(p.getAncienneteMaxMois() == 1 ? "datée de moins d'un mois"
                    : "datée de moins de " + p.getAncienneteMaxMois() + " mois");
        }
        if (Boolean.TRUE.equals(p.getParLot())) {
            morceaux.add("une par lot");
        }
        if (p.getModele() != null && !p.getModele().isBlank()) {
            morceaux.add("selon le modèle : " + p.getModele().trim());
        }
        String numero = p.getNumero() == null || p.getNumero().isBlank() ? "" : p.getNumero().trim() + " : ";
        return "- " + numero + String.join(", ", morceaux);
    }

    private static void facultatif(List<ErrorResponse.FieldError> erreurs, String champ, String v, int max, String nom) {
        if (v != null && v.trim().length() > max) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " : " + max + " caractères au plus."));
        }
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
