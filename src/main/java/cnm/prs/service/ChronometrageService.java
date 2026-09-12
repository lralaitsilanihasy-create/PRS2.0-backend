package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ChronometrageDto;
import cnm.prs.dto.PassageEtapeDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.SuspensionDossier;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutPv;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.SuspensionDossierRepository;
import cnm.prs.repository.TacheDossierRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>Chronométrage et prévision des délais</strong> (règle du pilote, 2026-09-01 ;
 * <strong>refonte du 2026-09-12</strong>).
 *
 * <h2>Le délai d'une étape ne se déclare plus, il se mesure</h2>
 *
 * <p>Jusqu'ici, un porteur <em>prenait en charge</em> son étape — un geste explicite qui ouvrait la
 * tâche, en horodatait le début et portait sa prévision — et aucune action du circuit n'était possible
 * sans lui. La demande du pilote (2026-09-12) supprime tout cet étage : <strong>réception, dispatch,
 * examen, visa, signature, vérification et archivage s'exécutent directement</strong>, et le délai de
 * chaque étape est <strong>calculé</strong> :</p>
 *
 * <pre>durée(étape) = fin de l'étape − entrée dans l'étape, en heures ouvrées</pre>
 *
 * <p>Les deux bornes existaient déjà. La <strong>fin</strong> est l'horodatage du geste métier qui
 * achève l'étape — celui-là même qui datait déjà la frise ({@link #datesEtapes}) et le journal. L'
 * <strong>entrée</strong> est <em>dérivée</em> : c'est la fin du passage précédent, car un dossier qui
 * sort d'une étape entre dans la suivante à cet instant précis. Rien ne se saisit, rien ne se stocke de
 * plus — {@code t_tache_dossier} ne porte plus qu'une fin par passage.</p>
 *
 * <h2>Pourquoi dériver plutôt que stocker une seconde borne</h2>
 *
 * <p>Une date d'entrée écrite à côté de la fin du passage précédent serait une <strong>copie</strong> :
 * deux colonnes censées porter le même instant, donc deux occasions de diverger, et un écart qui ne se
 * verrait qu'en recette. En la dérivant, la chaîne des passages est par construction sans trou ni
 * recouvrement — la fin de l'un est l'entrée de l'autre.</p>
 *
 * <p><strong>Trois bornes peuvent faire entrer dans une étape</strong>, et l'on retient la plus récente
 * qui précède la fin : la fin du passage précédent, le <strong>dépôt</strong> du dossier (pour la toute
 * première étape, et après un retrait suivi d'une nouvelle soumission), et la <strong>sortie d'une
 * attente PRMP</strong>. Cette dernière est ce qui empêche le temps de la PRMP de s'imputer à la
 * Commission : un examen repris après une lettre de renvoi commence quand le dossier revient, pas quand
 * il était parti. Seule {@code RECTIFICATION_PRMP} y échappe — cette étape <em>est</em> l'attente, sa
 * sortie est sa propre fin.</p>
 *
 * <h2>Ce qui n'a pas changé</h2>
 *
 * <p><strong>Le chronométrage n'empêche jamais le métier</strong> : aucune exception levée ici ne fait
 * tomber une transaction métier, une anomalie est journalisée et le geste passe. C'était vrai quand le
 * chronomètre pouvait bloquer un dossier ; ça l'est d'autant plus maintenant qu'il ne fait qu'observer.</p>
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
    /** L'attributaire courant de l'examen : la seule étape nominativement attribuée. */
    private final cnm.prs.repository.DispatchRepository dispatchRepository;

    public ChronometrageService(TacheDossierRepository tacheRepository,
            SuspensionDossierRepository suspensionRepository, DelaiStandardService delaiStandardService,
            DossierRepository dossierRepository, PvExamenRepository pvExamenRepository,
            ControleurRepository controleurRepository,
            cnm.prs.repository.DispatchRepository dispatchRepository) {
        this.tacheRepository = tacheRepository;
        this.suspensionRepository = suspensionRepository;
        this.delaiStandardService = delaiStandardService;
        this.dossierRepository = dossierRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.controleurRepository = controleurRepository;
        this.dispatchRepository = dispatchRepository;
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
        // Pendant l'attente de rectification, l'étape ouverte est celle de la PRMP : le dossier n'est pas
        // « sans étape » parce qu'aucun contrôleur n'y travaille — et c'est ce temps-là qu'on lui mesure.
        if (StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(statut)) {
            return EtapeCircuit.RECTIFICATION_PRMP;
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

    // ------------------------------------------------------------------ fin d'étape (gestes métier)

    /**
     * Enregistre la <strong>fin</strong> d'un passage par une étape — appelée depuis le <strong>geste
     * métier</strong> qui l'achève, au nom de l'utilisateur courant.
     *
     * <p>Depuis la refonte du 2026-09-12, il n'y a plus de tâche ouverte à retrouver : chaque appel écrit
     * une occurrence de rang suivant, déjà close. L'entrée — donc la durée — se dérive de la chaîne à la
     * lecture.</p>
     *
     * <p>Ne lève jamais : une anomalie de chronométrage est journalisée, elle ne fait pas échouer la
     * transaction métier qui l'appelle.</p>
     */
    public void cloturer(Integer idDossier, EtapeCircuit etape) {
        cloturerPourActeur(idDossier, etape, CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null));
    }

    /**
     * Même enregistrement, l'<strong>acteur</strong> étant imposé par l'appelant — nécessaire partout où
     * celui qui pose le geste n'est pas celui à qui l'étape revient : la co-signature (chaque désigné
     * signe SA part), l'examen soumis par délégation, l'examen abandonné à la réattribution.
     *
     * <p>Le profil suit l'acteur : imposer un acteur tiers sans imposer son profil aurait attribué au
     * porteur réel le rôle sous lequel un <em>autre</em> a cliqué.</p>
     */
    public void cloturerPourActeur(Integer idDossier, EtapeCircuit etape, String imActeur) {
        if (idDossier == null || etape == null) {
            return;
        }
        try {
            String moi = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            String profil = imActeur != null && imActeur.equals(moi)
                    ? CurrentUser.profil().map(ProfilUtilisateur::name).orElse(etape.porteur().name())
                    : etape.porteur().name();
            TacheDossier passage = new TacheDossier();
            passage.setIdTache(tacheRepository.nextId());
            passage.setIdDossier(idDossier);
            passage.setEtape(etape.name());
            Integer rang = tacheRepository.dernierRang(idDossier, etape.name());
            passage.setOccurrence((rang == null ? 0 : rang) + 1);
            passage.setImActeur(imActeur);
            passage.setProfil(profil);
            passage.setDateFin(LocalDateTime.now());
            tacheRepository.save(passage);
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] fin d'etape non enregistree dossier={} etape={} acteur={} : {}",
                    idDossier, etape, imActeur, ex.toString());
        }
    }

    /**
     * ⚠️ <strong>Fin de l'étape EXAMEN au nom de l'ATTRIBUTAIRE</strong> du dispatch, jamais du
     * déclencheur de la transition (signalement pilote du 2026-09-07, dossier 00001).
     *
     * <p>Le constat : après un retour de navette, le Président avait re-soumis le projet de PV pour le
     * Membre (délégation Président → Membre), et l'examen se retrouvait mesuré au nom du Président. Un
     * examen appartient à celui à qui il a été dispatché — même principe que la co-signature, où chaque
     * part est nominative.</p>
     *
     * <p>Sans dispatch connu, l'acteur courant fait foi : mieux vaut une étape datée sans titulaire
     * certain qu'un trou dans la chaîne, qui reporterait toute la durée de l'examen sur le visa.</p>
     */
    public void cloturerExamen(Integer idDossier) {
        if (idDossier == null) {
            return;
        }
        String attributaire = dispatchRepository.findImCtrlMembreByDossier(idDossier)
                .filter(s -> s != null && !s.isBlank())
                .orElse(CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null));
        cloturerPourActeur(idDossier, EtapeCircuit.EXAMEN, attributaire);
    }

    /**
     * Enregistre la fin d'une étape <strong>seulement si c'est bien l'étape en cours</strong> du dossier.
     *
     * <p>Sert les gestes qui peuvent survenir à deux moments du circuit et ne doivent clore l'étape que
     * dans l'un d'eux : la transmission SIGMP achève la vérification quand elle est <em>directe</em>
     * (avis FAV, aucun passage de vérification ne l'a close), mais ne doit rien enregistrer quand elle
     * suit une levée d'observations — la vérification y a déjà sa fin, et une seconde occurrence lui
     * aurait attribué le temps de la transmission.</p>
     *
     * <p>⚠️ À appeler <strong>avant</strong> que le geste ne change le statut du dossier : c'est le
     * statut qui dit quelle étape est ouverte.</p>
     */
    public void cloturerSiCourante(Integer idDossier, EtapeCircuit etape) {
        if (idDossier == null || etape == null) {
            return;
        }
        try {
            Dossier dossier = dossierRepository.findById(idDossier).orElse(null);
            if (dossier != null && etapeCourante(dossier) == etape) {
                cloturer(idDossier, etape);
            }
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] fin d'etape conditionnelle impossible dossier={} etape={} : {}",
                    idDossier, etape, ex.toString());
        }
    }

    /**
     * ⚠️ <strong>Ferme l'étape en cours d'un dossier dont l'aval va disparaître</strong> — réattribution,
     * retrait du dispatch, retrait accepté, suppression (signalement pilote du 2026-09-08, dossier 00305).
     *
     * <p>Ce qui est purgé n'a plus rien pour clore son étape : sans cet appel, le temps passé dans
     * l'étape défaite se déverserait sur celle qui reprend. <strong>Fermer, et non supprimer</strong> :
     * l'examen entamé par le sortant a bien eu lieu, sa durée est mesurée jusqu'à l'instant du retrait,
     * comme un passage abandonné. Le journal est append-only, le chronométrage aussi — on ne réécrit pas
     * l'histoire, on la termine.</p>
     *
     * <p>L'acteur est celui à qui l'étape revenait — l'attributaire du dispatch pour l'examen, la PRMP
     * propriétaire pour la rectification —, et non celui qui défait l'aval.</p>
     */
    public void cloturerEtapeCourante(Integer idDossier) {
        if (idDossier == null) {
            return;
        }
        try {
            Dossier dossier = dossierRepository.findById(idDossier).orElse(null);
            EtapeCircuit etape = etapeCourante(dossier);
            if (etape == null) {
                return;
            }
            cloturerPourActeur(idDossier, etape, porteurPresume(dossier, etape));
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] fermeture de l'etape courante impossible dossier={} : {}", idDossier, ex.toString());
        }
    }

    /**
     * Acteur à qui une étape <strong>revient</strong>, quand il est nominativement connu : l'attributaire
     * du dispatch pour l'examen, la PRMP propriétaire pour la rectification. Ailleurs, l'étape est portée
     * par un profil et non par une personne — {@code null}, et le geste qui la clôt nommera son auteur.
     */
    private String porteurPresume(Dossier dossier, EtapeCircuit etape) {
        if (dossier == null || etape == null) {
            return null;
        }
        if (etape == EtapeCircuit.EXAMEN) {
            return dispatchRepository.findImCtrlMembreByDossier(dossier.getIdDossier())
                    .filter(s -> s != null && !s.isBlank()).orElse(null);
        }
        if (etape == EtapeCircuit.RECTIFICATION_PRMP) {
            String prmp = dossier.getIdPrmp();
            return prmp == null || prmp.isBlank() ? null : prmp;
        }
        return null;
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

    // ------------------------------------------------------------------ la chaîne des passages

    /**
     * ⚠️ <strong>Le cœur de la refonte du 2026-09-12</strong> — la chaîne des passages d'un dossier :
     * chaque fin d'étape déjà horodatée, son entrée dérivée, et la durée qui s'en déduit.
     *
     * <p>Les passages arrivent <strong>triés par fin</strong> (l'identifiant départage deux fins au même
     * instant, dans l'ordre d'écriture : une étape abandonnée est fermée avant que la suivante ne
     * s'ouvre). On remonte la chaîne, chaque fin servant d'entrée à la suivante ; l'étape
     * <strong>en cours</strong>, qui n'a pas encore de fin, ferme la liste avec le temps déjà écoulé.</p>
     */
    private List<PassageEtapeDto> passages(Dossier dossier, List<TacheDossier> taches,
            List<SuspensionDossier> suspensions, EtapeCircuit courante, LocalDateTime maintenant,
            Map<String, String> noms) {
        List<LocalDateTime> reprises = reprises(suspensions);
        LocalDateTime depot = dossier == null ? null : dossier.getDateSoumission();
        List<PassageEtapeDto> lignes = new ArrayList<>();
        Map<String, Integer> rangs = new HashMap<>();
        LocalDateTime precedente = null;
        for (TacheDossier t : taches) {
            LocalDateTime entree = entree(precedente, t.getDateFin(), depot, reprises, etapeDe(t));
            rangs.merge(t.getEtape(), 1, Integer::sum);
            lignes.add(new PassageEtapeDto(t.getEtape(), t.getOccurrence(), t.getImActeur(),
                    noms.get(t.getImActeur()), t.getProfil(), entree, t.getDateFin(),
                    HeuresOuvrees.ecoulees(entree, t.getDateFin()), false));
            precedente = t.getDateFin();
        }
        if (courante != null) {
            LocalDateTime entree = entree(precedente, maintenant, depot, reprises, courante);
            String porteur = porteurPresume(dossier, courante);
            lignes.add(new PassageEtapeDto(courante.name(), rangs.getOrDefault(courante.name(), 0) + 1,
                    porteur, noms.get(porteur), courante.porteur().name(), entree, null,
                    HeuresOuvrees.ecoulees(entree, maintenant), true));
        }
        return lignes;
    }

    /**
     * <strong>Entrée</strong> dans une étape qui s'achève à {@code borne} : la plus récente des bornes
     * qui la précèdent — fin du passage précédent, dépôt du dossier, sortie d'attente PRMP.
     *
     * <p>Le <strong>dépôt</strong> compte pour la toute première étape (rien ne la précède), et de
     * nouveau après un retrait accepté : le dossier repart en brouillon, {@code DATE_SOUMISSION} est
     * effacée puis reposée à la nouvelle soumission, et le temps passé en brouillon ne s'impute à
     * personne.</p>
     *
     * <p>La <strong>sortie d'attente PRMP</strong> compte pour toutes les étapes sauf
     * {@code RECTIFICATION_PRMP} : celle-ci <em>est</em> l'attente, sa sortie est sa propre fin — la
     * retenir aurait réduit à zéro la seule durée qui mesure la PRMP. Partout ailleurs, elle est ce qui
     * empêche l'attente de s'imputer à la Commission : un examen repris après une lettre de renvoi
     * commence quand le dossier revient.</p>
     *
     * <p>{@code null} quand aucune borne n'est connue — un dossier antérieur au chronométrage, dont la
     * première fin n'a rien derrière elle : la durée est alors nulle plutôt qu'inventée.</p>
     */
    private static LocalDateTime entree(LocalDateTime finPrecedente, LocalDateTime borne,
            LocalDateTime depot, List<LocalDateTime> reprises, EtapeCircuit etape) {
        LocalDateTime retenue = candidate(null, finPrecedente, borne);
        retenue = candidate(retenue, depot, borne);
        if (etape != EtapeCircuit.RECTIFICATION_PRMP) {
            for (LocalDateTime reprise : reprises) {
                retenue = candidate(retenue, reprise, borne);
            }
        }
        return retenue;
    }

    /** Retient {@code candidate} si elle est connue, ne dépasse pas la borne, et bat la meilleure courante. */
    private static LocalDateTime candidate(LocalDateTime meilleure, LocalDateTime candidate, LocalDateTime borne) {
        if (candidate == null || (borne != null && candidate.isAfter(borne))) {
            return meilleure;
        }
        return meilleure == null || candidate.isAfter(meilleure) ? candidate : meilleure;
    }

    /** Fins des fenêtres d'attente PRMP CLOSES — les instants où la balle est revenue à la Commission. */
    private static List<LocalDateTime> reprises(List<SuspensionDossier> suspensions) {
        if (suspensions == null) {
            return List.of();
        }
        return suspensions.stream().map(SuspensionDossier::getFin).filter(java.util.Objects::nonNull).toList();
    }

    // ------------------------------------------------------------------ calcul de la date prévisionnelle

    /**
     * Date prévisionnelle de fin de traitement, en jours <strong>ouvrés</strong> :
     * {@code aujourd'hui + reste(étape en cours) + Σ délais standards des étapes restantes} jusqu'à la
     * transmission SIGMP incluse. {@code null} si le dossier n'est pas dans le circuit.
     *
     * <p>⚠️ <strong>Unité : l'HEURE ouvrée</strong> depuis le 2026-09-02. La somme se fait en heures,
     * puis se convertit en jours par tranche de 8 h, <strong>arrondie au supérieur</strong> : une journée
     * entamée compte pleine. {@code datePrevisionnelleFin} reste une date — la seule rescapée de la
     * bascule d'unité.</p>
     *
     * <p>⚠️ <strong>Depuis le 2026-09-12, la prévision d'une étape est TOUJOURS son délai standard</strong>
     * ({@code tr_delai_standard}, administrable) : plus personne ne saisit d'estimation. Le référentiel
     * reste donc ce qui permet d'annoncer une date <em>dès la soumission</em>, avant que quiconque à la
     * CNM ait touché le dossier — c'est même devenu sa seule raison d'être.</p>
     *
     * <p>L'écoulé de l'étape en cours est mesuré depuis son <strong>entrée dérivée</strong> et jusqu'à
     * {@code maintenant} : en heures, s'arrêter au début du jour sous-compterait la journée en cours et
     * rendrait la date optimiste de huit heures au pire. L'instant est un paramètre pour que le calcul
     * reste déterministe en test.</p>
     *
     * <p><strong>Une étape en dépassement compte 0</strong> : la date glisse jour après jour au lieu de
     * mentir sur un rattrapage qui n'aura pas lieu.</p>
     */
    public LocalDate datePrevisionnelleFin(Dossier dossier, String statutPv, List<TacheDossier> taches,
            List<SuspensionDossier> suspensions, LocalDateTime maintenant) {
        return datePrevisionnelleFin(dossier == null ? null : dossier.getStatut(), statutPv, taches,
                suspensions, dossier == null ? null : dossier.getDateSoumission(), maintenant,
                delaiStandardService.delais());
    }

    /**
     * Même calcul, le référentiel étant fourni <strong>déjà chargé</strong> — indispensable pour les
     * listes : relire les délais par étape et par dossier chargeait assez d'entités pour faire tomber le
     * contrat de pagination (lot D §3).
     */
    public LocalDate datePrevisionnelleFin(String statut, String statutPv, List<TacheDossier> taches,
            List<SuspensionDossier> suspensions, LocalDateTime depot, LocalDateTime maintenant,
            Map<EtapeCircuit, Integer> delais) {
        EtapeCircuit courante = etapeCourante(statut, () -> statutPv);
        EtapeCircuit reference = courante != null ? courante : REPRISE_APRES_ATTENTE.get(statut);
        if (reference == null) {
            return null;
        }
        // Entrée de l'étape en cours : la même dérivation que la chaîne des passages, bornée à maintenant.
        LocalDateTime entreeCourante = courante == null ? null
                : entree(derniereFin(taches), maintenant, depot, reprises(suspensions), courante);
        long totalHeures = 0L;
        for (EtapeCircuit etape : EtapeCircuit.etapesDuCompteur()) {
            if (etape.ordinal() < reference.ordinal()) {
                continue;   // franchie : l'étape de référence fait foi, y compris après un retour en arrière
            }
            int standard = delais.getOrDefault(etape, HeuresOuvrees.HEURES_PAR_JOUR);
            if (etape == courante && entreeCourante != null) {
                // Écoulé et délai standard dans la MÊME échelle (8 h par jour ouvré) : mesurer en heures
                // d'horloge mettrait en dépassement une étape entamée la veille alors qu'un seul jour de
                // travail a passé.
                totalHeures += Math.max(0L, standard - HeuresOuvrees.ecoulees(entreeCourante, maintenant));
            } else {
                totalHeures += standard;
            }
        }
        return JoursOuvres.ajouter(maintenant.toLocalDate(), HeuresOuvrees.enJoursArrondiSuperieur(totalHeures));
    }

    /** Fin du dernier passage enregistré — l'entrée de l'étape en cours, avant arbitrage des autres bornes. */
    private static LocalDateTime derniereFin(List<TacheDossier> taches) {
        return taches == null ? null : taches.stream().map(TacheDossier::getDateFin)
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
    }

    private static EtapeCircuit etapeDe(TacheDossier tache) {
        try {
            return EtapeCircuit.valueOf(tache.getEtape());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ restitution

    /** Chronométrage complet d'un dossier : passages par étape (entrée, fin, durée) + compteurs globaux. */
    @Transactional(readOnly = true)
    public ChronometrageDto chronometrage(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        List<TacheDossier> taches = tacheRepository.findParDossier(idDossier);
        List<SuspensionDossier> suspensions = suspensionRepository.findByIdDossierOrderByDebutAsc(idDossier);
        String statutPv = statutPvDe(idDossier);
        EtapeCircuit courante = etapeCourante(dossier.getStatut(), () -> statutPv);
        LocalDateTime maintenant = LocalDateTime.now();

        Map<String, String> noms = new HashMap<>();
        for (TacheDossier t : taches) {
            if (t.getImActeur() != null) {
                noms.computeIfAbsent(t.getImActeur(), this::nom);
            }
        }
        String porteurCourant = porteurPresume(dossier, courante);
        if (porteurCourant != null) {
            noms.computeIfAbsent(porteurCourant, this::nom);
        }

        LocalDateTime debut = borne(taches, EtapeCircuit.RECEPTION);
        LocalDateTime fin = borne(taches, EtapeCircuit.TRANSMISSION_SIGMP);
        LocalDateTime jusqua = fin != null ? fin : maintenant;
        long brut = debut == null ? 0L : HeuresOuvrees.ecoulees(debut, jusqua);
        long attentes = cumulAttentes(suspensions, jusqua);

        return new ChronometrageDto(idDossier,
                passages(dossier, taches, suspensions, courante, maintenant, noms),
                debut, fin, brut, Math.max(0L, brut - attentes), attentes,
                courante == null ? null : courante.name(),
                estEnAttentePrmp(dossier.getStatut()),
                datePrevisionnelleFin(dossier, statutPv, taches, suspensions, maintenant),
                // L'attributaire courant du dossier : les écrans qui ne chargent PAS les dispatchs (la
                // consultation) n'ont ainsi aucun appel de liste à ajouter — le serveur qui répond ici a
                // déjà le dispatch sous la main.
                dispatchRepository.findImCtrlMembreByDossier(idDossier).filter(s -> !s.isBlank()).orElse(null));
    }

    /**
     * ⚠️ Suivi des délais CNM (2026-09-06) — date d'<strong>enregistrement</strong> du dossier : la clôture
     * de l'étape {@code RECEPTION}, <strong>exactement</strong> le {@code debutCompteur} du chronométrage
     * (même borne, même liste de passages) — servie en lot sur {@code DossierDto} par la même méthode, pour
     * que la liste et le détail ne puissent pas diverger. {@code null} avant l'enregistrement.
     */
    public LocalDateTime dateEnregistrement(List<TacheDossier> taches) {
        return borne(taches, EtapeCircuit.RECEPTION);
    }

    /** Clés de {@link #datesEtapes} — les sept étapes de la frise du front, dans son ordre. */
    public static final List<String> ETAPES_FRISE = List.of(
            "RECEPTION", "DISPATCH", "EXAMEN", "PROJET_PV", "PV_SIGNE", "VERIFICATION", "CLOTURE");

    /** Statuts pour lesquels le DISPATCH est franchi (un dispatch annulé ramène en PRET_DISPATCH : non). */
    private static final Set<String> STATUTS_APRES_DISPATCH = Set.of(
            StatutDossier.DISPATCHE.name(), StatutDossier.EXAMINE.name(), StatutDossier.PV_SIGNE.name(),
            StatutDossier.EN_VERIFICATION.name(), StatutDossier.EN_ATTENTE_DECISION_PRMP.name(),
            StatutDossier.OBSERVATIONS_LEVEES.name(), StatutDossier.DECISION_TRANSMISE_SIGMP.name(),
            StatutDossier.EN_ATTENTE_PIECES.name(), StatutDossier.A_REEXAMINER.name(), StatutDossier.CLOTURE.name());

    /** Statuts pour lesquels l'EXAMEN est franchi (un réexamen — A_REEXAMINER — le remet en jeu : non). */
    private static final Set<String> STATUTS_APRES_EXAMEN = Set.of(
            StatutDossier.EXAMINE.name(), StatutDossier.PV_SIGNE.name(), StatutDossier.EN_VERIFICATION.name(),
            StatutDossier.EN_ATTENTE_DECISION_PRMP.name(), StatutDossier.OBSERVATIONS_LEVEES.name(),
            StatutDossier.DECISION_TRANSMISE_SIGMP.name(), StatutDossier.EN_ATTENTE_PIECES.name(),
            StatutDossier.CLOTURE.name());

    /**
     * ⚠️ Frise du tableau de bord (demande pilote 2026-09-07) — <strong>date de franchissement</strong> de
     * chacune des sept étapes de la frise du front, dérivée des passages déjà chargés en lot (aucune
     * requête) ; {@code null} pour une étape non atteinte. Le front datait ses points par jointure de
     * listes qui reviennent vides selon la portée (Président « toutes localités ») : ici la date vient du
     * dossier lui-même, quel que soit le profil qui le lit.
     *
     * <ul>
     *   <li>{@code RECEPTION} : fin de RECEPTION — <strong>identique</strong> à {@link #dateEnregistrement} ;</li>
     *   <li>{@code DISPATCH} / {@code EXAMEN} : fin du dernier passage, <strong>seulement si le statut a
     *       dépassé l'étape</strong> — un dispatch annulé ({@code PRET_DISPATCH}) ou un réexamen
     *       ({@code A_REEXAMINER}) laissent des passages derrière eux sans que l'étape soit franchie ;</li>
     *   <li>{@code PROJET_PV} : le projet de PV naît de la fin d'EXAMEN — même date, même condition ;</li>
     *   <li>{@code PV_SIGNE} : dernière signature (COSIGNATURE, à défaut VISA), <strong>seulement si le PV
     *       est {@code SIGNE}</strong> — une signature sur deux ne date pas un PV signé ;</li>
     *   <li>{@code VERIFICATION} : fin de la dernière VERIFICATION, seulement une fois les observations
     *       levées (un passage qui maintient des observations n'a pas franchi l'étape) ;</li>
     *   <li>{@code CLOTURE} : archivage, à défaut transmission SIGMP, seulement au statut {@code CLOTURE}.</li>
     * </ul>
     * Le « franchissement » s'apprécie donc sur le <em>statut</em> ; la <em>date</em> vient des passages.
     */
    public Map<String, LocalDateTime> datesEtapes(String statutDossier, String statutPv, List<TacheDossier> taches) {
        Map<String, LocalDateTime> dates = new java.util.LinkedHashMap<>();
        boolean dispatchFranchi = statutDossier != null && STATUTS_APRES_DISPATCH.contains(statutDossier);
        boolean examenFranchi = statutDossier != null && STATUTS_APRES_EXAMEN.contains(statutDossier);
        LocalDateTime examen = examenFranchi ? borne(taches, EtapeCircuit.EXAMEN) : null;
        dates.put("RECEPTION", borne(taches, EtapeCircuit.RECEPTION));
        dates.put("DISPATCH", dispatchFranchi ? borne(taches, EtapeCircuit.DISPATCH) : null);
        dates.put("EXAMEN", examen);
        dates.put("PROJET_PV", examen);
        boolean pvSigne = "SIGNE".equals(statutPv);
        LocalDateTime signature = borne(taches, EtapeCircuit.COSIGNATURE);
        dates.put("PV_SIGNE", pvSigne ? (signature != null ? signature : borne(taches, EtapeCircuit.VISA)) : null);
        boolean observationsLevees = statutDossier != null && List.of(
                StatutDossier.OBSERVATIONS_LEVEES.name(), StatutDossier.DECISION_TRANSMISE_SIGMP.name(),
                StatutDossier.CLOTURE.name()).contains(statutDossier);
        dates.put("VERIFICATION", observationsLevees ? borne(taches, EtapeCircuit.VERIFICATION) : null);
        LocalDateTime archivage = borne(taches, EtapeCircuit.ARCHIVAGE);
        dates.put("CLOTURE", StatutDossier.CLOTURE.name().equals(statutDossier)
                ? (archivage != null ? archivage : borne(taches, EtapeCircuit.TRANSMISSION_SIGMP)) : null);
        return dates;
    }

    /** Fin du dernier passage par une étape — borne du compteur global. */
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

    private static String nomComplet(Controleur c) {
        String prenoms = c.getPrenomsCont() == null ? "" : c.getPrenomsCont().trim();
        String nom = c.getNomCont() == null ? "" : c.getNomCont().trim();
        String complet = (prenoms + " " + nom).trim();
        return complet.isEmpty() ? null : complet;
    }

    // ------------------------------------------------------------------ enrichissement en lot

    /** Passages de plusieurs dossiers, groupés — une seule requête pour toute une liste. */
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
     * Fenêtres d'attente PRMP de plusieurs dossiers, groupées — une seule requête pour toute une liste.
     * ⚠️ 2026-09-12 : nécessaire à la date prévisionnelle, dont l'écoulé part de l'entrée dans l'étape en
     * cours — et une reprise après attente PRMP est l'une des bornes qui la fixent.
     */
    @Transactional(readOnly = true)
    public Map<Integer, List<SuspensionDossier>> suspensionsParDossier(Collection<Integer> idsDossiers) {
        Map<Integer, List<SuspensionDossier>> parDossier = new HashMap<>();
        if (idsDossiers == null || idsDossiers.isEmpty()) {
            return parDossier;
        }
        for (SuspensionDossier s : suspensionRepository.findParDossiers(idsDossiers)) {
            parDossier.computeIfAbsent(s.getIdDossier(), k -> new ArrayList<>()).add(s);
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
