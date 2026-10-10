package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.CaoDto;
import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.entity.Cao;
import cnm.prs.entity.CaoMembre;
import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.CleDetenteur;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.CompteCao;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CaoMembreRepository;
import cnm.prs.repository.CaoRepository;
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CleDetenteurRepository;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.CompteCaoRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>La commission d'appel d'offres (CAO), détentrice des parts de clé</strong> (demande front du 2026-10-04, soumission
 * en ligne, lot 2a ; décision du pilote, Q11 ; V67 ; ADR-0010 amendé).
 * <ul>
 *   <li><strong>La composition est un acte public de la PRMP</strong> (une décision) : {@code PUT} par la PRMP de la fiche (⚠️ ou son UGPM, §B4.1),
 *       lecture par qui lit la fiche. Une CAO par DAO. Contrôles : décision (référence, date) ; au moins deux membres de qualité
 *       {@code MEMBRE} et exactement un président parmi eux ; un {@code MEMBRE} a une origine (agent de l'entité contractante
 *       avec son service, expert de l'objet avec son domaine) ; une adresse une seule fois ; <strong>exclusions par
 *       construction</strong> (409 {@code MEMBRE_EXCLU}, la raison sans le compte) : contrôleur de la CNM, PRMP ou UGPM de la
 *       fiche, candidat inscrit, compte interne.</li>
 *   <li><strong>Les comptes</strong> : chaque {@code MEMBRE} reçoit, à la désignation, un compte {@code MEMBRE_CAO} à activer et
 *       une invitation par courriel ({@link CompteCaoService}) ; un compte déjà actif est seulement notifié. Les experts
 *       adjoints n'ont ni part ni compte.</li>
 *   <li><strong>V50 corrigé</strong> : {@code membresCommission} est dérivé de la CAO ({@link ParametresInternesService#membresCao}),
 *       la règle 13 {@code SE_CAO} entre au bilan, le journal dédié garde la trace des membres (Q7). Une cérémonie close fige la
 *       composition (409 {@code CEREMONIE_CLOSE}).</li>
 * </ul>
 */
@Service
@Transactional
public class CaoService {

    static final long TAILLE_MAX_DECISION = 10L * 1024 * 1024;

    private final CaoRepository caoRepository;
    private final CaoMembreRepository membreRepository;
    private final CompteCaoRepository compteCaoRepository;
    private final CompteCaoService comptes;
    private final CompteAuthRepository compteAuthRepository;
    private final ControleurRepository controleurRepository;
    private final PrmpRepository prmpRepository;
    private final UgpmRepository ugpmRepository;
    private final CompteCandidatRepository candidatRepository;
    private final DossierMecRepository dmcRepository;
    private final MarcheRepository marcheRepository;
    private final DossierRepository dossierRepository;
    private final FicheMarcheService fiches;
    private final ValeursPpmService valeursPpm;
    private final ParametresInternesService internes;
    private final ProceduresEnLigneService procedures;
    private final CeremonieClesRepository ceremonieRepository;
    private final CleDetenteurRepository cleRepository;

    public CaoService(CaoRepository caoRepository, CaoMembreRepository membreRepository, CompteCaoRepository compteCaoRepository,
            CompteCaoService comptes, CompteAuthRepository compteAuthRepository, ControleurRepository controleurRepository,
            PrmpRepository prmpRepository, UgpmRepository ugpmRepository, CompteCandidatRepository candidatRepository,
            DossierMecRepository dmcRepository, MarcheRepository marcheRepository, DossierRepository dossierRepository,
            FicheMarcheService fiches, ValeursPpmService valeursPpm, ParametresInternesService internes,
            ProceduresEnLigneService procedures, CeremonieClesRepository ceremonieRepository, CleDetenteurRepository cleRepository) {
        this.caoRepository = caoRepository;
        this.membreRepository = membreRepository;
        this.compteCaoRepository = compteCaoRepository;
        this.comptes = comptes;
        this.compteAuthRepository = compteAuthRepository;
        this.controleurRepository = controleurRepository;
        this.prmpRepository = prmpRepository;
        this.ugpmRepository = ugpmRepository;
        this.candidatRepository = candidatRepository;
        this.dmcRepository = dmcRepository;
        this.marcheRepository = marcheRepository;
        this.dossierRepository = dossierRepository;
        this.fiches = fiches;
        this.valeursPpm = valeursPpm;
        this.internes = internes;
        this.procedures = procedures;
        this.ceremonieRepository = ceremonieRepository;
        this.cleRepository = cleRepository;
    }

    // ------------------------------------------------------------------ §B1 lecture et désignation

    /** La CAO, pour qui lit la fiche (PRMP, UGPM, Commission, Administrateur, responsable) : 404, 403 hors périmètre. */
    @Transactional(readOnly = true)
    public CaoDto lire(Long idDmc) {
        fiches.controlerLecture(idDmc);
        return dto(idDmc);
    }

    /**
     * Désigne la CAO (PRMP de la fiche seule) : 400 par champ, 409 {@code MEMBRE_EXCLU}, 409 {@code CEREMONIE_CLOSE} si la
     * cérémonie est close et que les membres détenteurs changent. Un {@code id} absent crée le membre, présent le met à jour, un
     * membre omis est retiré. Comptes et invitations à la suite.
     */
    public CaoDto ecrire(Long idDmc, CaoDto.Corps corps) {
        exigerPrmp();
        fiches.controlerLecture(idDmc);
        CaoDto.Corps c = corps == null ? new CaoDto.Corps(null, null) : corps;
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        if (c.decision() == null || c.decision().reference() == null || c.decision().reference().isBlank()) {
            erreurs.add(new ErrorResponse.FieldError("decision.reference", "La référence de la décision de nomination est obligatoire."));
        }
        if (c.decision() == null || c.decision().date() == null) {
            erreurs.add(new ErrorResponse.FieldError("decision.date", "La date de la décision de nomination est obligatoire."));
        }
        List<CaoDto.MembreCorps> saisis = c.membres() == null ? List.of() : c.membres();
        Set<String> adresses = new LinkedHashSet<>();
        int presidents = 0;
        int membres = 0;
        int experts = 0;
        for (int i = 0; i < saisis.size(); i++) {
            CaoDto.MembreCorps m = saisis.get(i);
            String p = "membres[" + i + "].";
            if (m == null) {
                erreurs.add(new ErrorResponse.FieldError(p.substring(0, p.length() - 1), "Membre vide."));
                continue;
            }
            if (vide(m.nom())) {
                erreurs.add(new ErrorResponse.FieldError(p + "nom", "Le nom est obligatoire."));
            }
            if (vide(m.prenom())) {
                erreurs.add(new ErrorResponse.FieldError(p + "prenom", "Le prénom est obligatoire."));
            }
            String email = CandidatService.normaliserEmail(m.email());
            if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                erreurs.add(new ErrorResponse.FieldError(p + "email", "Une adresse électronique valable est obligatoire."));
            } else if (!adresses.add(email)) {
                erreurs.add(new ErrorResponse.FieldError(p + "email", "Cette adresse figure deux fois dans la commission."));
            }
            // ⚠️ 2026-10-04 (précision du pilote, demande 2a §B6) — « un expert est suffisant dans la CAO » : tout membre siège et
            // détient une part ; la qualité EXPERT_ADJOINT n'existe plus (MEMBRE toléré et ignoré).
            if (m.qualite() != null && !CaoMembre.MEMBRE.equals(m.qualite())) {
                erreurs.add(new ErrorResponse.FieldError(p + "qualite", CaoMembre.EXPERT_ADJOINT.equals(m.qualite())
                        ? "Les experts adjoints n'existent plus : un expert de l'objet siège comme membre."
                        : "La qualité n'est plus à envoyer : tout membre de la commission détient une part."));
            }
            membres++;
            if (Boolean.TRUE.equals(m.president())) {
                presidents++;
            }
            if (CaoMembre.ENTITE_CONTRACTANTE.equals(m.origine())) {
                if (vide(m.service())) {
                    erreurs.add(new ErrorResponse.FieldError(p + "service", "Le service d'un agent de l'entité contractante est attendu."));
                }
            } else if (CaoMembre.EXPERT_OBJET.equals(m.origine())) {
                experts++;
                if (vide(m.domaine())) {
                    erreurs.add(new ErrorResponse.FieldError(p + "domaine", "Le domaine d'expertise d'un expert de l'objet est attendu."));
                }
            } else {
                erreurs.add(new ErrorResponse.FieldError(p + "origine",
                        "Un membre a une origine : ENTITE_CONTRACTANTE (agent de l'autorité contractante) ou EXPERT_OBJET."));
            }
            if (m.id() != null && membreRepository.findById(m.id()).filter(x -> idDmc.equals(x.getIdDmc())).isEmpty()) {
                erreurs.add(new ErrorResponse.FieldError(p + "id", "Membre inconnu dans cette commission : " + m.id() + "."));
            }
        }
        if (membres < 2) {
            erreurs.add(new ErrorResponse.FieldError("membres", "Au moins deux membres sont attendus."));
        }
        if (experts > 1) {
            erreurs.add(new ErrorResponse.FieldError("membres", "Un expert de l'objet suffit : la commission n'en compte qu'un."));
        }
        if (presidents != 1) {
            erreurs.add(new ErrorResponse.FieldError("membres", presidents == 0 ? "Un président est à désigner parmi les membres."
                    : "Un seul président est attendu."));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        exclusions(idDmc, adresses);

        List<CaoMembre> existants = membreRepository.findByIdDmcOrderByRangAscIdMembreAsc(idDmc);
        List<String> avantRefs = refs(existants);
        Cao cao = caoRepository.findById(idDmc).orElseGet(() -> {
            Cao n = new Cao();
            n.setIdDmc(idDmc);
            return n;
        });
        cao.setDecisionReference(c.decision().reference().trim());
        cao.setDecisionDate(c.decision().date());
        cao.setDateMaj(LocalDateTime.now());
        cao.setIdPrmpMaj(CurrentUser.ref().orElse(null));
        caoRepository.save(cao);

        // Les comptes : chaque MEMBRE a le sien, créé s'il manque.
        Map<String, CompteCao> comptesParEmail = new LinkedHashMap<>();
        List<CaoMembre> apres = new ArrayList<>();
        Set<Long> conserves = new LinkedHashSet<>();
        for (int i = 0; i < saisis.size(); i++) {
            CaoDto.MembreCorps m = saisis.get(i);
            CaoMembre e = m.id() == null ? new CaoMembre() : membreRepository.findById(m.id()).orElseThrow();
            e.setIdDmc(idDmc);
            e.setRang(i);
            e.setNom(m.nom().trim());
            e.setPrenom(m.prenom().trim());
            e.setEmail(CandidatService.normaliserEmail(m.email()));
            e.setTelephone(vide(m.telephone()) ? null : m.telephone().trim());
            e.setQualite(CaoMembre.MEMBRE);   // ⚠️ 2026-10-04 : tout membre détient une part
            boolean membre = true;
            e.setOrigine(m.origine());
            e.setFonction(vide(m.fonction()) ? null : m.fonction().trim());
            e.setService(vide(m.service()) ? null : m.service().trim());
            e.setOrganisme(vide(m.organisme()) ? null : m.organisme().trim());
            e.setDomaine(vide(m.domaine()) ? null : m.domaine().trim());
            e.setPresident(membre && Boolean.TRUE.equals(m.president()));
            if (membre) {
                CompteCao compte = comptes.creerOuRetrouver(e.getEmail(), e.getNom(), e.getPrenom());
                comptesParEmail.put(e.getEmail(), compte);
                e.setIdCompte(compte.getIdCompte());
            } else {
                e.setIdCompte(null);
            }
            apres.add(e);
            if (e.getIdMembre() != null) {
                conserves.add(e.getIdMembre());
            }
        }
        List<String> apresRefs = refs(apres);
        CeremonieCles ceremonie = ceremonieRepository.findById(idDmc).orElse(null);
        if (ceremonie != null && CeremonieCles.CLOSE.equals(ceremonie.getEtat()) && !new LinkedHashSet<>(avantRefs).equals(new LinkedHashSet<>(apresRefs))) {
            throw new BusinessRuleException("La cérémonie des clés est close : les membres détenteurs d'une part ne se modifient plus. "
                    + "Le responsable de la procédure doit rouvrir la cérémonie d'abord.", "CEREMONIE_CLOSE");
        }
        for (CaoMembre e : existants) {
            if (!conserves.contains(e.getIdMembre())) {
                membreRepository.delete(e);
            }
        }
        membreRepository.flush();
        membreRepository.saveAll(apres);

        // Journal dédié (Q7) et invitations.
        if (!avantRefs.equals(apresRefs)) {
            internes.journaliser(idDmc, ParametresInternesService.CHAMP_MEMBRES, avantRefs.isEmpty() ? null : String.join(",", avantRefs),
                    apresRefs.isEmpty() ? null : String.join(",", apresRefs));
        }
        String[] procedure = procedureEtAutorite(idDmc);
        for (CaoMembre e : apres) {
            if (!e.estMembre() || avantRefs.contains(e.getIdCompte())) {
                continue;
            }
            CompteCao compte = comptesParEmail.get(e.getEmail());
            if (!CompteCao.ACTIF.equals(compte.getEtat())) {
                comptes.inviter(compte, procedure[0], procedure[1]);
            }
            internes.notifierMembre(idDmc, compte.getIdCompte(), TypeNotification.CLE_A_PUBLIER,
                    "Commission d'appel d'offres : vous êtes désigné membre",
                    "Vous êtes désigné membre de la commission d'appel d'offres de la procédure « " + procedure[0] + " » (" + procedure[1]
                            + "), détenteur d'une part de clé. Générez votre clé dans votre navigateur et publiez-la avant la cérémonie.");
        }
        return dto(idDmc);
    }

    /** Le PDF de la décision signée (PRMP) : 400 si ce n'est pas un PDF, 404 sans CAO, 413 au-delà de 10 Mo. */
    public CaoDto decision(Long idDmc, MultipartFile fichier) {
        exigerPrmp();
        fiches.controlerLecture(idDmc);
        Cao cao = caoRepository.findById(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune commission d'appel d'offres n'est désignée pour le DMC " + idDmc + "."));
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Le fichier de la décision est attendu.", "FICHIER_ABSENT");
        }
        if (fichier.getSize() > TAILLE_MAX_DECISION) {
            throw new PayloadTropVolumineuxException("La décision dépasse 10 Mo.");
        }
        byte[] octets;
        try {
            octets = fichier.getBytes();
        } catch (java.io.IOException e) {
            throw new BadRequestException("Le fichier ne se lit pas.", "FICHIER_ILLISIBLE");
        }
        if (octets.length < 5 || octets[0] != 0x25 || octets[1] != 0x50 || octets[2] != 0x44 || octets[3] != 0x46) {
            throw new BadRequestException("La décision de nomination attend un PDF.", "FORMAT_INVALIDE");
        }
        cao.setDecisionFichier(octets);
        cao.setDecisionTaille((long) octets.length);
        cao.setDecisionNomFichier(fichier.getOriginalFilename() == null ? "decision.pdf" : fichier.getOriginalFilename());
        cao.setDateMaj(LocalDateTime.now());
        cao.setIdPrmpMaj(CurrentUser.ref().orElse(null));
        caoRepository.save(cao);
        return dto(idDmc);
    }

    /** Renvoie l'invitation d'un membre (PRMP) : le code précédent ne vaut plus ; 409 {@code COMPTE_ACTIF}. */
    public CaoDto.Membre inviter(Long idDmc, Long idMembre) {
        exigerPrmp();
        fiches.controlerLecture(idDmc);
        CaoMembre m = membreRepository.findById(idMembre).filter(x -> idDmc.equals(x.getIdDmc()) && x.estMembre() && x.getIdCompte() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Membre détenteur de part introuvable : " + idMembre + "."));
        CompteCao compte = compteCaoRepository.findById(m.getIdCompte())
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable : " + m.getIdCompte() + "."));
        String[] procedure = procedureEtAutorite(idDmc);
        comptes.inviter(compte, procedure[0], procedure[1]);
        return membre(m, Map.of(compte.getIdCompte(), compte));
    }

    // ------------------------------------------------------------------ §B2 l'espace du membre

    /** Les CAO où le membre connecté siège, les plus récentes d'abord. */
    @Transactional(readOnly = true)
    public List<CaoDto.MaProcedure> mesProcedures() {
        String ref = CurrentUser.ref().orElseThrow(() -> new AccessDeniedException("Compte de membre introuvable."));
        List<CaoDto.MaProcedure> out = new ArrayList<>();
        for (CaoMembre m : membreRepository.findByIdCompteOrderByIdDmcDesc(ref)) {
            if (!m.estMembre()) {
                continue;
            }
            // ⚠️ 2026-10-10 — un DMC sans fiche sort sans référence ni date limite ; la lecture ne lève plus (le 404 rattrapé marquait
            // la transaction pour l'annulation : toute la liste répondait 500).
            ProcedureEnLigneDto vue = procedures.vueSiPresente(m.getIdDmc()).orElse(null);
            String[] pa = vue == null ? procedureEtAutorite(m.getIdDmc()) : new String[] { vue.objet(), vue.autoriteContractante() };
            out.add(new CaoDto.MaProcedure(m.getIdDmc(), vue == null ? null : vue.reference(), pa[0], pa[1],
                    Boolean.TRUE.equals(m.getPresident()), vue == null ? null : vue.dateLimite(),
                    ceremonieRepository.findById(m.getIdDmc()).map(CeremonieCles::getEtat).orElse(CeremonieCles.A_VENIR),
                    cleRepository.findFirstByIdDmcAndRoleAndImAndDateArchivageIsNull(m.getIdDmc(), CleDetenteur.MEMBRE, ref)
                            .map(CleDetenteur::getEtatPart).orElse(CeremonieService.ABSENTE)));
        }
        return out;
    }

    /** La vue du membre connecté sur une procédure : 403 s'il n'y siège pas, 404 DMC inconnu. */
    @Transactional(readOnly = true)
    public CaoDto.VueMembre vueMembre(Long idDmc) {
        String ref = CurrentUser.ref().orElseThrow(() -> new AccessDeniedException("Compte de membre introuvable."));
        if (!dmcRepository.existsById(idDmc)) {
            throw new ResourceNotFoundException("DMC introuvable : " + idDmc);
        }
        CaoMembre moi = membreRepository.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream()
                .filter(m -> m.estMembre() && ref.equals(m.getIdCompte())).findFirst()
                .orElseThrow(() -> new AccessDeniedException("Vous ne siégez pas dans la commission d'appel d'offres de cette procédure."));
        return new CaoDto.VueMembre(procedures.vue(idDmc), Boolean.TRUE.equals(moi.getPresident()), dto(idDmc));
    }

    // ------------------------------------------------------------------ construction et gardes

    private CaoDto dto(Long idDmc) {
        Cao cao = caoRepository.findById(idDmc).orElse(null);
        List<CaoMembre> membres = membreRepository.findByIdDmcOrderByRangAscIdMembreAsc(idDmc);
        Map<String, CompteCao> comptesParId = new LinkedHashMap<>();
        for (CaoMembre m : membres) {
            if (m.getIdCompte() != null) {
                compteCaoRepository.findById(m.getIdCompte()).ifPresent(c -> comptesParId.put(c.getIdCompte(), c));
            }
        }
        List<CaoDto.Membre> liste = membres.stream().map(m -> membre(m, comptesParId)).toList();
        return new CaoDto(idDmc, cao == null ? null : new CaoDto.Decision(cao.getDecisionReference(), cao.getDecisionDate(),
                cao.getDecisionTaille() != null), liste, CaoRegles.etat(cao, membres), CaoRegles.anomalies(cao, membres, comptesParId));
    }

    private static CaoDto.Membre membre(CaoMembre m, Map<String, CompteCao> comptes) {
        CaoDto.Compte compte = null;
        if (m.estMembre()) {
            CompteCao c = m.getIdCompte() == null ? null : comptes.get(m.getIdCompte());
            String etat = c == null ? "A_INVITER" : CompteCao.ACTIF.equals(c.getEtat()) ? "ACTIF" : CompteCao.ARCHIVE.equals(c.getEtat())
                    ? "ARCHIVE" : c.getDateInvitation() == null ? "A_INVITER" : "INVITE";
            compte = new CaoDto.Compte(etat, c == null ? null : c.getIdCompte(), c == null ? null : c.getDateInvitation(),
                    c == null ? null : c.getDateActivation());
        }
        return new CaoDto.Membre(m.getIdMembre(), m.getNom(), m.getPrenom(), m.getEmail(), m.getTelephone(), m.getOrigine(),
                m.getFonction(), m.getService(), m.getOrganisme(), m.getDomaine(), Boolean.TRUE.equals(m.getPresident()), compte);
    }

    private static List<String> refs(List<CaoMembre> membres) {
        return membres.stream().filter(m -> m.estMembre() && m.getIdCompte() != null).map(CaoMembre::getIdCompte).toList();
    }

    /**
     * Les exclusions par construction (409 {@code MEMBRE_EXCLU}) : une adresse qui est celle d'un contrôleur de la CNM, de la PRMP
     * ou d'une UGPM de la fiche, d'un candidat inscrit, ou d'un compte interne. La raison est dite, pas le compte.
     */
    private void exclusions(Long idDmc, Set<String> adresses) {
        DossierMec dmc = dmcRepository.findById(idDmc).orElse(null);
        String idPrmp = dmc == null ? null : marcheRepository.findIdDossierByIdDetail(dmc.getIdDetail()).flatMap(dossierRepository::findById)
                .map(cnm.prs.entity.Dossier::getIdPrmp).orElse(null);
        String emailPrmp = idPrmp == null ? null : prmpRepository.findById(idPrmp).map(Prmp::getEmailPrmp).map(CandidatService::normaliserEmail).orElse(null);
        Set<String> emailsUgpm = new LinkedHashSet<>();
        if (idPrmp != null) {
            for (Ugpm u : ugpmRepository.findByIdPrmpTutelle(idPrmp)) {
                if (u.getEmailUgpm() != null) {
                    emailsUgpm.add(CandidatService.normaliserEmail(u.getEmailUgpm()));
                }
            }
        }
        for (String email : adresses) {
            String raison = null;
            if (controleurRepository.existsByEmailContIgnoreCase(email)) {
                raison = "un contrôleur de la CNM";
            } else if (email.equals(emailPrmp)) {
                raison = "la PRMP de la fiche";
            } else if (emailsUgpm.contains(email)) {
                raison = "une UGPM de la fiche";
            } else if (candidatRepository.existsByEmail(email)) {
                raison = "un candidat inscrit (conflit d'intérêts)";
            } else {
                CompteAuth a = compteAuthRepository.findByLogin(email).orElse(null);
                if (a != null && !TypeActeur.MEMBRE_CAO.name().equals(a.getTypeActeur())) {
                    // ⚠️ 2026-10-05 — une adresse de dépositaire : un compte n'a qu'un profil.
                    raison = TypeActeur.DEPOSITAIRE.name().equals(a.getTypeActeur()) ? "le dépositaire d'une part de secours"
                            : "un compte interne de PRS";
                }
            }
            if (raison != null) {
                throw new BusinessRuleException("L'adresse " + email + " est celle de " + raison + " : cette personne ne peut pas siéger "
                        + "dans la commission d'appel d'offres.", "MEMBRE_EXCLU");
            }
        }
    }

    /** L'objet de la procédure et l'autorité contractante, lus sur le plan (pour les courriels). */
    private String[] procedureEtAutorite(Long idDmc) {
        DossierMec dmc = dmcRepository.findById(idDmc).orElse(null);
        if (dmc == null) {
            return new String[] { "procédure " + idDmc, "" };
        }
        Map<String, String> v = valeursPpm.lire(dmc.getIdDetail()).valeurs();
        String objet = v.get("OBJET");
        String entite = v.get("ENTITE");
        return new String[] { objet == null ? "procédure " + idDmc : objet, entite == null ? "" : entite };
    }

    /** ⚠️ Arbitrages du pilote (§B4.1) : la PRMP ou l'UGPM de la fiche (l'UGPM prépare la décision) ; le journal nomme l'acteur réel. */
    private static void exigerPrmp() {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("La commission d'appel d'offres se désigne par la PRMP de la fiche (ou son UGPM).");
        }
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
