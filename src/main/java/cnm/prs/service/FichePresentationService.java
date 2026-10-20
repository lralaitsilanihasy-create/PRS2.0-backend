package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.FichePresentationDto;
import cnm.prs.dto.FichePresentationDto.Ligne;
import cnm.prs.dto.FichePresentationDto.Section;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;

/**
 * ⚠️ 2026-10-17 (demande front « pieces-dmc-par-les-donnees », §B2 ; demande du pilote) — la <strong>fiche de présentation</strong>
 * d'un dossier né d'une fiche, produite de ses données comme celle du dossier de planification : elle remplace la pièce
 * « Fiche de présentation datée et signée » ({@link PiecesFournies}). Q1 : la validation nominative de la fiche par la PRMP, datée et
 * journalisée, vaut signature (comme R2, V102) ; confirmé par le pilote le 17/10.
 * <p>
 * Contenu (Q2) : celui que le front propose, ajusté aux points que le manuel de contrôle fait vérifier sur la fiche de présentation
 * d'un DAO (objet, mode de passation, allotissement, retard de disponibilité du DAO sur l'AGPM, garantie de soumission, validité des
 * offres, heure et lieu de remise). Le manuel n'annexe de modèle que pour le plan. Les valeurs de la fiche se lisent par champ ;
 * une ligne prend le premier champ qui vaut pour la catégorie et la forme de la fiche (les codes diffèrent d'une catégorie à
 * l'autre), et n'est pas servie sans valeur.
 */
@Service
@Transactional(readOnly = true)
public class FichePresentationService {

    public static final String CODE_INDISPONIBLE = "FICHE_PRESENTATION_INDISPONIBLE";
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final DossierRepository dossiers;
    private final DossierMecRepository dmcs;
    private final FicheMarcheService fiches;
    private final ValeursPpmService valeursPpm;
    private final ActeurDirectory acteurs;
    private final GenerateurDocumentsFiche generateur;
    private final ObjectProvider<DossierService> dossierService;
    /** ⚠️ 2026-10-19 (« dossier-marche-contrat-cadre », §B4) — le dossier de marché né d'une attribution. */
    private final AttributionRepository attributions;
    private final ObjectProvider<EvaluationService> evaluation;
    /** ⚠️ 2026-10-20 (« fiche-presentation-marche », §B1) — la séance d'ouverture, l'avis de la Commission et la publication. */
    private final ObjectProvider<SeanceService> seance;
    private final ReferencesCommission references;

    public FichePresentationService(DossierRepository dossiers, DossierMecRepository dmcs, FicheMarcheService fiches,
            ValeursPpmService valeursPpm, ActeurDirectory acteurs, GenerateurDocumentsFiche generateur,
            ObjectProvider<DossierService> dossierService, AttributionRepository attributions,
            ObjectProvider<EvaluationService> evaluation, ObjectProvider<SeanceService> seance, ReferencesCommission references) {
        this.seance = seance;
        this.references = references;
        this.attributions = attributions;
        this.evaluation = evaluation;
        this.dossiers = dossiers;
        this.dmcs = dmcs;
        this.fiches = fiches;
        this.valeursPpm = valeursPpm;
        this.acteurs = acteurs;
        this.generateur = generateur;
        this.dossierService = dossierService;
    }

    /** La fiche, sous la garde de lecture du dossier ; 409 {@code FICHE_PRESENTATION_INDISPONIBLE} sans fiche validée. */
    public FichePresentationDto lire(Integer idDossier) {
        dossierService.getObject().controlerVisibilite(idDossier);
        return contenu(idDossier);
    }

    /** Le PDF, sous la même garde. */
    public byte[] pdf(Integer idDossier) {
        dossierService.getObject().controlerVisibilite(idDossier);
        FichePresentationDto f = contenu(idDossier);
        return generateur.generer(document(f, LocalDateTime.now())).stream().filter(x -> "pdf".equals(x.extension())).findFirst()
                .orElseThrow(() -> new IllegalStateException("La fiche de présentation n'a pas été rendue en pdf.")).contenu();
    }

    /** {@code FICHE_PRESENTATION_<référence ou id du dossier>.pdf}. */
    public String nomPdf(Integer idDossier) {
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        String ref = d.getRefeDossier() == null || d.getRefeDossier().isBlank() ? String.valueOf(idDossier) : d.getRefeDossier();
        return "FICHE_PRESENTATION_" + ref.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    FichePresentationDto contenu(Integer idDossier) {
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        // ⚠️ 2026-10-19 (« dossier-marche-contrat-cadre », §B4) — un dossier de marché né d'une attribution se présente par la fiche
        // de sa procédure, avec la section « Attribution ».
        Attribution attribution = d.getIdDmc() != null || !"DDM".equals(d.getIdTypeDossier()) ? null
                : attributions.findFirstByIdDossier(idDossier).orElse(null);
        Long idDmc = attribution != null ? attribution.getIdDmc() : d.getIdDmc();
        FicheMarcheService.EtatVersion ev = idDmc == null ? null : fiches.etatValide(idDmc).orElse(null);
        if (ev == null) {
            throw new BusinessRuleException("Ce dossier n'est pas né d'une fiche validée : sa fiche de présentation n'est pas produite par "
                    + "l'application.", CODE_INDISPONIBLE, idDossier);
        }
        Integer idDetail = dmcs.findById(idDmc).map(DossierMec::getIdDetail).orElse(null);
        ValeursPpmService.ValeursPpm ppm = idDetail == null ? new ValeursPpmService.ValeursPpm(null, 0, Map.of(), Map.of())
                : valeursPpm.lire(idDetail);
        Map<String, String> p = ppm.valeurs();

        List<Section> sections = new ArrayList<>();
        sections.add(section("IDENTIFICATION", "Identification", lignes(
                ligne("AUTORITE", "Autorité contractante", p.get("ENTITE")),
                ligne("MINISTERE", "Ministère de tutelle", p.get("MINISTERE")),
                ligne("PRMP", "Personne responsable des marchés publics", p.get("PRMP")),
                ligne("PLAN", "Plan de passation", plan(p)),
                ligne("LIGNE", "Ligne du plan", ppm.ligne() == null ? null : "n° " + ppm.ligne().getIdDetail()),
                ligne("DOSSIER", "Dossier", d.getRefeDossier()))));
        sections.add(section("MARCHE", "Marché", lignes(
                ligne("OBJET", "Objet", p.get("OBJET")),
                ligne("CATEGORIE", "Catégorie", categorie(ev.categorie())),
                ligne("NATURE", "Nature des prestations", p.get("NATURE")),
                ligne("MODE", "Mode de passation", p.get("MODE")),
                ligne("FORME", "Forme du marché", forme(ev.type(), p.get("FORME_MARCHE"))),
                ligne("NB_LOTS", "Nombre de lots", ev.etat().getNbLots() == null ? p.get("NB_LOTS_PPM") : String.valueOf(ev.etat().getNbLots())),
                ligne("LOTS", "Désignation des lots", p.get("LOTS_DESIGNATION")),
                ligne("MONTANT_ESTIMATIF", "Montant estimatif du plan", p.get("MONTANT_ESTIMATIF")),
                ligne("FINANCEMENT", "Source de financement", p.get("FINANCEMENT")),
                // ⚠️ Recette du 17/10 (remarque 1) — l'imputation est B01-AC-16 (les lignes d'imputation du plan, sans leurs
                // montants, comme T1 sur la garde), le compte B01-AC-17 a sa propre ligne.
                ligne("IMPUTATION", "Imputation budgétaire", imputation(p.get("BENEFICIAIRES"))),
                ligne("COMPTE", "Compte budgétaire", p.get("COMPTES")))));
        List<Ligne> procedure = new ArrayList<>(lignes(
                ligne("NUMERO_DAO", "Numéro du dossier d'appel d'offres", premier(ev, "B02-OB-03")),
                ligne("MODE_REMISE", "Mode de remise des offres", premier(ev, "B04-SE-01")),
                ligne("PLATEFORME", "Plateforme de dépôt", premier(ev, "B04-SE-02")),
                ligne("DATE_LIMITE", "Date et heure limites de remise",
                        avecHeure(premier(ev, "B04-LR-03", "B04-OV-02", "B04-LH-02", "B04-CP-02"), premier(ev, "B04-LR-04"))),
                ligne("LIEU_REMISE", "Lieu de remise des offres", premier(ev, "B04-LR-02", "B04-LH-01", "B04-RQ-03")),
                ligne("OUVERTURE", "Ouverture des plis", avecHeure(premier(ev, "B04-OP-02"), premier(ev, "B04-OP-03"))),
                ligne("LIEU_OUVERTURE", "Lieu de l'ouverture des plis", premier(ev, "B04-OP-01", "B04-OV-01")),
                ligne("VALIDITE_OFFRES", "Délai de validité des offres (jours)", premier(ev, "B04-VO-01", "B04-VT-01", "B04-DV-01", "B04-DP-01")),
                ligne("GARANTIE", "Garantie de soumission exigée", premier(ev, "B05-GS-01", "B05-GQ-01")),
                ligne("GARANTIE_FORME", "Forme de la garantie de soumission", premier(ev, "B05-GS-02", "B05-GQ-02")),
                ligne("GARANTIE_MONTANT", "Montant de la garantie de soumission (Ariary)", premier(ev, "B05-GS-03", "B05-GQ-03")),
                ligne("GARANTIE_VALIDITE", "Validité de la garantie de soumission (jours)", premier(ev, "B05-GS-04"))));
        if (attribution != null) {
            // ⚠️ 2026-10-20 (« fiche-presentation-marche », §B1) — la fiche propre au dossier de marché : ce qui s'est passé (attribution,
            // mise en concurrence, séance, évaluation), plus le calendrier prévu du DAO ni la cohérence avec l'AGPM (contrôle du DAO).
            sections.addAll(duMarche(d, attribution, ev, ppm, procedure));
            return new FichePresentationDto(idDossier, idDmc, ev.version(), sections.stream().filter(s -> !s.lignes().isEmpty()).toList());
        }
        // Le calendrier de la procédure du contrat-cadre (B04-CP, huit étapes), sous les libellés du référentiel ; ⚠️ recette du 17/10
        // (remarque 2) : sans B04-CP-02, déjà servie comme date limite de remise.
        for (int i = 1; i <= 8; i++) {
            String code = "B04-CP-0" + i;
            if (i != 2) {
                procedure.add(ligne(code, ev.libelle(code), ev.valeur(code)));
            }
        }
        sections.add(section("PROCEDURE", "Procédure", procedure));
        // ⚠️ Recette du 17/10 (remarque 3) — du calendrier du plan (B01-AC-18, une trentaine d'étapes), les seules étapes du DAO, une
        // ligne chacune.
        sections.add(section("CALENDRIER_PLAN", "Calendrier prévisionnel du plan (étapes du DAO)",
                etapesDuDao(p.get("DATES_PREVISIONNELLES"))));

        // Le manuel : « relever l'éventuel retard de la date de disponibilité du DAO » au regard de l'AGPM. Le DAO est disponible à la
        // date de lancement de la fiche (contrat-cadre) ou, à défaut, à la validation de la fiche.
        LocalDate prevu = ppm.dates().get("LANCEMENT");
        LocalDate disponible = date(ev.valeur("B04-CP-01"));
        if (disponible == null && ev.etat().getDateValidation() != null) {
            disponible = ev.etat().getDateValidation().toLocalDate();
        }
        Long retard = prevu == null || disponible == null ? null : ChronoUnit.DAYS.between(prevu, disponible);
        sections.add(section("COHERENCE", "Cohérence avec l'AGPM", lignes(
                ligne("LANCEMENT_PREVU", "Lancement prévu au plan", prevu == null ? null : prevu.format(JOUR)),
                ligne("DAO_DISPONIBLE", "Disponibilité du dossier d'appel d'offres", disponible == null ? null : disponible.format(JOUR)),
                ligne("RETARD", "Retard sur le plan", retard == null ? null : retard > 0 ? retard + " jour(s)" : "aucun"))));

        String validePar = ev.etat().getValidePar();
        sections.add(section("VALIDATION", "Validation", lignes(
                ligne("VERSION", "Version de la fiche", String.valueOf(ev.version())),
                ligne("DATE_VALIDATION", "Validée le", ev.etat().getDateValidation() == null ? null
                        : ev.etat().getDateValidation().format(JOUR_HEURE)),
                ligne("VALIDEE_PAR", "Validée par", validePar == null ? null : acteurs.nom(validePar)))));
        return new FichePresentationDto(idDossier, idDmc, ev.version(), sections.stream().filter(s -> !s.lignes().isEmpty()).toList());
    }

    /**
     * ⚠️ 2026-10-20 (demande front « fiche-presentation-marche », §B1 ; manuel de contrôle, p. 50-52) — les sections d'un dossier de
     * marché né d'une attribution, après l'identification et le marché : {@code ATTRIBUTION}, {@code MISE_EN_CONCURRENCE},
     * {@code PROCEDURE} (conditions de la mise en concurrence, sans les dates prévues), {@code SEANCE}, {@code EVALUATION},
     * {@code PREVU_REALISE} (Q1 : le calendrier prévu du contrat-cadre face aux faits), {@code VALIDATION} (la création nominative).
     */
    private List<Section> duMarche(Dossier d, Attribution a, FicheMarcheService.EtatVersion ev, ValeursPpmService.ValeursPpm ppm,
            List<Ligne> procedure) {
        EvaluationDto evd = evaluation.getObject().vueSansGarde(a.getIdDmc()).orElse(null);
        EvaluationDto.Lot lot = evd == null ? null : evd.lots().stream().filter(l -> a.getLot().equals(l.lot())).findFirst().orElse(null);
        SeanceDto s = seance.getObject().dto(a.getIdDmc());
        LocalDate publication = references.publication(a.getIdDmc()).map(p -> date(p.datePublication())).orElse(null);
        String limite = premier(ev, "B04-LR-03", "B04-OV-02", "B04-LH-02", "B04-CP-02");
        LocalDate dateLimite = date(limite);
        Dossier mec = dossiers.findIdDossierByIdDmc(a.getIdDmc()).flatMap(dossiers::findById).orElse(null);

        List<Section> out = new ArrayList<>();
        out.add(section("ATTRIBUTION", "Attribution", attribution(a, lot, ppm.ligne() == null ? null : ppm.ligne().getMontEstim())));
        out.add(section("MISE_EN_CONCURRENCE", "Mise en concurrence", lignes(
                ligne("DOSSIER_MISE_EN_CONCURRENCE", "Dossier de mise en concurrence", mec == null ? null : mec.getRefeDossier()),
                ligne("AVIS_COMMISSION", "Avis de la Commission sur le dossier", references.pvCommission(a.getIdDmc())
                        .map(ReferencesCommission::lignePv).orElse(null)),
                ligne("PREMIERE_PUBLICATION", "Première publication de l'avis", publication == null ? null : publication.format(JOUR)),
                ligne("DATE_LIMITE", "Date et heure limites de remise", avecHeure(limite, premier(ev, "B04-LR-04"))),
                ligne("DELAI_REMISE", "Délai de remise effectif", publication == null || dateLimite == null ? null
                        : ChronoUnit.DAYS.between(publication, dateLimite) + " jour(s), de la première publication à la date limite"))));
        out.add(section("PROCEDURE", "Procédure", procedure.stream().filter(l -> l == null || !"DATE_LIMITE".equals(l.code())).toList()));
        out.add(section("SEANCE", "Séance d'ouverture des offres", seance(s, a.getLot())));
        out.add(section("EVALUATION", "Évaluation des offres", evaluation(evd, lot)));
        out.add(section("PREVU_REALISE", "Calendrier : prévu et réalisé", prevuRealise(ev, a, evd, publication, dateLimite)));
        out.add(section("VALIDATION", "Validation", lignes(
                // ⚠️ 2026-10-19 (§B4) — la création nominative du dossier de marché par la PRMP (ou son UGPM), datée et journalisée (Q2 :
                // vaut signature, hypothèse à confirmer par le pilote).
                a.getDossierCreeLe() == null ? null : ligne("DOSSIER_CREE_LE", "Dossier de marché créé le", a.getDossierCreeLe().format(JOUR_HEURE)),
                a.getDossierCreePar() == null ? null : ligne("DOSSIER_CREE_PAR", "Dossier de marché créé par", acteurs.nom(a.getDossierCreePar())))));
        return out;
    }

    /**
     * ⚠️ 2026-10-19 (« dossier-marche-contrat-cadre », §B4) — la section « Attribution » : le lot, la proposition de la commission au
     * rapport signé ; ⚠️ 2026-10-20 (§B1) une ligne par titulaire d'un contrat-cadre (rang, candidat, montants, délai) et l'écart à
     * l'estimation du plan (Q3 : sur l'offre classée 1re quand il y a plusieurs titulaires).
     */
    private List<Ligne> attribution(Attribution a, EvaluationDto.Lot lot, BigDecimal estimation) {
        EvaluationDto.Proposition p = lot == null ? null : lot.proposition();
        List<EvaluationDto.Titulaire> titulaires = p == null || p.titulaires() == null ? List.of() : p.titulaires();
        List<Ligne> out = new ArrayList<>();
        out.add(ligne("LOT", "Lot", String.valueOf(a.getLot())));
        BigDecimal retenu;
        if (titulaires.isEmpty()) {
            out.add(ligne("ATTRIBUTAIRE", "Attributaire proposé par la commission",
                    p == null ? null : p.candidat() + (p.numero() == null ? "" : " (offre n° " + p.numero() + ")")));
            out.add(ligne("MONTANT_HT", "Montant proposé hors taxes (Ariary)", p == null ? null : ValeursPpmService.montant(p.montant())));
            out.add(ligne("MONTANT_TTC", "Montant proposé toutes taxes (Ariary)", p == null ? null : ValeursPpmService.montant(p.montantTtc())));
            out.add(ligne("DELAI", "Délai d'exécution proposé", p == null ? null : p.delai()));
            retenu = p == null ? null : p.montant();
        } else {
            for (EvaluationDto.Titulaire t : titulaires) {
                out.add(ligne("TITULAIRE_" + t.rang(), "Titulaire proposé, rang " + t.rang(), t.candidat() + " (offre n° " + t.numero() + ")"
                        + (t.montant() == null ? "" : "\nHors taxes : " + ValeursPpmService.montant(t.montant()) + " Ar")
                        + (t.montantTtc() == null ? "" : "\nToutes taxes : " + ValeursPpmService.montant(t.montantTtc()) + " Ar")
                        + (t.delai() == null || t.delai().isBlank() ? "" : "\nDélai : " + t.delai())));
            }
            retenu = titulaires.get(0).montant();
        }
        out.add(ligne("ECART_ESTIMATION", titulaires.isEmpty() ? "Écart à l'estimation du plan" : "Écart à l'estimation du plan (offre classée 1re)",
                ecart(retenu, estimation)));
        return out;
    }

    /** « −22,5 % (179 570 562 Ar HT contre 231 563 789 Ar estimés) » ; nul sans l'un des deux montants. */
    static String ecart(BigDecimal montant, BigDecimal estimation) {
        if (montant == null || estimation == null || estimation.signum() == 0) {
            return null;
        }
        double pct = montant.subtract(estimation).doubleValue() * 100 / estimation.doubleValue();
        return String.format(java.util.Locale.FRANCE, "%+.1f %%", pct).replace('-', '−') + " (" + ValeursPpmService.montant(montant)
                + " Ar HT contre " + ValeursPpmService.montant(estimation) + " Ar estimés)";
    }

    /** La séance du lot : ouverture, offres reçues, ouvertes et non ouvertes (par état), PV d'ouverture signé, quorum. */
    private static List<Ligne> seance(SeanceDto s, Integer lot) {
        List<SeanceDto.OffreSeance> offres = s.offres().stream().filter(o -> o.lot() == null || o.lot().equals(lot)).toList();
        long ouvertes = offres.stream().filter(o -> SeanceService.INTACTE.equals(o.etat())).count();
        Map<String, Long> autres = new java.util.TreeMap<>();
        offres.stream().filter(o -> !SeanceService.INTACTE.equals(o.etat())).forEach(o -> autres.merge(etatOffre(o.etat()), 1L, Long::sum));
        LocalDateTime pvSigne = s.pv() == null || !s.pv().signe() ? null : s.pv().signatures().stream().map(SeanceDto.Signature::date)
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        return lignes(
                ligne("OUVERTE_LE", "Séance ouverte le", s.ouverteLe() == null ? null : s.ouverteLe().format(JOUR_HEURE)),
                s.ouverteLe() == null ? null : ligne("OFFRES_RECUES", "Offres reçues", String.valueOf(offres.size())),
                s.ouverteLe() == null ? null : ligne("OFFRES_OUVERTES", "Offres ouvertes", String.valueOf(ouvertes)),
                s.ouverteLe() == null ? null : ligne("OFFRES_NON_OUVERTES", "Offres non ouvertes", autres.isEmpty() ? "0"
                        : (offres.size() - ouvertes) + " (" + String.join(", ", autres.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).toList()) + ")"),
                ligne("PV_SIGNE", "Procès-verbal d'ouverture signé le", pvSigne == null ? null : pvSigne.format(JOUR_HEURE)),
                ligne("QUORUM", "Quorum au procès-verbal", s.pv() == null ? null : quorum(s.pv().quorum())));
    }

    private static String etatOffre(String etat) {
        return switch (etat == null ? "" : etat) {
            case "ALTEREE" -> "altérée";
            case "LECTURE_IMPOSSIBLE" -> "illisible";
            case "RETIREE" -> "retirée";
            case "REMPLACEE" -> "remplacée";
            case "ECARTEE" -> "écartée";
            default -> etat == null ? "inconnue" : etat.toLowerCase(java.util.Locale.FRENCH).replace('_', ' ');
        };
    }

    /** L'évaluation du lot : offres évaluées, conformes, écartées, corrigées, anormales, qualifiées ; le rapport signé et son quorum. */
    private static List<Ligne> evaluation(EvaluationDto evd, EvaluationDto.Lot lot) {
        if (evd == null || lot == null) {
            return List.of();
        }
        List<EvaluationDto.OffreEvaluee> o = lot.offres() == null ? List.of() : lot.offres();
        long conformes = o.stream().filter(x -> x.conformite() != null && cnm.prs.entity.EvaluationDecision.CONFORME.equals(x.conformite().decision())).count();
        long ecartees = o.stream().filter(x -> x.ecartee() != null).count();
        long corrigees = o.stream().filter(x -> x.evaluation() != null && x.evaluation().corrections() != null && !x.evaluation().corrections().isEmpty()).count();
        long suspectees = o.stream().filter(x -> x.anormale() != null && Boolean.TRUE.equals(x.anormale().suspectee())).count();
        long rejetees = o.stream().filter(x -> x.anormale() != null && EvaluationService.REJETEE.equals(x.anormale().decision())).count();
        long qualifiees = o.stream().filter(x -> x.qualification() != null && EvaluationService.QUALIFIE.equals(x.qualification().decision())).count();
        EvaluationDto.Rapport r = evd.rapport();
        return lignes(
                ligne("OUVERTE_LE", "Évaluation ouverte le", evd.ouverteLe() == null ? null : evd.ouverteLe().format(JOUR_HEURE)),
                ligne("OFFRES_EVALUEES", "Offres évaluées", String.valueOf(o.size())),
                ligne("OFFRES_CONFORMES", "Offres conformes", String.valueOf(conformes)),
                ligne("OFFRES_ECARTEES", "Offres écartées", String.valueOf(ecartees)),
                ligne("CORRECTIONS", "Offres aux prix corrigés", String.valueOf(corrigees)),
                ligne("OFFRES_ANORMALES", "Offres anormalement basses", suspectees == 0 ? "aucune" : suspectees + " suspectée(s), " + rejetees + " rejetée(s)"),
                ligne("OFFRES_QUALIFIEES", "Offres qualifiées", String.valueOf(qualifiees)),
                ligne("RAPPORT_SIGNE", "Rapport d'évaluation signé le (vaut PV de validation)", r == null || r.signeLe() == null ? null
                        : r.signeLe().format(JOUR_HEURE)),
                ligne("QUORUM", "Quorum au rapport", r == null ? null : quorum(r.quorum())));
    }

    private static String quorum(cnm.prs.dto.QuorumDto q) {
        return q == null ? null : q.obtenus() + " signataire(s) sur " + q.requis() + " requis — " + (q.atteint() ? "atteint" : "non atteint");
    }

    /**
     * Q1 — le calendrier prévu de la procédure du contrat-cadre ({@code B04-CP-01} à {@code 08}) face aux faits connus de l'application :
     * lancement / première publication, remise / date limite échue, évaluation / ouverture, attribution, notification, rejets /
     * information des candidats ; les offres optimisées (marchés subséquents) restent « prévu ». Vide hors contrat-cadre.
     */
    static List<Ligne> prevuRealise(FicheMarcheService.EtatVersion ev, Attribution a, EvaluationDto evd, LocalDate publication,
            LocalDate dateLimite) {
        Map<Integer, LocalDate> realise = new java.util.HashMap<>();
        if (publication != null) {
            realise.put(1, publication);
        }
        if (dateLimite != null && !dateLimite.isAfter(LocalDate.now())) {
            realise.put(2, dateLimite);
        }
        if (evd != null && evd.ouverteLe() != null) {
            realise.put(3, evd.ouverteLe().toLocalDate());
        }
        if (a.getAttribueLe() != null) {
            realise.put(4, a.getAttribueLe().toLocalDate());
        }
        if (a.getNotifieLe() != null) {
            realise.put(5, a.getNotifieLe().toLocalDate());
        }
        if (a.getInformeLe() != null) {
            realise.put(8, a.getInformeLe().toLocalDate());
        }
        List<Ligne> out = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String code = "B04-CP-0" + i;
            LocalDate prevu = date(ev.valeur(code));
            if (prevu == null) {
                continue;
            }
            LocalDate fait = realise.get(i);
            out.add(ligne(code, ev.libelle(code), "prévu le " + prevu.format(JOUR) + " ; " + (fait == null ? "non réalisé" : "réalisé le " + fait.format(JOUR))));
        }
        return out;
    }

    DocumentLibre document(FichePresentationDto f, LocalDateTime le) {
        List<DocumentLibre.Element> els = new ArrayList<>();
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "FICHE DE PRÉSENTATION"));
        boolean marche = f.sections().stream().anyMatch(s -> "ATTRIBUTION".equals(s.code()));   // ⚠️ 2026-10-19 (§B4)
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, marche ? "Dossier de marché" : "Dossier d'appel à la concurrence"));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        for (Section s : f.sections()) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, s.titre(), true));
            List<List<List<String>>> lignes = new ArrayList<>();
            s.lignes().forEach(l -> lignes.add(List.of(List.of(l.libelle()), List.of(l.valeur().split("\n", -1)))));
            els.add(new DocumentLibre.Tableau(2, lignes));
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        }
        String pied = "Fiche produite par l'application le " + le.format(JOUR_HEURE) + " à partir de la version " + f.versionFiche()
                + " de la fiche ; la validation tracée de la fiche par la PRMP vaut signature"
                + (marche ? ", comme la création nominative du dossier de marché." : ".");
        return new DocumentLibre("FICHE_PRESENTATION", null, els, pied);
    }

    // ------------------------------------------------------------------ outils

    private static Section section(String code, String titre, List<Ligne> lignes) {
        return new Section(code, titre, lignes.stream().filter(java.util.Objects::nonNull).toList());
    }

    /** Les lignes qui ont une valeur. */
    static List<Ligne> lignes(Ligne... ls) {
        return java.util.Arrays.stream(ls).filter(java.util.Objects::nonNull).toList();
    }

    /** Une ligne, ou nul sans valeur. */
    static Ligne ligne(String code, String libelle, String valeur) {
        return valeur == null || valeur.isBlank() ? null : new Ligne(code, libelle, valeur);
    }

    /** La valeur du premier champ qui vaut pour la fiche et porte une valeur. */
    static String premier(FicheMarcheService.EtatVersion ev, String... codes) {
        for (String c : codes) {
            String v = ev.valeur(c);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** Les étapes du DAO, par début de libellé (accents et casse neutralisés) : code de ligne, préfixe. */
    private static final List<String[]> ETAPES_DAO = List.of(
            new String[] {"ETABLISSEMENT_DAO", "ETABLISSEMENT DU DAO"},
            new String[] {"TRANSMISSION_DAO", "TRANSMISSION DU DAO"},
            new String[] {"LANCEMENT", "LANCEMENT"},
            new String[] {"REMISE_OFFRES", "REMISE DES OFFRES"},
            new String[] {"OUVERTURE_PLIS", "OUVERTURE"});

    /**
     * Les étapes du DAO du calendrier du plan (« libellé : période ; … », {@link ValeursPpmService}) : une ligne par étape, la première
     * qui commence par son préfixe ; libellé coupé à son premier « : », dates au format jj/mm/aaaa.
     */
    static List<Ligne> etapesDuDao(String dates) {
        if (dates == null) {
            return List.of();
        }
        List<Ligne> out = new ArrayList<>();
        for (String[] e : ETAPES_DAO) {
            for (String part : dates.split(" ; ")) {
                int sep = part.lastIndexOf(" : ");
                if (sep < 0) {
                    continue;
                }
                String libelle = part.substring(0, sep).trim();
                String cle = java.text.Normalizer.normalize(libelle, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                        .toUpperCase(java.util.Locale.FRENCH);
                if (cle.startsWith(e[1])) {
                    int deuxPoints = libelle.indexOf(" :");
                    String court = deuxPoints < 0 ? libelle : libelle.substring(0, deuxPoints).trim();
                    court = court.equals(court.toUpperCase(java.util.Locale.FRENCH))
                            ? court.charAt(0) + court.substring(1).toLowerCase(java.util.Locale.FRENCH) : court;
                    out.add(new Ligne(e[0], court, periode(part.substring(sep + 3).trim())));
                    break;
                }
            }
        }
        return out;
    }

    /** « 2026-03-02 → 2026-03-20 » en « 02/03/2026 → 20/03/2026 » ; « sans objet » et « — » tels quels. */
    private static String periode(String p) {
        StringBuilder sb = new StringBuilder();
        for (String morceau : p.split(" → ")) {
            LocalDate d = date(morceau);
            sb.append(sb.length() == 0 ? "" : " → ").append(d == null ? morceau : d.format(JOUR));
        }
        return sb.toString();
    }

    /** Les lignes d'imputation du plan, sans leurs montants entre parenthèses (comme la garde du DAO complet, T1). */
    static String imputation(String beneficiaires) {
        if (beneficiaires == null) {
            return null;
        }
        String s = beneficiaires.replaceAll("\\s*\\([^)]*\\)", "").trim();
        return s.isEmpty() ? null : s;
    }

    private static String avecHeure(String date, String heure) {
        return date == null ? null : heure == null || heure.isBlank() ? date : date + " à " + heure;
    }

    private static String plan(Map<String, String> p) {
        if (p.get("PPM_REFERENCE") == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(p.get("PPM_REFERENCE"));
        if (p.get("PPM_EXERCICE") != null) {
            sb.append(", exercice ").append(p.get("PPM_EXERCICE"));
        }
        if (p.get("PPM_VERSION") != null && !"0".equals(p.get("PPM_VERSION"))) {
            sb.append(", mise à jour n° ").append(p.get("PPM_VERSION"));
        }
        return sb.toString();
    }

    static String categorie(String code) {
        return code == null ? null : switch (code) {
            case "FOURNITURES_SERVICES" -> "Fournitures et services";
            case "TRAVAUX" -> "Travaux";
            case "PRESTATIONS_INTELLECTUELLES" -> "Prestations intellectuelles";
            default -> code;
        };
    }

    private static String forme(String type, String formePlan) {
        String t = type == null ? null : switch (type) {
            case "QUANTITE_FIXE" -> "Marché à quantités fixes";
            case "A_COMMANDE" -> "Marché à commandes";
            case "CONTRAT_CADRE" -> "Contrat-cadre";
            default -> type;
        };
        return t == null ? formePlan : formePlan == null || formePlan.equalsIgnoreCase(t) ? t : t + " (plan : " + formePlan + ")";
    }

    /** Une date de la fiche (« jj/mm/aaaa » ou ISO, éventuellement suivie d'une heure), ou nul. */
    static LocalDate date(String valeur) {
        if (valeur == null || valeur.length() < 10) {
            return null;
        }
        String s = valeur.substring(0, 10);
        try {
            return s.charAt(2) == '/' ? LocalDate.parse(s, JOUR) : LocalDate.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
