package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SeanceDto;
import cnm.prs.dto.SeanceFinanciereDto;
import cnm.prs.dto.TechniqueDto;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.EvaluationJournal;
import cnm.prs.entity.Offre;
import cnm.prs.entity.Seance;
import cnm.prs.entity.SeanceApport;
import cnm.prs.entity.SeanceFinanciere;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EvaluationJournalRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.SeanceFinanciereRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>La seconde séance d'ouverture des prestations intellectuelles</strong>, lot 3, tranche PI-d1 (demande front du 2026-10-07,
 * §B3 ; V88 ; arbitrage du pilote Q1) — après l'arrêt de l'évaluation technique de chaque lot :
 * <ul>
 *   <li>le <strong>responsable</strong> l'ouvre ; elle n'ouvre que les enveloppes <strong>financières</strong> des propositions
 *   qualifiées techniquement — en « qualité technique exclusivement » et en « qualification du consultant », celle du seul premier classé
 *   (Q1) ; les autres ne s'ouvrent jamais (Q2 au juriste : elles restent scellées, sous la règle de conservation des offres) ;</li>
 *   <li>les candidats dont l'enveloppe s'ouvre sont invités à y assister ({@code SEANCE_FINANCIERE}) ; les membres de la CAO apportent
 *   leurs parts de ces seules enveloppes, avec les mêmes clés de la cérémonie (la part de secours, avec un motif) ; au quorum, elles
 *   s'ouvrent ensemble, par le même déchiffrement que la première séance ({@link SeanceService#ouvrir}) ;</li>
 *   <li>la lecture donne la note technique, le rang et l'acte d'engagement de chaque enveloppe ; le responsable clôt la séance : le
 *   <strong>PV</strong> lit les notes techniques et les montants, et nomme les enveloppes non ouvertes.</li>
 * </ul>
 * Les parts claires vivent en mémoire seule ({@link PartsEnSeance}), comme à la première séance.
 */
@Service
@Transactional
public class SeanceFinanciereService {

    static final String METHODE = "B02-MS-01";
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final SeanceService seance;
    private final PartsEnSeance memoire;
    private final SeanceRepository seances;
    private final SeanceFinanciereRepository financieres;
    private final OffreRepository offres;
    private final EvaluationTechniqueService technique;
    private final FicheMarcheService fiches;
    private final ParametresInternesService internes;
    private final NotificationService notifications;
    private final CompteCandidatRepository candidats;
    private final GenerateurDocumentsFiche generateur;
    private final EvaluationJournalRepository journal;
    private final ObjectMapper mapper;
    private final Clock horloge;

    public SeanceFinanciereService(SeanceService seance, PartsEnSeance memoire, SeanceRepository seances, SeanceFinanciereRepository financieres,
            OffreRepository offres, EvaluationTechniqueService technique, FicheMarcheService fiches, ParametresInternesService internes,
            NotificationService notifications, CompteCandidatRepository candidats, GenerateurDocumentsFiche generateur,
            EvaluationJournalRepository journal, ObjectMapper mapper, Clock horloge) {
        this.seance = seance;
        this.memoire = memoire;
        this.seances = seances;
        this.financieres = financieres;
        this.offres = offres;
        this.technique = technique;
        this.fiches = fiches;
        this.internes = internes;
        this.notifications = notifications;
        this.candidats = candidats;
        this.generateur = generateur;
        this.journal = journal;
        this.mapper = mapper;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ lecture

    /** La seconde séance : responsable, membres de la CAO, PRMP, UGPM ; 404 tant qu'elle n'est pas ouverte. */
    @Transactional(readOnly = true)
    public SeanceFinanciereDto lire(Long idDmc) {
        seance.exigerLecteur(idDmc, true);
        return dto(exiger(idDmc));
    }

    /** Le PV de la seconde séance (PDF, ou Word) ; 404 tant qu'il n'est pas produit. */
    @Transactional(readOnly = true)
    public byte[] pv(Long idDmc, boolean docx) {
        seance.exigerLecteur(idDmc, true);
        SeanceFinanciere s = exiger(idDmc);
        byte[] b = docx ? s.getPvDocx() : s.getPv();
        if (b == null) {
            throw new ResourceNotFoundException("Le PV de la seconde séance n'est pas produit.");
        }
        return b;
    }

    // ------------------------------------------------------------------ l'ouverture

    /**
     * Le responsable ouvre la seconde séance : 409 {@code SEANCE_TECHNIQUE_NON_CLOSE} (la première séance n'est pas close),
     * {@code TECHNIQUE_NON_ARRETEE} (détails : les lots), {@code AUCUNE_FINANCIERE_A_OUVRIR}, {@code SEANCE_FINANCIERE_OUVERTE} ; 409
     * {@code CATEGORIE_SANS_NOTATION_TECHNIQUE} hors PI. Les candidats dont l'enveloppe s'ouvre, et les membres, sont avertis.
     */
    public SeanceFinanciereDto ouvrir(Long idDmc) {
        seance.exigerResponsable(idDmc);
        if (financieres.existsById(idDmc)) {
            throw new BusinessRuleException("La seconde séance est déjà ouverte.", "SEANCE_FINANCIERE_OUVERTE");
        }
        if (!seances.findById(idDmc).map(s -> Seance.CLOSE.equals(s.getEtat())).orElse(false)) {
            throw new BusinessRuleException("La séance d'ouverture des propositions techniques n'est pas close.", "SEANCE_TECHNIQUE_NON_CLOSE");
        }
        TechniqueDto t = technique.resultats(idDmc);
        List<Integer> nonArretes = t.lots().stream().filter(l -> l.arret() == null || l.arret().le() == null).map(TechniqueDto.Lot::lot).toList();
        if (!nonArretes.isEmpty()) {
            throw new BusinessRuleException("L'évaluation technique n'est pas arrêtée pour les lots " + nonArretes + ".", "TECHNIQUE_NON_ARRETEE", null,
                    Map.of("lots", nonArretes));
        }
        String methode = fiches.etatValide(idDmc).map(v -> v.valeur(METHODE)).orElse(null);
        Selection sel = selection(idDmc, t, methode);
        if (sel.aOuvrir().isEmpty()) {
            throw new BusinessRuleException("Aucune enveloppe financière n'est à ouvrir : aucune proposition n'est qualifiée techniquement.",
                    "AUCUNE_FINANCIERE_A_OUVRIR");
        }
        SeanceFinanciere s = new SeanceFinanciere();
        s.setIdDmc(idDmc);
        s.setEtat(SeanceFinanciere.OUVERTE);
        s.setMethode(methode);
        s.setAOuvrir(String.join(",", sel.aOuvrir().stream().map(Offre::getIdOffre).toList()));
        s.setOuverteLe(maintenant());
        s.setOuvertePar(acteur());
        s.setSecoursEmploye(false);
        financieres.save(s);
        tracer(idDmc, "SEANCE_FINANCIERE", "Seconde séance ouverte : " + sel.aOuvrir().size() + " enveloppe(s) financière(s) à ouvrir, "
                + sel.nonOuvertes().size() + " non ouverte(s)");
        for (String k : internes.membresCao(idDmc)) {
            internes.notifierMembre(idDmc, k, TypeNotification.PARTS_ATTENDUES, "Seconde séance : apportez vos parts", "La seconde séance de la "
                    + "procédure " + idDmc + " (propositions financières) est ouverte : déverrouillez votre clé et apportez vos parts.");
        }
        for (Offre o : sel.aOuvrir()) {
            CompteCandidat c = candidats.findById(o.getIdCandidat()).orElse(null);
            notifications.emettreCandidat(TypeNotification.SEANCE_FINANCIERE, o.getIdCandidat(), c == null ? null : c.getEmail(), idDmc.intValue(),
                    TypeObjet.PROCEDURE, "Ouverture de votre proposition financière", "Votre proposition n° " + o.getNumero() + " est qualifiée "
                            + "techniquement : son enveloppe financière s'ouvre en seconde séance ; vous êtes invité à y assister.");
        }
        return dto(s);
    }

    private record Selection(List<Offre> aOuvrir, List<SeanceFinanciereDto.NonOuverte> nonOuvertes) {
    }

    /**
     * Les enveloppes financières à ouvrir : celles des propositions qualifiées ; en « qualité technique exclusivement » ou
     * « qualification du consultant », du seul premier rang. Les autres, avec leur motif.
     */
    private Selection selection(Long idDmc, TechniqueDto t, String methode) {
        boolean premierSeul = methode != null && (methode.startsWith("Qualité technique exclusivement") || methode.startsWith("Qualification"));
        List<Offre> toutes = offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc);
        List<Offre> aOuvrir = new ArrayList<>();
        List<SeanceFinanciereDto.NonOuverte> non = new ArrayList<>();
        for (TechniqueDto.Lot l : t.lots()) {
            for (TechniqueDto.Offre o : l.offres()) {
                Offre tech = toutes.stream().filter(x -> x.getIdOffre().equals(o.idOffre())).findFirst().orElse(null);
                Offre fin = tech == null ? null : toutes.stream().filter(x -> Offre.FINANCIERE.equals(x.getEnveloppe()) && Offre.DEPOSEE.equals(x.getEtat())
                        && Objects.equals(x.getIdEntreprise(), tech.getIdEntreprise()) && Objects.equals(x.getLot(), tech.getLot())).findFirst().orElse(null);
                if (fin == null) {
                    continue;
                }
                if (EvaluationTechniqueService.ELIMINEE.equals(o.statut())) {
                    non.add(new SeanceFinanciereDto.NonOuverte(o.numero(), o.raisonSociale(), l.lot(), "éliminée à l'évaluation technique : "
                            + o.motifElimination()));
                } else if (premierSeul && !Objects.equals(o.rang(), 1)) {
                    non.add(new SeanceFinanciereDto.NonOuverte(o.numero(), o.raisonSociale(), l.lot(), "classée au rang " + o.rang()
                            + " : seule l'enveloppe du premier classé s'ouvre (méthode « " + methode + " »)"));
                } else {
                    aOuvrir.add(fin);
                }
            }
        }
        return new Selection(aOuvrir, non);
    }

    // ------------------------------------------------------------------ les parts

    /** Les parts chiffrées de l'appelant pour les enveloppes à ouvrir (membre ; {@code role=SECOURS} pour la part de secours). */
    @Transactional(readOnly = true)
    public List<SeanceDto.PartChiffree> mesParts(Long idDmc, String role) {
        String detenteur = seance.detenteur(idDmc, role);
        SeanceFinanciere s = exigerOuverte(idDmc);
        return seance.partsPour(idDmc, detenteur, aOuvrir(s));
    }

    /**
     * Apporte toutes ses parts en une fois : 409 {@code SEANCE_NON_OUVERTE}, {@code PARTS_INCOMPLETES} ({@code details.offres}),
     * {@code PART_INVALIDE}, {@code CLE_ABSENTE} ; 400 {@code MOTIF_ABSENT} pour la part de secours. Au quorum, tout s'ouvre ensemble.
     */
    public SeanceFinanciereDto apporter(Long idDmc, String role, SeanceDto.Apport a) {
        String detenteur = seance.detenteur(idDmc, role);
        SeanceFinanciere s = exigerOuverte(idDmc);
        boolean secours = SeanceApport.SECOURS.equals(detenteur);
        String motif = a == null || a.motif() == null || a.motif().isBlank() ? null : a.motif().trim();
        if (secours && motif == null) {
            throw new BadRequestException("L'emploi de la part de secours exige un motif, imprimé au PV.", "MOTIF_ABSENT");
        }
        List<Offre> aOuvrir = aOuvrir(s);
        List<SeanceDto.PartChiffree> attendues = seance.partsPour(idDmc, detenteur, aOuvrir);
        if (attendues.isEmpty()) {
            throw new BusinessRuleException("Aucune enveloppe à ouvrir n'est scellée pour votre clé.", "CLE_ABSENTE");
        }
        Map<String, byte[]> recues = new LinkedHashMap<>();
        for (SeanceDto.PartClaire p : a == null || a.parts() == null ? List.<SeanceDto.PartClaire>of() : a.parts()) {
            byte[] b = p == null ? null : ClesRsa.decoder(p.partClaire());
            if (b == null || !DechiffrementOffre.partValide(b)) {
                throw new BusinessRuleException("La part de l'enveloppe " + (p == null ? "?" : p.idOffre()) + " est invalide (33 octets attendus, "
                        + "abscisse non nulle).", "PART_INVALIDE");
            }
            recues.put(p.idOffre(), b);
        }
        Set<String> ids = new HashSet<>(attendues.stream().map(SeanceDto.PartChiffree::idOffre).toList());
        List<String> manquantes = ids.stream().filter(id -> !recues.containsKey(id)).toList();
        if (!manquantes.isEmpty()) {
            throw new BusinessRuleException("Vos parts valent pour toutes les enveloppes à ouvrir ou pour aucune : il en manque " + manquantes.size()
                    + ".", "PARTS_INCOMPLETES", null, Map.of("offres", manquantes));
        }
        recues.keySet().retainAll(ids);
        Map<String, Map<String, byte[]>> deja = memoire.de(idDmc);
        for (Map.Entry<String, byte[]> e : recues.entrySet()) {
            int x = e.getValue()[DechiffrementOffre.TAILLE_PART - 1] & 0xff;
            for (Map.Entry<String, Map<String, byte[]>> autre : deja.entrySet()) {
                byte[] b = autre.getKey().equals(detenteur) ? null : autre.getValue().get(e.getKey());
                if (b != null && (b[DechiffrementOffre.TAILLE_PART - 1] & 0xff) == x) {
                    throw new BusinessRuleException("La part de l'enveloppe " + e.getKey() + " porte l'abscisse d'une part déjà apportée.", "PART_INVALIDE");
                }
            }
        }
        memoire.poser(idDmc, detenteur, recues);
        if (secours) {
            s.setSecoursEmploye(true);
            s.setSecoursMotif(motif);
        } else {
            Set<String> presents = new LinkedHashSet<>(cnm.prs.entity.ChampFicheMarche.liste(s.getPresents()));
            presents.add(detenteur);
            s.setPresents(String.join(",", presents));
        }
        financieres.save(s);
        tracer(idDmc, "PARTS_FINANCIERES", (secours ? "part de secours (" + motif + ")" : internes.nomMembre(detenteur)) + " : " + recues.size()
                + " part(s)");
        Integer quorum = seance.quorum(idDmc);
        if (quorum != null && memoire.de(idDmc).size() >= quorum) {
            Map<String, Map<String, byte[]>> parts = memoire.de(idDmc);
            try {
                for (Offre o : aOuvrir) {
                    seance.ouvrir(o, parts, quorum);
                }
            } finally {
                memoire.oublier(idDmc);
            }
            s.setEtat(SeanceFinanciere.DECHIFFREE);
            s.setDechiffreeLe(maintenant());
            financieres.save(s);
            tracer(idDmc, "SEANCE_FINANCIERE_DECHIFFREE", "quorum atteint (" + quorum + ") : " + aOuvrir.size() + " enveloppe(s) financière(s) ouverte(s)");
        }
        return dto(s);
    }

    // ------------------------------------------------------------------ la clôture et le PV

    /**
     * Le responsable clôt la seconde séance et produit son PV : les présents, les notes techniques et les montants lus, les enveloppes
     * non ouvertes ; 400 {@code MEMBRE_INCONNU} ; 409 {@code SEANCE_NON_DECHIFFREE}, {@code SEANCE_CLOSE}.
     */
    public SeanceFinanciereDto cloturer(Long idDmc, SeanceFinanciereDto.Cloture c) {
        seance.exigerResponsable(idDmc);
        SeanceFinanciere s = exiger(idDmc);
        if (SeanceFinanciere.CLOSE.equals(s.getEtat())) {
            throw new BusinessRuleException("La seconde séance est close.", "SEANCE_CLOSE");
        }
        if (!SeanceFinanciere.DECHIFFREE.equals(s.getEtat())) {
            throw new BusinessRuleException("Les enveloppes financières ne sont pas encore ouvertes.", "SEANCE_NON_DECHIFFREE");
        }
        List<String> membres = internes.membresCao(idDmc);
        Set<String> presents = new LinkedHashSet<>(cnm.prs.entity.ChampFicheMarche.liste(s.getPresents()));
        for (String im : c == null || c.presents() == null ? List.<String>of() : c.presents()) {
            if (!membres.contains(im)) {
                throw new BadRequestException("« " + im + " » n'est pas membre de la commission d'appel d'offres.", "MEMBRE_INCONNU");
            }
            presents.add(im);
        }
        s.setPresents(presents.isEmpty() ? null : String.join(",", presents));
        s.setAutres(c == null || c.autres() == null ? null : mapper.writeValueAsString(c.autres().stream()
                .filter(x -> x != null && x.nom() != null && !x.nom().isBlank()).toList()));
        s.setObservations(c == null || c.observations() == null || c.observations().isBlank() ? null : c.observations().trim());
        s.setCloseLe(maintenant());
        s.setEtat(SeanceFinanciere.CLOSE);
        SeanceFinanciereDto d = dto(s);
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(document(s, d))) {
            if ("pdf".equals(f.extension())) {
                s.setPv(f.contenu());
            } else if ("docx".equals(f.extension())) {
                s.setPvDocx(f.contenu());
            }
        }
        financieres.save(s);
        tracer(idDmc, "SEANCE_FINANCIERE_CLOSE", "PV de la seconde séance produit");
        return dto(s);
    }

    private DocumentLibre document(SeanceFinanciere s, SeanceFinanciereDto d) {
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "PROCÈS-VERBAL D'OUVERTURE DES PROPOSITIONS FINANCIÈRES"));
        para(el, "Procédure " + s.getIdDmc() + (s.getMethode() == null ? "" : " — méthode de sélection : " + s.getMethode())
                + ". Séance ouverte le " + s.getOuverteLe().format(HORODATAGE) + ", enveloppes ouvertes le "
                + (s.getDechiffreeLe() == null ? "—" : s.getDechiffreeLe().format(HORODATAGE)) + ".");
        sous(el, "Présents");
        d.presents().forEach(im -> para(el, internes.nomMembre(im) + " (membre de la commission)"));
        d.autres().forEach(a -> para(el, a.nom() + (a.qualite() == null ? "" : " — " + a.qualite())));
        if (s.getSecoursEmploye()) {
            para(el, "La part de secours a été employée. Motif : " + s.getSecoursMotif());
        }
        sous(el, "Propositions financières ouvertes");
        for (SeanceFinanciereDto.Enveloppe e : d.aOuvrir()) {
            Map<String, Object> ae = e.acteEngagement();
            para(el, "Proposition n° " + e.numero() + " — " + e.raisonSociale() + (e.lot() == null ? "" : ", lot " + e.lot()) + " : note technique "
                    + (e.noteTechnique() == null ? "—" : e.noteTechnique().stripTrailingZeros().toPlainString()) + " points (rang " + e.rangTechnique()
                    + ") ; " + (ae == null ? "acte d'engagement illisible" : "montant HT : " + ae.get("montantHt") + " ; montant TTC : " + ae.get("montantTtc")
                            + " " + Objects.toString(ae.get("monnaie"), "MGA")) + " ; intégrité : " + e.integrite() + ".");
        }
        if (!d.nonOuvertes().isEmpty()) {
            sous(el, "Propositions financières non ouvertes");
            d.nonOuvertes().forEach(n -> para(el, "Proposition n° " + n.numero() + " — " + n.raisonSociale() + " : " + n.motif() + "."));
        }
        if (s.getObservations() != null) {
            sous(el, "Observations");
            para(el, s.getObservations());
        }
        para(el, "Établi par le responsable de la procédure le " + s.getCloseLe().format(HORODATAGE) + ".");
        return new DocumentLibre("PV_FINANCIER", null, el, "Procédure " + s.getIdDmc() + " — PV d'ouverture des propositions financières");
    }

    // ------------------------------------------------------------------ outils

    private SeanceFinanciereDto dto(SeanceFinanciere s) {
        Long idDmc = s.getIdDmc();
        TechniqueDto t = technique.resultatsSiNotes(idDmc).orElse(null);
        Map<String, TechniqueDto.Offre> parEntrepriseLot = new LinkedHashMap<>();
        List<Offre> toutes = offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc);
        for (TechniqueDto.Lot l : t == null ? List.<TechniqueDto.Lot>of() : t.lots()) {
            for (TechniqueDto.Offre o : l.offres()) {
                toutes.stream().filter(x -> x.getIdOffre().equals(o.idOffre())).findFirst()
                        .ifPresent(x -> parEntrepriseLot.put(x.getIdEntreprise() + "#" + x.getLot(), o));
            }
        }
        Map<String, Map<String, byte[]>> parts = memoire.de(idDmc);
        List<SeanceFinanciereDto.Enveloppe> env = new ArrayList<>();
        for (Offre o : aOuvrir(s)) {
            TechniqueDto.Offre tech = parEntrepriseLot.get(o.getIdEntreprise() + "#" + o.getLot());
            int recues = (int) parts.values().stream().filter(p -> p.containsKey(o.getIdOffre())).count();
            Map<String, Object> ae = null;
            if (o.getLecture() != null) {
                Map<String, Object> l = mapper.readValue(o.getLecture(), new TypeReference<Map<String, Object>>() {
                });
                @SuppressWarnings("unchecked")
                Map<String, Object> x = (Map<String, Object>) l.get("acteEngagement");
                ae = x;
            }
            env.add(new SeanceFinanciereDto.Enveloppe(o.getIdOffre(), o.getNumero(), o.getRaisonSociale(), o.getLot(), tech == null ? null : tech.total(),
                    tech == null ? null : tech.rang(), recues, o.getIntegrite(), ae));
        }
        List<SeanceDto.Autre> autres = s.getAutres() == null ? List.of() : mapper.readValue(s.getAutres(), new TypeReference<List<SeanceDto.Autre>>() {
        });
        return new SeanceFinanciereDto(idDmc, s.getEtat(), s.getMethode(), seance.quorum(idDmc), env, t == null ? List.of() : selection(idDmc, t, s.getMethode()).nonOuvertes(),
                cnm.prs.entity.ChampFicheMarche.liste(s.getPresents()), autres, Boolean.TRUE.equals(s.getSecoursEmploye()), s.getOuverteLe(),
                s.getDechiffreeLe(), s.getCloseLe(), s.getPv() != null);
    }

    private List<Offre> aOuvrir(SeanceFinanciere s) {
        List<Offre> out = new ArrayList<>();
        for (String id : cnm.prs.entity.ChampFicheMarche.liste(s.getAOuvrir())) {
            offres.findById(id).ifPresent(out::add);
        }
        return out;
    }

    private SeanceFinanciere exiger(Long idDmc) {
        return financieres.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("La seconde séance n'est pas ouverte."));
    }

    private SeanceFinanciere exigerOuverte(Long idDmc) {
        SeanceFinanciere s = financieres.findById(idDmc).filter(x -> SeanceFinanciere.OUVERTE.equals(x.getEtat()))
                .orElseThrow(() -> new BusinessRuleException("La seconde séance n'est pas ouverte aux parts.", "SEANCE_NON_OUVERTE"));
        return s;
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static void sous(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, t));
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new EvaluationJournal(null, idDmc, maintenant(), acteur(), action, detail));
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
