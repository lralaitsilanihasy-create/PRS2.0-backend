package cnm.prs.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DossierRepository;

/**
 * ⚠️ R9c (manuel révisé pour l'application, lot 2, §B4 n° 15 ; arbitrage Q13 du pilote) — <strong>l'immutabilité des étapes
 * closes</strong> : dès qu'un dossier de marché (DDM) d'une procédure est soumis à la Commission, la séance d'ouverture, la séance
 * financière, l'évaluation et l'AMI de cette procédure ne s'écrivent plus (409 {@code ETAPE_CLOSE}). Le dossier soumis est lu sur les
 * attributions de la procédure ; la seule voie de réécriture est la <strong>reprise tracée</strong> (reprise après avis défavorable,
 * réattribution après retrait : {@code t_attribution_reprise}) qui supprime l'attribution portant le dossier — le verrou se lève de
 * lui-même, la reprise reste au journal. Un DDM encore brouillon ne verrouille rien.
 */
@Service
public class EtapesCloses {

    public static final String CODE = "ETAPE_CLOSE";

    private final AttributionRepository attributions;
    private final DossierRepository dossiers;
    private final cnm.prs.repository.AttributionRepriseRepository reprises;
    private final cnm.prs.repository.EvaluationRepository evaluations;

    public EtapesCloses(AttributionRepository attributions, DossierRepository dossiers, cnm.prs.repository.AttributionRepriseRepository reprises,
            cnm.prs.repository.EvaluationRepository evaluations) {
        this.attributions = attributions;
        this.dossiers = dossiers;
        this.reprises = reprises;
        this.evaluations = evaluations;
    }

    /** Le premier dossier de marché soumis de la procédure (avec son lot), s'il en existe. */
    @Transactional(readOnly = true)
    public Optional<Map.Entry<Integer, Dossier>> dossierSoumis(Long idDmc) {
        for (Attribution a : attributions.findByIdDmcOrderByLotAsc(idDmc)) {
            if (a.getIdDossier() == null) {
                continue;
            }
            Optional<Dossier> d = dossiers.findById(a.getIdDossier()).filter(EtapesCloses::soumis);
            if (d.isPresent()) {
                return Optional.of(Map.entry(a.getLot(), d.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * 409 {@code ETAPE_CLOSE} {@code details { idDossierMarche, lot }} si un dossier de marché de la procédure est soumis — ⚠️ hors
     * <strong>reprise tracée</strong> (question du front du 14/10, procédure allotie ; arbitrage : garde + levée tracée) : tant qu'une
     * reprise ou une réattribution ({@code t_attribution_reprise}) postérieure à la soumission de ce dossier a rouvert l'évaluation, et
     * que celle-ci n'est pas close, le verrou ne joue pas ; il revient à la clôture de l'évaluation.
     */
    @Transactional(readOnly = true)
    public void exigerOuvertes(Long idDmc) {
        dossierSoumis(idDmc).filter(e -> !repriseOuverte(idDmc, e.getValue())).ifPresent(e -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("idDossierMarche", e.getValue().getIdDossier());
            details.put("lot", e.getKey());
            throw new BusinessRuleException("Le dossier de marché n° " + e.getValue().getIdDossier() + " (lot " + e.getKey() + ") est soumis à "
                    + "la Commission : la séance, l'évaluation et l'AMI de la procédure ne se modifient plus. Seule une reprise de l'évaluation "
                    + "après avis défavorable, ou une réattribution, les rouvre.", CODE, e.getValue().getIdDossier(), details);
        });
    }

    /** Une reprise ou réattribution postérieure à la soumission du dossier a rouvert l'évaluation, qui n'est pas close. */
    boolean repriseOuverte(Long idDmc, Dossier d) {
        if (!reprises.existsByIdDmcAndLeAfter(idDmc, d.getDateSoumission())) {
            return false;
        }
        return evaluations.findById(idDmc).map(ev -> !cnm.prs.entity.Evaluation.CLOSE.equals(ev.getEtat())).orElse(false);
    }

    static boolean soumis(Dossier d) {
        return d.getDateSoumission() != null && !StatutDossier.BROUILLON.name().equals(d.getStatut());
    }
}
