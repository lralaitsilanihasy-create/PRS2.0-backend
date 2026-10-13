package cnm.prs.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.ControleResultat;
import cnm.prs.entity.Dossier;

/**
 * ⚠️ R5 (manuel révisé, lot 1, §B6 ; arbitrage Q13 du pilote, n° 7 et 8) — <strong>2-III-A P5 « documents d'évaluation signés »</strong> :
 * un dossier de marché conduit dans l'application n'est recevable que si le PV de la séance d'ouverture est signé et si le rapport
 * d'évaluation est signé. Bloquant. Sans objet pour un dossier de marché saisi (procédure hors de l'application).
 */
@Component
public class ControleDocumentsEvaluationSignes implements ControleAutomatique {

    public static final String CODE = "DOCUMENTS_EVALUATION_SIGNES";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String reference() {
        return "2-III-A P5";
    }

    @Override
    public String libelle() {
        return "Documents d'évaluation signés : PV de la séance d'ouverture et rapport d'évaluation";
    }

    @Override
    public boolean bloquant() {
        return true;
    }

    @Override
    public boolean concerne(Dossier dossier) {
        return InstantaneService.FAMILLE_DDM.equals(dossier.getIdTypeDossier());
    }

    @Override
    public Resultat evaluer(Dossier dossier, ControlesContexte c) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (!c.enLigne()) {
            v.put("procedureEnLigne", false);
            return new Resultat(ControleResultat.NON_APPLICABLE, v);
        }
        SeanceDto.Pv pv = c.pvOuverture();
        EvaluationDto.Rapport rapport = c.rapport();
        boolean pvSigne = pv != null && pv.signe();
        boolean rapportSigne = rapport != null && rapport.signe();
        v.put("procedureEnLigne", true);
        v.put("seanceOuverte", c.seanceOuverte());
        v.put("pvOuvertureSigne", pvSigne);
        v.put("evaluationOuverte", c.evaluationOuverte());
        v.put("rapportSigne", rapportSigne);
        if (rapport != null && rapport.signeLe() != null) {
            v.put("rapportSigneLe", rapport.signeLe().toString());
        }
        return new Resultat(pvSigne && rapportSigne ? ControleResultat.CONFORME : ControleResultat.NON_CONFORME, v);
    }
}
