package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.SousCritereDto;
import cnm.prs.dto.TechniqueDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.Evaluation;
import cnm.prs.entity.EvaluationDeclaration;
import cnm.prs.entity.EvaluationEtape;
import cnm.prs.entity.EvaluationJournal;
import cnm.prs.entity.EvaluationNoteTechnique;
import cnm.prs.entity.EvaluationTechnique;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.AccesReserveException;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CaoMembreRepository;
import cnm.prs.repository.EvaluationDeclarationRepository;
import cnm.prs.repository.EvaluationJournalRepository;
import cnm.prs.repository.EvaluationNoteTechniqueRepository;
import cnm.prs.repository.EvaluationRepository;
import cnm.prs.repository.EvaluationTechniqueRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>L'évaluation technique des prestations intellectuelles</strong>, lot 3, tranche PI-c (demande front du 2026-10-07,
 * {@code demande-backend-2026-10-07-evaluation-pi.md}, §B4 ; V87 ; arbitrages du pilote Q3, Q4) — sur le socle de l'évaluation du lot 1
 * (évaluation ouverte, déclarations préalables, examen préliminaire de l'enveloppe technique arrêté par le président) :
 * <ul>
 *   <li>la <strong>grille</strong> : les critères {@code B06-TP-02} à {@code B06-TP-06} de la fiche validée, chacun détaillé en ses
 *   sous-critères s'il en a ({@link SousCriteresFiche}), sinon noté globalement ; le maximum de chaque élément est ses points ;</li>
 *   <li><strong>chaque membre</strong> déclaré sans conflit note chaque proposition retenue à l'examen préliminaire, élément par
 *   élément, de 0 au maximum, avec un motif (Q4) ;</li>
 *   <li>le serveur calcule la <strong>moyenne</strong> de chaque élément, la <strong>note technique</strong> (somme des moyennes) et le
 *   rang, et <strong>signale l'écart</strong> entre la note la plus haute et la plus basse d'un élément au-delà du seuil (20 % du
 *   maximum par défaut, {@code EVALUATION_ECART_NOTES_POURCENT}) ; simple alerte : la moyenne reste retenue ;</li>
 *   <li>le <strong>président arrête</strong> l'étape, lot par lot, une fois chaque proposition notée par chaque membre décideur ; les
 *   propositions sous le score minimum ({@code B06-TP-07}) sont alors <strong>éliminées</strong>, avec le motif : leur enveloppe
 *   financière ne sera pas ouverte (seconde séance, PI-d). Il peut rouvrir l'étape avec un motif.</li>
 * </ul>
 */
@Service
@Transactional
public class EvaluationTechniqueService {

    public static final String EN_COURS = "EN_COURS";
    public static final String QUALIFIEE = "QUALIFIEE";
    public static final String ELIMINEE = "ELIMINEE";
    public static final String PARAMETRE_ECART = "EVALUATION_ECART_NOTES_POURCENT";
    static final BigDecimal ECART_DEFAUT = BigDecimal.valueOf(20);
    static final String SCORE_MINIMUM = "B06-TP-07";

    private final EvaluationService evaluation;
    private final EvaluationRepository evaluations;
    private final EvaluationDeclarationRepository declarations;
    private final EvaluationNoteTechniqueRepository notes;
    private final EvaluationTechniqueRepository etapes;
    private final EvaluationJournalRepository journal;
    private final CaoMembreRepository caoMembres;
    private final ParametresInternesService internes;
    private final FicheMarcheService fiches;
    private final SousCriteresFiche sousCriteres;
    private final ParametreService parametres;
    private final cnm.prs.repository.SeanceFinanciereRepository financieres;
    private final Clock horloge;

    public EvaluationTechniqueService(EvaluationService evaluation, EvaluationRepository evaluations, EvaluationDeclarationRepository declarations,
            EvaluationNoteTechniqueRepository notes, EvaluationTechniqueRepository etapes, EvaluationJournalRepository journal,
            CaoMembreRepository caoMembres, ParametresInternesService internes, FicheMarcheService fiches, SousCriteresFiche sousCriteres,
            ParametreService parametres, cnm.prs.repository.SeanceFinanciereRepository financieres, Clock horloge) {
        this.evaluation = evaluation;
        this.evaluations = evaluations;
        this.declarations = declarations;
        this.notes = notes;
        this.etapes = etapes;
        this.journal = journal;
        this.caoMembres = caoMembres;
        this.internes = internes;
        this.fiches = fiches;
        this.sousCriteres = sousCriteres;
        this.parametres = parametres;
        this.financieres = financieres;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ lecture

    /** L'évaluation technique : CAO, responsable, PRMP, UGPM ; 404 sans évaluation ouverte ; 409 {@code CATEGORIE_SANS_NOTATION_TECHNIQUE}. */
    @Transactional(readOnly = true)
    public TechniqueDto lire(Long idDmc) {
        evaluation.controlerLecture(idDmc);
        return dto(idDmc, contexte(idDmc));
    }

    // ------------------------------------------------------------------ la notation (Q4)

    /**
     * La grille du membre appelant pour une proposition : chaque note sur un élément connu (400 {@code ELEMENT_INCONNU}), de 0 au maximum
     * (400 {@code NOTE_HORS_BAREME}), motivée (400 {@code MOTIF_OBLIGATOIRE}) ; 403 hors CAO, {@code MEMBRE_EN_CONFLIT} ; 409
     * {@code DECLARATION_MANQUANTE}, {@code CONFORMITE_NON_ARRETEE}, {@code OFFRE_ECARTEE}, {@code TECHNIQUE_ARRETEE},
     * {@code EVALUATION_CLOSE}. Une note saisie remplace la précédente du même membre ; le journal les garde toutes.
     */
    public TechniqueDto noter(Long idDmc, String idOffre, TechniqueDto.NotesRequest r) {
        String k = exigerDecideur(idDmc);
        Contexte c = contexte(idDmc);
        exigerEnCours(idDmc);
        EvaluationDto.Lot lot = c.lotDe(idOffre);
        if (lot == null) {
            throw new ResourceNotFoundException("Proposition introuvable dans l'évaluation : " + idOffre + ".");
        }
        if (!conformiteArretee(lot)) {
            throw new BusinessRuleException("L'examen préliminaire de ce lot n'est pas arrêté : la notation technique attend sa fin.",
                    "CONFORMITE_NON_ARRETEE");
        }
        EvaluationDto.OffreEvaluee o = lot.offres().stream().filter(x -> x.idOffre().equals(idOffre)).findFirst().orElseThrow();
        if (o.ecartee() != null) {
            throw new BusinessRuleException("Cette proposition est écartée à l'examen préliminaire : elle n'est pas notée.", "OFFRE_ECARTEE");
        }
        exigerNonArretee(idDmc, lot.lot());
        List<TechniqueDto.NoteSaisie> saisies = r == null || r.notes() == null ? List.of() : r.notes().stream().filter(Objects::nonNull).toList();
        if (saisies.isEmpty()) {
            throw new BadRequestException("Aucune note saisie.", "NOTE_HORS_BAREME");
        }
        for (TechniqueDto.NoteSaisie s : saisies) {
            TechniqueDto.Element e = c.elements().get(s.element());
            if (e == null) {
                throw new BadRequestException("Élément inconnu de la grille : " + s.element() + ".", "ELEMENT_INCONNU");
            }
            if (s.note() == null || s.note().signum() < 0 || s.note().compareTo(e.max()) > 0) {
                throw new BadRequestException("La note de « " + e.libelle() + " » se situe entre 0 et " + lisible(e.max()) + ".", "NOTE_HORS_BAREME");
            }
            if (s.motif() == null || s.motif().isBlank()) {
                throw new BadRequestException("La note de « " + e.libelle() + " » exige son motif.", "MOTIF_OBLIGATOIRE");
            }
        }
        LocalDateTime le = maintenant();
        for (TechniqueDto.NoteSaisie s : saisies) {
            EvaluationNoteTechnique n = notes.findByIdOffreAndImAndElement(idOffre, k, s.element())
                    .orElseGet(() -> new EvaluationNoteTechnique(null, idDmc, idOffre, k, s.element(), null, null, null));
            n.setNote(s.note());
            n.setMotif(s.motif().trim());
            n.setLe(le);
            notes.save(n);
        }
        tracer(idDmc, "NOTE_TECHNIQUE", "Proposition n° " + o.numero() + " (" + o.entreprise().raisonSociale() + ") — " + internes.nomMembre(k) + " : "
                + String.join(", ", saisies.stream().map(s -> s.element() + " = " + lisible(s.note())).toList()));
        return dto(idDmc, c);
    }

    /**
     * Le président arrête l'étape technique d'un lot : 409 {@code CONFORMITE_NON_ARRETEE}, {@code TECHNIQUE_ARRETEE},
     * {@code NOTATION_INCOMPLETE} (détails : les numéros des propositions dont une grille manque) ; les propositions sous le score minimum
     * sont éliminées. 404 lot inconnu.
     */
    public TechniqueDto arreter(Long idDmc, Integer lot, TechniqueDto.ArretRequest r) {
        String k = exigerPresident(idDmc);
        Contexte c = contexte(idDmc);
        exigerEnCours(idDmc);
        EvaluationDto.Lot l = c.lot(lot);
        if (!conformiteArretee(l)) {
            throw new BusinessRuleException("L'examen préliminaire de ce lot n'est pas arrêté.", "CONFORMITE_NON_ARRETEE");
        }
        exigerNonArretee(idDmc, lot);
        List<String> decideurs = decideurs(idDmc);
        Map<String, Map<String, Map<String, EvaluationNoteTechnique>>> parOffre = notesParOffre(idDmc);
        List<Integer> incompletes = new ArrayList<>();
        for (EvaluationDto.OffreEvaluee o : retenues(l)) {
            if (!complete(c, decideurs, parOffre.getOrDefault(o.idOffre(), Map.of()))) {
                incompletes.add(o.numero());
            }
        }
        if (!incompletes.isEmpty()) {
            throw new BusinessRuleException("Chaque membre doit noter chaque élément de chaque proposition : propositions n° " + incompletes + ".",
                    "NOTATION_INCOMPLETE", null, Map.of("offres", incompletes));
        }
        EvaluationTechnique e = etapes.findById(new EvaluationTechnique.Cle(idDmc, lot)).orElseGet(() -> new EvaluationTechnique(idDmc, lot, null,
                null, null, null, null));
        e.setArreteeLe(maintenant());
        e.setArreteePar(k);
        e.setObservation(r == null || r.observation() == null || r.observation().isBlank() ? null : r.observation().trim());
        etapes.save(e);
        TechniqueDto dto = dto(idDmc, c);
        TechniqueDto.Lot vu = dto.lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst().orElseThrow();
        tracer(idDmc, "TECHNIQUE_ARRETEE", "Lot " + lot + " : " + String.join(" ; ", vu.offres().stream()
                .map(o -> "n° " + o.numero() + " " + lisible(o.total()) + " pts, " + o.statut().toLowerCase(java.util.Locale.FRENCH)).toList()));
        return dto;
    }

    /** Le président rouvre l'étape technique d'un lot : motif obligatoire (400 {@code MOTIF_OBLIGATOIRE}) ; 409 {@code TECHNIQUE_NON_ARRETEE}. */
    public TechniqueDto rouvrir(Long idDmc, Integer lot, TechniqueDto.ReouvertureRequest r) {
        exigerPresident(idDmc);
        Contexte c = contexte(idDmc);
        exigerEnCours(idDmc);
        c.lot(lot);
        if (r == null || r.motif() == null || r.motif().isBlank()) {
            throw new BadRequestException("La réouverture exige son motif.", "MOTIF_OBLIGATOIRE");
        }
        // ⚠️ PI-d1 — les montants connus, les notes techniques ne se reprennent plus.
        if (financieres.existsById(idDmc)) {
            throw new BusinessRuleException("La seconde séance est ouverte : l'évaluation technique ne se rouvre plus.", "SEANCE_FINANCIERE_OUVERTE");
        }
        EvaluationTechnique e = etapes.findById(new EvaluationTechnique.Cle(idDmc, lot)).filter(x -> x.getArreteeLe() != null)
                .orElseThrow(() -> new BusinessRuleException("L'étape technique de ce lot n'est pas arrêtée.", "TECHNIQUE_NON_ARRETEE"));
        e.setArreteeLe(null);
        e.setArreteePar(null);
        e.setRouverteLe(maintenant());
        e.setMotifReouverture(r.motif().trim());
        etapes.save(e);
        tracer(idDmc, "TECHNIQUE_ROUVERTE", "Lot " + lot + " : " + r.motif().trim());
        return dto(idDmc, c);
    }

    /**
     * Les propositions qualifiées techniquement d'un lot arrêté (pour la seconde séance, PI-d) ; vide tant que l'étape n'est pas arrêtée.
     */
    @Transactional(readOnly = true)
    public List<String> qualifiees(Long idDmc, Integer lot) {
        TechniqueDto d = dto(idDmc, contexte(idDmc));
        return d.lots().stream().filter(l -> Objects.equals(l.lot(), lot) && l.arret() != null && l.arret().le() != null)
                .flatMap(l -> l.offres().stream()).filter(o -> QUALIFIEE.equals(o.statut())).map(TechniqueDto.Offre::idOffre).toList();
    }

    /** ⚠️ PI-d1 — les résultats techniques, sans garde : la seconde séance les lit. */
    @Transactional(readOnly = true)
    public TechniqueDto resultats(Long idDmc) {
        return dto(idDmc, contexte(idDmc));
    }

    /** ⚠️ PI-d1 — les mêmes résultats, vides hors PI ou tant que l'évaluation n'est pas ouverte (sans exception). */
    @Transactional(readOnly = true)
    public Optional<TechniqueDto> resultatsSiNotes(Long idDmc) {
        boolean pi = fiches.etatValide(idDmc).map(v -> ModelesDao.sigleLettre(v.categorie()) != null).orElse(false);
        return pi && evaluation.vueSansGarde(idDmc).isPresent() ? Optional.of(dto(idDmc, contexte(idDmc))) : Optional.empty();
    }

    // ------------------------------------------------------------------ la grille et la vue

    /** La grille de la fiche validée et les lots de l'évaluation. */
    private record Contexte(Map<String, TechniqueDto.Element> elements, BigDecimal scoreMinimum, List<EvaluationDto.Lot> lots) {

        EvaluationDto.Lot lot(Integer lot) {
            return lots.stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + "."));
        }

        EvaluationDto.Lot lotDe(String idOffre) {
            return lots.stream().filter(l -> l.offres().stream().anyMatch(o -> o.idOffre().equals(idOffre))).findFirst().orElse(null);
        }
    }

    private Contexte contexte(Long idDmc) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("La fiche de la procédure " + idDmc + " n'a pas de version validée."));
        if (ModelesDao.sigleLettre(v.categorie()) == null) {
            throw new BusinessRuleException("La notation technique est propre aux prestations intellectuelles.", "CATEGORIE_SANS_NOTATION_TECHNIQUE");
        }
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        List<SousCritereDto> sc = sousCriteres.lister(v.etat().getIdFiche());
        Map<String, TechniqueDto.Element> elements = new LinkedHashMap<>();
        for (String critere : SousCriteresFiche.CRITERES) {
            BigDecimal points = ControlesFicheMarche.nombre(v.valeur(critere));
            if (points == null || points.signum() <= 0) {
                continue;
            }
            ChampFicheMarche champ = v.champs().get(critere);
            String libelleCritere = champ == null ? critere : champ.getLibelle().replaceFirst("^Points\\s*:\\s*", "");
            List<SousCritereDto> sous = sc.stream().filter(s -> critere.equals(s.critere())).toList();
            if (sous.isEmpty()) {
                elements.put(critere, new TechniqueDto.Element(critere, critere, libelleCritere, libelleCritere, points));
            } else {
                for (int i = 0; i < sous.size(); i++) {
                    String code = critere + "#" + (i + 1);
                    elements.put(code, new TechniqueDto.Element(code, critere, libelleCritere, sous.get(i).libelle(), sous.get(i).points()));
                }
            }
        }
        return new Contexte(elements, ControlesFicheMarche.nombre(v.valeur(SCORE_MINIMUM)), ev.lots());
    }

    private TechniqueDto dto(Long idDmc, Contexte c) {
        BigDecimal seuil = seuilEcart();
        List<String> decideurs = decideurs(idDmc);
        Map<String, Map<String, Map<String, EvaluationNoteTechnique>>> parOffre = notesParOffre(idDmc);
        List<TechniqueDto.Lot> lots = new ArrayList<>();
        for (EvaluationDto.Lot l : c.lots()) {
            EvaluationTechnique e = etapes.findById(new EvaluationTechnique.Cle(idDmc, l.lot())).orElse(null);
            boolean arretee = e != null && e.getArreteeLe() != null;
            List<TechniqueDto.Offre> offres = new ArrayList<>();
            for (EvaluationDto.OffreEvaluee o : retenues(l)) {
                Map<String, Map<String, EvaluationNoteTechnique>> parMembre = parOffre.getOrDefault(o.idOffre(), Map.of());
                List<TechniqueDto.Grille> grilles = new ArrayList<>();
                parMembre.forEach((im, m) -> grilles.add(new TechniqueDto.Grille(im, internes.nomMembre(im), m.values().stream()
                        .sorted(Comparator.comparing(EvaluationNoteTechnique::getElement))
                        .map(n -> new TechniqueDto.Note(n.getElement(), n.getNote(), n.getMotif(), n.getLe())).toList())));
                List<TechniqueDto.Moyenne> moyennes = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                for (TechniqueDto.Element el : c.elements().values()) {
                    List<BigDecimal> ns = parMembre.values().stream().map(m -> m.get(el.code())).filter(Objects::nonNull)
                            .map(EvaluationNoteTechnique::getNote).toList();
                    if (ns.isEmpty()) {
                        moyennes.add(new TechniqueDto.Moyenne(el.code(), null, null, null, 0, false));
                        continue;
                    }
                    BigDecimal moy = ns.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(ns.size()), 2, RoundingMode.HALF_UP);
                    BigDecimal min = ns.stream().min(BigDecimal::compareTo).orElseThrow();
                    BigDecimal max = ns.stream().max(BigDecimal::compareTo).orElseThrow();
                    boolean ecart = max.subtract(min).multiply(BigDecimal.valueOf(100)).compareTo(seuil.multiply(el.max())) > 0;
                    moyennes.add(new TechniqueDto.Moyenne(el.code(), moy, min, max, ns.size(), ecart));
                    total = total.add(moy);
                }
                boolean complete = complete(c, decideurs, parMembre);
                String statut = !arretee ? EN_COURS : c.scoreMinimum() != null && total.compareTo(c.scoreMinimum()) < 0 ? ELIMINEE : QUALIFIEE;
                String motif = ELIMINEE.equals(statut) ? "Note technique de " + lisible(total) + " points, sous le score minimum de "
                        + lisible(c.scoreMinimum()) + " points (" + SCORE_MINIMUM + ")" : null;
                offres.add(new TechniqueDto.Offre(o.idOffre(), o.numero(), o.entreprise().nif(), o.entreprise().raisonSociale(), grilles, moyennes,
                        total, complete, statut, motif, null));
            }
            // Le rang : les propositions non éliminées, par note technique décroissante (égalité : même rang).
            List<TechniqueDto.Offre> classees = offres.stream().filter(o -> !ELIMINEE.equals(o.statut()))
                    .sorted(Comparator.comparing(TechniqueDto.Offre::total).reversed()).toList();
            Map<String, Integer> rangs = new HashMap<>();
            for (int i = 0; i < classees.size(); i++) {
                int rang = i > 0 && classees.get(i).total().compareTo(classees.get(i - 1).total()) == 0 ? rangs.get(classees.get(i - 1).idOffre()) : i + 1;
                rangs.put(classees.get(i).idOffre(), rang);
            }
            List<TechniqueDto.Offre> avecRang = offres.stream().map(o -> new TechniqueDto.Offre(o.idOffre(), o.numero(), o.nif(), o.raisonSociale(),
                    o.grilles(), o.moyennes(), o.total(), o.complete(), o.statut(), o.motifElimination(), rangs.get(o.idOffre()))).toList();
            TechniqueDto.Arret arret = e == null ? null : new TechniqueDto.Arret(e.getArreteeLe(), e.getArreteePar(),
                    e.getArreteePar() == null ? null : internes.nomMembre(e.getArreteePar()), e.getObservation(), e.getRouverteLe(), e.getMotifReouverture());
            lots.add(new TechniqueDto.Lot(l.lot(), conformiteArretee(l), arret, avecRang));
        }
        return new TechniqueDto(idDmc, c.scoreMinimum(), seuil, List.copyOf(c.elements().values()), lots);
    }

    private static boolean conformiteArretee(EvaluationDto.Lot l) {
        return l.etapesArretees() != null && l.etapesArretees().stream().anyMatch(a -> EvaluationEtape.CONFORMITE.equals(a.etape()));
    }

    private static List<EvaluationDto.OffreEvaluee> retenues(EvaluationDto.Lot l) {
        return l.offres().stream().filter(o -> o.ecartee() == null).toList();
    }

    /** Chaque membre décideur a noté chaque élément de la grille. */
    private static boolean complete(Contexte c, List<String> decideurs, Map<String, Map<String, EvaluationNoteTechnique>> parMembre) {
        return !decideurs.isEmpty() && decideurs.stream().allMatch(im -> parMembre.containsKey(im)
                && parMembre.get(im).keySet().containsAll(c.elements().keySet()));
    }

    /** offre → membre → élément → note. */
    private Map<String, Map<String, Map<String, EvaluationNoteTechnique>>> notesParOffre(Long idDmc) {
        Map<String, Map<String, Map<String, EvaluationNoteTechnique>>> m = new HashMap<>();
        for (EvaluationNoteTechnique n : notes.findByIdDmcOrderByIdAsc(idDmc)) {
            m.computeIfAbsent(n.getIdOffre(), x -> new LinkedHashMap<>()).computeIfAbsent(n.getIm(), x -> new LinkedHashMap<>()).put(n.getElement(), n);
        }
        return m;
    }

    /** Les membres de la commission déclarés sans conflit : ceux dont la grille est attendue. */
    private List<String> decideurs(Long idDmc) {
        return internes.membresCao(idDmc).stream()
                .filter(im -> declarations.findByIdDmcAndIm(idDmc, im).map(d -> !Boolean.TRUE.equals(d.getConflit())).orElse(false)).toList();
    }

    private BigDecimal seuilEcart() {
        BigDecimal v = ControlesFicheMarche.nombre(parametres.texte(PARAMETRE_ECART));
        return v == null ? ECART_DEFAUT : v;
    }

    // ------------------------------------------------------------------ gardes

    private void exigerEnCours(Long idDmc) {
        Evaluation e = evaluations.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        if (Evaluation.CLOSE.equals(e.getEtat())) {
            throw new BusinessRuleException("L'évaluation est close.", "EVALUATION_CLOSE");
        }
    }

    private void exigerNonArretee(Long idDmc, Integer lot) {
        if (etapes.findById(new EvaluationTechnique.Cle(idDmc, lot)).map(e -> e.getArreteeLe() != null).orElse(false)) {
            throw new BusinessRuleException("L'étape technique de ce lot est arrêtée : le président la rouvre, avec un motif.", "TECHNIQUE_ARRETEE");
        }
    }

    private String membreAppelant(Long idDmc) {
        String ref = CurrentUser.ref().orElse(null);
        return ref != null && TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null)) && internes.membresCao(idDmc).contains(ref)
                ? ref : null;
    }

    private String exigerDecideur(Long idDmc) {
        String k = membreAppelant(idDmc);
        if (k == null) {
            throw new AccessDeniedException("La notation technique se fait par les membres de la commission d'appel d'offres.");
        }
        EvaluationDeclaration d = declarations.findByIdDmcAndIm(idDmc, k).orElseThrow(() -> new BusinessRuleException(
                "Signez d'abord votre déclaration d'absence de conflit d'intérêts et de confidentialité.", "DECLARATION_MANQUANTE"));
        if (Boolean.TRUE.equals(d.getConflit())) {
            throw new AccesReserveException("Vous avez déclaré un conflit d'intérêts : vous ne notez pas.", "MEMBRE_EN_CONFLIT");
        }
        return k;
    }

    private String exigerPresident(Long idDmc) {
        String k = exigerDecideur(idDmc);
        boolean president = caoMembres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream()
                .anyMatch(m -> k.equals(m.getIdCompte()) && Boolean.TRUE.equals(m.getPresident()));
        if (!president) {
            throw new AccessDeniedException("L'étape technique s'arrête et se rouvre par le président de la commission.");
        }
        return k;
    }

    private static String lisible(BigDecimal b) {
        return b == null ? "—" : b.stripTrailingZeros().toPlainString();
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new EvaluationJournal(null, idDmc, maintenant(), CurrentUser.ref().or(CurrentUser::login).orElse(null), action, detail));
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
