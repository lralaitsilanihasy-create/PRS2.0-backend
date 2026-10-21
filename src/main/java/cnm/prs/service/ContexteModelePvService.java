package cnm.prs.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.FichePresentationDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.Localite;
import cnm.prs.entity.PvExamen;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.LocaliteRepository;
import cnm.prs.repository.PvExamenRepository;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », §B3 et §B4, lot G1) — le <strong>contexte d'un modèle de PV sur un
 * dossier réel</strong> : les variables du catalogue lues sur le dossier, son dernier PV d'examen (s'il en a un) et, pour un dossier
 * de procédure, sa fiche de présentation et la proposition de l'évaluation. Sert l'aperçu ; la production des PV (lot G2) le
 * reprendra.
 *
 * <p>Sans PV, les variables du PV restent vides, sauf l'avis choisi pour l'aperçu. Les variables de procédure sont celles de la fiche
 * de présentation (mêmes règles, un seul endroit) : un dossier qui n'est pas né d'une fiche validée les laisse vides.</p>
 */
@Service
public class ContexteModelePvService {

    private static final Map<String, String> AVIS = Map.of("FAV", "favorable", "FAVR", "favorable avec réserves", "DEF", "défavorable",
            "NSP", "ne se prononce pas");

    private final DossierRepository dossiers;
    private final ExamenRepository examens;
    private final PvExamenRepository pvs;
    private final LocaliteRepository localites;
    private final ControleurRepository controleurs;
    private final AttributionRepository attributions;
    private final PvDocumentService pvDocuments;
    private final FicheMarcheService fiches;
    private final ObjectProvider<FichePresentationService> fichePresentation;
    private final ObjectProvider<EvaluationService> evaluation;
    private final LettreRenvoiRepository lettres;
    private final ObjectProvider<LettreRenvoiService> lettresService;

    public ContexteModelePvService(DossierRepository dossiers, ExamenRepository examens, PvExamenRepository pvs,
            LocaliteRepository localites, ControleurRepository controleurs, AttributionRepository attributions,
            PvDocumentService pvDocuments, FicheMarcheService fiches, ObjectProvider<FichePresentationService> fichePresentation,
            ObjectProvider<EvaluationService> evaluation, LettreRenvoiRepository lettres,
            ObjectProvider<LettreRenvoiService> lettresService) {
        this.dossiers = dossiers;
        this.examens = examens;
        this.pvs = pvs;
        this.localites = localites;
        this.controleurs = controleurs;
        this.attributions = attributions;
        this.pvDocuments = pvDocuments;
        this.fiches = fiches;
        this.fichePresentation = fichePresentation;
        this.evaluation = evaluation;
        this.lettres = lettres;
        this.lettresService = lettresService;
    }

    /**
     * @param avis   l'avis de l'aperçu ({@code FAV}, {@code FAVR}, {@code DEF}, {@code NSP}) ; nul : celui du dernier PV
     * @param saisis les champs saisis de l'aperçu
     */
    @Transactional(readOnly = true)
    public ModelePvWord.Contexte pour(Integer idDossier, String avis, Map<String, String> saisis) {
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        Map<String, ModelePvWord.Valeur> v = new LinkedHashMap<>();
        Map<String, List<Map<String, ModelePvWord.Valeur>>> listes = new LinkedHashMap<>();
        Map<String, String> cles = new LinkedHashMap<>();

        PvExamen pv = dernierPv(idDossier);
        PvDocumentContexte pc = pv == null ? null : pvDocuments.contexteDuDossier(pv);
        String idLocalite = pv == null ? d.getIdLocalite() : examens.findLocaliteByExamen(pv.getIdExamen()).orElse(d.getIdLocalite());
        Localite loc = idLocalite == null ? null : localites.findById(idLocalite).orElse(null);
        String localite = loc == null ? idLocalite : loc.getLibelleLocalite();
        boolean centrale = Localite.estCentrale(idLocalite);

        texte(v, "COMMISSION.NOM", centrale ? "Commission Nationale des Marchés"
                : "Commission Régionale des Marchés" + (localite == null ? "" : " de " + localite));
        texte(v, "COMMISSION.LOCALITE", localite);
        texte(v, "COMMISSION.CHEF_LIEU", loc == null || loc.getChefLieu() == null || loc.getChefLieu().isBlank() ? localite : loc.getChefLieu());
        texte(v, "DOSSIER.REFERENCE", d.getRefeDossier());
        texte(v, "DOSSIER.SOUS_TYPE", d.getSousTypeDossier() == null ? d.getIdSousType() : d.getSousTypeDossier().getLibelleSousType());
        texte(v, "DOSSIER.ENTITE", d.getEntiteContract() == null ? null : d.getEntiteContract().getLibelleEntite());
        texte(v, "DOSSIER.PRMP", d.getPrmp() == null ? null
                : ActeurDirectory.nomCanonique(d.getPrmp().getNomPrmp(), d.getPrmp().getPrenomsPrmp()));
        Integer exercice = pc != null && pc.anneeExercice() != null ? pc.anneeExercice()
                : d.getDateRef() != null ? Integer.valueOf(d.getDateRef().getYear()) : null;
        texte(v, "DOSSIER.EXERCICE", exercice == null ? null : String.valueOf(exercice));
        String avisRetenu = avis != null ? avis : pv == null ? null : pv.getIdAvis();
        texte(v, "PV.AVIS", avisRetenu == null ? null : AVIS.getOrDefault(avisRetenu, avisRetenu));
        v.put("PV.DATE_DU_JOUR", ModelePvWord.Valeur.date(LocalDate.now()));
        List<Map<String, ModelePvWord.Valeur>> observations = new ArrayList<>();
        if (pc != null) {
            v.put("DOSSIER.DATE_RECEPTION", ModelePvWord.Valeur.date(pc.dateReception()));
            texte(v, "PV.REFERENCE", pc.refPv());
            v.put("PV.DATE_EXAMEN", ModelePvWord.Valeur.date(pc.dateExamen()));
            texte(v, "PV.PRESIDENT", pc.nomPresident());
            texte(v, "PV.CHEF_COMMISSION", pc.nomChefCommission());
            texte(v, "PV.MEMBRE", pc.nomMembre());
            for (PvDocumentContexte.Observation o : pc.observations()) {
                Map<String, ModelePvWord.Valeur> item = new LinkedHashMap<>();
                item.put("OBS.POINT", ModelePvWord.Valeur.de(o.pointControle()));
                item.put("OBS.AU_LIEU_DE", ModelePvWord.Valeur.de(o.auLieuDe()));
                item.put("OBS.LIRE", ModelePvWord.Valeur.de(o.lire()));
                observations.add(item);
            }
            viseur(v, pv, centrale);
        }
        listes.put("OBSERVATIONS", observations);

        cles.put("avis", avisRetenu);
        cles.put("portee", centrale ? "centrale" : "regionale");
        cles.put("sousType", d.getIdSousType());
        cles.put("agpm", DossierIntegriteService.SOUS_TYPE_PPM_AGPM.equals(d.getIdSousType()) ? "oui" : "non");
        procedure(d, v, listes, cles);
        return new ModelePvWord.Contexte(v, listes, cles, saisis == null ? Map.of() : saisis);
    }

    /**
     * ⚠️ 2026-10-21 (« modele-lettre-renvoi », B1) — le contexte d'une lettre de renvoi : celui du dossier, plus les variables
     * {@code LETTRE.*} de la lettre {@code idLettre} (une lettre du dossier), à défaut de la dernière du dossier, même non signée ; la
     * portée est celle de la lettre (localité du dossier, à défaut de la réception), comme le choix du fichier intégré. 404 pour une
     * lettre d'un autre dossier, 409 {@code LETTRE_ABSENTE} pour un dossier sans lettre.
     */
    @Transactional(readOnly = true)
    public ModelePvWord.Contexte pourLettre(Integer idDossier, Integer idLettre, Map<String, String> saisis) {
        ModelePvWord.Contexte base = pour(idDossier, null, saisis);
        LettreRenvoi lettre;
        if (idLettre != null) {
            lettre = lettres.findById(idLettre).filter(l -> idDossier.equals(l.getIdDossier()))
                    .orElseThrow(() -> new ResourceNotFoundException("La lettre " + idLettre + " n'est pas une lettre du dossier " + idDossier + "."));
        } else {
            lettre = lettres.findFirstByIdDossierOrderByIdLettreDesc(idDossier).orElseThrow(() -> new BusinessRuleException(
                    "Le dossier " + idDossier + " n'a pas de lettre de renvoi : l'aperçu d'une lettre se fait sur un dossier renvoyé.",
                    "LETTRE_ABSENTE"));
        }
        LettreRenvoiService.ValeursLettre l = lettresService.getObject().valeurs(lettre.getIdLettre());
        Map<String, ModelePvWord.Valeur> v = new LinkedHashMap<>(base.variables());
        v.put("LETTRE.DATE", ModelePvWord.Valeur.date(l.date()));
        v.put("LETTRE.DATE_EXAMEN", ModelePvWord.Valeur.date(l.dateExamen()));
        texte(v, "LETTRE.CORPS", l.corps());
        texte(v, "LETTRE.SIGNATAIRE", l.signataire());
        texte(v, "LETTRE.LOCALITE", l.localite());
        Map<String, String> cles = new LinkedHashMap<>(base.cles());
        cles.put("portee", l.centrale() ? "centrale" : "regionale");
        return new ModelePvWord.Contexte(v, base.listes(), cles, base.saisis());
    }

    /** Le PV du dernier examen du dossier, ou {@code null}. */
    private PvExamen dernierPv(Integer idDossier) {
        for (Examen e : examens.findParDossierRecents(idDossier)) {
            PvExamen pv = pvs.findFirstByIdExamenOrderByIdPvDesc(e.getIdExamen()).orElse(null);
            if (pv != null) {
                return pv;
            }
        }
        return null;
    }

    /** Qui vise : le Président s'il a visé, à défaut le Chef de commission ; « par intérim » sur un PV régional visé par intérim. */
    private void viseur(Map<String, ModelePvWord.Valeur> v, PvExamen pv, boolean centrale) {
        boolean president = pv.getImCtrlPresident() != null && !pv.getImCtrlPresident().isBlank();
        String im = president ? pv.getImCtrlPresident() : pv.getImCtrlCc();
        String nom = im == null ? null : controleurs.findById(im)
                .map(c -> ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " " + (c.getNomCont() == null ? "" : c.getNomCont())).trim())
                .orElse(im);
        texte(v, "PV.VISEUR", nom);
        texte(v, "PV.VISEUR_ROLE", nom == null ? null : president ? "Président de la Commission Nationale des Marchés" : "Chef de la Commission");
        texte(v, "PV.INTERIM", Boolean.TRUE.equals(pv.getViseParInterim()) && !centrale ? "par intérim" : null);
    }

    /** Les variables de procédure et de marché, lues sur la fiche de présentation ; la liste des titulaires sur la proposition. */
    private void procedure(Dossier d, Map<String, ModelePvWord.Valeur> v, Map<String, List<Map<String, ModelePvWord.Valeur>>> listes,
            Map<String, String> cles) {
        Attribution a = "DDM".equals(d.getIdTypeDossier()) ? attributions.findFirstByIdDossier(d.getIdDossier()).orElse(null) : null;
        Long idDmc = a != null ? a.getIdDmc() : d.getIdDmc();
        List<Map<String, ModelePvWord.Valeur>> titulaires = new ArrayList<>();
        listes.put("TITULAIRES", titulaires);
        cles.put("titulaires", "0");
        if (idDmc == null) {
            return;
        }
        FicheMarcheService.EtatVersion ev = fiches.etatValide(idDmc).orElse(null);
        if (ev != null) {
            cles.put("forme", ev.type());
            texte(v, "PROCEDURE.NUMERO", DaoCompletService.numero(ev.etat()));
        }
        FichePresentationDto fiche;
        try {
            fiche = fichePresentation.getObject().contenu(d.getIdDossier());
        } catch (BusinessRuleException | ResourceNotFoundException horsFiche) {
            fiche = null;
        }
        if (fiche != null) {
            Map<String, String> l = new LinkedHashMap<>();
            for (FichePresentationDto.Section s : fiche.sections()) {
                for (FichePresentationDto.Ligne li : s.lignes()) {
                    l.putIfAbsent(s.code() + "." + li.code(), li.valeur());
                }
            }
            texte(v, "PROCEDURE.OBJET", l.get("MARCHE.OBJET"));
            texte(v, "PROCEDURE.MODE", l.get("MARCHE.MODE"));
            texte(v, "PROCEDURE.FORME", l.get("MARCHE.FORME"));
            texte(v, "PROCEDURE.LOT", l.get("ATTRIBUTION.LOT"));
            String mec = l.get("MISE_EN_CONCURRENCE.DOSSIER_MISE_EN_CONCURRENCE");
            String avisMec = l.get("MISE_EN_CONCURRENCE.AVIS_COMMISSION");
            texte(v, "MARCHE.DOSSIER_MEC", mec == null ? null : avisMec == null ? mec : mec + ", " + avisMec);
            texte(v, "MARCHE.ESTIMATION", l.get("MARCHE.MONTANT_ESTIMATIF"));
            texte(v, "MARCHE.ECART", l.get("ATTRIBUTION.ECART_ESTIMATION"));
        }
        if (a == null) {
            return;
        }
        EvaluationDto evd = evaluation.getObject().vueSansGarde(idDmc).orElse(null);
        EvaluationDto.Proposition p = evd == null ? null : evd.lots().stream().filter(x -> a.getLot().equals(x.lot()))
                .map(EvaluationDto.Lot::proposition).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (p == null || p.infructueux()) {
            return;
        }
        if (p.titulaires() == null || p.titulaires().isEmpty()) {
            titulaires.add(titulaire(1, p.candidat(), p.montant(), p.montantTtc(), p.delai()));
        } else {
            for (EvaluationDto.Titulaire t : p.titulaires()) {
                titulaires.add(titulaire(t.rang(), t.candidat(), t.montant(), t.montantTtc(), t.delai()));
            }
        }
        cles.put("titulaires", String.valueOf(titulaires.size()));
    }

    private static Map<String, ModelePvWord.Valeur> titulaire(Integer rang, String candidat, java.math.BigDecimal ht,
            java.math.BigDecimal ttc, String delai) {
        Map<String, ModelePvWord.Valeur> t = new LinkedHashMap<>();
        t.put("TIT.RANG", ModelePvWord.Valeur.de(rang == null ? null : String.valueOf(rang)));
        t.put("TIT.CANDIDAT", ModelePvWord.Valeur.de(candidat));
        t.put("TIT.MONTANT_HT", ModelePvWord.Valeur.de(ht == null ? null : ValeursPpmService.montant(ht)));
        t.put("TIT.MONTANT_TTC", ModelePvWord.Valeur.de(ttc == null ? null : ValeursPpmService.montant(ttc)));
        t.put("TIT.DELAI", ModelePvWord.Valeur.de(delai));
        return t;
    }

    private static void texte(Map<String, ModelePvWord.Valeur> v, String code, String valeur) {
        if (valeur != null) {
            v.put(code, ModelePvWord.Valeur.de(valeur));
        }
    }
}
