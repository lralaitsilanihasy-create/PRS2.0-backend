package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.entity.ControleResultat;
import cnm.prs.entity.Dossier;

/**
 * ⚠️ R9b (manuel révisé pour l'application, lot 2, §B4 n° 9 ; art. 46-47 ; arbitrage QB2 du 14/10) — <strong>montant évalué calculé
 * par l'application</strong> : une correction retenue hors du calcul du serveur ({@code REPORT}, {@code AUTRE}, motivée) est
 * permise mais <strong>signalée</strong> au dossier de marché, avec son motif, pour que le Membre la lise et la lève. Non bloquant.
 * Sans objet pour une procédure hors de l'application. Les propositions financières des prestations intellectuelles, saisies à part,
 * ne sont pas lues ici (le journal de la procédure les cite).
 */
@Component
public class ControleCorrectionsHorsCalcul implements ControleAutomatique {

    public static final String CODE = "CORRECTIONS_HORS_CALCUL";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String reference() {
        return "Loi 2016-055 art. 46-47";
    }

    @Override
    public String libelle() {
        return "Montant évalué calculé par l'application : corrections hors calcul signalées";
    }

    @Override
    public boolean bloquant() {
        return false;
    }

    @Override
    public boolean concerne(Dossier dossier) {
        return InstantaneService.FAMILLE_DDM.equals(dossier.getIdTypeDossier());
    }

    @Override
    public Resultat evaluer(Dossier dossier, ControlesContexte c) {
        Map<String, Object> v = new LinkedHashMap<>();
        EvaluationDto e = c.enLigne() ? c.evaluation() : null;
        if (e == null) {
            v.put("procedureEnLigne", c.enLigne());
            v.put("evaluationOuverte", false);
            return new Resultat(ControleResultat.NON_APPLICABLE, v);
        }
        List<Map<String, Object>> ecarts = new ArrayList<>();
        for (EvaluationDto.Lot l : e.lots() == null ? List.<EvaluationDto.Lot>of() : e.lots()) {
            for (EvaluationDto.OffreEvaluee o : l.offres() == null ? List.<EvaluationDto.OffreEvaluee>of() : l.offres()) {
                if (o.evaluation() == null || o.evaluation().corrections() == null) {
                    continue;
                }
                for (EvaluationDto.Correction x : o.evaluation().corrections()) {
                    if (Boolean.TRUE.equals(x.ecart()) && Boolean.TRUE.equals(x.retenue())) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("lot", l.lot());
                        m.put("offre", o.numero());
                        m.put("libelle", x.libelle());
                        m.put("regle", x.regle());
                        m.put("avant", x.avant());
                        m.put("apres", x.apres());
                        m.put("motif", x.motif());
                        ecarts.add(m);
                    }
                }
            }
        }
        v.put("procedureEnLigne", true);
        v.put("corrections", ecarts);
        return new Resultat(ecarts.isEmpty() ? ControleResultat.CONFORME : ControleResultat.NON_CONFORME, v);
    }
}
