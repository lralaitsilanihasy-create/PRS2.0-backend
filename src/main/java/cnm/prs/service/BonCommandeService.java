package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.BesoinEnLigneDto;
import cnm.prs.dto.BonCommandeDto;
import cnm.prs.dto.ContratVigueurDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.BonCommande;
import cnm.prs.entity.BonCommandeLigne;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.ContratVigueur;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Offre;
import cnm.prs.entity.RemiseConcurrence;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.BonCommandeLigneRepository;
import cnm.prs.repository.BonCommandeRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.ContratVigueurRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.RemiseConcurrenceRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-3 ; V126 ; loi n° 2016-055, définition de l'accord-cadre et art. 30 ;
 * décret n° 2019-1064, art. 8, 33, 34) — les bons de commande, un seul module pour les deux formes (arbitrage du pilote) :
 *
 * <ul>
 *   <li><strong>Contrat-cadre</strong> : le bon « réglementaire » du marché subséquent (art. 33), passé avec le titulaire que la remise
 *       en concurrence a retenu, aux prix de son offre ; ses bons ne dépassent pas le montant retenu (409
 *       {@code MONTANT_SUBSEQUENT_DEPASSE}). Le cumul du contrat face à sa valeur maximale estimée (art. 8) est signalé, jamais refusé ;
 *       pas de minimum (art. 34).</li>
 *   <li><strong>Marché à commandes</strong> : des bons au fil des besoins, avec l'attributaire, aux prix du bordereau de son offre
 *       (repris du bordereau scellé quand il est lisible) ; le cumul de l'année contractuelle ne dépasse pas le maximum annuel
 *       ({@code B05-TP-03}, 409 {@code MAXIMUM_DEPASSE}) ; le minimum annuel ({@code B05-TP-02}) non atteint à moins de trente jours de
 *       la fin de l'année est signalé (loi, art. 30-II).</li>
 * </ul>
 * Aucun bon après l'échéance du contrat (409 {@code CONTRAT_ECHU}). Le bon (PDF et Word) est émis par la PRMP — il l'engage — et notifié
 * au titulaire ; sa première lecture par le titulaire vaut accusé de réception, tracé et notifié à la PRMP.
 */
@Service
public class BonCommandeService {

    public static final String MINIMUM_NON_ATTEINT = "MINIMUM_NON_ATTEINT";
    public static final String MONTANT_ESTIME_DEPASSE = "MONTANT_ESTIME_DEPASSE";
    /** ⚠️ 2026-10-29 (« bons-reception », §B1) — servie dans la consommation seulement. */
    public static final String QUANTITE_MIN_NON_ATTEINTE = "QUANTITE_MIN_NON_ATTEINTE";
    private static final Set<ProfilUtilisateur> EXTERNES = Set.of(ProfilUtilisateur.CANDIDAT, ProfilUtilisateur.DEPOSITAIRE,
            ProfilUtilisateur.MEMBRE_CAO);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final BonCommandeRepository bons;
    private final BonCommandeLigneRepository lignes;
    private final ContratVigueurRepository contrats;
    private final ContratVigueurService contratsService;
    private final RemiseConcurrenceRepository remises;
    private final AttributionRepository attributions;
    private final OffreRepository offres;
    private final CompteCandidatRepository candidats;
    private final DossierMecRepository dmcRepository;
    private final FicheMarcheService fiches;
    private final FormulairesEnLigne formulaires;
    private final SeanceService seance;
    private final ValeursPpmService valeursPpm;
    private final NotificationService notifications;
    private final CeremonieService ceremonies;
    private final EvaluationService evaluation;
    private final GenerateurDocumentsFiche generateur;
    private final Clock horloge;
    /** ⚠️ 2026-10-29 (« bons-reception », §B2, Q1) — le taux de TVA : l'offre du titulaire à l'attribution, à défaut le paramètre. */
    private final cnm.prs.repository.AttributionTitulaireRepository titulaires;
    private final ParametreService parametres;

    public BonCommandeService(BonCommandeRepository bons, BonCommandeLigneRepository lignes, ContratVigueurRepository contrats,
            ContratVigueurService contratsService, RemiseConcurrenceRepository remises, AttributionRepository attributions, OffreRepository offres,
            CompteCandidatRepository candidats, DossierMecRepository dmcRepository, FicheMarcheService fiches, FormulairesEnLigne formulaires,
            SeanceService seance, ValeursPpmService valeursPpm, NotificationService notifications, CeremonieService ceremonies,
            EvaluationService evaluation, GenerateurDocumentsFiche generateur, Clock horloge,
            cnm.prs.repository.AttributionTitulaireRepository titulaires, ParametreService parametres) {
        this.titulaires = titulaires;
        this.parametres = parametres;
        this.bons = bons;
        this.lignes = lignes;
        this.contrats = contrats;
        this.contratsService = contratsService;
        this.remises = remises;
        this.attributions = attributions;
        this.offres = offres;
        this.candidats = candidats;
        this.dmcRepository = dmcRepository;
        this.fiches = fiches;
        this.formulaires = formulaires;
        this.seance = seance;
        this.valeursPpm = valeursPpm;
        this.notifications = notifications;
        this.ceremonies = ceremonies;
        this.evaluation = evaluation;
        this.generateur = generateur;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ émission

    /**
     * La PRMP émet un bon. 400 {@code CHAMP_OBLIGATOIRE} ({@code delaiLivraison}, objet, désignation), {@code REMISE_OBLIGATOIRE}
     * (contrat-cadre), {@code LIGNES_OBLIGATOIRES}, {@code QUANTITE_INVALIDE}, {@code PRIX_OBLIGATOIRE}, {@code ARTICLE_INCONNU},
     * {@code PRIX_HORS_BORDEREAU} ; 409 {@code CONTRAT_ECHU}, {@code REMISE_NON_ATTRIBUEE}, {@code SANS_TITULAIRE},
     * {@code MONTANT_SUBSEQUENT_DEPASSE}, {@code MAXIMUM_DEPASSE} (avec {@code details}).
     */
    @Transactional
    public BonCommandeDto emettre(Long idContrat, BonCommandeDto.BonRequest r) {
        ContratVigueur c = contrats.findById(idContrat).orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + idContrat + "."));
        exigerPrmp(c.getIdDmc());
        contratsService.exigerEnVigueur(c.getIdDmc(), c.getLot());
        if (r == null) {
            throw new BadRequestException("Le bon de commande est à décrire.", "CHAMP_OBLIGATOIRE", Map.<String, Object>of("champ", "lignes"));
        }
        boolean cadre = ContratVigueur.CONTRAT_CADRE.equals(c.getTypeMarche());
        RemiseConcurrence remise = null;
        String idOffre;
        if (cadre) {
            if (r.idRemise() == null) {
                throw new BadRequestException("Le bon d'un contrat-cadre se rattache au marché subséquent attribué (idRemise).", "REMISE_OBLIGATOIRE");
            }
            remise = remises.findById(r.idRemise()).filter(x -> x.getIdContrat().equals(idContrat))
                    .orElseThrow(() -> new ResourceNotFoundException("Remise introuvable : " + r.idRemise() + "."));
            if (!RemiseConcurrence.ATTRIBUEE.equals(remise.getEtat())) {
                throw new BusinessRuleException("Le bon de commande suit l'attribution du marché subséquent (décret n° 2019-1064, art. 33).",
                        "REMISE_NON_ATTRIBUEE");
            }
            idOffre = remise.getIdOffreRetenue();
        } else {
            idOffre = contratsService.titulairesDto(c).stream().map(ContratVigueurDto.Titulaire::idOffre).findFirst()
                    .orElseThrow(() -> new BusinessRuleException("Le marché n'a pas de titulaire.", "SANS_TITULAIRE"));
        }
        String objet = nettoyer(r.objet());
        if (objet == null && remise != null) {
            objet = remise.getObjet();
        }
        if (objet == null) {
            throw new BadRequestException("« objet » est obligatoire.", "CHAMP_OBLIGATOIRE", Map.<String, Object>of("champ", "objet"));
        }
        String delai = nettoyer(r.delaiLivraison());
        if (delai == null && remise != null) {
            delai = remise.getDelaiExecution();
        }
        if (delai == null) {
            throw new BadRequestException("« delaiLivraison » est obligatoire.", "CHAMP_OBLIGATOIRE", Map.<String, Object>of("champ", "delaiLivraison"));
        }
        List<BonCommandeLigne> ls = lignes(c, idOffre, cadre, r.lignes());
        BigDecimal total = ls.stream().map(BonCommandeLigne::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BonCommande> duContrat = bons.findByIdContratOrderByNumeroAsc(idContrat);
        if (remise != null) {
            Long idRemise = remise.getId();
            BigDecimal deja = cumul(duContrat.stream().filter(b -> idRemise.equals(b.getIdRemise())).toList());
            if (remise.getMontantRetenu() != null && deja.add(total).compareTo(remise.getMontantRetenu()) > 0) {
                throw new BusinessRuleException("Les bons du marché subséquent ne dépassent pas son montant retenu ("
                        + FormulairesEnLigne.lisible(remise.getMontantRetenu()) + " Ariary HT ; déjà commandé : " + FormulairesEnLigne.lisible(deja)
                        + ").", "MONTANT_SUBSEQUENT_DEPASSE", null, Map.<String, Object>of("montantRetenu", remise.getMontantRetenu(), "dejaCommande", deja, "montant", total));
            }
        }
        if (!cadre) {
            controlerQuantites(c, ls, duContrat);   // ⚠️ 2026-10-29 (« bons-reception », §B1)
        }
        if (remise == null && c.getMontantMaximum() != null) {
            LocalDate[] annee = anneeContractuelle(c, aujourdhui());
            BigDecimal deja = cumul(duAnnee(duContrat, annee));
            if (deja.add(total).compareTo(c.getMontantMaximum()) > 0) {
                throw new BusinessRuleException("Les bons de l'année contractuelle ne dépassent pas le maximum du marché à commandes ("
                        + FormulairesEnLigne.lisible(c.getMontantMaximum()) + " Ariary HT ; déjà commandé : " + FormulairesEnLigne.lisible(deja)
                        + ", loi n° 2016-055, art. 30).", "MAXIMUM_DEPASSE", null, Map.<String, Object>of("maximum", c.getMontantMaximum(), "dejaCommande", deja,
                                "montant", total));
            }
        }
        BonCommande b = new BonCommande();
        b.setIdContrat(idContrat);
        b.setNumero(bons.findFirstByIdContratOrderByNumeroDesc(idContrat).map(BonCommande::getNumero).orElse(0) + 1);
        b.setIdRemise(remise == null ? null : remise.getId());
        b.setIdOffre(idOffre);
        b.setEtat(BonCommande.EMIS);
        b.setObjet(objet);
        b.setLieuLivraison(nettoyer(r.lieuLivraison()) != null ? nettoyer(r.lieuLivraison()) : remise == null ? null : remise.getLieuLivraison());
        b.setDelaiLivraison(delai);
        b.setObservations(nettoyer(r.observations()));
        b.setMontantHt(total);
        // ⚠️ 2026-10-29 (« bons-reception », §B2, Q1) — la TVA, figée à l'émission.
        BigDecimal taux = tauxTva(c, idOffre);
        if (taux != null) {
            b.setTauxTva(taux);
            b.setMontantTva(total.multiply(taux).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            b.setMontantTtc(total.add(b.getMontantTva()));
        }
        b.setEmisLe(maintenant());
        b.setEmisPar(acteur());
        bons.save(b);
        for (BonCommandeLigne l : ls) {
            l.setIdBon(b.getId());
            lignes.save(l);
        }
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(document(c, b, remise, ls))) {
            if ("pdf".equals(f.extension())) {
                b.setPdf(f.contenu());
            } else if ("docx".equals(f.extension())) {
                b.setDocx(f.contenu());
            }
        }
        bons.save(b);
        Offre o = offres.findById(idOffre).orElse(null);
        notifierTitulaire(o, c, TypeNotification.BON_COMMANDE, "Bon de commande n° " + b.getNumero(), "Le bon de commande n° " + b.getNumero()
                + " « " + objet + " » (" + FormulairesEnLigne.lisible(total) + " Ariary HT, délai : " + delai + ") vous est notifié. Il est "
                + "disponible dans votre espace (Mes bons de commande) ; sa lecture vaut accusé de réception.");
        evaluation.tracerAttribution(c.getIdDmc(), "BON_COMMANDE", "Lot " + c.getLot() + " : bon de commande n° " + b.getNumero() + " émis à "
                + (o == null ? idOffre : o.getRaisonSociale()) + (remise == null ? "" : " (marché subséquent, remise n° " + remise.getNumero() + ")")
                + ", " + FormulairesEnLigne.lisible(total) + " Ariary HT");
        return dto(b, c);
    }

    /** Les articles du bon : ceux du besoin repris (désignation, unité, prix du bordereau quand il est lisible), ou libres. */
    private List<BonCommandeLigne> lignes(ContratVigueur c, String idOffre, boolean cadre, List<BonCommandeDto.LigneRequest> demandees) {
        if (demandees == null || demandees.isEmpty()) {
            throw new BadRequestException("Le bon de commande porte au moins un article.", "LIGNES_OBLIGATOIRES");
        }
        Map<Integer, BesoinEnLigneDto.Article> besoin = new HashMap<>();
        formulaires.lot(c.getIdDmc(), c.getLot()).ifPresent(l -> l.articles().forEach(a -> besoin.put(a.idArticle(), a)));
        Map<Integer, BigDecimal> bordereau = cadre ? Map.of() : seance.prixLus(c.getIdDmc(), idOffre);
        List<BonCommandeLigne> out = new ArrayList<>();
        int rang = 0;
        for (BonCommandeDto.LigneRequest d : demandees) {
            rang++;
            if (d == null) {
                throw new BadRequestException("L'article " + rang + " est vide.", "LIGNES_OBLIGATOIRES");
            }
            BesoinEnLigneDto.Article a = null;
            if (d.idArticle() != null) {
                a = besoin.get(d.idArticle());
                if (a == null) {
                    throw new BadRequestException("L'article " + d.idArticle() + " n'est pas au besoin du marché.", "ARTICLE_INCONNU",
                            Map.<String, Object>of("rang", rang));
                }
            }
            String designation = nettoyer(d.designation()) != null ? nettoyer(d.designation()) : a == null ? null : a.designation();
            if (designation == null) {
                throw new BadRequestException("L'article " + rang + " a une désignation.", "CHAMP_OBLIGATOIRE", Map.<String, Object>of("champ",
                        "designation", "rang", rang));
            }
            if (d.quantite() == null || d.quantite().signum() <= 0) {
                throw new BadRequestException("La quantité de l'article " + rang + " est un nombre positif.", "QUANTITE_INVALIDE",
                        Map.<String, Object>of("rang", rang));
            }
            BigDecimal pu = a == null ? null : bordereau.get(a.idArticle());
            boolean duBordereau = pu != null;
            if (duBordereau && d.prixUnitaire() != null && d.prixUnitaire().compareTo(pu) != 0) {
                throw new BadRequestException("Le prix unitaire de « " + designation + " » est celui du bordereau de l'offre : "
                        + FormulairesEnLigne.lisible(pu) + " Ariary HT (loi n° 2016-055, art. 30).", "PRIX_HORS_BORDEREAU",
                        Map.<String, Object>of("rang", rang, "prixBordereau", pu));
            }
            if (!duBordereau) {
                pu = d.prixUnitaire();
            }
            if (pu == null || pu.signum() <= 0) {
                throw new BadRequestException("Le prix unitaire HT de l'article " + rang + " est à donner (" + (cadre ? "celui de l'offre retenue au "
                        + "marché subséquent" : "le bordereau de l'offre n'est pas lisible") + ").", "PRIX_OBLIGATOIRE", Map.<String, Object>of("rang", rang));
            }
            BonCommandeLigne l = new BonCommandeLigne();
            l.setRang(rang);
            l.setIdArticle(a == null ? null : a.idArticle());
            l.setDesignation(designation);
            l.setUnite(nettoyer(d.unite()) != null ? nettoyer(d.unite()) : a == null ? null : a.unite());
            l.setQuantite(d.quantite());
            l.setPrixUnitaire(pu.setScale(2, RoundingMode.HALF_UP));
            l.setMontant(d.quantite().multiply(pu).setScale(2, RoundingMode.HALF_UP));
            l.setPrixDuBordereau(duBordereau);
            out.add(l);
        }
        return out;
    }

    /**
     * ⚠️ 2026-10-29 (demande front « bons-reception », §B1) — marché à commandes : le cumul des quantités d'un article du besoin, sur
     * les bons émis (annulés exclus) de l'année contractuelle, ne dépasse pas sa {@code quantiteMax} (409 {@code QUANTITE_MAX_DEPASSEE},
     * {@code details} = {@code rang} (la première ligne de l'article), {@code idArticle}, {@code quantiteMax}, {@code dejaCommande},
     * {@code demande}). Une ligne sans {@code idArticle} n'est pas bornée en quantité : le montant reste son seul contrôle.
     */
    private void controlerQuantites(ContratVigueur c, List<BonCommandeLigne> ls, List<BonCommande> duContrat) {
        Map<Integer, BesoinEnLigneDto.Article> besoin = besoin(c);
        Map<Integer, BigDecimal> demande = new java.util.LinkedHashMap<>();
        Map<Integer, Integer> premierRang = new HashMap<>();
        for (BonCommandeLigne l : ls) {
            if (l.getIdArticle() != null) {
                demande.merge(l.getIdArticle(), l.getQuantite(), BigDecimal::add);
                premierRang.putIfAbsent(l.getIdArticle(), l.getRang());
            }
        }
        if (demande.isEmpty()) {
            return;
        }
        Map<Integer, BigDecimal> deja = quantitesAnnee(duAnnee(duContrat, anneeContractuelle(c, aujourdhui())));
        for (Map.Entry<Integer, BigDecimal> e : demande.entrySet()) {
            BesoinEnLigneDto.Article a = besoin.get(e.getKey());
            BigDecimal max = a == null ? null : a.quantiteMax();
            BigDecimal commande = deja.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (max != null && commande.add(e.getValue()).compareTo(max) > 0) {
                throw new BusinessRuleException("« " + a.designation() + " » : la quantité commandée dans l'année contractuelle dépasserait sa "
                        + "quantité maximale (" + FormulairesEnLigne.lisible(max) + " ; déjà commandé : " + FormulairesEnLigne.lisible(commande)
                        + " ; demandé : " + FormulairesEnLigne.lisible(e.getValue()) + ").", "QUANTITE_MAX_DEPASSEE", null,
                        Map.<String, Object>of("rang", premierRang.get(e.getKey()), "idArticle", e.getKey(), "quantiteMax", max,
                                "dejaCommande", commande, "demande", e.getValue()));
            }
        }
    }

    /** Les articles du besoin du lot, par identifiant. */
    private Map<Integer, BesoinEnLigneDto.Article> besoin(ContratVigueur c) {
        Map<Integer, BesoinEnLigneDto.Article> besoin = new java.util.LinkedHashMap<>();
        formulaires.lot(c.getIdDmc(), c.getLot()).ifPresent(l -> l.articles().forEach(a -> besoin.put(a.idArticle(), a)));
        return besoin;
    }

    /** Les quantités commandées par article ({@code idArticle}) sur des bons, les annulés exclus. */
    private Map<Integer, BigDecimal> quantitesAnnee(List<BonCommande> bs) {
        Map<Integer, BigDecimal> q = new HashMap<>();
        for (BonCommande b : bs) {
            if (!BonCommande.EMIS.equals(b.getEtat())) {
                continue;
            }
            for (BonCommandeLigne l : lignes.findByIdBonOrderByRangAsc(b.getId())) {
                if (l.getIdArticle() != null) {
                    q.merge(l.getIdArticle(), l.getQuantite(), BigDecimal::add);
                }
            }
        }
        return q;
    }

    /**
     * ⚠️ 2026-10-29 (« bons-reception », §B2, Q1 : la proposition du front) — le taux de TVA du bon, en % : le rapport TTC / HT de
     * l'offre du titulaire à l'attribution (sa ligne de titulaire d'un contrat-cadre, sinon l'attributaire du lot), qui suit l'offre
     * retenue, exonérée comprise (0 %) ; à défaut le paramètre {@code FICHE_TAUX_TVA} ; nul sans l'un ni l'autre.
     */
    BigDecimal tauxTva(ContratVigueur c, String idOffre) {
        BigDecimal ht = null;
        BigDecimal ttc = null;
        for (cnm.prs.entity.AttributionTitulaire t : titulaires.findByIdDmcAndLotOrderByRangAscIdOffreAsc(c.getIdDmc(), c.getLot())) {
            if (t.getIdOffre().equals(idOffre)) {
                ht = t.getMontant();
                ttc = t.getMontantTtc();
            }
        }
        if (ht == null || ttc == null) {
            Attribution a = attributions.findById(new Attribution.Cle(c.getIdDmc(), c.getLot())).orElse(null);
            if (a != null && idOffre.equals(a.getIdOffreAttribuee())) {
                ht = a.getMontant();
                ttc = a.getMontantTtc();
            }
        }
        BigDecimal deLOffre = taux(ht, ttc);
        return deLOffre != null ? deLOffre : parametres.tauxTva();
    }

    /** {@code (TTC − HT) / HT × 100}, à deux décimales ; nul si l'un manque, si le HT n'est pas positif ou si le TTC lui est inférieur. */
    static BigDecimal taux(BigDecimal ht, BigDecimal ttc) {
        if (ht == null || ttc == null || ht.signum() <= 0 || ttc.compareTo(ht) < 0) {
            return null;
        }
        return ttc.subtract(ht).multiply(BigDecimal.valueOf(100)).divide(ht, 2, RoundingMode.HALF_UP);
    }

    /**
     * ⚠️ 2026-10-29 (« bons-reception », §B1) — {@code QUANTITE_MIN_NON_ATTEINTE} : marché à commandes, à trente jours au plus de la fin
     * de l'année contractuelle, un article du besoin n'a pas atteint sa {@code quantiteMin}. Signalée, jamais bloquante.
     */
    static boolean quantiteMinNonAtteinte(ContratVigueur c, List<BonCommandeDto.ArticleConsomme> articles, LocalDate aujourdhui) {
        if (!ContratVigueur.A_COMMANDE.equals(c.getTypeMarche()) || aujourdhui.isAfter(c.getDateFin())) {
            return false;
        }
        LocalDate[] annee = anneeContractuelle(c, aujourdhui);
        return ChronoUnit.DAYS.between(aujourdhui, annee[1]) <= ContratVigueurService.JOURS_ECHEANCE_PROCHE && articles.stream()
                .anyMatch(a -> a.quantiteMin() != null && a.commandeAnnee().compareTo(a.quantiteMin()) < 0);
    }

    /** La PRMP annule un bon (motif obligatoire, 400 {@code MOTIF_OBLIGATOIRE}) ; 409 {@code DEJA_ANNULE}. Le titulaire en est notifié. */
    @Transactional
    public BonCommandeDto annuler(Long id, BonCommandeDto.AnnulationRequest r) {
        BonCommande b = exiger(id);
        ContratVigueur c = contrats.findById(b.getIdContrat()).orElseThrow();
        exigerPrmp(c.getIdDmc());
        if (BonCommande.ANNULE.equals(b.getEtat())) {
            throw new BusinessRuleException("Le bon de commande est déjà annulé.", "DEJA_ANNULE");
        }
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("L'annulation d'un bon de commande se motive.", "MOTIF_OBLIGATOIRE");
        }
        b.setEtat(BonCommande.ANNULE);
        b.setAnnuleLe(maintenant());
        b.setAnnulePar(acteur());
        b.setMotifAnnulation(motif);
        bons.save(b);
        Offre o = offres.findById(b.getIdOffre()).orElse(null);
        notifierTitulaire(o, c, TypeNotification.BON_COMMANDE_ANNULE, "Bon de commande n° " + b.getNumero() + " annulé", "Le bon de commande n° "
                + b.getNumero() + " « " + b.getObjet() + " » est annulé : " + motif + ".");
        evaluation.tracerAttribution(c.getIdDmc(), "BON_COMMANDE_ANNULE", "Lot " + c.getLot() + " : bon de commande n° " + b.getNumero()
                + " annulé — " + motif);
        return dto(b, c);
    }

    // ------------------------------------------------------------------ lecture

    /** Les bons d'un contrat et sa consommation : PRMP et UGPM de la fiche, Commission et Administrateur. */
    @Transactional(readOnly = true)
    public BonCommandeDto.Consommation consommation(Long idContrat) {
        ContratVigueur c = contrats.findById(idContrat).orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + idContrat + "."));
        exigerLecteur(c.getIdDmc());
        List<BonCommande> duContrat = bons.findByIdContratOrderByNumeroAsc(idContrat);
        boolean cadre = ContratVigueur.CONTRAT_CADRE.equals(c.getTypeMarche());
        LocalDate[] annee = cadre ? null : anneeContractuelle(c, aujourdhui());
        BigDecimal cumulAnnee = cadre ? null : cumul(duAnnee(duContrat, annee));
        List<BonCommandeDto.Subsequent> subsequents = new ArrayList<>();
        if (cadre) {
            for (RemiseConcurrence x : remises.findByIdContratOrderByNumeroAsc(idContrat)) {
                if (!RemiseConcurrence.ATTRIBUEE.equals(x.getEtat())) {
                    continue;
                }
                BigDecimal commande = cumul(duContrat.stream().filter(b -> x.getId().equals(b.getIdRemise())).toList());
                Offre o = offres.findById(x.getIdOffreRetenue()).orElse(null);
                subsequents.add(new BonCommandeDto.Subsequent(x.getId(), x.getNumero(), x.getObjet(), x.getIdOffreRetenue(),
                        o == null ? null : o.getRaisonSociale(), x.getMontantRetenu(), commande,
                        x.getMontantRetenu() == null ? null : x.getMontantRetenu().subtract(commande)));
            }
        }
        // ⚠️ 2026-10-29 (« bons-reception », §B1) — marché à commandes : les quantités par article du besoin.
        List<BonCommandeDto.ArticleConsomme> articles = new ArrayList<>();
        if (!cadre) {
            Map<Integer, BigDecimal> q = quantitesAnnee(duAnnee(duContrat, annee));
            for (BesoinEnLigneDto.Article a : besoin(c).values()) {
                BigDecimal commande = q.getOrDefault(a.idArticle(), BigDecimal.ZERO);
                articles.add(new BonCommandeDto.ArticleConsomme(a.idArticle(), a.designation(), a.unite(), a.quantiteMin(), a.quantiteMax(),
                        commande, a.quantiteMax() == null ? null : a.quantiteMax().subtract(commande)));
            }
        }
        List<String> alertes = new ArrayList<>(alertes(c, duContrat, aujourdhui()));
        if (quantiteMinNonAtteinte(c, articles, aujourdhui())) {
            alertes.add(QUANTITE_MIN_NON_ATTEINTE);
        }
        BigDecimal cumulTtc = duContrat.stream().filter(b -> BonCommande.EMIS.equals(b.getEtat()) && b.getMontantTtc() != null)
                .map(BonCommande::getMontantTtc).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BonCommandeDto.Consommation(idContrat, c.getTypeMarche(), cumul(duContrat), annee == null ? null : annee[0],
                annee == null ? null : annee[1], cumulAnnee, cadre ? null : c.getMontantMinimum(), cadre ? null : c.getMontantMaximum(),
                cadre || c.getMontantMaximum() == null ? null : c.getMontantMaximum().subtract(cumulAnnee), c.getMontantEstime(), subsequents,
                alertes, duContrat.stream().map(b -> dto(b, c)).toList(), cumulTtc, articles);
    }

    @Transactional(readOnly = true)
    public BonCommandeDto lire(Long id) {
        BonCommande b = exiger(id);
        ContratVigueur c = contrats.findById(b.getIdContrat()).orElseThrow();
        exigerLecteur(c.getIdDmc());
        return dto(b, c);
    }

    /** Le bon (PDF, ou Word) : lecteurs. */
    @Transactional(readOnly = true)
    public byte[] document(Long id, boolean docx) {
        BonCommande b = exiger(id);
        exigerLecteur(contrats.findById(b.getIdContrat()).orElseThrow().getIdDmc());
        return docx ? b.getDocx() : b.getPdf();
    }

    // ------------------------------------------------------------------ espace du titulaire

    /** Les bons de ses offres, les plus récents d'abord. */
    @Transactional(readOnly = true)
    public List<BonCommandeDto> mesBons(String idCandidat) {
        List<BonCommandeDto> out = new ArrayList<>();
        for (Offre o : offres.findAll().stream().filter(o -> idCandidat.equals(o.getIdCandidat())).toList()) {
            for (BonCommande b : bons.findByIdOffreOrderByEmisLeDescIdDesc(o.getIdOffre())) {
                contrats.findById(b.getIdContrat()).ifPresent(c -> out.add(dto(b, c)));
            }
        }
        out.sort(Comparator.comparing(BonCommandeDto::emisLe).reversed());
        return out;
    }

    /**
     * Son bon (PDF, ou Word) ; 404 hors des siens. La première lecture d'un bon émis vaut accusé de réception : tracée, notifiée à la
     * PRMP ({@code BON_COMMANDE_ACCUSE}).
     */
    @Transactional
    public byte[] documentDuTitulaire(String idCandidat, Long id, boolean docx) {
        BonCommande b = exiger(id);
        if (!offres.findById(b.getIdOffre()).map(o -> idCandidat.equals(o.getIdCandidat())).orElse(false)) {
            throw new ResourceNotFoundException("Bon de commande introuvable : " + id + ".");
        }
        if (b.getAccuseLe() == null && BonCommande.EMIS.equals(b.getEtat())) {
            b.setAccuseLe(maintenant());
            bons.save(b);
            ContratVigueur c = contrats.findById(b.getIdContrat()).orElseThrow();
            Offre o = offres.findById(b.getIdOffre()).orElse(null);
            String qui = o == null ? b.getIdOffre() : o.getRaisonSociale();
            ceremonies.notifierPrmp(c.getIdDmc(), TypeNotification.BON_COMMANDE_ACCUSE, "Bon de commande n° " + b.getNumero() + " reçu",
                    qui + " a accusé réception du bon de commande n° " + b.getNumero() + " (lot " + c.getLot() + ", procédure " + c.getIdDmc()
                            + ") le " + b.getAccuseLe().format(HEURE) + ".");
            evaluation.tracerAttribution(c.getIdDmc(), "BON_COMMANDE_ACCUSE", "Lot " + c.getLot() + " : bon de commande n° " + b.getNumero()
                    + " reçu par " + qui + " le " + b.getAccuseLe().format(HEURE));
        }
        return docx ? b.getDocx() : b.getPdf();
    }

    // ------------------------------------------------------------------ cumuls et alertes

    /**
     * Les alertes de consommation d'un contrat (aussi servies dans {@code ContratVigueurDto.alertes}) : {@code MINIMUM_NON_ATTEINT}
     * (marché à commandes, minimum annuel non atteint à trente jours au plus de la fin de l'année contractuelle), {@code
     * MONTANT_ESTIME_DEPASSE} (contrat-cadre, cumul au-delà de la valeur maximale estimée).
     */
    static List<String> alertes(ContratVigueur c, List<BonCommande> duContrat, LocalDate aujourdhui) {
        List<String> al = new ArrayList<>();
        if (aujourdhui.isAfter(c.getDateFin())) {
            return al;
        }
        if (ContratVigueur.CONTRAT_CADRE.equals(c.getTypeMarche())) {
            if (c.getMontantEstime() != null && cumul(duContrat).compareTo(c.getMontantEstime()) > 0) {
                al.add(MONTANT_ESTIME_DEPASSE);
            }
        } else if (c.getMontantMinimum() != null) {
            LocalDate[] annee = anneeContractuelle(c, aujourdhui);
            if (ChronoUnit.DAYS.between(aujourdhui, annee[1]) <= ContratVigueurService.JOURS_ECHEANCE_PROCHE
                    && cumul(duAnnee(duContrat, annee)).compareTo(c.getMontantMinimum()) < 0) {
                al.add(MINIMUM_NON_ATTEINT);
            }
        }
        return al;
    }

    /** L'année contractuelle qui contient ce jour : [effet + k ans, effet + (k + 1) ans − 1 jour], bornée par la fin du contrat. */
    static LocalDate[] anneeContractuelle(ContratVigueur c, LocalDate jour) {
        long k = Math.max(0, ChronoUnit.YEARS.between(c.getDateEffet(), jour));
        LocalDate debut = c.getDateEffet().plusYears(k);
        LocalDate fin = debut.plusYears(1).minusDays(1);
        return new LocalDate[] { debut, fin.isAfter(c.getDateFin()) ? c.getDateFin() : fin };
    }

    private static List<BonCommande> duAnnee(List<BonCommande> duContrat, LocalDate[] annee) {
        return duContrat.stream().filter(b -> !b.getEmisLe().toLocalDate().isBefore(annee[0]) && !b.getEmisLe().toLocalDate().isAfter(annee[1]))
                .toList();
    }

    /** Le cumul HT des bons émis (les annulés exclus). */
    static BigDecimal cumul(List<BonCommande> bs) {
        return bs.stream().filter(b -> BonCommande.EMIS.equals(b.getEtat())).map(BonCommande::getMontantHt).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ vue et document

    private BonCommandeDto dto(BonCommande b, ContratVigueur c) {
        Offre o = offres.findById(b.getIdOffre()).orElse(null);
        Integer numeroRemise = b.getIdRemise() == null ? null : remises.findById(b.getIdRemise()).map(RemiseConcurrence::getNumero).orElse(null);
        List<BonCommandeDto.Ligne> ls = lignes.findByIdBonOrderByRangAsc(b.getId()).stream().map(l -> new BonCommandeDto.Ligne(l.getRang(),
                l.getIdArticle(), l.getDesignation(), l.getUnite(), l.getQuantite(), l.getPrixUnitaire(), l.getMontant(), l.isPrixDuBordereau())).toList();
        return new BonCommandeDto(b.getId(), b.getIdContrat(), c.getIdDmc(), c.getLot(), c.getTypeMarche(), b.getNumero(), b.getIdRemise(),
                numeroRemise, b.getIdOffre(), o == null ? null : o.getNumero(), o == null ? null : o.getRaisonSociale(), b.getEtat(), b.getObjet(),
                b.getLieuLivraison(), b.getDelaiLivraison(), b.getObservations(), ls, b.getMontantHt(), b.getEmisLe(), b.getEmisPar(), b.getAccuseLe(),
                b.getAnnuleLe(), b.getAnnulePar(), b.getMotifAnnulation(), b.getTauxTva(), b.getMontantTva(), b.getMontantTtc());
    }

    private DocumentLibre document(ContratVigueur c, BonCommande b, RemiseConcurrence remise, List<BonCommandeLigne> ls) {
        DossierMec dmc = dmcRepository.findById(c.getIdDmc()).orElse(null);
        Map<String, String> plan = dmc == null || dmc.getIdDetail() == null ? Map.of() : valeursPpm.lire(dmc.getIdDetail()).valeurs();
        Attribution a = attributions.findById(new Attribution.Cle(c.getIdDmc(), c.getLot())).orElse(null);
        String numeroContrat = a != null && a.getReferenceEnregistrement() != null ? a.getReferenceEnregistrement()
                : "procédure " + c.getIdDmc() + ", lot " + c.getLot();
        boolean cadre = ContratVigueur.CONTRAT_CADRE.equals(c.getTypeMarche());
        Offre o = offres.findById(b.getIdOffre()).orElse(null);
        String autorite = Objects.toString(plan.get("ENTITE"), "L'Autorité contractante");
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, plan.get("MINISTERE") == null ? autorite : plan.get("MINISTERE") + " — " + autorite));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "La Personne responsable des marchés publics"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "BON DE COMMANDE N° " + b.getNumero()));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, (cadre ? "Contrat-cadre n° " : "Marché à commandes n° ") + numeroContrat
                + (a != null && a.getDateSignature() != null ? " du " + a.getDateSignature().format(JOUR) : "")));
        if (remise != null) {
            para(el, "Marché subséquent « " + remise.getObjet() + " », attribué à l'issue de la remise en concurrence n° " + remise.getNumero()
                    + (remise.getDecideeLe() == null ? "" : " du " + remise.getDecideeLe().format(JOUR)) + " (décret n° 2019-1064, art. 29 et 33).");
        }
        para(el, "Titulaire : " + (o == null ? b.getIdOffre() : o.getRaisonSociale() + (o.getNif() == null ? "" : ", NIF " + o.getNif())) + ".");
        para(el, "Objet de la commande : " + b.getObjet() + ".");
        List<List<List<String>>> t = new ArrayList<>();
        t.add(List.of(List.of("N°"), List.of("Désignation"), List.of("Unité"), List.of("Quantité"), List.of("Prix unitaire HT"), List.of("Montant HT")));
        for (BonCommandeLigne l : ls) {
            t.add(List.of(List.of(String.valueOf(l.getRang())), List.of(l.getDesignation()), List.of(Objects.toString(l.getUnite(), "—")),
                    List.of(FormulairesEnLigne.lisible(l.getQuantite())), List.of(FormulairesEnLigne.lisible(l.getPrixUnitaire())),
                    List.of(FormulairesEnLigne.lisible(l.getMontant()))));
        }
        t.add(List.of(List.of(""), List.of("Total hors taxes"), List.of(""), List.of(""), List.of(""), List.of(FormulairesEnLigne.lisible(b.getMontantHt()))));
        // ⚠️ 2026-10-29 (« bons-reception », §B2) — la TVA et le total TTC.
        if (b.getMontantTtc() != null) {
            t.add(List.of(List.of(""), List.of("TVA (" + b.getTauxTva().stripTrailingZeros().toPlainString() + " %)"), List.of(""), List.of(""),
                    List.of(""), List.of(FormulairesEnLigne.lisible(b.getMontantTva()))));
            t.add(List.of(List.of(""), List.of("Total toutes taxes comprises"), List.of(""), List.of(""), List.of(""),
                    List.of(FormulairesEnLigne.lisible(b.getMontantTtc()))));
        }
        el.add(new DocumentLibre.Tableau(6, t));
        para(el, "Arrêté le présent bon de commande à la somme de " + FormulairesEnLigne.lisible(b.getMontantHt()) + " Ariary hors taxes"
                + (b.getMontantTtc() == null ? "" : ", soit " + FormulairesEnLigne.lisible(b.getMontantTtc()) + " Ariary toutes taxes comprises")
                + ", aux prix " + (cadre ? "de l'offre retenue au marché subséquent." : "du bordereau de l'offre du titulaire (loi n° 2016-055, art. 30)."));
        para(el, "Délai de livraison : " + b.getDelaiLivraison() + ", à compter du lendemain de la notification du présent bon de commande.");
        if (b.getLieuLivraison() != null) {
            para(el, "Lieu de livraison : " + b.getLieuLivraison() + ".");
        }
        if (b.getObservations() != null) {
            para(el, "Observations : " + b.getObservations());
        }
        para(el, "Les conditions d'exécution, de réception et de règlement sont celles du " + (cadre ? "contrat-cadre et de son CCAP." : "marché et de son CCAP."));
        para(el, "Fait le " + b.getEmisLe().format(JOUR) + ", notifié sur la plateforme.");
        para(el, "La Personne Responsable des Marchés Publics" + (plan.get("PRMP") == null ? "," : ", " + plan.get("PRMP")));
        return new DocumentLibre("BON_COMMANDE", null, el, "Procédure " + c.getIdDmc() + ", lot " + c.getLot() + " — bon de commande n° " + b.getNumero());
    }

    // ------------------------------------------------------------------ droits et outils

    private void exigerPrmp(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Le bon de commande s'émet par la PRMP : il l'engage.");
        }
        fiches.controlerLecture(idDmc);
    }

    private void exigerLecteur(Long idDmc) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == null || EXTERNES.contains(p)) {
            throw new AccessDeniedException("Les bons de commande se lisent dans l'espace de l'autorité contractante et de la Commission.");
        }
        if (p == ProfilUtilisateur.PRMP || p == ProfilUtilisateur.UGPM) {
            fiches.controlerLecture(idDmc);
        }
    }

    private void notifierTitulaire(Offre o, ContratVigueur c, TypeNotification type, String titre, String corps) {
        if (o == null) {
            return;
        }
        CompteCandidat cc = candidats.findById(o.getIdCandidat()).orElse(null);
        notifications.emettreCandidat(type, o.getIdCandidat(), cc == null ? null : cc.getEmail(), c.getIdDmc().intValue(), TypeObjet.PROCEDURE, titre, corps);
    }

    private BonCommande exiger(Long id) {
        return bons.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bon de commande introuvable : " + id + "."));
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
