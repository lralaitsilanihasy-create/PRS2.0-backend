package cnm.prs.service;

import java.util.function.Supplier;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.Dossier;

/**
 * ⚠️ R5 (manuel révisé, lot 1, §B6) — ce qu'une règle peut lire du dossier soumis : sa procédure ({@code idDmc}), si elle est
 * conduite dans l'application, et les écrans vivants lus une seule fois ({@code null} quand la donnée n'existe pas, sans exception :
 * l'existence est vérifiée avant la lecture, comme pour les instantanés).
 */
public final class ControlesContexte {

    private final Dossier dossier;
    private final Long idDmc;
    private final boolean enLigne;
    private final Supplier<SeanceDto> seance;
    private final Supplier<EvaluationDto> evaluation;
    private SeanceDto seanceLue;
    private boolean seanceChargee;
    private EvaluationDto evaluationLue;
    private boolean evaluationChargee;

    public ControlesContexte(Dossier dossier, Long idDmc, boolean enLigne, Supplier<SeanceDto> seance, Supplier<EvaluationDto> evaluation) {
        this.dossier = dossier;
        this.idDmc = idDmc;
        this.enLigne = enLigne;
        this.seance = seance;
        this.evaluation = evaluation;
    }

    public Dossier dossier() {
        return dossier;
    }

    public Long idDmc() {
        return idDmc;
    }

    /** La procédure est conduite dans l'application (fiche, attribution en ligne…) : les écrans existent, les règles s'appliquent. */
    public boolean enLigne() {
        return enLigne;
    }

    /** La séance d'ouverture, ou {@code null} si elle n'est pas ouverte. */
    public SeanceDto seance() {
        if (!seanceChargee) {
            seanceLue = seance == null ? null : seance.get();
            seanceChargee = true;
        }
        return seanceLue;
    }

    /** L'évaluation, ou {@code null} si elle n'est pas ouverte. */
    public EvaluationDto evaluation() {
        if (!evaluationChargee) {
            evaluationLue = evaluation == null ? null : evaluation.get();
            evaluationChargee = true;
        }
        return evaluationLue;
    }

    public boolean seanceOuverte() {
        return seance() != null;
    }

    /** Le PV de la séance d'ouverture ({@code null} sans séance). */
    public SeanceDto.Pv pvOuverture() {
        SeanceDto s = seance();
        return s == null ? null : s.pv();
    }

    public boolean evaluationOuverte() {
        return evaluation() != null;
    }

    /** Le rapport d'évaluation ({@code null} sans évaluation). */
    public EvaluationDto.Rapport rapport() {
        EvaluationDto e = evaluation();
        return e == null ? null : e.rapport();
    }
}
