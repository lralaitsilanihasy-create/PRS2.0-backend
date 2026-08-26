package cnm.prs.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.DossierDto;
import cnm.prs.dto.EditionPpmRequest;
import cnm.prs.dto.MarcheDto;
import cnm.prs.dto.MarchePrevisionDto;
import cnm.prs.dto.PpmDto;
import cnm.prs.dto.ProcessusMarche;
import cnm.prs.dto.SaisieDossierRequest;
import cnm.prs.dto.SaisieBeneficiaireLigne;
import cnm.prs.dto.SaisieLotLigne;
import cnm.prs.dto.SaisieMarcheLigne;
import cnm.prs.dto.SaisiePpmRequest;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Compte;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Lot;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.entity.SoaBeneficiaire;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.mapper.DossierMapper;
import cnm.prs.mapper.PpmMapper;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.CompteRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.NatureRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.repository.SoaBeneficiaireRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.TrancheRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;

/**
 * Façade de saisie (§3.1, Module 02) : « saisir un PPM (famille DDP) ou un dossier DMC/DDM » EST créer
 * le dossier à soumettre.
 *
 * <p>En un seul appel transactionnel, crée le {@code t_dossier} (statut <strong>BROUILLON</strong>,
 * propriété de la PRMP courante) et son contenu. Le PPM et les lignes de marché passent par
 * {@link PpmService}/{@link MarcheService}, donc par les garde-fous d'intégrité partagés
 * ({@link DossierIntegriteService}) : aucun chemin ne crée d'incohérence.</p>
 */
@Service
@Transactional
public class SaisieService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SaisieService.class);

    /** Famille « Dossier de Planification » (codes centralisés dans {@link DossierIntegriteService}). */
    private static final String FAMILLE_DDP = DossierIntegriteService.FAMILLE_DDP;

    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final MarcheRepository marcheRepository;
    private final PpmService ppmService;
    private final MarcheService marcheService;
    private final DossierIntegriteService dossierIntegrite;
    private final EntiteContractRepository entiteContractRepository;
    private final PrmpRepository prmpRepository;
    private final ReferenceService referenceService;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final MarchePrevisionService marchePrevisionService;
    private final CapmRepository capmRepository;
    private final PieceJointeDossierService pieceJointeDossierService;
    private final TypePieceJointeRepository typePieceJointeRepository;
    private final NatureRepository natureRepository;
    private final ModePassationRepository modePassationRepository;
    private final CompteRepository compteRepository;
    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    private final SoaBeneficiaireRepository soaBeneficiaireRepository;
    private final AuditLogService auditLogService;
    private final TypeDmcService typeDmcService;
    private final LotRepository lotRepository;
    private final TrancheRepository trancheRepository;
    private final SousTypeDossierRepository sousTypeDossierRepository;
    /** ⚠️ Spec « Mandats PRMP » — habilitation à créer, et mandat d'attribution figé sur le dossier. */
    private final MandatService mandatService;
    private final JournalDossierService journalDossier;
    /** ⚠️ Visibilité des rectifications (2026-08-15) — gel de l'état pré-correction au 1er PUT du cycle. */
    private final RectificationDiffService rectificationDiffService;

    public SaisieService(DossierRepository dossierRepository, PpmRepository ppmRepository,
            MarcheRepository marcheRepository, PpmService ppmService,
            MarcheService marcheService, DossierIntegriteService dossierIntegrite,
            EntiteContractRepository entiteContractRepository, PrmpRepository prmpRepository,
            ReferenceService referenceService, MarchePrevisionRepository marchePrevisionRepository,
            MarchePrevisionService marchePrevisionService, CapmRepository capmRepository,
            PieceJointeDossierService pieceJointeDossierService,
            TypePieceJointeRepository typePieceJointeRepository,
            NatureRepository natureRepository, ModePassationRepository modePassationRepository,
            CompteRepository compteRepository, ServiceBeneficiaireRepository serviceBeneficiaireRepository,
            SoaBeneficiaireRepository soaBeneficiaireRepository, AuditLogService auditLogService,
            TypeDmcService typeDmcService, LotRepository lotRepository,
            TrancheRepository trancheRepository, SousTypeDossierRepository sousTypeDossierRepository,
            MandatService mandatService, JournalDossierService journalDossier,
            RectificationDiffService rectificationDiffService) {
        this.mandatService = mandatService;
        this.journalDossier = journalDossier;
        this.rectificationDiffService = rectificationDiffService;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.marcheRepository = marcheRepository;
        this.ppmService = ppmService;
        this.marcheService = marcheService;
        this.dossierIntegrite = dossierIntegrite;
        this.entiteContractRepository = entiteContractRepository;
        this.prmpRepository = prmpRepository;
        this.referenceService = referenceService;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.marchePrevisionService = marchePrevisionService;
        this.capmRepository = capmRepository;
        this.pieceJointeDossierService = pieceJointeDossierService;
        this.typePieceJointeRepository = typePieceJointeRepository;
        this.natureRepository = natureRepository;
        this.modePassationRepository = modePassationRepository;
        this.compteRepository = compteRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.soaBeneficiaireRepository = soaBeneficiaireRepository;
        this.auditLogService = auditLogService;
        this.typeDmcService = typeDmcService;
        this.lotRepository = lotRepository;
        this.trancheRepository = trancheRepository;
        this.sousTypeDossierRepository = sousTypeDossierRepository;
    }

    /** Saisie d'un PPM = dossier (BROUILLON) + PPM + lignes de marché (mode auto), en une transaction. */
    public DossierDto saisirPpm(SaisiePpmRequest req) {
        String idPrmp = prmpCourante();
        // Localité dérivée de l'entité contractante choisie (parmi les entités de la PRMP).
        String localite = dossierIntegrite.localiteDeLEntiteDeLaPrmp(req.idEntiteContract(), idPrmp);
        // Sous-type initial PPM ; recalculé (PPM-AGPM) par MarcheService à la création des lignes.
        Dossier dossier = creerDossier(FAMILLE_DDP, DossierIntegriteService.SOUS_TYPE_PPM,
                localite, idPrmp, req.idEntiteContract());  // PK séquence
        Integer idDossier = dossier.getIdDossier();

        PpmDto ppm = new PpmDto();
        ppm.setIdDossier(idDossier);
        ppm.setIdPrmp(idPrmp);
        ppm.setExercice(req.exercice());
        // ⚠️ Règle ajoutée — signataire auto (profil PRMP) + référence auto (acronyme entité), non saisis.
        ppm.setSignataire(signataireDeLaPrmp(idPrmp));
        ppm.setDateSignature(req.dateSignature());
        ppm.setReference(referenceService.genererPpm(libelleEntite(req.idEntiteContract()), req.exercice()));
        ppm.setIdLocalite(localite);
        Integer idPpm = ppmService.create(ppm).getIdPpm();          // PK séquence (retournée)

        if (req.marches() != null) {
            for (int i = 0; i < req.marches().size(); i++) {
                SaisieMarcheLigne ligne = req.marches().get(i);
                exigerAuMoinsUnProcessus(ligne, i);   // « au moins un processus » (NotEmpty, à la création)
                validerChronologieProcessus(ligne, i);   // CAPM connus + cohérence chronologique
                validerCoherenceMontants(ligne, i);   // Σ bénéficiaires = montant(s) du marché (si beneficiaires[] non vide)
                Integer idDetail = marcheService.create(toMarcheDto(ligne, idDossier, idPpm)).getIdDetail();
                for (ProcessusMarche p : ligne.processus()) {
                    // ⚠️ LOT 3b (2026-08-26) — PK null : allouee par la sequence seq_marche_prevision
                    // (le compteur local max+1 rendait le meme id a deux saisies concurrentes).
                    marchePrevisionService.create(new MarchePrevisionDto(
                            null, idDetail, p.idCapm(), p.dateDebut(), p.dateFin(), null));
                }
                creerBeneficiaires(idDetail, ligne);   // une ligne t_service_beneficiaire par bénéficiaire
                creerLots(idDetail, idDossier, ligne);   // une ligne t_lot par lot (aucun contrôle de somme)
            }
        }
        return DossierMapper.toDto(dossierRepository.findById(idDossier).orElseThrow());
    }

    /**
     * Saisie d'un PPM avec ses pièces jointes initiales (multipart). Crée le dossier/PPM puis
     * enregistre chaque pièce ({@code apresLettreRenvoi=false}), dans la même transaction : un format
     * invalide (magic-bytes) lève 400 et annule la saisie complète.
     *
     * @param pieces fichiers indexés par {@code idTypePiece} (parts {@code piece_<idTypePiece>})
     */
    public DossierDto saisirPpm(SaisiePpmRequest req, java.util.Map<Integer, MultipartFile> pieces) {
        exigerPiecesObligatoires(FAMILLE_DDP, pieces);   // contrôle AVANT toute persistance (400 si manquante)
        DossierDto dossier = saisirPpm(req);
        if (pieces != null) {
            for (java.util.Map.Entry<Integer, MultipartFile> e : pieces.entrySet()) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    pieceJointeDossierService.enregistrerInitiale(dossier.getIdDossier(), e.getKey(), e.getValue());
                }
            }
        }
        return dossier;
    }

    /**
     * Vérifie qu'à la création toutes les pièces jointes {@code obligatoire} du type de dossier
     * (référentiel {@code t_type_piece_jointe}) sont fournies dans la requête multipart (part
     * {@code piece_<idTypePiece>} non vide). Sinon → 400 {@code erreurs:[{champ:"piecesJointes", message}]},
     * une entrée par pièce manquante.
     */
    private void exigerPiecesObligatoires(String idTypeDossier, java.util.Map<Integer, MultipartFile> pieces) {
        List<ErrorResponse.FieldError> manquantes = typePieceJointeRepository
                .findByIdTypeDossierAndObligatoireTrue(idTypeDossier).stream()
                .filter(t -> pieces == null
                        || pieces.get(t.getIdTypePiece()) == null
                        || pieces.get(t.getIdTypePiece()).isEmpty())
                .map(t -> new ErrorResponse.FieldError("piecesJointes",
                        "La pièce '" + t.getLibellePiece() + "' est obligatoire."))
                .toList();
        if (!manquantes.isEmpty()) {
            throw new ChampsInvalidesException(manquantes);
        }
    }

    /**
     * ⚠️ Règle ajoutée — chaque marché doit comporter au moins un processus à la création (400 sinon).
     * Validé ici (et non par {@code @NotEmpty} sur le DTO) pour ne pas exiger de processus à l'édition,
     * qui partage {@link SaisieMarcheLigne}.
     */
    private void exigerAuMoinsUnProcessus(SaisieMarcheLigne ligne, int i) {
        if (ligne.processus() == null || ligne.processus().isEmpty()) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(
                    "marches[" + i + "].processus", "Au moins un processus est obligatoire.")));
        }
    }

    /** ⚠️ Règle ajoutée — charge le {@code Capm} d'un processus ; l'{@code idCapm} doit exister (400 sinon). */
    private Capm capmConnu(ProcessusMarche p, int i, int j) {
        return capmRepository.findById(p.idCapm())
                .orElseThrow(() -> new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(
                        "marches[" + i + "].processus[" + j + "].idCapm",
                        "Processus inconnu : " + p.idCapm() + "."))));
    }

    /**
     * Charge les CAPM des processus d'une ligne ({@code idCapm} existant, sinon 400) et valide leur
     * cohérence chronologique ({@link ProcessusChronologie}). Partagée création / édition — mêmes
     * validations sur les deux chemins.
     */
    private void validerChronologieProcessus(SaisieMarcheLigne ligne, int i) {
        List<ProcessusChronologie.Proc> procs = new ArrayList<>();
        for (int j = 0; j < ligne.processus().size(); j++) {
            ProcessusMarche p = ligne.processus().get(j);
            Capm capm = capmConnu(p, i, j);
            procs.add(new ProcessusChronologie.Proc("marches[" + i + "].processus[" + j + "]",
                    capm.getOrdre() == null ? 0 : capm.getOrdre(), capm.getLibelleProcessus(),
                    p.dateDebut(), p.dateFin()));
        }
        ErrorResponse.FieldError violation = ProcessusChronologie.premiereViolation(procs);
        if (violation != null) {
            throw new ChampsInvalidesException(List.of(violation));
        }
    }

    /**
     * Édition d'un brouillon PPM (§3.1 M02) : met à jour l'en-tête du PPM et <strong>réconcilie</strong>
     * ses lignes de marché avec la liste fournie (ajout / mise à jour par {@code idDetail} / retrait des
     * absentes), en une transaction. Garde-fous (propriété, statut BROUILLON, type PPM) + mode recalculé.
     *
     * <p>⚠️ Règle corrigée (2026-07-18) — les <strong>sous-objets</strong> des lignes (bénéficiaires,
     * lots, processus) sont traités comme au POST au lieu d'être silencieusement ignorés, avec les
     * <strong>mêmes validations</strong> (Σ bénéficiaires, chronologie des processus, ≥1 processus par
     * ligne <em>nouvelle</em>). Sémantique par ligne <em>mise à jour</em> ({@code idDetail} fourni) :
     * liste <strong>fournie</strong> = <strong>remplacement complet</strong> des enfants de ce type ;
     * liste <strong>absente</strong> ({@code null}) = enfants <strong>conservés</strong>. Un remplacement
     * des processus par une liste vide est refusé (invariant « ≥1 processus par marché »).</p>
     */
    public DossierDto editerPpm(Integer idDossier, EditionPpmRequest req) {
        // ⚠️ Règle étendue (2026-08-02, demande user) — la RECTIFICATION après vérification passe par
        // l'IMPORTATION du PPM rectifié (même façade que l'édition de brouillon) : la façade accepte
        // aussi un dossier EN_ATTENTE_DECISION_PRMP (propriétaire). En rectification, la STRUCTURE est
        // FIGÉE : mêmes lignes (mise à jour par idDetail), NI AJOUT NI RETRAIT — l'examen référence les
        // lignes de marché (t_examen_detail.ID_DETAIL) et le périmètre des observations est figé.
        boolean rectification = dossierRepository.findById(idDossier)
                .map(d -> StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(d.getStatut())).orElse(false);
        // ⚠️ 2026-08-05 (versionnement des PPM) — sur une VERSION (dossier rattaché à un prédécesseur),
        // une ligne absente de la demande est marquée SUPPRIMÉE, jamais effacée : son identité
        // inter-versions et son historique doivent survivre, et elle reste restaurable. La garantie est
        // posée ici, côté serveur, pour ne pas dépendre de ce que le client pense à renvoyer.
        boolean version = dossierRepository.findById(idDossier)
                .map(d -> d.getIdDossierParent() != null).orElse(false);
        if (rectification) {
            dossierIntegrite.exigerEnAttenteDecisionPrmpModifiable(idDossier);
            // ⚠️ Règle ajoutée (2026-08-15, visibilité des rectifications) — au PREMIER PUT du cycle,
            // l'état des lignes AVANT correction est figé (la rectification modifie la version courante
            // en place : sans instantané, rien à comparer pour le diff servi au vérificateur).
            rectificationDiffService.figerAvantPremiereCorrection(idDossier);
        } else {
            dossierIntegrite.exigerBrouillonModifiable(idDossier);
        }
        dossierIntegrite.exigerFamilleDdp(idDossier);
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Aucun PPM rattaché au dossier " + idDossier + "."));

        // 1) En-tête PPM (on repart de l'existant pour conserver idPrmp/idLocalite/idDossier).
        PpmDto entete = PpmMapper.toDto(ppm);
        entete.setExercice(req.exercice());
        entete.setSignataire(req.signataire());
        entete.setDateSignature(req.dateSignature());
        entete.setReference(req.reference());
        // Motif : corrigeable tant que la version n'est pas soumise ; ignoré hors version, et une valeur
        // absente conserve le motif posé à la création (jamais effacé par omission).
        if (version && req.motifMaj() != null && !req.motifMaj().isBlank()) {
            entete.setMotifMaj(req.motifMaj().trim());
        }
        ppmService.update(ppm.getIdPpm(), entete);

        // 2) Réconciliation des lignes par idDetail — sous-objets compris (règle corrigée 2026-07-18).
        Set<Integer> existants = new HashSet<>();
        for (Marche m : marcheRepository.findByIdDossier(idDossier)) {
            existants.add(m.getIdDetail());
        }
        Set<Integer> demandes = new HashSet<>();
        if (req.marches() != null) {
            for (int i = 0; i < req.marches().size(); i++) {
                SaisieMarcheLigne ligne = req.marches().get(i);
                boolean nouvelle = !existants.contains(ligne.idDetail());
                if (rectification && nouvelle) {
                    throw new BusinessRuleException("Rectification : la structure du dossier examiné est figée "
                            + "— aucune ligne de marché ne peut être ajoutée (ligne " + (i + 1)
                            + " sans correspondance). Le PPM rectifié doit comporter les mêmes lignes.");
                }
                demandes.add(ligne.idDetail());
                // Validations identiques au POST. Ligne nouvelle → ≥1 processus obligatoire ; ligne mise
                // à jour → un remplacement explicite par une liste vide viderait le marché (refusé).
                if (nouvelle) {
                    exigerAuMoinsUnProcessus(ligne, i);
                } else if (ligne.processus() != null && ligne.processus().isEmpty()) {
                    throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(
                            "marches[" + i + "].processus", "Au moins un processus est obligatoire.")));
                }
                if (ligne.processus() != null) {
                    validerChronologieProcessus(ligne, i);
                }
                validerCoherenceMontants(ligne, i);   // Σ bénéficiaires (si beneficiaires[] non vide)

                MarcheDto m = toMarcheDto(ligne, idDossier, ppm.getIdPpm());
                Integer idDetail;
                if (!nouvelle) {
                    marcheService.update(ligne.idDetail(), m);
                    idDetail = ligne.idDetail();
                    // Liste fournie = remplacement complet des enfants de ce type ; absente = conservés.
                    if (ligne.processus() != null) {
                        marchePrevisionRepository.deleteByIdDetail(idDetail);
                    }
                    if (ligne.beneficiaires() != null) {
                        serviceBeneficiaireRepository.deleteByIdDetail(idDetail);
                    }
                    if (ligne.lots() != null) {
                        List<Integer> idLots = lotRepository.findByIdDetail(idDetail).stream()
                                .map(Lot::getIdLot).toList();
                        if (!idLots.isEmpty()) {
                            trancheRepository.deleteByIdLotIn(idLots);   // FK-safe avant retrait des lots
                        }
                        lotRepository.deleteByIdDetail(idDetail);
                    }
                } else {
                    idDetail = marcheService.create(m).getIdDetail();
                }
                if (ligne.processus() != null) {
                    for (ProcessusMarche p : ligne.processus()) {
                        // ⚠️ LOT 3b (2026-08-26) — PK null : allouee par la sequence seq_marche_prevision
                        // (le compteur local max+1 rendait le meme id a deux saisies concurrentes).
                        marchePrevisionService.create(new MarchePrevisionDto(
                                null, idDetail, p.idCapm(), p.dateDebut(), p.dateFin(), null));
                    }
                }
                creerBeneficiaires(idDetail, ligne);     // sans effet si beneficiaires null/vide
                creerLots(idDetail, idDossier, ligne);   // sans effet si lots null/vide
            }
        }
        // 3) Retrait des lignes absentes de la demande — INTERDIT en rectification (structure figée),
        //    SUPPRESSION LOGIQUE sur une version (jamais d'effacement), suppression franche ailleurs.
        for (Integer id : existants) {
            if (!demandes.contains(id)) {
                if (rectification) {
                    throw new BusinessRuleException("Rectification : la structure du dossier examiné est figée "
                            + "— aucune ligne de marché ne peut être retirée. Le PPM rectifié doit comporter "
                            + "les mêmes lignes que le dossier examiné.");
                }
                if (version) {
                    marcheRepository.findById(id).ifPresent(m -> {
                        m.setSupprimee(Boolean.TRUE);
                        marcheRepository.save(m);
                    });
                } else {
                    marcheService.delete(id);
                }
            }
        }
        return DossierMapper.toDto(dossierRepository.findById(idDossier).orElseThrow());
    }

    private MarcheDto toMarcheDto(SaisieMarcheLigne ligne, Integer idDossier, Integer idPpm) {
        MarcheDto m = new MarcheDto();
        m.setIdDetail(ligne.idDetail());
        m.setIdDossier(idDossier);
        m.setIdPpm(idPpm);
        m.setDesignationMarche(ligne.designationMarche());
        m.setFormeMarche(ligne.formeMarche());   // validé/défauté en aval (MarcheMapper/MarcheService)
        m.setNumCompte(resoudreOuCreerCompte(ligne.numCompte()));
        m.setMontEstim(ligne.montEstim());
        m.setNouvMontEstim(ligne.nouvMontEstim());
        m.setFinancement(ligne.financement());
        m.setStatut(ligne.statut());
        // Nature / mode : id si fourni ; sinon résolus-ou-créés depuis le libellé (import PPM).
        m.setIdNature(resoudreOuCreerNature(ligne.idNature(), ligne.natureLibelle()));
        m.setIdMode(resoudreOuCreerMode(ligne.idMode(), ligne.modeLibelle()));
        return m;
    }

    /**
     * (Règle ajoutée) Résout la nature par id ; à défaut, par <strong>libellé normalisé</strong> (trim + casse +
     * accents) dans {@code tr_nature} ; à défaut, la <strong>crée à la volée</strong> (PK allouée à la
     * séquence {@code seq_nature}) et la trace.
     * {@code null} si ni id ni libellé.
     */
    private Integer resoudreOuCreerNature(Integer id, String libelle) {
        if (id != null) {
            return id;
        }
        if (libelle == null || libelle.isBlank()) {
            return null;
        }
        String cible = normaliser(libelle);
        List<Nature> refs = natureRepository.findAll();
        Integer existant = refs.stream()
                .filter(n -> n.getLibelle() != null && normaliser(n.getLibelle()).equals(cible))
                .map(Nature::getIdNature).findFirst().orElse(null);
        if (existant != null) {
            return existant;
        }
        // ⚠️ LOT 3b (2026-08-26) — PK allouée à la séquence seq_nature : le max+1 calculé sur la liste
        // en mémoire rendait le même id à deux imports concurrents, et le second écrasait la nature du premier.
        int nouvelId = ClePrimaire.allouerLibre(natureRepository::existsById, natureRepository::nextIdNature);
        natureRepository.save(new Nature(nouvelId, libelle.trim(), null));
        auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_nature",
                String.valueOf(nouvelId), "CREATION_A_LA_VOLEE", null);
        return nouvelId;
    }

    /**
     * Idem {@link #resoudreOuCreerNature} pour le mode de passation ({@code tr_mode}).
     *
     * <p>⚠️ Règle ajoutée — résolution avec la normalisation <strong>étendue</strong> partagée
     * ({@link LibelleNormalisation} : pluriels simples, apostrophes/espaces typographiques) : une coquille
     * du PDF (« APPEL D'OFFRE OUVERT » singulier) résout vers le mode canonique au lieu de créer un
     * quasi-doublon sans {@code declencheAgpm} — fermant le contournement silencieux de la règle AGPM.
     * Si un mode est néanmoins <strong>créé</strong> alors que son libellé est proche (Levenshtein ≤
     * {@value SaisiePpmImportService#SEUIL_SUGGESTION}) d'un mode <strong>déclencheur d'AGPM</strong>, le
     * cas est signalé : log WARN + trace d'audit {@code CREATION_MODE_PROCHE_AGPM} (pas d'avertissement de
     * réponse — le contrat {@code DossierDto} n'a pas de champ avertissements).</p>
     */
    private Integer resoudreOuCreerMode(Integer id, String libelle) {
        if (id != null) {
            return id;
        }
        if (libelle == null || libelle.isBlank()) {
            return null;
        }
        List<ModePassation> refs = modePassationRepository.findAll();
        // Résolution tolérante au suffixe de source de financement (RPI/PIP) — source unique LibelleNormalisation.
        ModePassation existant = LibelleNormalisation.resoudreMode(refs, ModePassation::getLibelle, libelle);
        if (existant != null) {
            return existant.getIdMode();
        }
        String cible = LibelleNormalisation.separerSource(libelle)[0];
        // ⚠️ LOT 3b (2026-08-26) — PK allouée à la séquence seq_mode_passation (même motif que la nature).
        int nouvelId = ClePrimaire.allouerLibre(
                modePassationRepository::existsById, modePassationRepository::nextIdMode);
        ModePassation md = new ModePassation();
        md.setIdMode(nouvelId);
        md.setLibelle(libelle.trim());
        md.setIdTypeDmc(typeDmcService.deriverIdPourLibelle(md.getLibelle()));   // auto-mapping type de DMC
        modePassationRepository.save(md);
        auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_mode_passation",
                String.valueOf(nouvelId), "CREATION_A_LA_VOLEE", null);
        signalerSiProcheModeAgpm(nouvelId, libelle.trim(), cible, refs);
        return nouvelId;
    }

    /**
     * ⚠️ Règle ajoutée — filet AGPM : si le mode créé à la volée est proche d'un mode existant
     * {@code declencheAgpm=true}, le signaler (WARN + audit) pour qu'un Admin arbitre (fusion / coche du
     * drapeau). Sans blocage : la création reste permise (un libellé réellement nouveau reste créé).
     */
    private void signalerSiProcheModeAgpm(int nouvelId, String libelle, String cibleNormalisee,
            List<ModePassation> refs) {
        for (ModePassation m : refs) {
            if (!Boolean.TRUE.equals(m.getDeclencheAgpm()) || m.getLibelle() == null) {
                continue;
            }
            int d = LibelleNormalisation.distance(cibleNormalisee, LibelleNormalisation.separerSource(m.getLibelle())[0]);
            if (d > 0 && d <= SaisiePpmImportService.SEUIL_SUGGESTION) {
                log.warn("Mode créé à la volée « {} » (id {}) proche du mode déclencheur d'AGPM « {} » (id {}, "
                        + "distance {}) — vérifier s'il s'agit d'une coquille (fusion / DECLENCHE_AGPM à arbitrer).",
                        libelle, nouvelId, m.getLibelle(), m.getIdMode(), d);
                auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_mode_passation",
                        String.valueOf(nouvelId), "CREATION_MODE_PROCHE_AGPM", null);
                return;
            }
        }
    }

    /**
     * (Règle ajoutée) Compte budgétaire du marché : réutilise l'existant si présent dans {@code tr_compte}
     * (PK = {@code NUM_COMPTE}), sinon le <strong>crée à la volée</strong> (import PPM). Ne supprime jamais.
     * {@code null}/vide → {@code null}. Évite la violation FK {@code t_marche.NUM_COMPTE → tr_compte}.
     */
    private String resoudreOuCreerCompte(String numCompte) {
        if (numCompte == null || numCompte.isBlank()) {
            return null;
        }
        String num = numCompte.trim();
        if (!compteRepository.existsById(num)) {
            compteRepository.save(new Compte(num, num, null, null));   // libellé par défaut = numéro
            auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_compte", num, "CREATION_A_LA_VOLEE", null);
        }
        return num;
    }

    /**
     * (Règle ajoutée) Cohérence des montants — <strong>uniquement si</strong> {@code beneficiaires[]} est non vide,
     * <strong>égalité exacte</strong> (montants entiers Ariary) : {@code Σ ancMontBenef = montEstim} ; et si
     * {@code nouvMontEstim} est fourni, chaque bénéficiaire doit porter {@code nouvMontBenef} et
     * {@code Σ nouvMontBenef = nouvMontEstim}. Écart → 400 ciblé sur {@code marches[i].beneficiaires}.
     */
    private void validerCoherenceMontants(SaisieMarcheLigne ligne, int i) {
        List<SaisieBeneficiaireLigne> benefs = ligne.beneficiaires();
        if (benefs == null || benefs.isEmpty()) {
            return;
        }
        String champ = "marches[" + i + "].beneficiaires";
        BigDecimal montEstim = ligne.montEstim() == null ? BigDecimal.ZERO : ligne.montEstim();
        BigDecimal sommeAnc = benefs.stream()
                .map(b -> b.ancMontBenef() == null ? BigDecimal.ZERO : b.ancMontBenef())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sommeAnc.compareTo(montEstim) != 0) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(champ,
                    "La somme des montants par bénéficiaire (" + sommeAnc.toPlainString()
                            + ") doit égaler le montant estimatif du marché (" + montEstim.toPlainString() + ").")));
        }
        if (ligne.nouvMontEstim() != null) {
            if (benefs.stream().anyMatch(b -> b.nouvMontBenef() == null)) {
                throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(champ,
                        "Le nouveau montant estimatif est fourni : chaque bénéficiaire doit porter nouvMontBenef.")));
            }
            BigDecimal sommeNouv = benefs.stream().map(SaisieBeneficiaireLigne::nouvMontBenef)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sommeNouv.compareTo(ligne.nouvMontEstim()) != 0) {
                throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(champ,
                        "La somme des nouveaux montants par bénéficiaire (" + sommeNouv.toPlainString()
                                + ") doit égaler le nouveau montant estimatif du marché ("
                                + ligne.nouvMontEstim().toPlainString() + ").")));
            }
        }
    }

    /** (Règle ajoutée) Crée une ligne {@code t_service_beneficiaire} par bénéficiaire (PK allouée à la séquence {@code seq_service_beneficiaire}). */
    private void creerBeneficiaires(Integer idDetail, SaisieMarcheLigne ligne) {
        List<SaisieBeneficiaireLigne> benefs = ligne.beneficiaires();
        if (benefs == null || benefs.isEmpty()) {
            return;
        }
        for (SaisieBeneficiaireLigne b : benefs) {
            ServiceBeneficiaire sb = new ServiceBeneficiaire();
            // ⚠️ LOT 3b (2026-08-26) — PK allouee a la sequence, ligne par ligne (plus de compteur local).
            sb.setIdBenef(ClePrimaire.allouerLibre(
                    serviceBeneficiaireRepository::existsById, serviceBeneficiaireRepository::nextIdBenef));
            sb.setIdDetail(idDetail);
            sb.setSoaCode(resoudreOuCreerSoa(b.soaCode(), b.soaLibelle()));
            sb.setNumCompte(resoudreOuCreerCompte(b.numCompte()));
            sb.setAncMontBenef(b.ancMontBenef());
            sb.setNouvMontBenef(b.nouvMontBenef());
            serviceBeneficiaireRepository.save(sb);
        }
    }

    /**
     * (Règle ajoutée) Crée une ligne {@code t_lot} par lot (PK allouée à la séquence {@code seq_lot}), rattachée au marché
     * ({@code idDetail}) et à son dossier ({@code idDossier}). Descriptif — aucun contrôle de somme.
     */
    private void creerLots(Integer idDetail, Integer idDossier, SaisieMarcheLigne ligne) {
        List<SaisieLotLigne> lots = ligne.lots();
        if (lots == null || lots.isEmpty()) {
            return;
        }
        for (SaisieLotLigne l : lots) {
            Lot lot = new Lot();
            // ⚠️ LOT 3b (2026-08-26) — PK allouee a la sequence, ligne par ligne (plus de compteur local).
            lot.setIdLot(ClePrimaire.allouerLibre(lotRepository::existsById, lotRepository::nextIdLot));
            lot.setIdDossier(idDossier);
            lot.setIdDetail(idDetail);
            lot.setDesignationLot(l.designationLot());
            lot.setMontLot(l.montLot());
            lot.setQteLot(l.qteLot());
            lot.setUniteLot(l.uniteLot());
            lotRepository.save(lot);
        }
    }

    /**
     * (Règle ajoutée, étendue 2026-07-25) Service bénéficiaire (SOA), résolu-ou-créé — jamais de suppression :
     * <ol>
     *   <li><strong>Code SOA présent</strong> (ancien format, ex. {@code 00-61-0-D10-00000}) : réutilise
     *       l'existant (PK = {@code soaCode}), sinon crée (libellé = celui fourni, sinon le code).</li>
     *   <li><strong>Service en texte libre</strong> (format SIGMP « Service Administratif et Financier », sans
     *       code) : résout par <strong>libellé normalisé</strong> ({@link LibelleNormalisation}) dans
     *       {@code tr_soa_beneficiaire} ; sinon crée avec un code <strong>dérivé du libellé</strong> (slug ≤ 25,
     *       suffixé en cas de collision).</li>
     * </ol>
     * Ni code ni libellé → {@code null}.
     */
    private String resoudreOuCreerSoa(String soaCode, String soaLibelle) {
        boolean codePresent = soaCode != null && !soaCode.isBlank();
        boolean libPresent = soaLibelle != null && !soaLibelle.isBlank();
        if (codePresent) {
            String code = soaCode.trim();
            if (!soaBeneficiaireRepository.existsById(code)) {
                soaBeneficiaireRepository.save(new SoaBeneficiaire(code, libPresent ? soaLibelle.trim() : code));
                auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_soa_beneficiaire", code,
                        "CREATION_A_LA_VOLEE", null);
            }
            return code;
        }
        if (!libPresent) {
            return null;
        }
        String cible = normaliser(soaLibelle);
        List<SoaBeneficiaire> refs = soaBeneficiaireRepository.findAll();
        String existant = refs.stream()
                .filter(s -> s.getLibelle() != null && normaliser(s.getLibelle()).equals(cible))
                .map(SoaBeneficiaire::getSoaCode).findFirst().orElse(null);
        if (existant != null) {
            return existant;
        }
        String code = genererCodeSoa(soaLibelle, refs);
        soaBeneficiaireRepository.save(new SoaBeneficiaire(code, soaLibelle.trim()));
        auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_soa_beneficiaire", code,
                "CREATION_A_LA_VOLEE", null);
        return code;
    }

    /** Code SOA à la volée dérivé d'un libellé : slug normalisé (MAJUSCULES, ≤ 25), suffixé « -n » si déjà pris. */
    private String genererCodeSoa(String libelle, List<SoaBeneficiaire> refs) {
        String base = normaliser(libelle);
        if (base.isEmpty()) {
            base = "SOA";
        }
        if (base.length() > 25) {
            base = base.substring(0, 25);
        }
        java.util.Set<String> pris = new java.util.HashSet<>();
        for (SoaBeneficiaire s : refs) {
            if (s.getSoaCode() != null) {
                pris.add(s.getSoaCode());
            }
        }
        String code = base;
        int n = 1;
        while (pris.contains(code) || soaBeneficiaireRepository.existsById(code)) {
            String suffixe = "-" + (++n);
            code = (base.length() + suffixe.length() > 25 ? base.substring(0, 25 - suffixe.length()) : base) + suffixe;
        }
        return code;
    }

    /** Normalisation pour dé-duplication — déléguée à la source unique {@link LibelleNormalisation} (étendue). */
    private static String normaliser(String s) {
        return LibelleNormalisation.normaliser(s);
    }

    /**
     * Saisie d'un dossier sans contenu (familles DMC / DDM) = un {@code t_dossier} (famille + sous-type
     * + localité), BROUILLON. ⚠️ Règle ajoutée — la PRMP choisit un <strong>sous-type</strong>
     * ({@code idSousType}, référentiel administrable) ; la famille s'en déduit. Un sous-type de la
     * famille DDP est refusé (utiliser la façade PPM). {@code idTypeDossier} (déprécié) reste accepté
     * en repli et est interprété comme un code de sous-type.
     */
    public DossierDto saisirDossier(SaisieDossierRequest req) {
        String code = req.idSousType() != null && !req.idSousType().isBlank()
                ? req.idSousType().trim() : req.idTypeDossier();
        if (code == null || code.isBlank()) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(
                    "idSousType", "Le sous-type de dossier est obligatoire.")));
        }
        SousTypeDossier sousType = sousTypeDossierRepository.findById(code.trim())
                .orElseThrow(() -> new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(
                        "idSousType", "Sous-type de dossier inconnu : « " + code.trim()
                                + " » (référentiel /api/sous-type-dossiers)."))));
        if (FAMILLE_DDP.equals(sousType.getIdTypeDossier())) {
            throw new BusinessRuleException(
                    "Pour un dossier de planification (PPM), utilisez POST /api/saisies/ppm.");
        }
        String idPrmp = prmpCourante();
        String localite = dossierIntegrite.localiteDeLEntiteDeLaPrmp(req.idEntiteContract(), idPrmp);
        Dossier d = creerDossier(sousType.getIdTypeDossier(), sousType.getIdSousType(),
                localite, idPrmp, req.idEntiteContract());
        return DossierMapper.toDto(d);
    }

    /**
     * ⚠️ Spec « Mandats PRMP » — c'est ici que l'<strong>attribution se fige</strong> : le dossier retient le
     * mandat en cours au moment de sa création ({@code ID_MANDAT_ATTRIB}) et ne le recalculera jamais. Un
     * changement de PRMP ne réattribue donc rien rétroactivement ; le successeur agira comme
     * <em>opérateur</em> (cf. {@code t_action_dossier}). Créer suppose d'être en fonction : sans mandat
     * actif, la saisie est bloquée (409 {@code VACANCE_PRMP}).
     */
    private Dossier creerDossier(String famille, String sousType, String idLocalite, String idPrmp,
            Integer idEntiteContract) {
        dossierIntegrite.exigerMandatActif();   // même garde (et même filtre de profil) que les éditions
        Dossier d = new Dossier();
        d.setIdDossier(dossierRepository.nextIdDossier().intValue());   // PK serveur (séquence)
        d.setIdTypeDossier(famille);
        d.setIdSousType(sousType);   // famille DDP : dérivé serveur ; DMC/DDM : choisi à la saisie
        d.setIdLocalite(idLocalite);
        d.setIdPrmp(idPrmp);   // périmètre = PRMP (pour une UGPM, sa PRMP de tutelle via CurrentUser.ref())
        d.setIdMandatAttrib(mandatService.idMandatCourant(idPrmp));   // figé une fois pour toutes
        d.setIdEntiteContract(idEntiteContract);
        d.setStatut(StatutDossier.BROUILLON.name());
        d.setDateSoumission(java.time.LocalDateTime.now());   // date/heure de saisie du dossier (§ secrétariat)
        d.setCreePar(CurrentUser.login().orElse(idPrmp));   // traçabilité : login créateur (PRMP ou UGPM)
        Dossier cree = dossierRepository.save(d);
        journalDossier.tracer(cree, JournalDossierService.CREATION,
                "Création du dossier (" + famille + " / " + sousType + ")");
        return cree;
    }

    private String prmpCourante() {
        return CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur PRMP non identifié."));
    }

    /** Libellé de l'entité contractante (source de l'acronyme de la référence PPM). */
    private String libelleEntite(Integer idEntiteContract) {
        return entiteContractRepository.findById(idEntiteContract).map(e -> e.getLibelleEntite()).orElse(null);
    }

    /** Signataire = « prénoms nom » de la PRMP (équivalent de t_prmp.signataire, absent) ; repli sur idPrmp. */
    private String signataireDeLaPrmp(String idPrmp) {
        return prmpRepository.findById(idPrmp).map(p -> {
            String n = ((p.getPrenomsPrmp() == null ? "" : p.getPrenomsPrmp()) + " "
                    + (p.getNomPrmp() == null ? "" : p.getNomPrmp())).trim();
            return n.isBlank() ? idPrmp : n;
        }).orElse(idPrmp);
    }
}
