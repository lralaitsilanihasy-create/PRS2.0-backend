package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CeremonieDto;
import cnm.prs.dto.OffreDto;
import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.ExclusionArmp;
import cnm.prs.entity.Offre;
import cnm.prs.entity.OffreJournal;
import cnm.prs.entity.OffreMorceau;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.OffreJournalRepository;
import cnm.prs.repository.OffreMorceauRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>Le dépôt scellé d'une offre</strong> (demande front du 2026-10-04, soumission en ligne, lot 3 ; ADR-0013 §1, §4, §7 ;
 * V68).
 * <ul>
 *   <li>Le candidat <strong>scelle son offre dans son navigateur</strong> ; le serveur reçoit un conteneur qu'il ne peut pas lire
 *       (en-tête JSON en clair, morceaux AES-256-GCM), l'horodate à son horloge, en recalcule l'empreinte et rend un accusé. Il sait
 *       qui a déposé, quand, pour quel lot, combien d'octets, et pour quelles clés — <strong>jamais le contenu</strong>, ni un prix.</li>
 *   <li>Les conditions (§B1), dans l'ordre : procédure en ligne ({@code PROCEDURE_FERMEE}), dépôts ouverts ({@code PROCEDURE_FERMEE}
 *       / {@code DELAI_DEPASSE}), cérémonie close et clés de l'en-tête identiques aux clés publiées ({@code CLES_INDISPONIBLES}),
 *       entreprise déclarée ({@code ENTREPRISE_ABSENTE}), pas d'exclusion de l'ARMP ({@code ENTREPRISE_EXCLUE}), remplacement permis
 *       ({@code REMPLACEMENT_INTERDIT}). La date limite et l'exclusion sont <strong>revérifiées au scellement</strong>.</li>
 *   <li>Une offre déposée par lot et par entreprise ({@code OFFRE_EXISTANTE}, sauf {@code remplace}) ; l'ancienne ne passe
 *       {@code REMPLACEE} qu'au scellement de la nouvelle. La première offre scellée pose {@code premierDepot} (cérémonie).</li>
 *   <li>Un dépôt {@code EN_COURS} sans morceau depuis 24 h, ou dont la date limite est passée, est purgé (fichiers compris) ; la
 *       clôture des dépôts est notifiée une fois ({@code DEPOTS_CLOS}). Le journal ({@code t_offre_journal}) survit à la purge.</li>
 * </ul>
 */
@Service
@Transactional
public class OffreService {

    static final int TAILLE_MORCEAU = 4 * 1024 * 1024;
    /** iv (12 octets) + étiquette (16 octets) d'un morceau AES-256-GCM. */
    static final int SURCOUT_MORCEAU = 28;
    static final int HEURES_ABANDON = 24;
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern HEX64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm:ss");

    private final OffreRepository offres;
    private final OffreMorceauRepository morceaux;
    private final OffreJournalRepository journal;
    private final StockageOffres stockage;
    private final ProceduresEnLigneService procedures;
    private final CeremonieService ceremonies;
    private final CeremonieClesRepository ceremonieRepository;
    private final ParametresInternesService internes;
    private final FicheMarcheService fiches;
    private final EntrepriseRepository entreprises;
    private final CompteCandidatRepository candidats;
    private final ExclusionArmpService exclusions;
    private final NotificationService notifications;
    private final GenerateurDocumentsFiche generateur;
    private final ObjectMapper mapper;
    private final Clock horloge;

    public OffreService(OffreRepository offres, OffreMorceauRepository morceaux, OffreJournalRepository journal, StockageOffres stockage,
            ProceduresEnLigneService procedures, CeremonieService ceremonies, CeremonieClesRepository ceremonieRepository,
            ParametresInternesService internes, FicheMarcheService fiches, EntrepriseRepository entreprises,
            CompteCandidatRepository candidats, ExclusionArmpService exclusions, NotificationService notifications,
            GenerateurDocumentsFiche generateur, ObjectMapper mapper, Clock horloge) {
        this.offres = offres;
        this.morceaux = morceaux;
        this.journal = journal;
        this.stockage = stockage;
        this.procedures = procedures;
        this.ceremonies = ceremonies;
        this.ceremonieRepository = ceremonieRepository;
        this.internes = internes;
        this.fiches = fiches;
        this.entreprises = entreprises;
        this.candidats = candidats;
        this.exclusions = exclusions;
        this.notifications = notifications;
        this.generateur = generateur;
        this.mapper = mapper;
        this.horloge = horloge;
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge);
    }

    // ------------------------------------------------------------------ §B4 créer

    /** {@code POST /api/candidat/offres} : 201 {@code EN_COURS} ; 400 en-tête mal formé ; 409 à code (§B1, {@code OFFRE_EXISTANTE}). */
    public OffreDto creer(String idCandidat, OffreDto.Creation c) {
        if (c == null || c.idDmc() == null || c.enTete() == null || c.enTete().isBlank()) {
            throw new BadRequestException("La procédure et l'en-tête du conteneur sont attendus.", "EN_TETE_INVALIDE");
        }
        Long idDmc = c.idDmc();
        ProceduresEnLigneService.Lue lue = procedureOuverte(idDmc);
        List<String> attendues = clesPubliees(idDmc);
        Entreprise entreprise = entreprises.findByIdCandidat(idCandidat).orElseThrow(() -> new BusinessRuleException(
                "Déclarez votre entreprise avant de déposer une offre.", "ENTREPRISE_ABSENTE"));
        List<String> groupement = nifsGroupement(c.groupementNifs());
        controlerExclusions(entreprise, groupement);
        Integer lot = controlerLot(lue.dto(), c.lot());
        Offre remplacee = null;
        if (c.remplace() != null && !c.remplace().isBlank()) {
            if (!ProceduresEnLigneService.remplacementAutorise(lue.etat())) {
                throw new BusinessRuleException("La fiche n'autorise pas le remplacement d'une offre déposée.", "REMPLACEMENT_INTERDIT");
            }
            remplacee = offres.findById(c.remplace().trim()).filter(o -> idCandidat.equals(o.getIdCandidat())
                    && idDmc.equals(o.getIdDmc()) && Objects.equals(lot, o.getLot()) && Offre.DEPOSEE.equals(o.getEtat()))
                    .orElseThrow(() -> new BadRequestException("L'offre à remplacer n'est pas l'une de vos offres déposées pour cette "
                            + "procédure et ce lot.", "REMPLACE_INVALIDE"));
        } else {
            deposee(idDmc, entreprise.getIdEntreprise(), lot).ifPresent(o -> {
                throw new BusinessRuleException("Vous avez déjà déposé une offre pour " + (lot == null ? "cette procédure" : "le lot " + lot)
                        + " : remplacez-la" + " si la fiche le permet.", "OFFRE_EXISTANTE");
            });
        }
        EnTete t = lireEnTete(c.enTete(), idDmc, lot, attendues);
        if (offres.existsById(t.idOffre())) {
            throw new BusinessRuleException("Cet identifiant d'offre est déjà utilisé : tirez-en un nouveau.", "OFFRE_EXISTANTE");
        }
        Long max = ProceduresEnLigneService.tailleMaxOffreOctets(lue.etat());
        long prevue = t.tailleContenu() + (long) SURCOUT_MORCEAU * t.nombreMorceaux() + c.enTete().length();
        if (max != null && prevue > max) {
            throw new BusinessRuleException("L'offre dépasse la taille maximale fixée par la fiche (" + max / (1024 * 1024) + " Mo).",
                    "TAILLE_DEPASSEE");
        }
        // Un dépôt en cours de la même entreprise pour le même lot est abandonné au profit de celui-ci.
        for (Offre o : offres.findByIdCandidatOrderByDateCreationDesc(idCandidat)) {
            if (Offre.EN_COURS.equals(o.getEtat()) && idDmc.equals(o.getIdDmc()) && Objects.equals(lot, o.getLot())) {
                purger(o, "abandonné au profit de " + t.idOffre());
            }
        }
        Offre o = new Offre();
        o.setIdOffre(t.idOffre());
        o.setIdDmc(idDmc);
        o.setIdCandidat(idCandidat);
        o.setIdEntreprise(entreprise.getIdEntreprise());
        o.setNif(entreprise.getNif());
        o.setRaisonSociale(entreprise.getRaisonSociale());
        o.setLot(lot);
        o.setEtat(Offre.EN_COURS);
        o.setDateCreation(maintenant());
        o.setEnTete(c.enTete());
        o.setNombreMorceaux(t.nombreMorceaux());
        o.setTailleMorceau(t.tailleMorceau());
        o.setTailleContenu(t.tailleContenu());
        o.setTaille(0L);
        o.setQuorum(t.quorum());
        o.setN(t.n());
        o.setEmpreintesDetenteurs(String.join(",", attendues));
        o.setRemplace(remplacee == null ? null : remplacee.getIdOffre());
        o.setGroupementNifs(groupement.isEmpty() ? null : String.join(",", groupement));
        offres.save(o);
        tracer(o, "CREATION", (lot == null ? "" : "lot " + lot + ", ") + t.nombreMorceaux() + " morceau(x) annoncé(s)"
                + (remplacee == null ? "" : ", remplace " + remplacee.getIdOffre()));
        return dto(o, 0);
    }

    // ------------------------------------------------------------------ §B4 morceaux

    /**
     * {@code PUT …/morceaux/{rang}} : rang de 0 à {@code nombreMorceaux - 1}, rejouable (un morceau renvoyé remplace le précédent).
     * 400 {@code EMPREINTE_DIFFERENTE} / {@code MORCEAU_INVALIDE} ; 409 {@code OFFRE_SCELLEE}, {@code DELAI_DEPASSE} ; 413.
     */
    public OffreDto.Recu morceau(String idCandidat, String idOffre, int rang, String empreinte, byte[] octets) {
        Offre o = sienne(idCandidat, idOffre);
        if (!Offre.EN_COURS.equals(o.getEtat())) {
            throw new BusinessRuleException("L'offre est déjà scellée : elle ne reçoit plus de morceau.", "OFFRE_SCELLEE");
        }
        exigerAvantLimite(o.getIdDmc());
        if (rang < 0 || rang >= o.getNombreMorceaux()) {
            throw new BadRequestException("Le rang " + rang + " est hors de l'offre (0 à " + (o.getNombreMorceaux() - 1) + ").",
                    "MORCEAU_INVALIDE");
        }
        if (octets == null || octets.length < SURCOUT_MORCEAU) {
            throw new BadRequestException("Le morceau " + rang + " est vide ou tronqué.", "MORCEAU_INVALIDE");
        }
        if (octets.length > o.getTailleMorceau() + SURCOUT_MORCEAU) {
            throw new PayloadTropVolumineuxException("Le morceau " + rang + " dépasse " + (o.getTailleMorceau() + SURCOUT_MORCEAU) + " octets.");
        }
        String calculee = ClesRsa.sha256Hex(octets);
        if (empreinte == null || !calculee.equalsIgnoreCase(empreinte.trim())) {
            throw new BadRequestException("L'empreinte du morceau " + rang + " ne correspond pas à son contenu : renvoyez-le.",
                    "EMPREINTE_DIFFERENTE");
        }
        stockage.ecrireMorceau(idOffre, rang, octets);
        OffreMorceau m = morceaux.findByIdOffreAndRang(idOffre, rang).orElseGet(OffreMorceau::new);
        m.setIdOffre(idOffre);
        m.setRang(rang);
        m.setTaille(octets.length);
        m.setEmpreinte(calculee);
        m.setDateRecu(maintenant());
        morceaux.save(m);
        List<OffreMorceau> recus = morceaux.findByIdOffreOrderByRangAsc(idOffre);
        o.setTaille(recus.stream().mapToLong(OffreMorceau::getTaille).sum());
        o.setDernierMorceau(maintenant());
        offres.save(o);
        return new OffreDto.Recu(rang, octets.length, recus.size());
    }

    // ------------------------------------------------------------------ §B4 sceller

    /**
     * {@code POST …/sceller} : tous les morceaux, tailles cohérentes, empreinte globale identique, date limite et exclusion
     * revérifiées, clés toujours publiées, taille ≤ {@code B04-SE-09}. Puis {@code DEPOSEE}, horodatage et numéro, remplacement,
     * {@code premierDepot}, accusé par courriel.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public OffreDto.Accuse sceller(String idCandidat, String idOffre, OffreDto.Scellement s) {
        Offre o = sienne(idCandidat, idOffre);
        if (!Offre.EN_COURS.equals(o.getEtat())) {
            throw new BusinessRuleException("L'offre est déjà scellée.", "OFFRE_SCELLEE");
        }
        if (s == null || s.empreinte() == null || !HEX64.matcher(s.empreinte().trim().toLowerCase()).matches()) {
            throw new BadRequestException("L'empreinte calculée par le navigateur (SHA-256, hexadécimal) est attendue.", "EMPREINTE_INVALIDE");
        }
        ProceduresEnLigneService.Lue lue = exigerAvantLimite(o.getIdDmc());
        List<String> publiees = clesPubliees(o.getIdDmc());
        if (!publiees.equals(List.of(o.getEmpreintesDetenteurs().split(",")))) {
            throw new BusinessRuleException("Une clé de la cérémonie a été remplacée depuis le début du dépôt : scellez de nouveau "
                    + "votre offre avec les clés publiées.", "CLES_INDISPONIBLES");
        }
        Entreprise entreprise = entreprises.findById(o.getIdEntreprise()).orElseThrow(() -> new BusinessRuleException(
                "Votre entreprise n'est plus déclarée.", "ENTREPRISE_ABSENTE"));
        controlerExclusions(entreprise, o.getGroupementNifs() == null ? List.of() : List.of(o.getGroupementNifs().split(",")));
        List<OffreMorceau> recus = morceaux.findByIdOffreOrderByRangAsc(idOffre);
        Set<Integer> presents = new LinkedHashSet<>();
        recus.forEach(m -> presents.add(m.getRang()));
        List<Integer> manquants = new ArrayList<>();
        for (int r = 0; r < o.getNombreMorceaux(); r++) {
            if (!presents.contains(r)) {
                manquants.add(r);
            }
        }
        if (!manquants.isEmpty()) {
            throw new BusinessRuleException("Morceaux manquants : " + manquants + ".", "MORCEAU_MANQUANT", null,
                    java.util.Map.of("rangs", manquants));
        }
        long contenu = 0;
        for (OffreMorceau m : recus) {
            boolean dernier = m.getRang() == o.getNombreMorceaux() - 1;
            if (!dernier && m.getTaille() != o.getTailleMorceau() + SURCOUT_MORCEAU) {
                throw new BusinessRuleException("Le morceau " + m.getRang() + " n'a pas la taille d'un morceau plein ("
                        + (o.getTailleMorceau() + SURCOUT_MORCEAU) + " octets) : seul le dernier est plus court.", "MORCEAU_INVALIDE");
            }
            contenu += m.getTaille() - SURCOUT_MORCEAU;
        }
        if (o.getTailleContenu() != null && contenu != o.getTailleContenu()) {
            throw new BusinessRuleException("Les morceaux reçus portent " + contenu + " octets de contenu, l'en-tête en annonce "
                    + o.getTailleContenu() + ".", "MORCEAU_INVALIDE");
        }
        Long max = ProceduresEnLigneService.tailleMaxOffreOctets(lue.etat());
        if (max != null && o.getTaille() + o.getEnTete().length() > max) {
            throw new BusinessRuleException("L'offre dépasse la taille maximale fixée par la fiche (" + max / (1024 * 1024) + " Mo).",
                    "TAILLE_DEPASSEE");
        }
        StockageOffres.Assemblage a = stockage.assembler(idOffre, o.getEnTete(), o.getNombreMorceaux());
        if (!a.empreinte().equalsIgnoreCase(s.empreinte().trim())) {
            stockage.supprimerConteneur(idOffre);
            tracer(o, "SCELLEMENT_REFUSE", "empreinte serveur " + a.empreinte() + " ≠ navigateur " + s.empreinte().trim());
            throw new BusinessRuleException("L'empreinte recalculée par le serveur (" + a.empreinte() + ") n'est pas celle du navigateur : "
                    + "un morceau a été altéré en route. Renvoyez les morceaux, puis scellez de nouveau.", "EMPREINTE_DIFFERENTE");
        }
        // Une seule offre déposée par entreprise et par lot.
        Optional<Offre> autre = deposee(o.getIdDmc(), o.getIdEntreprise(), o.getLot()).filter(x -> !x.getIdOffre().equals(o.getRemplace()));
        if (autre.isPresent()) {
            stockage.supprimerConteneur(idOffre);
            throw new BusinessRuleException("Une autre offre est déjà déposée pour ce lot.", "OFFRE_EXISTANTE");
        }
        stockage.retirerMorceaux(idOffre);
        morceaux.deleteAll(recus);
        LocalDateTime maintenant = maintenant();
        o.setEtat(Offre.DEPOSEE);
        o.setDateDepot(maintenant);
        o.setNumero(offres.findByIdDmcOrderByNumeroAscDateCreationAsc(o.getIdDmc()).stream().map(Offre::getNumero)
                .filter(Objects::nonNull).max(Integer::compare).orElse(0) + 1);
        o.setEmpreinte(a.empreinte());
        o.setChemin(a.chemin().toString());
        offres.save(o);
        if (o.getRemplace() != null) {
            offres.findById(o.getRemplace()).filter(x -> Offre.DEPOSEE.equals(x.getEtat())).ifPresent(x -> {
                x.setEtat(Offre.REMPLACEE);
                x.setRemplaceePar(o.getIdOffre());
                offres.save(x);
                tracer(x, "REMPLACEMENT", "remplacée par " + o.getIdOffre());
            });
        }
        ceremonies.poserPremierDepot(o.getIdDmc());
        tracer(o, "SCELLEMENT", "n° " + o.getNumero() + ", " + o.getTaille() + " octets, empreinte " + a.empreinte());
        OffreDto.Accuse accuse = accuse(o);
        candidats.findById(idCandidat).ifPresent(cc -> notifications.emettreCandidat(TypeNotification.ACCUSE_DEPOT, idCandidat,
                cc.getEmail(), o.getIdDmc().intValue(), TypeObjet.PROCEDURE, "Accusé de réception de votre offre",
                texteAccuse(o, lue.dto())));
        return accuse;
    }

    // ------------------------------------------------------------------ §B4 lire, retirer

    @Transactional(readOnly = true)
    public List<OffreDto> mesOffres(String idCandidat) {
        return offres.findByIdCandidatOrderByDateCreationDesc(idCandidat).stream().map(o -> dto(o, nombreRecus(o))).toList();
    }

    @Transactional(readOnly = true)
    public OffreDto lire(String idCandidat, String idOffre) {
        Offre o = sienne(idCandidat, idOffre);
        return dto(o, nombreRecus(o));
    }

    /** Le PDF de l'accusé : 409 {@code OFFRE_NON_DEPOSEE} pour un dépôt en cours. */
    @Transactional(readOnly = true)
    public byte[] accusePdf(String idCandidat, String idOffre) {
        Offre o = sienne(idCandidat, idOffre);
        if (o.getDateDepot() == null) {
            throw new BusinessRuleException("L'offre n'est pas encore déposée : pas d'accusé.", "OFFRE_NON_DEPOSEE");
        }
        ProcedureEnLigneDto p = procedureLue(o.getIdDmc());
        List<cnm.prs.service.DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "ACCUSÉ DE RÉCEPTION D'UNE OFFRE DÉPOSÉE EN LIGNE"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        for (String ligne : texteAccuse(o, p).split("\n")) {
            el.add(new DocumentLibre.Paragraphe(ligne.isBlank() ? DocumentLibre.Style.VIDE : DocumentLibre.Style.PARA, ligne));
        }
        return generateur.generer(new DocumentLibre("ACCUSE", null, el, "Offre " + o.getIdOffre())).stream()
                .filter(f -> "pdf".equals(f.extension())).findFirst().orElseThrow().contenu();
    }

    /**
     * {@code DELETE} : avant la date limite, si {@code B04-SE-10 = OUI} ; le conteneur est conservé, marqué {@code RETIREE}, et ne
     * s'ouvre jamais (question 4 au juriste). 409 {@code OFFRE_NON_DEPOSEE} pour un dépôt en cours (il se purge seul).
     */
    public OffreDto retirer(String idCandidat, String idOffre) {
        Offre o = sienne(idCandidat, idOffre);
        if (!Offre.DEPOSEE.equals(o.getEtat())) {
            throw new BusinessRuleException("Seule une offre déposée se retire" + (Offre.EN_COURS.equals(o.getEtat())
                    ? " : un dépôt en cours se purge seul après 24 heures, ou cède la place au suivant." : "."), "OFFRE_NON_DEPOSEE");
        }
        ProceduresEnLigneService.Lue lue = exigerAvantLimite(o.getIdDmc());
        if (!ProceduresEnLigneService.remplacementAutorise(lue.etat())) {
            throw new BusinessRuleException("La fiche n'autorise ni le remplacement ni le retrait d'une offre déposée.", "REMPLACEMENT_INTERDIT");
        }
        o.setEtat(Offre.RETIREE);
        o.setDateRetrait(maintenant());
        offres.save(o);
        tracer(o, "RETRAIT", "conteneur conservé, marqué RETIREE");
        candidats.findById(idCandidat).ifPresent(cc -> notifications.emettreCandidat(TypeNotification.OFFRE_RETIREE, idCandidat,
                cc.getEmail(), o.getIdDmc().intValue(), TypeObjet.PROCEDURE, "Votre offre est retirée",
                "Votre offre n° " + o.getNumero() + " (empreinte " + o.getEmpreinte() + ") pour la procédure « " + lue.dto().objet()
                        + " » est retirée le " + o.getDateRetrait().format(HORODATAGE) + ". Elle ne sera pas ouverte."));
        return dto(o, 0);
    }

    // ------------------------------------------------------------------ §B5 ce que la PRMP en sait

    /**
     * {@code GET /api/fiches-marche/{idDmc}/depots} : PRMP et UGPM de la fiche (périmètre), responsable de la procédure ; 403
     * autrement (Administrateur compris). Avant la date limite, le nombre seul ; après, le registre.
     */
    @Transactional(readOnly = true)
    public OffreDto.Depots depots(Long idDmc) {
        if (!internes.estTitulaire(idDmc)) {
            ProfilUtilisateur p = CurrentUser.profil().orElse(null);
            if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
                throw new AccessDeniedException("Les dépôts se lisent par la PRMP de la fiche, son UGPM et le responsable de la procédure.");
            }
            fiches.controlerLecture(idDmc);
        } else {
            fiches.controlerLecture(idDmc);
        }
        LocalDateTime limite = dateLimite(idDmc);
        boolean clos = limite != null && !maintenant().isBefore(limite);
        List<Offre> toutes = offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc);
        long nombre = toutes.stream().filter(o -> Offre.DEPOSEE.equals(o.getEtat())).count();
        if (!clos) {
            return new OffreDto.Depots(false, nombre, limite, null);
        }
        List<OffreDto.Depot> registre = new ArrayList<>();
        for (String etat : List.of(Offre.DEPOSEE, Offre.RETIREE, Offre.REMPLACEE, Offre.ECARTEE)) {
            toutes.stream().filter(o -> etat.equals(o.getEtat())).forEach(o -> registre.add(new OffreDto.Depot(o.getNumero(),
                    o.getRaisonSociale(), o.getNif(), o.getLot(), o.getDateDepot(), o.getDateRetrait(), o.getEmpreinte(), o.getTaille(),
                    o.getEtat())));
        }
        return new OffreDto.Depots(true, nombre, limite, registre);
    }

    /** Le résumé porté par la fiche : le nombre d'offres déposées et la clôture, ou {@code null} si la date limite ne se lit pas. */
    @Transactional(readOnly = true)
    public OffreDto.Resume resume(Long idDmc) {
        LocalDateTime limite = dateLimite(idDmc);
        return new OffreDto.Resume(offres.countByIdDmcAndEtat(idDmc, Offre.DEPOSEE), limite != null && !maintenant().isBefore(limite));
    }

    // ------------------------------------------------------------------ nuit et heure : purge, clôture

    /**
     * La purge des dépôts {@code EN_COURS} abandonnés (aucun morceau depuis {@value #HEURES_ABANDON} h, ou la date limite passée), et
     * la notification {@code DEPOTS_CLOS} (une fois, à la PRMP et au responsable). Rend {@code [purgés, clôtures notifiées]}.
     */
    public int[] entretenir() {
        LocalDateTime maintenant = maintenant();
        int purges = 0;
        for (Offre o : offres.findByEtat(Offre.EN_COURS)) {
            LocalDateTime dernier = o.getDernierMorceau() != null ? o.getDernierMorceau() : o.getDateCreation();
            LocalDateTime limite = dateLimite(o.getIdDmc());
            boolean abandon = dernier.isBefore(maintenant.minusHours(HEURES_ABANDON));
            boolean echu = limite != null && !maintenant.isBefore(limite);
            if (abandon || echu) {
                purger(o, abandon ? "aucun morceau depuis " + HEURES_ABANDON + " heures" : "date limite passée");
                purges++;
            }
        }
        int clotures = 0;
        for (CeremonieCles c : ceremonieRepository.findByEtat(CeremonieCles.CLOSE)) {
            if (c.getDateDepotsClos() != null) {
                continue;
            }
            LocalDateTime limite = dateLimite(c.getIdDmc());
            if (limite == null || maintenant.isBefore(limite)) {
                continue;
            }
            long nombre = offres.countByIdDmcAndEtat(c.getIdDmc(), Offre.DEPOSEE);
            String titre = "Dépôts clos : " + nombre + " offre(s)";
            String corps = "La date limite de remise des offres de la procédure " + c.getIdDmc() + " est passée ("
                    + limite.format(HORODATAGE) + ") : " + nombre + " offre(s) déposée(s). Le registre des dépôts est consultable.";
            ceremonies.notifierPrmp(c.getIdDmc(), TypeNotification.DEPOTS_CLOS, titre, corps);
            ceremonies.notifierResponsable(c.getIdDmc(), TypeNotification.DEPOTS_CLOS, titre, corps);
            c.setDateDepotsClos(maintenant);
            ceremonieRepository.save(c);
            clotures++;
        }
        return new int[] { purges, clotures };
    }

    // ------------------------------------------------------------------ conditions (§B1)

    /** Conditions 1 et 2 : la procédure est en ligne et ses dépôts sont ouverts. */
    private ProceduresEnLigneService.Lue procedureOuverte(Long idDmc) {
        ProceduresEnLigneService.Lue lue = procedures.trouver(idDmc).orElseThrow(() -> new BusinessRuleException(
                "Cette procédure n'est pas ouverte au dépôt en ligne.", "PROCEDURE_FERMEE"));
        String etat = lue.dto().etat();
        if (ProceduresEnLigneService.CLOSE.equals(etat)) {
            throw new BusinessRuleException("La date limite de remise des offres est passée (" + lue.dto().dateLimite() + ").", "DELAI_DEPASSE");
        }
        if (!ProceduresEnLigneService.OUVERTE.equals(etat)) {
            throw new BusinessRuleException("Les dépôts ne sont pas encore ouverts (ouverture le " + lue.dto().dateOuvertureDepots() + ").",
                    "PROCEDURE_FERMEE");
        }
        return lue;
    }

    /** La date limite, revérifiée à chaque morceau et au scellement. */
    private ProceduresEnLigneService.Lue exigerAvantLimite(Long idDmc) {
        ProceduresEnLigneService.Lue lue = procedures.trouver(idDmc).orElseThrow(() -> new BusinessRuleException(
                "Cette procédure n'est plus ouverte au dépôt en ligne.", "PROCEDURE_FERMEE"));
        LocalDateTime limite = ProceduresEnLigneService.dateLimite(lue.etat());
        if (limite != null && !maintenant().isBefore(limite)) {
            throw new BusinessRuleException("La date limite de remise des offres est passée (" + RemiseElectronique.isoMinute(limite)
                    + ") : le dépôt n'a pas été scellé à temps.", "DELAI_DEPASSE");
        }
        return lue;
    }

    /** Condition 3 : la cérémonie close et ses clés, dans l'ordre publié. */
    private List<String> clesPubliees(Long idDmc) {
        try {
            CeremonieDto.ClesPubliques cles = ceremonies.clesPubliques(idDmc);
            return cles.detenteurs().stream().map(CeremonieDto.ClePubliee::empreinte).toList();
        } catch (ResourceNotFoundException e) {
            throw new BusinessRuleException("Les clés de la cérémonie ne sont pas publiées : l'offre ne peut pas encore être scellée.",
                    "CLES_INDISPONIBLES");
        }
    }

    /** Condition 5 : l'entreprise et chaque membre du groupement ne sont pas exclus par l'ARMP, au répertoire du jour. */
    private void controlerExclusions(Entreprise entreprise, List<String> groupement) {
        LocalDate jour = LocalDate.now(horloge);
        exclusions.enCours(entreprise.getNif(), jour).ifPresent(e -> {
            throw new BusinessRuleException("Votre entreprise (NIF " + entreprise.getNif() + ") est exclue des marchés publics par la "
                    + "décision de l'ARMP " + e.getReferenceDecision() + " du " + e.getDateDebut().format(JOUR) + ", "
                    + jusquau(e) + ". Vous ne pouvez pas déposer d'offre pendant cette période.", "ENTREPRISE_EXCLUE");
        });
        for (String nif : groupement) {
            Optional<ExclusionArmp> ex = exclusions.enCours(nif, jour);
            if (ex.isPresent()) {
                ExclusionArmp e = ex.get();
                throw new BusinessRuleException("Un membre du groupement (NIF " + nif + (e.getRaisonSociale() == null ? "" : ", "
                        + e.getRaisonSociale()) + ") est exclu des marchés publics par la décision de l'ARMP " + e.getReferenceDecision()
                        + " du " + e.getDateDebut().format(JOUR) + ", " + jusquau(e) + ". Le groupement ne peut pas déposer d'offre "
                        + "pendant cette période.", "ENTREPRISE_EXCLUE");
            }
        }
    }

    private static String jusquau(ExclusionArmp e) {
        return e.getDateFin() == null ? "sans date de fin" : "jusqu'au " + e.getDateFin().format(JOUR);
    }

    private static List<String> nifsGroupement(List<String> saisis) {
        List<String> out = new ArrayList<>();
        if (saisis != null) {
            for (String s : saisis) {
                String n = NormalisationCandidat.identifiant(s);
                if (n != null && !out.contains(n)) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    /** Le lot : obligatoire et entre 1 et le nombre de lots pour un marché alloti, {@code null} sinon (400). */
    private static Integer controlerLot(ProcedureEnLigneDto p, Integer lot) {
        int nb = p.lots() == null ? 0 : p.lots().size();
        if (nb > 1) {
            if (lot == null || lot < 1 || lot > nb) {
                throw new BadRequestException("Le marché est alloti : le lot est attendu, de 1 à " + nb + ".", "LOT_INVALIDE");
            }
            return lot;
        }
        if (lot != null && lot != 1) {
            throw new BadRequestException("Le marché n'est pas alloti : lot nul attendu.", "LOT_INVALIDE");
        }
        return null;
    }

    // ------------------------------------------------------------------ l'en-tête (ADR §4)

    private record EnTete(String idOffre, int tailleMorceau, int nombreMorceaux, long tailleContenu, int quorum, int n) {
    }

    /**
     * Relit et contrôle l'en-tête, tel que le navigateur l'a sérialisé (400 {@code EN_TETE_INVALIDE}) : version 1, UUID, la
     * procédure et le lot du corps, les trois algorithmes, morceaux de 4 Mio dont le nombre colle à la taille, quorum et {@code n}
     * de la cérémonie, et {@code parts[].empreinte} exactement les empreintes publiées, dans l'ordre (409 {@code CLES_INDISPONIBLES}).
     */
    private EnTete lireEnTete(String brut, Long idDmc, Integer lot, List<String> attendues) {
        JsonNode t;
        try {
            t = mapper.readTree(brut);
        } catch (RuntimeException e) {
            throw invalide("l'en-tête n'est pas un JSON lisible");
        }
        if (t == null || !t.isObject()) {
            throw invalide("l'en-tête n'est pas un objet JSON");
        }
        if (t.path("version").asInt(-1) != 1) {
            throw invalide("version 1 attendue");
        }
        String idOffre = t.path("idOffre").asString(null);
        if (idOffre == null || !UUID.matcher(idOffre).matches()) {
            throw invalide("idOffre doit être un UUID");
        }
        if (!t.path("idDmc").isIntegralNumber() || t.path("idDmc").asLong() != idDmc) {
            throw invalide("idDmc ne correspond pas à la procédure");
        }
        JsonNode l = t.path("lot");
        Integer lotTete = l.isNull() || l.isMissingNode() ? null : l.isIntegralNumber() ? l.asInt() : -1;
        if (!Objects.equals(lotTete, lot)) {
            throw invalide("lot ne correspond pas au corps");
        }
        List<String> algos = new ArrayList<>();
        t.path("algorithmes").forEach(a -> algos.add(a.asString("")));
        if (!algos.equals(CeremonieService.ALGORITHMES)) {
            throw invalide("algorithmes attendus : " + CeremonieService.ALGORITHMES);
        }
        int tailleMorceau = t.path("tailleMorceau").asInt(-1);
        if (tailleMorceau != TAILLE_MORCEAU) {
            throw invalide("tailleMorceau attendue : " + TAILLE_MORCEAU);
        }
        int nombre = t.path("nombreMorceaux").asInt(-1);
        long tailleContenu = t.path("tailleContenu").asLong(-1);
        long attendu = Math.max(1, (tailleContenu + tailleMorceau - 1) / tailleMorceau);
        if (tailleContenu < 0 || nombre < 1 || nombre != attendu) {
            throw invalide("nombreMorceaux (" + nombre + ") ne correspond pas à tailleContenu (" + tailleContenu + ")");
        }
        int quorum = t.path("quorum").asInt(-1);
        int n = t.path("n").asInt(-1);
        RemiseElectronique.Internes i = internes.internes(idDmc);
        if (i == null || i.quorum() == null || quorum != i.quorum() || n != attendues.size()) {
            throw invalide("quorum et n doivent être ceux de la cérémonie");
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode p : t.path("parts")) {
            String part = p.path("part").asString(null);
            if (part == null || ClesRsa.decoder(part) == null) {
                throw invalide("chaque part chiffrée est attendue en base64");
            }
            parts.add(p.path("empreinte").asString("").toLowerCase());
        }
        if (!parts.equals(attendues)) {
            throw new BusinessRuleException("L'offre n'est pas scellée pour les clés publiées (une clé a pu être remplacée) : relisez "
                    + "les clés et scellez de nouveau.", "CLES_INDISPONIBLES");
        }
        return new EnTete(idOffre, tailleMorceau, nombre, tailleContenu, quorum, n);
    }

    private static BadRequestException invalide(String raison) {
        return new BadRequestException("En-tête du conteneur invalide : " + raison + ".", "EN_TETE_INVALIDE");
    }

    // ------------------------------------------------------------------ outils

    private Offre sienne(String idCandidat, String idOffre) {
        Offre o = offres.findById(idOffre).orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + idOffre + "."));
        if (!o.getIdCandidat().equals(idCandidat)) {
            throw new AccessDeniedException("Cette offre n'est pas la vôtre.");
        }
        return o;
    }

    private Optional<Offre> deposee(Long idDmc, Integer idEntreprise, Integer lot) {
        return lot == null ? offres.findFirstByIdDmcAndIdEntrepriseAndLotIsNullAndEtat(idDmc, idEntreprise, Offre.DEPOSEE)
                : offres.findFirstByIdDmcAndIdEntrepriseAndLotAndEtat(idDmc, idEntreprise, lot, Offre.DEPOSEE);
    }

    private void purger(Offre o, String raison) {
        stockage.purger(o.getIdOffre());
        morceaux.deleteAll(morceaux.findByIdOffreOrderByRangAsc(o.getIdOffre()));
        tracer(o, "PURGE", raison);
        offres.delete(o);
        offres.flush();
    }

    private LocalDateTime dateLimite(Long idDmc) {
        try {
            return fiches.etatValide(idDmc).map(ProceduresEnLigneService::dateLimite).orElse(null);
        } catch (ResourceNotFoundException | BusinessRuleException e) {
            return null;
        }
    }

    private ProcedureEnLigneDto procedureLue(Long idDmc) {
        return procedures.trouver(idDmc).map(ProceduresEnLigneService.Lue::dto).orElseGet(() -> procedures.vue(idDmc));
    }

    private int nombreRecus(Offre o) {
        return Offre.EN_COURS.equals(o.getEtat()) ? morceaux.findByIdOffreOrderByRangAsc(o.getIdOffre()).size() : o.getNombreMorceaux();
    }

    private void tracer(Offre o, String action, String detail) {
        journal.save(new OffreJournal(null, o.getIdOffre(), o.getIdDmc(), o.getIdCandidat(), maintenant(), action, detail));
    }

    private OffreDto dto(Offre o, int recus) {
        ProcedureEnLigneDto p;
        try {
            p = procedureLue(o.getIdDmc());
        } catch (RuntimeException e) {
            p = null;
        }
        return new OffreDto(o.getIdOffre(), o.getIdDmc(), p == null ? null : p.reference(), p == null ? null : p.objet(), o.getLot(),
                o.getEtat(), o.getDateCreation(), o.getDateDepot(), o.getDateRetrait(), o.getNumero(), o.getTaille(), o.getNombreMorceaux(),
                recus, o.getEmpreinte(), o.getRemplace(), o.getRemplaceePar());
    }

    private OffreDto.Accuse accuse(Offre o) {
        return new OffreDto.Accuse(dto(o, o.getNombreMorceaux()), new OffreDto.Entreprise(o.getNif(), o.getRaisonSociale()), o.getN(),
                o.getQuorum(), List.of(o.getEmpreintesDetenteurs().split(",")));
    }

    /** Le texte de l'accusé (courriel et PDF). */
    private static String texteAccuse(Offre o, ProcedureEnLigneDto p) {
        return "Procédure : " + (p == null ? o.getIdDmc() : p.reference() + " — " + p.objet()) + "\n"
                + (p == null || p.autoriteContractante() == null ? "" : "Autorité contractante : " + p.autoriteContractante() + "\n")
                + (o.getLot() == null ? "" : "Lot : " + o.getLot() + "\n")
                + "Entreprise : " + o.getRaisonSociale() + " (NIF " + o.getNif() + ")\n"
                + "Offre n° " + o.getNumero() + ", déposée le " + o.getDateDepot().format(HORODATAGE) + " (heure du serveur)\n"
                + "Identifiant : " + o.getIdOffre() + "\n"
                + "Taille : " + o.getTaille() + " octets chiffrés\n"
                + "Empreinte (SHA-256) : " + o.getEmpreinte() + "\n"
                + "Scellée pour " + o.getN() + " détenteurs de parts, quorum " + o.getQuorum() + " : " + o.getEmpreintesDetenteurs()
                + "\n\nLe serveur ne connaît pas le contenu de votre offre : il l'a reçue chiffrée et la conserve telle quelle jusqu'à "
                + "l'ouverture des plis. Gardez cet accusé : son empreinte sera recalculée à l'ouverture.";
    }

    /** {@code GET /api/horloge} : l'heure du serveur à la seconde ({@code AAAA-MM-JJTHH:MM:SS}) et le fuseau de la plateforme. */
    public OffreDto.Horloge horloge(String fuseau) {
        return new OffreDto.Horloge(maintenant().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                fuseau == null || fuseau.isBlank() ? "Indian/Antananarivo" : fuseau);
    }
}
