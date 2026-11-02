package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MotifRestreintDto;
import cnm.prs.entity.FicheMotifRestreint;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.FicheMotifRestreintRepository;

/**
 * ⚠️ V155 (2026-11-02, demande front « appel-offres-restreint », §B2) — le <strong>motif du recours à l'appel d'offres
 * restreint</strong> d'une version de fiche (loi n° 2016-055, art. 38-IV ; décret n° 2019-1310, art. 10) : l'un des quatre cas et sa
 * justification. Comme les sous-critères ({@link SousCriteresFiche}) : écrit en brouillon, figé à la validation, copié à la
 * révision ; exigé au bilan d'une fiche dont la ligne du plan est en mode restreint ({@code MOTIF_RESTREINT}).
 *
 * <p>Impression : {@code {{MOTIF_RESTREINT.motif}}} (le libellé du cas) et {@code {{MOTIF_RESTREINT.justification}}}.</p>
 */
@Component
@Transactional
public class MotifRestreintFiche {

    /** Les quatre cas, dans l'ordre de la loi, avec leur libellé. */
    public static final Map<String, String> MOTIFS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("URGENCE_AVEREE", "Urgence avérée");
        m.put("CONFIDENTIEL", "Caractère confidentiel");
        m.put("PRESTATAIRE_DEFAILLANT", "Défaillance du prestataire");
        m.put("PETIT_NOMBRE", "Petit nombre de prestataires");
        MOTIFS = java.util.Collections.unmodifiableMap(m);
    }

    public static final String URGENCE_AVEREE = "URGENCE_AVEREE";
    public static final String PREFIXE_JETON = "MOTIF_RESTREINT.";
    static final int JUSTIFICATION_MAX = 4000;

    private final FicheMotifRestreintRepository repository;

    public MotifRestreintFiche(FicheMotifRestreintRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<MotifRestreintDto> lire(Integer idFiche) {
        if (idFiche == null) {
            return Optional.empty();
        }
        return repository.findById(idFiche).map(MotifRestreintFiche::dto);
    }

    static MotifRestreintDto dto(FicheMotifRestreint m) {
        return new MotifRestreintDto(m.getMotif(), MOTIFS.get(m.getMotif()), m.getJustification(), m.getSaisiLe());
    }

    /** Le motif connu (casse ignorée) et la justification, obligatoires ; 400 nominatif {@code motif}, {@code justification}. */
    static MotifRestreintDto valider(MotifRestreintDto recu) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        String motif = recu == null || recu.motif() == null ? null : recu.motif().trim().toUpperCase(Locale.ROOT);
        String justification = recu == null || recu.justification() == null ? null : recu.justification().trim();
        if (motif == null || motif.isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError("motif", "Le motif du recours à l'appel d'offres restreint est obligatoire."));
        } else if (!MOTIFS.containsKey(motif)) {
            erreurs.add(new ErrorResponse.FieldError("motif", "Motif inconnu : « " + recu.motif() + " » (" + String.join(", ", MOTIFS.keySet())
                    + ")."));
        }
        if (justification == null || justification.isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError("justification", "La justification du recours est obligatoire."));
        } else if (justification.length() > JUSTIFICATION_MAX) {
            erreurs.add(new ErrorResponse.FieldError("justification", "La justification : " + JUSTIFICATION_MAX + " caractères au plus."));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        return new MotifRestreintDto(motif, MOTIFS.get(motif), justification, null);
    }

    public MotifRestreintDto ecrire(Integer idFiche, MotifRestreintDto valide, LocalDateTime le, String par) {
        return dto(repository.save(new FicheMotifRestreint(idFiche, valide.motif(), valide.justification(), le, par)));
    }

    public void copier(Integer source, Integer cible) {
        repository.findById(source).ifPresent(m -> repository.save(new FicheMotifRestreint(cible, m.getMotif(), m.getJustification(),
                m.getSaisiLe(), m.getSaisiPar())));
    }

    /** Les jetons d'impression : le libellé du cas et la justification ; vides sans motif. */
    static Map<String, String> jetons(Optional<MotifRestreintDto> motif) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(PREFIXE_JETON + "motif", motif.map(MotifRestreintDto::libelle).orElse(""));
        m.put(PREFIXE_JETON + "justification", motif.map(MotifRestreintDto::justification).orElse(""));
        return m;
    }
}
