package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DiffDossierDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.SnapshotRectifLigne;
import cnm.prs.entity.Verification;
import cnm.prs.enums.TypeChangementLigne;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.repository.SnapshotRectifLigneRepository;
import cnm.prs.repository.VerificationRepository;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ Règle ajoutée (2026-08-15, visibilité des rectifications) — rend les changements d'une
 * <strong>rectification</strong> visibles au circuit (vérificateur en tête) : la rectification modifie
 * la version courante <em>en place</em> (structure figée, mise à jour par {@code idDetail}), donc l'état
 * <strong>pré-correction</strong> des lignes est figé au <strong>premier</strong>
 * {@code PUT /api/saisies/ppm/{id}} de chaque cycle ({@link #figerAvantPremiereCorrection}), et le diff
 * du <strong>dernier cycle</strong> est servi via {@code GET /api/dossiers/{id}/diff-rectification}
 * ({@link #diffRectification}) dans le <strong>même DTO</strong> que le diff des mises à jour
 * ({@link DiffDossierDto}) — le front réutilise tel quel son tableau (surlignage MODIFIEE + légende).
 *
 * <p>Un <strong>cycle</strong> = de la transmission des observations ({@code EN_ATTENTE_DECISION_PRMP})
 * à la resoumission. Après une nouvelle transmission puis une nouvelle rectification, le premier PUT du
 * nouveau cycle remplace l'instantané : c'est toujours le <strong>dernier</strong> cycle qui est servi
 * (le vérificateur juge le dernier état). La structure étant figée en rectification, le diff ne produit
 * que des lignes {@code INCHANGEE} / {@code MODIFIEE} (appariement direct par {@code idDetail}).</p>
 *
 * <p>⚠️ Les empreintes de collections et la normalisation des valeurs reprennent la même sémantique que
 * {@code MiseAJourPpmService} (champs {@code CHAMPS_COMPARES}) — à garder synchrones.</p>
 */
@Service
@Transactional
public class RectificationDiffService {

    private final SnapshotRectifLigneRepository snapshotRepository;
    private final MarcheRepository marcheRepository;
    private final DossierRepository dossierRepository;
    private final ActionDossierRepository actionDossierRepository;
    private final VerificationRepository verificationRepository;
    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    private final LotRepository lotRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final DossierIntegriteService dossierIntegrite;

    public RectificationDiffService(SnapshotRectifLigneRepository snapshotRepository,
            MarcheRepository marcheRepository, DossierRepository dossierRepository,
            ActionDossierRepository actionDossierRepository, VerificationRepository verificationRepository,
            ServiceBeneficiaireRepository serviceBeneficiaireRepository, LotRepository lotRepository,
            MarchePrevisionRepository marchePrevisionRepository, DossierIntegriteService dossierIntegrite) {
        this.snapshotRepository = snapshotRepository;
        this.marcheRepository = marcheRepository;
        this.dossierRepository = dossierRepository;
        this.actionDossierRepository = actionDossierRepository;
        this.verificationRepository = verificationRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.lotRepository = lotRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.dossierIntegrite = dossierIntegrite;
    }

    // ------------------------------------------------------------------
    // Gel de l'état pré-correction (premier PUT d'un cycle)
    // ------------------------------------------------------------------

    /**
     * Fige l'état des lignes AVANT la première correction du cycle courant. Appelé par la façade
     * d'édition ({@code SaisieService.editerPpm}) en branche rectification, <strong>avant</strong> toute
     * mutation : au premier PUT du cycle, l'instantané du cycle précédent est remplacé ; les PUT
     * suivants du même cycle ne re-figent pas (le diff compare toujours à l'état d'AVANT la première
     * correction).
     */
    public void figerAvantPremiereCorrection(Integer idDossier) {
        int cycle = cycleCourant(idDossier);
        if (snapshotRepository.existsByIdDossierAndCycle(idDossier, cycle)) {
            return; // pas le premier PUT du cycle
        }
        snapshotRepository.deleteParDossier(idDossier); // remplace l'instantané du cycle précédent
        LocalDateTime maintenant = LocalDateTime.now();
        List<SnapshotRectifLigne> rows = new ArrayList<>();
        for (Marche m : marcheRepository.findByIdDossier(idDossier)) {
            SnapshotRectifLigne s = new SnapshotRectifLigne();
            s.setIdDossier(idDossier);
            s.setCycle(cycle);
            s.setIdDetail(m.getIdDetail());
            s.setIdLigneOrigine(m.getIdLigneOrigine());
            s.setDesignationMarche(m.getDesignationMarche());
            s.setMontEstim(m.getMontEstim());
            s.setNouvMontEstim(m.getNouvMontEstim());
            s.setNumCompte(m.getNumCompte());
            s.setFinancement(m.getFinancement());
            s.setStatut(m.getStatut());
            s.setIdNature(m.getIdNature());
            s.setIdMode(m.getIdMode());
            s.setFormeMarche(m.getFormeMarche() == null ? null : m.getFormeMarche().name());
            s.setSupprimee(m.getSupprimee());
            s.setEmpBeneficiaires(empreinteBeneficiaires(m.getIdDetail()));
            s.setEmpLots(empreinteLots(m.getIdDetail()));
            s.setEmpProcessus(empreintePrevisions(m.getIdDetail()));
            s.setDateSnapshot(maintenant);
            rows.add(s);
        }
        snapshotRepository.saveAll(rows);
    }

    // ------------------------------------------------------------------
    // Diff du dernier cycle (lecture circuit)
    // ------------------------------------------------------------------

    /**
     * Diff du dernier cycle de rectification : instantané pré-correction vs lignes courantes, dans le
     * même {@link DiffDossierDto} que le diff des versions ({@code idDossierPrecedent}/{@code numMaj}
     * nuls — ce n'est pas une comparaison de versions ; {@code motifMaj} = motif de la resoumission qui
     * a clos le cycle ; {@code fige} = cycle clos par une resoumission). 409 si aucune rectification
     * n'a été enregistrée. Lecture : tout-voyant, PRMP propriétaire, ou contrôleur de la localité du
     * dossier (vérificateur titulaire ou délégué compris — même périmètre que la consultation).
     */
    @Transactional(readOnly = true)
    public DiffDossierDto diffRectification(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        controlerAccesLecture(dossier);
        List<SnapshotRectifLigne> avant = snapshotRepository.findByIdDossierOrderByIdDetailAsc(idDossier);
        if (avant.isEmpty()) {
            throw new BusinessRuleException("Aucune rectification enregistrée pour ce dossier : "
                    + "aucun instantané pré-correction (la PRMP n'a pas encore corrigé).");
        }
        int cycle = avant.get(0).getCycle();
        boolean fige = nbResoumissions(idDossier) >= cycle;
        String motif = fige ? verificationRepository.findPassagesDuDossier(idDossier).stream()
                .findFirst().map(Verification::getMotifRectif).orElse(null) : null;

        Map<Integer, Marche> courantes = marcheRepository.findByIdDossier(idDossier).stream()
                .collect(Collectors.toMap(Marche::getIdDetail, m -> m, (a, b) -> a, LinkedHashMap::new));
        List<DiffDossierDto.LigneDiff> lignes = new ArrayList<>();
        for (SnapshotRectifLigne s : avant) {
            Marche m = courantes.remove(s.getIdDetail());
            if (m == null) {
                // Défensif : la structure est figée en rectification, une ligne ne peut pas disparaître.
                lignes.add(new DiffDossierDto.LigneDiff(null, s.getIdLigneOrigine(), s.getDesignationMarche(),
                        TypeChangementLigne.SUPPRIMEE.name(), "ORIGINE", List.of()));
                continue;
            }
            List<DiffDossierDto.ChampDiff> ecarts = comparer(s, m);
            lignes.add(new DiffDossierDto.LigneDiff(m.getIdDetail(), m.getIdLigneOrigine(),
                    m.getDesignationMarche(),
                    (ecarts.isEmpty() ? TypeChangementLigne.INCHANGEE : TypeChangementLigne.MODIFIEE).name(),
                    "ORIGINE", ecarts));
        }
        // Défensif : lignes apparues depuis l'instantané (impossible en rectification, structure figée).
        for (Marche m : courantes.values()) {
            lignes.add(new DiffDossierDto.LigneDiff(m.getIdDetail(), m.getIdLigneOrigine(),
                    m.getDesignationMarche(), TypeChangementLigne.NOUVELLE.name(), "ORIGINE", List.of()));
        }
        return new DiffDossierDto(idDossier, null, null, motif, fige, recap(lignes), lignes);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Cycle courant = nombre de resoumissions PRMP du dossier + 1 (journal {@code t_action_dossier}). */
    private int cycleCourant(Integer idDossier) {
        return nbResoumissions(idDossier) + 1;
    }

    private int nbResoumissions(Integer idDossier) {
        return (int) actionDossierRepository.findByIdDossierOrderByDateActionAscIdActionAsc(idDossier).stream()
                .filter(a -> JournalDossierService.RESOUMISSION.equals(a.getTypeAction()))
                .count();
    }

    /** Lecture : tout-voyant, PRMP propriétaire, ou contrôleur de la localité du dossier (cf. observations). */
    private void controlerAccesLecture(Dossier dossier) {
        if (Visibilite.voitTout()) {
            return;
        }
        if (Visibilite.estPrmp()) {
            dossierIntegrite.exigerProprietaire(dossier);
            return;
        }
        Visibilite.exigerLocalite(dossier.getIdLocalite());
    }

    /** Mêmes champs comparés que le diff des versions ({@code MiseAJourPpmService.CHAMPS_COMPARES}). */
    private List<DiffDossierDto.ChampDiff> comparer(SnapshotRectifLigne avant, Marche apres) {
        List<DiffDossierDto.ChampDiff> ecarts = new ArrayList<>();
        ajouterEcart(ecarts, "designationMarche", texte(avant.getDesignationMarche()), texte(apres.getDesignationMarche()));
        ajouterEcart(ecarts, "montEstim", montant(avant.getMontEstim()), montant(apres.getMontEstim()));
        ajouterEcart(ecarts, "nouvMontEstim", montant(avant.getNouvMontEstim()), montant(apres.getNouvMontEstim()));
        ajouterEcart(ecarts, "numCompte", texte(avant.getNumCompte()), texte(apres.getNumCompte()));
        ajouterEcart(ecarts, "financement", texte(avant.getFinancement()), texte(apres.getFinancement()));
        ajouterEcart(ecarts, "statut", texte(avant.getStatut()), texte(apres.getStatut()));
        ajouterEcart(ecarts, "idNature", nombre(avant.getIdNature()), nombre(apres.getIdNature()));
        ajouterEcart(ecarts, "idMode", nombre(avant.getIdMode()), nombre(apres.getIdMode()));
        ajouterEcart(ecarts, "formeMarche", avant.getFormeMarche(),
                apres.getFormeMarche() == null ? null : apres.getFormeMarche().name());
        ajouterEcart(ecarts, "beneficiaires", avant.getEmpBeneficiaires(), empreinteBeneficiaires(apres.getIdDetail()));
        ajouterEcart(ecarts, "lots", avant.getEmpLots(), empreinteLots(apres.getIdDetail()));
        ajouterEcart(ecarts, "processus", avant.getEmpProcessus(), empreintePrevisions(apres.getIdDetail()));
        return ecarts;
    }

    private void ajouterEcart(List<DiffDossierDto.ChampDiff> ecarts, String champ, String a, String b) {
        if (!Objects.equals(a, b)) {
            ecarts.add(new DiffDossierDto.ChampDiff(champ, a, b));
        }
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

    // --- Empreintes et normalisation : même sémantique que MiseAJourPpmService (à garder synchrones) ---

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

    private String montant(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private String texte(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String nombre(Integer v) {
        return v == null ? null : String.valueOf(v);
    }

    private String normaliser(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.FRENCH).replaceAll("\\s+", " ");
    }
}
