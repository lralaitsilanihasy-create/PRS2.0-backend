package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.HistoriqueDossierDto;
import cnm.prs.entity.ActeGestion;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.PvExamen;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ActeGestionRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.SuspensionDossierRepository;
import cnm.prs.repository.TransmissionSigmpRepository;
import cnm.prs.repository.VersionDossierRepository;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ 2026-10-31 (demande front « filiation-dossier », §B2, premier temps : le circuit de la Commission ; demande du pilote) —
 * l'<strong>historique</strong> d'un dossier : ses objets rattachés, triés par date, chacun avec son identifiant et sa référence.
 * Réception, dispatch, examen, PV (projet et définitif), lettres de renvoi, transmissions SIGMP, suspensions, demandes de retrait,
 * versions archivées (rectifications), actes de gestion du marché. Une requête par nature, pour un dossier.
 * <p>
 * Mêmes règles de visibilité que les écrans : 403 hors périmètre ; pour la PRMP et son UGPM, ni dispatch ni examen (organisation
 * interne de la Commission), le PV seulement signé (règle du 02/08), la lettre seulement signée, et aucun matricule de contrôleur
 * (vues internes, audit du 14/09).
 */
@Service
@Transactional(readOnly = true)
public class HistoriqueDossierService {

    /** La lettre que la PRMP reçoit : signée ({@code StatutLettreRenvoi.SIGNE}). */
    private static final Set<String> LETTRE_EMISE = Set.of(cnm.prs.enums.StatutLettreRenvoi.SIGNE.name());

    private final DossierRepository dossiers;
    private final DossierService dossierService;
    private final ReceptionRepository receptions;
    private final DispatchRepository dispatchs;
    private final ExamenRepository examens;
    private final PvExamenRepository pvs;
    private final LettreRenvoiRepository lettres;
    private final TransmissionSigmpRepository transmissions;
    private final SuspensionDossierRepository suspensions;
    private final DemandeRetraitRepository retraits;
    private final VersionDossierRepository versions;
    private final ActeGestionRepository actes;
    private final org.springframework.beans.factory.ObjectProvider<PublicationAgpmService> publicationsAgpm;
    private final cnm.prs.repository.PublicationAvisRepository publicationsAvis;
    private final cnm.prs.repository.SeanceRepository seances;
    private final cnm.prs.repository.EvaluationRepository evaluations;
    private final cnm.prs.repository.AttributionRepository attributions;
    private final cnm.prs.repository.ContratVigueurRepository contrats;
    private final cnm.prs.repository.BonCommandeRepository bons;
    private final cnm.prs.repository.ReceptionCommandeRepository receptionsCommande;

    public HistoriqueDossierService(DossierRepository dossiers, DossierService dossierService, ReceptionRepository receptions,
            DispatchRepository dispatchs, ExamenRepository examens, PvExamenRepository pvs, LettreRenvoiRepository lettres,
            TransmissionSigmpRepository transmissions, SuspensionDossierRepository suspensions, DemandeRetraitRepository retraits,
            VersionDossierRepository versions, ActeGestionRepository actes,
            org.springframework.beans.factory.ObjectProvider<PublicationAgpmService> publicationsAgpm,
            cnm.prs.repository.PublicationAvisRepository publicationsAvis, cnm.prs.repository.SeanceRepository seances,
            cnm.prs.repository.EvaluationRepository evaluations, cnm.prs.repository.AttributionRepository attributions,
            cnm.prs.repository.ContratVigueurRepository contrats, cnm.prs.repository.BonCommandeRepository bons,
            cnm.prs.repository.ReceptionCommandeRepository receptionsCommande) {
        this.publicationsAgpm = publicationsAgpm;
        this.publicationsAvis = publicationsAvis;
        this.seances = seances;
        this.evaluations = evaluations;
        this.attributions = attributions;
        this.contrats = contrats;
        this.bons = bons;
        this.receptionsCommande = receptionsCommande;
        this.dossiers = dossiers;
        this.dossierService = dossierService;
        this.receptions = receptions;
        this.dispatchs = dispatchs;
        this.examens = examens;
        this.pvs = pvs;
        this.lettres = lettres;
        this.transmissions = transmissions;
        this.suspensions = suspensions;
        this.retraits = retraits;
        this.versions = versions;
        this.actes = actes;
    }

    /** {@code GET /api/dossiers/{idDossier}/historique} : 404 inconnu, 403 hors périmètre ; triés par date (sans date en dernier). */
    public List<HistoriqueDossierDto> historique(Integer idDossier) {
        dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        dossierService.controlerVisibilite(idDossier);
        boolean prmp = Visibilite.estPrmp();
        List<HistoriqueDossierDto> out = new ArrayList<>();
        receptions.findByIdDossier(idDossier).forEach(r -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.RECEPTION,
                r.getDateReception(), r.getIdReception(), r.getReference(), Boolean.TRUE.equals(r.getComplet()) ? "COMPLET" : "INCOMPLET",
                null, prmp ? null : r.getImCtrlRecept(), "Passage " + (r.getNumPassage() == null ? "" : r.getNumPassage() + " ")
                        + Objects.toString(r.getTypePassage(), "").trim(), null, r.getNumPassage())));
        if (!prmp) {
            for (Dispatch d : dispatchs.findParDossier(idDossier)) {
                out.add(new HistoriqueDossierDto(HistoriqueDossierDto.DISPATCH, d.getDateDispatch(), d.getIdDispatch(), null, null, null,
                        d.getImCtrlDispatch(), d.getImCtrlMembre() == null ? null : "attribué à " + d.getImCtrlMembre(), null, null));
            }
            examens.findParDossierRecents(idDossier).forEach(e -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.EXAMEN,
                    debut(e.getDateExamen()), e.getIdExamen(), null, null, null, e.getImCtrlMembre(), null, null, null)));
        }
        for (PvExamen pv : pvs.findTousParDossier(idDossier)) {
            boolean signe = "SIGNE".equals(pv.getStatutPv());
            if (prmp && !signe) {
                continue;
            }
            LocalDate date = pv.getDatePv() != null ? pv.getDatePv() : pv.getDateSignaturePresident() != null ? pv.getDateSignaturePresident()
                    : pv.getDateSoumissionInitiale();
            out.add(new HistoriqueDossierDto(HistoriqueDossierDto.PV, debut(date), pv.getIdPv(),
                    pv.getRefePv() != null ? pv.getRefePv() : pv.getReferencePv(), signe ? "DEFINITIF" : pv.getStatutPv(), pv.getIdAvis(),
                    prmp ? null : pv.getImCtrlMembre(), pv.getNbNavettes() == null || pv.getNbNavettes() == 0 ? null
                            : pv.getNbNavettes() + " navette(s)", null, null));
        }
        for (LettreRenvoi l : lettres.findByIdDossierOrderByIdLettreAsc(idDossier)) {
            if (prmp && (l.getStatut() == null || !LETTRE_EMISE.contains(l.getStatut()))) {
                continue;
            }
            out.add(new HistoriqueDossierDto(HistoriqueDossierDto.LETTRE_RENVOI, debut(l.getDateLettre()), l.getIdLettre(), l.getRefLettre(),
                    l.getStatut(), null, prmp ? null : l.getImSignataire(), l.getDateArchivage() == null ? null : "archivée le " + l.getDateArchivage(),
                    null, null));
        }
        transmissions.findByIdDossier(idDossier).forEach(t -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.TRANSMISSION_SIGMP,
                t.getDateTransmission(), t.getIdTransmission(), null, t.getStatutEnvoi(), null, prmp ? null : t.getImVerificateur(), t.getSens(),
                null, null)));
        suspensions.findByIdDossierOrderByDebutAsc(idDossier).forEach(s -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.SUSPENSION,
                s.getDebut(), s.getIdSuspension(), null, s.getStatut(), null, null, s.getFin() == null ? "en cours" : "levée le " + s.getFin(),
                null, null)));
        retraits.findByIdDossierOrderByDateDemandeAsc(idDossier).forEach(r -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.RETRAIT,
                r.getDateDemande(), r.getIdDemandeRetrait(), null, r.getStatut(), null, null, r.getMotifRetrait(), null, null)));
        versions.findByIdDossierOrderByNumeroAsc(idDossier).forEach(v -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.VERSION_ARCHIVEE,
                v.getDateVersion(), v.getIdVersion(), v.getReference(), null, null, null, v.getOrigine(), null, v.getNumero())));
        List<ActeGestion> as = actes.findByIdDossierMarcheOrderByIdActeAsc(idDossier);
        if (!as.isEmpty()) {
            Map<Integer, Dossier> ds = new HashMap<>();
            dossiers.findAllById(as.stream().map(ActeGestion::getIdDossier).toList()).forEach(d -> ds.put(d.getIdDossier(), d));
            for (ActeGestion a : as) {
                Dossier d = ds.get(a.getIdDossier());
                out.add(new HistoriqueDossierDto(HistoriqueDossierDto.ACTE_GESTION, a.getCreeLe(), a.getIdDossier(),
                        d == null ? null : d.getRefeDossier(), d == null ? null : d.getStatut(), null, null, a.getSousType(), null, null));
            }
        }
        procedure(dossiers.findById(idDossier).orElseThrow(), out);
        out.sort(Comparator.comparing(HistoriqueDossierDto::date, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    // ------------------------------------------------------------------ ⚠️ 2026-10-31 (§B2, second temps) — la procédure

    /**
     * Ce qui se rattache au dossier par sa procédure : un plan, sa publication de l'AGPM ; un dossier de mise en concurrence (né d'une
     * fiche), la procédure entière — publications, séance, évaluation, attribution de chaque lot, contrats, bons et réceptions ; un
     * dossier de marché, la même chose restreinte à son lot.
     */
    private void procedure(Dossier d, List<HistoriqueDossierDto> out) {
        if (PublicationAgpmService.FAMILLE_DDP.equals(d.getIdTypeDossier())) {
            publicationsAgpm.getObject().enVigueurDuPlan(d.getIdDossier()).ifPresent(p -> out.add(agpm(p)));
            return;
        }
        Long idDmc = d.getIdDmc();
        Integer lot = null;
        if (idDmc == null) {
            cnm.prs.entity.Attribution a = attributions.findFirstByIdDossier(d.getIdDossier()).orElse(null);
            if (a == null) {
                return;
            }
            idDmc = a.getIdDmc();
            lot = a.getLot();
        }
        Long procedure = idDmc;
        Integer leLot = lot;
        publicationsAgpm.getObject().enVigueurPourDmc(procedure).ifPresent(p -> out.add(agpm(p)));
        publicationsAvis.findFirstByIdDmcAndRemplaceeLeIsNull(procedure).ifPresent(p -> out.add(new HistoriqueDossierDto(
                HistoriqueDossierDto.PUBLICATION_AVIS, debut(p.getDateParution()), id(p.getIdPublication()),
                p.getJournal() == null ? null : p.getJournal() + (p.getNumero() == null ? "" : " n° " + p.getNumero()), p.getNature(), null,
                null, null, null, null)));
        seances.findById(procedure).ifPresent(s -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.SEANCE,
                s.getOuverteLe(), null, null, s.getEtat(), null, null, s.getCloseLe() == null ? null : "close le " + s.getCloseLe(), null, null)));
        evaluations.findById(procedure).ifPresent(e -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.EVALUATION,
                e.getOuverteLe(), null, null, e.getEtat(), null, null, null, null, null)));
        for (cnm.prs.entity.Attribution a : attributions.findByIdDmcOrderByLotAsc(procedure)) {
            if (leLot != null && !leLot.equals(a.getLot())) {
                continue;
            }
            LocalDateTime date = a.getNotifieLe() != null ? a.getNotifieLe() : a.getSigneLe() != null ? a.getSigneLe() : a.getAttribueLe();
            out.add(new HistoriqueDossierDto(HistoriqueDossierDto.ATTRIBUTION, date, a.getIdDossier(), a.getReferenceEnregistrement(),
                    a.getEtat(), null, null, null, a.getLot(), null));
        }
        List<cnm.prs.entity.ContratVigueur> cs = contrats.findByIdDmcOrderByLotAsc(procedure).stream()
                .filter(c -> leLot == null || leLot.equals(c.getLot())).toList();
        cs.forEach(c -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.CONTRAT, debut(c.getDateEffet()), id(c.getId()), null,
                c.getTypeMarche(), null, null, "jusqu'au " + c.getDateFin(), c.getLot(), null)));
        if (cs.isEmpty()) {
            return;
        }
        List<cnm.prs.entity.BonCommande> bs = bons.findByIdContratInOrderByIdContratAscNumeroAsc(cs.stream().map(cnm.prs.entity.ContratVigueur::getId)
                .toList());
        bs.forEach(b -> out.add(new HistoriqueDossierDto(HistoriqueDossierDto.BON_COMMANDE, b.getEmisLe(), id(b.getId()), null, b.getEtat(),
                null, null, b.getObjet(), null, b.getNumero())));
        if (!bs.isEmpty()) {
            receptionsCommande.findByIdBonInOrderByIdAsc(bs.stream().map(cnm.prs.entity.BonCommande::getId).toList()).forEach(r -> out.add(
                    new HistoriqueDossierDto(HistoriqueDossierDto.RECEPTION_COMMANDE, r.getSigneLe() != null ? r.getSigneLe()
                            : debut(r.getDateReception() != null ? r.getDateReception() : r.getDateLivraison()), id(r.getId()), null, r.getEtat(),
                            r.getDecision(), null, r.getType(), null, null)));
        }
    }

    private static HistoriqueDossierDto agpm(cnm.prs.entity.PublicationAgpm p) {
        return new HistoriqueDossierDto(HistoriqueDossierDto.PUBLICATION_AGPM, debut(p.getDateParution()), id(p.getIdPublication()),
                p.getReferenceAgpm(), null, null, null, p.getJournal() + (p.getNumero() == null ? "" : " n° " + p.getNumero()), null, null);
    }

    private static Integer id(Long id) {
        return id == null ? null : id.intValue();
    }

    private static LocalDateTime debut(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }
}
