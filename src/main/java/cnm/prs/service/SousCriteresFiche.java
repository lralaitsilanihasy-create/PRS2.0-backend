package cnm.prs.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SousCritereDto;
import cnm.prs.entity.FicheSousCritere;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.FicheSousCritereRepository;

/**
 * ⚠️ V84 (lot 3 PI, tranche PI-a, arbitrage Q3 du pilote) — les <strong>sous-critères techniques</strong> d'une version de fiche de
 * prestations intellectuelles : chaque critère {@code B06-TP-02} à {@code B06-TP-06} se détaille en sous-critères pondérés (par
 * exemple, un barème par expert pour le personnel clé), dont la somme fait les points du critère ; un critère sans sous-critère se note
 * globalement. Comme les pièces de l'offre ({@link PiecesFiche}) : remplacés en bloc (l'ordre est la position), figés à la validation,
 * copiés à la révision.
 *
 * <p>Impression : {@code {{SOUSCRITERES.B06-TP-03}}}, une ligne par sous-critère (« a) Approche technique et méthodologie : 20
 * points ») ; lisible par une condition ({@code SOUSCRITERES.B06-TP-03 renseigne}).</p>
 */
@Component
@Transactional
public class SousCriteresFiche {

    /** Les cinq critères techniques de la DPIC, dans l'ordre. */
    public static final List<String> CRITERES = List.of("B06-TP-02", "B06-TP-03", "B06-TP-04", "B06-TP-05", "B06-TP-06");
    public static final String PREFIXE_JETON = "SOUSCRITERES.";

    private final FicheSousCritereRepository repository;

    public SousCriteresFiche(FicheSousCritereRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SousCritereDto> lister(Integer idFiche) {
        if (idFiche == null) {
            return List.of();
        }
        return repository.findByIdFicheOrderByOrdreAsc(idFiche).stream()
                .map(s -> new SousCritereDto(s.getOrdre(), s.getCritere(), s.getLibelle(), s.getPoints())).toList();
    }

    /** Critère connu, libellé obligatoire (≤ 300), points &gt; 0 ; 400 {@code sousCriteres[i].…}. */
    public static void valider(List<SousCritereDto> lignes) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            SousCritereDto s = lignes.get(i);
            String q = "sousCriteres[" + i + "].";
            if (s == null) {
                erreurs.add(new ErrorResponse.FieldError("sousCriteres[" + i + "]", "Ligne vide."));
                continue;
            }
            if (s.critere() == null || !CRITERES.contains(s.critere().trim())) {
                erreurs.add(new ErrorResponse.FieldError(q + "critere", "Critère inconnu : « " + s.critere() + " » (" + String.join(", ", CRITERES) + ")."));
            }
            if (s.libelle() == null || s.libelle().isBlank()) {
                erreurs.add(new ErrorResponse.FieldError(q + "libelle", "Le libellé est obligatoire."));
            } else if (s.libelle().trim().length() > 300) {
                erreurs.add(new ErrorResponse.FieldError(q + "libelle", "Le libellé : 300 caractères au plus."));
            }
            if (s.points() == null || s.points().signum() <= 0) {
                erreurs.add(new ErrorResponse.FieldError(q + "points", "Les points d'un sous-critère sont positifs."));
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
    }

    public void remplacer(Integer idFiche, List<SousCritereDto> lignes) {
        repository.deleteByIdFiche(idFiche);
        repository.flush();
        int ordre = 0;
        for (SousCritereDto s : lignes) {
            repository.save(new FicheSousCritere(null, idFiche, s.critere().trim(), ++ordre, s.libelle().trim(), s.points()));
        }
    }

    /** La révision copie la liste de la version précédente. */
    public void copier(Integer idFicheSource, Integer idFicheCible) {
        for (SousCritereDto s : lister(idFicheSource)) {
            repository.save(new FicheSousCritere(null, idFicheCible, s.critere(), s.ordre(), s.libelle(), s.points()));
        }
    }

    /** La somme des points des sous-critères, par critère détaillé. */
    public static Map<String, BigDecimal> sommes(List<SousCritereDto> lignes) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (SousCritereDto s : lignes) {
            m.merge(s.critere(), s.points(), BigDecimal::add);
        }
        return m;
    }

    /** Les jetons {@code SOUSCRITERES.<critère>} : « a) libellé : n points », une ligne par sous-critère du critère. */
    public static Map<String, String> jetons(List<SousCritereDto> lignes) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String c : CRITERES) {
            List<SousCritereDto> l = lignes.stream().filter(s -> c.equals(s.critere())).toList();
            if (l.isEmpty()) {
                continue;
            }
            List<String> out = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) {
                out.add((char) ('a' + Math.min(i, 25)) + ") " + l.get(i).libelle() + " : " + l.get(i).points().stripTrailingZeros().toPlainString()
                        + " points");
            }
            m.put(PREFIXE_JETON + c, String.join("\n", out));
        }
        return m;
    }
}
