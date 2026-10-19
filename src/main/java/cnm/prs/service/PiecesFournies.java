package cnm.prs.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.PvExamen;
import cnm.prs.repository.PvExamenRepository;

/**
 * ⚠️ 2026-10-17 (demande front « pieces-dmc-par-les-donnees », §B1 ; demande du pilote) — les pièces d'un dossier né d'une fiche que
 * l'application <strong>fournit par ses données</strong>, quel que soit le mode de remise (R8 ne les concerne plus) :
 * <ul>
 *   <li>{@code FICHE_PRESENTATION} : la fiche validée, nominativement, par la PRMP (Q1 : la validation tracée vaut signature, comme
 *   R2 ; confirmé par le pilote le 17/10) ; rendue par {@code GET /api/dossiers/{id}/fiche-presentation} ;</li>
 *   <li>⚠️ 17/10 (« canevas-rapport-evaluation », §B1) — {@code CANEVAS_RAPPORT} : le canevas produit de la fiche, hors
 *   contrat-cadre ; rendu par {@code GET /api/dossiers/{id}/canevas-rapport} ;</li>
 *   <li>{@code CALENDRIER} : le calendrier annuel produit depuis le plan de la ligne (V115), quand ce plan est dans l'application ;</li>
 *   <li>{@code AGPM_CONTROLE}, {@code PPM_CONTROLE} : le plan de la ligne, quand son PV d'examen est <strong>signé</strong> dans
 *   l'application.</li>
 * </ul>
 * ⚠️ 2026-10-19 (« dossier-marche-contrat-cadre », §B4) — un dossier de marché né d'une attribution reçoit les siennes
 * ({@link #duMarche}) : fiche de présentation, PV de la Commission sur le DAO, PV de validation de la CAO.
 * Un dossier sans fiche ({@code ID_DMC} nul), ou dont la fiche n'a pas de version validée, n'en a aucune : rien ne prouve les données.
 * Q3 : la règle tient à la fiche, pas au sous-type.
 */
@Component
@Transactional(readOnly = true)
public class PiecesFournies {

    public static final String PAR_APPLICATION = "APPLICATION";
    public static final String FICHE_PRESENTATION = "FICHE_PRESENTATION";
    public static final String CALENDRIER = "CALENDRIER";
    public static final String CANEVAS_RAPPORT = "CANEVAS_RAPPORT";
    public static final Set<String> CONTROLE_DU_PLAN = Set.of("AGPM_CONTROLE", "PPM_CONTROLE");

    /** Ce qui fournit une pièce : la route qui la rend, et pour le contrôle du plan, le plan et son PV signé. */
    public record Fourniture(String lien, Integer idDossierPlan, Integer idPvPlan) {
    }

    /** ⚠️ 2026-10-19 (« dossier-marche-contrat-cadre », §B4) — les pièces du dossier de marché né d'une attribution. */
    public static final String PV_CNM_DMC = "PV_CNM_DMC";
    public static final String PV_VALIDATION_CAO = "PV_VALIDATION_CAO";
    static final String RAPPORT_ANALYSE = "RAPPORT_ANALYSE";

    private final FicheMarcheService fiches;
    private final CalendrierAnnuelService calendrier;
    private final PvExamenRepository pvs;
    private final cnm.prs.repository.AttributionRepository attributions;
    private final cnm.prs.repository.DossierRepository dossiers;
    private final cnm.prs.repository.PieceJointeDossierRepository pieces;
    private final cnm.prs.repository.TypePieceJointeRepository types;

    public PiecesFournies(FicheMarcheService fiches, CalendrierAnnuelService calendrier, PvExamenRepository pvs,
            cnm.prs.repository.AttributionRepository attributions, cnm.prs.repository.DossierRepository dossiers,
            cnm.prs.repository.PieceJointeDossierRepository pieces, cnm.prs.repository.TypePieceJointeRepository types) {
        this.fiches = fiches;
        this.calendrier = calendrier;
        this.pvs = pvs;
        this.attributions = attributions;
        this.dossiers = dossiers;
        this.pieces = pieces;
        this.types = types;
    }

    /**
     * ⚠️ 2026-10-19 (demande front « dossier-marche-contrat-cadre », §B2) — la procédure d'un dossier de marché né d'une attribution
     * ({@code t_attribution.ID_DOSSIER}) : la fiche, le lot, le dossier de mise en concurrence ; nul pour tout autre dossier.
     */
    public cnm.prs.dto.DossierDto.Procedure procedureDe(Dossier d) {
        if (d.getIdDossier() == null || !"DDM".equals(d.getIdTypeDossier())) {
            return null;
        }
        return attributions.findFirstByIdDossier(d.getIdDossier()).map(a -> {
            Dossier mec = dossiers.findIdDossierByIdDmc(a.getIdDmc()).flatMap(dossiers::findById).orElse(null);
            return new cnm.prs.dto.DossierDto.Procedure(a.getIdDmc(), a.getLot(), mec == null ? null : mec.getIdDossier(),
                    mec == null ? null : mec.getRefeDossier());
        }).orElse(null);
    }

    /** Par code de pièce, ce qui la fournit ; vide pour un dossier sans fiche validée. */
    public Map<String, Fourniture> de(Dossier d) {
        Map<String, Fourniture> out = new HashMap<>();
        cnm.prs.dto.DossierDto.Procedure procedure = procedureDe(d);
        if (procedure != null) {
            return duMarche(d, procedure);
        }
        FicheMarcheService.EtatVersion ev = d.getIdDmc() == null ? null : fiches.etatValide(d.getIdDmc()).orElse(null);
        if (ev == null) {
            return out;
        }
        String racine = d.getIdDossier() == null ? null : "/api/dossiers/" + d.getIdDossier();
        out.put(FICHE_PRESENTATION, new Fourniture(racine == null ? null : racine + "/fiche-presentation", null, null));
        // ⚠️ 2026-10-17 (« canevas-rapport-evaluation », §B1) — le canevas, hors contrat-cadre (qui ne l'exige pas).
        if (CanevasRapportService.prevu(ev)) {
            out.put(CANEVAS_RAPPORT, new Fourniture(racine == null ? null : racine + "/canevas-rapport", null, null));
        }
        Fourniture plan = planDe(d);
        if (plan == null) {
            return out;
        }
        out.put(CALENDRIER, new Fourniture(racine == null ? null : racine + "/calendrier-annuel", plan.idDossierPlan(), null));
        if (plan.idPvPlan() != null) {
            CONTROLE_DU_PLAN.forEach(code -> out.put(code, plan));
        }
        return out;
    }

    /**
     * ⚠️ 2026-10-19 (« dossier-marche-contrat-cadre », §B4) — un dossier de marché né d'une attribution : sa fiche de présentation
     * (fiche de la procédure validée), le PV <strong>signé</strong> de la Commission sur le dossier de mise en concurrence, et le PV de
     * validation de la CAO, dont le rapport d'évaluation signé tient lieu (« il vaut procès-verbal de validation » ; lien : la pièce
     * jointe d'office). Une pièce que les données ne prouvent pas n'est pas fournie.
     */
    private Map<String, Fourniture> duMarche(Dossier d, cnm.prs.dto.DossierDto.Procedure procedure) {
        Map<String, Fourniture> out = new HashMap<>();
        String racine = "/api/dossiers/" + d.getIdDossier();
        if (fiches.etatValide(procedure.idDmc()).isPresent()) {
            out.put(FICHE_PRESENTATION, new Fourniture(racine + "/fiche-presentation", null, null));
        }
        if (procedure.idDossierMiseEnConcurrence() != null) {
            pvs.findSignesParDossierRows(procedure.idDossierMiseEnConcurrence()).stream().findFirst()
                    .ifPresent(pv -> out.put(PV_CNM_DMC, new Fourniture("/api/pv-examens/" + pv.getIdPv() + "/document", null, null)));
        }
        types.findFirstByCode(RAPPORT_ANALYSE).flatMap(t -> pieces.findByIdDossier(d.getIdDossier()).stream()
                .filter(p -> t.getIdTypePiece().equals(p.getIdTypePiece()) && p.getProduitePar() != null).findFirst())
                .ifPresent(p -> out.put(PV_VALIDATION_CAO, new Fourniture("/api/piece-jointe-dossiers/" + p.getIdPiece() + "/contenu", null, null)));
        return out;
    }

    /**
     * §B3 — le plan qui porte la ligne en vigueur de la fiche du dossier, et le PV <strong>signé</strong> de son examen (nul s'il
     * n'y en a pas) ; lien : le document du PV. Nul pour un dossier sans fiche, ou dont la ligne n'est pas dans l'application.
     */
    public Fourniture planDe(Dossier d) {
        Integer idPlan = calendrier.idPlanDeFiche(d.getIdDmc()).orElse(null);
        if (idPlan == null) {
            return null;
        }
        Integer idPv = pvs.findSignesParDossierRows(idPlan).stream().findFirst().map(PvExamen::getIdPv).orElse(null);
        return new Fourniture(idPv == null ? null : "/api/pv-examens/" + idPv + "/document", idPlan, idPv);
    }
}
