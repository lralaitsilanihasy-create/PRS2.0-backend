package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CeremonieDto;
import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.dto.OffreDto;
import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.CaoMembre;
import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.CleDetenteur;
import cnm.prs.entity.ExclusionArmp;
import cnm.prs.entity.Offre;
import cnm.prs.entity.RapprochementCandidat;
import cnm.prs.entity.Seance;
import cnm.prs.entity.SeanceApport;
import cnm.prs.entity.SeanceJournal;
import cnm.prs.entity.SeanceSignature;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.AccesReserveException;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CaoMembreRepository;
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CleDetenteurRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.SeanceApportRepository;
import cnm.prs.repository.SeanceJournalRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.repository.SeanceSignatureRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>L'ouverture des plis en séance</strong> (demande front du 2026-10-04, soumission en ligne, lot 4 ; ADR-0013 §1, §2, §5 ;
 * V69).
 * <ul>
 *   <li>À l'heure d'ouverture ({@code B04-OP-02} + {@code B04-OP-03}), la date limite passée, le <strong>responsable</strong> ouvre la
 *       séance ; chaque <strong>membre de la CAO</strong> reçoit alors ses parts chiffrées ({@code mes-parts}), les déchiffre dans son
 *       navigateur et les apporte <strong>toutes en une fois</strong>. Avant l'ouverture, personne n'a rien — pas même les parts
 *       chiffrées. Les parts claires vivent en mémoire ({@link PartsEnSeance}), jamais en base ni au journal.</li>
 *   <li>Au <strong>quorum</strong> (la part de secours compte pour une, avec un motif), le serveur, dans un même geste et pour chaque
 *       offre déposée : écarte l'offre d'une entreprise exclue depuis son dépôt (non déchiffrée), recalcule l'empreinte du conteneur
 *       ({@code ALTEREE} signalée), recombine {@code K} par BouncyCastle ({@link DechiffrementOffre}), déchiffre, relit l'archive ZIP et
 *       son {@code manifeste.json}, vérifie l'empreinte de chaque pièce, range le clair sur disque — puis <strong>oublie {@code K} et les
 *       parts</strong>. Un échec sur une offre ({@code LECTURE_IMPOSSIBLE}) n'arrête pas les autres.</li>
 *   <li>La lecture en séance, les pièces, le PV d'ouverture (ou de carence, ou de constat S5), sa publication si {@code B04-OP-13 = OUI}
 *       (sans les alertes ni la vérification des NIF).</li>
 * </ul>
 * Lecteurs : le responsable, les membres de la CAO, la PRMP et l'UGPM de la fiche (l'état, puis la lecture ; pas les pièces pour
 * l'UGPM). Écritures : le responsable ; les parts, chaque membre pour lui-même.
 */
@Service
@Transactional
public class SeanceService {

    static final String INTACTE = "INTACTE";
    static final String ALTEREE = "ALTEREE";
    static final String LECTURE_IMPOSSIBLE = "LECTURE_IMPOSSIBLE";
    static final String MANIFESTE = "manifeste.json";
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SeanceRepository seances;
    private final SeanceApportRepository apports;
    private final SeanceJournalRepository journal;
    private final SeanceSignatureRepository signatures;
    /** ⚠️ 2026-10-05 (lot 5) — les formulaires de l'offre (format 3), analysés à l'ouverture. */
    private final FormulairesEnLigne formulaires;
    private final PartsEnSeance memoire;
    private final OffreRepository offres;
    private final StockageOffres stockage;
    private final CleDetenteurRepository cles;
    private final CeremonieClesRepository ceremonies;
    private final CeremonieService ceremonieService;
    private final ParametresInternesService internes;
    private final CaoMembreRepository caoMembres;
    private final FicheMarcheService fiches;
    private final ProceduresEnLigneService procedures;
    private final ExclusionArmpService exclusions;
    private final EntrepriseCandidatService entreprises;
    private final RapprochementCandidatService rapprochements;
    private final CompteCandidatRepository candidats;
    private final NotificationService notifications;
    private final GenerateurDocumentsFiche generateur;
    private final DossierMecRepository dmcRepository;
    private final ObjectMapper mapper;
    private final Clock horloge;

    public SeanceService(SeanceRepository seances, SeanceApportRepository apports, SeanceJournalRepository journal, PartsEnSeance memoire,
            OffreRepository offres, StockageOffres stockage, CleDetenteurRepository cles, CeremonieClesRepository ceremonies,
            CeremonieService ceremonieService, ParametresInternesService internes, CaoMembreRepository caoMembres, FicheMarcheService fiches,
            ProceduresEnLigneService procedures, ExclusionArmpService exclusions, EntrepriseCandidatService entreprises,
            RapprochementCandidatService rapprochements, CompteCandidatRepository candidats, NotificationService notifications,
            GenerateurDocumentsFiche generateur, DossierMecRepository dmcRepository, ObjectMapper mapper, Clock horloge,
            SeanceSignatureRepository signatures, FormulairesEnLigne formulaires) {
        this.formulaires = formulaires;
        this.signatures = signatures;
        this.seances = seances;
        this.apports = apports;
        this.journal = journal;
        this.memoire = memoire;
        this.offres = offres;
        this.stockage = stockage;
        this.cles = cles;
        this.ceremonies = ceremonies;
        this.ceremonieService = ceremonieService;
        this.internes = internes;
        this.caoMembres = caoMembres;
        this.fiches = fiches;
        this.procedures = procedures;
        this.exclusions = exclusions;
        this.entreprises = entreprises;
        this.rapprochements = rapprochements;
        this.candidats = candidats;
        this.notifications = notifications;
        this.generateur = generateur;
        this.dmcRepository = dmcRepository;
        this.mapper = mapper;
        this.horloge = horloge;
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge);
    }

    // ------------------------------------------------------------------ §B1 l'état, ouvrir, présences

    @Transactional(readOnly = true)
    public SeanceDto lire(Long idDmc) {
        exigerLecteur(idDmc, true);
        return dto(idDmc);
    }

    /** Ouvrir (responsable) : 409 {@code SEANCE_PREMATUREE} (l'heure dans {@code details}), {@code DEPOTS_NON_CLOS}, {@code SEANCE_DEJA_OUVERTE}. */
    public SeanceDto ouvrir(Long idDmc) {
        exigerResponsable(idDmc);
        if (seances.existsById(idDmc)) {
            throw new BusinessRuleException("La séance est déjà ouverte.", "SEANCE_DEJA_OUVERTE");
        }
        LocalDateTime maintenant = maintenant();
        LocalDateTime heure = heureOuverture(idDmc);
        if (heure != null && maintenant.isBefore(heure)) {
            throw new BusinessRuleException("La séance ne s'ouvre qu'à l'heure d'ouverture des plis (" + heure.format(HORODATAGE) + ").",
                    "SEANCE_PREMATUREE", null, Map.of("heureOuverture", RemiseElectronique.isoMinute(heure)));
        }
        LocalDateTime limite = dateLimite(idDmc);
        if (limite == null || maintenant.isBefore(limite)) {
            throw new BusinessRuleException("La date limite de remise des offres n'est pas passée : les dépôts ne sont pas clos.",
                    "DEPOTS_NON_CLOS");
        }
        Seance s = new Seance();
        s.setIdDmc(idDmc);
        s.setOuverteLe(maintenant);
        boolean carence = deposees(idDmc).isEmpty();
        s.setEtat(carence ? Seance.DECHIFFREE : Seance.OUVERTE);
        if (carence) {
            s.setDechiffreeLe(maintenant);
        }
        seances.save(s);
        tracer(idDmc, "OUVERTURE", carence ? "aucune offre déposée : carence" : deposees(idDmc).size() + " offre(s) déposée(s)");
        if (!carence) {
            for (String k : internes.membresCao(idDmc)) {
                internes.notifierMembre(idDmc, k, TypeNotification.PARTS_ATTENDUES, "Séance d'ouverture : apportez vos parts",
                        "La séance d'ouverture des plis de la procédure " + idDmc + " est ouverte : déverrouillez votre clé et apportez vos "
                                + "parts.");
            }
        }
        return dto(idDmc);
    }

    /** Présences (responsable) : les membres de la CAO présents, les autres présents ; 400 un membre inconnu ; 409 séance close. */
    public SeanceDto presences(Long idDmc, SeanceDto.Presences p) {
        exigerResponsable(idDmc);
        Seance s = seanceModifiable(idDmc);
        List<String> membres = internes.membresCao(idDmc);
        Set<String> presents = new LinkedHashSet<>();
        for (String im : p == null || p.presents() == null ? List.<String>of() : p.presents()) {
            if (!membres.contains(im)) {
                throw new BadRequestException("« " + im + " » n'est pas membre de la commission d'appel d'offres.", "MEMBRE_INCONNU");
            }
            presents.add(im);
        }
        List<SeanceDto.Autre> autres = new ArrayList<>();
        for (SeanceDto.Autre a : p == null || p.autres() == null ? List.<SeanceDto.Autre>of() : p.autres()) {
            if (a == null || a.nom() == null || a.nom().isBlank()) {
                throw new BadRequestException("Le nom de chaque autre présent est attendu.", "PRESENT_INVALIDE");
            }
            autres.add(new SeanceDto.Autre(a.nom().trim(), a.qualite() == null ? null : a.qualite().trim()));
        }
        // Un membre qui a apporté ses parts reste présent d'office.
        presents.addAll(memoire.de(idDmc).keySet().stream().filter(membres::contains).toList());
        s.setPresents(presents.isEmpty() ? null : String.join(",", presents));
        s.setAutres(mapper.writeValueAsString(autres));
        seances.save(s);
        tracer(idDmc, "PRESENCES", presents.size() + " membre(s), " + autres.size() + " autre(s)");
        return dto(idDmc);
    }

    // ------------------------------------------------------------------ §B2 les parts

    /** Les parts chiffrées de l'appelant (membre ; responsable avec {@code role=SECOURS}), séance ouverte seulement. */
    @Transactional(readOnly = true)
    public List<SeanceDto.PartChiffree> mesParts(Long idDmc, String role) {
        String detenteur = detenteur(idDmc, role);
        Seance s = exigerOuverte(idDmc);
        exigerSecoursDemande(idDmc, detenteur, s);
        return partsDe(idDmc, detenteur);
    }

    /**
     * ⚠️ 2026-10-05 (dépositaire, §B3) — le responsable <strong>demande</strong> la part de secours, avec le motif porté au PV ;
     * {@code SECOURS_DEMANDE} part au dépositaire, qui l'apporte depuis son espace. 400 {@code MOTIF_ABSENT} ; 409
     * {@code SEANCE_NON_OUVERTE}, {@code SECOURS_INUTILE} (la part de secours est déjà apportée, ou le quorum déjà réuni),
     * {@code GESTE_DU_RESPONSABLE} (clé générée selon l'ancien geste : le responsable apporte lui-même la part). Une nouvelle demande
     * remplace la précédente.
     */
    public SeanceDto demanderSecours(Long idDmc, SeanceDto.DemandeSecours d) {
        exigerResponsable(idDmc);
        if (d == null || d.motif() == null || d.motif().isBlank()) {
            throw new BadRequestException("La demande de la part de secours exige un motif, imprimé au PV.", "MOTIF_ABSENT");
        }
        Seance s = exigerOuverte(idDmc);
        Map<String, Map<String, byte[]>> deja = memoire.de(idDmc);
        Integer quorum = quorum(idDmc);
        if (deja.containsKey(SeanceApport.SECOURS) || quorum != null && deja.size() >= quorum) {
            throw new BusinessRuleException("La part de secours est déjà apportée, ou le quorum déjà réuni : elle ne sert pas.",
                    "SECOURS_INUTILE");
        }
        if (CleDetenteur.PAR_RESPONSABLE.equals(secoursGenerePar(idDmc))) {
            throw new BusinessRuleException("Cette clé de secours a été générée chez le responsable (ancien geste) : apportez la part "
                    + "vous-même, avec la phrase du pli et le motif.", "GESTE_DU_RESPONSABLE");
        }
        s.setSecoursDemandeMotif(d.motif().trim());
        s.setSecoursDemandeLe(maintenant());
        seances.save(s);
        tracer(idDmc, "SECOURS_DEMANDE", "part de secours demandée au dépositaire : " + d.motif().trim());
        internes.notifierDepositaire(idDmc, TypeNotification.SECOURS_DEMANDE, "Séance d'ouverture : apportez la part de secours",
                "Le responsable de la procédure " + idDmc + " demande la part de secours (" + d.motif().trim() + "). Connectez-vous, "
                        + "déverrouillez votre clé de secours et apportez la part.");
        return dto(idDmc);
    }

    /** ⚠️ V71 — qui a généré la clé de secours active : {@code RESPONSABLE}, {@code DEPOSITAIRE}, {@code null} sans clé. */
    private String secoursGenerePar(Long idDmc) {
        return cles.findFirstByIdDmcAndRoleAndDateArchivageIsNull(idDmc, CleDetenteur.SECOURS)
                .map(c -> c.getGenerePar() == null ? CleDetenteur.PAR_RESPONSABLE : c.getGenerePar()).orElse(null);
    }

    /** ⚠️ V71 (§B3) — le dépositaire n'apporte sa part que demandée : 409 {@code SECOURS_NON_DEMANDE}. */
    private void exigerSecoursDemande(Long idDmc, String detenteur, Seance s) {
        if (SeanceApport.SECOURS.equals(detenteur) && !CleDetenteur.PAR_RESPONSABLE.equals(secoursGenerePar(idDmc))
                && s.getSecoursDemandeLe() == null) {
            throw new BusinessRuleException("Le responsable de la procédure n'a pas demandé la part de secours.", "SECOURS_NON_DEMANDE");
        }
    }

    /**
     * Apporte toutes ses parts claires en une fois : 409 {@code SEANCE_NON_OUVERTE}, {@code PARTS_INCOMPLETES} (les offres manquantes
     * dans {@code details.offres}), {@code PART_INVALIDE} (33 octets, abscisse non nulle et distincte de celles déjà apportées pour la
     * même offre), {@code CLE_ABSENTE} ; 400 sans motif pour la part de secours. Au quorum, tout s'ouvre ensemble.
     */
    public SeanceDto apporter(Long idDmc, String role, SeanceDto.Apport a) {
        String detenteur = detenteur(idDmc, role);
        Seance seance = exigerOuverte(idDmc);
        boolean secours = SeanceApport.SECOURS.equals(detenteur);
        // ⚠️ V71 (§B3) — le dépositaire apporte une part demandée, le motif est celui de la demande ; l'ancien geste garde le motif au corps.
        exigerSecoursDemande(idDmc, detenteur, seance);
        boolean parDepositaire = secours && !CleDetenteur.PAR_RESPONSABLE.equals(secoursGenerePar(idDmc));
        String motifSecours = !secours ? null : parDepositaire ? seance.getSecoursDemandeMotif()
                : a == null || a.motif() == null || a.motif().isBlank() ? null : a.motif().trim();
        if (secours && motifSecours == null) {
            throw new BadRequestException("L'emploi de la part de secours exige un motif, imprimé au PV.", "MOTIF_ABSENT");
        }
        List<SeanceDto.PartChiffree> attendues = partsDe(idDmc, detenteur);
        if (attendues.isEmpty()) {
            throw new BusinessRuleException("Aucune offre déposée n'est scellée pour votre clé.", "CLE_ABSENTE");
        }
        Map<String, byte[]> recues = new LinkedHashMap<>();
        for (SeanceDto.PartClaire p : a == null || a.parts() == null ? List.<SeanceDto.PartClaire>of() : a.parts()) {
            byte[] b = p == null ? null : ClesRsa.decoder(p.partClaire());
            if (b == null || !DechiffrementOffre.partValide(b)) {
                throw new BusinessRuleException("La part de l'offre " + (p == null ? "?" : p.idOffre()) + " est invalide (33 octets attendus, "
                        + "abscisse non nulle).", "PART_INVALIDE");
            }
            recues.put(p.idOffre(), b);
        }
        List<String> manquantes = attendues.stream().map(SeanceDto.PartChiffree::idOffre).filter(id -> !recues.containsKey(id)).toList();
        if (!manquantes.isEmpty()) {
            throw new BusinessRuleException("Vos parts valent pour toutes les offres ou pour aucune : il en manque " + manquantes.size() + ".",
                    "PARTS_INCOMPLETES", null, Map.of("offres", manquantes));
        }
        Set<String> attenduesIds = new HashSet<>(attendues.stream().map(SeanceDto.PartChiffree::idOffre).toList());
        recues.keySet().retainAll(attenduesIds);
        // Une même abscisse apportée par deux détenteurs pour une offre : ce n'est pas sa part.
        Map<String, Map<String, byte[]>> deja = memoire.de(idDmc);
        for (Map.Entry<String, byte[]> e : recues.entrySet()) {
            int x = e.getValue()[DechiffrementOffre.TAILLE_PART - 1] & 0xff;
            for (Map.Entry<String, Map<String, byte[]>> autre : deja.entrySet()) {
                byte[] b = autre.getKey().equals(detenteur) ? null : autre.getValue().get(e.getKey());
                if (b != null && (b[DechiffrementOffre.TAILLE_PART - 1] & 0xff) == x) {
                    throw new BusinessRuleException("La part de l'offre " + e.getKey() + " porte l'abscisse d'une part déjà apportée par un "
                            + "autre détenteur.", "PART_INVALIDE");
                }
            }
        }
        memoire.poser(idDmc, detenteur, recues);
        SeanceApport ap = apports.findByIdDmcAndDetenteur(idDmc, detenteur).orElseGet(SeanceApport::new);
        ap.setIdDmc(idDmc);
        ap.setDetenteur(detenteur);
        ap.setNombre(recues.size());
        ap.setDate(maintenant());
        apports.save(ap);
        Seance s = seances.findById(idDmc).orElseThrow();
        if (secours) {
            s.setSecoursEmploye(true);
            s.setSecoursMotif(motifSecours);
            tracer(idDmc, "SECOURS", "part de secours employée" + (parDepositaire ? " (apportée par le dépositaire)" : "") + " : " + motifSecours);
        } else {
            Set<String> presents = new LinkedHashSet<>(cnm.prs.entity.ChampFicheMarche.liste(s.getPresents()));
            presents.add(detenteur);
            s.setPresents(String.join(",", presents));
        }
        seances.save(s);
        tracer(idDmc, "APPORT", detenteur + " : " + recues.size() + " part(s)");
        Integer quorum = quorum(idDmc);
        if (quorum != null && memoire.de(idDmc).size() >= quorum) {
            dechiffrer(idDmc, quorum);
        }
        return dto(idDmc);
    }

    // ------------------------------------------------------------------ §B3 tout s'ouvre ensemble

    private void dechiffrer(Long idDmc, int quorum) {
        Map<String, Map<String, byte[]>> parts = memoire.de(idDmc);
        LocalDate jour = LocalDate.now(horloge);
        try {
            for (Offre o : deposees(idDmc)) {
                Optional<String> exclusion = exclusion(o, jour);
                if (exclusion.isPresent()) {
                    o.setEtat(Offre.ECARTEE);
                    o.setMotifEcartement(exclusion.get());
                    offres.save(o);
                    tracer(idDmc, "ECARTEMENT", "offre n° " + o.getNumero() + " : " + exclusion.get());
                    continue;
                }
                ouvrir(o, parts, quorum);
            }
        } finally {
            memoire.oublier(idDmc);
        }
        Seance s = seances.findById(idDmc).orElseThrow();
        s.setEtat(Seance.DECHIFFREE);
        s.setDechiffreeLe(maintenant());
        seances.save(s);
        tracer(idDmc, "DECHIFFREMENT", "quorum atteint (" + quorum + ") : offres ouvertes ensemble");
    }

    /** Ouvre une offre : intégrité, recombinaison, déchiffrement, ZIP, manifeste, pièces. Un échec la marque {@code LECTURE_IMPOSSIBLE}. */
    void ouvrir(Offre o, Map<String, Map<String, byte[]>> parts, int quorum) {
        String integrite;
        StockageOffres.Conteneur c;
        try {
            c = stockage.lire(o.getChemin(), o.getNombreMorceaux(), o.getTailleMorceau() + DechiffrementOffre.IV + DechiffrementOffre.ETIQUETTE);
            integrite = c.empreinte().equalsIgnoreCase(o.getEmpreinte()) ? INTACTE : ALTEREE;
        } catch (RuntimeException e) {
            marquer(o, LECTURE_IMPOSSIBLE, "conteneur illisible sur le disque");
            return;
        }
        List<byte[]> retenues = new ArrayList<>();
        for (Map<String, byte[]> p : parts.values()) {
            byte[] b = p.get(o.getIdOffre());
            if (b != null && retenues.size() < quorum) {
                retenues.add(b);
            }
        }
        byte[] k = null;
        try {
            if (retenues.size() < quorum) {
                marquer(o, LECTURE_IMPOSSIBLE, "parts insuffisantes pour cette offre");
                return;
            }
            k = DechiffrementOffre.recombiner(retenues);
            byte[] clair = DechiffrementOffre.dechiffrer(k, o.getIdOffre(), c.enTete(), c.morceaux());
            Map<String, byte[]> fichiers = dezipper(clair);
            byte[] manifeste = fichiers.get(MANIFESTE);
            if (manifeste == null) {
                marquer(o, LECTURE_IMPOSSIBLE, "l'archive ne porte pas de manifeste.json");
                return;
            }
            JsonNode m = mapper.readTree(manifeste);
            Map<String, Object> lecture = new LinkedHashMap<>();
            lecture.put("entreprise", mapper.convertValue(m.path("entreprise"), Object.class));
            lecture.put("groupement", m.path("groupement").isMissingNode() ? null : mapper.convertValue(m.path("groupement"), Object.class));
            lecture.put("acteEngagement", mapper.convertValue(m.path("acteEngagement"), Object.class));
            lecture.put("garantie", m.path("garantie").isMissingNode() ? null : mapper.convertValue(m.path("garantie"), Object.class));
            List<Map<String, Object>> pieces = new ArrayList<>();
            for (JsonNode p : m.path("pieces")) {
                String nom = p.path("nomFichier").asString(null);
                byte[] f = nom == null ? null : fichiers.get(nom);
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("code", p.path("code").asString(null));
                l.put("nomFichier", nom);
                l.put("presente", f != null);
                l.put("empreinteConforme", f != null && ClesRsa.sha256Hex(f).equalsIgnoreCase(p.path("sha256").asString("")));
                pieces.add(l);
            }
            lecture.put("pieces", pieces);
            // ⚠️ 2026-10-05 (lot 5, §B3) — le format 3 : les formulaires analysés (totaux recalculés, alertes) ; le détail ligne à
            // ligne reste dans le contenu déchiffré, jamais en base ni au journal. Une analyse impossible n'empêche pas la lecture.
            JsonNode f = m.path("formulaires");
            int nbAlertes = 0;
            if (f.isObject()) {
                Map<String, Object> lus = new LinkedHashMap<>();
                try {
                    FormulairesEnLigne.Analyse a = formulaires.analyser(o.getIdDmc(), o.getLot(), f, m.path("acteEngagement"),
                            maintenant().toLocalDate());
                    lus.put("parties", FormulairesEnLigne.parties(f));
                    lus.put("totaux", a.totaux());
                    lus.put("alertes", a.alertes());
                    nbAlertes = a.alertes().size();
                } catch (RuntimeException e) {
                    lus.put("totaux", Map.of());
                    lus.put("alertes", List.of(new SeanceDto.Alerte("FORMULAIRES_ILLISIBLES",
                            "Les formulaires de l'offre n'ont pas pu être analysés : la commission les lit dans le détail.")));
                    nbAlertes = 1;
                }
                lecture.put("formulaires", lus);
            }
            stockage.ecrireClair(o.getIdOffre(), clair);
            o.setIntegrite(integrite);
            o.setLecture(mapper.writeValueAsString(lecture));
            o.setMotifLecture(ALTEREE.equals(integrite) ? "l'empreinte du conteneur diffère de celle de l'accusé" : null);
            o.setOuverteLe(maintenant());
            offres.save(o);
            tracer(o.getIdDmc(), "OUVERTURE_OFFRE", "offre n° " + o.getNumero() + " : " + integrite + ", " + pieces.size() + " pièce(s)"
                    + (f.isObject() ? ", formulaires : " + nbAlertes + " alerte(s)" : ""));
        } catch (GeneralSecurityException e) {
            marquer(o, LECTURE_IMPOSSIBLE, ALTEREE.equals(integrite) ? "conteneur altéré : le déchiffrement est refusé"
                    : "le déchiffrement est refusé (clé ou morceau)");
        } catch (IOException | RuntimeException e) {
            marquer(o, LECTURE_IMPOSSIBLE, "contenu illisible : " + e.getClass().getSimpleName());
        } finally {
            if (k != null) {
                java.util.Arrays.fill(k, (byte) 0);
            }
        }
    }

    private void marquer(Offre o, String integrite, String motif) {
        o.setIntegrite(integrite);
        o.setMotifLecture(motif);
        o.setOuverteLe(maintenant());
        offres.save(o);
        tracer(o.getIdDmc(), "OUVERTURE_OFFRE", "offre n° " + o.getNumero() + " : " + integrite + " (" + motif + ")");
    }

    private static Map<String, byte[]> dezipper(byte[] zip) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    out.put(e.getName(), in.readAllBytes());
                }
            }
        }
        if (out.isEmpty()) {
            throw new IOException("archive vide ou illisible");
        }
        return out;
    }

    /** L'exclusion d'une entreprise (ou d'un membre de son groupement) prononcée après son dépôt, au répertoire du jour. */
    private Optional<String> exclusion(Offre o, LocalDate jour) {
        List<String> nifs = new ArrayList<>();
        nifs.add(o.getNif());
        if (o.getGroupementNifs() != null) {
            nifs.addAll(List.of(o.getGroupementNifs().split(",")));
        }
        for (String nif : nifs) {
            Optional<ExclusionArmp> e = exclusions.enCours(nif, jour);
            if (e.isPresent()) {
                return Optional.of("écartée : entreprise exclue par l'ARMP (NIF " + nif + ", décision " + e.get().getReferenceDecision()
                        + " du " + e.get().getDateDebut().format(JOUR) + ")");
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ §B4 la lecture et les pièces

    @Transactional(readOnly = true)
    public SeanceDto.Lecture lecture(Long idDmc) {
        exigerLecteur(idDmc, true);
        exigerDechiffree(idDmc);
        return construireLecture(idDmc, true);
    }

    /**
     * ⚠️ V76 (évaluation des offres, §B2) — la lecture complète (alertes comprises) pour l'évaluation, qui fait ses propres gardes
     * d'accès ; l'état de la séance (CLOSE) est vérifié par elle à l'ouverture.
     */
    @Transactional(readOnly = true)
    public SeanceDto.Lecture lecturePourEvaluation(Long idDmc) {
        return construireLecture(idDmc, true);
    }

    /** ⚠️ V76 — l'état de la séance, {@code A_VENIR} sans séance. */
    @Transactional(readOnly = true)
    public String etat(Long idDmc) {
        return seances.findById(idDmc).map(Seance::getEtat).orElse(Seance.A_VENIR);
    }

    private SeanceDto.Lecture construireLecture(Long idDmc, boolean complete) {
        List<OffreDto.PieceAttendue> attendues;
        try {
            attendues = procedures.piecesAttenduesInternes(idDmc);   // ⚠️ 2026-10-09 : sans la garde « invité », sans exception
        } catch (RuntimeException e) {
            attendues = List.of();
        }
        Map<String, String> libelles = new LinkedHashMap<>();
        attendues.forEach(a -> libelles.put(a.code(), a.libelle()));
        Map<String, String> valeurs = valeursFiche(idDmc);
        // ⚠️ 2026-10-05 — le minimum des travaux est B05-GQ-03 (B05-GS-03 aux fournitures) : il n'était pas lu.
        java.util.function.Function<Integer, BigDecimal> minimums = lot -> {
            for (String code : List.of(GARANTIE_MINIMUM, GARANTIE_MINIMUM_TRAVAUX)) {
                String x = lot != null && valeurs.containsKey(code + "#" + lot) ? valeurs.get(code + "#" + lot) : valeurs.get(code);
                if (x != null && !x.isBlank()) {
                    return montant(x);
                }
            }
            return null;
        };
        boolean retraitPayant = procedures.trouver(idDmc).map(x -> x.dto().retraitPayant()).orElse(false);
        int nbLots = fiches.etatValide(idDmc).map(v -> v.etat().getNbLots()).filter(java.util.Objects::nonNull).orElse(1);
        List<Offre> toutes = offresDuPli(idDmc);
        Map<String, Offre> parCandidat = new LinkedHashMap<>();
        toutes.stream().filter(o -> Offre.DEPOSEE.equals(o.getEtat())).forEach(o -> parCandidat.put(o.getIdCandidat(), o));
        List<SeanceDto.OffreLue> lues = new ArrayList<>();
        List<SeanceDto.NonOuverte> nonOuvertes = new ArrayList<>();
        for (Offre o : toutes) {
            if (Offre.EN_COURS.equals(o.getEtat())) {
                continue;
            }
            if (!Offre.DEPOSEE.equals(o.getEtat())) {
                nonOuvertes.add(new SeanceDto.NonOuverte(o.getNumero(), o.getRaisonSociale(), o.getEtat(),
                        Offre.ECARTEE.equals(o.getEtat()) ? o.getMotifEcartement() : Offre.RETIREE.equals(o.getEtat())
                                ? "retirée par le soumissionnaire" + (o.getDateRetrait() == null ? "" : " le " + o.getDateRetrait().format(HORODATAGE))
                                : remplacement(toutes, o.getRemplaceePar())));
                continue;
            }
            Map<String, Object> l = o.getLecture() == null ? Map.of() : mapper.readValue(o.getLecture(), new TypeReference<Map<String, Object>>() {
            });
            EntrepriseCandidatDto.Entreprise e = null;
            if (complete) {
                try {
                    e = entreprises.lire(o.getIdCandidat());
                } catch (ResourceNotFoundException ex) {
                    e = null;
                }
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pieces = (List<Map<String, Object>>) l.getOrDefault("pieces", List.of());
            List<SeanceDto.PieceLue> piecesLues = new ArrayList<>();
            Set<String> codes = new HashSet<>();
            for (Map<String, Object> p : pieces) {
                String code = (String) p.get("code");
                codes.add(code);
                piecesLues.add(new SeanceDto.PieceLue(code, libelles.getOrDefault(code, code), Boolean.TRUE.equals(p.get("presente")),
                        (String) p.get("nomFichier"), Boolean.TRUE.equals(p.get("empreinteConforme"))));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> garantie = (Map<String, Object>) l.get("garantie");
            if (garantie != null) {
                codes.add("GARANTIE");
            }
            List<String> manquantes = o.getLecture() == null ? List.of()
                    // ⚠️ H-PI-1 — une enveloppe PI ne doit que ses propres pièces.
                    : attendues.stream().filter(a -> a.obligatoire() && !codes.contains(a.code()))
                            .filter(a -> a.enveloppe() == null || o.getEnveloppe() == null || a.enveloppe().equals(o.getEnveloppe()))
                            .map(OffreDto.PieceAttendue::libelle).toList();
            List<SeanceDto.Alerte> alertes = new ArrayList<>();
            if (complete) {
                for (RapprochementCandidat r : rapprochements.de(o.getIdCandidat())) {
                    String autre = o.getIdCandidat().equals(r.getIdCandidatA()) ? r.getIdCandidatB() : r.getIdCandidatA();
                    Offre ao = parCandidat.get(autre);
                    if (ao != null) {
                        alertes.add(new SeanceDto.Alerte("RAPPROCHEMENT", "Même " + critere(r.getCritere()) + " que l'offre n° " + ao.getNumero()
                                + " (" + ao.getRaisonSociale() + ")."));
                    }
                }
                if (e != null && e.exclusion() != null) {
                    alertes.add(new SeanceDto.Alerte("EXCLUSION", "Exclusion de l'ARMP en cours : décision " + e.exclusion().referenceDecision() + "."));
                }
            }
            // ⚠️ Arbitrages du pilote (§B3, Q2) : le montant et l'émetteur de la garantie (manifeste v2), null en v1 ; une alerte, pas un refus.
            SeanceDto.Garantie lue = garantie == null ? null : new SeanceDto.Garantie((String) garantie.get("codeVerification"),
                    garantie.get("nomFichier") != null, montant(garantie.get("montant")), texte(garantie.get("monnaie")), texte(garantie.get("emetteur")));
            BigDecimal minimum = minimums.apply(o.getLot());
            if (minimum != null && lue != null && lue.montant() != null && lue.montant().compareTo(minimum) < 0) {
                alertes.add(new SeanceDto.Alerte("GARANTIE_INSUFFISANTE", "Garantie de " + montantLisible(lue.montant())
                        + " pour un minimum de " + montantLisible(minimum) + " fixé par la fiche" + (o.getLot() == null ? "" : " (lot " + o.getLot() + ")")
                        + "."));
            }
            // ⚠️ 2026-10-05 (lot 5, §B3) — les formulaires (format 3) : totaux recalculés, alertes de l'analyse à l'ouverture.
            @SuppressWarnings("unchecked")
            Map<String, Object> form = (Map<String, Object>) l.get("formulaires");
            Map<String, Object> totaux = null;
            if (form != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) form.get("totaux");
                totaux = t;
                if (complete && form.get("alertes") instanceof List<?> liste) {
                    for (Object x : liste) {
                        if (x instanceof Map<?, ?> a) {
                            alertes.add(new SeanceDto.Alerte(String.valueOf(a.get("type")), String.valueOf(a.get("message"))));
                        }
                    }
                }
            }
            // ⚠️ 2026-10-06 (retrait après paiement, §B5) — le reçu validé de l'entreprise pour le lot de l'offre ; sans lui (dossier
            // retiré sur papier, par exemple), une alerte — jamais un refus.
            SeanceDto.FraisDossier frais = null;
            if (retraitPayant) {
                Optional<cnm.prs.entity.RecuDao> recu = procedures.recuValide(idDmc, o.getNif(), o.getLot());
                frais = new SeanceDto.FraisDossier(recu.isPresent(), recu.map(cnm.prs.entity.RecuDao::getDateDecision).orElse(null),
                        recu.map(cnm.prs.entity.RecuDao::getReferencePaiement).orElse(null));
                if (complete && recu.isEmpty()) {
                    alertes.add(new SeanceDto.Alerte("FRAIS_NON_REGLES", "Aucun reçu de frais de dossier validé pour l'entreprise"
                            + (o.getLot() == null ? "" : " (lot " + o.getLot() + ")") + "."));
                }
            }
            // ⚠️ 2026-10-05 (§B5) — les documents remplis de l'offre ; une offre ouverte avant cette livraison les relit dans son clair.
            List<String> parties = null;
            if (form != null) {
                parties = form.get("parties") instanceof List<?> p ? p.stream().map(String::valueOf).toList() : partiesRelues(o);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> ae = (Map<String, Object>) l.get("acteEngagement");
            // ⚠️ 2026-10-07 (rabais structuré, §B1-§B2) — le rabais lu et ses contrôles, en alertes (le dépôt ne le voit pas, scellé).
            SeanceDto.RabaisLu rabais = RabaisOffre.lire(ae);
            if (complete) {
                alertes.addAll(RabaisOffre.controler(rabais, ae == null ? null : montant(ae.get("montantHt")), o.getLot(), nbLots));
            }
            // ⚠️ V86 (lot 3 PI, PI-b) — l'enveloppe technique ne lit aucun montant ; son pendant financier doit être déposé.
            if (Offre.TECHNIQUE.equals(o.getEnveloppe())) {
                if (ae != null && (ae.get("montantHt") != null || ae.get("montantTtc") != null)) {
                    alertes.add(new SeanceDto.Alerte("MONTANT_DANS_TECHNIQUE", "L'enveloppe technique porte un montant : il n'est pas lu ; "
                            + "la commission en tire la conséquence (art. 42)."));
                }
                if (financieresScellees(idDmc).stream().noneMatch(x -> Objects.equals(x.getIdEntreprise(), o.getIdEntreprise())
                        && Objects.equals(x.getLot(), o.getLot()))) {
                    alertes.add(new SeanceDto.Alerte("FINANCIERE_MANQUANTE", "Aucune enveloppe financière n'est déposée pour cette proposition."));
                }
                ae = null;
                rabais = null;
            }
            lues.add(new SeanceDto.OffreLue(o.getNumero(), o.getIdOffre(), o.getLot(), o.getEtat(), o.getIntegrite(), o.getMotifLecture(),
                    new SeanceDto.EntrepriseLue(o.getNif(), o.getRaisonSociale(), e == null ? null : e.verification(), e == null ? null : e.exclusion()),
                    l.get("groupement"), ae, lue, piecesLues, manquantes, alertes, form != null, totaux, parties, frais, rabais));
        }
        return new SeanceDto.Lecture(lues, nonOuvertes);
    }

    /** Le montant minimal de la garantie de soumission ({@code B05-GS-03}, par lot {@code B05-GS-03#n}). */
    static final String GARANTIE_MINIMUM = "B05-GS-03";
    /** ⚠️ 2026-10-05 — le même minimum aux travaux. */
    static final String GARANTIE_MINIMUM_TRAVAUX = "B05-GQ-03";

    private Map<String, String> valeursFiche(Long idDmc) {
        try {
            return fiches.etatValide(idDmc).map(e -> e.etat().getValeurs()).filter(Objects::nonNull).orElse(Map.of());
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /** Un montant du manifeste ou de la fiche (nombre, ou chaîne « 1 600 000 » / « 1600000,50 ») ; {@code null} s'il ne se lit pas. */
    static BigDecimal montant(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        String t = v.toString().replaceAll("[\\s\\u00a0\\u202f]", "").replace(',', '.');
        try {
            return t.isEmpty() ? null : new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String texte(Object v) {
        return v == null || v.toString().isBlank() ? null : v.toString().trim();
    }

    private static String montantLisible(BigDecimal m) {
        java.text.DecimalFormatSymbols sym = new java.text.DecimalFormatSymbols(java.util.Locale.FRANCE);
        sym.setGroupingSeparator(' ');
        return new java.text.DecimalFormat("#,##0.##", sym).format(m);
    }

    private static String totalLisible(Object v) {
        BigDecimal m = montant(v);
        return m == null ? "—" : montantLisible(m);
    }

    /** Les parties relues dans le contenu déchiffré ; {@code null} s'il est purgé ou illisible. */
    private List<String> partiesRelues(Offre o) {
        try {
            byte[] clair = stockage.lireClair(o.getIdOffre());
            byte[] manifeste = clair == null ? null : dezipper(clair).get(MANIFESTE);
            return manifeste == null ? null : FormulairesEnLigne.parties(mapper.readTree(manifeste).path("formulaires"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String critere(String c) {
        return switch (c == null ? "" : c) {
            case "TELEPHONE" -> "téléphone";
            case "SIGNATAIRE" -> "signataire";
            case "ADRESSE" -> "adresse";
            case "EMAIL" -> "adresse électronique";
            default -> c;
        };
    }

    /**
     * ⚠️ 2026-10-05 (lot 5, §B3.4) — le <strong>détail des formulaires</strong> d'une offre (la partie {@code formulaires} du manifeste,
     * relue dans le contenu déchiffré) avec le besoin de son lot : la commission seule (403 {@code PIECE_RESERVEE_CAO}) ; 404 offre
     * inconnue, purgée, ou sans formulaires ; 409 séance non déchiffrée.
     */
    @Transactional(readOnly = true)
    public SeanceDto.Formulaires formulairesOffre(Long idDmc, String idOffre) {
        Offre o = offreOuverte(idDmc, idOffre);
        JsonNode f = formulairesDe(o);
        return new SeanceDto.Formulaires(o.getIdOffre(), o.getNumero(), o.getLot(), mapper.convertValue(f, Object.class),
                formulaires.lot(idDmc, o.getLot()).orElse(null));
    }

    /** §B3.4 — le formulaire rempli en PDF, produit à la volée (Q2) : {@code BORDEREAU} (ou {@code DQE}), {@code CONFORMITE}, {@code CAPACITES}. */
    @Transactional(readOnly = true)
    public byte[] formulairePdf(Long idDmc, String idOffre, String type) {
        Offre o = offreOuverte(idDmc, idOffre);
        JsonNode f = formulairesDe(o);
        ProcedureEnLigneDto p = procedure(idDmc);
        String entete = (p == null ? "Procédure " + idDmc : p.reference() + " — " + p.objet()) + " ; offre n° " + o.getNumero() + " — "
                + o.getRaisonSociale() + (o.getLot() == null ? "" : ", lot " + o.getLot());
        return formulaires.pdf(type, formulaires.lot(idDmc, o.getLot()).orElse(null), f, entete, idDmc);
    }

    private Offre offreOuverte(Long idDmc, String idOffre) {
        exigerMembreCao(idDmc);
        exigerDechiffree(idDmc);
        return offres.findById(idOffre).filter(x -> idDmc.equals(x.getIdDmc()))
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + idOffre + "."));
    }

    /**
     * ⚠️ 2026-10-07 (évaluation des offres, §B3.1) — les corrections arithmétiques proposées d'une offre ouverte, depuis son bordereau
     * scellé ; vide pour une offre sans formulaires ou dont le contenu est purgé. Sans garde : l'évaluation fait les siennes.
     */
    @Transactional(readOnly = true)
    public List<FormulairesEnLigne.Correction> correctionsProposees(String idOffre) {
        Offre o = offres.findById(idOffre).orElse(null);
        byte[] clair = o == null ? null : stockage.lireClair(idOffre);
        if (clair == null) {
            return List.of();
        }
        try {
            byte[] manifeste = dezipper(clair).get(MANIFESTE);
            if (manifeste == null) {
                return List.of();
            }
            JsonNode m = mapper.readTree(manifeste);
            return formulaires.corrections(o.getIdDmc(), o.getLot(), m.path("formulaires"), m.path("acteEngagement"));
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /**
     * ⚠️ 2026-10-07 (évaluation des offres, lot 2, §B2.1) — le bordereau des prix (ou DQE) rempli d'une offre, en PDF, pour le dossier de
     * marché ; vide pour une offre sans formulaires ou purgée. Sans garde : l'attribution fait les siennes.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> bordereauPdf(String idOffre) {
        Offre o = offres.findById(idOffre).orElse(null);
        if (o == null) {
            return Optional.empty();
        }
        try {
            JsonNode f = formulairesDe(o);
            ProcedureEnLigneDto p = procedure(o.getIdDmc());
            String entete = (p == null ? "Procédure " + o.getIdDmc() : p.reference() + " — " + p.objet()) + " ; offre n° " + o.getNumero()
                    + " — " + o.getRaisonSociale() + (o.getLot() == null ? "" : ", lot " + o.getLot());
            return Optional.of(formulaires.pdf("BORDEREAU", formulaires.lot(o.getIdDmc(), o.getLot()).orElse(null), f, entete, o.getIdDmc()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** ⚠️ 2026-10-07 (lot 2, §B2.1) — le PV d'ouverture signé (version complète), nul sans PV signé. */
    @Transactional(readOnly = true)
    public byte[] pvSigne(Long idDmc) {
        // ⚠️ 2026-10-07 (constat D1 de la recette) — une séance close avant les signatures électroniques (V70) n'a pas de date de
        // signature : son PV, figé à la clôture, est le PV définitif.
        return seances.findById(idDmc).filter(s -> s.getPvSigneLe() != null || Seance.CLOSE.equals(s.getEtat())).map(Seance::getPv).orElse(null);
    }

    private JsonNode formulairesDe(Offre o) {
        byte[] clair = stockage.lireClair(o.getIdOffre());
        if (clair == null) {
            throw new ResourceNotFoundException(o.getPurgeeLe() != null ? "Le contenu de l'offre " + o.getIdOffre() + " a été purgé le "
                    + o.getPurgeeLe().format(JOUR) + ", au terme de sa conservation." : "L'offre " + o.getIdOffre() + " n'a pas été ouverte.");
        }
        try {
            byte[] manifeste = dezipper(clair).get(MANIFESTE);
            JsonNode f = manifeste == null ? null : mapper.readTree(manifeste).path("formulaires");
            if (f == null || !f.isObject()) {
                throw new ResourceNotFoundException("L'offre " + o.getIdOffre() + " ne porte pas de formulaires : elle a été déposée par pièces.");
            }
            return f;
        } catch (IOException e) {
            throw new ResourceNotFoundException("Le contenu de l'offre " + o.getIdOffre() + " ne se relit pas.");
        }
    }

    /** Une pièce d'une offre ouverte : ⚠️ les membres de la CAO seulement (403 {@code PIECE_RESERVEE_CAO}) ; 404 inconnue ; 409 séance non déchiffrée. */
    @Transactional(readOnly = true)
    public byte[] piece(Long idDmc, String idOffre, String nomFichier) {
        exigerMembreCao(idDmc);   // ⚠️ arbitrage du pilote (§B1) : les membres de la CAO seulement
        exigerDechiffree(idDmc);
        Offre o = offres.findById(idOffre).filter(x -> idDmc.equals(x.getIdDmc()))
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + idOffre + "."));
        byte[] clair = stockage.lireClair(o.getIdOffre());
        if (clair == null) {
            throw new ResourceNotFoundException(o.getPurgeeLe() != null ? "Le contenu de l'offre " + idOffre + " a été purgé le "
                    + o.getPurgeeLe().format(JOUR) + ", au terme de sa conservation." : "L'offre " + idOffre + " n'a pas été ouverte.");
        }
        try {
            byte[] f = dezipper(clair).get(nomFichier);
            if (f == null || MANIFESTE.equals(nomFichier)) {
                throw new ResourceNotFoundException("Pièce introuvable : " + nomFichier + ".");
            }
            return f;
        } catch (IOException e) {
            throw new ResourceNotFoundException("Le contenu de l'offre " + idOffre + " ne se relit pas.");
        }
    }

    // ------------------------------------------------------------------ §B5 le PV, §B6 S5

    /** Produit le PV d'ouverture (ou de carence) : responsable ; 409 {@code SEANCE_NON_DECHIFFREE}. */
    public SeanceDto produirePv(Long idDmc, SeanceDto.Observations obs) {
        exigerResponsable(idDmc);
        Seance s = seances.findById(idDmc).orElseThrow(() -> new BusinessRuleException("La séance n'est pas ouverte.", "SEANCE_NON_DECHIFFREE"));
        if (!Seance.DECHIFFREE.equals(s.getEtat())) {
            throw new BusinessRuleException("Le PV d'ouverture se produit une fois toutes les offres ouvertes.", "SEANCE_NON_DECHIFFREE");
        }
        s.setObservations(obs == null || obs.observations() == null || obs.observations().isBlank() ? null : obs.observations().trim());
        s.setEtat(Seance.PV_A_SIGNER);
        tracer(idDmc, "PV", (deposeesOuOuvertes(idDmc).isEmpty() ? "PV de carence" : "PV d'ouverture") + " produit");
        appelerSignatures(idDmc, s);
        return dto(idDmc);
    }

    /**
     * ⚠️ Arbitrages du pilote (§B2) : fige les signataires (les membres présents de la CAO), produit le PV à signer et les appelle à
     * signer ({@code PV_A_SIGNER}) ; sans membre présent, le PV est signé d'office (rien à attendre).
     */
    private void appelerSignatures(Long idDmc, Seance s) {
        List<String> signataires = dto(idDmc).membres().stream().filter(SeanceDto.Membre::present).map(SeanceDto.Membre::im).toList();
        s.setSignataires(signataires.isEmpty() ? null : String.join(",", signataires));
        if (signataires.isEmpty()) {
            finaliser(idDmc, s);
            return;
        }
        s.setPv(pdf(idDmc, s, true));
        s.setPvPublic(null);
        s.setPvPublie(false);
        seances.save(s);
        String quoi = libellePv(idDmc, s);
        for (String k : signataires) {
            internes.notifierMembre(idDmc, k, TypeNotification.PV_A_SIGNER, "Séance d'ouverture : signez le " + quoi,
                    "Le " + quoi + " de la procédure " + idDmc + " est produit : relisez-le et signez-le sur la plateforme.");
        }
    }

    /** Le PV entièrement signé : la séance se clôt, le PV se régénère avec toutes les signatures, se publie et se notifie. */
    private void finaliser(Long idDmc, Seance s) {
        LocalDateTime maintenant = maintenant();
        s.setPvSigneLe(maintenant);
        if (Seance.PV_A_SIGNER.equals(s.getEtat())) {
            s.setEtat(Seance.CLOSE);
            s.setCloseLe(maintenant);
        }
        boolean publie = publication(idDmc);
        s.setPv(pdf(idDmc, s, true));
        s.setPvPublic(publie ? pdf(idDmc, s, false) : null);
        s.setPvPublie(publie);
        seances.save(s);
        String quoi = libellePv(idDmc, s);
        tracer(idDmc, "PV_SIGNE", quoi + " signé" + (publie ? ", publié" : ""));
        notifierPv(idDmc, publie && !Seance.ILLISIBLE.equals(s.getEtat()), TypeNotification.PV_OUVERTURE,
                Character.toUpperCase(quoi.charAt(0)) + quoi.substring(1), "Le " + quoi + " de la procédure " + idDmc + " est signé.");
    }

    /**
     * ⚠️ 2026-10-07 (constat C3 de la recette) — une offre remplacée se désigne par le numéro de l'offre qui la remplace, jamais par
     * son identifiant technique : « remplacée par l'offre n° 4 ».
     */
    private static String remplacement(List<Offre> toutes, String idRemplacante) {
        Integer numero = toutes.stream().filter(x -> x.getIdOffre().equals(idRemplacante)).map(Offre::getNumero).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        return numero == null ? "remplacée par une offre ultérieure du même candidat" : "remplacée par l'offre n° " + numero;
    }

    private String libellePv(Long idDmc, Seance s) {
        return Seance.ILLISIBLE.equals(s.getEtat()) ? "PV de constat d'illisibilité"
                : deposeesOuOuvertes(idDmc).isEmpty() ? "PV de carence" : "PV d'ouverture des plis";
    }

    /** ⚠️ §B2 : la signature électronique simple du PV par un membre présent ; 403 {@code NON_PRESENT}, 409 {@code PV_NON_PRODUIT}, {@code DEJA_SIGNE}. */
    public SeanceDto signer(Long idDmc) {
        exigerDmc(idDmc);
        String k = membreCaoAppelant(idDmc);
        Seance s = pvASigner(idDmc);
        if (k == null || !signatairesDe(s).contains(k)) {
            throw new AccesReserveException("Le PV se signe par les membres de la commission présents à la séance.", "NON_PRESENT");
        }
        if (signatures.existsByIdDmcAndIm(idDmc, k)) {
            throw new BusinessRuleException("Vous avez déjà signé ce PV.", "DEJA_SIGNE");
        }
        signatures.save(new SeanceSignature(null, idDmc, k, maintenant(), false, null, null));
        tracer(idDmc, "SIGNATURE", internes.nomMembre(k) + " a signé le PV");
        apresSignature(idDmc, s);
        return dto(idDmc);
    }

    /**
     * ⚠️ §B2, Q1 : l'empêchement d'un membre présent, constaté par le président de la commission (ou le responsable de la procédure) ;
     * le motif est porté au PV. 400 {@code MOTIF_ABSENT}, {@code NON_SIGNATAIRE} ; 403 ; 409 {@code PV_NON_PRODUIT}, {@code DEJA_SIGNE}.
     */
    public SeanceDto empechement(Long idDmc, SeanceDto.Empechement e) {
        exigerDmc(idDmc);
        String k = membreCaoAppelant(idDmc);
        boolean president = k != null && caoMembres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream()
                .anyMatch(m -> k.equals(m.getIdCompte()) && Boolean.TRUE.equals(m.getPresident()));
        boolean responsable = !president && CurrentUser.profil().isPresent() && internes.estTitulaire(idDmc);
        if (!president && !responsable) {
            throw new AccessDeniedException("L'empêchement d'un membre se constate par le président de la commission.");
        }
        Seance s = pvASigner(idDmc);
        if (e == null || e.motif() == null || e.motif().isBlank()) {
            throw new BadRequestException("L'empêchement exige un motif, porté au PV.", "MOTIF_ABSENT");
        }
        if (e.im() == null || !signatairesDe(s).contains(e.im())) {
            throw new BadRequestException("« " + e.im() + " » n'est pas appelé à signer ce PV.", "NON_SIGNATAIRE");
        }
        if (signatures.existsByIdDmcAndIm(idDmc, e.im())) {
            throw new BusinessRuleException("Ce membre a déjà signé le PV (ou son empêchement est déjà constaté).", "DEJA_SIGNE");
        }
        String par = president ? internes.nomMembre(k) : internes.responsable(idDmc).map(r -> r.getNomResponsable()).orElse(null);
        String motif = e.motif().trim();
        signatures.save(new SeanceSignature(null, idDmc, e.im(), maintenant(), true, motif,
                par == null ? null : par.length() > 100 ? par.substring(0, 100) : par));
        tracer(idDmc, "EMPECHEMENT", internes.nomMembre(e.im()) + " empêché de signer : " + motif);
        apresSignature(idDmc, s);
        return dto(idDmc);
    }

    private void apresSignature(Long idDmc, Seance s) {
        Set<String> faites = new HashSet<>();
        signatures.findByIdDmcOrderByDateAscIdAsc(idDmc).forEach(x -> faites.add(x.getIm()));
        if (faites.containsAll(signatairesDe(s))) {
            finaliser(idDmc, s);
        } else {
            s.setPv(pdf(idDmc, s, true));
            seances.save(s);
        }
    }

    /** La séance dont le PV attend ses signatures ; 409 {@code PV_NON_PRODUIT} sinon (pas produit, ou déjà entièrement signé). */
    private Seance pvASigner(Long idDmc) {
        Seance s = seances.findById(idDmc).orElse(null);
        if (s == null || s.getPv() == null || s.getSignataires() == null) {
            throw new BusinessRuleException("Le PV de la séance n'est pas produit.", "PV_NON_PRODUIT");
        }
        if (s.getPvSigneLe() != null) {
            throw new BusinessRuleException("Le PV est déjà entièrement signé.", "DEJA_SIGNE");
        }
        return s;
    }

    private static List<String> signatairesDe(Seance s) {
        return cnm.prs.entity.ChampFicheMarche.liste(s.getSignataires());
    }

    /** S5 : constater l'illisibilité (responsable) ; 400 sans motif ; 409 tant que le quorum reste possible ({@code QUORUM_POSSIBLE}). */
    public SeanceDto constaterIllisible(Long idDmc, SeanceDto.Constat c) {
        exigerResponsable(idDmc);
        if (c == null || c.motif() == null || c.motif().isBlank()) {
            throw new BadRequestException("Le constat d'illisibilité exige un motif.", "MOTIF_ABSENT");
        }
        Seance s = exigerOuverte(idDmc);
        Integer quorum = quorum(idDmc);
        int possibles = detenteursPossibles(idDmc);
        if (quorum != null && possibles >= quorum) {
            throw new BusinessRuleException("Le quorum reste atteignable : " + possibles + " détenteur(s) peuvent encore apporter leurs parts "
                    + "(quorum " + quorum + ").", "QUORUM_POSSIBLE", null, Map.of("possibles", possibles, "quorum", quorum));
        }
        memoire.oublier(idDmc);
        s.setEtat(Seance.ILLISIBLE);
        s.setMotifIllisible(c.motif().trim());
        s.setCloseLe(maintenant());
        seances.save(s);
        tracer(idDmc, "CONSTAT", "offres illisibles : " + c.motif().trim());
        appelerSignatures(idDmc, s);   // ⚠️ §B2 : le PV de constat se signe comme le PV d'ouverture
        for (Offre o : deposees(idDmc)) {
            candidats.findById(o.getIdCandidat()).ifPresent(cc -> notifications.emettreCandidat(TypeNotification.OFFRES_ILLISIBLES,
                    o.getIdCandidat(), cc.getEmail(), idDmc.intValue(), TypeObjet.PROCEDURE, "Offres illisibles : procédure à relancer",
                    "La séance d'ouverture de la procédure " + idDmc + " a constaté que les offres ne peuvent pas être ouvertes (" + c.motif().trim()
                            + "). La procédure sera relancée."));
        }
        return dto(idDmc);
    }

    /** Le PDF du PV : responsable, membres, PRMP, UGPM ; 404 tant qu'il n'est pas produit. */
    @Transactional(readOnly = true)
    public byte[] pv(Long idDmc) {
        exigerLecteur(idDmc, true);
        return seances.findById(idDmc).map(Seance::getPv).filter(Objects::nonNull)
                .orElseThrow(() -> new ResourceNotFoundException("Le PV de la séance n'est pas encore produit."));
    }

    /** Le PV publié ({@code B04-OP-13 = OUI}) : public ; 404 sinon. */
    @Transactional(readOnly = true)
    public byte[] pvPublic(Long idDmc) {
        return seances.findById(idDmc).filter(s -> Boolean.TRUE.equals(s.getPvPublie())).map(Seance::getPvPublic).filter(Objects::nonNull)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun PV d'ouverture publié pour la procédure " + idDmc + "."));
    }

    private byte[] pdf(Long idDmc, Seance s, boolean complet) {
        List<DocumentLibre.Element> el = new ArrayList<>();
        ProcedureEnLigneDto p = procedure(idDmc);
        String titre = Seance.ILLISIBLE.equals(s.getEtat()) ? "PROCÈS-VERBAL DE CONSTAT D'ILLISIBILITÉ DES OFFRES"
                : deposeesOuOuvertes(idDmc).isEmpty() ? "PROCÈS-VERBAL DE CARENCE" : "PROCÈS-VERBAL D'OUVERTURE DES PLIS";
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, titre));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "(offres déposées en ligne)"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        if (p != null) {
            el.add(para("Procédure : " + p.reference() + " — " + p.objet()));
            if (p.autoriteContractante() != null) {
                el.add(para("Autorité contractante : " + p.autoriteContractante()));
            }
            el.add(para("Date limite de remise des offres : " + p.dateLimite()));
        }
        el.add(para("Séance ouverte le " + s.getOuverteLe().format(HORODATAGE) + " (heure du serveur)."));
        SeanceDto d = dto(idDmc);
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Présents"));
        el.add(para("Responsable de la procédure : " + internes.responsable(idDmc).map(r -> r.getNomResponsable()).orElse("—")));
        for (SeanceDto.Membre m : d.membres()) {
            el.add(para((m.president() ? "Président de la commission : " : "Membre de la commission : ") + m.nom()
                    + (m.present() ? "" : " (absent)") + (m.partsApportees() ? ", parts apportées" : "")));
        }
        for (SeanceDto.Autre a : d.autres()) {
            el.add(para(a.nom() + (a.qualite() == null ? "" : " — " + a.qualite())));
        }
        if (Boolean.TRUE.equals(s.getSecoursEmploye())) {
            el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Emploi de la part de secours"));
            el.add(para("La part de secours a été employée. Motif : " + s.getSecoursMotif()));
        }
        if (Seance.ILLISIBLE.equals(s.getEtat())) {
            el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Constat"));
            el.add(para("Le quorum de déchiffrement ne peut plus être atteint : les offres déposées ne peuvent pas être ouvertes. Motif : "
                    + s.getMotifIllisible() + ". La procédure est à relancer."));
            el.add(para("Offres déposées : " + deposees(idDmc).size() + "."));
        } else {
            SeanceDto.Lecture l = construireLecture(idDmc, complet);
            el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Offres reçues : " + (l.offres().size() + l.nonOuvertes().size())));
            if (l.offres().isEmpty() && l.nonOuvertes().isEmpty()) {
                el.add(para("Aucune offre n'a été déposée avant la date limite : la séance constate la carence."));
            }
            // ⚠️ V86 (lot 3 PI, PI-b) — une consultation restreinte : la séance n'ouvre que les enveloppes techniques.
            List<Offre> financieres = financieresScellees(idDmc);
            if (!financieres.isEmpty() || l.offres().stream().anyMatch(x -> estTechnique(x.idOffre()))) {
                el.add(para("Séance d'ouverture des propositions techniques : aucun montant n'est lu. Propositions financières : "
                        + financieres.size() + " enveloppe(s) déposée(s), restées scellées ; elles s'ouvriront en seconde séance, pour les seuls "
                        + "candidats qualifiés techniquement (art. 42 de la loi n° 2016-055)."));
            }
            for (SeanceDto.OffreLue o : l.offres()) {
                el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Offre n° " + o.numero() + " — " + o.entreprise().raisonSociale()
                        + " (NIF " + o.entreprise().nif() + ")" + (o.lot() == null ? "" : ", lot " + o.lot())));
                if (o.acteEngagement() != null) {
                    Map<String, Object> ae = o.acteEngagement();
                    el.add(para("Montant HT : " + ae.get("montantHt") + " ; montant TTC : " + ae.get("montantTtc") + " " + Objects.toString(ae.get("monnaie"), "MGA")));
                    el.add(para("Délai : " + ae.get("delai") + " " + Objects.toString(ae.get("delaiUnite"), "") + " ; validité : " + ae.get("validiteJours")
                            + " jours ; rabais : " + (o.rabais() == null ? "aucun" : o.rabais().lecture())));   // ⚠️ 2026-10-07 : rabais structuré, chiffré
                }
                if (o.formulaires() && o.totaux() != null && !o.totaux().isEmpty()) {   // ⚠️ 2026-10-05 (lot 5, §B3.5)
                    Map<String, Object> t = o.totaux();
                    el.add(para("Bordereau (totaux recalculés) : HT " + totalLisible(t.get("ht")) + " ; TVA " + totalLisible(t.get("tva"))
                            + " ; TTC " + totalLisible(t.get("ttc")) + " MGA" + (t.containsKey("htMin") ? " — au maximum des quantités ; au minimum : HT "
                                    + totalLisible(t.get("htMin")) + ", TTC " + totalLisible(t.get("ttcMin")) + " MGA" : "") + "."));
                }
                SeanceDto.Garantie g = o.garantie();
                el.add(para("Garantie : " + (g == null ? "non fournie" : "fournie, code de vérification " + g.codeVerification()
                        + (g.montant() == null ? "" : " ; montant : " + montantLisible(g.montant()) + " " + Objects.toString(g.monnaie(), "MGA"))
                        + (g.emetteur() == null ? "" : " ; émetteur : " + g.emetteur()))));
                el.add(para("Intégrité : " + o.integrite() + (o.motif() == null ? "" : " (" + o.motif() + ")")));
                el.add(para("Pièces manquantes : " + (o.piecesManquantes().isEmpty() ? "aucune" : String.join(", ", o.piecesManquantes()))));
                if (complet) {
                    if (o.entreprise().verification() != null) {
                        el.add(para("Vérification du NIF : " + o.entreprise().verification().statut()));
                    }
                    for (SeanceDto.Alerte a : o.alertes()) {
                        el.add(para("Alerte (" + a.type() + ") : " + a.message()));
                    }
                }
            }
            if (!l.nonOuvertes().isEmpty()) {
                el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Offres non ouvertes"));
                for (SeanceDto.NonOuverte n : l.nonOuvertes()) {
                    el.add(para("Offre n° " + n.numero() + " — " + n.entreprise() + " : " + n.motif()));
                }
            }
        }
        if (s.getObservations() != null) {
            el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Observations"));
            el.add(para(s.getObservations()));
        }
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Signatures des membres présents"));
        // ⚠️ Arbitrages du pilote (§B2) : la signature électronique simple de chaque membre présent, ou son empêchement constaté.
        Map<String, SeanceSignature> signees = new LinkedHashMap<>();
        signatures.findByIdDmcOrderByDateAscIdAsc(idDmc).forEach(x -> signees.put(x.getIm(), x));
        for (SeanceDto.Membre m : d.membres()) {
            boolean appele = s.getSignataires() == null ? m.present() : signatairesDe(s).contains(m.im());
            if (!appele) {
                continue;
            }
            SeanceSignature x = signees.get(m.im());
            String qui = m.nom() + " (" + (m.president() ? "Président" : "Membre") + " de la commission)";
            el.add(para(x == null ? qui + " — signature attendue"
                    : Boolean.TRUE.equals(x.getEmpechement()) ? qui + " — empêché de signer : " + x.getMotif()
                            + (x.getConstatePar() == null ? "" : " (constaté par " + x.getConstatePar() + " le " + x.getDate().format(HORODATAGE) + ")")
                    : qui + " — signé électroniquement sur la plateforme le " + x.getDate().format(HORODATAGE)));
        }
        return generateur.generer(new DocumentLibre("PV_OUVERTURE", null, el, "Procédure " + idDmc)).stream()
                .filter(f -> "pdf".equals(f.extension())).findFirst().orElseThrow().contenu();
    }

    private static DocumentLibre.Paragraphe para(String t) {
        return new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t);
    }

    private void notifierPv(Long idDmc, boolean publie, TypeNotification type, String titre, String corps) {
        ceremonieService.notifierPrmp(idDmc, type, titre, corps);
        for (String k : internes.membresCao(idDmc)) {
            internes.notifierMembre(idDmc, k, type, titre, corps);
        }
        if (publie) {
            for (Offre o : deposeesOuOuvertes(idDmc)) {
                candidats.findById(o.getIdCandidat()).ifPresent(cc -> notifications.emettreCandidat(type, o.getIdCandidat(), cc.getEmail(),
                        idDmc.intValue(), TypeObjet.PROCEDURE, titre, corps + " Il est publié sur la procédure."));
            }
        }
    }

    // ------------------------------------------------------------------ §B7 les rappels

    /** SEANCE_A_VENIR la veille et une heure avant, aux membres et au responsable, une fois chacun. Rend le nombre de rappels. */
    public int rappeler() {
        LocalDateTime maintenant = maintenant();
        int n = 0;
        for (CeremonieCles c : ceremonies.findByEtat(CeremonieCles.CLOSE)) {
            Long idDmc = c.getIdDmc();
            if (seances.existsById(idDmc)) {
                continue;
            }
            LocalDateTime heure = heureOuverture(idDmc);
            if (heure == null || !maintenant.isBefore(heure)) {
                continue;
            }
            boolean veille = c.getRappelVeille() == null && !maintenant.isBefore(heure.minusHours(24));
            boolean uneHeure = c.getRappelHeure() == null && !maintenant.isBefore(heure.minusHours(1));
            if (!veille && !uneHeure) {
                continue;
            }
            String corps = "La séance d'ouverture des plis de la procédure " + idDmc + " a lieu le " + heure.format(HORODATAGE)
                    + " (heure du serveur). Munissez-vous de votre phrase secrète.";
            for (String k : internes.membresCao(idDmc)) {
                internes.notifierMembre(idDmc, k, TypeNotification.SEANCE_A_VENIR, "Séance d'ouverture des plis", corps);
            }
            ceremonieService.notifierResponsable(idDmc, TypeNotification.SEANCE_A_VENIR, "Séance d'ouverture des plis", corps);
            if (uneHeure) {
                c.setRappelHeure(maintenant);
            }
            if (veille || uneHeure) {
                c.setRappelVeille(c.getRappelVeille() == null ? maintenant : c.getRappelVeille());
            }
            ceremonies.save(c);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ outils

    private SeanceDto dto(Long idDmc) {
        Seance s = seances.findById(idDmc).orElse(null);
        LocalDateTime heure = heureOuverture(idDmc);
        LocalDateTime maintenant = maintenant();
        Map<String, Map<String, byte[]>> parts = memoire.de(idDmc);
        Set<String> presents = new LinkedHashSet<>(cnm.prs.entity.ChampFicheMarche.liste(s == null ? null : s.getPresents()));
        Map<String, CaoMembre> caoParCompte = new LinkedHashMap<>();
        caoMembres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).forEach(m -> {
            if (m.getIdCompte() != null) {
                caoParCompte.put(m.getIdCompte(), m);
            }
        });
        List<SeanceDto.Membre> membres = new ArrayList<>();
        for (String k : internes.membresCao(idDmc)) {
            CaoMembre m = caoParCompte.get(k);
            membres.add(new SeanceDto.Membre(k, internes.nomMembre(k), m != null && Boolean.TRUE.equals(m.getPresident()),
                    presents.contains(k) || parts.containsKey(k), parts.containsKey(k)));
        }
        List<SeanceDto.Autre> autres = new ArrayList<>();
        if (s != null && s.getAutres() != null) {
            autres.addAll(mapper.readValue(s.getAutres(), new TypeReference<List<SeanceDto.Autre>>() {
            }));
        }
        List<SeanceDto.OffreSeance> os = new ArrayList<>();
        for (Offre o : offresDuPli(idDmc)) {
            if (Offre.EN_COURS.equals(o.getEtat())) {
                continue;
            }
            int recues = (int) parts.values().stream().filter(p -> p.containsKey(o.getIdOffre())).count();
            os.add(new SeanceDto.OffreSeance(o.getNumero(), o.getLot(), o.getIntegrite() != null && Offre.DEPOSEE.equals(o.getEtat())
                    ? o.getIntegrite() : o.getEtat(), recues));
        }
        Long dans = heure == null || !maintenant.isBefore(heure) ? null : Duration.between(maintenant, heure).getSeconds();
        return new SeanceDto(idDmc, s == null ? Seance.A_VENIR : s.getEtat(), heure, s == null ? null : s.getOuverteLe(), dans, quorum(idDmc),
                membres, autres, s != null && Boolean.TRUE.equals(s.getSecoursEmploye()), os, s == null ? null : s.getDechiffreeLe(),
                blocPv(idDmc, s, membres), s == null || s.getSecoursDemandeLe() == null ? null
                        : new SeanceDto.SecoursDemande(s.getSecoursDemandeMotif(), s.getSecoursDemandeLe()), secoursGenerePar(idDmc));
    }

    /** ⚠️ §B2 : le PV, ses signatures posées et celles qui restent attendues. */
    private SeanceDto.Pv blocPv(Long idDmc, Seance s, List<SeanceDto.Membre> membres) {
        if (s == null) {
            return new SeanceDto.Pv(false, false, false, List.of(), List.of());
        }
        Map<String, SeanceDto.Membre> parIm = new LinkedHashMap<>();
        membres.forEach(m -> parIm.put(m.im(), m));
        List<SeanceDto.Signature> faites = new ArrayList<>();
        Set<String> deja = new HashSet<>();
        for (SeanceSignature x : signatures.findByIdDmcOrderByDateAscIdAsc(idDmc)) {
            SeanceDto.Membre m = parIm.get(x.getIm());
            deja.add(x.getIm());
            faites.add(new SeanceDto.Signature(x.getIm(), m == null ? internes.nomMembre(x.getIm()) : m.nom(), m != null && m.president(),
                    x.getDate(), Boolean.TRUE.equals(x.getEmpechement()), x.getMotif(), x.getConstatePar()));
        }
        List<SeanceDto.Attendue> attendues = s.getPvSigneLe() != null ? List.of() : signatairesDe(s).stream().filter(k -> !deja.contains(k))
                .map(k -> new SeanceDto.Attendue(k, parIm.containsKey(k) ? parIm.get(k).nom() : internes.nomMembre(k))).toList();
        return new SeanceDto.Pv(s.getPv() != null, Boolean.TRUE.equals(s.getPvPublie()), s.getPvSigneLe() != null, faites, attendues);
    }

    /** Les parts chiffrées d'un détenteur, retrouvées dans l'en-tête de chaque offre déposée par l'empreinte de l'une de ses clés. */
    private List<SeanceDto.PartChiffree> partsDe(Long idDmc, String detenteur) {
        return partsPour(idDmc, detenteur, deposees(idDmc));
    }

    /** ⚠️ V88 (lot 3 PI, PI-d1) — les parts chiffrées d'un détenteur pour les offres données (la seconde séance : les financières). */
    List<SeanceDto.PartChiffree> partsPour(Long idDmc, String detenteur, List<Offre> aOuvrir) {
        boolean secours = SeanceApport.SECOURS.equals(detenteur);
        List<CleDetenteur> siennes = cles.findByIdDmcOrderByIdCleAsc(idDmc).stream()
                .filter(c -> secours ? CleDetenteur.SECOURS.equals(c.getRole()) : CleDetenteur.MEMBRE.equals(c.getRole()) && detenteur.equals(c.getIm()))
                .toList();
        List<SeanceDto.PartChiffree> out = new ArrayList<>();
        for (Offre o : aOuvrir) {
            JsonNode t = mapper.readTree(o.getEnTete());
            for (JsonNode p : t.path("parts")) {
                String empreinte = p.path("empreinte").asString("");
                Optional<CleDetenteur> cle = siennes.stream().filter(c -> c.getEmpreinte().equalsIgnoreCase(empreinte)).findFirst();
                if (cle.isPresent()) {
                    CleDetenteur c = cle.get();
                    out.add(new SeanceDto.PartChiffree(o.getIdOffre(), empreinte, p.path("part").asString(), c.getDateArchivage() == null ? null
                            : new CeremonieDto.Enveloppe(c.getEnvChiffre(), c.getEnvIv(), c.getEnvSel(), c.getEnvIterations(), c.getEnvKdf(),
                                    c.getEnvAlgorithme())));
                    break;
                }
            }
        }
        return out;
    }

    /** Les détenteurs qui peuvent encore apporter leurs parts : ceux qui l'ont fait, plus ceux dont la clé active n'est pas perdue. */
    private int detenteursPossibles(Long idDmc) {
        Set<String> deja = memoire.de(idDmc).keySet();
        int n = deja.size();
        for (CleDetenteur c : cles.findByIdDmcAndDateArchivageIsNullOrderByIdCleAsc(idDmc)) {
            String d = CleDetenteur.SECOURS.equals(c.getRole()) ? SeanceApport.SECOURS : c.getIm();
            if (!deja.contains(d) && !CleDetenteur.PERDUE.equals(c.getEtatPart())) {
                n++;
            }
        }
        return n;
    }

    /**
     * ⚠️ V86 (lot 3 PI, PI-b) — les offres que la séance connaît : pour une consultation restreinte, les seules enveloppes
     * <strong>techniques</strong> ; les financières restent scellées jusqu'à la seconde séance (tranche PI-d), jamais lues ici.
     */
    private List<Offre> offresDuPli(Long idDmc) {
        return offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc).stream().filter(o -> !Offre.FINANCIERE.equals(o.getEnveloppe())).toList();
    }

    /** ⚠️ V86 (PI-b) — les enveloppes financières déposées de la procédure (scellées), pour le PV. */
    private boolean estTechnique(String idOffre) {
        return offres.findById(idOffre).map(o -> Offre.TECHNIQUE.equals(o.getEnveloppe())).orElse(false);
    }

    private List<Offre> financieresScellees(Long idDmc) {
        return offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc).stream()
                .filter(o -> Offre.FINANCIERE.equals(o.getEnveloppe()) && Offre.DEPOSEE.equals(o.getEtat())).toList();
    }

    private List<Offre> deposees(Long idDmc) {
        return offresDuPli(idDmc).stream().filter(o -> Offre.DEPOSEE.equals(o.getEtat())).toList();
    }

    private List<Offre> deposeesOuOuvertes(Long idDmc) {
        return offresDuPli(idDmc).stream()
                .filter(o -> !Offre.EN_COURS.equals(o.getEtat())).toList();
    }

    Integer quorum(Long idDmc) {
        RemiseElectronique.Internes i = internes.internes(idDmc);
        return i == null ? null : i.quorum();
    }

    private LocalDateTime heureOuverture(Long idDmc) {
        try {
            return fiches.etatValide(idDmc).map(e -> {
                Map<String, String> v = e.etat().getValeurs();
                return v == null ? null : RemiseElectronique.echeance(v.get(RemiseElectronique.DATE_OUVERTURE_PLIS),
                        v.get(RemiseElectronique.HEURE_OUVERTURE_PLIS));
            }).orElse(null);
        } catch (ResourceNotFoundException | BusinessRuleException e) {
            return null;
        }
    }

    private LocalDateTime dateLimite(Long idDmc) {
        try {
            return fiches.etatValide(idDmc).map(ProceduresEnLigneService::dateLimite).orElse(null);
        } catch (ResourceNotFoundException | BusinessRuleException e) {
            return null;
        }
    }

    private boolean publication(Long idDmc) {
        try {
            return fiches.etatValide(idDmc).map(e -> e.etat().getValeurs() == null ? null : e.etat().getValeurs().get("B04-OP-13"))
                    .map("OUI"::equalsIgnoreCase).orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ProcedureEnLigneDto procedure(Long idDmc) {
        try {
            return procedures.trouver(idDmc).map(ProceduresEnLigneService.Lue::dto).orElseGet(() -> procedures.vue(idDmc));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Seance exigerOuverte(Long idDmc) {
        Seance s = seances.findById(idDmc).orElse(null);
        if (s == null || !Seance.OUVERTE.equals(s.getEtat())) {
            throw new BusinessRuleException("La séance n'est pas ouverte : les parts ne se servent ni ne s'apportent.", "SEANCE_NON_OUVERTE");
        }
        return s;
    }

    private Seance seanceModifiable(Long idDmc) {
        Seance s = seances.findById(idDmc).orElseThrow(() -> new BusinessRuleException("La séance n'est pas ouverte.", "SEANCE_NON_OUVERTE"));
        if (Seance.CLOSE.equals(s.getEtat()) || Seance.ILLISIBLE.equals(s.getEtat()) || Seance.PV_A_SIGNER.equals(s.getEtat())) {
            throw new BusinessRuleException("La séance est close.", "SEANCE_CLOSE");
        }
        return s;
    }

    private void exigerDechiffree(Long idDmc) {
        String etat = seances.findById(idDmc).map(Seance::getEtat).orElse(Seance.A_VENIR);
        if (!Seance.DECHIFFREE.equals(etat) && !Seance.PV_A_SIGNER.equals(etat) && !Seance.CLOSE.equals(etat)) {
            throw new BusinessRuleException("Les offres ne sont pas encore ouvertes.", "SEANCE_NON_DECHIFFREE");
        }
    }

    /** Le détenteur de l'appelant : son identifiant {@code K…} s'il est membre de la CAO, {@code SECOURS} pour le responsable avec {@code role=SECOURS}. */
    String detenteur(Long idDmc, String role) {
        exigerDmc(idDmc);
        if (SeanceApport.SECOURS.equalsIgnoreCase(role)) {
            // ⚠️ V71 (§B3, §B4) — la part de secours s'apporte par son détenteur : le dépositaire (nouveau geste), ou le
            // responsable pour une clé générée chez lui (ancien geste).
            boolean ancienGeste = CleDetenteur.PAR_RESPONSABLE.equals(secoursGenerePar(idDmc));
            if (ancienGeste ? !internes.estTitulaire(idDmc) : !internes.estDepositaire(idDmc)) {
                throw new AccessDeniedException(ancienGeste ? "Cette part de secours a été générée chez le responsable de la procédure : "
                        + "elle s'apporte par lui." : "La part de secours s'apporte par son dépositaire, depuis son espace.");
            }
            return SeanceApport.SECOURS;
        }
        String ref = CurrentUser.ref().orElse(null);
        if (ref == null || !TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null))
                || !internes.membresCao(idDmc).contains(ref)) {
            throw new AccessDeniedException("Seuls les membres de la commission d'appel d'offres apportent des parts.");
        }
        return ref;
    }

    /** ⚠️ Arbitrages du pilote (§B1) : un membre de la CAO de la procédure ; 403 {@code PIECE_RESERVEE_CAO} pour tout autre. */
    private void exigerMembreCao(Long idDmc) {
        exigerDmc(idDmc);
        if (membreCaoAppelant(idDmc) == null) {
            throw new AccesReserveException("Les pièces des offres se lisent par les membres de la commission d'appel d'offres seulement.",
                    "PIECE_RESERVEE_CAO");
        }
    }

    /** L'identifiant {@code K…} de l'appelant s'il est membre de la CAO de la procédure, {@code null} sinon. */
    private String membreCaoAppelant(Long idDmc) {
        String ref = CurrentUser.ref().orElse(null);
        return ref != null && TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null))
                && internes.membresCao(idDmc).contains(ref) ? ref : null;
    }

    void exigerResponsable(Long idDmc) {
        exigerDmc(idDmc);
        if (CurrentUser.profil().isEmpty() || !internes.estTitulaire(idDmc)) {
            throw new AccessDeniedException("La séance d'ouverture se conduit par le responsable de la procédure.");
        }
    }

    /** Responsable, membres de la CAO, PRMP de la fiche, et l'UGPM si {@code ugpm}. */
    void exigerLecteur(Long idDmc, boolean ugpm) {
        exigerDmc(idDmc);
        if (internes.estTitulaire(idDmc)) {
            return;
        }
        String ref = CurrentUser.ref().orElse(null);
        if (ref != null && TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null)) && internes.membresCao(idDmc).contains(ref)) {
            return;
        }
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == ProfilUtilisateur.PRMP || ugpm && p == ProfilUtilisateur.UGPM) {
            fiches.controlerLecture(idDmc);
            return;
        }
        throw new AccessDeniedException("La séance se lit par le responsable de la procédure, les membres de la commission et la PRMP de la fiche.");
    }

    private void exigerDmc(Long idDmc) {
        if (idDmc == null || !dmcRepository.existsById(idDmc)) {
            throw new ResourceNotFoundException("DMC introuvable : " + idDmc);
        }
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new SeanceJournal(null, idDmc, maintenant(), CurrentUser.ref().or(CurrentUser::login).orElse(null), action, detail));
    }

}
