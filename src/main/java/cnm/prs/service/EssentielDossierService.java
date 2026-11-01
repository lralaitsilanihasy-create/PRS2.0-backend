package cnm.prs.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EssentielDossierDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.BonCommande;
import cnm.prs.entity.ContratVigueur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Offre;
import cnm.prs.entity.PvExamen;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ActeGestionRepository;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.AvisRepository;
import cnm.prs.repository.BonCommandeRepository;
import cnm.prs.repository.ContratVigueurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ 2026-11-01 (demande front « essentiel-dossier », §B1 ; variante A de la page du dossier retenue par le pilote) — « L'essentiel »
 * d'un dossier, pour la bande sous l'en-tête :
 * <ul>
 *   <li>{@code avis} : celui du dernier PV du dossier (le PV de la Commission sur le DAO pour un dossier de mise en concurrence, celui
 *       du marché pour un dossier de marché) ; pour la PRMP et l'UGPM, seulement d'après un PV signé ;</li>
 *   <li>{@code attributions} : le lot d'un dossier de marché, ou chaque lot attribué de la procédure d'un dossier de mise en
 *       concurrence — l'offre attribuée, à défaut le titulaire de rang 1 (contrat-cadre multi-attributaire) ;</li>
 *   <li>{@code contrat} : le contrat en vigueur du marché (le premier lot pour un dossier de mise en concurrence) ; signé le jour de la
 *       signature du marché, à défaut sa prise d'effet ;</li>
 *   <li>{@code suites} : bons de commande (ou marchés subséquents) des contrats, le dernier, et les actes de gestion du marché.</li>
 * </ul>
 * Un dossier de planification n'a que l'avis. Aucun nom de contrôleur n'est servi. Mêmes lecteurs que {@code GET /api/dossiers/{id}}.
 */
@Service
@Transactional(readOnly = true)
public class EssentielDossierService {

    private final DossierRepository dossiers;
    private final DossierService dossierService;
    private final PvExamenRepository pvs;
    private final AvisRepository avis;
    private final AttributionRepository attributions;
    private final OffreRepository offres;
    private final cnm.prs.repository.AttributionTitulaireRepository titulaires;
    private final ContratVigueurRepository contrats;
    private final BonCommandeRepository bons;
    private final ActeGestionRepository actes;

    public EssentielDossierService(DossierRepository dossiers, DossierService dossierService, PvExamenRepository pvs, AvisRepository avis,
            AttributionRepository attributions, OffreRepository offres, cnm.prs.repository.AttributionTitulaireRepository titulaires,
            ContratVigueurRepository contrats, BonCommandeRepository bons, ActeGestionRepository actes) {
        this.dossiers = dossiers;
        this.dossierService = dossierService;
        this.pvs = pvs;
        this.avis = avis;
        this.attributions = attributions;
        this.offres = offres;
        this.titulaires = titulaires;
        this.contrats = contrats;
        this.bons = bons;
        this.actes = actes;
    }

    /** {@code GET /api/dossiers/{idDossier}/essentiel} : 404 inconnu ; 403 hors périmètre. */
    public EssentielDossierDto essentiel(Integer idDossier) {
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        dossierService.controlerVisibilite(idDossier);
        EssentielDossierDto.Avis av = avis(idDossier);
        if (PublicationAgpmService.FAMILLE_DDP.equals(d.getIdTypeDossier())) {
            return new EssentielDossierDto(av, null, null, null);
        }
        // La procédure et le lot : un dossier de marché porte son lot ; un dossier de mise en concurrence, tous les lots.
        Attribution duMarche = d.getIdDmc() == null ? attributions.findFirstByIdDossier(idDossier).orElse(null) : null;
        Long idDmc = d.getIdDmc() != null ? d.getIdDmc() : duMarche == null ? null : duMarche.getIdDmc();
        if (idDmc == null) {
            return new EssentielDossierDto(av, null, null, null);
        }
        List<Attribution> lots = duMarche != null ? List.of(duMarche) : attributions.findByIdDmcOrderByLotAsc(idDmc);
        List<EssentielDossierDto.Attribution> attribues = new ArrayList<>();
        for (Attribution a : lots) {
            if (a.getAttribueLe() == null || a.getRetireLe() != null) {
                continue;
            }
            attribues.add(attribution(a));
        }
        Integer lot = duMarche == null ? null : duMarche.getLot();
        List<ContratVigueur> cs = contrats.findByIdDmcOrderByLotAsc(idDmc).stream().filter(c -> lot == null || lot.equals(c.getLot())).toList();
        EssentielDossierDto.Contrat contrat = null;
        if (!cs.isEmpty()) {
            ContratVigueur c = cs.get(0);
            Attribution a = lots.stream().filter(x -> c.getLot().equals(x.getLot())).findFirst().orElse(null);
            java.time.LocalDate signe = a != null && a.getSigneLe() != null ? a.getSigneLe().toLocalDate() : c.getDateEffet();
            contrat = new EssentielDossierDto.Contrat(c.getTypeMarche(), libelleForme(c.getTypeMarche()), signe, c.getDateFin());
        }
        List<BonCommande> bs = cs.isEmpty() ? List.of()
                : bons.findByIdContratInOrderByIdContratAscNumeroAsc(cs.stream().map(ContratVigueur::getId).toList());
        List<Integer> marches = lots.stream().map(Attribution::getIdDossier).filter(java.util.Objects::nonNull).distinct().toList();
        int nbActes = marches.isEmpty() ? 0 : actes.findByIdDossierMarcheInOrderByIdActeAsc(marches).size();
        EssentielDossierDto.Suites suites = null;
        if (!bs.isEmpty() || nbActes > 0) {
            BonCommande dernier = bs.stream().max(Comparator.comparing(BonCommande::getEmisLe, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(BonCommande::getNumero)).orElse(null);
            suites = new EssentielDossierDto.Suites(bs.size(), dernier == null ? null
                    : new EssentielDossierDto.DernierBon(dernier.getNumero(), dernier.getEmisLe()), nbActes);
        }
        return new EssentielDossierDto(av, attribues.isEmpty() ? null : attribues, contrat, suites);
    }

    /** L'avis du dernier PV du dossier ; pour la PRMP et l'UGPM, du dernier PV signé. */
    private EssentielDossierDto.Avis avis(Integer idDossier) {
        List<PvExamen> tous = pvs.findTousParDossier(idDossier);
        boolean prmp = Visibilite.estPrmp();
        PvExamen pv = tous.stream().filter(p -> !prmp || "SIGNE".equals(p.getStatutPv())).filter(p -> p.getIdAvis() != null)
                .max(Comparator.comparing(PvExamen::getIdPv)).orElse(null);
        if (pv == null) {
            return null;
        }
        String libelle = avis.findById(pv.getIdAvis()).map(cnm.prs.entity.Avis::getLibelleAvis).orElse(pv.getIdAvis());
        return new EssentielDossierDto.Avis(pv.getIdAvis(), libelle, pv.getRefePv() != null ? pv.getRefePv() : pv.getReferencePv(),
                "SIGNE".equals(pv.getStatutPv()), pv.getIdPv());
    }

    /** L'attributaire d'un lot : l'offre attribuée, à défaut le titulaire de rang 1. */
    private EssentielDossierDto.Attribution attribution(Attribution a) {
        String idOffre = a.getIdOffreAttribuee();
        java.math.BigDecimal montant = a.getMontant();
        if (idOffre == null) {
            cnm.prs.entity.AttributionTitulaire t = titulaires.findByIdDmcAndLotOrderByRangAscIdOffreAsc(a.getIdDmc(), a.getLot()).stream()
                    .filter(x -> x.getRetireLe() == null).findFirst().orElse(null);
            if (t != null) {
                idOffre = t.getIdOffre();
                montant = montant != null ? montant : t.getMontant();
            }
        }
        Offre o = idOffre == null ? null : offres.findById(idOffre).orElse(null);
        return new EssentielDossierDto.Attribution(a.getLot(), o == null ? null : o.getRaisonSociale(), o == null ? null : o.getNumero(),
                montant, a.getIdDossier());
    }

    /** Le libellé de la forme du marché ({@code FormeMarche}), à défaut le code. */
    static String libelleForme(String forme) {
        if (forme == null) {
            return null;
        }
        for (cnm.prs.enums.FormeMarche f : cnm.prs.enums.FormeMarche.values()) {
            if (f.name().equals(forme)) {
                return f.libelle();
            }
        }
        return forme;
    }
}
