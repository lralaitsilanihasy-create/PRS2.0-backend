package cnm.prs.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.PvExamen;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.TacheDossierRepository;
import cnm.prs.security.CurrentUser;

/**
 * Garde-fous d'intégrité d'un dossier de saisie (§3.1) — appliqués dans un service <strong>partagé</strong>,
 * donc valables sur <em>toutes</em> les voies (façade de saisie ET endpoints granulaires) :
 *
 * <ul>
 *   <li><strong>Propriété</strong> : seule la PRMP propriétaire ({@code t_dossier.ID_PRMP}) édite/soumet
 *       son dossier ;</li>
 *   <li><strong>Éditabilité</strong> : on ne modifie que les dossiers au statut {@code BROUILLON} ;</li>
 *   <li><strong>Cohérence type↔contenu</strong> : un dossier de la famille {@code DDP} (planification)
 *       doit porter un {@code t_ppm} ; un dossier {@code DMC}/{@code DDM} ne doit pas en porter.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class DossierIntegriteService {

    /**
     * ⚠️ Règle ajoutée — codes des <strong>familles</strong> de dossier ({@code tr_type_dossier}) après la
     * restructuration famille → sous-type : {@code DDP} « Dossier de Planification » (ex-PPM),
     * {@code DMC} « Dossier de Mise en Concurrence » (ex-DAO), {@code DDM} « Dossier de Marché » (ex-MAOO).
     * Source unique des littéraux — ne pas dupliquer ces codes ailleurs.
     */
    public static final String FAMILLE_DDP = "DDP";
    public static final String FAMILLE_DMC = "DMC";
    public static final String FAMILLE_DDM = "DDM";

    /** Sous-types ({@code tr_sous_type_dossier}) de la famille DDP — gérés par le serveur (dérivés). */
    public static final String SOUS_TYPE_PPM = "PPM";
    public static final String SOUS_TYPE_PPM_AGPM = "PPM-AGPM";

    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final MarcheRepository marcheRepository;
    private final EntiteContractRepository entiteContractRepository;
    private final PrmpEntiteRepository prmpEntiteRepository;
    private final MandatService mandatService;
    /** ⚠️ 2026-09-07 — la référence suit le sous-type tant qu'aucun PV n'est signé (cf. propagation). */
    private final PvExamenRepository pvExamenRepository;
    /** ⚠️ 2026-09-07 (suite) — « ce plan requiert-il un AGPM ? », source unique (seuil AMI administrable). */
    private final AgpmService agpmService;
    /** ⚠️ 2026-09-07 — « aucune action sans prise en charge » : la tâche RECTIFICATION_PRMP fait foi. */
    private final TacheDossierRepository tacheRepository;

    public DossierIntegriteService(DossierRepository dossierRepository, PpmRepository ppmRepository,
            MarcheRepository marcheRepository, EntiteContractRepository entiteContractRepository,
            PrmpEntiteRepository prmpEntiteRepository, MandatService mandatService,
            PvExamenRepository pvExamenRepository, AgpmService agpmService,
            TacheDossierRepository tacheRepository) {
        this.tacheRepository = tacheRepository;
        this.agpmService = agpmService;
        this.pvExamenRepository = pvExamenRepository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.marcheRepository = marcheRepository;
        this.entiteContractRepository = entiteContractRepository;
        this.prmpEntiteRepository = prmpEntiteRepository;
        this.mandatService = mandatService;
    }

    /**
     * Localité d'un dossier = celle de l'<strong>entité contractante</strong> choisie (§1). Vérifie
     * que l'entité existe, qu'elle fait partie des entités <strong>actives</strong> de la PRMP courante
     * (sinon 403) et qu'elle porte une localité (sinon 400). Source unique de la localité d'un dossier.
     */
    public String localiteDeLEntiteDeLaPrmp(Integer idEntiteContract, String idPrmp) {
        EntiteContract entite = entiteContractRepository.findById(idEntiteContract)
                .orElseThrow(() -> new BadRequestException(
                        "Entité contractante introuvable : " + idEntiteContract + "."));
        if (idPrmp == null
                || !prmpEntiteRepository.existsByIdPrmpAndIdEntiteContractAndActifTrue(idPrmp, idEntiteContract)) {
            throw new AccessDeniedException(
                    "Cette entité ne fait pas partie de vos entités contractantes (§3.1).");
        }
        String localite = entite.getIdLocalite();
        if (localite == null || localite.isBlank()) {
            throw new BadRequestException(
                    "L'entité « " + entite.getLibelleEntite() + " » n'a pas de localité : saisie impossible (§1).");
        }
        return localite;
    }

    /**
     * Charge un dossier modifiable : il existe, appartient à la PRMP courante et est au statut
     * {@code BROUILLON}. Utilisé avant toute édition (en-tête PPM, lignes de marché).
     *
     * @throws ResourceNotFoundException si le dossier n'existe pas
     * @throws AccessDeniedException     si le dossier ne lui appartient pas (→ 403)
     * @throws BusinessRuleException     si le dossier n'est pas un brouillon (→ 409)
     */
    public Dossier exigerBrouillonModifiable(Integer idDossier) {
        Dossier dossier = charger(idDossier);
        exigerOperateurHabilite(dossier);
        if (!StatutDossier.BROUILLON.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "Opération impossible : le dossier n'est pas un brouillon (statut « " + dossier.getStatut() + " »).");
        }
        return dossier;
    }

    /**
     * ⚠️ Règle ajoutée (2026-08-02, rectification par import) — MISE À JOUR d'un PPM / d'une ligne de
     * marché acceptée au statut {@code BROUILLON} (flux brouillon) <strong>ou</strong>
     * {@code EN_ATTENTE_DECISION_PRMP} (rectification : la PRMP importe le PPM rectifié, la façade
     * d'édition met les lignes à jour EN PLACE — création/suppression restent réservées au brouillon,
     * la structure d'un dossier examiné est figée).
     */
    public Dossier exigerModifiablePourEditionPpm(Integer idDossier) {
        Dossier dossier = charger(idDossier);
        exigerOperateurHabilite(dossier);
        String statut = dossier.getStatut();
        if (!StatutDossier.BROUILLON.name().equals(statut)
                && !StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(statut)) {
            throw new BusinessRuleException(
                    "Opération impossible : le dossier n'est ni un brouillon ni en attente de décision PRMP "
                            + "(statut « " + statut + " »).");
        }
        // Un brouillon s'édite librement ; une RECTIFICATION est un geste du circuit, donc prise en charge.
        if (StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(statut)) {
            exigerRectificationPriseEnCharge(idDossier);
        }
        return dossier;
    }

    /**
     * Charge un dossier <strong>rectifiable</strong> (édition restreinte) : il existe, appartient à la
     * PRMP courante et est au statut {@code EN_ATTENTE_DECISION_PRMP}. La PRMP corrige le contenu sans
     * repasser par le brouillon ; le statut reste {@code EN_ATTENTE_DECISION_PRMP} jusqu'à la resoumission.
     *
     * @throws ResourceNotFoundException si le dossier n'existe pas
     * @throws AccessDeniedException     si le dossier ne lui appartient pas (→ 403)
     * @throws BusinessRuleException     si le dossier n'est pas EN_ATTENTE_DECISION_PRMP (→ 409)
     */
    public Dossier exigerEnAttenteDecisionPrmpModifiable(Integer idDossier) {
        Dossier dossier = charger(idDossier);
        exigerOperateurHabilite(dossier);
        if (!StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "Rectification impossible : le dossier n'est pas en attente de décision PRMP (statut « "
                            + dossier.getStatut() + " »).");
        }
        exigerRectificationPriseEnCharge(idDossier);
        return dossier;
    }

    /**
     * ⚠️ <strong>Règle pilote (2026-09-07) — « aucune action sans prise en charge », étendue à la
     * PRMP</strong> : pendant {@code EN_ATTENTE_DECISION_PRMP}, l'étape {@code RECTIFICATION_PRMP} est
     * ouverte et revient à la PRMP propriétaire. Tant qu'elle ne l'a pas <em>prise en charge</em>, aucune
     * rectification ne passe — ni l'édition (façade de saisie, import du PPM rectifié, lignes de marché),
     * ni la resoumission.
     *
     * <p><strong>Pourquoi la garde vit ici et pas seulement sur la resoumission.</strong> Le front
     * verrouille deux boutons, « Modifier le dossier » et « Resoumettre » ; ne fermer que le second
     * aurait laissé le premier cosmétique — la rectification elle-même serait passée par l'API sans
     * qu'aucune tâche ne soit ouverte, et le geste qu'on cherche à horodater n'aurait pas eu lieu.</p>
     *
     * <p>La garde lit les <strong>tâches</strong> et non le chronométrage complet : {@code
     * ChronometrageService} dépend déjà de ce service, l'inverse ferait un cycle.</p>
     *
     * @throws BusinessRuleException 409 tant que l'étape n'est pas prise en charge
     */
    public void exigerRectificationPriseEnCharge(Integer idDossier) {
        if (rectificationPriseEnCharge(idDossier)) {
            return;
        }
        throw new BusinessRuleException("Prenez d'abord en charge la rectification de ce dossier "
                + "(« Prendre en charge ») : elle ouvre votre tâche, et vous permet de rectifier puis "
                + "de resoumettre.");
    }

    /** Vrai si l'étape {@code RECTIFICATION_PRMP} du dossier est ouverte (prise en charge, non close). */
    public boolean rectificationPriseEnCharge(Integer idDossier) {
        return idDossier != null
                && !tacheRepository.ouvertes(idDossier, EtapeCircuit.RECTIFICATION_PRMP.name()).isEmpty();
    }

    /**
     * Exige que le dossier appartienne à la PRMP courante (si une PRMP propriétaire est connue).
     *
     * <p>⚠️ Règle ajoutée (spec « Mandats PRMP ») — la garde accepte <strong>deux</strong> titres :
     * la PRMP <em>d'attribution</em> ({@code t_dossier.ID_PRMP}, figée à la création) et la PRMP
     * <em>en fonction</em> sur le périmètre du dossier. C'est ce second titre qui permet la
     * <strong>reprise du traitement</strong> après un changement de PRMP : le successeur agit sur les
     * dossiers de l'UGPM sans qu'aucune réattribution rétroactive n'ait lieu — {@code ID_PRMP} et
     * {@code ID_MANDAT_ATTRIB} restent ceux du prédécesseur.</p>
     */
    public void exigerProprietaire(Dossier dossier) {
        String courant = CurrentUser.ref().orElse(null);
        if (dossier.getIdPrmp() == null || dossier.getIdPrmp().equals(courant)) {
            return;
        }
        if (estPrmpEnFonctionSurLeDossier(dossier, courant)) {
            return;
        }
        throw new AccessDeniedException("Vous n'êtes pas le propriétaire de ce dossier (§3.1).");
    }

    /**
     * Vrai si {@code idPrmp} est la PRMP <strong>en fonction</strong> sur le périmètre du dossier :
     * mandat actif aujourd'hui <em>et</em> affectation active sur l'entité contractante du dossier
     * ({@code t_prmp_entite}), qui matérialise le rattachement du poste après la passation de témoin.
     *
     * <p>Volontairement <strong>strict</strong> : c'est un titre à part entière, pas un repli permissif.
     * Un dossier sans entité contractante, ou une PRMP sans mandat actif, ne l'obtiennent pas. Les
     * appelants qui ont leur propre garde de propriété s'en servent comme extension, sans en hériter la
     * tolérance de {@link #exigerProprietaire} envers les dossiers sans propriétaire connu.</p>
     */
    public boolean estPrmpEnFonctionSurLeDossier(Dossier dossier, String idPrmp) {
        if (idPrmp == null || idPrmp.isBlank() || dossier.getIdEntiteContract() == null) {
            return false;
        }
        return prmpEntiteRepository.existsByIdPrmpAndIdEntiteContractAndActifTrue(idPrmp,
                        dossier.getIdEntiteContract())
                && mandatService.estEnFonction(idPrmp);
    }

    /**
     * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — <strong>standby de transition</strong> : aucune action de
     * traitement n'est possible tant qu'aucune PRMP n'est en fonction. Il n'y a pas d'intérim : l'action
     * attend la nomination, puis sera faite par le nouveau titulaire <em>en tant qu'opérateur</em>, sans
     * toucher à l'attribution du dossier. Le déblocage est automatique — rien à rejouer.
     *
     * <p>Ne concerne que les acteurs côté PRMP (PRMP et agents UGPM sous sa tutelle) : le circuit interne
     * CNM n'est pas suspendu par la vacance d'une PRMP.</p>
     *
     * @throws cnm.prs.exception.VacancePrmpException 409 {@code VACANCE_PRMP}
     */
    public void exigerMandatActif() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM) {
            return;
        }
        mandatService.exigerMandatActif(CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null));
    }

    /**
     * Garde complète d'une <strong>action de traitement</strong> sur un dossier : propriété (attribution ou
     * PRMP en fonction) <em>et</em> habilitation (un mandat actif à la date de l'action).
     */
    public void exigerOperateurHabilite(Dossier dossier) {
        exigerProprietaire(dossier);
        exigerMandatActif();
    }

    /** Un PPM (et ses lignes de marché) ne peut être rattaché qu'à un dossier de la famille {@code DDP}. */
    public void exigerFamilleDdp(Integer idDossier) {
        Dossier dossier = charger(idDossier);
        if (!FAMILLE_DDP.equals(dossier.getIdTypeDossier())) {
            throw new BusinessRuleException(
                    "PPM/marché interdit : le dossier " + idDossier + " n'est pas de la famille DDP (type « "
                            + dossier.getIdTypeDossier() + " »).");
        }
    }

    /**
     * ⚠️ Règle ajoutée — <strong>recalcule le sous-type d'un dossier DDP</strong> (source de vérité unique :
     * les marchés) : {@link #SOUS_TYPE_PPM_AGPM} ssi ≥1 marché en « appel d'offres ouvert »
     * ({@code ModePassation.declencheAgpm}), sinon {@link #SOUS_TYPE_PPM}. Sans effet sur les autres
     * familles (DMC/DDM : sous-type choisi à la saisie). À appeler après toute écriture de marché
     * (création / mise à jour / suppression / rectification) et en filet de sécurité à la soumission.
     */
    @Transactional
    public void recalculerSousTypeDdp(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier).orElse(null);
        if (dossier == null || !FAMILLE_DDP.equals(dossier.getIdTypeDossier())) {
            return;
        }
        String sousType = agpmService.requisPourDossier(idDossier) ? SOUS_TYPE_PPM_AGPM : SOUS_TYPE_PPM;
        if (!sousType.equals(dossier.getIdSousType())) {
            dossier.setIdSousType(sousType);
            dossierRepository.save(dossier);
            propagerSousTypeAuxReferences(dossier, sousType);
        }
    }

    /**
     * ⚠️ Arbitrage pilote (2026-09-07) — <strong>la référence porte le sous-type dérivé</strong> :
     * {@code …/PPM-AGPM/…} dès qu'un marché est passé par appel d'offres, {@code …/PPM/…} sinon. Le segment
     * est recomposé <strong>sans consommer de numéro</strong> (le compteur et l'acronyme ne bougent pas) sur
     * les trois porteurs de la référence, <strong>ensemble</strong> : le dossier, la référence initiale du
     * PPM (celle que restaure un retrait accepté) et les PV.
     *
     * <p><strong>Pourquoi ensemble.</strong> Le front relie un PV à son dossier en reconstruisant la
     * référence ({@code refePv.replace("/PV/", "/") == refeDossier}) : désaligner les deux casserait la
     * jointure. Ils bougent donc d'un seul geste, ou pas du tout.</p>
     *
     * <p><strong>Où s'arrête la correction.</strong> Dès qu'un PV est <strong>signé</strong>, la référence
     * est <em>gelée</em> : elle est imprimée sur un document officiel, citée dans les lettres et les
     * courriers — la renommer après coup réécrirait l'histoire. Le sous-type, lui, continue de suivre la
     * réalité du plan ; c'est un écart assumé, et le seul cas où il survient est la rectification d'un
     * dossier déjà passé en commission.</p>
     */
    private void propagerSousTypeAuxReferences(Dossier dossier, String sousType) {
        Integer idDossier = dossier.getIdDossier();
        if (!pvExamenRepository.findSignesParDossier(idDossier).isEmpty()) {
            return;   // référence officielle déjà imprimée : elle ne se renomme plus
        }
        String refeDossier = ReferenceService.remplacerSegmentSousType(dossier.getRefeDossier(), sousType);
        if (refeDossier != null && !refeDossier.equals(dossier.getRefeDossier())) {
            dossier.setRefeDossier(refeDossier);
            dossierRepository.save(dossier);
        }
        for (Ppm ppm : ppmRepository.findByIdDossier(idDossier)) {
            String reference = ReferenceService.remplacerSegmentSousType(ppm.getReference(), sousType);
            if (reference != null && !reference.equals(ppm.getReference())) {
                ppm.setReference(reference);
                ppmRepository.save(ppm);
            }
        }
        for (PvExamen pv : pvExamenRepository.findTousParDossier(idDossier)) {
            String refePv = ReferenceService.remplacerSegmentSousType(pv.getRefePv(), sousType);
            if (refePv != null && !refePv.equals(pv.getRefePv())) {
                pv.setRefePv(refePv);
                pvExamenRepository.save(pv);
            }
        }
    }

    /**
     * Cohérence type↔contenu vérifiée à la soumission :
     * <ul>
     *   <li>un dossier {@code PPM} doit porter un {@code t_ppm} ;</li>
     *   <li>un dossier {@code PPM} doit comporter <strong>au moins une ligne de marché</strong>
     *       (⚠️ règle ajoutée, non littérale dans {@code regles-gestion.md} — un PPM est un plan de
     *       passation de marchés ; un PPM vide n'a rien à contrôler) ;</li>
     *   <li>un {@code DAO}/{@code MAOO} ne doit pas porter de {@code t_ppm} (et n'est pas concerné par
     *       la règle des marchés).</li>
     * </ul>
     */
    public void validerCoherenceAvantSoumission(Dossier dossier) {
        boolean aPpm = ppmRepository.existsByIdDossier(dossier.getIdDossier());
        boolean estDdp = FAMILLE_DDP.equals(dossier.getIdTypeDossier());
        if (estDdp && !aPpm) {
            throw new BusinessRuleException(
                    "Dossier de la famille DDP sans PPM rattaché : soumission impossible (§3.1).");
        }
        if (!estDdp && aPpm) {
            throw new BusinessRuleException(
                    "Dossier de type « " + dossier.getIdTypeDossier() + " » ne doit pas porter de PPM.");
        }
        // ⚠️ Règle ajoutée : un PPM doit comporter au moins un marché avant soumission.
        if (estDdp && !marcheRepository.existsByIdDossier(dossier.getIdDossier())) {
            throw new BusinessRuleException(
                    "Un PPM doit comporter au moins un marché avant soumission.");
        }
    }

    private Dossier charger(Integer idDossier) {
        return dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
    }
}
