package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CapmDto;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Ppm;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.PpmRepository;

/**
 * ⚠️ 2026-10-14 (demande front « q-piece », §B2 ; arbitrage du pilote du 14/10 sur Q-pièce) — le <strong>calendrier annuel de
 * passation</strong> produit par l'application à partir du plan : toutes les lignes non retirées du plan, chacune avec les processus de
 * la grille CAPM de son mode (et ceux saisis hors grille), leurs dates, « sans objet » et son motif, ou « non daté » (H1 : un plan
 * soumis avant la règle du calendrier complet est rendu avec les dates qu'il a). Rendu par {@link DocumentLibre}, sans Word.
 * <p>
 * Lisible depuis le dossier de planification lui-même et depuis un dossier produit par une fiche marché ({@code ID_DMC}) : le plan
 * est alors celui de la ligne en vigueur de la fiche. Il remplace la pièce « Calendrier annuel de passation mis à jour et signé »
 * du dossier conduit dans l'application (V115, comme R2 : la validation tracée vaut signature).
 */
@Service
@Transactional(readOnly = true)
public class CalendrierAnnuelService {

    public static final String CODE_INDISPONIBLE = "CALENDRIER_INDISPONIBLE";
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");
    private static final String FAMILLE_DDP = "DDP";

    private final DossierRepository dossiers;
    private final DossierMecRepository dmcs;
    private final MarcheRepository marches;
    private final MarchePrevisionRepository previsions;
    private final PpmRepository ppms;
    private final CapmService capmService;
    private final CapmRepository capms;
    private final ModePassationRepository modes;
    private final EntiteContractRepository entites;
    private final ValeursPpmService valeursPpm;
    private final GenerateurDocumentsFiche generateur;
    private final ObjectProvider<DossierService> dossierService;

    public CalendrierAnnuelService(DossierRepository dossiers, DossierMecRepository dmcs, MarcheRepository marches,
            MarchePrevisionRepository previsions, PpmRepository ppms, CapmService capmService, CapmRepository capms,
            ModePassationRepository modes, EntiteContractRepository entites, ValeursPpmService valeursPpm,
            GenerateurDocumentsFiche generateur, ObjectProvider<DossierService> dossierService) {
        this.dossiers = dossiers;
        this.dmcs = dmcs;
        this.marches = marches;
        this.previsions = previsions;
        this.ppms = ppms;
        this.capmService = capmService;
        this.capms = capms;
        this.modes = modes;
        this.entites = entites;
        this.valeursPpm = valeursPpm;
        this.generateur = generateur;
        this.dossierService = dossierService;
    }

    /**
     * ⚠️ 2026-10-17 (« pieces-dmc-par-les-donnees ») — le dossier de planification qui porte la ligne en vigueur de la fiche de ce
     * dossier ; vide pour un dossier sans fiche ou dont la ligne n'est pas dans l'application.
     */
    public java.util.Optional<Integer> idPlanDeFiche(Long idDmc) {
        return java.util.Optional.ofNullable(idDmc).flatMap(dmcs::findById).map(DossierMec::getIdDetail).flatMap(marches::findById)
                .map(valeursPpm::ligneEnVigueur).map(Marche::getIdDossier);
    }

    /**
     * Le plan dont le calendrier se lit depuis ce dossier : lui-même (dossier de planification), ou celui de la ligne en vigueur
     * de sa fiche marché. 409 {@code CALENDRIER_INDISPONIBLE} sinon.
     */
    public Dossier plan(Integer idDossier) {
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        if (FAMILLE_DDP.equals(d.getIdTypeDossier())) {
            return d;
        }
        Integer idPlan = idPlanDeFiche(d.getIdDmc()).orElse(null);
        if (idPlan == null) {
            throw new BusinessRuleException("Ce dossier n'est pas rattaché à un plan de passation conduit dans l'application : son "
                    + "calendrier annuel n'est pas produit.", CODE_INDISPONIBLE, idDossier);
        }
        return dossiers.findById(idPlan).orElseThrow(() -> new ResourceNotFoundException("Plan introuvable : " + idPlan));
    }

    /** Le PDF, sous la garde de lecture du dossier demandé. */
    public byte[] pdf(Integer idDossier) {
        dossierService.getObject().controlerVisibilite(idDossier);
        return generateur.generer(document(plan(idDossier), LocalDateTime.now())).stream().filter(f -> "pdf".equals(f.extension()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Le calendrier n'a pas été rendu en pdf.")).contenu();
    }

    /** {@code CALENDRIER_ANNUEL_<référence ou id du plan>.pdf}. */
    public String nomPdf(Integer idDossier) {
        Dossier p = plan(idDossier);
        String ref = p.getRefeDossier() == null || p.getRefeDossier().isBlank() ? String.valueOf(p.getIdDossier()) : p.getRefeDossier();
        return "CALENDRIER_ANNUEL_" + ref.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    DocumentLibre document(Dossier plan, LocalDateTime le) {
        Ppm ppm = ppms.findByIdDossier(plan.getIdDossier()).stream().findFirst().orElse(null);
        String entite = plan.getIdEntiteContract() == null ? null
                : entites.findById(plan.getIdEntiteContract()).map(e -> e.getLibelleEntite()).orElse(null);
        List<DocumentLibre.Element> els = new ArrayList<>();
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "CALENDRIER ANNUEL DE PASSATION DES MARCHÉS"));
        if (entite != null) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, entite));
        }
        StringBuilder sous = new StringBuilder("Exercice ").append(ppm == null || ppm.getExercice() == null ? "—" : ppm.getExercice());
        if (ppm != null && ppm.getReference() != null) {
            sous.append(" — plan ").append(ppm.getReference());
        }
        if (ppm != null && ppm.getNumMaj() != null && ppm.getNumMaj() > 0) {
            sous.append(" — mise à jour n° ").append(ppm.getNumMaj());
        }
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, sous.toString()));
        if (plan.getRefeDossier() != null) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "Dossier " + plan.getRefeDossier() + " — " + plan.getStatut()));
        }
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));

        Map<Integer, List<MarchePrevision>> parLigne = previsions.findParDossiers(List.of(plan.getIdDossier())).stream()
                .collect(Collectors.groupingBy(MarchePrevision::getIdDetail));
        Map<Integer, List<CapmDto>> grilles = new HashMap<>();
        List<Marche> lignes = marches.findByIdDossier(plan.getIdDossier()).stream().filter(m -> !Boolean.TRUE.equals(m.getSupprimee()))
                .sorted(Comparator.comparing(Marche::getIdDetail)).toList();
        if (lignes.isEmpty()) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Le plan ne porte aucune ligne."));
        }
        int n = 1;
        int incompletes = 0;
        for (Marche m : lignes) {
            ModePassation mode = m.getIdMode() == null ? null : modes.findById(m.getIdMode()).orElse(null);
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, n++ + ". " + (m.getDesignationMarche() == null
                    ? "Ligne " + m.getIdDetail() : m.getDesignationMarche()), true));
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Mode : " + (mode == null ? "—" : mode.getLibelle())
                    + " — montant estimatif : " + (m.getMontEstim() == null ? "—" : ValeursPpmService.montant(m.getMontEstim()) + " Ar HT"),
                    true));
            List<List<List<String>>> rangees = new ArrayList<>();
            rangees.add(List.of(List.of("Processus"), List.of("Début"), List.of("Fin")));
            boolean incomplete = false;
            for (Rang r : rangs(m, parLigne.getOrDefault(m.getIdDetail(), List.of()), grilles)) {
                MarchePrevision p = r.prevision();
                if (p == null) {
                    incomplete = true;
                    rangees.add(List.of(List.of(r.libelle()), List.of("non daté"), List.of("")));
                } else if (p.isSansObjet()) {
                    rangees.add(List.of(List.of(r.libelle()), List.of("sans objet"),
                            List.of(p.getMotifSansObjet() == null ? "" : p.getMotifSansObjet())));
                } else {
                    rangees.add(List.of(List.of(r.libelle()), List.of(date(p.getDateDebut())), List.of(date(p.getDateFin()))));
                }
            }
            if (incomplete) {
                incompletes++;
            }
            els.add(new DocumentLibre.Tableau(3, rangees));
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        }
        if (incompletes > 0) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, incompletes + " ligne(s) n'ont pas tous les processus de leur "
                    + "modèle datés : le plan a été soumis avant que le calendrier complet soit exigé."));
        }
        String pied = "Calendrier produit par l'application le " + le.format(JOUR_HEURE) + " à partir du plan "
                + (plan.getRefeDossier() == null ? "n° " + plan.getIdDossier() : plan.getRefeDossier())
                + " ; la validation tracée du plan vaut signature.";
        return new DocumentLibre("CALENDRIER", null, els, pied);
    }

    /** Un rang du tableau : processus de la grille (dans son ordre) puis ceux saisis hors grille. */
    record Rang(String libelle, MarchePrevision prevision) {
    }

    private List<Rang> rangs(Marche m, List<MarchePrevision> saisies, Map<Integer, List<CapmDto>> grilles) {
        List<CapmDto> grille = m.getIdMode() == null ? List.of() : grilles.computeIfAbsent(m.getIdMode(), capmService::findAll);
        Map<Integer, MarchePrevision> parCapm = new LinkedHashMap<>();
        saisies.forEach(p -> parCapm.putIfAbsent(p.getIdCapm(), p));
        List<Rang> rangs = new ArrayList<>();
        for (CapmDto c : grille) {
            rangs.add(new Rang(c.getLibelleProcessus(), parCapm.remove(c.getIdCapm())));
        }
        parCapm.values().stream().sorted(Comparator.comparing((MarchePrevision p) -> ordre(p.getIdCapm()))).forEach(p ->
                rangs.add(new Rang(capms.findById(p.getIdCapm()).map(Capm::getLibelleProcessus).orElse("Étape " + p.getIdCapm()), p)));
        return rangs;
    }

    private int ordre(Integer idCapm) {
        return capms.findById(idCapm).map(Capm::getOrdre).map(o -> o == null ? 0 : o).orElse(0);
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(JOUR);
    }
}
