package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.ControleurDto;
import cnm.prs.dto.CreerInterimRequest;
import cnm.prs.dto.InterimDto;
import cnm.prs.dto.InterimLiensDto;
import cnm.prs.dto.MesInterimsDto;
import cnm.prs.dto.RevoquerInterimRequest;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Interim;
import cnm.prs.entity.InterimPiece;
import cnm.prs.entity.Localite;
import cnm.prs.enums.MotifInterim;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutInterim;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.InterimMapper;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.InterimPieceRepository;
import cnm.prs.repository.InterimRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.InterimContexte;
import cnm.prs.security.Suppleance;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ <strong>L'intérim désigné</strong> (demande front du 2026-09-21, « Gestion de l'INTÉRIM dans le circuit
 * de contrôle », lot 1 ; arbitrages du pilote du 2026-09-21 ; ADR-0008) — cycle de vie des désignations
 * ({@code t_interim}) et <strong>résolution des suppléances</strong> pour les gardes.
 *
 * <h2>Ce qu'un intérim est, et n'est pas</h2>
 *
 * <p>« X (titulaire) est absent du D1 au D2 ; Y (intérimaire) agit à sa place, dans SON périmètre, sous sa
 * PROPRE identité. » Il complète la délégation ascendante sans la remplacer : la délégation ouvre une
 * <em>tâche de profil</em> à un supérieur (jamais une identité — invariant du 2026-08-15) ; l'intérim ouvre
 * les <em>actes d'identité</em> du titulaire — dispatch, visa, réattribution, retrait, part de signature —
 * à une personne nommée, pour une période, avec une pièce. Et il <strong>descend</strong> : un Membre peut
 * suppléer un Chef de commission (arbitrage Q1), ce que la délégation ne permet jamais.</p>
 *
 * <h2>Qui désigne, qui supplée (§B3)</h2>
 *
 * <p>Le <strong>titulaire désigne lui-même</strong> son intérimaire (chacun connaît son absence) ; l'Administrateur
 * peut le faire en repli, comme pour les mandats. Le Président désigne n'importe quel CC ; un CC de la
 * Centrale, un autre CC de la Centrale ou un Membre de sa localité ; un CC régional, un Membre de sa
 * localité (il n'y a pas d'autre CC). Les autres profils ne déclarent pas d'absence dans ce lot.</p>
 *
 * <h2>Le statut se déduit, il ne se stocke pas</h2>
 *
 * <p>{@link #statutEffectif} lit les dates à la date du jour : {@code REVOQUE} prime dès sa date d'effet ;
 * sinon A_VENIR, ACTIF, ACHEVE. Un seul intérim ACTIF ou A_VENIR par titulaire à une date donnée
 * (chevauchement → 409). Le cumul côté intérimaire est <strong>signalé</strong>, pas interdit.</p>
 *
 * <h2>Non transitif</h2>
 *
 * <p>Chaque intérim porte son seul titulaire : l'intérimaire du Président désigne, s'il s'absente, un
 * intérimaire pour son <em>propre</em> rôle de CC — jamais pour le Président (arbitrage Q3).</p>
 */
@Service
@Transactional(readOnly = true)
public class InterimService {

    /** Profils qui déclarent une absence dans ce lot (contrainte {@code ck_interim_profil_titulaire}). */
    public static final Set<ProfilUtilisateur> PROFILS_TITULAIRES =
            EnumSet.of(ProfilUtilisateur.PRESIDENT, ProfilUtilisateur.CHEF_COMMISSION);

    /**
     * Profils qui peuvent être désignés intérimaires (§B3). C'est aussi la liste des profils pour lesquels le
     * contexte d'intérim est résolu à chaque requête : pour tout autre, aucune requête n'est faite.
     */
    public static final Set<ProfilUtilisateur> PROFILS_INTERIMAIRES =
            EnumSet.of(ProfilUtilisateur.CHEF_COMMISSION, ProfilUtilisateur.MEMBRE);

    /** Taille maximale de la pièce (alignée sur {@code spring.servlet.multipart.max-file-size}). */
    public static final int PIECE_MAX_OCTETS = 10 * 1024 * 1024;

    /** Borne « sans fin » d'un intérim {@code VACANCE_POSTE}, pour les requêtes de chevauchement. */
    private static final LocalDate SANS_FIN = LocalDate.of(9999, 12, 31);

    /** Statuts où un dossier est encore entre les mains de son attributaire (cumul signalé à la désignation). */
    private static final Set<String> STATUTS_ATTRIBUTION_EN_COURS = Set.of(StatutDossier.DISPATCHE.name(),
            StatutDossier.EXAMINE.name(), StatutDossier.A_REEXAMINER.name());

    private final InterimRepository repository;
    private final InterimPieceRepository pieceRepository;
    private final ControleurRepository controleurRepository;
    private final ProfileRepository profileRepository;
    private final DispatchRepository dispatchRepository;
    private final Clock clock;

    public InterimService(InterimRepository repository, InterimPieceRepository pieceRepository,
            ControleurRepository controleurRepository, ProfileRepository profileRepository,
            DispatchRepository dispatchRepository, Clock clock) {
        this.repository = repository;
        this.pieceRepository = pieceRepository;
        this.controleurRepository = controleurRepository;
        this.profileRepository = profileRepository;
        this.dispatchRepository = dispatchRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Historique chronologique, statut dérivé. {@code actifs} ne garde que les intérims ACTIFS <strong>et</strong>
     * A_VENIR (ceux qui comptent encore). Vue interne à la Commission : 403 pour la PRMP et l'UGPM.
     */
    public List<InterimDto> lister(String titulaire, String interimaire, boolean actifs) {
        exigerVueInterne();
        LocalDate aujourdhui = aujourdhui();
        List<Interim> interims;
        if (titulaire != null && !titulaire.isBlank()) {
            interims = repository.findByImTitulaireOrderByDateDebutAscIdInterimAsc(titulaire.trim());
        } else if (interimaire != null && !interimaire.isBlank()) {
            interims = repository.findByImInterimaireOrderByDateDebutAscIdInterimAsc(interimaire.trim());
        } else if (actifs) {
            interims = repository.findEnCoursOuAVenir(aujourdhui);
        } else {
            interims = repository.findAllByOrderByDateDebutAscIdInterimAsc();
        }
        return interims.stream()
                .filter(i -> !actifs || estEnCoursOuAVenir(i, aujourdhui))
                .filter(i -> interimaire == null || interimaire.isBlank() || interimaire.trim().equals(i.getImInterimaire()))
                .map(i -> versDto(i, aujourdhui))
                .toList();
    }

    /** Les intérims du connecté : exercés (ACTIF, intérimaire), subi (ACTIF, titulaire), à venir (l'un ou l'autre). */
    public MesInterimsDto mes() {
        exigerVueInterne();
        String moi = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (moi == null) {
            return new MesInterimsDto(List.of(), null, List.of());
        }
        LocalDate aujourdhui = aujourdhui();
        List<InterimDto> exerces = new ArrayList<>();
        List<InterimDto> aVenir = new ArrayList<>();
        InterimDto subi = null;
        for (Interim i : repository.findConcernant(moi)) {
            StatutInterim statut = statutEffectif(i, aujourdhui);
            if (statut == StatutInterim.ACTIF && moi.equals(i.getImInterimaire())) {
                exerces.add(versDto(i, aujourdhui));
            } else if (statut == StatutInterim.ACTIF && moi.equals(i.getImTitulaire())) {
                subi = versDto(i, aujourdhui);
            } else if (statut == StatutInterim.A_VENIR) {
                aVenir.add(versDto(i, aujourdhui));
            }
        }
        return new MesInterimsDto(exerces, subi, aVenir);
    }

    public InterimDto findById(Integer id) {
        exigerVueInterne();
        return versDto(charger(id), aujourdhui());
    }

    /** La pièce PDF ; 403 pour la PRMP et l'UGPM (même garde que la note d'intérim au visa), 404 sans pièce. */
    public byte[] piece(Integer id) {
        exigerVueInterne();
        charger(id);
        return pieceRepository.findById(id).map(InterimPiece::getContenu)
                .filter(c -> c != null && c.length > 0)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune pièce pour l'intérim " + id));
    }

    // ------------------------------------------------------------------ écriture

    /**
     * Désignation d'un intérimaire — gardes dans l'ordre : désignateur (403 nominatif), titulaire admis (409),
     * intérimaire admissible (409 nominatif), dates (400), chevauchement (409), pièce (400). Le cumul de
     * l'intérimaire est signalé dans {@code avertissements}, jamais refusé.
     */
    @Transactional
    public InterimDto creer(CreerInterimRequest req, MultipartFile piece) {
        String moi = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Acteur non identifié."));
        ProfilUtilisateur profilMoi = CurrentUser.profil().orElse(null);
        String imTitulaire = req.imTitulaire().trim();
        String imInterimaire = req.imInterimaire().trim();

        Controleur titulaire = controleurRepository.findById(imTitulaire)
                .orElseThrow(() -> new ResourceNotFoundException("Contrôleur introuvable : " + imTitulaire));
        // ① Le titulaire désigne lui-même ; l'Administrateur en repli (§B3, à confirmer par le pilote).
        if (!moi.equals(imTitulaire) && profilMoi != ProfilUtilisateur.ADMINISTRATEUR) {
            throw new AccessDeniedException("Seul " + nom(titulaire) + " désigne son intérimaire : chacun déclare "
                    + "sa propre absence (l'Administrateur peut le faire en repli).");
        }
        // ② Seuls le Président et les Chefs de commission déclarent une absence dans ce lot.
        ProfilUtilisateur profilTitulaire = profilDe(titulaire);
        if (profilTitulaire == null || !PROFILS_TITULAIRES.contains(profilTitulaire)) {
            throw new BusinessRuleException(nom(titulaire) + " ne déclare pas d'absence dans ce lot : seuls le "
                    + "Président et les Chefs de commission désignent un intérimaire.");
        }
        // ③ L'intérimaire : existe, distinct, et admissible selon le titulaire (§B3).
        if (imInterimaire.equals(imTitulaire)) {
            throw new BusinessRuleException("Un titulaire ne peut pas être son propre intérimaire.");
        }
        Controleur interimaire = controleurRepository.findById(imInterimaire)
                .orElseThrow(() -> new ResourceNotFoundException("Contrôleur introuvable : " + imInterimaire));
        ProfilUtilisateur profilInterimaire = profilDe(interimaire);
        exigerAdmissible(titulaire, profilTitulaire, interimaire, profilInterimaire);

        // ④ Les dates : une absence a un terme, sauf vacance de poste.
        LocalDate debut = req.dateDebut();
        LocalDate fin = req.dateFin();
        if (fin == null && req.motif() != MotifInterim.VACANCE_POSTE) {
            throw new BadRequestException("La date de fin est obligatoire (motif " + req.motif()
                    + ") : une absence a un terme ; une prolongation se déclare comme un nouvel intérim. "
                    + "Seule une vacance de poste (VACANCE_POSTE) reste sans terme.");
        }
        if (fin != null && fin.isBefore(debut)) {
            throw new BadRequestException("La date de fin (" + fin + ") précède la date de début (" + debut + ").");
        }
        // ⑤ Un seul intérim ACTIF ou A_VENIR par titulaire à une date donnée.
        List<Interim> chevauchants = repository.findChevauchantsTitulaire(imTitulaire, debut, fin == null ? SANS_FIN : fin);
        if (!chevauchants.isEmpty()) {
            Interim autre = chevauchants.get(0);
            throw new BusinessRuleException("La période " + debut + " → " + (fin == null ? "sans terme" : fin)
                    + " chevauche l'intérim n° " + autre.getIdInterim() + " déjà désigné pour " + autre.getNomTitulaire()
                    + " (" + autre.getNomInterimaire() + ", du " + autre.getDateDebut() + " au "
                    + (autre.getDateFin() == null ? "—" : autre.getDateFin()) + ").");
        }
        // ⑥ La pièce, obligatoire (arbitrage Q4) — lue en dernier : elle ne s'enregistre que sur une
        // désignation qui aboutit.
        byte[] contenu = lirePiece(piece);

        Interim interim = new Interim();
        interim.setImTitulaire(imTitulaire);
        interim.setNomTitulaire(nom(titulaire));
        interim.setProfilTitulaire(profilTitulaire.name());
        interim.setIdLocaliteTitulaire(titulaire.getIdLocalite());
        interim.setImInterimaire(imInterimaire);
        interim.setNomInterimaire(nom(interimaire));
        interim.setProfilInterimaire(profilInterimaire.name());
        interim.setIdLocaliteInterimaire(interimaire.getIdLocalite());
        interim.setDateDebut(debut);
        interim.setDateFin(fin);
        interim.setMotif(req.motif().name());
        interim.setReference(req.reference().trim());
        interim.setPieceNom(nomDeFichier(piece));
        interim.setPieceTaille((long) contenu.length);
        interim.setDesignePar(moi);
        interim.setNomDesignePar(controleurRepository.findById(moi).map(this::nom).orElse(moi));
        interim.setDateDesignation(LocalDateTime.now(clock));
        Interim enregistre = repository.save(interim);
        pieceRepository.save(new InterimPiece(enregistre.getIdInterim(), contenu));

        InterimDto dto = versDto(enregistre, aujourdhui());
        dto.setAvertissements(avertissements(enregistre, interimaire));
        return dto;
    }

    /**
     * Fin avant terme, par le titulaire, le désignateur ou l'Administrateur (403 sinon) ; déjà ACHEVE ou REVOQUE
     * → 409. À effet à la date donnée, au plus tôt aujourd'hui (400 sinon) : les droits tombent à la requête
     * suivante, les actes déjà posés restent ceux de l'intérimaire.
     */
    @Transactional
    public InterimDto revoquer(Integer id, RevoquerInterimRequest req) {
        Interim interim = charger(id);
        String moi = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Acteur non identifié."));
        ProfilUtilisateur profilMoi = CurrentUser.profil().orElse(null);
        if (!moi.equals(interim.getImTitulaire()) && !moi.equals(interim.getDesignePar())
                && profilMoi != ProfilUtilisateur.ADMINISTRATEUR) {
            throw new AccessDeniedException("La révocation de cet intérim appartient à " + interim.getNomTitulaire()
                    + " (titulaire), à celui qui l'a désigné, ou à l'Administrateur.");
        }
        LocalDate aujourdhui = aujourdhui();
        StatutInterim statut = statutEffectif(interim, aujourdhui);
        if (statut == StatutInterim.ACHEVE || statut == StatutInterim.REVOQUE) {
            throw new BusinessRuleException("Intérim déjà " + (statut == StatutInterim.ACHEVE ? "achevé le "
                    + interim.getDateFin() : "révoqué le " + interim.getDateRevocation()) + " : rien à révoquer.");
        }
        LocalDate date = req.dateRevocation() != null ? req.dateRevocation() : aujourdhui;
        if (date.isBefore(aujourdhui)) {
            throw new BadRequestException("La révocation prend effet au plus tôt aujourd'hui (" + aujourdhui
                    + ") : les actes déjà posés par l'intérimaire ne se défont pas.");
        }
        if (interim.getDateFin() != null && date.isAfter(interim.getDateFin())) {
            throw new BadRequestException("Date de révocation (" + date + ") hors de la période de l'intérim (→ "
                    + interim.getDateFin() + ").");
        }
        interim.setDateRevocation(date);
        interim.setMotifRevocation(req.motif().trim());
        interim.setRevoquePar(moi);
        return versDto(repository.save(interim), aujourdhui);
    }

    // ------------------------------------------------------------------ dérivation

    /** Le statut à une date : REVOQUE prime dès sa date d'effet ; sinon la période décide. */
    public StatutInterim statutEffectif(Interim interim, LocalDate date) {
        if (interim.getDateRevocation() != null && !date.isBefore(interim.getDateRevocation())) {
            return StatutInterim.REVOQUE;
        }
        if (date.isBefore(interim.getDateDebut())) {
            return StatutInterim.A_VENIR;
        }
        if (interim.getDateFin() != null && date.isAfter(interim.getDateFin())) {
            return StatutInterim.ACHEVE;
        }
        return StatutInterim.ACTIF;
    }

    private boolean estEnCoursOuAVenir(Interim i, LocalDate date) {
        StatutInterim s = statutEffectif(i, date);
        return s == StatutInterim.ACTIF || s == StatutInterim.A_VENIR;
    }

    // ------------------------------------------------------------------ résolution pour les gardes

    /**
     * Les suppléances <strong>actives aujourd'hui</strong> d'un contrôleur (celles qu'il exerce) — posées dans
     * {@link InterimContexte} par l'intercepteur, une fois par requête, pour les seuls profils qui peuvent
     * être intérimaires ({@link #PROFILS_INTERIMAIRES}) ; vide pour les autres, sans requête.
     */
    public List<Suppleance> suppleancesActivesDe(String im, ProfilUtilisateur profil) {
        if (im == null || im.isBlank() || profil == null || !PROFILS_INTERIMAIRES.contains(profil)) {
            return List.of();
        }
        return repository.findEnVigueurPourInterimaire(im, aujourdhui()).stream()
                .map(i -> suppleance(i, null)).toList();
    }

    /**
     * Le suppléant <strong>actif aujourd'hui</strong> d'un titulaire, s'il en a un — pour la copie des
     * notifications (§B4.7), avec le courriel de l'intérimaire résolu sur l'annuaire.
     */
    public Optional<Suppleance> suppleantActifDe(String imTitulaire) {
        if (imTitulaire == null || imTitulaire.isBlank()) {
            return Optional.empty();
        }
        return repository.findEnVigueurPourTitulaire(imTitulaire, aujourdhui()).stream().findFirst()
                .map(i -> suppleance(i, controleurRepository.findById(i.getImInterimaire())
                        .map(Controleur::getEmailCont).orElse(null)));
    }

    /**
     * ⚠️ Au titre de qui le connecté <strong>dispatche</strong> un dossier de cette localité (POST) : en son
     * nom s'il le peut (Président ; CC de la localité, hors centrale) ; sinon par intérim — du CC de la
     * localité s'il le supplée (jamais un dossier central : le CC ne pré-dispatche pas la centrale), sinon
     * du Président. Vide s'il n'agit pour personne : les gardes ordinaires tranchent alors, en son nom.
     */
    public Optional<Suppleance> suppleanceDispatch(String localiteDossier) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT) {
            return Optional.empty();
        }
        if (profil == ProfilUtilisateur.CHEF_COMMISSION && localiteDossier != null
                && localiteDossier.equals(localite) && !Localite.estCentrale(localiteDossier)) {
            return Optional.empty();   // son propre ressort
        }
        if (localiteDossier != null && !Localite.estCentrale(localiteDossier)) {
            Optional<Suppleance> cc = InterimContexte.suppleances().stream()
                    .filter(s -> s.profilTitulaire() == ProfilUtilisateur.CHEF_COMMISSION
                            && localiteDossier.equals(s.localiteTitulaire()))
                    .findFirst();
            if (cc.isPresent()) {
                return cc;
            }
        }
        return InterimContexte.duPresident();
    }

    /**
     * Au titre de qui le connecté agit face à un <strong>dispatcheur</strong> connu : lui-même s'il l'est, sinon
     * sa suppléance de ce dispatcheur, sinon vide. Le premier point de chaque garde d'identité étendue.
     */
    public static Optional<Suppleance> suppleanceFace(String moi, String titulaire) {
        if (titulaire == null || titulaire.equals(moi)) {
            return Optional.empty();
        }
        return InterimContexte.pour(titulaire);
    }

    // ------------------------------------------------------------------ annuaire

    /**
     * Enrichit une liste de fiches d'annuaire des liens d'intérim ACTIFS du jour (§B5), en une requête pour
     * toute la liste ; rien pour la PRMP et l'UGPM (vue interne, règle C2).
     */
    public void enrichir(List<ControleurDto> fiches) {
        if (fiches == null || fiches.isEmpty()) {
            return;
        }
        if (Visibilite.estPrmp()) {
            fiches.forEach(f -> {
                f.setInterimEnCours(null);
                f.setInterimPour(null);
            });
            return;
        }
        Map<String, InterimLiensDto.EnCours> parTitulaire = new HashMap<>();
        Map<String, List<InterimLiensDto.Pour>> parInterimaire = new HashMap<>();
        for (Interim i : repository.findEnVigueur(aujourdhui())) {
            parTitulaire.putIfAbsent(i.getImTitulaire(), new InterimLiensDto.EnCours(i.getIdInterim(),
                    i.getImInterimaire(), i.getNomInterimaire(), i.getDateFin()));
            parInterimaire.computeIfAbsent(i.getImInterimaire(), k -> new ArrayList<>())
                    .add(new InterimLiensDto.Pour(i.getIdInterim(), i.getImTitulaire(), i.getNomTitulaire(),
                            i.getDateFin()));
        }
        for (ControleurDto f : fiches) {
            f.setInterimEnCours(parTitulaire.get(f.getImControleur()));
            f.setInterimPour(parInterimaire.getOrDefault(f.getImControleur(), List.of()));
        }
    }

    // ------------------------------------------------------------------ outils

    private Suppleance suppleance(Interim i, String emailInterimaire) {
        return new Suppleance(i.getIdInterim(), i.getImTitulaire(), i.getNomTitulaire(),
                ProfilUtilisateur.valueOf(i.getProfilTitulaire()), i.getIdLocaliteTitulaire(),
                i.getImInterimaire(), i.getNomInterimaire(), emailInterimaire);
    }

    /**
     * Admissibilité de l'intérimaire selon le titulaire (§B3) — Président ← CC (toute localité) ; CC de la
     * Centrale ← CC de la Centrale ou Membre de sa localité ; CC régional ← Membre de sa localité. 409 nominatif.
     */
    private void exigerAdmissible(Controleur titulaire, ProfilUtilisateur profilTitulaire, Controleur interimaire,
            ProfilUtilisateur profilInterimaire) {
        String qui = nom(interimaire);
        if (profilTitulaire == ProfilUtilisateur.PRESIDENT) {
            if (profilInterimaire != ProfilUtilisateur.CHEF_COMMISSION) {
                throw new BusinessRuleException(qui + " n'est pas un Chef de commission : le Président ne "
                        + "désigne qu'un Chef de commission, de n'importe quelle localité.");
            }
            return;
        }
        String localite = titulaire.getIdLocalite();
        boolean memeLocalite = localite != null && localite.equals(interimaire.getIdLocalite());
        if (profilInterimaire == ProfilUtilisateur.MEMBRE) {
            if (!memeLocalite) {
                throw new BusinessRuleException(qui + " n'est pas de la localité " + localite
                        + " : un Chef de commission ne désigne qu'un Membre de sa localité.");
            }
            return;
        }
        if (profilInterimaire == ProfilUtilisateur.CHEF_COMMISSION && Localite.estCentrale(localite)) {
            if (!memeLocalite) {
                throw new BusinessRuleException(qui + " n'est pas de la localité " + localite
                        + " : un Chef de commission de la Centrale ne désigne qu'un autre Chef de commission "
                        + "de la Centrale, ou un Membre de sa localité.");
            }
            return;
        }
        throw new BusinessRuleException(qui + " n'est pas " + (Localite.estCentrale(localite)
                ? "un Chef de commission de la Centrale ni un Membre de la localité " + localite
                : "un Membre de la localité " + localite)
                + " : seuls ceux-là peuvent suppléer " + nom(titulaire) + ".");
    }

    /** Les cumuls signalés à la désignation (§B3) : déjà intérimaire d'un autre, déjà attributaire de dossiers. */
    private List<String> avertissements(Interim interim, Controleur interimaire) {
        List<String> avertissements = new ArrayList<>();
        LocalDate fin = interim.getDateFin() == null ? SANS_FIN : interim.getDateFin();
        for (Interim autre : repository.findChevauchantsInterimaire(interim.getImInterimaire(), interim.getDateDebut(), fin)) {
            if (!autre.getIdInterim().equals(interim.getIdInterim())) {
                avertissements.add(interim.getNomInterimaire() + " est déjà intérimaire de " + autre.getNomTitulaire()
                        + " du " + autre.getDateDebut() + " au " + (autre.getDateFin() == null ? "—" : autre.getDateFin())
                        + " (intérim n° " + autre.getIdInterim() + ").");
            }
        }
        long attributions = dispatchRepository.countAttributionsEnCours(interimaire.getImControleur(),
                STATUTS_ATTRIBUTION_EN_COURS);
        if (attributions > 0) {
            avertissements.add(interim.getNomInterimaire() + " est attributaire de " + attributions
                    + " dossier" + (attributions > 1 ? "s" : "") + " en cours d'examen.");
        }
        return avertissements;
    }

    /** Lit et valide la pièce : obligatoire, PDF reconnu sur les octets d'en-tête, taille plafonnée — 400 sinon. */
    private static byte[] lirePiece(MultipartFile piece) {
        if (piece == null || piece.isEmpty()) {
            throw new BadRequestException("Pièce requise : joignez la note de service ou la décision (PDF) qui "
                    + "désigne l'intérimaire — un intérim ne se déclare pas sans pièce.");
        }
        byte[] contenu;
        try {
            contenu = piece.getBytes();
        } catch (java.io.IOException e) {
            throw new BadRequestException("Pièce illisible : " + e.getMessage());
        }
        boolean pdf = contenu.length >= 4 && contenu[0] == 0x25 && contenu[1] == 0x50
                && contenu[2] == 0x44 && contenu[3] == 0x46;   // %PDF
        if (!pdf) {
            throw new BadRequestException("Pièce invalide : seul le format PDF est accepté.");
        }
        if (contenu.length > PIECE_MAX_OCTETS) {
            throw new BadRequestException("Pièce trop volumineuse (" + contenu.length + " octets ; max "
                    + PIECE_MAX_OCTETS + ").");
        }
        return contenu;
    }

    private static String nomDeFichier(MultipartFile piece) {
        String nom = piece == null ? null : piece.getOriginalFilename();
        return nom == null || nom.isBlank() ? "designation-interim.pdf" : nom;
    }

    private Interim charger(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intérim introuvable : " + id));
    }

    private InterimDto versDto(Interim interim, LocalDate date) {
        return InterimMapper.toDto(interim, statutEffectif(interim, date).name());
    }

    /** Vue interne à la Commission : la PRMP et l'UGPM n'apprennent pas qui supplée qui (comme la note d'intérim). */
    private static void exigerVueInterne() {
        if (Visibilite.estPrmp()) {
            throw new AccessDeniedException("Les intérims sont une organisation interne de la Commission : ils ne "
                    + "sont pas communiqués aux personnes responsables des marchés publics.");
        }
    }

    /** Profil d'un contrôleur par sa FK scalaire ({@code ID_PROFILE}), comme {@code ControleurDirectory.profilDe}. */
    private ProfilUtilisateur profilDe(Controleur c) {
        return c.getIdProfile() == null ? null
                : profileRepository.findById(c.getIdProfile()).map(p -> ProfilUtilisateur.resolve(p.getProfile())).orElse(null);
    }

    /** « NOM Prénoms » (convention canonique) ; repli sur le matricule. */
    private String nom(Controleur c) {
        String nom = ActeurDirectory.nomCanonique(c.getNomCont(), c.getPrenomsCont());
        return nom.isBlank() ? c.getImControleur() : nom;
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }
}
