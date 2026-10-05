package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CompteDesignableDto;
import cnm.prs.dto.ParametresInternesDto;
import cnm.prs.dto.ParametresInternesRequest;
import cnm.prs.dto.ResponsableProcedureDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.ParametreInterneJournal;
import cnm.prs.entity.ParametreInterneProcedure;
import cnm.prs.entity.Profile;
import cnm.prs.entity.ResponsableProcedure;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.repository.ParametreInterneJournalRepository;
import cnm.prs.repository.ParametreInterneProcedureRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.ResponsableProcedureRepository;
import cnm.prs.dto.CeremonieDto;
import cnm.prs.entity.CaoMembre;
import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.CleDetenteur;
import cnm.prs.entity.CompteCao;
import cnm.prs.entity.CompteDepositaire;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CleDetenteurRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>Les paramètres internes d'une procédure et le rôle « Responsable de la procédure »</strong> (demande front du
 * 2026-09-27, remise électronique, §B4 et §B5 ; ADR-0010).
 *
 * <ul>
 *   <li><strong>Paramètres internes</strong> ({@code t_parametre_interne_procedure}) : membres détenteurs d'une part de clé,
 *       quorum, cérémonie des clés. <strong>Réservés au titulaire du rôle</strong> pour cette fiche — 403 pour tout
 *       autre, Administrateur et PRMP compris. Ordre des gardes : profil authentifié → identité (prédicat pur
 *       {@link PredicatsIdentite#estResponsableProcedure}) → corps. Modifiables tant que la fiche n'est pas validée en mode
 *       électronique ; après, lecture seule (409 {@code FICHE_VALIDEE}). Un membre ne peut pas être le responsable
 *       (409 {@code MEMBRE_COMMISSION}, dans les deux sens).</li>
 *   <li><strong>Journal</strong> : le journal dédié ({@code t_parametre_interne_journal}) porte ancienne et nouvelle valeur
 *       et n'est servi qu'au titulaire (Q7) ; le journal global {@code t_audit_log} reçoit la route par l'intercepteur,
 *       sans valeurs.</li>
 *   <li><strong>Responsable</strong> ({@code t_responsable_procedure}) : désigné et retiré par l'Administrateur, un seul actif
 *       par DMC (409 {@code RESPONSABLE_EXISTANT}), jamais un membre de la commission de la fiche. Désignables : les
 *       contrôleurs de la localité de la fiche et ceux sans localité (compétents partout), hors membres.</li>
 *   <li>L'<strong>état</strong> seul ({@code COMPLETS} / {@code INCOMPLETS} / {@code ABSENTS}) et le titulaire sont exposés sur
 *       la fiche à tous ceux qui la lisent ({@link #contexteBilan}).</li>
 * </ul>
 */
@Service
@Transactional
public class ParametresInternesService {

    static final String CHAMP_MEMBRES = "membresCommission";
    static final String CHAMP_QUORUM = "quorum";
    static final String CHAMP_CEREMONIE = "dateCeremonie";
    static final String CHAMP_RESPONSABLE = "responsable";
    /** ⚠️ V66 (lot 2, §B1) — le dépositaire de la part de secours, au journal dédié. */
    static final String CHAMP_DEPOSITAIRE = "depositaire";

    private final ParametreInterneProcedureRepository internesRepository;
    private final CeremonieClesRepository ceremonieRepository;
    private final CleDetenteurRepository cleRepository;
    private final NotificationService notifications;
    /** ⚠️ V67 (lot 2a) — la CAO, source des membres détenteurs de parts. */
    private final cnm.prs.repository.CaoRepository caoRepository;
    private final cnm.prs.repository.CaoMembreRepository caoMembreRepository;
    private final cnm.prs.repository.CompteCaoRepository compteCaoRepository;
    /** ⚠️ V71 (dépositaire, §B1) — le compte du dépositaire et ses exclusions. */
    private final CompteDepositaireService comptesDepositaire;
    private final cnm.prs.repository.CompteDepositaireRepository compteDepositaireRepository;
    private final cnm.prs.repository.MarcheRepository marcheRepository;
    private final cnm.prs.repository.DossierRepository dossierRepository;
    private final cnm.prs.repository.PrmpRepository prmpRepository;
    private final cnm.prs.repository.UgpmRepository ugpmRepository;
    private final cnm.prs.repository.CompteCandidatRepository candidatRepository;
    private final cnm.prs.repository.CompteAuthRepository compteAuthRepository;
    private final ParametreInterneJournalRepository journalRepository;
    private final ResponsableProcedureRepository responsableRepository;
    private final DossierMecRepository dmcRepository;
    private final FicheMarcheRepository ficheRepository;
    private final FicheMarcheValeurRepository valeurRepository;
    private final ChampFicheMarcheRepository champRepository;
    private final ControleurRepository controleurRepository;
    private final ProfileRepository profileRepository;
    private final ValeursPpmService valeursPpm;
    private final ActeurDirectory acteurs;
    private final ParametreService parametres;
    private final ObjectMapper mapper;

    public ParametresInternesService(ParametreInterneProcedureRepository internesRepository,
            ParametreInterneJournalRepository journalRepository, ResponsableProcedureRepository responsableRepository,
            DossierMecRepository dmcRepository, FicheMarcheRepository ficheRepository,
            FicheMarcheValeurRepository valeurRepository, ChampFicheMarcheRepository champRepository,
            ControleurRepository controleurRepository, ProfileRepository profileRepository, ValeursPpmService valeursPpm,
            ActeurDirectory acteurs, ParametreService parametres, ObjectMapper mapper,
            CeremonieClesRepository ceremonieRepository, CleDetenteurRepository cleRepository, NotificationService notifications,
            cnm.prs.repository.CaoRepository caoRepository, cnm.prs.repository.CaoMembreRepository caoMembreRepository,
            cnm.prs.repository.CompteCaoRepository compteCaoRepository, CompteDepositaireService comptesDepositaire,
            cnm.prs.repository.CompteDepositaireRepository compteDepositaireRepository, cnm.prs.repository.MarcheRepository marcheRepository,
            cnm.prs.repository.DossierRepository dossierRepository, cnm.prs.repository.PrmpRepository prmpRepository,
            cnm.prs.repository.UgpmRepository ugpmRepository, cnm.prs.repository.CompteCandidatRepository candidatRepository,
            cnm.prs.repository.CompteAuthRepository compteAuthRepository) {
        this.comptesDepositaire = comptesDepositaire;
        this.compteDepositaireRepository = compteDepositaireRepository;
        this.marcheRepository = marcheRepository;
        this.dossierRepository = dossierRepository;
        this.prmpRepository = prmpRepository;
        this.ugpmRepository = ugpmRepository;
        this.candidatRepository = candidatRepository;
        this.compteAuthRepository = compteAuthRepository;
        this.caoRepository = caoRepository;
        this.caoMembreRepository = caoMembreRepository;
        this.compteCaoRepository = compteCaoRepository;
        this.ceremonieRepository = ceremonieRepository;
        this.cleRepository = cleRepository;
        this.notifications = notifications;
        this.internesRepository = internesRepository;
        this.journalRepository = journalRepository;
        this.responsableRepository = responsableRepository;
        this.dmcRepository = dmcRepository;
        this.ficheRepository = ficheRepository;
        this.valeurRepository = valeurRepository;
        this.champRepository = champRepository;
        this.controleurRepository = controleurRepository;
        this.profileRepository = profileRepository;
        this.valeursPpm = valeursPpm;
        this.acteurs = acteurs;
        this.parametres = parametres;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ ce que la fiche dit à tous (§B5.1)

    /** Le titulaire actif du rôle pour ce DMC, s'il y en a un. */
    @Transactional(readOnly = true)
    public Optional<ResponsableProcedure> responsable(Long idDmc) {
        return responsableRepository.findFirstByIdDmcAndDateRetraitIsNull(idDmc);
    }

    /** Le titulaire en DTO, {@code null} sans titulaire. */
    @Transactional(readOnly = true)
    public ResponsableProcedureDto responsableDto(Long idDmc) {
        return responsable(idDmc).map(r -> new ResponsableProcedureDto(r.getImResponsable(), r.getNomResponsable())).orElse(null);
    }

    /** L'utilisateur courant est-il le titulaire du rôle pour ce DMC ? */
    @Transactional(readOnly = true)
    public boolean estTitulaire(Long idDmc) {
        String acteur = CurrentUser.ref().orElse(null);
        return responsable(idDmc).map(r -> PredicatsIdentite.estResponsableProcedure(acteur, r.getImResponsable())).orElse(false);
    }

    /** Les paramètres internes en lecture pure ({@code null} : jamais enregistrés). */
    @Transactional(readOnly = true)
    public RemiseElectronique.Internes internes(Long idDmc) {
        ParametreInterneProcedure p = internesRepository.findById(idDmc).orElse(null);
        // ⚠️ V67 (lot 2a, §B3, Q11) — les membres sont DÉRIVÉS de la CAO (qualité MEMBRE, comptes K…) ; une CAO désignée sans
        // paramètres enregistrés donne des paramètres incomplets (quorum, date), plus « absents ».
        List<String> membres = membresCao(idDmc);
        if (p == null && membres.isEmpty()) {
            return null;
        }
        return new RemiseElectronique.Internes(membres, p == null ? null : p.getQuorum(), p == null ? null : p.getDateCeremonie(),
                responsable(idDmc).map(ResponsableProcedure::getImResponsable).orElse(null), depositaire(p));
    }

    /** ⚠️ V66 (lot 2, §B1) — le dépositaire de la part de secours, {@code null} tant qu'il n'est pas désigné. */
    static CeremonieDto.Depositaire depositaire(ParametreInterneProcedure p) {
        if (p == null || p.getDepositaireNom() == null || p.getDepositaireNom().isBlank()) {
            return null;
        }
        return new CeremonieDto.Depositaire(p.getDepositaireNom(), p.getDepositaireOrganisme(), p.getDepositaireFonction(),
                p.getDepositaireContact(), p.getDepositaireEmail(), p.getDepositaireTelephone(), null);
    }

    /** ⚠️ V71 (§B1) — le compte {@code D…} du dépositaire désigné, {@code null} sans dépositaire ou désigné avant V71 sans adresse. */
    @Transactional(readOnly = true)
    public String compteDepositaire(Long idDmc) {
        return internesRepository.findById(idDmc).map(ParametreInterneProcedure::getIdCompteDepositaire).orElse(null);
    }

    /** ⚠️ V71 — l'appelant est-il le dépositaire désigné de cette procédure (profil {@code DEPOSITAIRE}, son compte) ? */
    @Transactional(readOnly = true)
    public boolean estDepositaire(Long idDmc) {
        String ref = CurrentUser.ref().orElse(null);
        return ref != null && cnm.prs.enums.TypeActeur.DEPOSITAIRE.name().equals(CurrentUser.acteurType().orElse(null))
                && ref.equals(compteDepositaire(idDmc));
    }

    /**
     * Le contexte que le bilan lit hors de la fiche (règles 6, 8, 10, 11) : le mode du cadrage, les paramètres
     * administrables, les paramètres internes, la présence d'un responsable.
     */
    @Transactional(readOnly = true)
    public ControlesFicheMarche.RemiseElectroniqueBilan contexteBilan(Long idDmc, Map<String, ?> cadrage) {
        boolean electronique = RemiseElectronique.electronique(cadrage);
        return new ControlesFicheMarche.RemiseElectroniqueBilan(electronique,
                electronique ? parametres.remiseElectronique() : null, electronique ? internes(idDmc) : null,
                responsable(idDmc).isPresent(), !electronique || caoConstituee(idDmc));   // ⚠️ V67 : règle 13
    }

    /** L'état servi sur la fiche : {@code COMPLETS}, {@code INCOMPLETS} ou {@code ABSENTS}. */
    @Transactional(readOnly = true)
    public RemiseElectronique.Etat etat(Long idDmc) {
        return RemiseElectronique.etat(internes(idDmc), datePublication(idDmc));
    }

    // ------------------------------------------------------------------ paramètres internes (§B4)

    @Transactional(readOnly = true)
    public ParametresInternesDto lire(Long idDmc) {
        exigerTitulaire(idDmc);
        return dto(idDmc);
    }

    /**
     * Remplace les paramètres internes. Gardes, dans l'ordre : titulaire (403) ; fiche validée en mode électronique
     * (409 {@code FICHE_VALIDEE}) ; corps (400 nominatifs) ; un membre est le responsable (409 {@code MEMBRE_COMMISSION}).
     * Journal dédié : une entrée par champ changé, ancienne → nouvelle valeur.
     */
    public ParametresInternesDto ecrire(Long idDmc, ParametresInternesRequest corps) {
        exigerTitulaire(idDmc);
        CeremonieCles ceremonie = ceremonieRepository.findById(idDmc).orElse(null);
        // ⚠️ V66 (lot 2, §B5.2) — une cérémonie rouverte rend les paramètres modifiables, fiche validée ou non : c'est là qu'un
        // membre qui quitte la commission se remplace.
        if (ceremonie == null || !CeremonieCles.A_REFAIRE.equals(ceremonie.getEtat())) {
            exigerFicheModifiable(idDmc);
        }
        ParametresInternesRequest c = corps == null ? new ParametresInternesRequest(null, null, null) : corps;
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        // ⚠️ V66 (lot 2, §B1) — le dépositaire : une désignation nominative, le nom obligatoire s'il est donné.
        CeremonieDto.Depositaire depositaire = null;
        if (c.depositaire() != null) {
            CeremonieDto.Depositaire d = c.depositaire();
            // ⚠️ V71 (demande du 05/10, §B1) — l'adresse est obligatoire : le dépositaire reçoit un compte et génère sa clé.
            String email = d.email() == null || d.email().isBlank() ? null : CandidatService.normaliserEmail(d.email());
            if (d.nom() == null || d.nom().isBlank()) {
                erreurs.add(new ErrorResponse.FieldError(CHAMP_DEPOSITAIRE, "Le nom du dépositaire de la part de secours est obligatoire."));
            }
            if (email == null) {
                erreurs.add(new ErrorResponse.FieldError(CHAMP_DEPOSITAIRE + ".email", "L'adresse électronique du dépositaire est "
                        + "obligatoire : il reçoit un compte et génère lui-même la clé de secours."));
            } else if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                erreurs.add(new ErrorResponse.FieldError(CHAMP_DEPOSITAIRE + ".email", "Adresse électronique invalide."));
            }
            if (d.nom() != null && !d.nom().isBlank() && email != null) {
                depositaire = new CeremonieDto.Depositaire(d.nom().trim(), vide(d.organisme()), vide(d.fonction()), vide(d.contact()),
                        email, vide(d.telephone()), null);
            }
        }
        // ⚠️ V67 (lot 2a, §B3, Q11) — les membres sont ceux de la CAO, désignée par la PRMP : le corps ne les porte plus.
        if (c.membresCommission() != null && !c.membresCommission().isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError(CHAMP_MEMBRES, RemiseElectronique.MESSAGE_MEMBRES_CAO));
        }
        Set<String> membres = new LinkedHashSet<>(membresCao(idDmc));
        if (c.quorum() != null && c.quorum() < 1) {
            erreurs.add(new ErrorResponse.FieldError(CHAMP_QUORUM, "Le quorum de déchiffrement est un nombre de membres, 1 au moins."));
        }
        LocalDateTime ceremonieDate = null;
        if (c.dateCeremonie() != null && !c.dateCeremonie().isBlank()) {
            ceremonieDate = RemiseElectronique.dateHeure(c.dateCeremonie());
            if (ceremonieDate == null) {
                erreurs.add(new ErrorResponse.FieldError(CHAMP_CEREMONIE,
                        "La cérémonie des clés attend une date et une heure AAAA-MM-JJTHH:MM."));
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        String titulaire = responsable(idDmc).map(ResponsableProcedure::getImResponsable).orElse(null);
        if (titulaire != null && membres.stream().anyMatch(m -> m.equalsIgnoreCase(titulaire))) {
            throw new BusinessRuleException("Le responsable de la procédure (" + titulaire + ") ne peut pas détenir une part de clé : "
                    + "retirez-le des membres ou changez de responsable.", "MEMBRE_COMMISSION");
        }

        ParametreInterneProcedure p = internesRepository.findById(idDmc).orElse(null);
        Integer avantQuorum = p == null ? null : p.getQuorum();
        LocalDateTime avantCeremonie = p == null ? null : p.getDateCeremonie();
        CeremonieDto.Depositaire avantDepositaire = depositaire(p);
        // ⚠️ V66 (lot 2, §B2.4) — une cérémonie close fige quorum, date et dépositaire (⚠️ V67 : et la CAO, côté PRMP) :
        // rouvrir d'abord (§B5.2).
        if (ceremonie != null && CeremonieCles.CLOSE.equals(ceremonie.getEtat())
                && (!Objects.equals(avantQuorum, c.quorum()) || !Objects.equals(avantCeremonie, ceremonieDate)
                        || !Objects.equals(avantDepositaire, depositaire))) {
            throw new BusinessRuleException("La cérémonie des clés est close : le quorum, la date de la cérémonie et le dépositaire "
                    + "ne se modifient plus. Rouvrez la cérémonie d'abord.", "CEREMONIE_CLOSE");
        }
        if (p == null) {
            p = new ParametreInterneProcedure();
            p.setIdDmc(idDmc);
        }
        p.setMembresCle(null);   // ⚠️ V67 — dérivés de la CAO, plus jamais écrits ici
        p.setQuorum(c.quorum());
        p.setDateCeremonie(ceremonieDate);
        p.setDepositaireNom(depositaire == null ? null : depositaire.nom());
        p.setDepositaireOrganisme(depositaire == null ? null : depositaire.organisme());
        p.setDepositaireFonction(depositaire == null ? null : depositaire.fonction());
        p.setDepositaireContact(depositaire == null ? null : depositaire.contact());
        // ⚠️ V71 (§B1) — le compte du dépositaire : exclusions nommées, puis créé ou retrouvé par son adresse ; invité et appelé à
        // publier sa clé s'il change.
        String avantCompte = p.getIdCompteDepositaire();
        CompteDepositaire compte = null;
        if (depositaire != null) {
            exclusionsDepositaire(idDmc, depositaire.email());
            compte = comptesDepositaire.creerOuRetrouver(depositaire.email(), depositaire.nom(), depositaire.telephone());
        }
        p.setDepositaireEmail(depositaire == null ? null : depositaire.email());
        p.setDepositaireTelephone(depositaire == null ? null : depositaire.telephone());
        p.setIdCompteDepositaire(compte == null ? null : compte.getIdCompte());
        p.setDateMaj(LocalDateTime.now());
        p.setImMaj(CurrentUser.ref().orElse(null));
        internesRepository.save(p);
        if (compte != null && !compte.getIdCompte().equals(avantCompte)) {
            String[] pa = procedureEtAutorite(idDmc);
            if (!CompteDepositaire.ACTIF.equals(compte.getEtat())) {
                comptesDepositaire.inviter(compte, pa[0], pa[1]);
            }
            notifierDepositaire(idDmc, compte, TypeNotification.CLE_A_PUBLIER, "Part de secours : votre clé est à publier",
                    "Vous êtes désigné dépositaire de la part de secours de la procédure « " + pa[0] + " ». Générez votre clé de secours "
                            + "sur votre poste et publiez-la depuis votre espace : vous seul verrez votre phrase secrète.");
        }

        journaliser(idDmc, CHAMP_QUORUM, avantQuorum == null ? null : String.valueOf(avantQuorum),
                c.quorum() == null ? null : String.valueOf(c.quorum()));
        journaliser(idDmc, CHAMP_CEREMONIE, avantCeremonie == null ? null : RemiseElectronique.isoMinute(avantCeremonie),
                ceremonieDate == null ? null : RemiseElectronique.isoMinute(ceremonieDate));
        journaliser(idDmc, CHAMP_DEPOSITAIRE, texte(avantDepositaire), texte(depositaire));
        return dto(idDmc);
    }

    // ------------------------------------------------------------------ ⚠️ V67 (lot 2a, §B3) — la CAO, source des membres

    /** Les membres détenteurs d'une part : les identifiants courts ({@code K…}) des membres de qualité {@code MEMBRE} de la CAO. */
    @Transactional(readOnly = true)
    public List<String> membresCao(Long idDmc) {
        return caoMembreRepository.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream()
                .filter(m -> m.estMembre() && m.getIdCompte() != null).map(CaoMembre::getIdCompte).toList();
    }

    /** La CAO est-elle constituée (règle 13) ? */
    @Transactional(readOnly = true)
    public boolean caoConstituee(Long idDmc) {
        return CaoRegles.constituee(caoRepository.findById(idDmc).orElse(null), caoMembreRepository.findByIdDmcOrderByRangAscIdMembreAsc(idDmc));
    }

    /** L'état de la CAO servi sur la fiche : {@code null} en mode papier, sinon {@code ABSENTE} / {@code INCOMPLETE} / {@code COMPLETE}. */
    @Transactional(readOnly = true)
    public String etatCao(Long idDmc, boolean electronique) {
        if (!electronique) {
            return null;
        }
        return CaoRegles.etat(caoRepository.findById(idDmc).orElse(null), caoMembreRepository.findByIdDmcOrderByRangAscIdMembreAsc(idDmc));
    }

    /** « NOM Prénom » d'un détenteur : un membre de CAO ({@code K…}), ou un contrôleur ; le matricule à défaut. */
    @Transactional(readOnly = true)
    public String nomMembre(String im) {
        if (im == null) {
            return null;
        }
        CompteCao cao = compteCaoRepository.findById(im).orElse(null);
        if (cao != null) {
            return CompteCaoService.nom(cao);
        }
        return controleurRepository.findById(im).map(c -> ActeurDirectory.nomCanonique(c.getNomCont(), c.getPrenomsCont())).orElse(im);
    }

    /** Le dépositaire au journal dédié : « nom ; organisme ; fonction ; contact », {@code null} sans dépositaire. */
    static String texte(CeremonieDto.Depositaire d) {
        if (d == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(d.nom());
        for (String s : List.of(d.organisme() == null ? "" : d.organisme(), d.fonction() == null ? "" : d.fonction(),
                d.contact() == null ? "" : d.contact(), d.email() == null ? "" : d.email(), d.telephone() == null ? "" : d.telephone())) {
            if (!s.isBlank()) {
                sb.append(" ; ").append(s);
            }
        }
        return sb.toString();
    }


    // ------------------------------------------------------------------ ⚠️ V71 (demande du 05/10) — le compte du dépositaire

    /**
     * Les exclusions du dépositaire (409 {@code DEPOSITAIRE_INCOMPATIBLE}, la raison dite, pas le compte) : la PRMP ou une UGPM de
     * la fiche, le responsable de la procédure, un membre de la CAO de la procédure, un candidat inscrit — et toute adresse déjà
     * prise par un autre compte de PRS (un login est une adresse, un compte n'a qu'un profil).
     */
    private void exclusionsDepositaire(Long idDmc, String email) {
        cnm.prs.entity.DossierMec dmc = dmcRepository.findById(idDmc).orElse(null);
        String idPrmp = dmc == null ? null : marcheRepository.findIdDossierByIdDetail(dmc.getIdDetail()).flatMap(dossierRepository::findById)
                .map(cnm.prs.entity.Dossier::getIdPrmp).orElse(null);
        String raison = null;
        if (idPrmp != null && prmpRepository.findById(idPrmp).map(cnm.prs.entity.Prmp::getEmailPrmp).map(CandidatService::normaliserEmail)
                .filter(email::equals).isPresent()) {
            raison = "la PRMP de la fiche";
        } else if (idPrmp != null && ugpmRepository.findByIdPrmpTutelle(idPrmp).stream().map(cnm.prs.entity.Ugpm::getEmailUgpm)
                .filter(Objects::nonNull).map(CandidatService::normaliserEmail).anyMatch(email::equals)) {
            raison = "une UGPM de la fiche";
        } else if (responsable(idDmc).flatMap(r -> controleurRepository.findById(r.getImResponsable())).map(Controleur::getEmailCont)
                .filter(Objects::nonNull).map(CandidatService::normaliserEmail).filter(email::equals).isPresent()) {
            raison = "le responsable de la procédure";
        } else if (caoMembreRepository.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream().map(CaoMembre::getEmail)
                .filter(Objects::nonNull).map(CandidatService::normaliserEmail).anyMatch(email::equals)) {
            raison = "un membre de la commission d'appel d'offres de la procédure";
        } else if (candidatRepository.existsByEmail(email)) {
            raison = "un candidat inscrit (conflit d'intérêts)";
        } else {
            cnm.prs.entity.CompteAuth a = compteAuthRepository.findByLogin(email).orElse(null);
            if (a != null && !cnm.prs.enums.TypeActeur.DEPOSITAIRE.name().equals(a.getTypeActeur())) {
                raison = cnm.prs.enums.TypeActeur.MEMBRE_CAO.name().equals(a.getTypeActeur()) ? "un membre de commission d'appel d'offres"
                        : "un autre compte de PRS";
            }
        }
        if (raison != null) {
            throw new BusinessRuleException("L'adresse " + email + " est celle de " + raison + " : cette personne ne peut pas être "
                    + "dépositaire de la part de secours.", "DEPOSITAIRE_INCOMPATIBLE");
        }
    }

    /**
     * {@code POST …/parametres-internes/depositaire/inviter} (responsable) : renvoie l'invitation ; 409 {@code DEPOSITAIRE_ABSENT}
     * sans dépositaire à compte, {@code DEJA_ACTIF} si le compte est activé.
     */
    public ParametresInternesDto inviterDepositaire(Long idDmc) {
        exigerTitulaire(idDmc);
        CompteDepositaire compte = Optional.ofNullable(compteDepositaire(idDmc)).flatMap(compteDepositaireRepository::findById)
                .orElseThrow(() -> new BusinessRuleException("Aucun dépositaire à compte n'est désigné : renseignez son adresse "
                        + "électronique dans les paramètres internes.", "DEPOSITAIRE_ABSENT"));
        String[] pa = procedureEtAutorite(idDmc);
        comptesDepositaire.inviter(compte, pa[0], pa[1]);
        return dto(idDmc);
    }

    /** Une notification vers le dépositaire désigné de la procédure (trace et courriel), s'il a un compte. */
    void notifierDepositaire(Long idDmc, TypeNotification type, String titre, String corps) {
        Optional.ofNullable(compteDepositaire(idDmc)).flatMap(compteDepositaireRepository::findById)
                .ifPresent(c -> notifierDepositaire(idDmc, c, type, titre, corps));
    }

    private void notifierDepositaire(Long idDmc, CompteDepositaire c, TypeNotification type, String titre, String corps) {
        notifications.emettreDepositaire(type, c.getIdCompte(), c.getEmail(), idDmc.intValue(), TypeObjet.PROCEDURE, titre, corps);
    }

    /** L'objet de la procédure et l'autorité contractante, lus sur le plan (pour les courriels). */
    String[] procedureEtAutorite(Long idDmc) {
        cnm.prs.entity.DossierMec dmc = dmcRepository.findById(idDmc).orElse(null);
        if (dmc == null) {
            return new String[] { "procédure " + idDmc, "" };
        }
        Map<String, String> v = valeursPpm.lire(dmc.getIdDetail()).valeurs();
        String objet = v.get("OBJET");
        String entite = v.get("ENTITE");
        return new String[] { objet == null ? "procédure " + idDmc : objet, entite == null ? "" : entite };
    }
    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** ⚠️ V66 (lot 2, §B6) — une notification vers un membre (contrôleur), objet {@code PROCEDURE} = le DMC. */
    void notifierMembre(Long idDmc, String im, TypeNotification type, String titre, String corps) {
        // ⚠️ V67 (lot 2a, §B4) — un membre de CAO : notification de son type, et le courriel part aussi (une personne
        // extérieure ne vit pas dans l'application) ; sinon, un contrôleur (le responsable).
        CompteCao cao = compteCaoRepository.findById(im).orElse(null);
        if (cao != null) {
            notifications.emettreMembreCao(type, im, cao.getEmail(), idDmc.intValue(), TypeObjet.PROCEDURE, titre, corps);
            return;
        }
        String email = controleurRepository.findById(im).map(Controleur::getEmailCont).orElse(null);
        notifications.emettreControleur(type, im, email, idDmc.intValue(), TypeObjet.PROCEDURE, null, titre, corps);
    }

    // ------------------------------------------------------------------ responsable (§B5)

    /**
     * Désigne le titulaire (Administrateur, contrôlé par le contrôleur). 404 compte inconnu ; 409 {@code RESPONSABLE_EXISTANT}
     * s'il y a déjà un titulaire actif ; 409 {@code MEMBRE_COMMISSION} si le compte détient une part de clé de cette fiche.
     */
    public ResponsableProcedureDto designer(Long idDmc, String im) {
        exigerDmc(idDmc);
        String m = im == null ? "" : im.trim();
        Controleur c = controleurRepository.findById(m)
                .orElseThrow(() -> new ResourceNotFoundException("Compte inconnu : " + m + "."));
        responsable(idDmc).ifPresent(r -> {
            throw new BusinessRuleException("Un responsable de la procédure est déjà désigné (" + r.getImResponsable()
                    + ") : retirez-le d'abord.", "RESPONSABLE_EXISTANT");
        });
        RemiseElectronique.Internes i = internes(idDmc);
        if (i != null && i.membres().stream().anyMatch(x -> x.equalsIgnoreCase(m))) {
            throw new BusinessRuleException("Le compte " + m + " détient une part de clé de cette procédure : un membre de la "
                    + "commission ne peut pas en être le responsable.", "MEMBRE_COMMISSION");
        }
        ResponsableProcedure r = new ResponsableProcedure();
        r.setIdDmc(idDmc);
        r.setImResponsable(m);
        r.setNomResponsable(ActeurDirectory.nomCanonique(c.getNomCont(), c.getPrenomsCont()));
        r.setDesignePar(CurrentUser.ref().orElse(null));
        r.setDateDesignation(LocalDateTime.now());
        responsableRepository.save(r);
        journaliser(idDmc, CHAMP_RESPONSABLE, null, m);
        return new ResponsableProcedureDto(r.getImResponsable(), r.getNomResponsable());
    }

    /** Retire le titulaire actif (Administrateur) ; 404 s'il n'y en a pas. */
    public void retirer(Long idDmc) {
        exigerDmc(idDmc);
        ResponsableProcedure r = responsable(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun responsable de la procédure n'est désigné pour le DMC " + idDmc + "."));
        r.setRetirePar(CurrentUser.ref().orElse(null));
        r.setDateRetrait(LocalDateTime.now());
        responsableRepository.save(r);
        journaliser(idDmc, CHAMP_RESPONSABLE, r.getImResponsable(), null);
    }

    /**
     * Les comptes désignables comme responsable (Administrateur) : les contrôleurs de la localité de la fiche et ceux
     * sans localité (compétents partout), hors membres détenteurs d'une part de clé de cette fiche.
     */
    @Transactional(readOnly = true)
    public List<CompteDesignableDto> candidatsResponsable(Long idDmc) {
        exigerDmc(idDmc);
        String localite = localite(idDmc);
        RemiseElectronique.Internes i = internes(idDmc);
        Set<String> membres = new LinkedHashSet<>(i == null ? List.of() : i.membres());
        Map<Integer, ProfilUtilisateur> profils = profils();
        List<CompteDesignableDto> out = new ArrayList<>();
        for (Controleur c : controleurRepository.findAll()) {
            boolean competent = c.getIdLocalite() == null || localite == null || localite.equals(c.getIdLocalite());
            if (competent && membres.stream().noneMatch(m -> m.equalsIgnoreCase(c.getImControleur()))) {
                out.add(designable(c, profils.get(c.getIdProfile())));
            }
        }
        out.sort(java.util.Comparator.comparing(CompteDesignableDto::nom, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ------------------------------------------------------------------ gardes et lectures

    /** Profil authentifié, puis identité : seul le titulaire du rôle pour ce DMC passe (Administrateur et PRMP compris : 403). */
    private void exigerTitulaire(Long idDmc) {
        if (CurrentUser.profil().isEmpty()) {
            throw new AccessDeniedException("Les paramètres internes de la procédure sont réservés à son responsable.");
        }
        exigerDmc(idDmc);
        if (!estTitulaire(idDmc)) {
            throw new AccessDeniedException("Les paramètres internes de la procédure " + idDmc
                    + " sont réservés au responsable de la procédure désigné pour cette fiche.");
        }
    }

    private void exigerDmc(Long idDmc) {
        if (idDmc == null || !dmcRepository.existsById(idDmc)) {
            throw new ResourceNotFoundException("DMC introuvable : " + idDmc);
        }
    }

    /** Après validation en mode électronique, les paramètres internes sont en lecture seule (409 {@code FICHE_VALIDEE}). */
    private void exigerFicheModifiable(Long idDmc) {
        FicheMarche f = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null);
        if (f != null && StatutFicheMarche.VALIDEE.name().equals(f.getStatut()) && RemiseElectronique.electronique(cadrage(f))) {
            throw new BusinessRuleException("La fiche marché est validée en remise électronique (version " + f.getNumeroVersion()
                    + ") : ses paramètres internes sont en lecture seule ; ouvrez une révision pour les modifier.", "FICHE_VALIDEE");
        }
    }

    private Map<String, Object> cadrage(FicheMarche f) {
        if (f == null || f.getCadrage() == null || f.getCadrage().isBlank()) {
            return Map.of();
        }
        return mapper.readValue(f.getCadrage(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    /** La date de publication de l'avis de la version courante (rôle {@code SE_CEREMONIE:PUBLICATION}) ; {@code null} sans elle. */
    private LocalDateTime datePublication(Long idDmc) {
        FicheMarche f = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null);
        if (f == null || f.getIdFiche() == null) {
            return null;
        }
        String code = null;
        for (ChampFicheMarche c : champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc()) {
            for (String[] rr : ControlesFicheMarche.controles(c)) {
                if (ControlesFicheMarche.SE_CEREMONIE.equals(rr[0]) && RemiseElectronique.ROLE_PUBLICATION.equals(rr[1])) {
                    code = c.getCode();
                }
            }
        }
        if (code == null) {
            return null;
        }
        String cible = code;
        return valeurRepository.findByIdFiche(f.getIdFiche()).stream().filter(v -> cible.equals(v.getCodeChamp()))
                .map(v -> RemiseElectronique.dateHeure(v.getValeur())).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String localite(Long idDmc) {
        return dmcRepository.findById(idDmc).map(d -> valeursPpm.enTete(d.getIdDetail()).idLocalite()).orElse(null);
    }

    private Map<Integer, ProfilUtilisateur> profils() {
        Map<Integer, ProfilUtilisateur> out = new LinkedHashMap<>();
        for (Profile p : profileRepository.findAll()) {
            out.put(p.getIdProfile(), ProfilUtilisateur.resolve(p.getProfile()));
        }
        return out;
    }

    private static CompteDesignableDto designable(Controleur c, ProfilUtilisateur profil) {
        return new CompteDesignableDto(c.getImControleur(), ActeurDirectory.nomCanonique(c.getNomCont(), c.getPrenomsCont()),
                profil == null ? null : profil.name());
    }

    private ParametresInternesDto dto(Long idDmc) {
        RemiseElectronique.Internes i = internes(idDmc);
        LocalDateTime publication = datePublication(idDmc);
        List<CompteDesignableDto> membres = new ArrayList<>();
        if (i != null) {
            // ⚠️ V67 (lot 2a) — les membres sont ceux de la CAO (comptes MEMBRE_CAO) : lus, plus choisis.
            for (String im : i.membres()) {
                membres.add(new CompteDesignableDto(im, nomMembre(im), compteCaoRepository.existsById(im)
                        ? ProfilUtilisateur.MEMBRE_CAO.name() : null));
            }
        }
        Integer quorum = i != null ? i.quorum() : parametres.remiseElectronique().quorumPropose();
        List<ParametresInternesDto.Anomalie> anomalies = RemiseElectronique.anomalies(i, publication).stream()
                .map(a -> new ParametresInternesDto.Anomalie(a.regle(), a.message())).toList();
        List<ParametresInternesDto.EntreeJournal> journal = journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc).stream()
                .map(j -> new ParametresInternesDto.EntreeJournal(j.getDate(), j.getImActeur(), j.getNomActeur(), j.getChamp(),
                        j.getAncienneValeur(), j.getNouvelleValeur()))
                .toList();
        // ⚠️ V66 (lot 2) — la part de secours (§B1) et les avertissements (§B3, S1).
        // ⚠️ V71 (§B1) — le dépositaire avec l'état de son compte.
        CeremonieDto.Depositaire dep = i == null ? null : i.depositaire();
        if (dep != null) {
            String idCompte = compteDepositaire(idDmc);
            CompteDepositaire cd = idCompte == null ? null : compteDepositaireRepository.findById(idCompte).orElse(null);
            dep = new CeremonieDto.Depositaire(dep.nom(), dep.organisme(), dep.fonction(), dep.contact(), dep.email(), dep.telephone(),
                    cd == null ? null : new CeremonieDto.CompteDepositaire(cd.getIdCompte(), CompteDepositaireService.etat(cd)));
        }
        CeremonieDto.PartDeSecours secours = new CeremonieDto.PartDeSecours(dep, etatPartDeSecours(idDmc, i));
        List<ParametresInternesDto.Anomalie> avertissements = new ArrayList<>();
        if (i != null && i.quorumSansMarge() && !RemiseElectronique.quorumInvalide(i)) {
            avertissements.add(new ParametresInternesDto.Anomalie(ControlesFicheMarche.SE_QUORUM_MARGE, RemiseElectronique.MESSAGE_QUORUM_MARGE));
        }
        return new ParametresInternesDto(idDmc, membres, membres.size(), quorum, i == null ? null : i.dateCeremonie(),
                responsableDto(idDmc), RemiseElectronique.etat(i, publication).name(), anomalies, journal, secours, avertissements);
    }

    /** ⚠️ V66 (lot 2, §B1) — {@code A_DESIGNER}, {@code DESIGNE}, puis l'état de la clé de secours ({@code PUBLIEE}, {@code VERIFIEE}, {@code PERDUE}). */
    private String etatPartDeSecours(Long idDmc, RemiseElectronique.Internes i) {
        if (i == null || i.depositaire() == null) {
            return "A_DESIGNER";
        }
        return cleRepository.findFirstByIdDmcAndRoleAndDateArchivageIsNull(idDmc, CleDetenteur.SECOURS)
                .map(CleDetenteur::getEtatPart).orElse("DESIGNE");
    }

    /** Une entrée du journal dédié si la valeur change (valeurs en clair : le journal n'est servi qu'au titulaire). */
    void journaliser(Long idDmc, String champ, String avant, String apres) {
        if (Objects.equals(avant, apres)) {
            return;
        }
        String acteur = CurrentUser.ref().orElse(null);
        String login = CurrentUser.login().orElse(null);
        ParametreInterneJournal j = new ParametreInterneJournal();
        j.setIdDmc(idDmc);
        j.setDate(LocalDateTime.now());
        // ⚠️ Arbitrages du pilote (§B4.1) : le « ref » d'une UGPM est celui de sa PRMP de tutelle — l'acteur réel est nommé par son login.
        boolean ugpm = CurrentUser.profil().filter(p -> p == cnm.prs.enums.ProfilUtilisateur.UGPM).isPresent();
        j.setImActeur(!ugpm && acteur != null && acteur.length() <= 10 ? acteur : null);
        String nom = login == null ? null : acteurs.nom(login);
        j.setNomActeur(nom != null ? nom : ugpm ? login : null);
        j.setChamp(champ);
        j.setAncienneValeur(avant);
        j.setNouvelleValeur(apres);
        journalRepository.save(j);
    }
}
