package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DiffDossierDto;
import cnm.prs.dto.DossierDto;
import cnm.prs.dto.EditionPpmRequest;
import cnm.prs.dto.SaisieBeneficiaireLigne;
import cnm.prs.dto.SaisieLotLigne;
import cnm.prs.dto.SaisieMarcheLigne;
import cnm.prs.dto.ProcessusMarche;
import cnm.prs.dto.SaisiePpmImportResult;
import cnm.prs.entity.Capm;
import cnm.prs.entity.ChangementLigne;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeChangementLigne;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DossierMapper;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.ChangementLigneRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>Mise à jour d'un PPM (règle ajoutée 2026-08-05)</strong> — versionnement d'un dossier de
 * planification : une mise à jour ne modifie JAMAIS le dossier en place, elle crée une nouvelle version.
 *
 * <p><strong>Ne pas confondre avec la rectification</strong> ({@code SaisieService.editerPpm} sur un
 * dossier {@code EN_ATTENTE_DECISION_PRMP}), qui corrige la version courante en réponse aux observations
 * du PV — même dossier, même identité. Ici il s'agit d'un PPM déjà validé que la PRMP fait évoluer :
 * nouveau dossier, nouvelle instruction, historique conservé.</p>
 *
 * <h3>Cycle</h3>
 * <ol>
 *   <li>{@link #creerMiseAJour} copie le dossier en vigueur dans un {@code BROUILLON} rattaché par
 *       {@code ID_DOSSIER_PARENT}, incrémente {@code NUM_MAJ} et exige un motif.</li>
 *   <li>La PRMP édite ce brouillon comme n'importe quel autre (grille de saisie existante), et peut
 *       supprimer/restaurer des lignes ({@link #supprimerLigne} / {@link #restaurerLigne}).</li>
 *   <li>{@link #diff} alimente l'aperçu, recalculé à chaque appel tant que la version est brouillon.</li>
 *   <li>À la soumission, {@link #figerDiffEtRemplacerParent} écrit la trace et bascule le prédécesseur
 *       en {@link StatutDossier#REMPLACE}. <strong>Avant</strong> ce moment, une mise à jour abandonnée
 *       se supprime sans laisser de trace ni neutraliser le dossier en vigueur.</li>
 * </ol>
 */
@Service
public class MiseAJourPpmService {

    /**
     * Statuts depuis lesquels une mise à jour est ouverte : la Commission a rendu sa décision, le PPM est
     * en vigueur. Un dossier encore dans le circuit se corrige (rectification), il ne se versionne pas.
     */
    private static final List<String> STATUTS_MISE_A_JOUR_OUVERTE =
            List.of(StatutDossier.DECISION_TRANSMISE_SIGMP.name(), StatutDossier.CLOTURE.name());

    /**
     * Champs scalaires comparés d'une version à l'autre. Les collections rattachées (bénéficiaires, lots,
     * dates prévisionnelles) sont comparées par empreinte normalisée et remontent comme un champ unique —
     * une différence y est signalée, sans détail élément par élément.
     */
    /**
     * ⚠️ 2026-08-05 (demande user) — référence portée par une mise à jour EN COURS. Le compteur officiel
     * n'est consommé qu'à la soumission : ouvrir une mise à jour ne rend rien effectif, et l'abandonner
     * ne laisse pas de trou dans la numérotation. {@code t_ppm.REFERENCE} étant NOT NULL, il faut une
     * valeur — celle-ci est reconnaissable et remplacée par {@link #rendreEffective}.
     */
    public static final String REFERENCE_PROVISOIRE = "(mise à jour en cours)";

    /** Statut d'une ligne de marché neuve, aligné sur le défaut de la saisie initiale. */
    private static final String STATUT_MARCHE_DEFAUT = "PREVU";

    /** Types de pièce du référentiel DDP mobilisés par le versionnement (cf. `t_type_piece_jointe`). */
    private static final Integer TYPE_PPM_SIGNE = 1;          // « Plan de passation des marchés daté et signé »
    private static final Integer TYPE_PV_PRECEDENT = 22;      // « PV du dossier précédent »
    private static final Integer TYPE_PPM_ANTERIEUR = 23;     // « PPM antérieur daté et signé »
    /** Pièces reconstituées à chaque version : jamais recopiées du prédécesseur (sinon empilement). */
    private static final List<Integer> TYPES_HISTORIQUE = List.of(TYPE_PV_PRECEDENT, TYPE_PPM_ANTERIEUR);

    private static final List<String> CHAMPS_COMPARES = List.of(
            "designationMarche", "montEstim", "nouvMontEstim", "numCompte", "financement",
            "statut", "idNature", "idMode", "formeMarche", "beneficiaires", "lots", "processus");

    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final MarcheRepository marcheRepository;
    private final LotRepository lotRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    private final PieceJointeDossierRepository pieceJointeDossierRepository;
    private final ChangementLigneRepository changementLigneRepository;
    private final ReferenceService referenceService;
    private final EntiteContractRepository entiteContractRepository;
    /** Façade de saisie : l'import d'une version délègue sa persistance à l'édition existante. */
    private final SaisieService saisieService;
    /** Référentiels nécessaires à la résolution des étapes du PDF (mêmes règles qu'à la création). */
    private final CapmRepository capmRepository;
    private final ModePassationRepository modePassationRepository;
    /** Dossier historique de la version : PV du prédécesseur + PPM des versions antérieures. */
    private final PvExamenRepository pvExamenRepository;
    private final PvExamenService pvExamenService;
    /** ⚠️ Spec « Mandats PRMP » — garde de propriété partagée (attribution OU PRMP en fonction). */
    private final DossierIntegriteService dossierIntegrite;

    public MiseAJourPpmService(DossierRepository dossierRepository, PpmRepository ppmRepository,
            MarcheRepository marcheRepository, LotRepository lotRepository,
            MarchePrevisionRepository marchePrevisionRepository,
            ServiceBeneficiaireRepository serviceBeneficiaireRepository,
            PieceJointeDossierRepository pieceJointeDossierRepository,
            ChangementLigneRepository changementLigneRepository, ReferenceService referenceService,
            EntiteContractRepository entiteContractRepository, SaisieService saisieService,
            CapmRepository capmRepository, ModePassationRepository modePassationRepository,
            PvExamenRepository pvExamenRepository, PvExamenService pvExamenService,
            DossierIntegriteService dossierIntegrite) {
        this.dossierIntegrite = dossierIntegrite;
        this.saisieService = saisieService;
        this.capmRepository = capmRepository;
        this.modePassationRepository = modePassationRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.pvExamenService = pvExamenService;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.marcheRepository = marcheRepository;
        this.lotRepository = lotRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.pieceJointeDossierRepository = pieceJointeDossierRepository;
        this.changementLigneRepository = changementLigneRepository;
        this.referenceService = referenceService;
        this.entiteContractRepository = entiteContractRepository;
    }

    // ------------------------------------------------------------------ création de la version

    /**
     * Crée la version n+1 d'un PPM en vigueur : nouveau dossier {@code BROUILLON}, copie profonde du PPM,
     * des lignes de marché (avec leurs lots, bénéficiaires et dates prévisionnelles) et des pièces jointes.
     *
     * @param idDossierSource le dossier en vigueur à faire évoluer
     * @param motif           motif métier, obligatoire (traçabilité)
     * @throws AccessDeniedException  si la PRMP courante n'est pas propriétaire du dossier
     * @throws BusinessRuleException  motif vide (400), statut incompatible (409), dossier déjà remplacé
     *                                (409) ou mise à jour déjà en cours (409)
     */
    @Transactional
    public DossierDto creerMiseAJour(Integer idDossierSource, String motif) {
        Dossier source = dossierRepository.findById(idDossierSource)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossierSource));
        exigerOperateurHabilite(source);
        if (motif == null || motif.isBlank()) {
            throw new BusinessRuleException("Le motif de la mise à jour est obligatoire.");
        }
        if (StatutDossier.REMPLACE.name().equals(source.getStatut())) {
            throw new BusinessRuleException(
                    "Ce dossier a déjà été remplacé par une version postérieure : mettez à jour la version en vigueur.");
        }
        if (!STATUTS_MISE_A_JOUR_OUVERTE.contains(source.getStatut())) {
            throw new BusinessRuleException(
                    "Une mise à jour n'est possible que sur un PPM dont la Commission a rendu sa décision "
                            + "(statut actuel : " + source.getStatut() + ").");
        }
        dossierRepository.findByIdDossierParent(idDossierSource).stream()
                .filter(d -> StatutDossier.BROUILLON.name().equals(d.getStatut()))
                .findFirst()
                .ifPresent(d -> {
                    throw new BusinessRuleException(
                            "Une mise à jour de ce dossier est déjà en cours (brouillon #" + d.getIdDossier() + ").");
                });

        Ppm ppmSource = ppmRepository.findByIdDossier(idDossierSource).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Ce dossier ne porte aucun PPM : mise à jour impossible."));
        // ⚠️ 2026-08-05 — le PV du prédécesseur est une pièce EXIGÉE de la mise à jour : on le vérifie
        // ici plutôt que de laisser la PRMP buter à la soumission sur un dossier qu'elle ne peut pas
        // compléter. En circuit normal un plan clôturé porte toujours son PV signé.
        if (pvExamenRepository.findSignesParDossierRows(idDossierSource).isEmpty()) {
            throw new BusinessRuleException(
                    "Le PV signé de ce dossier est introuvable : la mise à jour exige le PV du dossier précédent.");
        }

        Dossier cible = copierDossier(source);
        Ppm ppmCible = copierPpm(ppmSource, cible, motif);
        copierLignes(source, cible, ppmCible);
        // Les pièces du dossier d'origine sont reprises : la PRMP ne remplace que celles qui changent.
        copierPiecesJointes(source.getIdDossier(), cible.getIdDossier());
        joindreDossierHistorique(source, cible);
        return DossierMapper.toDto(cible);
    }

    /** Nouveau dossier BROUILLON, rattaché au prédécesseur. Référence et date de réf. restent à venir. */
    private Dossier copierDossier(Dossier source) {
        Dossier cible = new Dossier();
        cible.setIdDossier(dossierRepository.nextIdDossier().intValue());
        cible.setIdTypeDossier(source.getIdTypeDossier());
        cible.setIdSousType(source.getIdSousType());
        cible.setIdLocalite(source.getIdLocalite());
        cible.setIdPrmp(source.getIdPrmp());
        // ⚠️ Spec « Mandats PRMP » — la version n+1 hérite de l'attribution de la lignée (PRMP ET mandat) :
        // une mise à jour prolonge un plan existant, elle ne le réattribue pas au titulaire du moment.
        cible.setIdMandatAttrib(source.getIdMandatAttrib());
        cible.setIdEntiteContract(source.getIdEntiteContract());
        cible.setStatut(StatutDossier.BROUILLON.name());
        cible.setIdDossierParent(source.getIdDossier());
        // refeDossier / dateRef : attribués à la réception, comme pour tout dossier neuf.
        return dossierRepository.save(cible);
    }

    /**
     * Copie l'en-tête du PPM en incrémentant le compteur de mise à jour. {@code datePpmInit} remonte à la
     * version initiale : elle se propage inchangée, à défaut c'est la date de signature de la source.
     */
    private Ppm copierPpm(Ppm source, Dossier cible, String motif) {
        Ppm ppm = new Ppm();
        ppm.setIdPpm(ppmRepository.nextIdPpm().intValue());
        ppm.setIdDossier(cible.getIdDossier());
        ppm.setIdPrmp(source.getIdPrmp());
        ppm.setExercice(source.getExercice());
        ppm.setSignataire(source.getSignataire());
        ppm.setDateSignature(source.getDateSignature());   // modifiable ensuite dans le brouillon
        ppm.setIdLocalite(source.getIdLocalite());
        ppm.setLibelle(source.getLibelle());
        // ⚠️ 2026-08-05 (demande user) — la référence n'est PAS consommée ici : ouvrir une mise à jour ne
        // doit rien rendre effectif. Un compteur brûlé par une mise à jour abandonnée laisserait un trou
        // dans la numérotation officielle. La vraie référence est attribuée à la SOUMISSION.
        ppm.setReference(REFERENCE_PROVISOIRE);

        int numPrecedent = source.getNumMaj() == null ? 0 : source.getNumMaj();
        ppm.setDatePpmInit(source.getDatePpmInit() != null ? source.getDatePpmInit() : source.getDateSignature());
        ppm.setNumMajPrec(numPrecedent);
        ppm.setDateMajPrec(source.getDateMaj());
        ppm.setNumMaj(numPrecedent + 1);
        ppm.setDateMaj(LocalDate.now());
        ppm.setMotifMaj(motif);
        return ppmRepository.save(ppm);
    }

    /**
     * Recopie chaque ligne de marché sous une nouvelle PK, en conservant son identité inter-versions
     * ({@code idLigneOrigine}) et son état de suppression : une ligne supprimée dans la version
     * précédente reste présente et restaurable dans la nouvelle.
     */
    private void copierLignes(Dossier source, Dossier cible, Ppm ppmCible) {
        // ⚠️ Marché, lot, prévision et bénéficiaire viennent tous d'une SÉQUENCE serveur : la consommer
        // à CHAQUE ligne. L'allouer une fois puis incrémenter localement laisse la séquence en retard —
        // la création suivante réattribue les mêmes identifiants et ÉCRASE les copies (save() sur PK
        // existante = update). Les trois compteurs locaux qui subsistaient ici pour les lignes filles
        // (seqLot / seqPrevision / seqBenef) sont supprimés au profit de leurs séquences.
        for (Marche origine : marcheRepository.findByIdDossier(source.getIdDossier())) {
            Marche copie = new Marche();
            copie.setIdDetail(marcheRepository.nextIdMarche().intValue());
            copie.setIdLigneOrigine(origine.getIdLigneOrigine());   // getter coalescent : jamais null
            copie.setSupprimee(origine.getSupprimee());
            copie.setIdDossier(cible.getIdDossier());
            copie.setIdPpm(ppmCible.getIdPpm());
            copie.setDesignationMarche(origine.getDesignationMarche());
            copie.setNumCompte(origine.getNumCompte());
            copie.setMontEstim(origine.getMontEstim());
            copie.setAncienMontEstim(origine.getAncienMontEstim());
            copie.setNouvMontEstim(origine.getNouvMontEstim());
            copie.setFinancement(origine.getFinancement());
            copie.setStatut(origine.getStatut());
            copie.setIdNature(origine.getIdNature());
            copie.setIdMode(origine.getIdMode());
            copie.setFormeMarche(origine.getFormeMarche());
            marcheRepository.save(copie);

            for (Lot lot : lotRepository.findByIdDetail(origine.getIdDetail())) {
                Lot c = new Lot();
                c.setIdLot(lotRepository.nextIdLot().intValue());   // séquence consommée à CHAQUE ligne
                c.setIdDossier(cible.getIdDossier());
                c.setIdDetail(copie.getIdDetail());
                c.setDesignationLot(lot.getDesignationLot());
                c.setMontLot(lot.getMontLot());
                c.setQteLot(lot.getQteLot());
                c.setUniteLot(lot.getUniteLot());
                lotRepository.save(c);
            }
            for (MarchePrevision p : marchePrevisionRepository.findByIdDetail(origine.getIdDetail())) {
                MarchePrevision c = new MarchePrevision();
                c.setIdPrevision(marchePrevisionRepository.nextIdMarchePrevision().intValue());
                c.setIdDetail(copie.getIdDetail());
                c.setIdCapm(p.getIdCapm());
                c.setDateDebut(p.getDateDebut());
                c.setDateFin(p.getDateFin());
                marchePrevisionRepository.save(c);
            }
            for (ServiceBeneficiaire b : serviceBeneficiaireRepository.findByIdDetail(origine.getIdDetail())) {
                ServiceBeneficiaire c = new ServiceBeneficiaire();
                c.setIdBenef(serviceBeneficiaireRepository.nextIdBenef().intValue());
                c.setIdDetail(copie.getIdDetail());
                c.setSoaCode(b.getSoaCode());
                c.setNumCompte(b.getNumCompte());
                c.setAncMontBenef(b.getAncMontBenef());
                c.setNouvMontBenef(b.getNouvMontBenef());
                serviceBeneficiaireRepository.save(c);
            }
        }
    }

    /**
     * Reprend les pièces jointes du dossier d'origine (contenu dupliqué). Le drapeau
     * {@code apresLettreRenvoi} et le rattachement à une lettre ne sont PAS repris : ils qualifient
     * l'instruction du dossier précédent, pas celle qui commence.
     */
    /**
     * Reprend les pièces jointes du dossier d'origine (contenu dupliqué) : la PRMP ne remplace ensuite
     * que celles qui changent. Le drapeau {@code apresLettreRenvoi} et le rattachement à une lettre ne
     * sont PAS repris : ils qualifient l'instruction du dossier précédent, pas celle qui commence.
     */
    private void copierPiecesJointes(Integer idSource, Integer idCible) {
        for (PieceJointeDossier p : pieceJointeDossierRepository.findByIdDossier(idSource)) {
            // Les pièces d'HISTORIQUE du prédécesseur ne sont pas recopiées telles quelles : elles sont
            // reconstituées depuis la chaîne des versions, sans quoi elles s'empileraient à chaque version.
            if (TYPES_HISTORIQUE.contains(p.getIdTypePiece())) {
                continue;
            }
            PieceJointeDossier c = new PieceJointeDossier();
            // ⚠️ ID_PIECE est en @GeneratedValue(IDENTITY) : l'assigner ferait passer l'entité pour
            // détachée (merge sur une ligne inexistante) au lieu d'être insérée.
            c.setIdDossier(idCible);
            c.setIdTypePiece(p.getIdTypePiece());
            c.setNomFichier(p.getNomFichier());
            c.setContenu(p.getContenu());
            c.setFormat(p.getFormat());
            c.setTaille(p.getTaille());
            c.setDateUpload(p.getDateUpload());
            c.setApresLettreRenvoi(Boolean.FALSE);
            c.setVersionCorrigee(Boolean.FALSE);
            pieceJointeDossierRepository.save(c);
        }
    }

    /**
     * ⚠️ <strong>Dossier historique d'une mise à jour</strong> (demande user 2026-08-05) — une version
     * doit porter, EN PLUS des pièces d'un dossier neuf, le <strong>PV du dossier prédécesseur</strong>
     * et le <strong>PPM daté et signé de chaque version antérieure</strong>.
     *
     * <p>Ces pièces sont <strong>constituées automatiquement</strong> : l'application détient déjà ces
     * documents, il serait absurde de demander à la PRMP de les redéposer. Elles sont donc présentes dès
     * l'ouverture de la version, et la garde de soumission ne fait que le constater.</p>
     */
    private void joindreDossierHistorique(Dossier source, Dossier cible) {
        // ① PV signé du prédécesseur (le plus récent s'il y a eu plusieurs navettes).
        // Idempotent : la méthode sert aussi à réparer une version incomplète (cf. exigerDossierHistorique).
        if (!pieceJointeDossierRepository.existsByIdDossierAndIdTypePiece(cible.getIdDossier(), TYPE_PV_PRECEDENT)) {
            pvExamenRepository.findSignesParDossierRows(source.getIdDossier()).stream().findFirst().ifPresent(pv -> {
                byte[] pdf = pvExamenService.documentPourHistorique(pv.getIdPv());
                if (pdf != null) {
                    enregistrer(cible.getIdDossier(), TYPE_PV_PRECEDENT, pdf,
                            "PV-" + nomFichierSur(pv.getRefePv(), "pv-" + pv.getIdPv()) + ".pdf");
                }
            });
        }
        if (pieceJointeDossierRepository.existsByIdDossierAndIdTypePiece(cible.getIdDossier(), TYPE_PPM_ANTERIEUR)) {
            return;   // les PPM antérieurs sont posés d'un bloc : présents, ils sont complets
        }

        // ② PPM daté et signé de CHAQUE version antérieure, de la plus récente à l'initiale.
        Dossier ancetre = source;
        for (int garde = 0; ancetre != null && garde < 1000; garde++) {
            final Dossier courant = ancetre;
            pieceJointeDossierRepository.findByIdDossier(courant.getIdDossier()).stream()
                    .filter(p -> TYPE_PPM_SIGNE.equals(p.getIdTypePiece()))
                    .findFirst()
                    .ifPresent(p -> enregistrer(cible.getIdDossier(), TYPE_PPM_ANTERIEUR, p.getContenu(),
                            "PPM-" + nomFichierSur(courant.getRefeDossier(), "dossier-" + courant.getIdDossier()) + ".pdf"));
            ancetre = courant.getIdDossierParent() == null ? null
                    : dossierRepository.findById(courant.getIdDossierParent()).orElse(null);
        }
    }

    private void enregistrer(Integer idDossier, Integer idTypePiece, byte[] contenu, String nom) {
        PieceJointeDossier p = new PieceJointeDossier();
        p.setIdDossier(idDossier);
        p.setIdTypePiece(idTypePiece);
        p.setNomFichier(nom);
        p.setContenu(contenu);
        p.setFormat("pdf");
        p.setTaille((long) contenu.length);
        p.setDateUpload(LocalDateTime.now());
        p.setApresLettreRenvoi(Boolean.FALSE);
        p.setVersionCorrigee(Boolean.FALSE);
        pieceJointeDossierRepository.save(p);
    }

    /** Référence utilisable comme nom de fichier (les « / » d'une référence officielle sont interdits). */
    private String nomFichierSur(String reference, String repli) {
        String base = reference == null || reference.isBlank() ? repli : reference;
        return base.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    /**
     * ⚠️ 2026-08-05 — pièces exigées d'une VERSION en plus de celles d'un dossier neuf. Contrôlé ici et
     * pas au référentiel : sur un dossier initial ces pièces n'ont aucun sens (même principe que
     * l'obligation conditionnelle de l'AGPM).
     */
    public void exigerDossierHistorique(Dossier dossier) {
        if (dossier.getIdDossierParent() == null) {
            return;
        }
        // ⚠️ Ces pièces sont constituées par l'application, pas déposées par la PRMP : si elles manquent
        // — version ouverte avant l'entrée en vigueur de la règle, ou pièce supprimée par erreur — on les
        // reconstitue AVANT de contrôler. Sans cela, un brouillon se retrouverait dans une impasse : exigé
        // de fournir un document qu'il n'a aucun moyen de produire.
        boolean pvAbsent = !pieceJointeDossierRepository
                .existsByIdDossierAndIdTypePiece(dossier.getIdDossier(), TYPE_PV_PRECEDENT);
        boolean ppmAbsent = !pieceJointeDossierRepository
                .existsByIdDossierAndIdTypePiece(dossier.getIdDossier(), TYPE_PPM_ANTERIEUR);
        if (pvAbsent || ppmAbsent) {
            dossierRepository.findById(dossier.getIdDossierParent())
                    .ifPresent(parent -> joindreDossierHistorique(parent, dossier));
        }

        List<ErrorResponse.FieldError> manquantes = new ArrayList<>();
        if (!pieceJointeDossierRepository.existsByIdDossierAndIdTypePiece(dossier.getIdDossier(), TYPE_PV_PRECEDENT)) {
            manquantes.add(new ErrorResponse.FieldError("piecesJointes",
                    "Le PV du dossier précédent est obligatoire pour une mise à jour."));
        }
        if (!pieceJointeDossierRepository.existsByIdDossierAndIdTypePiece(dossier.getIdDossier(), TYPE_PPM_ANTERIEUR)) {
            manquantes.add(new ErrorResponse.FieldError("piecesJointes",
                    "Le PPM daté et signé des versions antérieures est obligatoire pour une mise à jour."));
        }
        if (!manquantes.isEmpty()) {
            throw new ChampsInvalidesException(manquantes);
        }
    }

    // ------------------------------------------------------------------ import du PPM mis à jour

    /**
     * ⚠️ <strong>Mise à jour PAR IMPORT du PPM PDF</strong> (demande user 2026-08-05) — une mise à jour
     * arrive comme un document, exactement comme la création : la PRMP importe le PPM modifié, elle ne
     * ressaisit rien. Même principe que la rectification après observations.
     *
     * <p>Le PDF est parsé (façade read-only existante), puis <strong>rapproché</strong> des lignes de la
     * version : chaque ligne importée est appariée à une ligne existante par empreinte métier (libellé
     * normalisé + services bénéficiaires, puis libellé seul), et lui transmet son {@code idDetail}. C'est
     * ce rapprochement qui fait toute la valeur du diff — sans lui, un réimport ferait apparaître le plan
     * entier comme « supprimé puis recréé », et l'identité des lignes serait perdue.</p>
     *
     * <p>La persistance est ensuite <strong>déléguée à {@code editerPpm}</strong> : résolution des
     * référentiels à la volée, validations de montants et de chronologie, création des lignes nouvelles,
     * et suppression LOGIQUE des lignes absentes de l'import (garantie posée côté serveur pour une
     * version). Une ligne réapparue dans le PDF après avoir été supprimée est restaurée.</p>
     *
     * @return le diff recalculé — ce que la PRMP doit vérifier avant de créer la mise à jour
     */
    @Transactional
    public DiffDossierDto appliquerImport(Integer idDossier, SaisiePpmImportResult importe) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        exigerOperateurHabilite(dossier);
        if (dossier.getIdDossierParent() == null) {
            throw new BusinessRuleException("Ce dossier n'est pas une mise à jour : import impossible ici.");
        }
        if (!StatutDossier.BROUILLON.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException("Cette mise à jour a déjà été soumise : elle ne peut plus être réimportée.");
        }
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Aucun PPM rattaché à cette version."));
        exigerMemeEntiteContractante(dossier, importe);

        List<Marche> existantes = new ArrayList<>(marcheRepository.findByIdDossier(idDossier));
        List<Integer> consommees = new ArrayList<>();
        List<SaisieMarcheLigne> lignes = new ArrayList<>();
        for (SaisiePpmImportResult.MarcheImport m : importe.marches() == null ? List.<SaisiePpmImportResult.MarcheImport>of() : importe.marches()) {
            Marche appariee = apparier(m, existantes, consommees);
            if (appariee != null) {
                consommees.add(appariee.getIdDetail());
            }
            lignes.add(versLigne(m, appariee));
        }

        saisieService.editerPpm(idDossier, new EditionPpmRequest(
                importe.exercice() != null ? importe.exercice() : ppm.getExercice(),
                ppm.getSignataire(),
                importe.dateSignature() != null && !importe.dateSignature().isBlank()
                        ? LocalDate.parse(importe.dateSignature()) : ppm.getDateSignature(),
                ppm.getReference(),
                null,                       // motif : inchangé, il ne vient pas du PDF
                lignes));

        // Une ligne réapparue dans le PDF est remise en service (elle avait pu être supprimée avant).
        for (Integer idDetail : consommees) {
            marcheRepository.findById(idDetail).ifPresent(l -> {
                if (l.getSupprimee()) {
                    l.setSupprimee(Boolean.FALSE);
                    marcheRepository.save(l);
                }
            });
        }
        return diff(idDossier);
    }

    /**
     * ⚠️ <strong>Une mise à jour ne change pas d'entité contractante</strong> (demande user 2026-08-06) —
     * l'entité est un attribut HÉRITÉ de la version (verrouillé dans le formulaire). Importer le plan
     * d'une autre entité produirait un dossier incohérent : mêmes identifiants de lignes, mais un tout
     * autre organisme. La garde est ici, au plus près de l'import, et non côté écran.
     *
     * <p>Le refus est prononcé lorsque le document désigne une entité <strong>résolue</strong> différente,
     * ou — si l'autorité contractante lue n'a pas pu être résolue au référentiel — lorsque son libellé ne
     * correspond manifestement pas à celui de l'entité du dossier. La comparaison de libellés est
     * tolérante (casse, accents, ponctuation, inclusion) pour ne pas rejeter une simple variation
     * d'écriture.</p>
     */
    private void exigerMemeEntiteContractante(Dossier dossier, SaisiePpmImportResult importe) {
        Integer idAttendu = dossier.getIdEntiteContract();
        if (idAttendu == null) {
            return; // cas théorique : l'entité est obligatoire à la création, donc toujours héritée.
        }
        String attendu = entiteContractRepository.findById(idAttendu)
                .map(e -> e.getLibelleEntite()).orElse(null);
        Integer idLu = importe.idEntiteContract();
        if (idLu != null) {
            if (!idLu.equals(idAttendu)) {
                String lu = entiteContractRepository.findById(idLu).map(e -> e.getLibelleEntite())
                        .orElse(importe.autoriteContractante());
                throw new BusinessRuleException(refusEntite(lu, attendu));
            }
            return;
        }
        // Entité non résolue : on ne refuse que si les libellés ne se recouvrent manifestement pas.
        String luBrut = importe.autoriteContractante();
        if (luBrut == null || luBrut.isBlank() || attendu == null || attendu.isBlank()) {
            return;
        }
        String a = normaliserEntite(luBrut);
        String b = normaliserEntite(attendu);
        if (!a.isEmpty() && !b.isEmpty() && !a.contains(b) && !b.contains(a)) {
            throw new BusinessRuleException(refusEntite(luBrut, attendu));
        }
    }

    private String refusEntite(String lu, String attendu) {
        return "Ce document concerne « " + nz(lu).trim() + " », alors que le plan mis à jour relève de « "
                + nz(attendu).trim() + " ». Une mise à jour ne peut pas changer d'entité contractante : "
                + "importez le PPM de la bonne entité, ou créez un nouveau dossier.";
    }

    /** Libellé d'entité comparable : majuscules, sans accents ni ponctuation, espaces normalisés. */
    private String normaliserEntite(String s) {
        String sansAccent = java.text.Normalizer.normalize(nz(s), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccent.toUpperCase(Locale.FRENCH).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    /**
     * Apparie une ligne importée à une ligne encore libre de la version : empreinte métier complète
     * (libellé normalisé + bénéficiaires), puis libellé seul. Renvoie {@code null} si la ligne est
     * réellement nouvelle.
     */
    private Marche apparier(SaisiePpmImportResult.MarcheImport m, List<Marche> existantes, List<Integer> consommees) {
        String libelle = normaliser(m.designationMarche());
        String empreinte = libelle + "|" + empreinteBeneficiairesImport(m);
        for (Marche e : existantes) {
            if (!consommees.contains(e.getIdDetail()) && empreinteMetier(e).equals(empreinte)) {
                return e;
            }
        }
        for (Marche e : existantes) {
            if (!consommees.contains(e.getIdDetail()) && normaliser(e.getDesignationMarche()).equals(libelle)
                    && !libelle.isEmpty()) {
                return e;
            }
        }
        return null;
    }

    /** Empreinte des bénéficiaires d'une ligne IMPORTÉE, au même format que celle des lignes en base. */
    private String empreinteBeneficiairesImport(SaisiePpmImportResult.MarcheImport m) {
        if (m.beneficiaires() == null) {
            return "";
        }
        return m.beneficiaires().stream()
                .map(b -> texte(b.soaCode()) + ":" + montant(b.nouvMontBenef() != null ? b.nouvMontBenef() : b.ancMontBenef()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    /** Conversion d'une ligne du PDF vers la façade de saisie, en portant l'{@code idDetail} apparié. */
    private SaisieMarcheLigne versLigne(SaisiePpmImportResult.MarcheImport m, Marche appariee) {
        Integer idDetail = appariee == null ? null : appariee.getIdDetail();
        // ⚠️ 2026-08-06 — un import ne doit RIEN effacer. Le PPM PDF ne porte ni le compte du marché, ni
        // son statut, ni toujours ses lots ou ses bénéficiaires : sur une ligne appariée, ces champs sont
        // REPRIS de l'existant, sinon un simple réimport les viderait et le diff annoncerait des
        // « modifications » qui n'en sont pas (compte, statut et lots passant à vide).
        List<SaisieBeneficiaireLigne> benefs = m.beneficiaires() == null || m.beneficiaires().isEmpty()
                ? null   // liste absente = enfants conservés (contrat de la façade d'édition)
                : m.beneficiaires().stream()
                        .map(b -> new SaisieBeneficiaireLigne(b.soaCode(), b.soaLibelle(), b.numCompte(),
                                b.ancMontBenef(), b.nouvMontBenef()))
                        .toList();
        List<SaisieLotLigne> lots = m.lots() == null || m.lots().isEmpty()
                ? null
                : m.lots().stream()
                        .map(l -> new SaisieLotLigne(l.designationLot(), l.montLot(), l.qteLot(), l.uniteLot()))
                        .toList();
        // Dates prévisionnelles : sur une ligne EXISTANTE, `processus` absent conserve les enfants (contrat
        // de la façade). Sur une ligne NOUVELLE, au moins un processus est exigé → on résout les étapes du
        // PDF (« LANCEMENT », « OUVERTURE »…) sur la grille CAPM effective du mode, exactement comme le
        // fait la saisie initiale : égalité de libellé, sinon premier libellé (ordre ASC) qui le contient.
        List<ProcessusMarche> processus = idDetail != null ? null : processusDepuisImport(m);
        return new SaisieMarcheLigne(
                idDetail, m.designationMarche(), m.formeMarche(),
                appariee == null ? null : appariee.getNumCompte(),
                m.montEstim(), m.nouvMontEstim(), m.financement(),
                // Ligne nouvelle : même défaut qu'à la saisie initiale (`PpmFormFactory.ligneMarche`).
                appariee == null ? STATUT_MARCHE_DEFAUT : appariee.getStatut(),
                m.idNature(), m.natureLibelle(),
                benefs, lots, processus,
                m.idMode(), m.modeLibelle());
    }

    /** Étapes du PDF résolues en {@code idCapm} sur la grille effective du mode de la ligne. */
    private List<ProcessusMarche> processusDepuisImport(SaisiePpmImportResult.MarcheImport m) {
        List<Capm> effectifs = capmsEffectifs(m);
        List<ProcessusMarche> resultat = new ArrayList<>();
        for (SaisiePpmImportResult.PrevisionImport p : m.previsions() == null
                ? List.<SaisiePpmImportResult.PrevisionImport>of() : m.previsions()) {
            Integer idCapm = resoudreCapm(p.processus(), effectifs);
            if (idCapm != null && p.dateDebut() != null && !p.dateDebut().isBlank()) {
                resultat.add(new ProcessusMarche(idCapm, LocalDate.parse(p.dateDebut()), null));
            }
        }
        if (resultat.isEmpty() && !effectifs.isEmpty()) {
            // Le document ne porte aucune étape exploitable : on pose la PREMIÈRE du processus à la date
            // de l'exercice, faute de quoi la ligne serait refusée. La PRMP ajustera les dates au détail.
            resultat.add(new ProcessusMarche(effectifs.get(0).getIdCapm(), LocalDate.now(), null));
        }
        return resultat;
    }

    /**
     * Grille CAPM effective d'une ligne : processus spécifiques au mode (ou à son mode-modèle), à défaut
     * les processus communs ({@code idMode} nul). Triée par {@code ordre} ASC.
     */
    private List<Capm> capmsEffectifs(SaisiePpmImportResult.MarcheImport m) {
        Integer resolu = m.idMode();
        if (resolu == null && m.modeLibelle() != null && !m.modeLibelle().isBlank()) {
            resolu = modePassationRepository.findAll().stream()
                    .filter(mo -> normaliser(mo.getLibelle()).equals(normaliser(m.modeLibelle())))
                    .map(ModePassation::getIdMode).findFirst().orElse(null);
        }
        final Integer idMode = resolu;   // capturé par les lambdas ci-dessous
        List<Capm> tous = capmRepository.findAll();
        List<Capm> specifiques = idMode == null ? List.of()
                : tous.stream().filter(c -> idMode.equals(c.getIdMode())).toList();
        if (specifiques.isEmpty() && idMode != null) {
            Integer modele = modePassationRepository.findById(idMode)
                    .map(ModePassation::getIdModeModeleCapm).orElse(null);
            if (modele != null) {
                specifiques = tous.stream().filter(c -> modele.equals(c.getIdMode())).toList();
            }
        }
        List<Capm> retenus = specifiques.isEmpty()
                ? tous.stream().filter(c -> c.getIdMode() == null).toList() : specifiques;
        return retenus.stream()
                .sorted(Comparator.comparing(c -> c.getOrdre() == null ? 0 : c.getOrdre()))
                .toList();
    }

    /** Égalité stricte de libellé d'abord, sinon premier libellé (ordre ASC) qui contient le mot-clé. */
    private Integer resoudreCapm(String processus, List<Capm> effectifs) {
        String cle = processus == null ? "" : processus.trim().toUpperCase(Locale.FRENCH);
        if (cle.isEmpty()) {
            return null;
        }
        return effectifs.stream()
                .filter(c -> nz(c.getLibelleProcessus()).trim().toUpperCase(Locale.FRENCH).equals(cle))
                .map(Capm::getIdCapm).findFirst()
                .orElseGet(() -> effectifs.stream()
                        .filter(c -> nz(c.getLibelleProcessus()).toUpperCase(Locale.FRENCH).contains(cle))
                        .map(Capm::getIdCapm).findFirst().orElse(null));
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ suppression / restauration

    /** Marque une ligne supprimée dans une mise à jour en cours (suppression logique, réversible). */
    @Transactional
    public void supprimerLigne(Integer idDetail) {
        basculerSuppression(idDetail, true);
    }

    /** Remet en service une ligne précédemment supprimée. */
    @Transactional
    public void restaurerLigne(Integer idDetail) {
        basculerSuppression(idDetail, false);
    }

    private void basculerSuppression(Integer idDetail, boolean supprimee) {
        Marche ligne = marcheRepository.findById(idDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Ligne de marché introuvable : " + idDetail));
        Dossier dossier = dossierRepository.findById(ligne.getIdDossier())
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + ligne.getIdDossier()));
        exigerOperateurHabilite(dossier);
        if (!StatutDossier.BROUILLON.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "La suppression d'une ligne n'est possible que sur un brouillon de mise à jour.");
        }
        ligne.setSupprimee(supprimee);
        marcheRepository.save(ligne);
    }

    // ------------------------------------------------------------------ diff

    /**
     * Compare une version à son prédécesseur. Tant que la version est un brouillon, le diff est
     * <strong>recalculé</strong> (il doit suivre la saisie en cours) ; une fois soumise, il est relu
     * depuis la trace figée, qui fait foi.
     *
     * @throws BusinessRuleException si le dossier n'est pas une mise à jour (aucun prédécesseur)
     */
    @Transactional(readOnly = true)
    public DiffDossierDto diff(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        // ⚠️ Lecture ÉLARGIE (2026-08-15) — plus seulement la PRMP propriétaire : les profils du circuit
        // qui consultent le dossier lisent le diff (périmètre de localité habituel), le tableau partagé
        // du front (surlignage MODIFIEE) étant privé de donnée par l'ancien 403.
        controlerAccesLectureDiff(dossier);
        if (dossier.getIdDossierParent() == null) {
            throw new BusinessRuleException("Ce dossier n'est pas une mise à jour : il n'a pas de version précédente.");
        }
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElse(null);
        Integer numMaj = ppm == null ? null : ppm.getNumMaj();
        String motif = ppm == null ? null : ppm.getMotifMaj();

        if (changementLigneRepository.existsByIdDossier(idDossier)) {
            return depuisTrace(dossier, numMaj, motif);
        }
        return calculer(dossier, numMaj, motif);
    }

    /** Lecture du diff : tout-voyant, PRMP propriétaire, ou contrôleur de la localité du dossier. */
    private void controlerAccesLectureDiff(Dossier dossier) {
        if (cnm.prs.security.Visibilite.voitTout()) {
            return;
        }
        if (cnm.prs.security.Visibilite.estPrmp()) {
            dossierIntegrite.exigerProprietaire(dossier);
            return;
        }
        cnm.prs.security.Visibilite.exigerLocalite(dossier.getIdLocalite());
    }

    /** Diff calculé à la volée entre le dossier et son parent. */
    private DiffDossierDto calculer(Dossier dossier, Integer numMaj, String motif) {
        List<Marche> avant = marcheRepository.findByIdDossier(dossier.getIdDossierParent());
        List<Marche> apres = marcheRepository.findByIdDossier(dossier.getIdDossier());

        Map<Integer, Marche> avantParOrigine = avant.stream()
                .collect(Collectors.toMap(Marche::getIdLigneOrigine, m -> m, (a, b) -> a, LinkedHashMap::new));
        // Repli d'appariement pour les lignes sans ancêtre (réimport PDF) : libellé normalisé + SOA.
        Map<String, Marche> avantParEmpreinte = avant.stream()
                .collect(Collectors.toMap(this::empreinteMetier, m -> m, (a, b) -> a, LinkedHashMap::new));

        List<DiffDossierDto.LigneDiff> lignes = new ArrayList<>();
        List<Integer> apparies = new ArrayList<>();

        for (Marche m : apres) {
            Marche ancetre = avantParOrigine.get(m.getIdLigneOrigine());
            String apparieePar = "ORIGINE";
            if (ancetre == null) {
                ancetre = avantParEmpreinte.get(empreinteMetier(m));
                apparieePar = ancetre == null ? "AUCUN" : "LIBELLE_SOA";
            }
            if (ancetre == null) {
                lignes.add(ligne(m, m.getIdLigneOrigine(), TypeChangementLigne.NOUVELLE, apparieePar, List.of()));
                continue;
            }
            apparies.add(ancetre.getIdLigneOrigine());
            if (m.getSupprimee() && !ancetre.getSupprimee()) {
                lignes.add(ligne(m, ancetre.getIdLigneOrigine(), TypeChangementLigne.SUPPRIMEE, apparieePar, List.of()));
            } else if (!m.getSupprimee() && ancetre.getSupprimee()) {
                lignes.add(ligne(m, ancetre.getIdLigneOrigine(), TypeChangementLigne.RESTAUREE, apparieePar, List.of()));
            } else {
                List<DiffDossierDto.ChampDiff> ecarts = comparer(ancetre, m);
                lignes.add(ligne(m, ancetre.getIdLigneOrigine(),
                        ecarts.isEmpty() ? TypeChangementLigne.INCHANGEE : TypeChangementLigne.MODIFIEE,
                        apparieePar, ecarts));
            }
        }
        // Lignes du prédécesseur qu'aucune ligne de la nouvelle version ne reprend : disparues.
        for (Marche m : avant) {
            if (!apparies.contains(m.getIdLigneOrigine()) && !m.getSupprimee()) {
                lignes.add(new DiffDossierDto.LigneDiff(null, m.getIdLigneOrigine(), m.getDesignationMarche(),
                        TypeChangementLigne.SUPPRIMEE.name(), "ORIGINE", List.of()));
            }
        }
        return new DiffDossierDto(dossier.getIdDossier(), dossier.getIdDossierParent(), numMaj, motif,
                false, recap(lignes), lignes);
    }

    private DiffDossierDto.LigneDiff ligne(Marche m, Integer origine, TypeChangementLigne type,
            String apparieePar, List<DiffDossierDto.ChampDiff> champs) {
        return new DiffDossierDto.LigneDiff(m.getIdDetail(), origine, m.getDesignationMarche(),
                type.name(), apparieePar, champs);
    }

    /** Compare les champs retenus ; les collections rattachées passent par une empreinte normalisée. */
    private List<DiffDossierDto.ChampDiff> comparer(Marche avant, Marche apres) {
        List<DiffDossierDto.ChampDiff> ecarts = new ArrayList<>();
        for (String champ : CHAMPS_COMPARES) {
            String a = valeur(avant, champ);
            String b = valeur(apres, champ);
            if (!Objects.equals(a, b)) {
                ecarts.add(new DiffDossierDto.ChampDiff(champ, a, b));
            }
        }
        return ecarts;
    }

    private String valeur(Marche m, String champ) {
        return switch (champ) {
            case "designationMarche" -> texte(m.getDesignationMarche());
            case "montEstim" -> montant(m.getMontEstim());
            case "nouvMontEstim" -> montant(m.getNouvMontEstim());
            case "numCompte" -> texte(m.getNumCompte());
            case "financement" -> texte(m.getFinancement());
            case "statut" -> texte(m.getStatut());
            case "idNature" -> m.getIdNature() == null ? null : String.valueOf(m.getIdNature());
            case "idMode" -> m.getIdMode() == null ? null : String.valueOf(m.getIdMode());
            case "formeMarche" -> m.getFormeMarche().name();
            case "beneficiaires" -> empreinteBeneficiaires(m.getIdDetail());
            case "lots" -> empreinteLots(m.getIdDetail());
            case "processus" -> empreintePrevisions(m.getIdDetail());
            default -> null;
        };
    }

    /**
     * Empreinte d'appariement de repli : libellé normalisé + codes SOA triés. Deux lignes de versions
     * différentes qui la partagent désignent le même marché, même sans ancêtre commun.
     */
    private String empreinteMetier(Marche m) {
        return normaliser(m.getDesignationMarche()) + "|" + empreinteBeneficiaires(m.getIdDetail());
    }

    private String empreinteBeneficiaires(Integer idDetail) {
        return serviceBeneficiaireRepository.findByIdDetail(idDetail).stream()
                .map(b -> texte(b.getSoaCode()) + ":" + montant(b.getNouvMontBenef() != null
                        ? b.getNouvMontBenef() : b.getAncMontBenef()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String empreinteLots(Integer idDetail) {
        return lotRepository.findByIdDetail(idDetail).stream()
                .map(l -> normaliser(l.getDesignationLot()) + ":" + montant(l.getMontLot())
                        + ":" + (l.getQteLot() == null ? "" : l.getQteLot()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String empreintePrevisions(Integer idDetail) {
        return marchePrevisionRepository.findByIdDetail(idDetail).stream()
                .map(p -> p.getIdCapm() + ":" + Optional.ofNullable(p.getDateDebut()).map(Object::toString).orElse("")
                        + ":" + Optional.ofNullable(p.getDateFin()).map(Object::toString).orElse(""))
                .sorted()
                .collect(Collectors.joining(","));
    }

    /** Montants comparés à la valeur, pas à la représentation : 1000 et 1000.00 sont égaux. */
    private String montant(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private String texte(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String normaliser(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.FRENCH).replaceAll("\\s+", " ");
    }

    private DiffDossierDto.RecapDiff recap(List<DiffDossierDto.LigneDiff> lignes) {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (DiffDossierDto.LigneDiff l : lignes) {
            n.merge(l.type(), 1, Integer::sum);
        }
        return new DiffDossierDto.RecapDiff(
                n.getOrDefault(TypeChangementLigne.INCHANGEE.name(), 0),
                n.getOrDefault(TypeChangementLigne.MODIFIEE.name(), 0),
                n.getOrDefault(TypeChangementLigne.NOUVELLE.name(), 0),
                n.getOrDefault(TypeChangementLigne.SUPPRIMEE.name(), 0),
                n.getOrDefault(TypeChangementLigne.RESTAUREE.name(), 0),
                lignes.size());
    }

    /** Relecture du diff figé : c'est lui qui fait foi une fois la version soumise. */
    private DiffDossierDto depuisTrace(Dossier dossier, Integer numMaj, String motif) {
        Map<Integer, List<ChangementLigne>> parLigne = changementLigneRepository
                .findByIdDossierOrderByIdChangementAsc(dossier.getIdDossier()).stream()
                .collect(Collectors.groupingBy(ChangementLigne::getIdLigneOrigine, LinkedHashMap::new,
                        Collectors.toList()));
        List<DiffDossierDto.LigneDiff> lignes = parLigne.entrySet().stream().map(e -> {
            List<ChangementLigne> traces = e.getValue();
            ChangementLigne tete = traces.get(0);
            List<DiffDossierDto.ChampDiff> champs = traces.stream()
                    .filter(t -> t.getChamp() != null)
                    .map(t -> new DiffDossierDto.ChampDiff(t.getChamp(), t.getValeurAvant(), t.getValeurApres()))
                    .toList();
            return new DiffDossierDto.LigneDiff(null, e.getKey(), tete.getDesignation(),
                    tete.getTypeChangement(), "ORIGINE", champs);
        }).sorted(Comparator.comparing(DiffDossierDto.LigneDiff::idLigneOrigine)).toList();
        return new DiffDossierDto(dossier.getIdDossier(), dossier.getIdDossierParent(), numMaj, motif,
                true, recap(lignes), lignes);
    }

    // ------------------------------------------------------------------ figeage à la soumission

    /**
     * À appeler à la <strong>soumission</strong> d'une version : écrit la trace du diff (append-only,
     * idempotent) et bascule le prédécesseur en {@link StatutDossier#REMPLACE}. Sans effet sur un dossier
     * qui n'est pas une mise à jour.
     */
    @Transactional
    public void figerDiffEtRemplacerParent(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier).orElse(null);
        if (dossier == null || dossier.getIdDossierParent() == null
                || changementLigneRepository.existsByIdDossier(idDossier)) {
            return;
        }
        rendreEffective(dossier);
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElse(null);
        DiffDossierDto diff = calculer(dossier, ppm == null ? null : ppm.getNumMaj(),
                ppm == null ? null : ppm.getMotifMaj());

        int seq = changementLigneRepository.findMaxId();
        for (DiffDossierDto.LigneDiff l : diff.lignes()) {
            if (l.champs().isEmpty()) {
                changementLigneRepository.save(trace(++seq, idDossier, l, null, null, null));
            } else {
                for (DiffDossierDto.ChampDiff c : l.champs()) {
                    changementLigneRepository.save(
                            trace(++seq, idDossier, l, c.champ(), c.avant(), c.apres()));
                }
            }
        }
        dossierRepository.findById(dossier.getIdDossierParent()).ifPresent(parent -> {
            parent.setStatut(StatutDossier.REMPLACE.name());
            dossierRepository.save(parent);
        });
    }

    /**
     * ⚠️ 2026-08-05 (demande user) — moment où la mise à jour devient <strong>effective</strong> : c'est
     * ici, et pas à l'ouverture, que la référence officielle est attribuée (le compteur n'est donc
     * consommé que par les versions réellement créées).
     */
    private void rendreEffective(Dossier dossier) {
        ppmRepository.findByIdDossier(dossier.getIdDossier()).stream().findFirst().ifPresent(ppm -> {
            if (REFERENCE_PROVISOIRE.equals(ppm.getReference())) {
                ppm.setReference(referenceService.genererPpm(
                        entiteContractRepository.findById(dossier.getIdEntiteContract())
                                .map(e -> e.getLibelleEntite()).orElse(null),
                        ppm.getExercice()));
                ppmRepository.save(ppm);
            }
        });
    }

    private ChangementLigne trace(int id, Integer idDossier, DiffDossierDto.LigneDiff l,
            String champ, String avant, String apres) {
        ChangementLigne t = new ChangementLigne();
        t.setIdChangement(id);
        t.setIdDossier(idDossier);
        t.setIdLigneOrigine(l.idLigneOrigine());
        t.setDesignation(l.designation());
        t.setTypeChangement(l.type());
        t.setChamp(champ);
        t.setValeurAvant(avant);
        t.setValeurApres(apres);
        return t;
    }

    // ------------------------------------------------------------------ chaîne des versions

    /**
     * Chaîne complète des versions, de la plus récente à l'initiale (v3 → v2 → v1). Le point d'entrée peut
     * être n'importe quelle version : on remonte d'abord à la racine, puis on redescend la descendance.
     */
    @Transactional(readOnly = true)
    public List<DossierDto> chaineVersions(Integer idDossier) {
        Dossier depart = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));

        // Descendance : versions POSTÉRIEURES au point d'entrée, de la plus récente à la plus proche.
        // Les brouillons en sont exclus — une mise à jour en cours n'est pas encore une version — mais
        // le point d'entrée lui-même est toujours conservé, brouillon compris : c'est ce qui permet à un
        // brouillon de mise à jour d'afficher sa propre filiation.
        List<Dossier> posterieures = new ArrayList<>();
        Dossier courant = suivante(depart);
        for (int garde = 0; courant != null && garde < 1000; garde++) {
            posterieures.add(courant);
            courant = suivante(courant);
        }
        java.util.Collections.reverse(posterieures);

        // Remontée : le point d'entrée puis ses ancêtres, jusqu'à la version initiale.
        List<Dossier> anterieures = new ArrayList<>();
        Dossier remontee = depart;
        for (int garde = 0; remontee != null && garde < 1000; garde++) {
            anterieures.add(remontee);
            remontee = remontee.getIdDossierParent() == null ? null
                    : dossierRepository.findById(remontee.getIdDossierParent()).orElse(null);
        }

        List<Dossier> chaine = new ArrayList<>(posterieures);
        chaine.addAll(anterieures);
        return chaine.stream().map(DossierMapper::toDto).toList();
    }

    /** Version qui succède à celle-ci (hors brouillon de mise à jour en cours), s'il en existe une. */
    private Dossier suivante(Dossier dossier) {
        return dossierRepository.findByIdDossierParent(dossier.getIdDossier()).stream()
                .filter(d -> !StatutDossier.BROUILLON.name().equals(d.getStatut()))
                .findFirst()
                .orElse(null);
    }

    /**
     * La PRMP authentifiée doit être propriétaire du dossier.
     *
     * <p>⚠️ Spec « Mandats PRMP » — délégué à la garde partagée : la PRMP <em>en fonction</em> sur le
     * périmètre est acceptée au même titre que la PRMP d'attribution, sans que rien ne soit réattribué.
     * La garde de vacance, elle, n'est posée que sur les écritures (cf. {@link #exigerOperateurHabilite}) :
     * la lecture d'un diff reste possible pendant une transition.</p>
     */
    private void exigerProprietaire(Dossier dossier) {
        String courante = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur PRMP non identifié."));
        if (courante.equals(dossier.getIdPrmp())
                || dossierIntegrite.estPrmpEnFonctionSurLeDossier(dossier, courante)) {
            return;
        }
        throw new AccessDeniedException("Ce dossier n'appartient pas à votre périmètre.");
    }

    /** Propriété + habilitation (mandat actif) — pour les actions qui écrivent. */
    private void exigerOperateurHabilite(Dossier dossier) {
        exigerProprietaire(dossier);
        dossierIntegrite.exigerMandatActif();
    }
}
