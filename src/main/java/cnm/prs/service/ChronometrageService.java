package cnm.prs.service;

import java.time.Clock;
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
 * <p><strong>Le chronométrage ne doit pas empêcher le métier</strong> — et voici exactement ce qui est
 * garanti (⚠️ Audit 2026-09-14, constat C3 : la version précédente de ce paragraphe promettait
 * « jamais », ce que le code ne tenait pas).</p>
 *
 * <p>Les écritures du chronomètre <strong>rejoignent la transaction du geste métier</strong>. L'INSERT
 * d'un passage ne part qu'au flush — souvent au commit, bien après le retour de ce service : un
 * {@code try/catch} autour du {@code save()} ne voit donc pas la violation SQL, qui annule le geste
 * entier. Les deux remèdes intuitifs sont faux : un {@code flush()} dans le {@code try} ne sauve rien
 * (PostgreSQL avorte la transaction à la première erreur, JPA la marque rollback-only), et
 * {@code REQUIRES_NEW} ne voit pas les lignes non commitées du geste en cours (dossier, passages
 * précédents) — clé étrangère refusée ou rangs d'occurrence faux.</p>
 *
 * <p><strong>Ce qui est garanti</strong> : avant toute écriture, ce qui violerait une contrainte connue du
 * schéma est <strong>validé</strong> — longueur de l'acteur, du profil, de l'étape ou du statut suspensif,
 * dossier inexistant. Une écriture qui ne tiendrait pas est <strong>écartée</strong> avec un WARN
 * {@code [CHRONO]}, et le geste métier passe sans elle (le passage manque à la chaîne ; sa durée se
 * reporte sur le suivant).</p>
 *
 * <p><strong>Ce qui ne l'est pas</strong> : une violation que la validation ne peut pas prévoir (dossier
 * supprimé plus loin dans le même geste sans purge de ses passages, panne de la base) fait échouer le
 * geste avec son passage. Et une exception levée <em>par un repository</em> dans le {@code try} marque
 * la transaction rollback-only même rattrapée : les {@code try/catch} de ce service ne couvrent que les
 * erreurs de calcul du service lui-même.</p>
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
     *
     * <p>⚠️ 2026-09-14 — <strong>publique</strong> (lecture seule, {@code Map.of} est immuable) : l'accueil
     * « À faire » y lit l'étape qui porte un dossier en attente PRMP. La lire ici, et non la recopier, est ce
     * qui garantit que les deux écrans parlent de la même étape.</p>
     */
    public static final Map<String, EtapeCircuit> REPRISE_APRES_ATTENTE = Map.of(
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
    /**
     * ⚠️ 2026-09-14 — horloge injectée ({@code ClockConfig}) : seul « maintenant » du service, pour les fins de
     * passage, les fenêtres d'attente et la restitution. Horloge système en production — aucun changement de
     * comportement ; une horloge figée en test rend l'écoulé et l'échéance vérifiables à l'heure près.
     */
    private final Clock clock;

    public ChronometrageService(TacheDossierRepository tacheRepository,
            SuspensionDossierRepository suspensionRepository, DelaiStandardService delaiStandardService,
            DossierRepository dossierRepository, PvExamenRepository pvExamenRepository,
            ControleurRepository controleurRepository,
            cnm.prs.repository.DispatchRepository dispatchRepository, Clock clock) {
        this.tacheRepository = tacheRepository;
        this.suspensionRepository = suspensionRepository;
        this.delaiStandardService = delaiStandardService;
        this.dossierRepository = dossierRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.controleurRepository = controleurRepository;
        this.dispatchRepository = dispatchRepository;
        this.clock = clock;
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
     * <p>Un passage qui ne tiendrait pas dans le schéma est écarté <strong>avant</strong> d'être écrit,
     * avec un WARN, et le geste métier passe ; les limites de cette garantie sont décrites en tête de
     * classe (⚠️ Audit 2026-09-14, C3).</p>
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
            // ⚠️ Audit 2026-09-14 (C3) — valider AVANT d'écrire : l'INSERT part au commit, hors de portée du
            // catch ci-dessous. Un acteur trop long (une PRMP de 10 caractères quand IM_ACTEUR en faisait 7)
            // annulait en silence la resoumission entière ; désormais le passage est écarté, le geste passe.
            String rejet = motifDeRejetPassage(idDossier, etape, imActeur, profil);
            if (rejet != null) {
                LOG.warn("[CHRONO] fin d'etape ecartee avant ecriture dossier={} etape={} acteur={} : {}",
                        idDossier, etape, imActeur, rejet);
                return;
            }
            TacheDossier passage = new TacheDossier();
            passage.setIdTache(tacheRepository.nextId());
            passage.setIdDossier(idDossier);
            passage.setEtape(etape.name());
            Integer rang = tacheRepository.dernierRang(idDossier, etape.name());
            passage.setOccurrence((rang == null ? 0 : rang) + 1);
            passage.setImActeur(imActeur);
            passage.setProfil(profil);
            passage.setDateFin(LocalDateTime.now(clock));
            tacheRepository.save(passage);
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] fin d'etape non enregistree dossier={} etape={} acteur={} : {}",
                    idDossier, etape, imActeur, ex.toString());
        }
    }

    /**
     * ⚠️ Audit 2026-09-14 (C3) — ce qui ferait refuser l'INSERT d'un passage par PostgreSQL, vérifié
     * <strong>avant</strong> l'écriture ; {@code null} si le passage tient. Les longueurs sont celles de
     * l'entité ({@link TacheDossier#LONGUEUR_IM_ACTEUR}…), déclarées au même endroit que ses
     * {@code @Column}. Les champs obligatoires restants (identifiant, rang, date de fin) sont posés par
     * {@link #cloturerPourActeur} lui-même et ne peuvent pas manquer.
     */
    private String motifDeRejetPassage(Integer idDossier, EtapeCircuit etape, String imActeur, String profil) {
        if (etape.name().length() > TacheDossier.LONGUEUR_ETAPE) {
            return "etape de " + etape.name().length() + " caracteres (max " + TacheDossier.LONGUEUR_ETAPE + ")";
        }
        if (imActeur != null && imActeur.length() > TacheDossier.LONGUEUR_IM_ACTEUR) {
            return "acteur de " + imActeur.length() + " caracteres (max " + TacheDossier.LONGUEUR_IM_ACTEUR + ")";
        }
        if (profil != null && profil.length() > TacheDossier.LONGUEUR_PROFIL) {
            return "profil de " + profil.length() + " caracteres (max " + TacheDossier.LONGUEUR_PROFIL + ")";
        }
        if (!dossierRepository.existsById(idDossier)) {
            return "dossier inexistant (cle etrangere)";
        }
        return null;
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
            // ⚠️ Audit 2026-09-14 (C3) — même garde préalable que pour les passages : l'INSERT part au commit.
            if (statut.name().length() > SuspensionDossier.LONGUEUR_STATUT || !dossierRepository.existsById(idDossier)) {
                LOG.warn("[CHRONO] attente PRMP ecartee avant ecriture dossier={} statut={} : statut trop long "
                        + "ou dossier inexistant", idDossier, statut);
                return;
            }
            SuspensionDossier suspension = new SuspensionDossier();
            suspension.setIdSuspension(suspensionRepository.nextId());
            suspension.setIdDossier(idDossier);
            suspension.setStatut(statut.name());
            suspension.setDebut(LocalDateTime.now(clock));
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
                s.setFin(LocalDateTime.now(clock));
                suspensionRepository.save(s);
            });
        } catch (RuntimeException ex) {
            LOG.warn("[CHRONO] fermeture d'attente PRMP impossible dossier={} : {}", idDossier, ex.toString());
        }
    }

    /**
     * ⚠️ Audit 2026-09-14 (E1) — efface passages et fenêtres d'attente d'un dossier <strong>qui
     * disparaît</strong>. Réservé à la suppression du dossier ({@code DossierService#delete}) : leurs FK
     * vers {@code t_dossier} (V14, sans cascade) faisaient échouer en 409 la suppression d'un brouillon
     * revenu de circuit par retrait.
     *
     * <p><strong>Jamais au retrait</strong> ({@code CircuitCascadeService#purgerCircuit}) : là, le dossier
     * survit, et son chronométrage est append-only comme son journal — on ferme l'étape en cours, on
     * n'efface pas l'histoire. Contrairement aux écritures de ce service, cette purge <strong>ne rattrape
     * aucune exception</strong> : une suppression qui échoue doit échouer.</p>
     */
    public void purgerDossier(Integer idDossier) {
        tacheRepository.deleteByIdDossier(idDossier);
        suspensionRepository.deleteByIdDossier(idDossier);
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
        // ⚠️ 2026-09-14 — une seule dérivation : l'étape en cours, son entrée et son reste viennent de
        // delaiCourant, que l'accueil « À faire » sert tel quel. Les résultats n'ont pas bougé d'un jour.
        DelaiCourant courant = delaiCourant(statut, statutPv, taches, suspensions, depot, maintenant, delais);
        EtapeCircuit reference = courant.etape() != null ? courant.etape() : etapeDeReprise(statut);
        if (reference == null) {
            return null;
        }
        long totalHeures = 0L;
        for (EtapeCircuit etape : EtapeCircuit.etapesDuCompteur()) {
            if (etape.ordinal() < reference.ordinal()) {
                continue;   // franchie : l'étape de référence fait foi, y compris après un retour en arrière
            }
            if (etape == courant.etape() && courant.entree() != null) {
                // Écoulé et délai standard dans la MÊME échelle (8 h par jour ouvré) : mesurer en heures
                // d'horloge mettrait en dépassement une étape entamée la veille alors qu'un seul jour de
                // travail a passé. Une étape en dépassement compte 0.
                totalHeures += Math.max(0L, courant.restantHeures());
            } else {
                totalHeures += standard(delais, etape);
            }
        }
        return JoursOuvres.ajouter(maintenant.toLocalDate(), HeuresOuvrees.enJoursArrondiSuperieur(totalHeures));
    }

    /**
     * ⚠️ <strong>Le délai de l'étape en cours</strong> (demande front du 2026-09-14, accueil « À faire », §4) —
     * ce que {@code GET /api/dossiers/{id}/chronometrage} sert pour son passage {@code enCours}, plus ce qu'il
     * faut pour juger l'urgence : délai standard, reste, échéance, et la pause PRMP en cours.
     *
     * @param etape         étape courante ({@link #etapeCourante(String, String)}) ; {@code null} hors circuit
     *                      et pendant une attente PRMP autre que la rectification — les autres champs du délai
     *                      sont alors nuls, seule la pause peut être renseignée
     * @param entree        entrée dérivée, <strong>la même</strong> que celle du passage en cours de la chaîne
     *                      ({@link #entree}) ; {@code null} si aucune borne n'est connue
     * @param ecouleHeures  heures ouvrées de l'entrée à {@code maintenant} — égal au {@code dureeHeuresOuvrees}
     *                      du passage {@code enCours} ; 0 sans entrée connue (« nulle plutôt qu'inventée »)
     * @param standardHeures délai standard de l'étape ({@code tr_delai_standard}, repli 8 h comme la prévision)
     * @param restantHeures standard − écoulé ; négatif en dépassement
     * @param echeance      entrée + standard en heures ouvrées ({@link HeuresOuvrees#ajouter}) ; {@code null} sans
     *                      entrée connue
     * @param pauseDepuis   statut suspensif seulement : début de la fenêtre d'attente PRMP OUVERTE
     *                      ({@code t_suspension_dossier}), {@code null} s'il n'y en a pas
     * @param pauseHeures   heures ouvrées écoulées depuis {@code pauseDepuis} ; {@code null} sans fenêtre ouverte
     */
    public record DelaiCourant(EtapeCircuit etape, LocalDateTime entree, Long ecouleHeures, Integer standardHeures,
            Long restantHeures, LocalDateTime echeance, LocalDateTime pauseDepuis, Long pauseHeures) {
    }

    /**
     * Délai de l'étape en cours d'un dossier, calculé <strong>en mémoire</strong> sur des données déjà chargées
     * (en lot pour une liste) : aucune requête. L'entrée de l'étape suit exactement la dérivation de la chaîne
     * des passages — fin du dernier passage, dépôt, sortie d'attente PRMP (sauf pour la rectification) —, si
     * bien que l'écoulé servi ici et celui du passage {@code enCours} ne peuvent pas diverger.
     *
     * <p>{@code maintenant} est un paramètre, comme pour {@link #datePrevisionnelleFin} : l'appelant le lit une
     * fois (horloge injectée) pour toute sa liste, et le calcul reste déterministe en test.</p>
     */
    public DelaiCourant delaiCourant(String statut, String statutPv, List<TacheDossier> taches,
            List<SuspensionDossier> suspensions, LocalDateTime depot, LocalDateTime maintenant,
            Map<EtapeCircuit, Integer> delais) {
        LocalDateTime pauseDepuis = estEnAttentePrmp(statut) ? debutAttenteOuverte(suspensions) : null;
        Long pauseHeures = pauseDepuis == null ? null : HeuresOuvrees.ecoulees(pauseDepuis, maintenant);
        EtapeCircuit courante = etapeCourante(statut, () -> statutPv);
        if (courante == null) {
            return new DelaiCourant(null, null, null, null, null, null, pauseDepuis, pauseHeures);
        }
        // Entrée de l'étape en cours : la même dérivation que la chaîne des passages, bornée à maintenant.
        LocalDateTime entree = entree(derniereFin(taches), maintenant, depot, reprises(suspensions), courante);
        long ecoule = HeuresOuvrees.ecoulees(entree, maintenant);
        int standard = standard(delais, courante);
        return new DelaiCourant(courante, entree, ecoule, standard, standard - ecoule,
                entree == null ? null : HeuresOuvrees.ajouter(entree, standard), pauseDepuis, pauseHeures);
    }

    /**
     * Étape qui <strong>reprendra</strong> quand la PRMP rendra la main ({@link #REPRISE_APRES_ATTENTE}) ;
     * {@code null} si le statut n'est pas suspensif.
     */
    public static EtapeCircuit etapeDeReprise(String statut) {
        return statut == null ? null : REPRISE_APRES_ATTENTE.get(statut);
    }

    /** Délai standard d'une étape dans le référentiel chargé — repli 8 h, celui de la prévision. */
    private static int standard(Map<EtapeCircuit, Integer> delais, EtapeCircuit etape) {
        return delais.getOrDefault(etape, HeuresOuvrees.HEURES_PAR_JOUR);
    }

    /** Début de la fenêtre d'attente PRMP ouverte (sans fin) la plus récente ; {@code null} s'il n'y en a pas. */
    private static LocalDateTime debutAttenteOuverte(List<SuspensionDossier> suspensions) {
        return suspensions == null ? null : suspensions.stream()
                .filter(s -> s.getFin() == null && s.getDebut() != null)
                .map(SuspensionDossier::getDebut)
                .max(LocalDateTime::compareTo).orElse(null);
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
        LocalDateTime maintenant = LocalDateTime.now(clock);

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

        ChronometrageDto complet = new ChronometrageDto(idDossier,
                passages(dossier, taches, suspensions, courante, maintenant, noms),
                debut, fin, brut, Math.max(0L, brut - attentes), attentes,
                courante == null ? null : courante.name(),
                estEnAttentePrmp(dossier.getStatut()),
                datePrevisionnelleFin(dossier, statutPv, taches, suspensions, maintenant),
                // L'attributaire courant du dossier : les écrans qui ne chargent PAS les dispatchs (la
                // consultation) n'ont ainsi aucun appel de liste à ajouter — le serveur qui répond ici a
                // déjà le dispatch sous la main.
                dispatchRepository.findImCtrlMembreByDossier(idDossier).filter(s -> !s.isBlank()).orElse(null));
        return cnm.prs.security.Visibilite.estPrmp() ? sansIdentites(complet) : complet;
    }

    /**
     * ⚠️ Audit 2026-09-14 (C2) — <strong>vue de la partie contrôlée</strong> (PRMP, UGPM) du chronométrage :
     * le même objet, <strong>sans identités</strong>. {@code imActeur} et {@code nomActeur} de chaque passage
     * et l'{@code attributaire} passent à {@code null} — qui traite le dossier à la CNM est une information
     * interne (règle pilote du 2026-09-06). Tout le reste est conservé : étapes, dates, durées,
     * {@code profil}, compteurs, {@code attentePrmp} et {@code datePrevisionnelleFin}, dont le widget compact
     * de l'écran de rectification (étape courante + fin prévue) a besoin.
     */
    private static ChronometrageDto sansIdentites(ChronometrageDto dto) {
        List<PassageEtapeDto> etapes = dto.etapes() == null ? null : dto.etapes().stream()
                .map(p -> new PassageEtapeDto(p.etape(), p.occurrence(), null, null, p.profil(), p.entree(),
                        p.fin(), p.dureeHeuresOuvrees(), p.enCours()))
                .toList();
        return new ChronometrageDto(dto.idDossier(), etapes, dto.debutCompteur(), dto.finCompteur(),
                dto.dureeBruteHeuresOuvrees(), dto.dureeNetteHeuresOuvrees(), dto.attentePrmpHeuresOuvrees(),
                dto.etapeCourante(), dto.attentePrmp(), dto.datePrevisionnelleFin(), null);
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
     *
     * <p>Raccourci de {@link #frise} sans acteurs — pour qui n'a besoin que des dates.</p>
     */
    public Map<String, LocalDateTime> datesEtapes(String statutDossier, String statutPv, List<TacheDossier> taches) {
        return frise(statutDossier, statutPv, taches, null, null, Map.of()).dates();
    }

    /**
     * ⚠️ Frise du tableau de bord (2026-09-13) — les <strong>dates</strong> et les <strong>acteurs</strong> de
     * franchissement des sept étapes, dérivés ensemble. Invariant : une clé est nommée <strong>si et
     * seulement si</strong> elle est datée — même passage, même règle de recul (dispatch annulé, réexamen).
     */
    public record Frise(Map<String, LocalDateTime> dates, Map<String, String> acteurs) {
    }

    /**
     * État du PV le plus récent d'un dossier, lu en lot : son statut et, pour chaque part, le matricule de
     * qui l'a <strong>effectivement</strong> signée ({@code null} tant que la part n'est pas posée).
     */
    public record EtatPv(String statut, String imMembre, String imCc, String imPresident) {
    }

    /**
     * ⚠️ Frise du tableau de bord (2026-09-13) — dates <em>et</em> acteurs de franchissement des sept
     * étapes. Les dates suivent la règle de {@link #datesEtapes} ; l'acteur d'une clé est celui du
     * <strong>même passage</strong> que sa date, en « Prénoms Nom » nu, {@code null} si la clé n'est pas
     * datée. Deux clés s'en écartent, volontairement :
     * <ul>
     *   <li>{@code DISPATCH} : l'<strong>attributaire courant</strong> (réattributions comprises), et non
     *       l'auteur du passage — le chronométrage, lui, consigne ce passage au nom du
     *       <em>dispatcheur</em> (ed86707). La frise dit « à qui », le chronométrage dit « par qui » : le
     *       front ne doit pas recopier {@code nomActeur} du passage. Sans attributaire connu sous un statut
     *       qui a pourtant franchi l'étape (circuit purgé : état dégradé), l'auteur du passage, pour que la
     *       clé reste nommée dès qu'elle est datée ;</li>
     *   <li>{@code PV_SIGNE} : une seule chaîne, les signataires <strong>effectivement signés</strong> lus
     *       sur le PV, joints par « · » dans l'ordre Membre · CC · Président. À défaut de part datée sur un
     *       PV pourtant {@code SIGNE} (données antérieures), l'acteur du passage qui date la clé.</li>
     * </ul>
     * Les noms sont résolus dans l'{@code annuaire} déjà chargé (aucune requête) ; un matricule qui n'y
     * figure pas est servi tel quel.
     */
    public Frise frise(String statutDossier, String statutPv, List<TacheDossier> taches,
            String attributaire, EtatPv pv, Map<String, Controleur> annuaire) {
        Map<String, LocalDateTime> dates = new java.util.LinkedHashMap<>();
        Map<String, String> acteurs = new java.util.LinkedHashMap<>();
        boolean dispatchFranchi = statutDossier != null && STATUTS_APRES_DISPATCH.contains(statutDossier);
        boolean examenFranchi = statutDossier != null && STATUTS_APRES_EXAMEN.contains(statutDossier);

        TacheDossier reception = dernierPassage(taches, EtapeCircuit.RECEPTION);
        poser(dates, acteurs, "RECEPTION", reception, nomNu(annuaire, acteurDe(reception)));

        TacheDossier dispatch = dispatchFranchi ? dernierPassage(taches, EtapeCircuit.DISPATCH) : null;
        String imAttributaire = attributaire != null && !attributaire.isBlank() ? attributaire : acteurDe(dispatch);
        poser(dates, acteurs, "DISPATCH", dispatch, nomNu(annuaire, imAttributaire));

        TacheDossier examen = examenFranchi ? dernierPassage(taches, EtapeCircuit.EXAMEN) : null;
        String examinateur = nomNu(annuaire, acteurDe(examen));
        poser(dates, acteurs, "EXAMEN", examen, examinateur);
        poser(dates, acteurs, "PROJET_PV", examen, examinateur);

        boolean pvSigne = StatutPv.SIGNE.name().equals(statutPv);
        TacheDossier signature = null;
        if (pvSigne) {
            signature = dernierPassage(taches, EtapeCircuit.COSIGNATURE);
            if (signature == null) {
                signature = dernierPassage(taches, EtapeCircuit.VISA);
            }
        }
        String signataires = pvSigne ? signataires(pv, annuaire) : null;
        poser(dates, acteurs, "PV_SIGNE", signature,
                signataires != null ? signataires : nomNu(annuaire, acteurDe(signature)));

        boolean observationsLevees = statutDossier != null && List.of(
                StatutDossier.OBSERVATIONS_LEVEES.name(), StatutDossier.DECISION_TRANSMISE_SIGMP.name(),
                StatutDossier.CLOTURE.name()).contains(statutDossier);
        TacheDossier verification = observationsLevees ? dernierPassage(taches, EtapeCircuit.VERIFICATION) : null;
        poser(dates, acteurs, "VERIFICATION", verification, nomNu(annuaire, acteurDe(verification)));

        TacheDossier cloture = null;
        if (StatutDossier.CLOTURE.name().equals(statutDossier)) {
            cloture = dernierPassage(taches, EtapeCircuit.ARCHIVAGE);
            if (cloture == null) {
                cloture = dernierPassage(taches, EtapeCircuit.TRANSMISSION_SIGMP);
            }
        }
        poser(dates, acteurs, "CLOTURE", cloture, nomNu(annuaire, acteurDe(cloture)));
        return new Frise(dates, acteurs);
    }

    /** Une clé est datée ET nommée par le même passage — ou ni l'un ni l'autre : c'est l'invariant. */
    private static void poser(Map<String, LocalDateTime> dates, Map<String, String> acteurs, String cle,
            TacheDossier passage, String nom) {
        dates.put(cle, passage == null ? null : passage.getDateFin());
        acteurs.put(cle, passage == null ? null : nom);
    }

    private static String acteurDe(TacheDossier passage) {
        return passage == null ? null : passage.getImActeur();
    }

    /**
     * « Membre · CC · Président » des parts <strong>effectivement</strong> signées du PV ; {@code null} si
     * aucune part n'est datée (ou sans PV).
     */
    static String signataires(EtatPv pv, Map<String, Controleur> annuaire) {
        if (pv == null) {
            return null;
        }
        List<String> noms = new ArrayList<>();
        for (String im : new String[] {pv.imMembre(), pv.imCc(), pv.imPresident()}) {
            String nom = nomNu(annuaire, im);
            if (nom != null) {
                noms.add(nom);
            }
        }
        return noms.isEmpty() ? null : String.join(" · ", noms);
    }

    /**
     * Dernier passage par une étape — fin la plus tardive, à égalité le rang d'écriture (même tri que
     * les passages servis : {@code dateFin, idTache}) ; {@code null} si aucun.
     */
    private static TacheDossier dernierPassage(List<TacheDossier> taches, EtapeCircuit etape) {
        return taches.stream()
                .filter(t -> etape.name().equals(t.getEtape()) && t.getDateFin() != null)
                .max(java.util.Comparator.comparing(TacheDossier::getDateFin)
                        .thenComparing(TacheDossier::getIdTache,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
    }

    /** Fin du dernier passage par une étape — borne du compteur global. */
    private LocalDateTime borne(List<TacheDossier> taches, EtapeCircuit etape) {
        TacheDossier dernier = dernierPassage(taches, etape);
        return dernier == null ? null : dernier.getDateFin();
    }

    /**
     * « Prénoms Nom » nu d'un matricule, résolu dans un annuaire déjà chargé (aucune requête) ; le
     * matricule lui-même s'il n'y figure pas ou n'y a pas de nom ; {@code null} sans matricule.
     */
    public static String nomNu(Map<String, Controleur> annuaire, String im) {
        if (im == null || im.isBlank()) {
            return null;
        }
        Controleur c = annuaire == null ? null : annuaire.get(im);
        String nom = c == null ? null : nomNu(c);
        return nom != null ? nom : im;
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
        return controleurRepository.findById(imActeur).map(ChronometrageService::nomNu).orElse(null);
    }

    /** « Prénoms Nom » nu d'un contrôleur — la seule composition du nom pour la frise ; {@code null} si vide. */
    public static String nomNu(Controleur c) {
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
     * État du PV le plus récent, par dossier — une seule requête : son statut et ses parts signées
     * (⚠️ 2026-09-13, pour {@code acteursEtapes.PV_SIGNE}). La liste étant ordonnée par {@code idPv}
     * croissant, la dernière valeur écrite pour un dossier est bien la plus récente.
     *
     * <p>Une part n'est retenue que <strong>datée</strong> : {@code imCtrlCc} et {@code imCtrlPresident}
     * sont aussi posés à la création du PV (le circuit), ils ne disent pas qu'on a signé. La part Membre
     * est celle du co-signataire désigné ; à défaut (PV antérieurs à la co-signature du 2026-08-28),
     * l'attributaire, qui signait alors lui-même.</p>
     */
    @Transactional(readOnly = true)
    public Map<Integer, EtatPv> etatsPvParDossier(Collection<Integer> idsDossiers) {
        Map<Integer, EtatPv> parDossier = new HashMap<>();
        if (idsDossiers == null || idsDossiers.isEmpty()) {
            return parDossier;
        }
        for (Object[] l : pvExamenRepository.etatsPvParDossiers(idsDossiers)) {
            if (l.length < 9 || l[0] == null || l[1] == null) {
                continue;
            }
            String coSignataire = (String) l[3];
            String imMembre = l[4] == null ? null
                    : (coSignataire != null && !coSignataire.isBlank() ? coSignataire : (String) l[2]);
            String imCc = l[6] == null ? null : (String) l[5];
            String imPresident = l[8] == null ? null : (String) l[7];
            parDossier.put((Integer) l[0], new EtatPv((String) l[1], imMembre, imCc, imPresident));
        }
        return parDossier;
    }

    /**
     * Attributaire courant ({@code imCtrlMembre} du dispatch, réattributions comprises) de plusieurs
     * dossiers — une seule requête pour toute une liste (⚠️ 2026-09-13, pour {@code acteursEtapes.DISPATCH}).
     * Même source que {@code ChronometrageDto.attributaire}.
     */
    @Transactional(readOnly = true)
    public Map<Integer, String> attributairesParDossier(Collection<Integer> idsDossiers) {
        Map<Integer, String> parDossier = new HashMap<>();
        if (idsDossiers == null || idsDossiers.isEmpty()) {
            return parDossier;
        }
        for (Object[] ligne : dispatchRepository.findAttributairesParDossiers(idsDossiers)) {
            if (ligne.length >= 2 && ligne[0] != null && ligne[1] != null && !((String) ligne[1]).isBlank()) {
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
