package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.VersionArchiveeDetailDto;
import cnm.prs.dto.VersionArchiveeDetailDto.BeneficiaireVersion;
import cnm.prs.dto.VersionArchiveeDetailDto.LigneVersion;
import cnm.prs.dto.VersionArchiveeDetailDto.LotVersion;
import cnm.prs.dto.VersionArchiveeDetailDto.PrevisionVersion;
import cnm.prs.dto.VersionArchiveeDto;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.entity.SnapshotRectifBeneficiaire;
import cnm.prs.entity.SnapshotRectifLigne;
import cnm.prs.entity.SnapshotRectifLot;
import cnm.prs.entity.SnapshotRectifPrevision;
import cnm.prs.entity.VersionDossier;
import cnm.prs.enums.OrigineVersion;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.repository.SnapshotRectifBeneficiaireRepository;
import cnm.prs.repository.SnapshotRectifLigneRepository;
import cnm.prs.repository.SnapshotRectifLotRepository;
import cnm.prs.repository.SnapshotRectifPrevisionRepository;
import cnm.prs.repository.VersionDossierRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ <strong>Versions archivées d'un dossier</strong> (demande pilote du 2026-09-06, relayée par le front —
 * {@code docs/demande-backend-2026-09-06-versions-rectification.md}, commit {@code 49fad99}).
 *
 * <p>La rectification ({@code PUT /api/saisies/ppm/{id}} sur un dossier {@code EN_ATTENTE_DECISION_PRMP})
 * corrige le PPM <em>en place</em>. Depuis le 2026-08-15, l'état d'AVANT la première correction de chaque
 * cycle était figé ({@code t_snapshot_rectif_ligne}) — mais une seule série survivait, celle du dernier
 * cycle, et pour le seul {@code /diff-rectification}. Le pilote veut l'<strong>historique du dossier</strong> :
 * chaque version remplacée est désormais une <strong>version archivée</strong>, immuable, numérotée,
 * datée, signée (PRMP opératrice + login), rattachée à son itération de rectification, avec ses lignes
 * <em>complètes</em> (bénéficiaires, lots, dates prévisionnelles compris).</p>
 *
 * <h3>Pourquoi pas la mécanique des mises à jour</h3>
 * <p>Une mise à jour de PPM ({@code MiseAJourPpmService}) crée un <em>nouveau dossier</em> par version : une
 * nouvelle instruction, avec son circuit. Une version archivée de rectification n'est pas un dossier — pas
 * de statut, pas de réception, pas de KPI, invisible des listes. La réutiliser aurait semé des dossiers
 * fantômes. On garde donc les lignes déjà figées et on leur donne un en-tête ({@link VersionDossier}).</p>
 *
 * <h3>Cycle de vie</h3>
 * <ol>
 *   <li>{@link #archiverAvantPremiereCorrection} — appelé par {@code SaisieService.editerPpm} en branche
 *       rectification, AVANT toute mutation. Au premier PUT d'un cycle, l'état courant devient la version
 *       n+1 ; les PUT suivants du même cycle ne re-figent pas. Plus rien n'est effacé.</li>
 *   <li>{@link #lister} / {@link #detail} — lecture par le circuit (même périmètre que le diff).</li>
 *   <li>{@link #derniereVersionRectification} + {@link #lignes} — servent {@code RectificationDiffService},
 *       dont le contrat (« diff du dernier cycle ») est inchangé.</li>
 *   <li>Purge avec le circuit ({@code CircuitCascadeService}) : retrait accepté, annulation de dispatch,
 *       suppression du dossier — comme l'instantané qu'elles remplacent.</li>
 * </ol>
 *
 * <h3>Reprise d'avant la V18</h3>
 * <p>La série d'instantanés existante est devenue la version n° 1 de son dossier (migration). Ses
 * collections n'avaient été figées que par <em>empreinte</em> : à la lecture, si une ligne n'a aucun
 * enfant figé mais une empreinte non vide, les enfants sont <strong>reconstitués</strong> depuis
 * l'empreinte (mode dégradé documenté sur {@link VersionArchiveeDetailDto}).</p>
 */
@Service
@Transactional
public class VersionDossierService {

    /** Motif d'un élément d'empreinte de lot : {@code désignation:montant:quantité} (désignation libre). */
    private static final Pattern EMP_LOT = Pattern.compile("(.*?):([^:,]*):([^:,]*)(?:,|$)");

    private final VersionDossierRepository versionRepository;
    private final SnapshotRectifLigneRepository ligneRepository;
    private final SnapshotRectifBeneficiaireRepository beneficiaireArchiveRepository;
    private final SnapshotRectifLotRepository lotArchiveRepository;
    private final SnapshotRectifPrevisionRepository previsionArchiveRepository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final MarcheRepository marcheRepository;
    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    private final LotRepository lotRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final CapmRepository capmRepository;
    private final ActionDossierRepository actionDossierRepository;
    private final JournalDossierService journalDossier;
    private final DossierIntegriteService dossierIntegrite;
    private final EmpreintesLigne empreintes;

    public VersionDossierService(VersionDossierRepository versionRepository,
            SnapshotRectifLigneRepository ligneRepository,
            SnapshotRectifBeneficiaireRepository beneficiaireArchiveRepository,
            SnapshotRectifLotRepository lotArchiveRepository,
            SnapshotRectifPrevisionRepository previsionArchiveRepository, DossierRepository dossierRepository,
            PpmRepository ppmRepository, MarcheRepository marcheRepository,
            ServiceBeneficiaireRepository serviceBeneficiaireRepository, LotRepository lotRepository,
            MarchePrevisionRepository marchePrevisionRepository, CapmRepository capmRepository,
            ActionDossierRepository actionDossierRepository, JournalDossierService journalDossier,
            DossierIntegriteService dossierIntegrite, EmpreintesLigne empreintes) {
        this.versionRepository = versionRepository;
        this.ligneRepository = ligneRepository;
        this.beneficiaireArchiveRepository = beneficiaireArchiveRepository;
        this.lotArchiveRepository = lotArchiveRepository;
        this.previsionArchiveRepository = previsionArchiveRepository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.marcheRepository = marcheRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.lotRepository = lotRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.capmRepository = capmRepository;
        this.actionDossierRepository = actionDossierRepository;
        this.journalDossier = journalDossier;
        this.dossierIntegrite = dossierIntegrite;
        this.empreintes = empreintes;
    }

    // ------------------------------------------------------------------
    // Archivage (premier PUT d'un cycle de rectification)
    // ------------------------------------------------------------------

    /**
     * Archive l'état COURANT du dossier comme version n+1 si c'est le premier PUT du cycle courant ; sans
     * effet sinon (le diff compare toujours à l'état d'AVANT la première correction du cycle). À appeler
     * <strong>avant</strong> toute mutation des lignes.
     */
    public void archiverAvantPremiereCorrection(Integer idDossier) {
        int cycle = nbResoumissions(idDossier) + 1;
        String origine = OrigineVersion.RECTIFICATION.name();
        if (versionRepository.existsByIdDossierAndOrigineAndCycle(idDossier, origine, cycle)) {
            return; // pas le premier PUT du cycle
        }
        int numero = versionRepository.findFirstByIdDossierOrderByNumeroDesc(idDossier)
                .map(v -> v.getNumero() + 1).orElse(1);
        List<Marche> marches = marcheRepository.findByIdDossier(idDossier);
        LocalDateTime maintenant = LocalDateTime.now();
        String operateur = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);

        VersionDossier v = new VersionDossier();
        v.setIdDossier(idDossier);
        v.setNumero(numero);
        v.setOrigine(origine);
        v.setCycle(cycle);
        v.setDateVersion(maintenant);
        v.setIdPrmpAuteur(operateur);
        v.setNomAuteur(journalDossier.nomOperateur(operateur));
        v.setAuteur(CurrentUser.login().orElse(operateur));
        ppmRepository.findByIdDossier(idDossier).stream().findFirst().ifPresent(p -> {
            v.setExercice(p.getExercice());
            v.setReference(p.getReference());
            v.setSignataire(p.getSignataire());
            v.setDateSignature(p.getDateSignature());
        });
        v.setNbLignes(marches.size());
        VersionDossier version = versionRepository.save(v);

        for (Marche m : marches) {
            SnapshotRectifLigne s = new SnapshotRectifLigne();
            s.setIdVersion(version.getIdVersion());
            s.setIdDossier(idDossier);
            s.setCycle(cycle);
            s.setIdDetail(m.getIdDetail());
            s.setIdLigneOrigine(m.getIdLigneOrigine());
            s.setDesignationMarche(m.getDesignationMarche());
            s.setMontEstim(m.getMontEstim());
            s.setAncienMontEstim(m.getAncienMontEstim());
            s.setNouvMontEstim(m.getNouvMontEstim());
            s.setNumCompte(m.getNumCompte());
            s.setFinancement(m.getFinancement());
            s.setStatut(m.getStatut());
            s.setIdNature(m.getIdNature());
            s.setIdMode(m.getIdMode());
            s.setFormeMarche(m.getFormeMarche() == null ? null : m.getFormeMarche().name());
            s.setSupprimee(m.getSupprimee());
            s.setJustifModeDerogatoire(m.getJustifModeDerogatoire());
            s.setJustifDelaiAmenage(m.getJustifDelaiAmenage());
            s.setEmpBeneficiaires(empreintes.beneficiaires(m.getIdDetail()));
            s.setEmpLots(empreintes.lots(m.getIdDetail()));
            s.setEmpProcessus(empreintes.previsions(m.getIdDetail()));
            s.setDateSnapshot(maintenant);
            SnapshotRectifLigne ligne = ligneRepository.save(s);
            copierEnfants(ligne.getIdSnapshot(), m.getIdDetail());
        }
    }

    /** Copie le contenu réel des collections de la ligne courante — ce que l'empreinte seule ne restitue pas. */
    private void copierEnfants(Integer idSnapshot, Integer idDetail) {
        for (ServiceBeneficiaire b : serviceBeneficiaireRepository.findByIdDetail(idDetail)) {
            SnapshotRectifBeneficiaire a = new SnapshotRectifBeneficiaire();
            a.setIdSnapshot(idSnapshot);
            a.setSoaCode(b.getSoaCode());
            a.setNumCompte(b.getNumCompte());
            a.setAncMontBenef(b.getAncMontBenef());
            a.setNouvMontBenef(b.getNouvMontBenef());
            beneficiaireArchiveRepository.save(a);
        }
        for (Lot l : lotRepository.findByIdDetail(idDetail)) {
            SnapshotRectifLot a = new SnapshotRectifLot();
            a.setIdSnapshot(idSnapshot);
            a.setDesignationLot(l.getDesignationLot());
            a.setMontLot(l.getMontLot());
            a.setQteLot(l.getQteLot());
            a.setUniteLot(l.getUniteLot());
            lotArchiveRepository.save(a);
        }
        for (MarchePrevision p : marchePrevisionRepository.findByIdDetail(idDetail)) {
            SnapshotRectifPrevision a = new SnapshotRectifPrevision();
            a.setIdSnapshot(idSnapshot);
            a.setIdCapm(p.getIdCapm());
            a.setDateDebut(p.getDateDebut());
            a.setDateFin(p.getDateFin());
            previsionArchiveRepository.save(a);
        }
    }

    /** Resoumissions PRMP du dossier (journal {@code t_action_dossier}) — le cycle courant en vaut +1. */
    @Transactional(readOnly = true)
    public int nbResoumissions(Integer idDossier) {
        return (int) actionDossierRepository.findByIdDossierOrderByDateActionAscIdActionAsc(idDossier).stream()
                .filter(a -> JournalDossierService.RESOUMISSION.equals(a.getTypeAction()))
                .count();
    }

    // ------------------------------------------------------------------
    // Accès pour le diff du dernier cycle
    // ------------------------------------------------------------------

    /** Dernière version archivée par rectification — l'état d'AVANT la première correction du dernier cycle. */
    @Transactional(readOnly = true)
    public Optional<VersionDossier> derniereVersionRectification(Integer idDossier) {
        return versionRepository.findFirstByIdDossierAndOrigineOrderByNumeroDesc(idDossier,
                OrigineVersion.RECTIFICATION.name());
    }

    @Transactional(readOnly = true)
    public List<SnapshotRectifLigne> lignes(VersionDossier version) {
        return ligneRepository.findByIdVersionOrderByIdDetailAsc(version.getIdVersion());
    }

    // ------------------------------------------------------------------
    // Lecture API (historique du dossier)
    // ------------------------------------------------------------------

    /** Versions archivées du dossier, de la plus ancienne à la plus récente ; vide si jamais rectifié. */
    @Transactional(readOnly = true)
    public List<VersionArchiveeDto> lister(Integer idDossier) {
        controlerAccesLecture(charger(idDossier));
        return versionRepository.findByIdDossierOrderByNumeroAsc(idDossier).stream().map(this::toDto).toList();
    }

    /** Contenu complet d'une version archivée (lecture seule). 404 si le numéro n'existe pas pour ce dossier. */
    @Transactional(readOnly = true)
    public VersionArchiveeDetailDto detail(Integer idDossier, Integer numero) {
        controlerAccesLecture(charger(idDossier));
        VersionDossier version = versionRepository.findByIdDossierAndNumero(idDossier, numero)
                .orElseThrow(() -> new ResourceNotFoundException("Version archivée n° " + numero
                        + " introuvable pour le dossier " + idDossier + "."));
        List<SnapshotRectifLigne> lignes = lignes(version);
        List<Integer> ids = lignes.stream().map(SnapshotRectifLigne::getIdSnapshot).toList();
        Map<Integer, List<SnapshotRectifBeneficiaire>> benefs = ids.isEmpty() ? Map.of()
                : beneficiaireArchiveRepository.findByIdSnapshotInOrderByIdSnapshotBenefAsc(ids).stream()
                        .collect(Collectors.groupingBy(SnapshotRectifBeneficiaire::getIdSnapshot));
        Map<Integer, List<SnapshotRectifLot>> lots = ids.isEmpty() ? Map.of()
                : lotArchiveRepository.findByIdSnapshotInOrderByIdSnapshotLotAsc(ids).stream()
                        .collect(Collectors.groupingBy(SnapshotRectifLot::getIdSnapshot));
        Map<Integer, List<SnapshotRectifPrevision>> prevs = ids.isEmpty() ? Map.of()
                : previsionArchiveRepository.findByIdSnapshotInOrderByIdSnapshotPrevAsc(ids).stream()
                        .collect(Collectors.groupingBy(SnapshotRectifPrevision::getIdSnapshot));
        Map<Integer, Integer> ordres = capmRepository.findAll().stream()
                .filter(c -> c.getOrdre() != null)
                .collect(Collectors.toMap(Capm::getIdCapm, Capm::getOrdre, (a, b) -> a));

        List<LigneVersion> resultat = new ArrayList<>();
        for (SnapshotRectifLigne s : lignes) {
            Integer id = s.getIdSnapshot();
            resultat.add(new LigneVersion(s.getIdDetail(), s.getIdLigneOrigine(), s.getDesignationMarche(),
                    s.getNumCompte(), s.getMontEstim(), s.getAncienMontEstim(), s.getNouvMontEstim(),
                    s.getFinancement(), s.getStatut(), s.getIdNature(), s.getIdMode(), s.getFormeMarche(),
                    s.getSupprimee(), s.getJustifModeDerogatoire(), s.getJustifDelaiAmenage(),
                    beneficiaires(benefs.getOrDefault(id, List.of()), s.getEmpBeneficiaires()),
                    lots(lots.getOrDefault(id, List.of()), s.getEmpLots()),
                    previsions(prevs.getOrDefault(id, List.of()), s.getEmpProcessus(), ordres)));
        }
        return new VersionArchiveeDetailDto(toDto(version), resultat);
    }

    /**
     * Lecture : tout-voyant, PRMP propriétaire, ou contrôleur de la localité du dossier (vérificateur
     * titulaire ou délégué compris) — même périmètre que la consultation et que le diff de rectification.
     */
    public void controlerAccesLecture(Dossier dossier) {
        if (Visibilite.voitTout()) {
            return;
        }
        if (Visibilite.estPrmp()) {
            dossierIntegrite.exigerProprietaire(dossier);
            return;
        }
        Visibilite.exigerLocalite(dossier.getIdLocalite());
    }

    // ------------------------------------------------------------------
    // Purge (avec le circuit)
    // ------------------------------------------------------------------

    /** Supprime toutes les versions archivées du dossier, enfants d'abord (ordre FK-safe). */
    public void purger(Integer idDossier) {
        beneficiaireArchiveRepository.deleteParDossier(idDossier);
        lotArchiveRepository.deleteParDossier(idDossier);
        previsionArchiveRepository.deleteParDossier(idDossier);
        ligneRepository.deleteParDossier(idDossier);
        versionRepository.deleteParDossier(idDossier);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Dossier charger(Integer idDossier) {
        return dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
    }

    private VersionArchiveeDto toDto(VersionDossier v) {
        return new VersionArchiveeDto(v.getIdDossier(), v.getNumero(), v.getOrigine(), v.getCycle(),
                v.getDateVersion(), v.getIdPrmpAuteur(), v.getNomAuteur(), v.getAuteur(), v.getNbLignes(),
                v.getExercice(), v.getReference(), v.getSignataire(), v.getDateSignature());
    }

    // --- Collections : enfants figés, sinon reconstitution depuis l'empreinte (reprise d'avant la V18) ---

    private List<BeneficiaireVersion> beneficiaires(List<SnapshotRectifBeneficiaire> figes, String empreinte) {
        if (!figes.isEmpty()) {
            return figes.stream().map(b -> new BeneficiaireVersion(b.getSoaCode(), b.getNumCompte(),
                    b.getAncMontBenef(), b.getNouvMontBenef())).toList();
        }
        List<BeneficiaireVersion> reconstitues = new ArrayList<>();
        for (String element : elements(empreinte)) {
            int sep = element.lastIndexOf(':');
            if (sep < 0) {
                continue;
            }
            // Le montant d'empreinte est le nouveau s'il existait, sinon l'ancien : un seul montant connu.
            reconstitues.add(new BeneficiaireVersion(valeur(element.substring(0, sep)), null, null,
                    montant(element.substring(sep + 1))));
        }
        return reconstitues;
    }

    private List<LotVersion> lots(List<SnapshotRectifLot> figes, String empreinte) {
        if (!figes.isEmpty()) {
            return figes.stream().map(l -> new LotVersion(l.getDesignationLot(), l.getMontLot(), l.getQteLot(),
                    l.getUniteLot())).toList();
        }
        List<LotVersion> reconstitues = new ArrayList<>();
        if (empreinte == null || empreinte.isBlank()) {
            return reconstitues;
        }
        Matcher m = EMP_LOT.matcher(empreinte);
        while (m.find()) {
            // Désignation normalisée au gel (minuscules, espaces réduits) — irrécupérable telle quelle.
            reconstitues.add(new LotVersion(valeur(m.group(1)), montant(m.group(2)), entier(m.group(3)), null));
        }
        return reconstitues;
    }

    private List<PrevisionVersion> previsions(List<SnapshotRectifPrevision> figes, String empreinte,
            Map<Integer, Integer> ordres) {
        List<PrevisionVersion> resultat = new ArrayList<>();
        if (!figes.isEmpty()) {
            for (SnapshotRectifPrevision p : figes) {
                resultat.add(new PrevisionVersion(p.getIdCapm(), ordres.get(p.getIdCapm()), p.getDateDebut(),
                        p.getDateFin()));
            }
        } else {
            for (String element : elements(empreinte)) {
                String[] parts = element.split(":", -1);
                Integer idCapm = parts.length > 0 ? entier(parts[0]) : null;
                if (idCapm == null) {
                    continue;
                }
                resultat.add(new PrevisionVersion(idCapm, ordres.get(idCapm),
                        parts.length > 1 ? date(parts[1]) : null, parts.length > 2 ? date(parts[2]) : null));
            }
        }
        resultat.sort(Comparator.comparing((PrevisionVersion p) -> p.ordre(),
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PrevisionVersion::idCapm));
        return resultat;
    }

    private static List<String> elements(String empreinte) {
        if (empreinte == null || empreinte.isBlank()) {
            return List.of();
        }
        return List.of(empreinte.split(","));
    }

    /** Une valeur d'empreinte : {@code "null"} et le vide sont des absences. */
    private static String valeur(String v) {
        return v == null || v.isBlank() || "null".equals(v) ? null : v;
    }

    private static BigDecimal montant(String v) {
        String s = valeur(v);
        try {
            return s == null ? null : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer entier(String v) {
        String s = valeur(v);
        try {
            return s == null ? null : Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate date(String v) {
        String s = valeur(v);
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
