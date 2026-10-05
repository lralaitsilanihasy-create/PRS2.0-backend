package cnm.prs.service;

import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CeremonieDto;
import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.CleDetenteur;
import cnm.prs.entity.DefiCle;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.AccesReserveException;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CleDetenteurRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DefiCleRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.NotificationRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>La cérémonie des clés et la procédure de secours S1 à S4</strong> (demande front du 2026-10-04, soumission en
 * ligne, lot 2 ; ADR-0013 §1, §3, §5, §6 ; V66).
 * <ul>
 *   <li><strong>Les clés</strong> ({@code t_cle_detenteur}) : chaque membre désigné publie sa clé publique (SPKI, RSA 3072) et son
 *       empreinte, avec sa clé privée <strong>enveloppée</strong> par sa phrase secrète. Le serveur contrôle la clé et l'empreinte,
 *       garde l'enveloppe et <strong>ne déchiffre rien, ne peut rien déchiffrer</strong> ; il ne la rend qu'à son propriétaire.
 *       ⚠️ V71 (demande du 2026-10-05) — la part de secours (S3) naît désormais sur le poste du <strong>dépositaire</strong> (compte
 *       {@code DEPOSITAIRE}), qui seul voit sa phrase ; une part générée chez le responsable (ancien geste, {@code generePar =
 *       RESPONSABLE}) reste valable et se gère par lui.</li>
 *   <li><strong>La cérémonie</strong> ({@code t_ceremonie_cles}) : {@code A_VENIR} tant que les {@code n} = membres + 1 clés ne sont
 *       pas publiées ; le responsable la <strong>clôt</strong> ({@code CLOSE}, date effective) ; close, elle fige les paramètres
 *       internes et conditionne la publication de l'avis (§B2.6). Il peut la <strong>rouvrir</strong> ({@code A_REFAIRE}) tant
 *       qu'aucune offre n'est déposée (S4).</li>
 *   <li><strong>S2</strong> : un défi — 32 octets chiffrés avec la clé publique, le détenteur les déchiffre dans son navigateur
 *       et renvoie le clair ; le serveur, qui n'a gardé que le SHA-256, compare en temps constant. Rien n'est révélé.</li>
 *   <li><strong>S4</strong> : remplacer sa clé — avant le premier dépôt, l'ancienne est supprimée ; après, archivée : des offres
 *       en dépendent.</li>
 *   <li><strong>Journal</strong> dédié (empreintes, jamais une clé ni une enveloppe) et notifications (§B6).</li>
 * </ul>
 * Qui lit : le responsable et les membres désignés — chacun doit retrouver <em>sa</em> empreinte dans la liste publiée.
 */
@Service
@Transactional
public class CeremonieService {

    static final String CLE_PUBLIEE = "clePubliee";
    static final String CLE_REMPLACEE = "cleRemplacee";
    static final String CLE_PERDUE = "clePerdue";
    static final String DEFI_REUSSI = "defiReussi";
    static final String DEFI_ECHOUE = "defiEchoue";
    static final String CEREMONIE_CLOSE = "ceremonieClose";
    static final String CEREMONIE_ROUVERTE = "ceremonieRouverte";

    static final String ABSENTE = "ABSENTE";
    static final int ITERATIONS_MIN = 600_000;
    static final int DEFI_MINUTES = 5;
    static final String KDF = "PBKDF2-SHA-256";
    static final String ALGORITHME_ENVELOPPE = "AES-256-GCM";
    public static final List<String> ALGORITHMES = List.of(ALGORITHME_ENVELOPPE, ClesRsa.ALGORITHME_RSA, "SHAMIR-GF256");

    private final CeremonieClesRepository ceremonieRepository;
    private final CleDetenteurRepository cleRepository;
    private final DefiCleRepository defiRepository;
    private final ParametresInternesService internes;
    private final ControleurRepository controleurRepository;
    private final DossierMecRepository dmcRepository;
    private final MarcheRepository marcheRepository;
    private final DossierRepository dossierRepository;
    private final PrmpRepository prmpRepository;
    private final NotificationService notifications;
    private final NotificationRepository notificationRepository;
    private final FicheMarcheService fiches;
    private final ProceduresEnLigneService procedures;
    private final ParametreService parametres;

    public CeremonieService(CeremonieClesRepository ceremonieRepository, CleDetenteurRepository cleRepository,
            DefiCleRepository defiRepository, ParametresInternesService internes, ControleurRepository controleurRepository,
            DossierMecRepository dmcRepository, MarcheRepository marcheRepository, DossierRepository dossierRepository,
            PrmpRepository prmpRepository, NotificationService notifications, NotificationRepository notificationRepository,
            FicheMarcheService fiches, ProceduresEnLigneService procedures, ParametreService parametres) {
        this.ceremonieRepository = ceremonieRepository;
        this.cleRepository = cleRepository;
        this.defiRepository = defiRepository;
        this.internes = internes;
        this.controleurRepository = controleurRepository;
        this.dmcRepository = dmcRepository;
        this.marcheRepository = marcheRepository;
        this.dossierRepository = dossierRepository;
        this.prmpRepository = prmpRepository;
        this.notifications = notifications;
        this.notificationRepository = notificationRepository;
        this.fiches = fiches;
        this.procedures = procedures;
        this.parametres = parametres;
    }

    // ------------------------------------------------------------------ ce que la fiche et l'avis en disent

    /** L'état servi sur la fiche : {@code null} en mode papier, {@code A_VENIR} sans ligne, sinon l'état enregistré. */
    @Transactional(readOnly = true)
    public String etatPourFiche(Long idDmc, boolean electronique) {
        if (!electronique) {
            return null;
        }
        return ceremonieRepository.findById(idDmc).map(CeremonieCles::getEtat).orElse(CeremonieCles.A_VENIR);
    }

    /**
     * ⚠️ V68 (lot 3, §B4) — la première offre scellée pose {@code premierDepot} : plus de réouverture, la CAO figée, une clé
     * remplacée est désormais archivée (S4). Sans effet si déjà posé.
     */
    public void poserPremierDepot(Long idDmc) {
        CeremonieCles c = ceremonie(idDmc);
        if (!Boolean.TRUE.equals(c.getPremierDepot())) {
            c.setPremierDepot(true);
            c.setDateMaj(LocalDateTime.now());
            ceremonieRepository.save(c);
        }
    }

    /** ⚠️ §B2.6 — la cérémonie est-elle close ? (ce que la publication de l'avis exige en mode électronique). */
    @Transactional(readOnly = true)
    public boolean estClose(Long idDmc) {
        return ceremonieRepository.findById(idDmc).map(c -> CeremonieCles.CLOSE.equals(c.getEtat())).orElse(false);
    }

    // ------------------------------------------------------------------ §B2.1 lecture

    /** La cérémonie, pour le responsable et les membres désignés (403 autrement, Administrateur et PRMP compris). */
    @Transactional(readOnly = true)
    public CeremonieDto lire(Long idDmc) {
        exigerLecteur(idDmc);
        return dto(idDmc);
    }

    // ------------------------------------------------------------------ §B2.2, §B2.3, §B5.1 publier, remplacer, relire

    /**
     * Publie une clé (201) : un membre pour lui-même ({@code role} nul ou {@code MEMBRE}), le responsable pour la part de
     * secours ({@code SECOURS}, 409 {@code DEPOSITAIRE_ABSENT} sans dépositaire). 409 {@code CEREMONIE_CLOSE} (remplacer passe par
     * §B5.1), 409 {@code CLE_EXISTANTE}.
     */
    public CeremonieDto.Detenteur publier(Long idDmc, String role, CeremonieDto.CleCorps corps) {
        Cible cible = cible(idDmc, role, true);
        CeremonieCles ceremonie = ceremonie(idDmc);
        if (CeremonieCles.CLOSE.equals(ceremonie.getEtat())) {
            throw new BusinessRuleException("La cérémonie des clés est close : une clé ne se publie plus, elle se remplace.", "CEREMONIE_CLOSE");
        }
        active(idDmc, cible).ifPresent(c -> {
            throw new BusinessRuleException("Une clé est déjà publiée pour " + cible.libelle() + " (empreinte " + c.getEmpreinte()
                    + ") : pour en changer, remplacez-la.", "CLE_EXISTANTE");
        });
        CleDetenteur c = enregistrer(idDmc, cible, corps, 0);
        internes.journaliser(idDmc, CLE_PUBLIEE, null, cible.role() + " " + c.getEmpreinte());
        return detenteurDe(idDmc, cible, c);
    }

    /**
     * ⚠️ S4 — remplace une clé, cérémonie close ou non. Avant le premier dépôt l'ancienne est supprimée ; après, archivée
     * (des offres scellées pour son empreinte en dépendent). La part repasse à {@code PUBLIEE}, la vérification est effacée.
     */
    public CeremonieDto.Detenteur remplacer(Long idDmc, String role, CeremonieDto.CleCorps corps) {
        Cible cible = cible(idDmc, role, true);
        CeremonieCles ceremonie = ceremonie(idDmc);
        CleDetenteur ancienne = active(idDmc, cible).orElse(null);
        int remplacements = ancienne == null ? 0 : ancienne.getRemplacements() + 1;
        if (ancienne != null) {
            if (Boolean.TRUE.equals(ceremonie.getPremierDepot())) {
                ancienne.setDateArchivage(LocalDateTime.now());
                cleRepository.save(ancienne);
            } else {
                cleRepository.delete(ancienne);
            }
            cleRepository.flush();   // l'index d'unicité de la clé active
        }
        CleDetenteur c = enregistrer(idDmc, cible, corps, remplacements);
        internes.journaliser(idDmc, CLE_REMPLACEE, ancienne == null ? null : cible.role() + " " + ancienne.getEmpreinte(),
                cible.role() + " " + c.getEmpreinte());
        return detenteurDe(idDmc, cible, c);
    }

    /** L'enveloppe de sa propre clé ({@code /mienne}), ou celle de la part de secours pour le responsable ({@code /secours}). 404 sans clé. */
    @Transactional(readOnly = true)
    public CeremonieDto.Enveloppe enveloppe(Long idDmc, String role) {
        Cible cible = cible(idDmc, role, false);
        CleDetenteur c = active(idDmc, cible)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune clé publiée pour " + cible.libelle() + "."));
        return new CeremonieDto.Enveloppe(c.getEnvChiffre(), c.getEnvIv(), c.getEnvSel(), c.getEnvIterations(), c.getEnvKdf(),
                c.getEnvAlgorithme());
    }

    // ------------------------------------------------------------------ §B2.4, §B5.2 clore, rouvrir

    /** Clôt la cérémonie (responsable) : 409 {@code CLES_INCOMPLETES} tant que les {@code n} clés ne sont pas publiées (les manquants sont nommés). */
    public CeremonieDto cloturer(Long idDmc) {
        exigerResponsable(idDmc);
        CeremonieCles ceremonie = ceremonie(idDmc);
        RemiseElectronique.Internes i = internes.internes(idDmc);
        List<String> manquants = new ArrayList<>();
        if (i == null || i.membres().size() < 2 || i.quorum() == null) {
            throw new BusinessRuleException("Les paramètres internes de la procédure (membres, quorum) ne sont pas complets : la "
                    + "cérémonie ne peut pas être close.", "CLES_INCOMPLETES");
        }
        List<CleDetenteur> actives = cleRepository.findByIdDmcAndDateArchivageIsNullOrderByIdCleAsc(idDmc);
        for (String im : i.membres()) {
            if (actives.stream().noneMatch(c -> CleDetenteur.MEMBRE.equals(c.getRole()) && im.equalsIgnoreCase(c.getIm())
                    && !CleDetenteur.PERDUE.equals(c.getEtatPart()))) {
                manquants.add(nomMembre(im));
            }
        }
        if (actives.stream().noneMatch(c -> CleDetenteur.SECOURS.equals(c.getRole()) && !CleDetenteur.PERDUE.equals(c.getEtatPart()))) {
            manquants.add("la part de secours");
        }
        if (!manquants.isEmpty()) {
            throw new BusinessRuleException("Clés manquantes : " + String.join(", ", manquants) + ". La cérémonie se clôt quand les "
                    + (i.membres().size() + 1) + " clés sont publiées.", "CLES_INCOMPLETES");
        }
        ceremonie.setEtat(CeremonieCles.CLOSE);
        ceremonie.setDateCloture(LocalDateTime.now());
        ceremonie.setDateMaj(LocalDateTime.now());
        ceremonieRepository.save(ceremonie);
        internes.journaliser(idDmc, CEREMONIE_CLOSE, null, empreintes(actives));
        String titre = "Cérémonie des clés close : clés publiées";
        String corps = "La cérémonie des clés de la procédure " + idDmc + " est close : les " + actives.size()
                + " clés publiques sont publiées aux candidats. Vérifiez que votre empreinte figure dans la liste.";
        for (String im : i.membres()) {
            internes.notifierMembre(idDmc, im, TypeNotification.CLES_PUBLIEES, titre, corps);
        }
        notifierPrmp(idDmc, TypeNotification.CLES_PUBLIEES, titre, "La cérémonie des clés de la procédure " + idDmc
                + " est close : l'avis spécifique peut être publié.");
        CeremonieDto dto = dto(idDmc);
        if (!dto.avertissements().isEmpty()) {
            notifierResponsable(idDmc, TypeNotification.MARGE_QUORUM, "Marge du quorum épuisée", RemiseElectronique.MESSAGE_MARGE_EPUISEE);
        }
        return dto;
    }

    /**
     * ⚠️ S4 — rouvre la cérémonie (responsable) : toutes les parts repassent à {@code ABSENTE}, les paramètres internes
     * redeviennent modifiables, chaque membre republie. 409 {@code DEPOT_EXISTANT} dès la première offre scellée.
     */
    public CeremonieDto rouvrir(Long idDmc) {
        exigerResponsable(idDmc);
        CeremonieCles ceremonie = ceremonie(idDmc);
        if (Boolean.TRUE.equals(ceremonie.getPremierDepot())) {
            throw new BusinessRuleException("Des offres ont déjà été déposées pour cette procédure : la cérémonie ne se refait pas. "
                    + "Une part perdue reste perdue ; la marge du quorum et la part de secours tiennent.", "DEPOT_EXISTANT");
        }
        List<CleDetenteur> actives = cleRepository.findByIdDmcAndDateArchivageIsNullOrderByIdCleAsc(idDmc);
        String avant = actives.isEmpty() ? "aucune clé" : empreintes(actives);
        cleRepository.deleteAll(actives);
        ceremonie.setEtat(CeremonieCles.A_REFAIRE);
        ceremonie.setDateCloture(null);
        ceremonie.setDateMaj(LocalDateTime.now());
        ceremonieRepository.save(ceremonie);
        internes.journaliser(idDmc, CEREMONIE_ROUVERTE, avant, null);
        RemiseElectronique.Internes i = internes.internes(idDmc);
        if (i != null) {
            for (String im : i.membres()) {
                internes.notifierMembre(idDmc, im, TypeNotification.CLE_A_PUBLIER, "Cérémonie des clés à refaire : votre clé est à publier",
                        "La cérémonie des clés de la procédure " + idDmc + " est rouverte : générez une nouvelle clé et publiez-la.");
            }
        }
        internes.notifierDepositaire(idDmc, TypeNotification.CLE_A_PUBLIER, "Cérémonie des clés à refaire : votre clé de secours est à "
                + "publier", "La cérémonie des clés de la procédure " + idDmc + " est rouverte : générez une nouvelle clé de secours et "
                + "publiez-la.");
        return dto(idDmc);
    }

    // ------------------------------------------------------------------ §B2.5 les clés publiées aux candidats

    /** Public : 404 tant que la cérémonie n'est pas close, ou hors des critères des procédures en ligne. Sans matricule ni nom. */
    @Transactional(readOnly = true)
    public CeremonieDto.ClesPubliques clesPubliques(Long idDmc) {
        procedures.procedure(idDmc);
        CeremonieCles ceremonie = ceremonieRepository.findById(idDmc).filter(c -> CeremonieCles.CLOSE.equals(c.getEtat()))
                .orElseThrow(() -> new ResourceNotFoundException("La cérémonie des clés de la procédure " + idDmc + " n'est pas close."));
        RemiseElectronique.Internes i = internes.internes(idDmc);
        List<CeremonieDto.ClePubliee> cles = cleRepository.findByIdDmcAndDateArchivageIsNullOrderByIdCleAsc(idDmc).stream()
                .map(c -> new CeremonieDto.ClePubliee(c.getRole(), c.getEmpreinte(), c.getClePublique())).toList();
        return new CeremonieDto.ClesPubliques(idDmc, i == null ? null : i.quorum(), (i == null ? 0 : i.membres().size()) + 1,
                ALGORITHMES, ceremonie.getDateCloture(), cles);
    }

    // ------------------------------------------------------------------ §B4 S2 : le défi, la part perdue

    /** Ouvre un défi (201) sur sa clé (membre) ou sur la part de secours (responsable). 409 {@code CLE_ABSENTE}. */
    public CeremonieDto.Defi defi(Long idDmc, String role) {
        Cible cible = cible(idDmc, role, false);
        CleDetenteur c = active(idDmc, cible).orElseThrow(() -> new BusinessRuleException("Aucune clé publiée pour "
                + cible.libelle() + " : rien à vérifier.", "CLE_ABSENTE"));
        RSAPublicKey cle = ClesRsa.lire(c.getClePublique());
        if (cle == null) {
            throw new IllegalStateException("La clé publiée ne se relit pas : " + c.getEmpreinte());
        }
        ClesRsa.Defi d = ClesRsa.defi(cle);
        DefiCle defi = new DefiCle();
        defi.setIdDmc(idDmc);
        defi.setIdCle(c.getIdCle());
        defi.setImAppelant(CurrentUser.ref().orElse(null));
        defi.setEmpreinteClair(ClesRsa.sha256Hex(d.clair()));
        defi.setDateCreation(LocalDateTime.now());
        defi.setExpire(LocalDateTime.now().plusMinutes(DEFI_MINUTES));
        defiRepository.save(defi);
        return new CeremonieDto.Defi(defi.getIdDefi(), d.chiffre(), defi.getExpire());
    }

    /**
     * Répond au défi : le clair en base64, comparé en temps constant. Réussi : {@code VERIFIEE}, journal ; échoué : 409
     * {@code DEFI_ECHOUE} (journal, la part ne change pas) ; 409 {@code DEFI_EXPIRE}. Un défi ne sert qu'une fois.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public CeremonieDto.Detenteur repondre(Long idDmc, Long idDefi, CeremonieDto.ReponseDefi reponse) {
        DefiCle defi = defiRepository.findById(idDefi).filter(d -> idDmc.equals(d.getIdDmc()))
                .orElseThrow(() -> new ResourceNotFoundException("Défi introuvable : " + idDefi + "."));
        CleDetenteur c = cleRepository.findById(defi.getIdCle())
                .orElseThrow(() -> new ResourceNotFoundException("Défi introuvable : " + idDefi + "."));
        Cible cible = cible(idDmc, c.getRole(), false);
        if (!Objects.equals(cible.im(), c.getIm())) {
            throw new AccessDeniedException("Ce défi ne porte pas sur votre clé.");
        }
        if (Boolean.TRUE.equals(defi.getConsomme()) || LocalDateTime.now().isAfter(defi.getExpire())) {
            defi.setConsomme(true);
            defiRepository.save(defi);
            throw new BusinessRuleException("Le défi a expiré (" + DEFI_MINUTES + " minutes, usage unique) : ouvrez-en un autre.", "DEFI_EXPIRE");
        }
        defi.setConsomme(true);
        defiRepository.save(defi);
        boolean ok = reponse != null && ClesRsa.repond(defi.getEmpreinteClair(), reponse.clair());
        if (!ok) {
            internes.journaliser(idDmc, DEFI_ECHOUE, null, cible.role() + " " + c.getEmpreinte());
            throw new BusinessRuleException("La réponse ne correspond pas au défi : la clé déverrouillée n'est pas celle publiée, ou "
                    + "la phrase secrète est erronée.", "DEFI_ECHOUE");
        }
        c.setEtatPart(CleDetenteur.VERIFIEE);
        c.setDerniereVerification(LocalDateTime.now());
        cleRepository.save(c);
        internes.journaliser(idDmc, DEFI_REUSSI, null, cible.role() + " " + c.getEmpreinte());
        return detenteurDe(idDmc, cible, c);
    }

    /** Déclare sa part perdue (membre), ou la part de secours (responsable) : {@code PERDUE}, journal, responsable notifié. */
    public CeremonieDto.Detenteur perdue(Long idDmc, String role) {
        Cible cible = cible(idDmc, role, false);
        CleDetenteur c = active(idDmc, cible).orElseThrow(() -> new BusinessRuleException("Aucune clé publiée pour "
                + cible.libelle() + " : rien à déclarer perdu.", "CLE_ABSENTE"));
        c.setEtatPart(CleDetenteur.PERDUE);
        cleRepository.save(c);
        internes.journaliser(idDmc, CLE_PERDUE, cible.role() + " " + c.getEmpreinte(), null);
        notifierResponsable(idDmc, TypeNotification.PART_PERDUE, "Part de clé déclarée perdue",
                "La part de " + cible.libelle() + " (empreinte " + c.getEmpreinte() + ") est déclarée perdue pour la procédure " + idDmc
                        + ". Remplacez la clé, ou refaites la cérémonie si aucune offre n'est déposée.");
        CeremonieDto dto = dto(idDmc);
        if (!dto.avertissements().isEmpty()) {
            notifierResponsable(idDmc, TypeNotification.MARGE_QUORUM, "Marge du quorum épuisée", RemiseElectronique.MESSAGE_MARGE_EPUISEE);
        }
        return detenteurDe(idDmc, cible, c);
    }

    /**
     * ⚠️ §B4 — le rappel de nuit : {@code FICHE_SE_VERIFICATION_PART_JOURS} jours avant la date limite de remise, chaque membre
     * dont la part n'est pas vérifiée depuis la clôture reçoit {@code PART_A_VERIFIER}, une fois par clôture.
     *
     * @return le nombre de rappels émis
     */
    public int rappelerVerifications() {
        Integer jours = parametres.remiseElectronique().verificationPartJours();
        int j = jours == null ? 7 : jours;
        LocalDateTime maintenant = LocalDateTime.now();
        int emis = 0;
        for (CeremonieCles ceremonie : ceremonieRepository.findByEtat(CeremonieCles.CLOSE)) {
            Long idDmc = ceremonie.getIdDmc();
            LocalDateTime limite;
            try {
                limite = fiches.etatValide(idDmc).map(ProceduresEnLigneService::dateLimite).orElse(null);
            } catch (ResourceNotFoundException | BusinessRuleException e) {
                continue;
            }
            if (limite == null || maintenant.isAfter(limite) || maintenant.isBefore(limite.minusDays(j))) {
                continue;
            }
            RemiseElectronique.Internes i = internes.internes(idDmc);
            if (i == null) {
                continue;
            }
            for (String im : i.membres()) {
                CleDetenteur c = cleRepository.findFirstByIdDmcAndRoleAndImAndDateArchivageIsNull(idDmc, CleDetenteur.MEMBRE, im).orElse(null);
                boolean verifiee = c != null && CleDetenteur.VERIFIEE.equals(c.getEtatPart()) && c.getDerniereVerification() != null
                        && ceremonie.getDateCloture() != null && !c.getDerniereVerification().isBefore(ceremonie.getDateCloture());
                if (verifiee || notificationRepository.existsByTypeNotifAndDestinataireRefAndTypeObjetAndIdObjetAndDateEnvoiGreaterThanEqual(
                        TypeNotification.PART_A_VERIFIER.name(), im, TypeObjet.PROCEDURE.name(), idDmc.intValue(),
                        ceremonie.getDateCloture() == null ? maintenant.minusYears(1) : ceremonie.getDateCloture())) {
                    continue;
                }
                internes.notifierMembre(idDmc, im, TypeNotification.PART_A_VERIFIER, "Vérifiez votre part de clé",
                        "La date limite de remise des offres de la procédure " + idDmc + " approche (" + RemiseElectronique.isoMinute(limite)
                                + ") : vérifiez que votre phrase secrète déverrouille bien votre clé (défi).");
                emis++;
            }
        }
        return emis;
    }

    // ------------------------------------------------------------------ construction

    private CeremonieDto dto(Long idDmc) {
        CeremonieCles ceremonie = ceremonieRepository.findById(idDmc).orElse(null);
        RemiseElectronique.Internes i = internes.internes(idDmc);
        List<String> membres = i == null ? List.of() : i.membres();
        List<CleDetenteur> actives = cleRepository.findByIdDmcAndDateArchivageIsNullOrderByIdCleAsc(idDmc);
        List<CeremonieDto.Detenteur> detenteurs = new ArrayList<>();
        int disponibles = 0;
        for (String im : membres) {
            CleDetenteur c = actives.stream().filter(x -> CleDetenteur.MEMBRE.equals(x.getRole()) && im.equalsIgnoreCase(x.getIm()))
                    .findFirst().orElse(null);
            detenteurs.add(detenteur(CleDetenteur.MEMBRE, im, c));
            if (c != null && !CleDetenteur.PERDUE.equals(c.getEtatPart())) {
                disponibles++;
            }
        }
        CleDetenteur secours = actives.stream().filter(x -> CleDetenteur.SECOURS.equals(x.getRole())).findFirst().orElse(null);
        detenteurs.add(detenteur(CleDetenteur.SECOURS, null, secours, i == null || i.depositaire() == null ? null : i.depositaire().nom()));
        String etat = ceremonie == null ? CeremonieCles.A_VENIR : ceremonie.getEtat();
        List<CeremonieDto.Avertissement> avertissements = new ArrayList<>();
        if (CeremonieCles.CLOSE.equals(etat) && i != null && i.quorum() != null && disponibles <= i.quorum()) {
            avertissements.add(new CeremonieDto.Avertissement(ControlesFicheMarche.SE_MARGE_EPUISEE, RemiseElectronique.MESSAGE_MARGE_EPUISEE));
        }
        return new CeremonieDto(idDmc, etat, i == null ? null : i.dateCeremonie(), ceremonie == null ? null : ceremonie.getDateCloture(),
                i == null ? null : i.quorum(), membres.size() + 1, ceremonie != null && Boolean.TRUE.equals(ceremonie.getPremierDepot()),
                detenteurs, avertissements);
    }


    /** Le détenteur visé par un geste, son nom résolu : celui du membre, ou celui du dépositaire pour la part de secours. */
    private CeremonieDto.Detenteur detenteurDe(Long idDmc, Cible cible, CleDetenteur c) {
        if (CleDetenteur.SECOURS.equals(cible.role())) {
            RemiseElectronique.Internes i = internes.internes(idDmc);
            return detenteur(cible.role(), null, c, i == null || i.depositaire() == null ? null : i.depositaire().nom());
        }
        return detenteur(cible.role(), cible.im(), c);
    }
    private CeremonieDto.Detenteur detenteur(String role, String im, CleDetenteur c) {
        return detenteur(role, im, c, im == null ? null : nomMembre(im));
    }

    private static CeremonieDto.Detenteur detenteur(String role, String im, CleDetenteur c, String nom) {
        if (c == null) {
            return new CeremonieDto.Detenteur(role, im, nom, null, null, null, ABSENTE, null, 0, null);
        }
        return new CeremonieDto.Detenteur(role, im, nom, c.getEmpreinte(), c.getClePublique(), c.getDatePublication(), c.getEtatPart(),
                c.getDerniereVerification(), c.getRemplacements(),
                CleDetenteur.SECOURS.equals(role) ? (c.getGenerePar() == null ? CleDetenteur.PAR_RESPONSABLE : c.getGenerePar()) : null);
    }

    /** ⚠️ V67 (lot 2a) — un membre de CAO ({@code K…}) ou, à défaut, un contrôleur. */
    private String nomMembre(String im) {
        return internes.nomMembre(im);
    }

    private static String empreintes(List<CleDetenteur> cles) {
        return String.join(", ", cles.stream().map(c -> c.getRole() + " " + c.getEmpreinte()).toList());
    }

    /** La ligne de la cérémonie, créée {@code A_VENIR} au premier geste. */
    private CeremonieCles ceremonie(Long idDmc) {
        return ceremonieRepository.findById(idDmc).orElseGet(() -> {
            CeremonieCles c = new CeremonieCles(idDmc, CeremonieCles.A_VENIR, null, false, LocalDateTime.now(), null, null, null);
            return ceremonieRepository.save(c);
        });
    }

    /** Contrôle le corps (400 à code) et enregistre la clé, {@code PUBLIEE}. */
    private CleDetenteur enregistrer(Long idDmc, Cible cible, CeremonieDto.CleCorps corps, int remplacements) {
        if (corps == null || corps.clePublique() == null || corps.enveloppe() == null) {
            throw new BadRequestException("La clé publique, son empreinte et l'enveloppe de la clé privée sont attendues.", "CLE_INVALIDE");
        }
        if (ClesRsa.lire(corps.clePublique()) == null) {
            throw new BadRequestException("La clé publique ne se lit pas : une clé RSA de " + ClesRsa.MODULE_BITS
                    + " bits, en forme SPKI base64, est attendue.", "CLE_INVALIDE");
        }
        String empreinte = ClesRsa.empreinte(corps.clePublique());
        if (corps.empreinte() == null || !empreinte.equalsIgnoreCase(corps.empreinte().trim())) {
            throw new BadRequestException("L'empreinte annoncée (" + corps.empreinte() + ") n'est pas celle de la clé publique ("
                    + empreinte + ").", "EMPREINTE_INVALIDE");
        }
        CeremonieDto.Enveloppe e = corps.enveloppe();
        if (ClesRsa.decoder(e.chiffre()) == null || ClesRsa.decoder(e.iv()) == null || ClesRsa.decoder(e.sel()) == null) {
            throw new BadRequestException("L'enveloppe attend chiffre, iv et sel en base64.", "ENVELOPPE_INVALIDE");
        }
        if (e.iterations() == null || e.iterations() < ITERATIONS_MIN) {
            throw new BadRequestException("La dérivation de la clé d'enveloppe attend au moins " + ITERATIONS_MIN + " itérations.",
                    "ENVELOPPE_INVALIDE");
        }
        if (!KDF.equalsIgnoreCase(e.kdf()) || !ALGORITHME_ENVELOPPE.equalsIgnoreCase(e.algorithme())) {
            throw new BadRequestException("L'enveloppe attend kdf = " + KDF + " et algorithme = " + ALGORITHME_ENVELOPPE + ".",
                    "ENVELOPPE_INVALIDE");
        }
        CleDetenteur c = new CleDetenteur();
        c.setIdDmc(idDmc);
        c.setRole(cible.role());
        c.setIm(cible.im());
        c.setClePublique(corps.clePublique().trim());
        c.setEmpreinte(empreinte);
        c.setEnvChiffre(e.chiffre().trim());
        c.setEnvIv(e.iv().trim());
        c.setEnvSel(e.sel().trim());
        c.setEnvIterations(e.iterations());
        c.setEnvKdf(KDF);
        c.setEnvAlgorithme(ALGORITHME_ENVELOPPE);
        c.setEtatPart(CleDetenteur.PUBLIEE);
        c.setDatePublication(LocalDateTime.now());
        c.setRemplacements(remplacements);
        if (CleDetenteur.SECOURS.equals(cible.role())) {   // ⚠️ V71 (§B2) : la clé naît chez le dépositaire
            c.setGenerePar(CleDetenteur.PAR_DEPOSITAIRE);
            c.setIdDepositaire(cible.depositaire());
        }
        return cleRepository.save(c);
    }

    // ------------------------------------------------------------------ gardes

    /**
     * Qui est visé par un geste : un membre pour sa propre clé, ou la part de secours ; ⚠️ V71 — {@code depositaire} = le compte
     * {@code D…} de l'appelant quand c'est le dépositaire qui agit (nouveau geste).
     */
    private record Cible(String role, String im, String depositaire) {
        String libelle() {
            return CleDetenteur.SECOURS.equals(role) ? "la part de secours" : "le membre " + im;
        }
    }

    /**
     * La cible d'un geste et sa garde. ⚠️ V71 (demande du 05/10, §B2, §B4) — la part de secours :
     * <ul>
     *   <li>la <strong>publier</strong> ou la <strong>remplacer</strong> ({@code ecriture}) : le dépositaire désigné seul ; le
     *       responsable reçoit 403 {@code GESTE_DU_DEPOSITAIRE} ;</li>
     *   <li>relire son enveloppe, ouvrir un défi, la déclarer perdue : son <strong>détenteur</strong> — le responsable pour une
     *       part générée selon l'ancien geste ({@code generePar = RESPONSABLE}), le dépositaire qui l'a publiée sinon.</li>
     * </ul>
     */
    private Cible cible(Long idDmc, String role, boolean ecriture) {
        exigerDmc(idDmc);
        String acteur = CurrentUser.ref().orElse(null);
        if (acteur == null || CurrentUser.profil().isEmpty()) {
            throw new AccessDeniedException("La cérémonie des clés est réservée au responsable de la procédure et aux membres désignés.");
        }
        if (CleDetenteur.SECOURS.equalsIgnoreCase(role)) {
            boolean responsable = internes.estTitulaire(idDmc);
            boolean depositaire = internes.estDepositaire(idDmc);
            if (!responsable && !depositaire) {
                throw new AccessDeniedException("La part de secours se gère par son dépositaire.");
            }
            if (ecriture) {
                if (!depositaire) {
                    throw new AccesReserveException("La clé de secours naît sur le poste du dépositaire : lui seul la génère et la publie, "
                            + "depuis son espace.", "GESTE_DU_DEPOSITAIRE");
                }
                return new Cible(CleDetenteur.SECOURS, null, acteur);
            }
            CleDetenteur active = cleRepository.findFirstByIdDmcAndRoleAndDateArchivageIsNull(idDmc, CleDetenteur.SECOURS).orElse(null);
            boolean ancienGeste = active != null && !CleDetenteur.PAR_DEPOSITAIRE.equals(active.getGenerePar());
            boolean detenteur = ancienGeste ? responsable
                    : depositaire && (active == null || acteur.equals(active.getIdDepositaire()));
            if (!detenteur) {
                throw new AccessDeniedException(ancienGeste ? "Cette part de secours a été générée chez le responsable de la procédure : "
                        + "elle se gère par lui." : "La part de secours se gère par le dépositaire qui l'a publiée.");
            }
            return new Cible(CleDetenteur.SECOURS, null, depositaire && !ancienGeste ? acteur : null);
        }
        String im = membre(idDmc, acteur);
        if (im == null) {
            throw new AccessDeniedException("Vous n'êtes pas membre désigné de la commission de cette procédure.");
        }
        return new Cible(CleDetenteur.MEMBRE, im, null);
    }

    /** Le matricule tel que les paramètres internes l'écrivent, si l'acteur est un membre désigné ; {@code null} sinon. */
    private String membre(Long idDmc, String acteur) {
        RemiseElectronique.Internes i = internes.internes(idDmc);
        if (i == null) {
            return null;
        }
        return i.membres().stream().filter(m -> PredicatsIdentite.estResponsableProcedure(acteur, m)).findFirst().orElse(null);
    }

    private void exigerLecteur(Long idDmc) {
        exigerDmc(idDmc);
        String acteur = CurrentUser.ref().orElse(null);
        if (acteur == null || CurrentUser.profil().isEmpty() || !internes.estTitulaire(idDmc) && membre(idDmc, acteur) == null
                && !internes.estDepositaire(idDmc)) {   // ⚠️ V71 : le dépositaire lit la cérémonie
            throw new AccessDeniedException("La cérémonie des clés de la procédure " + idDmc
                    + " se lit par son responsable, ses membres désignés et le dépositaire de la part de secours.");
        }
    }

    private void exigerResponsable(Long idDmc) {
        exigerDmc(idDmc);
        if (CurrentUser.profil().isEmpty() || !internes.estTitulaire(idDmc)) {
            throw new AccessDeniedException("La cérémonie des clés se clôt et se rouvre par le responsable de la procédure.");
        }
    }

    private void exigerDmc(Long idDmc) {
        if (idDmc == null || !dmcRepository.existsById(idDmc)) {
            throw new ResourceNotFoundException("DMC introuvable : " + idDmc);
        }
    }

    private Optional<CleDetenteur> active(Long idDmc, Cible cible) {
        return CleDetenteur.SECOURS.equals(cible.role())
                ? cleRepository.findFirstByIdDmcAndRoleAndDateArchivageIsNull(idDmc, CleDetenteur.SECOURS)
                : cleRepository.findFirstByIdDmcAndRoleAndImAndDateArchivageIsNull(idDmc, CleDetenteur.MEMBRE, cible.im());
    }

    // ------------------------------------------------------------------ notifications

    void notifierResponsable(Long idDmc, TypeNotification type, String titre, String corps) {
        internes.responsable(idDmc).ifPresent(r -> internes.notifierMembre(idDmc, r.getImResponsable(), type, titre, corps));
    }

    /** Vers la PRMP du plan de la ligne du DMC, si elle se retrouve. */
    void notifierPrmp(Long idDmc, TypeNotification type, String titre, String corps) {
        DossierMec dmc = dmcRepository.findById(idDmc).orElse(null);
        if (dmc == null) {
            return;
        }
        String idPrmp = marcheRepository.findIdDossierByIdDetail(dmc.getIdDetail()).flatMap(dossierRepository::findById)
                .map(cnm.prs.entity.Dossier::getIdPrmp).orElse(null);
        if (idPrmp == null) {
            return;
        }
        String email = prmpRepository.findById(idPrmp).map(Prmp::getEmailPrmp).orElse(null);
        notifications.emettrePrmp(type, idPrmp, email, idDmc.intValue(), TypeObjet.PROCEDURE, null, titre, corps);
    }
}
