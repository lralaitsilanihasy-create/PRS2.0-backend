package cnm.prs.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.QuorumDto;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.ControleResultat;
import cnm.prs.entity.Dossier;

/**
 * ⚠️ R5 (manuel révisé, lot 1, §B6 ; arbitrage Q13 du pilote, n° 6) — <strong>2-III-C P3 / P14 « quorum, président, empêchements »</strong> :
 * le quorum de la commission (R1, §B2) est atteint, avec le président ou son représentant, au PV de la séance d'ouverture et au
 * rapport d'évaluation. Bloquant. Sans objet hors de l'application.
 */
@Component
public class ControleQuorumCommission implements ControleAutomatique {

    public static final String CODE = "QUORUM_COMMISSION";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String reference() {
        return "2-III-C P3 / P14";
    }

    @Override
    public String libelle() {
        return "Quorum de la commission, président ou représentant, empêchements : PV d'ouverture et rapport d'évaluation";
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
        QuorumDto ouverture = pv == null ? null : pv.quorum();
        QuorumDto rapportQuorum = rapport == null ? null : rapport.quorum();
        v.put("procedureEnLigne", true);
        v.put("pvOuverture", resume(ouverture));
        v.put("rapport", resume(rapportQuorum));
        return new Resultat(satisfait(ouverture) && satisfait(rapportQuorum) ? ControleResultat.CONFORME : ControleResultat.NON_CONFORME, v);
    }

    /** Atteint, et le président (ou son représentant) a signé quand il est requis. */
    static boolean satisfait(QuorumDto q) {
        return q != null && q.atteint() && (!q.presidentRequis() || q.presidentSigne());
    }

    private static Map<String, Object> resume(QuorumDto q) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (q == null) {
            m.put("constate", false);
            return m;
        }
        m.put("constate", true);
        m.put("regle", q.regle());
        m.put("requis", q.requis());
        m.put("obtenus", q.obtenus());
        m.put("atteint", q.atteint());
        m.put("presidentRequis", q.presidentRequis());
        m.put("presidentSigne", q.presidentSigne());
        if (q.representant() != null) {
            m.put("representant", q.representantNom() == null ? q.representant() : q.representantNom());
        }
        return m;
    }
}
