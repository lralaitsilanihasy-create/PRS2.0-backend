package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ChronometrageDto;
import cnm.prs.dto.TacheDossierDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.SuspensionDossier;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.NiveauNavette;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutPv;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.SuspensionDossierRepository;
import cnm.prs.repository.TacheDossierRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PermissionService;

/**
 * ⚠️ <strong>Chronométrage et prévision des délais</strong> (règle du pilote, 2026-09-01).
 *
 * <p>Trois responsabilités : <strong>ouvrir</strong> une tâche (prise en charge explicite, arbitrage ①),
 * la <strong>clore</strong> depuis le geste métier existant, et <strong>calculer</strong> la date
 * prévisionnelle de fin que la PRMP consulte.</p>
 *
 * <p><strong>Le chronométrage n'empêche jamais le métier.</strong> C'est la règle qui gouverne tout ce
 * service : la clôture est <em>tolérante</em> (un geste posé sans prise en charge préalable crée
 * l'occurrence avec une durée nulle plutôt que d'échouer), et aucune exception levée ici ne fait tomber
 * une transaction métier. Un chronomètre qui bloque un dossier serait pire que pas de chronomètre.</p>
 *
 * <p><strong>Deux sources, pour deux usages.</strong> Le drapeau {@code attentePrmp} exposé à la PRMP est
 * dérivé du <strong>statut courant</strong> du dossier ; le cumul des attentes du compteur net vient de
 * {@code t_suspension_dossier}. Ainsi une fenêtre non enregistrée fausserait un cumul, jamais
 * l'affichage — la donnée la plus visible s'appuie sur la source la plus fiable.</p>
 */
@Service
@Transactional
public class ChronometrageService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ChronometrageService.class);

    /**
     * ⚠️ <strong>Cartographie des statuts suspensifs</strong> (arbitrage ④), validée le 2026-09-01 —
     * associée à l'étape qui <strong>reprendra</strong> quand la PRMP rendra la main.
     *
     * <p>Trois statuts, et trois seulement. La « rectification des documents témoins » évoquée par la
     * spec n'en est pas un quatrième : c'est exactement la période {@code EN_ATTENTE_DECISION_PRMP},
     * pendant laquelle la PRMP corrige puis resoumet.</p>
     *
     * <p>Connaître l'étape de reprise n'est pas un luxe : sans elle, un dossier en attente après des
     * observations non levées compterait la vérification comme franchie, alors qu'elle sera
     * <strong>rejouée</strong> — la date annoncée serait trop optimiste d'une vérification entière.</p>
     */
    private static final Map<String, EtapeCircuit> REPRISE_APRES_ATTENTE = Map.of(
            StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name(), EtapeCircuit.RECEPTION,
            StatutDossier.EN_ATTENTE_PIECES.name(), EtapeCircuit.EXAMEN,
            StatutDossier.EN_ATTENTE_DECISION_PRMP.name(), EtapeCircuit.VERIFICATION);

    private final TacheDossierRepository tacheRepository;
    private final SuspensionDossierRepository suspensionRepository;
    private final DelaiStandardService delaiStandardService;
    private final DossierRepository dossierRepository;
    private final PvExamenRepository pvExamenRepository;
    private final ControleurRepository controleurRepository;
    private final PermissionService permissionService;
    /** ⚠️ 2026-09-04 — l'attributaire courant de l'examen : la seule étape nominativement attribuée. */
    private final cnm.prs.repository.DispatchRepository dispatchRepository;
    /** ⚠️ 2026-09-04 — source unique du circuit : la garde du VISA lit le meme discriminant que la navette. */
    private final CircuitDossierService circuitService;
    private final ControleurDirectory controleurDirectory;

    public ChronometrageService(TacheDossierRepository tacheRepository,
            SuspensionDossierRepository suspensionRepository, DelaiStandardService delaiStandardService,
            DossierRepository dossierRepository, PvExamenRepository pvExamenRepository,
            ControleurRepository controleurRepository, PermissionService permissionService,
            cnm.prs.repository.DispatchRepository dispatchRepository,
            CircuitDossierService circuitService, ControleurDirectory controleurDirectory) {
        this.tacheRepository = tacheRepository;
        this.suspensionRepository = suspensionRepository;
        this.delaiStandardService = delaiStandardService;
        this.dossierRepository = dossierRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.controleurRepository = controleurRepository;
        this.permissionService = permissionService;
        this.dispatchRepository = dispatchRepository;
        this.circuitService = circuitService;
        this.controleurDirectory = controleurDirectory;
    }

    // ------------------------------------------------------------------ étape courante

    /**
     * Étape ouverte d'un dossier, déduite de son statut et de celui de son PV. {@code null} quand aucune
     * tâche CNM n'est en cours : brouillon, attente PRMP, dossier clos, retiré ou remplacé.
     */
    public EtapeCircuit etapeCourante(Dossier dossier) {
        if (dossier == null || dossier.getStatut() == null) {
            return null;
        }
        return etapeCourante(dossier.getStatut(), () -> statutPvDe(dossier.getIdDossier()));
    }

    /**
     * Même résolution, le statut du PV étant fourni par l'appelant — utilisé par l'enrichissement en
     * lot, où les statuts de PV sont chargés en une seule requête pour toute la liste.
     */
    private EtapeCircuit etapeCourante(String statut, java.util.function.Supplier<String> statutPv) {
        if (StatutDossier.SOUMIS.name().equals(statut)) {
            return EtapeCircuit.RECEPTION;
        }
        if (StatutDossier.PRET_DISPATCH.name().equals(statut)) {
            return EtapeCircuit.DISPATCH;
        }
        if (StatutDossier.DISPATCHE.name().equals(statut) || StatutDossier.A_REEXAMINER.name().equals(statut)) {
            return EtapeCircuit.EXAMEN;
        }
        if (StatutDossier.EXAMINE.name().equals(statut)) {
            return etapeSelonPv(statutPv.get());
        }
        if (StatutDossier.EN_VERIFICATION.name().equals(statut)) {
            return EtapeCircuit.VERIFICATION;
        }
        if (StatutDossier.OBSERVATIONS_LEVEES.name().equals(statut)) {
            return EtapeCircuit.TRANSMISSION_SIGMP;
        }
        if (StatutDossier.DECISION_TRANSMISE_SIGMP.name().equals(statut)) {
            return EtapeCircuit.ARCHIVAGE;
        }
        return null;
    }

    /**
     * Sous-étape d'un dossier {@code EXAMINE} : le statut du dossier ne suffit pas, il couvre trois
     * moments distincts du circuit. C'est le statut du PV qui tranche.
     */
    private EtapeCircuit etapeSelonPv(String statutPv) {
        if (statutPv == null || StatutPv.BROUILLON.name().equals(statutPv)
                || StatutPv.EN_RECTIFICATION.name().equals(statutPv)) {
            return EtapeCircuit.EXAMEN;
        }
        if (StatutPv.PROJET_SOUMIS.name().equals(statutPv)) {
            return EtapeCircuit.VISA;
        }
        if (StatutPv.PROJET_ACCEPTE.name().equals(statutPv)) {
            return EtapeCircuit.COSIGNATURE;
        }
        return null;
    }

    private String statutPvDe(Integer idDossier) {
        return pvExamenRepository.statutsPvParDossier(idDossier).stream()
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ prise en charge

    /**
     * Prise en charge <strong>explicite</strong> de l'étape courante (arbitrage ①), avec la prévision du
     * porteur. Rejouée sur une tâche encore ouverte, elle <strong>corrige</strong> la prévision au lieu
     * de créer une occurrence : corriger son estimation n'est pas recommencer sa tâche.
     *
     * <p>⚠️ <strong>Le replay ne vaut que pour le MÊME acteur</strong> (constat de recette du
     * 2026-09-04). Il ne vérifiait pas qui appelait : un second acteur recevait 200 et
     * <em>corrigeait la prévision du premier</em>. Vécu en recette — le CC avait pris l'examen, et
     * l'assignataire se retrouvait sans recours : son propre appel « réussissait » en modifiant la
     * tâche du CC. Un acteur différent reçoit désormais <strong>409 nominal</strong> : il faut savoir
     * à qui parler pour débloquer.</p>
     *
     * <p><strong>Sauf pour les étapes à plusieurs porteurs</strong> ({@link EtapeCircuit#plusieursPorteurs()},
     * c'est-à-dire la co-signature) : le CC désigné et le Membre désigné y tiennent chacun leur tâche,
     * sans ordre imposé. Y appliquer le 409 ferait verrouiller le second par le premier — l'autre
     * moitié du même constat.</p>
     *
     * @throws BusinessRuleException si aucune étape n'est ouverte, ou si un AUTRE acteur tient déjà
     *         celle-ci (409)
     */
    public TacheDossierDto prendreEnCharge(Integer idDossier, Integer previsionHeures) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        EtapeCircuit etape = etapeCourante(dossier);
        if (etape == null) {
            throw new BusinessRuleException("Aucune étape n'est en cours sur ce dossier (statut « "
                    + dossier.getStatut() + " ») : rien à prendre en charge.");
        }
        exigerPorteurEligible(dossier, etape);
        exigerActeurAttendu(idDossier, etape);

        String moi = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        List<TacheDossier> ouvertes = tacheRepository.ouvertes(idDossier, etape.name());
        TacheDossier mienne = ouvertes.stream()
                .filter(t -> moi != null && moi.equals(t.getImActeur())).findFirst().orElse(null);
        if (mienne != null) {
            mienne.setPrevisionHeures(previsionHeures);
            mienne.setPrevisionStandard(Boolean.FALSE);
            TacheDossier maj = tacheRepository.save(mienne);
            return TacheDossierDto.de(maj, nom(maj.getImActeur()));
        }
        if (!ouvertes.isEmpty() && !etape.plusieursPorteurs()) {
            String tenant = ouvertes.get(0).getImActeur();
            throw new BusinessRuleException("Étape déjà prise en charge par " + nomOuMatricule(tenant)
                    + " : une étape est tenue par une personne à la fois. Faites-la lui clore, ou "
                    + "demandez-lui de vous la transmettre.");
        }
        TacheDossier tache = tacheRepository.save(
                nouvelle(idDossier, etape, LocalDateTime.now(), previsionHeures, false));
        return TacheDossierDto.de(tache, nom(tache.getImActeur()));
    }


    /**
     * Garde de la prise en charge : <strong>profil effectif</strong> (délégations et intérim résolus par
     * la garde centrale) et <strong>périmètre</strong> du dossier.
     *
     * <p>Volontairement plus légère que le geste métier de l'étape, qui conserve sa garde intacte : une
     * prise en charge indue n'altère aucune donnée métier, elle ne fait que démarrer un chronomètre.
     * Rejouer ici chacune des huit gardes métier aurait multiplié les endroits où vivent les règles
     * d'habilitation, pour un gain de sécurité nul.</p>
     */
    private void exigerPorteurEligible(Dossier dossier, EtapeCircuit etape) {
        if (!permissionService.peutExercer(etape.porteur())) {
            throw new AccessDeniedException(
                    "Cette étape (" + etape.name() + ") revient au profil " + etape.porteur().name() + ".");
        }
        String localiteActeur = CurrentUser.localite().orElse(null);
        if (!CurrentUser.voitToutesLocalites() && localiteActeur != null
                && dossier.getIdLocalite() != null && !localiteActeur.equals(dossier.getIdLocalite())) {
            throw new AccessDeniedException("Ce dossier n'est pas de votre localité.");
        }
    }

    /**
     * ⚠️ <strong>Les acteurs que la prise en charge accepte</strong> pour l'étape courante (spec pilote
     * du 2026-09-04) — liste FERMÉE de matricules, ou {@code null} quand elle ne peut pas l'être.
     *
     * <p><strong>Pourquoi une liste plutôt qu'un booléen.</strong> La même valeur sert deux fois : la
     * garde s'en sert pour refuser, le DTO du chronométrage l'expose pour que le front masque le geste
     * à quiconque n'y figure pas. Les dériver séparément aurait permis de masquer un bouton que le
     * serveur accepte, ou d'en offrir un qu'il refuse — l'écart ne se voyant qu'en recette.</p>
     *
     * <p><strong>{@code null} n'est pas « personne », c'est « pas de liste close ».</strong> Sur une
     * navette simple, le visa admet le dispatcheur <em>et</em> tout P/CC du périmètre par intérim :
     * l'ensemble n'est pas énumérable. On ne garde alors rien de plus que le profil et la localité, et
     * le front replie sur la règle du porteur nominal. Une liste vide aurait dit « personne » et
     * bloqué tout le monde.</p>
     */
    @Transactional(readOnly = true)
    public List<String> acteursAttendus(Integer idDossier, EtapeCircuit etape) {
        if (idDossier == null || etape == null) {
            return null;
        }
        return switch (etape) {
            case EXAMEN -> unSeul(dispatchRepository.findImCtrlMembreByDossier(idDossier)
                    .filter(s -> !s.isBlank()).orElse(null));
            case VISA -> acteursDuVisa(idDossier);
            case COSIGNATURE -> acteursDeLaCoSignature(idDossier);
            // Les autres étapes n'ont pas de titulaire nominatif : profil et localité suffisent.
            default -> null;
        };
    }

    /**
     * Acteurs du VISA — l'étage de la navette décide.
     *
     * <p>Deux niveaux : au niveau {@code CC}, le seul CC dispatcheur ; au niveau {@code PRESIDENT},
     * les Présidents. Navette simple : {@code null}, l'intérim ouvrant la porte à tout P/CC du
     * périmètre (cf. {@code PvExamenService#viser}) — c'est le cas non énumérable.</p>
     *
     * <p>Un PV en navette <strong>sans niveau</strong> — soumis avant la livraison du 2026-09-04 —
     * retombe aussi sur {@code null} : on ne durcit pas rétroactivement un dossier en cours.</p>
     */
    private List<String> acteursDuVisa(Integer idDossier) {
        CircuitDossierService.Circuit circuit = circuitService.parDossier(idDossier);
        if (!circuitService.deuxNiveaux(circuit)) {
            return null;
        }
        String niveau = pvExamenRepository.niveauxNavetteParDossier(idDossier).stream()
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (NiveauNavette.CC.name().equals(niveau)) {
            return unSeul(circuit.dispatcheur());
        }
        if (NiveauNavette.PRESIDENT.name().equals(niveau)) {
            List<String> presidents = controleurDirectory.presidents().stream()
                    .map(Controleur::getImControleur).filter(java.util.Objects::nonNull).toList();
            return presidents.isEmpty() ? null : presidents;
        }
        return null;
    }

    /**
     * Acteurs de la CO-SIGNATURE — les <strong>désignés</strong> du visa, et eux seuls : chacun ouvre
     * SA tâche (l'étape admet plusieurs porteurs depuis le 2026-09-04). Aucun désigné lisible → pas de
     * liste close, donc pas de garde nominative.
     */
    private List<String> acteursDeLaCoSignature(Integer idDossier) {
        List<String> designes = pvExamenRepository.coSignatairesParDossier(idDossier).stream()
                .flatMap(r -> java.util.stream.Stream.of((String) r[0], (String) r[1]))
                .filter(java.util.Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList();
        return designes.isEmpty() ? null : designes;
    }

    private List<String> unSeul(String im) {
        return im == null || im.isBlank() ? null : List.of(im);
    }

    /**
     * ⚠️ <strong>Garde nominative de la prise en charge</strong> (2026-09-04) — 403 quand l'étape a des
     * titulaires identifiables et que l'appelant n'en est pas.
     *
     * <p>Elle remplace la garde d'EXAMEN posée le matin même, dont elle est la généralisation. Le
     * constat qui l'impose (dossier 100286) : le CC, ayant transmis le PV au Président, a re-cliqué
     * « Prendre en charge » — le serveur a ouvert à son nom l'occurrence VISA qui revenait au
     * Président, qui s'est retrouvé verrouillé sans recours dans l'UI. La mécanique par niveaux du
     * 2026-09-04 fermait bien l'occurrence du CC ; rien ne gardait la <strong>création</strong> de la
     * suivante.</p>
     *
     * <p>403 et non 409 : ce n'est pas l'étape qui n'est pas prête, c'est l'appelant qui n'est pas
     * celui qu'on attend.</p>
     */
    private void exigerActeurAttendu(Integer idDossier, EtapeCircuit etape) {
        List<String> attendus = acteursAttendus(idDossier, etape);
        if (attendus == null || attendus.isEmpty()) {
            return;   // pas de liste close : profil et localité restent les seules gardes
        }
        String moi = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (moi != null && attendus.contains(moi)) {
            return;
        }
        String noms = attendus.stream().map(this::nomOuMatricule).collect(Collectors.joining(", "));
        throw new AccessDeniedException(switch (etape) {
            case EXAMEN -> "L'examen de ce dossier est attribué à " + noms
                    + " : lui seul peut le prendre en charge, même par délégation.";
            case VISA -> "Le visa de ce dossier revient à " + noms
                    + " : sur un dossier à deux niveaux, chaque étage a son acteur et sa tâche.";
            case COSIGNATURE -> "La co-signature de ce PV revient aux désignés du visa (" + noms
                    + ") : chacun ouvre et clôt SA part.";
            default -> "Cette étape revient à " + noms + ".";
        });
    }

    // ------------------------------------------------------------------ clôture (gestes métier)

    /**
     * Clôt la tâche ouverte d'une étape — appelée depuis le <strong>geste métier</strong> de clôture.
     *
     * <p><strong>Tolérante par construction</strong> : sans prise en charge préalable, l'occurrence est
     * créée avec {@code priseEnCharge = fin} (durée nulle) et la prévision <em>standard</em> du
     * référentiel. Ne lève jamais : une anomalie de chronométrage est journalisée, elle ne fait pas
     * échouer la transaction métier qui l'appelle.</p>
     */
    public void cloturer(Integer idDossier, EtapeCircuit etape) {
        if (idDossier == null || etape == null) {
            return;
        }
        try {
            LocalDateTime maintenant = LocalDateTime.now();
            TacheDossier tache = tacheRepository
                    .findFirstByIdDossierAndEtapeAndDateFinIsNull(idDossier, etape.name())
                    .orElseGet(() -> nouvelle(idDossier, etape, maintenant,
                            delaiStandardService.delai(etape), true));
            tache.setDateFin(maintenant);
            tacheRepository.save(tache);
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] cloture impossible dossier={} etape={} : {}", idDossier, etape, ex.toString());
        }
    }

    /**
     * ⚠️ <strong>Clôt la tâche d'UN acteur donné</strong> (2026-09-04) — variante nécessaire aux étapes
     * à plusieurs porteurs, où « la » tâche ouverte n'existe pas : la co-signature en compte une par
     * désigné, et chaque signature ne doit clore que la sienne.
     *
     * <p>Sans elle, la première signature fermait la tâche que {@code findFirst} rendait — souvent
     * celle de l'autre —, et le PV se terminait avec une tâche ouverte au nom de quelqu'un qui avait
     * pourtant signé. Même tolérance que {@link #cloturer} : sans prise en charge préalable, une
     * occurrence est créée sur l'acteur puis close aussitôt.</p>
     */
    public void cloturerPourActeur(Integer idDossier, EtapeCircuit etape, String imActeur) {
        if (idDossier == null || etape == null) {
            return;
        }
        try {
            LocalDateTime maintenant = LocalDateTime.now();
            TacheDossier tache = tacheRepository.ouvertes(idDossier, etape.name()).stream()
                    .filter(t -> imActeur != null && imActeur.equals(t.getImActeur()))
                    .findFirst()
                    .orElseGet(() -> nouvelle(idDossier, etape, maintenant,
                            delaiStandardService.delai(etape), true));
            tache.setDateFin(maintenant);
            tacheRepository.save(tache);
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] cloture impossible dossier={} etape={} acteur={} : {}",
                    idDossier, etape, imActeur, ex.toString());
        }
    }

    /** Construit une occurrence neuve, de rang suivant, sur l'acteur courant. */
    private TacheDossier nouvelle(Integer idDossier, EtapeCircuit etape, LocalDateTime priseEnCharge,
            Integer prevision, boolean standard) {
        TacheDossier tache = new TacheDossier();
        tache.setIdTache(tacheRepository.nextId());
        tache.setIdDossier(idDossier);
        tache.setEtape(etape.name());
        Integer rang = tacheRepository.dernierRang(idDossier, etape.name());
        tache.setOccurrence((rang == null ? 0 : rang) + 1);
        tache.setImActeur(CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null));
        tache.setProfil(CurrentUser.profil().map(ProfilUtilisateur::name).orElse(etape.porteur().name()));
        tache.setDatePriseEnCharge(priseEnCharge);
        tache.setPrevisionHeures(prevision == null ? 1 : prevision);
        tache.setPrevisionStandard(standard);
        return tache;
    }

    // ------------------------------------------------------------------ suspensions PRMP

    /** Ouvre une fenêtre d'attente PRMP. Sans effet si une fenêtre est déjà ouverte (idempotent). */
    public void entrerEnAttentePrmp(Integer idDossier, StatutDossier statut) {
        if (idDossier == null || statut == null) {
            return;
        }
        try {
            if (suspensionRepository.findFirstByIdDossierAndFinIsNullOrderByDebutDesc(idDossier).isPresent()) {
                return;
            }
            SuspensionDossier suspension = new SuspensionDossier();
            suspension.setIdSuspension(suspensionRepository.nextId());
            suspension.setIdDossier(idDossier);
            suspension.setStatut(statut.name());
            suspension.setDebut(LocalDateTime.now());
            suspensionRepository.save(suspension);
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] ouverture d'attente PRMP impossible dossier={} : {}", idDossier, ex.toString());
        }
    }

    /** Ferme la fenêtre d'attente ouverte, s'il y en a une. */
    public void sortirDAttentePrmp(Integer idDossier) {
        if (idDossier == null) {
            return;
        }
        try {
            suspensionRepository.findFirstByIdDossierAndFinIsNullOrderByDebutDesc(idDossier).ifPresent(s -> {
                s.setFin(LocalDateTime.now());
                suspensionRepository.save(s);
            });
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] fermeture d'attente PRMP impossible dossier={} : {}", idDossier, ex.toString());
        }
    }

    /** Vrai si le dossier est dans un statut où la balle est chez la PRMP. */
    public static boolean estEnAttentePrmp(String statut) {
        return statut != null && REPRISE_APRES_ATTENTE.containsKey(statut);
    }

    // ------------------------------------------------------------------ calcul de la date prévisionnelle

    /**
     * Date prévisionnelle de fin de traitement, en jours <strong>ouvrés</strong> :
     * {@code aujourd'hui + reste(étape en cours) + Σ prévisions des étapes restantes} jusqu'à la
     * transmission SIGMP incluse. {@code null} si le dossier n'est pas dans le circuit.
     *
     * <p>⚠️ <strong>Unité : l'HEURE ouvrée</strong> depuis le 2026-09-02. La somme se fait en heures,
     * puis se convertit en jours par tranche de 8 h, <strong>arrondie au supérieur</strong> : une journée
     * entamée compte pleine. {@code datePrevisionnelleFin} reste une date — la seule rescapée de la
     * bascule d'unité.</p>
     *
     * <p>⚠️ L'écoulé est mesuré jusqu'à <strong>{@code maintenant}</strong>, et non jusqu'au début du
     * jour : en heures, s'arrêter à minuit sous-compterait la journée en cours et rendrait la date
     * optimiste de huit heures au pire. L'instant est un paramètre pour que le calcul reste déterministe
     * en test.</p>
     *
     * <p><strong>Une étape en dépassement compte 0</strong> : la date glisse jour après jour au lieu de
     * mentir sur un rattrapage qui n'aura pas lieu. Les étapes non encore prises en charge comptent pour
     * leur délai standard, ce qui permet d'annoncer une date dès la soumission.</p>
     */
    public LocalDate datePrevisionnelleFin(String statut, String statutPv, List<TacheDossier> taches,
            LocalDateTime maintenant) {
        return datePrevisionnelleFin(statut, statutPv, taches, maintenant, delaiStandardService.delais());
    }

    /**
     * Même calcul, le référentiel étant fourni <strong>déjà chargé</strong> — indispensable pour les
     * listes : relire les délais par étape et par dossier chargeait assez d'entités pour faire tomber le
     * contrat de pagination (lot D §3).
     */
    public LocalDate datePrevisionnelleFin(String statut, String statutPv, List<TacheDossier> taches,
            LocalDateTime maintenant, Map<EtapeCircuit, Integer> delais) {
        EtapeCircuit reference = etapeCourante(statut, () -> statutPv);
        if (reference == null) {
            reference = REPRISE_APRES_ATTENTE.get(statut);
        }
        if (reference == null) {
            return null;
        }
        Map<EtapeCircuit, TacheDossier> ouvertes = new HashMap<>();
        Set<EtapeCircuit> closes = new HashSet<>();
        for (TacheDossier t : taches == null ? List.<TacheDossier>of() : taches) {
            EtapeCircuit etape = etapeDe(t);
            if (etape == null) {
                continue;
            }
            if (t.enCours()) {
                ouvertes.put(etape, t);
            } else {
                closes.add(etape);
            }
        }
        long totalHeures = 0L;
        for (EtapeCircuit etape : EtapeCircuit.etapesDuCompteur()) {
            if (etape.ordinal() < reference.ordinal()) {
                continue;   // franchie : l'étape de référence fait foi, y compris après un retour en arrière
            }
            TacheDossier ouverte = ouvertes.get(etape);
            if (ouverte != null) {
                // Ecoule et prevision dans la MEME echelle (8 h par jour ouvre) : tout l enjeu du point 5
                // de la spec. Mesure en heures d horloge, une tache prise en charge la veille serait en
                // depassement de 16 h alors qu un seul jour de travail a passe.
                long ecoulees = HeuresOuvrees.ecoulees(ouverte.getDatePriseEnCharge(), maintenant);
                totalHeures += Math.max(0L, ouverte.getPrevisionHeures() - ecoulees);
            } else {
                totalHeures += delais.getOrDefault(etape, HeuresOuvrees.HEURES_PAR_JOUR);
            }
        }
        return JoursOuvres.ajouter(maintenant.toLocalDate(), HeuresOuvrees.enJoursArrondiSuperieur(totalHeures));
    }

    private static EtapeCircuit etapeDe(TacheDossier tache) {
        try {
            return EtapeCircuit.valueOf(tache.getEtape());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ restitution

    /** Chronométrage complet d'un dossier : occurrences + compteurs globaux. */
    @Transactional(readOnly = true)
    public ChronometrageDto chronometrage(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        List<TacheDossier> taches = tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(idDossier);
        List<SuspensionDossier> suspensions = suspensionRepository.findByIdDossierOrderByDebutAsc(idDossier);

        Map<String, String> noms = new HashMap<>();
        for (TacheDossier t : taches) {
            if (t.getImActeur() != null) {
                noms.computeIfAbsent(t.getImActeur(), this::nom);
            }
        }
        List<TacheDossierDto> occurrences = taches.stream()
                .map(t -> TacheDossierDto.de(t, noms.get(t.getImActeur()))).toList();

        LocalDateTime debut = borne(taches, EtapeCircuit.RECEPTION);
        LocalDateTime fin = borne(taches, EtapeCircuit.TRANSMISSION_SIGMP);
        LocalDateTime jusqua = fin != null ? fin : LocalDateTime.now();
        long brut = debut == null ? 0L : HeuresOuvrees.ecoulees(debut, jusqua);
        long attentes = cumulAttentes(suspensions, jusqua);
        String statutPv = statutPvDe(idDossier);
        EtapeCircuit courante = etapeCourante(dossier.getStatut(), () -> statutPv);

        return new ChronometrageDto(idDossier, occurrences, debut, fin,
                brut, Math.max(0L, brut - attentes), attentes,
                courante == null ? null : courante.name(),
                estEnAttentePrmp(dossier.getStatut()),
                datePrevisionnelleFin(dossier.getStatut(), statutPv, taches, LocalDateTime.now()),
                // ⚠️ 2026-09-04 — l attributaire courant, EXACTEMENT la valeur sur laquelle porte la
                // garde de la prise en charge d EXAMEN (exigerAttributaireSiExamen) : la meme requete,
                // donc la meme reponse. Servir une derivation voisine aurait permis au front de
                // masquer un bouton que le serveur aurait accepte, ou l inverse.
                dispatchRepository.findImCtrlMembreByDossier(idDossier)
                        .filter(s -> !s.isBlank()).orElse(null),
                // ⚠️ 2026-09-04 — la MEME liste que celle sur laquelle porte la garde : le front masque
                // le bouton a quiconque n y figure pas, et le serveur refusera exactement les memes.
                acteursAttendus(idDossier, courante));
    }

    /** Fin de la dernière occurrence close d'une étape — borne du compteur global. */
    private LocalDateTime borne(List<TacheDossier> taches, EtapeCircuit etape) {
        return taches.stream()
                .filter(t -> etape.name().equals(t.getEtape()) && t.getDateFin() != null)
                .map(TacheDossier::getDateFin)
                .max(LocalDateTime::compareTo).orElse(null);
    }

    /** Cumul en <strong>heures ouvrées</strong> des attentes PRMP ; une fenêtre ouverte court jusqu’à {@code jusqua}. */
    private long cumulAttentes(List<SuspensionDossier> suspensions, LocalDateTime jusqua) {
        long totalHeures = 0L;
        for (SuspensionDossier s : suspensions) {
            totalHeures += HeuresOuvrees.ecoulees(s.getDebut(), s.getFin() != null ? s.getFin() : jusqua);
        }
        return totalHeures;
    }

    /** « Prénoms nom » d'un contrôleur ; {@code null} si le matricule est inconnu. */
    private String nom(String imActeur) {
        if (imActeur == null) {
            return null;
        }
        return controleurRepository.findById(imActeur).map(ChronometrageService::nomComplet).orElse(null);
    }

    /**
     * Nom lisible, <strong>repli sur le matricule</strong>. Les messages du 2026-09-04 nomment la
     * personne qui bloque : « déjà prise en charge par null » n'aiderait personne à savoir à qui parler.
     */
    private String nomOuMatricule(String imActeur) {
        String n = nom(imActeur);
        return n == null || n.isBlank() ? imActeur : n;
    }

    private static String nomComplet(Controleur c) {
        String prenoms = c.getPrenomsCont() == null ? "" : c.getPrenomsCont().trim();
        String nom = c.getNomCont() == null ? "" : c.getNomCont().trim();
        String complet = (prenoms + " " + nom).trim();
        return complet.isEmpty() ? null : complet;
    }

    // ------------------------------------------------------------------ enrichissement en lot

    /** Tâches de plusieurs dossiers, groupées — une seule requête pour toute une liste. */
    @Transactional(readOnly = true)
    public Map<Integer, List<TacheDossier>> tachesParDossier(Collection<Integer> idsDossiers) {
        Map<Integer, List<TacheDossier>> parDossier = new HashMap<>();
        if (idsDossiers == null || idsDossiers.isEmpty()) {
            return parDossier;
        }
        for (TacheDossier t : tacheRepository.findParDossiers(idsDossiers)) {
            parDossier.computeIfAbsent(t.getIdDossier(), k -> new ArrayList<>()).add(t);
        }
        return parDossier;
    }

    /**
     * Statut du PV le plus récent, par dossier — une seule requête. La liste étant ordonnée par
     * {@code idPv} croissant, la dernière valeur écrite pour un dossier est bien la plus récente.
     */
    @Transactional(readOnly = true)
    public Map<Integer, String> statutsPvParDossier(Collection<Integer> idsDossiers) {
        Map<Integer, String> parDossier = new HashMap<>();
        if (idsDossiers == null || idsDossiers.isEmpty()) {
            return parDossier;
        }
        for (Object[] ligne : pvExamenRepository.statutsPvParDossiers(idsDossiers)) {
            if (ligne.length >= 2 && ligne[0] != null && ligne[1] != null) {
                parDossier.put((Integer) ligne[0], (String) ligne[1]);
            }
        }
        return parDossier;
    }

    /** Étape courante d'un dossier dont le statut de PV est déjà connu (enrichissement en lot). */
    public EtapeCircuit etapeCourante(String statut, String statutPv) {
        return etapeCourante(statut, () -> statutPv);
    }
}
