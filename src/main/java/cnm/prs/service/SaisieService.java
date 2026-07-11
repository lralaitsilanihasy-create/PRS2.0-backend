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
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;

/**
 * Façade de saisie (§3.1, Module 02) : « saisir un PPM/DAO/MAOO » EST créer le dossier à soumettre.
 *
 * <p>En un seul appel transactionnel, crée le {@code t_dossier} (statut <strong>BROUILLON</strong>,
 * propriété de la PRMP courante) et son contenu. Le PPM et les lignes de marché passent par
 * {@link PpmService}/{@link MarcheService}, donc par les garde-fous d'intégrité partagés
 * ({@link DossierIntegriteService}) : aucun chemin ne crée d'incohérence.</p>
 */
@Service
@Transactional
public class SaisieService {

    private static final String TYPE_PPM = "PPM";

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
            TypeDmcService typeDmcService, LotRepository lotRepository) {
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
    }

    /** Saisie d'un PPM = dossier (BROUILLON) + PPM + lignes de marché (mode auto), en une transaction. */
    public DossierDto saisirPpm(SaisiePpmRequest req) {
        String idPrmp = prmpCourante();
        // Localité dérivée de l'entité contractante choisie (parmi les entités de la PRMP).
        String localite = dossierIntegrite.localiteDeLEntiteDeLaPrmp(req.idEntiteContract(), idPrmp);
        Dossier dossier = creerDossier(TYPE_PPM, localite, idPrmp, req.idEntiteContract());  // PK séquence
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
            int prevSeq = marchePrevisionRepository.findMaxId();   // PK prévision allouée serveur (max+1)
            for (int i = 0; i < req.marches().size(); i++) {
                SaisieMarcheLigne ligne = req.marches().get(i);
                exigerAuMoinsUnProcessus(ligne, i);   // « au moins un processus » (NotEmpty, à la création)
                // Charge les CAPM (idCapm existant, sinon 400) et valide la chronologie des processus.
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
                validerCoherenceMontants(ligne, i);   // Σ bénéficiaires = montant(s) du marché (si beneficiaires[] non vide)
                Integer idDetail = marcheService.create(toMarcheDto(ligne, idDossier, idPpm)).getIdDetail();
                for (ProcessusMarche p : ligne.processus()) {
                    marchePrevisionService.create(new MarchePrevisionDto(
                            ++prevSeq, idDetail, p.idCapm(), p.dateDebut(), p.dateFin(), null));
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
        exigerPiecesObligatoires(TYPE_PPM, pieces);   // contrôle AVANT toute persistance (400 si manquante)
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
     * Édition d'un brouillon PPM (§3.1 M02) : met à jour l'en-tête du PPM et <strong>réconcilie</strong>
     * ses lignes de marché avec la liste fournie (ajout / mise à jour par {@code idDetail} / retrait des
     * absentes), en une transaction. Garde-fous (propriété, statut BROUILLON, type PPM) + mode recalculé.
     */
    public DossierDto editerPpm(Integer idDossier, EditionPpmRequest req) {
        dossierIntegrite.exigerBrouillonModifiable(idDossier);
        dossierIntegrite.exigerTypePpm(idDossier);
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Aucun PPM rattaché au dossier " + idDossier + "."));

        // 1) En-tête PPM (on repart de l'existant pour conserver idPrmp/idLocalite/idDossier).
        PpmDto entete = PpmMapper.toDto(ppm);
        entete.setExercice(req.exercice());
        entete.setSignataire(req.signataire());
        entete.setDateSignature(req.dateSignature());
        entete.setReference(req.reference());
        ppmService.update(ppm.getIdPpm(), entete);

        // 2) Réconciliation des lignes par idDetail.
        Set<Integer> existants = new HashSet<>();
        for (Marche m : marcheRepository.findByIdDossier(idDossier)) {
            existants.add(m.getIdDetail());
        }
        Set<Integer> demandes = new HashSet<>();
        if (req.marches() != null) {
            for (SaisieMarcheLigne ligne : req.marches()) {
                demandes.add(ligne.idDetail());
                MarcheDto m = toMarcheDto(ligne, idDossier, ppm.getIdPpm());
                if (existants.contains(ligne.idDetail())) {
                    marcheService.update(ligne.idDetail(), m);
                } else {
                    marcheService.create(m);
                }
            }
        }
        // 3) Retrait des lignes absentes de la demande.
        for (Integer id : existants) {
            if (!demandes.contains(id)) {
                marcheService.delete(id);
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
     * accents) dans {@code tr_nature} ; à défaut, la <strong>crée à la volée</strong> (PK = max+1) et la trace.
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
        int nouvelId = refs.stream().map(Nature::getIdNature).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        natureRepository.save(new Nature(nouvelId, libelle.trim(), null));
        auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_nature",
                String.valueOf(nouvelId), "CREATION_A_LA_VOLEE", null);
        return nouvelId;
    }

    /** Idem {@link #resoudreOuCreerNature} pour le mode de passation ({@code tr_mode}). */
    private Integer resoudreOuCreerMode(Integer id, String libelle) {
        if (id != null) {
            return id;
        }
        if (libelle == null || libelle.isBlank()) {
            return null;
        }
        String cible = normaliser(libelle);
        List<ModePassation> refs = modePassationRepository.findAll();
        Integer existant = refs.stream()
                .filter(md -> md.getLibelle() != null && normaliser(md.getLibelle()).equals(cible))
                .map(ModePassation::getIdMode).findFirst().orElse(null);
        if (existant != null) {
            return existant;
        }
        int nouvelId = refs.stream().map(ModePassation::getIdMode).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        ModePassation md = new ModePassation();
        md.setIdMode(nouvelId);
        md.setLibelle(libelle.trim());
        md.setIdTypeDmc(typeDmcService.deriverIdPourLibelle(md.getLibelle()));   // auto-mapping type de DMC
        modePassationRepository.save(md);
        auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_mode_passation",
                String.valueOf(nouvelId), "CREATION_A_LA_VOLEE", null);
        return nouvelId;
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

    /** (Règle ajoutée) Crée une ligne {@code t_service_beneficiaire} par bénéficiaire (PK allouée max+1). */
    private void creerBeneficiaires(Integer idDetail, SaisieMarcheLigne ligne) {
        List<SaisieBeneficiaireLigne> benefs = ligne.beneficiaires();
        if (benefs == null || benefs.isEmpty()) {
            return;
        }
        int nextId = serviceBeneficiaireRepository.findMaxIdBenef() + 1;
        for (SaisieBeneficiaireLigne b : benefs) {
            ServiceBeneficiaire sb = new ServiceBeneficiaire();
            sb.setIdBenef(nextId++);
            sb.setIdDetail(idDetail);
            sb.setSoaCode(resoudreOuCreerSoa(b.soaCode()));
            sb.setNumCompte(resoudreOuCreerCompte(b.numCompte()));
            sb.setAncMontBenef(b.ancMontBenef());
            sb.setNouvMontBenef(b.nouvMontBenef());
            serviceBeneficiaireRepository.save(sb);
        }
    }

    /**
     * (Règle ajoutée) Crée une ligne {@code t_lot} par lot (PK allouée max+1), rattachée au marché
     * ({@code idDetail}) et à son dossier ({@code idDossier}). Descriptif — aucun contrôle de somme.
     */
    private void creerLots(Integer idDetail, Integer idDossier, SaisieMarcheLigne ligne) {
        List<SaisieLotLigne> lots = ligne.lots();
        if (lots == null || lots.isEmpty()) {
            return;
        }
        int nextId = lotRepository.findMaxIdLot() + 1;
        for (SaisieLotLigne l : lots) {
            Lot lot = new Lot();
            lot.setIdLot(nextId++);
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
     * (Règle ajoutée) Service bénéficiaire (SOA) : réutilise l'existant si présent dans {@code tr_soa_beneficiaire}
     * (PK = {@code soaCode}), sinon le <strong>crée à la volée</strong> — jamais de suppression. {@code null}/vide → {@code null}.
     */
    private String resoudreOuCreerSoa(String soaCode) {
        if (soaCode == null || soaCode.isBlank()) {
            return null;
        }
        String code = soaCode.trim();
        if (!soaBeneficiaireRepository.existsById(code)) {
            soaBeneficiaireRepository.save(new SoaBeneficiaire(code, code));   // libellé par défaut = code
            auditLogService.enregistrer(CurrentUser.ref().orElse(null), "tr_soa_beneficiaire", code,
                    "CREATION_A_LA_VOLEE", null);
        }
        return code;
    }

    /** Normalisation pour dé-duplication : sans accents, majuscules, sans caractères non alphanumériques. */
    private static String normaliser(String s) {
        String sansAccents = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toUpperCase(java.util.Locale.FRENCH).replaceAll("[^A-Z0-9]", "");
    }

    /** Saisie d'un dossier sans contenu (DAO/MAOO) = un {@code t_dossier} (type + localité), BROUILLON. */
    public DossierDto saisirDossier(SaisieDossierRequest req) {
        if (TYPE_PPM.equalsIgnoreCase(req.idTypeDossier())) {
            throw new BusinessRuleException("Pour un PPM, utilisez POST /api/saisies/ppm.");
        }
        String idPrmp = prmpCourante();
        String localite = dossierIntegrite.localiteDeLEntiteDeLaPrmp(req.idEntiteContract(), idPrmp);
        Dossier d = creerDossier(req.idTypeDossier(), localite, idPrmp, req.idEntiteContract());
        return DossierMapper.toDto(d);
    }

    private Dossier creerDossier(String type, String idLocalite, String idPrmp,
            Integer idEntiteContract) {
        Dossier d = new Dossier();
        d.setIdDossier(dossierRepository.nextIdDossier().intValue());   // PK serveur (séquence)
        d.setIdTypeDossier(type);
        d.setIdLocalite(idLocalite);
        d.setIdPrmp(idPrmp);   // périmètre = PRMP (pour une UGPM, sa PRMP de tutelle via CurrentUser.ref())
        d.setIdEntiteContract(idEntiteContract);
        d.setStatut(StatutDossier.BROUILLON.name());
        d.setDateSoumission(java.time.LocalDateTime.now());   // date/heure de saisie du dossier (§ secrétariat)
        d.setCreePar(CurrentUser.login().orElse(idPrmp));   // traçabilité : login créateur (PRMP ou UGPM)
        return dossierRepository.save(d);
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
