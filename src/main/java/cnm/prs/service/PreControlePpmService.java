package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Anomalie;
import cnm.prs.entity.AnomalieLigne;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.enums.SourceSignalement;
import cnm.prs.enums.StatutSignalement;
import cnm.prs.enums.TypeSignalement;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AnomalieLigneRepository;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.NatureRepository;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2) — <strong>exécuter les règles sur un
 * plan et tenir à jour ses signalements</strong>.
 *
 * <p>Trois responsabilités, et pas une de plus :</p>
 * <ol>
 *   <li><strong>charger</strong> le plan une fois ({@link ContextePreControle}) — les règles ne touchent
 *       jamais la base ;</li>
 *   <li><strong>exécuter</strong> les règles dont la ligne de {@code t_regle_anomalie} est active ;</li>
 *   <li><strong>rapprocher</strong> les constats de ce qui est déjà enregistré. C'est le point délicat du
 *       lot, et la raison d'être de {@code CLE_SIGNALEMENT} : un signalement <strong>déjà écarté</strong>
 *       qui ressort doit retrouver sa ligne, avec le motif de la PRMP — jamais être remplacé par un
 *       doublon vierge. Et un signalement qui ne ressort plus n'est <strong>pas supprimé</strong> : il
 *       passe {@code LEVE_MODIFICATION}. Sans cela, la dissuasion s'évapore — il suffirait de réimputer
 *       ses lignes jusqu'à ce que l'alarme se taise pour effacer toute trace.</li>
 * </ol>
 *
 * <p><strong>Ce service n'a aucune garde de visibilité</strong>, délibérément : il n'est appelé que par
 * des couches qui en ont déjà posé une (l'endpoint de l'étape 3 vérifie que la PRMP est propriétaire du
 * plan, ou que le contrôleur en a la localité). C'est la règle d'étanchéité du plan, §2 : les accès aux
 * données passent par les gardes existantes, on n'en invente pas de nouvelles ici.</p>
 *
 * <p><strong>Il ne touche jamais aux signalements de source IA</strong> : ce sont deux populations
 * distinctes, produites par deux mécanismes différents (étape 6), et une exécution des règles n'a pas à
 * lever une piste du modèle.</p>
 */
@Service
@Transactional
public class PreControlePpmService {

    private static final Logger log = LoggerFactory.getLogger(PreControlePpmService.class);

    /** Famille des dossiers de planification — la seule dont la grille porte les points du PPM. */
    private static final String FAMILLE_DDP = "DDP";

    private final PpmRepository ppmRepository;
    private final DossierRepository dossierRepository;
    private final MarcheRepository marcheRepository;
    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    private final LotRepository lotRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final NatureRepository natureRepository;
    private final ModePassationRepository modePassationRepository;
    private final CapmRepository capmRepository;
    private final PointsCtrlRepository pointsCtrlRepository;
    private final RegleAnomalieRepository regleAnomalieRepository;
    private final AnomalieRepository anomalieRepository;
    private final AnomalieLigneRepository anomalieLigneRepository;
    private final SeuilMarcheService seuilMarcheService;

    public PreControlePpmService(PpmRepository ppmRepository, DossierRepository dossierRepository,
            MarcheRepository marcheRepository,
            ServiceBeneficiaireRepository serviceBeneficiaireRepository, LotRepository lotRepository,
            MarchePrevisionRepository marchePrevisionRepository, NatureRepository natureRepository,
            ModePassationRepository modePassationRepository, CapmRepository capmRepository,
            PointsCtrlRepository pointsCtrlRepository, RegleAnomalieRepository regleAnomalieRepository,
            AnomalieRepository anomalieRepository, AnomalieLigneRepository anomalieLigneRepository,
            SeuilMarcheService seuilMarcheService) {
        this.ppmRepository = ppmRepository;
        this.dossierRepository = dossierRepository;
        this.marcheRepository = marcheRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.lotRepository = lotRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.natureRepository = natureRepository;
        this.modePassationRepository = modePassationRepository;
        this.capmRepository = capmRepository;
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.regleAnomalieRepository = regleAnomalieRepository;
        this.anomalieRepository = anomalieRepository;
        this.anomalieLigneRepository = anomalieLigneRepository;
        this.seuilMarcheService = seuilMarcheService;
    }

    /**
     * Ce qu'une exécution a changé — de quoi écrire une phrase honnête à l'écran (« 3 signalements, dont 1
     * prioritaire ; 2 levés par vos modifications ») sans recompter derrière.
     *
     * @param detectes   constats rendus par les règles
     * @param nouveaux   signalements créés
     * @param retrouves  signalements déjà en base, remis à jour (écartement et motif conservés)
     * @param leves      signalements qui ne ressortent plus, passés à {@code LEVE_MODIFICATION}
     * @param ouverts    signalements ouverts à l'issue de l'exécution (ni écartés, ni levés)
     * @param ecartes    signalements écartés, conservés tels quels
     */
    public record ResultatPreControle(int detectes, int nouveaux, int retrouves, int leves, int ouverts,
            int ecartes) {
    }

    /**
     * Exécute le pré-contrôle d'un plan et met ses signalements à jour.
     *
     * <p>Idempotent : deux exécutions de suite sur un plan inchangé rendent le même résultat et ne créent
     * rien la seconde fois. C'est ce qui permet de l'appeler sur un bouton, à la soumission, et autant de
     * fois que la PRMP le veut.</p>
     */
    public ResultatPreControle executer(Integer idPpm) {
        Ppm ppm = ppmRepository.findById(idPpm)
                .orElseThrow(() -> new ResourceNotFoundException("PPM introuvable : " + idPpm));
        return rapprocher(ppm, executerRegles(charger(ppm)));
    }

    /** Les constats des règles actives, sans rien écrire — pour un aperçu ou un test. */
    @Transactional(readOnly = true)
    public List<SignalementDetecte> constats(Integer idPpm) {
        Ppm ppm = ppmRepository.findById(idPpm)
                .orElseThrow(() -> new ResourceNotFoundException("PPM introuvable : " + idPpm));
        return executerRegles(charger(ppm)).constats();
    }

    /**
     * Ce qu'une passe des règles a produit, <strong>et lesquelles ont tourné</strong>. Les deux comptent :
     * un signalement qui ne ressort plus n'est « levé » que si <strong>sa</strong> règle a bien été
     * exécutée. Sinon, éteindre une règle depuis l'administration lèverait tous ses signalements en
     * prétendant que « le plan a changé » — ce qui serait faux, et effacerait des constats que le
     * contrôleur a peut-être déjà lus.
     */
    private record Execution(List<SignalementDetecte> constats, java.util.Set<String> codesExecutes) {
    }

    // ------------------------------------------------------------------ 1. charger

    private ContextePreControle charger(Ppm ppm) {
        Integer idDossier = ppm.getIdDossier();
        List<Marche> lignes = marcheRepository.findByIdPpm(ppm.getIdPpm()).stream()
                .filter(m -> !Boolean.TRUE.equals(m.getSupprimee()))
                .sorted((a, b) -> Integer.compare(a.getIdDetail(), b.getIdDetail()))
                .toList();

        Map<Integer, List<ServiceBeneficiaire>> beneficiaires =
                serviceBeneficiaireRepository.findParDossiers(List.of(idDossier)).stream()
                        .collect(Collectors.groupingBy(ServiceBeneficiaire::getIdDetail));
        Map<Integer, List<Lot>> lots = lotRepository.findByIdDossier(idDossier).stream()
                .collect(Collectors.groupingBy(Lot::getIdDetail));
        Map<Integer, List<MarchePrevision>> previsions =
                marchePrevisionRepository.findParDossiers(List.of(idDossier)).stream()
                        .collect(Collectors.groupingBy(MarchePrevision::getIdDetail));
        Map<Integer, Nature> natures = natureRepository.findAll().stream()
                .collect(Collectors.toMap(Nature::getIdNature, Function.identity(), (a, b) -> a));
        Map<Integer, ModePassation> modes = modePassationRepository.findAll().stream()
                .collect(Collectors.toMap(ModePassation::getIdMode, Function.identity(), (a, b) -> a));
        Map<Integer, String> processus = capmRepository.findAll().stream()
                .filter(c -> c.getLibelleProcessus() != null)
                .collect(Collectors.toMap(Capm::getIdCapm, Capm::getLibelleProcessus, (a, b) -> a));

        return new ContextePreControle(ppm, localiteDe(ppm), seuilMarcheService.chargerEnVigueurLe(dateDeLecture(ppm)),
                lignes, beneficiaires, lots, previsions, natures, modes, processus);
    }

    /**
     * Organisme de contrôle du plan — il choisit le barème de seuils. Le PPM le porte ; s'il ne le porte
     * pas (plans anciens), le dossier le porte toujours.
     */
    private String localiteDe(Ppm ppm) {
        if (ppm.getIdLocalite() != null && !ppm.getIdLocalite().isBlank()) {
            return ppm.getIdLocalite();
        }
        return dossierRepository.findById(ppm.getIdDossier()).map(Dossier::getIdLocalite).orElse(null);
    }

    /**
     * Date à laquelle le barème se lit : celle du plan, <strong>pas celle du jour</strong>. Un plan signé
     * sous l'ancien arrêté doit se relire avec ses seuils — sinon un contrôleur verrait, un an plus tard,
     * un signalement qui n'avait pas lieu d'être au moment des faits.
     *
     * <p>Ordre de préférence : date de la mise à jour en cours, à défaut date de signature, à défaut le
     * premier jour de l'exercice, à défaut aujourd'hui.</p>
     */
    private static LocalDate dateDeLecture(Ppm ppm) {
        if (ppm.getDateMaj() != null) {
            return ppm.getDateMaj();
        }
        if (ppm.getDateSignature() != null) {
            return ppm.getDateSignature();
        }
        if (ppm.getExercice() != null) {
            return LocalDate.of(ppm.getExercice(), 1, 1);
        }
        return LocalDate.now();
    }

    // ------------------------------------------------------------------ 2. exécuter

    /**
     * Exécute les règles <strong>actives</strong>. Une règle sans ligne dans {@code t_regle_anomalie} est
     * sautée avec un avertissement : ses signalements ne pourraient pas être enregistrés
     * ({@code ID_REGLE_ANOMALIE} est obligatoire et porte une clé étrangère), et le seeder de démarrage a
     * précisément pour rôle que ce cas n'arrive pas.
     *
     * <p>Une règle qui échoue n'arrête pas les autres : un plan mal formé doit produire moins de
     * signalements, pas priver la PRMP de tous.</p>
     */
    private Execution executerRegles(ContextePreControle contexte) {
        List<SignalementDetecte> constats = new ArrayList<>();
        java.util.Set<String> executees = new java.util.LinkedHashSet<>();
        for (ReglePreControle regle : ReglesPreControle.toutes()) {
            Optional<RegleAnomalie> ligneRegle = regleAnomalieRepository.findByCodeRegle(regle.type().name());
            if (ligneRegle.isEmpty()) {
                log.warn("[PRE-CONTROLE] règle {} sautée : aucune ligne dans t_regle_anomalie.",
                        regle.type());
                continue;
            }
            if (Boolean.FALSE.equals(ligneRegle.get().getActif())) {
                continue;   // désactivée par l'Administrateur — sans redéploiement
            }
            try {
                constats.addAll(regle.examiner(contexte));
                executees.add(regle.type().name());
            } catch (RuntimeException e) {
                log.error("[PRE-CONTROLE] règle {} en échec sur le PPM {} : {}", regle.type(),
                        contexte.ppm().getIdPpm(), e.getMessage(), e);
            }
        }
        return new Execution(constats, executees);
    }

    // ------------------------------------------------------------------ 3. rapprocher

    private ResultatPreControle rapprocher(Ppm ppm, Execution execution) {
        List<SignalementDetecte> constats = execution.constats();
        Map<String, Anomalie> existants = anomalieRepository.findByIdPpmOrderByIdAnomalie(ppm.getIdPpm())
                .stream()
                .filter(a -> !SourceSignalement.IA.name().equals(a.getSource()))
                .filter(a -> a.getCleSignalement() != null)
                .collect(Collectors.toMap(Anomalie::getCleSignalement, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        Map<String, Integer> idsRegles = new HashMap<>();
        Map<TypeSignalement.PointDeGrille, Integer> pointsDeGrille = pointsDeGrille();
        LocalDateTime maintenant = LocalDateTime.now();

        int nouveaux = 0;
        int retrouves = 0;
        for (SignalementDetecte constat : constats) {
            Integer idRegle = idsRegles.computeIfAbsent(constat.type().name(),
                    code -> regleAnomalieRepository.findByCodeRegle(code)
                            .map(RegleAnomalie::getIdRegleAnomalie).orElse(null));
            if (idRegle == null) {
                continue;   // déjà signalé à l'exécution : la règle n'a pas de ligne, on n'invente pas de FK
            }
            Anomalie existant = existants.remove(constat.cle());
            if (existant == null) {
                creer(ppm, constat, idRegle, pointsDeGrille, maintenant);
                nouveaux++;
            } else {
                mettreAJour(existant, constat, idRegle, pointsDeGrille, maintenant);
                retrouves++;
            }
        }

        int leves = 0;
        for (Anomalie orphelin : existants.values()) {
            if (!execution.codesExecutes().contains(orphelin.getTypeAnomalie())) {
                continue;   // sa règle n'a pas tourné : son silence ne prouve rien (voir Execution)
            }
            if (StatutSignalement.LEVE_MODIFICATION.name().equals(orphelin.getStatut())) {
                continue;   // déjà levé lors d'une exécution précédente
            }
            lever(orphelin, maintenant);
            leves++;
        }

        List<Anomalie> apres = anomalieRepository.findByIdPpmOrderByIdAnomalie(ppm.getIdPpm());
        int ouverts = (int) apres.stream()
                .filter(a -> StatutSignalement.OUVERT.name().equals(a.getStatut())).count();
        int ecartes = (int) apres.stream()
                .filter(a -> StatutSignalement.ECARTE.name().equals(a.getStatut())).count();
        return new ResultatPreControle(constats.size(), nouveaux, retrouves, leves, ouverts, ecartes);
    }

    private void creer(Ppm ppm, SignalementDetecte constat, Integer idRegle,
            Map<TypeSignalement.PointDeGrille, Integer> pointsDeGrille, LocalDateTime maintenant) {
        Anomalie a = new Anomalie();
        a.setIdAnomalie(anomalieRepository.nextIdAnomalie().intValue());
        a.setIdPpm(ppm.getIdPpm());
        a.setIdDetail(constat.idDetail());
        a.setIdRegleAnomalie(idRegle);
        a.setTypeAnomalie(constat.type().name());
        a.setGravite(constat.gravite().name());
        a.setSource(SourceSignalement.REGLE.name());
        a.setStatut(StatutSignalement.OUVERT.name());
        a.setDescription(constat.description());
        a.setSuggestion(constat.suggestion());
        a.setCleSignalement(constat.cle());
        a.setDateDetection(maintenant);
        a.setIdPointCtrl(pointsDeGrille.get(constat.type().pointDeGrille()));
        anomalieRepository.save(a);
        ecrireLignes(a.getIdAnomalie(), constat);
    }

    /**
     * Remet à jour un signalement déjà enregistré : le <strong>constat</strong> est réécrit (les montants
     * ont pu changer), mais <strong>ni le statut, ni l'écartement, ni son motif</strong> ne sont touchés.
     * C'est la promesse faite à la PRMP : un signalement qu'elle a écarté en motivant ne revient pas vierge
     * à chaque vérification.
     *
     * <p>Un signalement <strong>levé</strong> qui ressort redevient ouvert — le plan a de nouveau le défaut
     * —, et la trace de la levée est effacée puisqu'elle n'est plus vraie. Un signalement
     * <strong>écarté</strong>, lui, reste écarté.</p>
     */
    private void mettreAJour(Anomalie existant, SignalementDetecte constat, Integer idRegle,
            Map<TypeSignalement.PointDeGrille, Integer> pointsDeGrille, LocalDateTime maintenant) {
        existant.setIdRegleAnomalie(idRegle);
        existant.setIdDetail(constat.idDetail());
        existant.setGravite(constat.gravite().name());
        existant.setDescription(constat.description());
        existant.setSuggestion(constat.suggestion());
        existant.setIdPointCtrl(pointsDeGrille.get(constat.type().pointDeGrille()));
        existant.setDateDetection(maintenant);
        if (StatutSignalement.LEVE_MODIFICATION.name().equals(existant.getStatut())) {
            existant.setStatut(StatutSignalement.OUVERT.name());
            existant.setDateLevee(null);
            existant.setDetailLevee(null);
        }
        anomalieRepository.save(existant);
        anomalieLigneRepository.deleteByIdAnomalie(existant.getIdAnomalie());
        ecrireLignes(existant.getIdAnomalie(), constat);
    }

    /**
     * Un signalement qui ne ressort plus est <strong>levé</strong>, jamais supprimé, et la date le dit.
     *
     * <p>⚠️ Le <strong>détail</strong> de ce qui a changé reste sommaire à cette étape : le service sait
     * que le constat ne ressort plus, pas quelle ligne a été réécrite. Le rapprochement fin avec le
     * journal des changements de lignes ({@code t_changement_ligne}, qui existe déjà pour les mises à
     * jour) est prévu à l'étape 3, où l'écran du contrôleur en a l'usage.</p>
     */
    private void lever(Anomalie orphelin, LocalDateTime maintenant) {
        orphelin.setStatut(StatutSignalement.LEVE_MODIFICATION.name());
        orphelin.setDateLevee(maintenant);
        orphelin.setDetailLevee("Ce signalement ne ressort plus de la vérification du "
                + maintenant.toLocalDate() + " : le plan a changé depuis sa détection"
                + (orphelin.getDateDetection() == null ? ""
                        : " du " + orphelin.getDateDetection().toLocalDate())
                + ".");
        anomalieRepository.save(orphelin);
    }

    /** Réécrit les lignes visées par un signalement inter-lignes (le groupe change quand le plan change). */
    private void ecrireLignes(Integer idAnomalie, SignalementDetecte constat) {
        for (SignalementDetecte.LigneVisee ligne : constat.lignes()) {
            AnomalieLigne visee = new AnomalieLigne();
            visee.setIdAnomalie(idAnomalie);
            visee.setIdDetail(ligne.idDetail());
            visee.setMontant(ligne.montant());
            anomalieLigneRepository.save(visee);
        }
    }

    /**
     * Rattachement des signalements à la grille de contrôle du PPM, par <strong>libellé normalisé</strong>
     * : la grille est un référentiel administrable — l'Administrateur peut avoir ajusté un libellé, et la
     * grille livrée porte les siens sans accents. Un point introuvable laisse le signalement sans
     * rattachement, ce qui n'empêche rien.
     */
    private Map<TypeSignalement.PointDeGrille, Integer> pointsDeGrille() {
        Map<String, Integer> parLibelle = new HashMap<>();
        for (PointsCtrl point : pointsCtrlRepository.findAll()) {
            if (FAMILLE_DDP.equals(point.getIdTypeDossier()) && point.getLibelPointCtrl() != null) {
                parLibelle.putIfAbsent(ContextePreControle.normaliser(point.getLibelPointCtrl()),
                        point.getIdPointCtrl());
            }
        }
        Map<TypeSignalement.PointDeGrille, Integer> resolus = new LinkedHashMap<>();
        for (TypeSignalement.PointDeGrille point : TypeSignalement.PointDeGrille.values()) {
            Integer id = parLibelle.get(ContextePreControle.normaliser(point.libelle()));
            if (id != null) {
                resolus.put(point, id);
            }
        }
        return resolus;
    }
}
