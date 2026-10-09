package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ActeGestionDto;
import cnm.prs.entity.ActeGestion;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ActeGestionRepository;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5a, §B1 et §B5 ; V98 ; arbitrages du pilote : montant initial et catégorie lus,
 * sinon déclarés au dépôt ; cumul des avenants = ceux déjà soumis, hors avis défavorable, montants HT) — les actes de gestion
 * contractuelle déposés <strong>depuis le marché</strong> :
 * <ul>
 *   <li>un acte (famille DGC : avenant, résiliation, indemnité, remise de pénalités, sursis) se dépose sur un dossier de marché (DDM)
 *   dont le dernier PV signé est favorable ({@code FAV}, {@code FAVR}) — la Commission n'examine que les actes des contrats qu'elle a
 *   contrôlés a priori ; il naît en brouillon, à la PRMP du marché, avec l'entité et la localité du marché ;</li>
 *   <li>l'avenant prend un <strong>rang</strong> (1, 2…), qui entre dans la référence ({@code …/AVN2/…}) ;</li>
 *   <li>l'avenant est refusé après la réception définitive (travaux) ou provisoire (fournitures, services, prestations intellectuelles),
 *   après le règlement du solde, et quand le cumul des avenants dépasse le tiers du montant initial HT — au dépôt, à la modification
 *   et de nouveau à la soumission.</li>
 * </ul>
 */
@Service
@Transactional
public class ActesGestionService {

    public static final String FAMILLE_DGC = "DGC";
    public static final String AVENANT = "AVN";
    static final String FAMILLE_DDM = "DDM";
    static final Set<String> AVIS_FAVORABLES = Set.of("FAV", "FAVR");
    static final Set<String> CATEGORIES = Set.of("FOURNITURES_SERVICES", "TRAVAUX", "PRESTATIONS_INTELLECTUELLES");
    private static final Set<String> HORS_CUMUL = Set.of(StatutDossier.BROUILLON.name(), StatutDossier.RETIRE.name(),
            StatutDossier.REMPLACE.name());

    private final ActeGestionRepository actes;
    private final DossierRepository dossiers;
    private final SousTypeDossierRepository sousTypes;
    private final PvExamenRepository pvs;
    private final AttributionRepository attributions;
    private final PiecesExigees pieces;
    private final DossierIntegriteService integrite;
    private final ObjectProvider<SaisieService> saisie;
    private final ObjectProvider<DossierService> dossierService;
    private final Clock clock;

    public ActesGestionService(ActeGestionRepository actes, DossierRepository dossiers, SousTypeDossierRepository sousTypes,
            PvExamenRepository pvs, AttributionRepository attributions, PiecesExigees pieces, DossierIntegriteService integrite,
            ObjectProvider<SaisieService> saisie, ObjectProvider<DossierService> dossierService,
            Clock clock) {
        this.actes = actes;
        this.dossiers = dossiers;
        this.sousTypes = sousTypes;
        this.pvs = pvs;
        this.attributions = attributions;
        this.pieces = pieces;
        this.integrite = integrite;
        this.saisie = saisie;
        this.dossierService = dossierService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------ lectures

    /** Le marché et ses actes (même garde de lecture que le dossier de marché). */
    @Transactional(readOnly = true)
    public ActeGestionDto.Marche marche(Integer idMarche) {
        Dossier marche = marcheExistant(idMarche);
        dossierService.getObject().controlerVisibilite(idMarche);
        return vue(marche);
    }

    /** L'acte porté par un dossier DGC (même garde de lecture que ce dossier). */
    @Transactional(readOnly = true)
    public ActeGestionDto.Acte acte(Integer idDossier) {
        ActeGestion a = actes.findByIdDossier(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun acte de gestion sur le dossier " + idDossier + "."));
        dossierService.getObject().controlerVisibilite(idDossier);
        List<ActeGestion> duMarche = actes.findByIdDossierMarcheOrderByIdActeAsc(a.getIdDossierMarche());
        Map<Integer, Dossier> parId = dossiersDe(duMarche);
        return dto(a, parId.get(a.getIdDossier()), compte(a, parId));
    }

    // ------------------------------------------------------------------------------------------------ écritures

    /**
     * Dépôt d'un acte depuis le marché : 404 marché inconnu ; 400 {@code PAS_UN_MARCHE}, {@code SOUS_TYPE_HORS_DGC},
     * {@code MONTANT_INITIAL_OBLIGATOIRE}, {@code CATEGORIE_OBLIGATOIRE}, {@code CATEGORIE_INCONNUE} ; 403 hors PRMP du marché ;
     * 409 {@code MARCHE_NON_CONTROLE}, {@code AVENANT_APRES_RECEPTION}, {@code AVENANT_APRES_SOLDE}, {@code AVENANT_PLAFOND}.
     */
    public ActeGestionDto.Acte deposer(Integer idMarche, ActeGestionDto.Demande demande) {
        Dossier marche = marcheExistant(idMarche);
        integrite.exigerOperateurHabilite(marche);
        exigerMarcheControle(marche);
        String sousType = sousTypeDgc(demande == null ? null : demande.sousType());
        ActeGestion a = new ActeGestion();
        a.setIdDossierMarche(idMarche);
        a.setSousType(sousType);
        appliquer(a, demande);
        List<ActeGestion> autres = actes.findByIdDossierMarcheOrderByIdActeAsc(idMarche);
        if (AVENANT.equals(sousType)) {
            a.setRang(autres.stream().filter(x -> AVENANT.equals(x.getSousType())).map(ActeGestion::getRang).filter(Objects::nonNull)
                    .max(Integer::compareTo).orElse(0) + 1);
            exigerAvenantRecevable(marche, a, autres);
        }
        Dossier d = saisie.getObject().creerDossierMarche(sousType, marche.getIdLocalite(), marche.getIdEntiteContract());
        a.setIdDossier(d.getIdDossier());
        a.setCreeLe(LocalDateTime.now(clock));
        a.setCreePar(CurrentUser.login().orElse(null));
        ActeGestion sauve = actes.save(a);
        return dto(sauve, d, false);
    }

    /** Modification des déclarations d'un acte encore en brouillon : 409 {@code DOSSIER_NON_BROUILLON} ; mêmes garde-fous qu'au dépôt. */
    public ActeGestionDto.Acte modifier(Integer idDossier, ActeGestionDto.Demande demande) {
        ActeGestion a = actes.findByIdDossier(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun acte de gestion sur le dossier " + idDossier + "."));
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        integrite.exigerOperateurHabilite(d);
        if (!StatutDossier.BROUILLON.name().equals(d.getStatut())) {
            throw new BusinessRuleException("L'acte ne se modifie qu'en brouillon (statut « " + d.getStatut() + " »).",
                    "DOSSIER_NON_BROUILLON", idDossier);
        }
        appliquer(a, demande);
        if (AVENANT.equals(a.getSousType())) {
            Dossier marche = marcheExistant(a.getIdDossierMarche());
            exigerAvenantRecevable(marche, a, autresQue(a));
        }
        return dto(actes.save(a), d, false);
    }

    /**
     * Garde de la soumission d'un dossier DGC (appelée par {@link DossierService#soumettre}) : 409 {@code ACTE_SANS_MARCHE} pour un
     * dossier DGC né hors du marché ; le marché doit être toujours contrôlé ; l'avenant est revu (réception, solde, plafond).
     */
    @Transactional(readOnly = true)
    public void exigerAvantSoumission(Dossier d) {
        if (!FAMILLE_DGC.equals(d.getIdTypeDossier())) {
            return;
        }
        ActeGestion a = actes.findByIdDossier(d.getIdDossier()).orElseThrow(() -> new BusinessRuleException(
                "Un acte de gestion contractuelle se dépose depuis le marché qu'il concerne.", "ACTE_SANS_MARCHE", d.getIdDossier()));
        Dossier marche = marcheExistant(a.getIdDossierMarche());
        exigerMarcheControle(marche);
        if (AVENANT.equals(a.getSousType())) {
            exigerAvenantRecevable(marche, a, autresQue(a));
        }
    }

    // ------------------------------------------------------------------------------------------------ règles

    private void exigerAvenantRecevable(Dossier marche, ActeGestion a, List<ActeGestion> autres) {
        Faits f = faits(marche, a, autres);
        if (f.montantInitial() == null) {
            throw new BadRequestException("Le montant initial HT du marché est inconnu : déclarez-le (montantInitialHt).",
                    "MONTANT_INITIAL_OBLIGATOIRE");
        }
        if (f.categorie() == null) {
            throw new BadRequestException("La catégorie du marché est inconnue : déclarez-la (categorie).", "CATEGORIE_OBLIGATOIRE");
        }
        LocalDate aujourdhui = LocalDate.now(clock);
        boolean travaux = "TRAVAUX".equals(f.categorie());
        LocalDate reception = travaux ? f.receptionDefinitive() : premiere(f.receptionProvisoire(), f.receptionDefinitive());
        if (reception != null && !reception.isAfter(aujourdhui)) {
            throw new BusinessRuleException("Un avenant ne se conclut plus après la réception " + (travaux ? "définitive des travaux"
                    : "provisoire des fournitures ou services") + " (prononcée le " + reception + ").", "AVENANT_APRES_RECEPTION",
                    a.getIdDossier(), Map.of("dateReception", reception.toString()));
        }
        if (f.solde() != null && !f.solde().isAfter(aujourdhui)) {
            throw new BusinessRuleException("Un avenant ne se conclut plus après le règlement du solde du marché (le " + f.solde() + ").",
                    "AVENANT_APRES_SOLDE", a.getIdDossier(), Map.of("dateSolde", f.solde().toString()));
        }
        Map<Integer, Dossier> parId = dossiersDe(autres);
        BigDecimal cumul = autres.stream().filter(x -> AVENANT.equals(x.getSousType()) && compte(x, parId)).map(ActeGestion::getMontantHt)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).add(a.getMontantHt() == null ? BigDecimal.ZERO : a.getMontantHt());
        BigDecimal plafond = plafond(f.montantInitial());
        if (cumul.compareTo(plafond) > 0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("cumulHt", cumul);
            details.put("plafondHt", plafond);
            details.put("montantInitialHt", f.montantInitial());
            throw new BusinessRuleException("Le cumul des avenants (" + cumul.toPlainString() + " HT) dépasse le tiers du montant initial du "
                    + "marché (" + plafond.toPlainString() + " HT).", "AVENANT_PLAFOND", a.getIdDossier(), details);
        }
    }

    /** Un avenant antérieur entre dans le cumul : déjà soumis (ni brouillon, ni retiré), et son dernier avis signé n'est pas défavorable. */
    private boolean compte(ActeGestion x, Map<Integer, Dossier> parId) {
        Dossier d = parId.get(x.getIdDossier());
        return d != null && d.getStatut() != null && !HORS_CUMUL.contains(d.getStatut()) && !"DEF".equals(avis(d.getIdDossier()));
    }

    private void exigerMarcheControle(Dossier marche) {
        String avis = avis(marche.getIdDossier());
        if (avis == null || !AVIS_FAVORABLES.contains(avis)) {
            throw new BusinessRuleException("La Commission n'examine que les actes des marchés qu'elle a contrôlés a priori : le marché "
                    + libelle(marche) + " n'a pas de PV favorable signé.", "MARCHE_NON_CONTROLE", marche.getIdDossier());
        }
    }

    /** Les faits du marché : connus du serveur d'abord, puis déclarés (l'acte courant, puis les autres du plus récent au plus ancien). */
    private Faits faits(Dossier marche, ActeGestion courant, List<ActeGestion> autres) {
        List<ActeGestion> ordre = new java.util.ArrayList<>();
        if (courant != null) {
            ordre.add(courant);
        }
        for (int i = autres.size() - 1; i >= 0; i--) {
            ordre.add(autres.get(i));
        }
        BigDecimal montantConnu = attributions.findFirstByIdDossier(marche.getIdDossier()).map(Attribution::getMontant).orElse(null);
        BigDecimal montantDeclare = premier(ordre, ActeGestion::getMontantInitialHt);
        String categorieConnue = pieces.contexte(marche).categorie();
        String categorieDeclaree = premier(ordre, ActeGestion::getCategorie);
        return new Faits(montantConnu != null ? montantConnu : montantDeclare,
                montantConnu != null ? "ATTRIBUTION" : montantDeclare != null ? "DECLARE" : null,
                categorieConnue != null ? categorieConnue : categorieDeclaree,
                categorieConnue != null ? "FICHE" : categorieDeclaree != null ? "DECLARE" : null,
                premier(ordre, ActeGestion::getDateReceptionProvisoire), premier(ordre, ActeGestion::getDateReceptionDefinitive),
                premier(ordre, ActeGestion::getDateSolde));
    }

    private record Faits(BigDecimal montantInitial, String sourceMontant, String categorie, String sourceCategorie,
            LocalDate receptionProvisoire, LocalDate receptionDefinitive, LocalDate solde) {
    }

    // ------------------------------------------------------------------------------------------------ outils

    private Dossier marcheExistant(Integer idMarche) {
        Dossier marche = dossiers.findById(idMarche).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idMarche));
        if (!FAMILLE_DDM.equals(marche.getIdTypeDossier())) {
            throw new BadRequestException("Le dossier " + idMarche + " n'est pas un dossier de marché (famille DDM).", "PAS_UN_MARCHE");
        }
        return marche;
    }

    private String sousTypeDgc(String code) {
        String c = code == null ? "" : code.trim();
        SousTypeDossier st = sousTypes.findById(c).orElse(null);
        if (st == null || !FAMILLE_DGC.equals(st.getIdTypeDossier())) {
            throw new BadRequestException("Sous-type d'acte de gestion inconnu : « " + c + " » (AVN, DR, INDEMN, PENAL, SURSIS).",
                    "SOUS_TYPE_HORS_DGC");
        }
        return st.getIdSousType();
    }

    private static void appliquer(ActeGestion a, ActeGestionDto.Demande d) {
        if (d == null) {
            return;
        }
        if (d.categorie() != null && !d.categorie().isBlank() && !CATEGORIES.contains(d.categorie().trim())) {
            throw new BadRequestException("Catégorie inconnue : « " + d.categorie().trim() + " » (attendu : " + CATEGORIES + ").",
                    "CATEGORIE_INCONNUE");
        }
        a.setMontantHt(AVENANT.equals(a.getSousType()) ? d.montantHt() : null);
        a.setMontantInitialHt(d.montantInitialHt());
        a.setCategorie(d.categorie() == null || d.categorie().isBlank() ? null : d.categorie().trim());
        a.setDateReceptionProvisoire(d.dateReceptionProvisoire());
        a.setDateReceptionDefinitive(d.dateReceptionDefinitive());
        a.setDateSolde(d.dateSolde());
    }

    private List<ActeGestion> autresQue(ActeGestion a) {
        return actes.findByIdDossierMarcheOrderByIdActeAsc(a.getIdDossierMarche()).stream()
                .filter(x -> !x.getIdActe().equals(a.getIdActe())).toList();
    }

    private Map<Integer, Dossier> dossiersDe(List<ActeGestion> liste) {
        Map<Integer, Dossier> parId = new LinkedHashMap<>();
        dossiers.findAllById(liste.stream().map(ActeGestion::getIdDossier).toList()).forEach(d -> parId.put(d.getIdDossier(), d));
        return parId;
    }

    private String avis(Integer idDossier) {
        return pvs.findSignesParDossierRows(idDossier).stream().findFirst().map(PvExamen::getIdAvis).orElse(null);
    }

    private ActeGestionDto.Marche vue(Dossier marche) {
        List<ActeGestion> liste = actes.findByIdDossierMarcheOrderByIdActeAsc(marche.getIdDossier());
        Map<Integer, Dossier> parId = dossiersDe(liste);
        Faits f = faits(marche, null, liste);
        List<ActeGestionDto.Acte> dtos = liste.stream().map(a -> dto(a, parId.get(a.getIdDossier()), compte(a, parId))).toList();
        BigDecimal cumul = dtos.stream().filter(x -> AVENANT.equals(x.sousType()) && x.compteDansLeCumul()).map(ActeGestionDto.Acte::montantHt)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        int suivant = liste.stream().filter(x -> AVENANT.equals(x.getSousType())).map(ActeGestion::getRang).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        return new ActeGestionDto.Marche(marche.getIdDossier(), marche.getIdSousType(), marche.getRefeDossier(), avis(marche.getIdDossier()),
                f.montantInitial(), f.sourceMontant(), f.categorie(), f.sourceCategorie(), f.receptionProvisoire(), f.receptionDefinitive(),
                f.solde(), cumul, f.montantInitial() == null ? null : plafond(f.montantInitial()), suivant, dtos);
    }

    private ActeGestionDto.Acte dto(ActeGestion a, Dossier d, boolean compte) {
        return new ActeGestionDto.Acte(a.getIdActe(), a.getIdDossier(), a.getIdDossierMarche(), a.getSousType(), a.getRang(), a.getMontantHt(),
                a.getMontantInitialHt(), a.getCategorie(), a.getDateReceptionProvisoire(), a.getDateReceptionDefinitive(), a.getDateSolde(),
                d == null ? null : d.getStatut(), d == null ? null : d.getRefeDossier(), d == null ? null : avis(d.getIdDossier()),
                AVENANT.equals(a.getSousType()) && compte, a.getCreeLe());
    }

    private static BigDecimal plafond(BigDecimal montantInitial) {
        return montantInitial.divide(BigDecimal.valueOf(3), 2, RoundingMode.DOWN);
    }

    private static String libelle(Dossier marche) {
        return marche.getRefeDossier() != null ? marche.getRefeDossier() : "n° " + marche.getIdDossier();
    }

    private static <T> T premier(List<ActeGestion> ordre, Function<ActeGestion, T> champ) {
        return ordre.stream().map(champ).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static LocalDate premiere(LocalDate a, LocalDate b) {
        return a == null ? b : b == null ? a : a.isBefore(b) ? a : b;
    }
}
