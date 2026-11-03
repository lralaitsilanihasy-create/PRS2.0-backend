package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.JalonReelDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Marche;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EvaluationRapportRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.SeanceRepository;

/**
 * ⚠️ 2026-11-03 (demande front « jalons-reels », B1) — les <strong>dates réelles des jalons</strong> d'un plan de passation, calculées à
 * la lecture à partir des faits que l'application enregistre (aucun stockage ; {@code t_echeance} reste aux alertes, Q1). Le
 * rapprochement processus CAPM → fait :
 * <ul>
 *   <li>102 avis général : la publication de l'AGPM déposée sur le plan (sa date de parution) ;</li>
 *   <li>106, 107, 108, 109 : le dossier de mise en concurrence de la ligne (né de la fiche, ou créé hors fiche et rattaché, V163) — sa
 *       première réception ; la date de son premier PV (avis) ; sa soumission à la signature, à défaut la date du PV ; sa signature ;</li>
 *   <li>111 lancement : la première impression de l'avis spécifique ou des lettres d'invitation ;</li>
 *   <li>112 remise des offres : la date limite de la dernière version validée de la fiche, une fois passée ;</li>
 *   <li>113 ouverture des plis : la séance ; 114 évaluation : la signature du rapport d'évaluation ; 115 : la signature du PV
 *       d'ouverture ;</li>
 *   <li>117 à 120 : le dossier de marché d'un lot (ou le marché de gré à gré rattaché) — réception, avis, soumission, signature du PV ;</li>
 *   <li>123 à 129 : l'attribution du lot — décision, information des candidats non retenus, signature, visa du contrôleur financier,
 *       approbation, enregistrement, notification ;</li>
 *   <li>les anciens processus 1 (LANCEMENT), 3 (OUVERTURE) et 4 (ATTRIBUTION) comme 111, 113 et 123.</li>
 * </ul>
 * Un processus sans fait (besoins, choix de la procédure, établissement du DAO, avis du bailleur, remises en forme, exécution) n'a pas de
 * date réelle (Q2). Une ligne à plusieurs lots prend la <strong>première</strong> date atteinte, le lot dans la source. Les faits des
 * copies de la ligne dans les autres versions du plan comptent (même ligne d'origine). Périmètre de la filiation ; une requête par
 * nature de fait pour tout le plan.
 */
@Service
@Transactional(readOnly = true)
public class JalonsReelsService {

    private static final java.time.format.DateTimeFormatter JOUR = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FAMILLE_DDP = "DDP";
    private static final String FAMILLE_DMC = "DMC";

    private final DossierRepository dossiers;
    private final DossierService dossierService;
    private final MarcheRepository marches;
    private final DossierMecRepository dmcs;
    private final ReceptionRepository receptions;
    private final PvExamenRepository pvs;
    private final SeanceRepository seances;
    private final EvaluationRapportRepository rapports;
    private final AttributionRepository attributions;
    private final FicheMarcheRepository fiches;
    private final FicheMarcheValeurRepository valeurs;
    private final DocumentFicheMarcheRepository documents;
    private final ObjectProvider<PublicationAgpmService> agpm;

    public JalonsReelsService(DossierRepository dossiers, DossierService dossierService, MarcheRepository marches, DossierMecRepository dmcs,
            ReceptionRepository receptions, PvExamenRepository pvs, SeanceRepository seances, EvaluationRapportRepository rapports,
            AttributionRepository attributions, FicheMarcheRepository fiches, FicheMarcheValeurRepository valeurs,
            DocumentFicheMarcheRepository documents, ObjectProvider<PublicationAgpmService> agpm) {
        this.dossiers = dossiers;
        this.dossierService = dossierService;
        this.marches = marches;
        this.dmcs = dmcs;
        this.receptions = receptions;
        this.pvs = pvs;
        this.seances = seances;
        this.rapports = rapports;
        this.attributions = attributions;
        this.fiches = fiches;
        this.valeurs = valeurs;
        this.documents = documents;
        this.agpm = agpm;
    }

    /** Une date candidate pour un jalon d'une ligne. */
    private record Fait(Integer idDetail, int idCapm, LocalDate date, String source) {
    }

    /** Les dates réelles connues du plan ; 404 dossier inconnu ; 403 hors périmètre ; 409 {@code PAS_UN_PLAN}. */
    public List<JalonReelDto> jalons(Integer idPlan) {
        Dossier plan = dossiers.findById(idPlan).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idPlan));
        dossierService.controlerVisibilite(idPlan);
        if (!FAMILLE_DDP.equals(plan.getIdTypeDossier())) {
            throw new BusinessRuleException("Les jalons se lisent sur un plan de passation.", "PAS_UN_PLAN");
        }
        List<Marche> lignes = marches.findByIdDossierIn(List.of(idPlan));
        if (lignes.isEmpty()) {
            return List.of();
        }
        // Les copies de chaque ligne dans les versions du plan (même ligne d'origine) → la ligne du plan demandé.
        Map<Integer, Integer> origineVersLigne = new HashMap<>();
        lignes.forEach(l -> origineVersLigne.putIfAbsent(l.getIdLigneOrigine(), l.getIdDetail()));
        Map<Integer, Integer> versLigne = new HashMap<>();
        lignes.forEach(l -> versLigne.put(l.getIdDetail(), l.getIdDetail()));
        List<Marche> copies = new ArrayList<>(marches.findByIdLigneOrigineIn(origineVersLigne.keySet()));
        copies.addAll(marches.findAllById(origineVersLigne.keySet()));
        copies.forEach(c -> {
            Integer l = origineVersLigne.get(c.getIdLigneOrigine());
            if (l != null) {
                versLigne.putIfAbsent(c.getIdDetail(), l);
            }
        });

        List<Fait> faits = new ArrayList<>();
        // 102 — la publication de l'AGPM, sur ce plan ou une version antérieure.
        PublicationAgpmService a = agpm.getIfAvailable();
        if (a != null) {
            Dossier p = plan;
            Set<Integer> vus = new HashSet<>();
            while (p != null && vus.add(p.getIdDossier())) {
                var pub = a.enVigueurDuPlan(p.getIdDossier());
                if (pub.isPresent()) {
                    LocalDate d = pub.get().getDateParution() != null ? pub.get().getDateParution() : jour(pub.get().getDeposeLe());
                    lignes.forEach(l -> faits.add(new Fait(l.getIdDetail(), 102, d, "Publication de l'avis général (AGPM)")));
                    break;
                }
                p = p.getIdDossierParent() == null ? null : dossiers.findById(p.getIdDossierParent()).orElse(null);
            }
        }

        // Les procédures des lignes, leurs dossiers de mise en concurrence et de marché.
        Map<Long, Integer> ligneDuDmc = new HashMap<>();
        for (DossierMec m : dmcs.findByIdDetailIn(versLigne.keySet())) {
            ligneDuDmc.put(m.getIdDmc(), versLigne.get(m.getIdDetail()));
        }
        Map<Integer, Integer> ligneDuMec = new HashMap<>();
        Map<Integer, String> refe = new HashMap<>();
        Map<Integer, Integer> ligneDuMarche = new HashMap<>();
        Map<Integer, Integer> lotDuMarche = new HashMap<>();
        if (!ligneDuDmc.isEmpty()) {
            for (Dossier d : dossiers.findByIdDmcIn(ligneDuDmc.keySet())) {
                ligneDuMec.put(d.getIdDossier(), ligneDuDmc.get(d.getIdDmc()));
                refe.put(d.getIdDossier(), d.getRefeDossier());
            }
        }
        for (Dossier d : dossiers.findByIdDetailOrigineIn(versLigne.keySet())) {   // V163 : hors fiche, rattachés à la ligne
            Integer l = versLigne.get(d.getIdDetailOrigine());
            (FAMILLE_DMC.equals(d.getIdTypeDossier()) ? ligneDuMec : ligneDuMarche).put(d.getIdDossier(), l);
            refe.put(d.getIdDossier(), d.getRefeDossier());
        }
        List<Attribution> attribs = ligneDuDmc.isEmpty() ? List.of()
                : attributions.findByIdDmcInOrderByIdDmcAscLotAsc(ligneDuDmc.keySet());
        Set<Integer> dossiersDeMarche = new HashSet<>();
        for (Attribution at : attribs) {
            if (at.getIdDossier() != null) {
                ligneDuMarche.put(at.getIdDossier(), ligneDuDmc.get(at.getIdDmc()));
                lotDuMarche.put(at.getIdDossier(), at.getLot());
                dossiersDeMarche.add(at.getIdDossier());
            }
        }
        if (!dossiersDeMarche.isEmpty()) {
            dossiers.findAllById(dossiersDeMarche).forEach(d -> refe.put(d.getIdDossier(), d.getRefeDossier()));
        }

        // 106 / 117 — la première réception ; 107-109 / 118-120 — le PV.
        Set<Integer> suivis = new HashSet<>(ligneDuMec.keySet());
        suivis.addAll(ligneDuMarche.keySet());
        if (!suivis.isEmpty()) {
            receptions.findByIdDossierIn(suivis).forEach(r -> {
                boolean mec = ligneDuMec.containsKey(r.getIdDossier());
                faits.add(new Fait(mec ? ligneDuMec.get(r.getIdDossier()) : ligneDuMarche.get(r.getIdDossier()), mec ? 106 : 117,
                        jour(r.getDateReception()), "Réception du dossier " + nom(refe, r.getIdDossier(), lotDuMarche)));
            });
            for (Object[] pv : pvs.datesPvParDossiers(suivis)) {
                Integer id = (Integer) pv[0];
                boolean mec = ligneDuMec.containsKey(id);
                Integer l = mec ? ligneDuMec.get(id) : ligneDuMarche.get(id);
                String n = nom(refe, id, lotDuMarche);
                LocalDate datePv = (LocalDate) pv[1];
                LocalDate soumis = (LocalDate) pv[2];
                LocalDate signe = plusTard((LocalDate) pv[3], (LocalDate) pv[4]);
                faits.add(new Fait(l, mec ? 107 : 118, datePv, "Avis de la Commission sur le dossier " + n));
                faits.add(new Fait(l, mec ? 108 : 119, soumis != null ? soumis : datePv, "PV de la Commission sur le dossier " + n));
                if ("SIGNE".equals(pv[5]) || "ARCHIVE".equals(pv[5]) || signe != null) {
                    faits.add(new Fait(l, mec ? 109 : 120, signe, "Signature du PV de la Commission sur le dossier " + n));
                }
            }
        }

        if (!ligneDuDmc.isEmpty()) {
            // 111 — la première impression de l'avis spécifique ou des lettres d'invitation ; 112 — la date limite passée.
            List<FicheMarche> fs = fiches.findByIdDmcIn(ligneDuDmc.keySet());
            Map<Integer, Long> dmcDeLaFiche = new HashMap<>();
            fs.forEach(f -> dmcDeLaFiche.put(f.getIdFiche(), f.getIdDmc()));
            if (!fs.isEmpty()) {
                for (var doc : documents.findByIdFicheInAndTypeInOrderByIdDocumentDesc(dmcDeLaFiche.keySet(),
                        DocumentsFicheMarcheService.TYPES_PUBLICATION)) {
                    faits.add(new Fait(ligneDuDmc.get(dmcDeLaFiche.get(doc.getIdFiche())), 111, jour(doc.getDateGeneration()),
                            DocumentsFicheMarcheService.TYPE_LETTRE.equals(doc.getType()) ? "Première impression des lettres d'invitation"
                                    : "Première impression de l'avis spécifique"));
                }
                Map<Long, FicheMarche> derniereValidee = new HashMap<>();
                fs.stream().filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut()))
                        .forEach(f -> derniereValidee.merge(f.getIdDmc(), f, (x, y) -> x.getNumeroVersion() >= y.getNumeroVersion() ? x : y));
                if (!derniereValidee.isEmpty()) {
                    Map<Integer, Map<String, String>> parFiche = new HashMap<>();
                    valeurs.findByIdFicheIn(derniereValidee.values().stream().map(FicheMarche::getIdFiche).toList())
                            .forEach(v -> parFiche.computeIfAbsent(v.getIdFiche(), k -> new HashMap<>()).put(v.getCodeChamp(), v.getValeur()));
                    LocalDate aujourdhui = LocalDate.now(HorlogeRecette.HORLOGE);
                    derniereValidee.forEach((dmc, f) -> {
                        LocalDate limite = DelaisPublicite.dateLimite(parFiche.getOrDefault(f.getIdFiche(), Map.of()));
                        if (limite != null && !limite.isAfter(aujourdhui)) {
                            faits.add(new Fait(ligneDuDmc.get(dmc), 112, limite, "Date limite de remise des offres (fiche, version "
                                    + f.getNumeroVersion() + ")"));
                        }
                    });
                }
            }
            // 113, 115 — la séance d'ouverture ; 114 — le rapport d'évaluation signé.
            seances.findAllById(ligneDuDmc.keySet()).forEach(s -> {
                Integer l = ligneDuDmc.get(s.getIdDmc());
                faits.add(new Fait(l, 113, jour(s.getOuverteLe()), "Séance d'ouverture des plis"));
                faits.add(new Fait(l, 115, jour(s.getPvSigneLe()), "Signature du PV d'ouverture des plis"));
            });
            rapports.findAllById(ligneDuDmc.keySet()).forEach(r -> faits.add(new Fait(ligneDuDmc.get(r.getIdDmc()), 114, jour(r.getSigneLe()),
                    "Signature du rapport d'évaluation")));
            // 123 à 129 — l'attribution de chaque lot.
            for (Attribution at : attribs) {
                Integer l = ligneDuDmc.get(at.getIdDmc());
                String lot = at.getLot() == null ? "" : " (lot " + at.getLot() + ")";
                faits.add(new Fait(l, 123, jour(at.getAttribueLe()), "Attribution provisoire" + lot));
                faits.add(new Fait(l, 124, jour(at.getInformeLe()), "Information des candidats non retenus" + lot));
                faits.add(new Fait(l, 125, at.getDateSignature() != null ? at.getDateSignature() : jour(at.getSigneLe()), "Signature du marché" + lot));
                faits.add(new Fait(l, 126, at.getVisaDate(), "Visa du contrôleur financier" + lot));
                faits.add(new Fait(l, 127, at.getApprobationDate() != null ? at.getApprobationDate() : jour(at.getApprouveLe()),
                        "Approbation du marché" + lot));
                faits.add(new Fait(l, 128, at.getDateEnregistrement() != null ? at.getDateEnregistrement() : jour(at.getEnregistreLe()),
                        "Enregistrement du marché" + lot));
                faits.add(new Fait(l, 129, at.getDateNotification() != null ? at.getDateNotification() : jour(at.getNotifieLe()),
                        "Notification du marché" + lot));
            }
        }

        // Les anciens processus (1 LANCEMENT, 3 OUVERTURE, 4 ATTRIBUTION) prennent la date de leur équivalent.
        List<Fait> anciens = new ArrayList<>();
        for (Fait f : faits) {
            Integer ancien = f.idCapm() == 111 ? Integer.valueOf(1) : f.idCapm() == 113 ? Integer.valueOf(3) : f.idCapm() == 123 ? Integer.valueOf(4) : null;
            if (ancien != null) {
                anciens.add(new Fait(f.idDetail(), ancien, f.date(), f.source()));
            }
        }
        faits.addAll(anciens);

        // La première date par ligne et par processus.
        Map<String, Fait> premiers = new LinkedHashMap<>();
        faits.stream().filter(f -> f.idDetail() != null && f.date() != null)
                .sorted(Comparator.comparing(Fait::date))
                .forEach(f -> premiers.putIfAbsent(f.idDetail() + ":" + f.idCapm(), f));
        return premiers.values().stream()
                .sorted(Comparator.comparing(Fait::idDetail).thenComparing(Fait::idCapm))
                .map(f -> new JalonReelDto(f.idDetail(), f.idCapm(), f.date(), f.source() + ", le " + f.date().format(JOUR)))
                .toList();
    }

    private static LocalDate jour(LocalDateTime t) {
        return t == null ? null : t.toLocalDate();
    }

    private static LocalDate plusTard(LocalDate a, LocalDate b) {
        return a == null ? b : b == null ? a : a.isAfter(b) ? a : b;
    }

    private static String nom(Map<Integer, String> refe, Integer idDossier, Map<Integer, Integer> lots) {
        String r = Objects.requireNonNullElse(refe.get(idDossier), "n° " + idDossier);
        Integer lot = lots.get(idDossier);
        return r + (lot == null ? "" : " (lot " + lot + ")");
    }
}
