package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AnalyseIaDto;
import cnm.prs.dto.EcartementRequest;
import cnm.prs.dto.ResumePreControleDto;
import cnm.prs.dto.SignalementDto;
import cnm.prs.entity.Anomalie;
import cnm.prs.entity.AnomalieLigne;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.enums.GraviteSignalement;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.SourceSignalement;
import cnm.prs.enums.StatutSignalement;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AnomalieLigneRepository;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 3) — <strong>qui voit quoi, qui écarte
 * quoi</strong>.
 *
 * <p>Le moteur ({@link PreControlePpmService}) ne pose aucune garde : elles sont toutes ici, en un seul
 * endroit, parce que c'est le vrai risque du lot. « Le risque réel n'est pas le modèle mais l'étanchéité
 * des habilitations » — l'audit du 2026-09-14 a classé critique une fuite de données PRMP, et un écran de
 * signalements qui se tromperait de périmètre ferait exactement la même.</p>
 *
 * <h2>Le périmètre</h2>
 * <ul>
 *   <li><strong>PRMP et agent d'UGPM</strong> : les plans dont ils sont propriétaires, et rien d'autre. La
 *       garde est celle du circuit ({@link DossierIntegriteService#exigerProprietaire}), qui admet la PRMP
 *       d'attribution <em>et</em> la PRMP en fonction sur le périmètre — sans quoi un successeur ne
 *       pourrait plus rien vérifier après une passation de témoin.</li>
 *   <li><strong>Contrôleurs</strong> : les plans de <strong>leur localité</strong> — c'est-à-dire de leur
 *       organisme de contrôle. Le Président, qui n'a pas de localité dans son jeton, les voit tous.</li>
 * </ul>
 *
 * <h2>Ce que la PRMP ne voit pas</h2>
 * <p>Un écartement prononcé par un <strong>contrôleur</strong> n'est pas servi à la PRMP : c'est une
 * appréciation interne au contrôle, qui se dit dans le PV et pas dans son écran. Le signalement lui reste
 * présenté tel qu'elle l'a laissé. La visibilité voulue par le pilote est l'inverse — le contrôleur voit
 * <strong>les écartements de la PRMP, avec leur motif</strong> — et la symétrie annoncée est
 * hiérarchique : les écartements d'un contrôleur sont visibles de sa hiérarchie, Chef de commission pour
 * sa localité, Président pour toutes.</p>
 */
@Service
@Transactional
public class SignalementPreControleService {

    private final PpmRepository ppmRepository;
    private final DossierRepository dossierRepository;
    private final MarcheRepository marcheRepository;
    private final AnomalieRepository anomalieRepository;
    private final AnomalieLigneRepository anomalieLigneRepository;
    private final RegleAnomalieRepository regleAnomalieRepository;
    private final PointsCtrlRepository pointsCtrlRepository;
    private final PreControlePpmService preControle;
    private final DossierIntegriteService dossierIntegrite;
    private final AnalysePreControleIaService analyseIa;

    public SignalementPreControleService(PpmRepository ppmRepository, DossierRepository dossierRepository,
            MarcheRepository marcheRepository, AnomalieRepository anomalieRepository,
            AnomalieLigneRepository anomalieLigneRepository,
            RegleAnomalieRepository regleAnomalieRepository, PointsCtrlRepository pointsCtrlRepository,
            PreControlePpmService preControle, DossierIntegriteService dossierIntegrite,
            AnalysePreControleIaService analyseIa) {
        this.analyseIa = analyseIa;
        this.ppmRepository = ppmRepository;
        this.dossierRepository = dossierRepository;
        this.marcheRepository = marcheRepository;
        this.anomalieRepository = anomalieRepository;
        this.anomalieLigneRepository = anomalieLigneRepository;
        this.regleAnomalieRepository = regleAnomalieRepository;
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.preControle = preControle;
        this.dossierIntegrite = dossierIntegrite;
    }

    // ------------------------------------------------------------------ lecture

    /** Lance une vérification du plan et rend son résumé. Ne bloque jamais rien : elle signale. */
    public ResumePreControleDto verifier(Integer idPpm) {
        Ppm ppm = exigerAccesAuPlan(idPpm);
        preControle.executer(idPpm);
        return resume(ppm);
    }

    /** Relit les signalements d'un plan sans relancer les règles. */
    @Transactional(readOnly = true)
    public ResumePreControleDto lire(Integer idPpm) {
        return resume(exigerAccesAuPlan(idPpm));
    }

    /**
     * ⚠️ Étape 6 — demande à l'<strong>assistant</strong> ce que les règles ne peuvent pas voir : le
     * fractionnement déguisé, un objet imprécis, une nature incohérente. Ses constats arrivent en
     * <strong>pistes</strong> ({@code source = IA}), à côté des faits, jamais à leur place.
     *
     * <p>Même garde de périmètre que le reste : on ne fait analyser que ce qu'on a le droit de lire. Et
     * l'appel est <strong>explicite</strong> — un modèle n'est jamais sollicité à la frappe.</p>
     *
     * @throws cnm.prs.exception.ResourceNotFoundException si l'assistant n'est pas activé (contrat du lot 1)
     */
    public AnalyseIaDto analyserParLAssistant(Integer idPpm) {
        Ppm ppm = exigerAccesAuPlan(idPpm);
        AnalysePreControleIaService.Analyse analyse = analyseIa.analyser(
                preControle.chargerContexte(idPpm), CurrentUser.ref().orElse(null));
        return new AnalyseIaDto(analyse.synthese(), resume(ppm));
    }

    // ------------------------------------------------------------------ écartement

    /**
     * Écarte un signalement, avec motif obligatoire.
     *
     * <p>Un seul écartement par signalement, par <strong>qui agit le premier</strong> : si la PRMP l'a
     * écarté, le contrôleur <strong>lit son motif</strong> et, s'il n'en est pas convaincu, porte une
     * observation dans son examen — le circuit a déjà ce qu'il faut pour cela. Ré-écarter par-dessus
     * effacerait le motif de la PRMP, et « rien ne s'efface » (3.f, condition 3).</p>
     *
     * <p>Côté PRMP, l'écartement n'est plus possible une fois le plan <strong>soumis</strong> : les
     * signalements sont alors figés.</p>
     */
    public SignalementDto ecarter(Integer idSignalement, EcartementRequest requete) {
        Anomalie signalement = charger(idSignalement);
        Ppm ppm = exigerAccesAuPlan(signalement.getIdPpm());
        TypeActeur cote = coteActeur();

        if (!StatutSignalement.OUVERT.name().equals(signalement.getStatut())) {
            throw new BusinessRuleException(StatutSignalement.ECARTE.name().equals(signalement.getStatut())
                    ? "Ce signalement a déjà été écarté" + auteurLisible(signalement)
                            + " : son motif est « " + signalement.getCommentaireTraitement()
                            + " ». Un second écartement effacerait ce motif ; portez plutôt une "
                            + "observation dans votre examen si vous n'en êtes pas convaincu."
                    : "Ce signalement ne ressort plus du plan (levé par modification) : il n'y a plus rien "
                            + "à écarter.");
        }
        if (cote == TypeActeur.PRMP && signalement.getFige()) {
            throw new BusinessRuleException("Le plan est soumis : les écartements sont figés et ne peuvent "
                    + "plus être modifiés. C'est ce qui leur donne leur valeur devant le contrôleur.");
        }

        signalement.setStatut(StatutSignalement.ECARTE.name());
        signalement.setTypeActeurTraitement(cote.name());
        signalement.setImTraitement(refActeur());
        signalement.setDateTraitement(LocalDateTime.now());
        signalement.setCommentaireTraitement(requete.motif().trim());
        anomalieRepository.save(signalement);
        return versDto(signalement, contexteDeLecture(ppm), vuePrmp());
    }

    /**
     * Reprend son propre écartement — <strong>seul son auteur</strong> peut le faire, et seulement tant que
     * le plan n'est pas soumis : après, l'écartement est figé.
     *
     * <p>Le motif est alors effacé, et c'est volontaire : avant la soumission, rien n'a été transmis à
     * personne, et une PRMP qui se corrige ne doit pas traîner un brouillon d'écartement. « Rien ne
     * s'efface » vaut à partir du moment où le dossier part — c'est exactement ce que {@code FIGE}
     * matérialise.</p>
     */
    public SignalementDto reprendre(Integer idSignalement) {
        Anomalie signalement = charger(idSignalement);
        Ppm ppm = exigerAccesAuPlan(signalement.getIdPpm());

        if (!StatutSignalement.ECARTE.name().equals(signalement.getStatut())) {
            throw new BusinessRuleException("Ce signalement n'est pas écarté : il n'y a rien à reprendre.");
        }
        if (signalement.getFige()) {
            throw new BusinessRuleException("Le plan est soumis : cet écartement est figé.");
        }
        String moi = refActeur();
        if (signalement.getImTraitement() != null && !signalement.getImTraitement().equals(moi)) {
            throw new AccessDeniedException("Cet écartement est celui d'un autre acteur : seul son auteur "
                    + "peut le reprendre.");
        }

        signalement.setStatut(StatutSignalement.OUVERT.name());
        signalement.setTypeActeurTraitement(null);
        signalement.setImTraitement(null);
        signalement.setDateTraitement(null);
        signalement.setCommentaireTraitement(null);
        anomalieRepository.save(signalement);
        return versDto(signalement, contexteDeLecture(ppm), vuePrmp());
    }

    // ------------------------------------------------------------------ gardes

    /**
     * Charge le plan <strong>et</strong> vérifie que l'utilisateur courant a le droit de le voir. Les deux
     * ensemble, délibérément : il ne doit pas être possible d'obtenir un plan sans être passé par la garde.
     */
    private Ppm exigerAccesAuPlan(Integer idPpm) {
        Ppm ppm = ppmRepository.findById(idPpm)
                .orElseThrow(() -> new ResourceNotFoundException("PPM introuvable : " + idPpm));
        Dossier dossier = dossierRepository.findById(ppm.getIdDossier())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + ppm.getIdDossier()));
        if (vuePrmp()) {
            dossierIntegrite.exigerProprietaire(dossier);
            return ppm;
        }
        if (CurrentUser.voitToutesLocalites()) {
            return ppm;   // Président : aucune localité au jeton = toutes les localités
        }
        String maLocalite = CurrentUser.localite().orElse(null);
        String saLocalite = dossier.getIdLocalite() != null ? dossier.getIdLocalite() : ppm.getIdLocalite();
        if (maLocalite != null && maLocalite.equals(saLocalite)) {
            return ppm;
        }
        throw new AccessDeniedException(
                "Ce plan relève d'une autre commission que la vôtre : vous n'y avez pas accès (§1.1).");
    }

    /** Vrai si l'utilisateur courant est du côté PRMP (PRMP titulaire ou agent de son UGPM). */
    private static boolean vuePrmp() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        return profil == ProfilUtilisateur.PRMP || profil == ProfilUtilisateur.UGPM;
    }

    /**
     * Côté auquel l'écartement sera imputé. Un agent d'UGPM est enregistré comme sa <strong>PRMP de
     * tutelle</strong> : c'est l'{@code ID_PRMP} de la tutelle que porte son jeton, et son périmètre est
     * celui de sa tutelle.
     */
    private static TypeActeur coteActeur() {
        return vuePrmp() ? TypeActeur.PRMP : TypeActeur.CONTROLEUR;
    }

    /** Référence de l'acteur courant : {@code IM_CONTROLEUR} ou {@code ID_PRMP} (claim {@code ref}). */
    private static String refActeur() {
        return CurrentUser.ref().filter(r -> !r.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Acteur non identifié : jeton sans référence."));
    }

    private Anomalie charger(Integer idSignalement) {
        Anomalie signalement = anomalieRepository.findById(idSignalement)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Signalement introuvable : " + idSignalement));
        if (signalement.getIdPpm() == null) {
            throw new BusinessRuleException("Ce signalement n'est rattaché à aucun plan : il ne relève pas "
                    + "du pré-contrôle du PPM.");
        }
        return signalement;
    }

    /** « par la PRMP » / « par le contrôle », pour une phrase lisible dans un refus. */
    private static String auteurLisible(Anomalie signalement) {
        if (TypeActeur.PRMP.name().equals(signalement.getTypeActeurTraitement())) {
            return " par la PRMP";
        }
        if (TypeActeur.CONTROLEUR.name().equals(signalement.getTypeActeurTraitement())) {
            return " par le contrôle";
        }
        return "";
    }

    // ------------------------------------------------------------------ mise en forme

    /**
     * Ce qu'il faut pour habiller les signalements d'un plan : désignations des lignes, libellés des règles
     * et des points de grille. Chargé une fois — un plan peut porter des dizaines de signalements.
     */
    private record ContexteLecture(Map<Integer, String> designations, Map<Integer, String> libellesRegles,
            Map<Integer, String> libellesPoints, Map<Integer, List<AnomalieLigne>> lignesVisees) {
    }

    private ContexteLecture contexteDeLecture(Ppm ppm) {
        Map<Integer, String> designations = new HashMap<>();
        for (Marche m : marcheRepository.findByIdPpm(ppm.getIdPpm())) {
            designations.put(m.getIdDetail(),
                    m.getDesignationMarche() == null ? "" : m.getDesignationMarche().trim());
        }
        Map<Integer, String> regles = new HashMap<>();
        for (RegleAnomalie r : regleAnomalieRepository.findAll()) {
            regles.put(r.getIdRegleAnomalie(), r.getLibelle());
        }
        Map<Integer, String> points = new HashMap<>();
        for (PointsCtrl p : pointsCtrlRepository.findAll()) {
            points.put(p.getIdPointCtrl(), p.getLibelPointCtrl());
        }
        List<Integer> ids = anomalieRepository.findByIdPpmOrderByIdAnomalie(ppm.getIdPpm()).stream()
                .map(Anomalie::getIdAnomalie).toList();
        Map<Integer, List<AnomalieLigne>> visees = ids.isEmpty() ? Map.of()
                : anomalieLigneRepository.findByIdAnomalieIn(ids).stream()
                        .collect(java.util.stream.Collectors.groupingBy(AnomalieLigne::getIdAnomalie));
        return new ContexteLecture(designations, regles, points, visees);
    }

    /**
     * Le résumé d'un plan. Les signalements sont triés comme l'écran les lit : <strong>ouverts d'abord,
     * prioritaires en tête</strong> — la hiérarchisation est ce qui rend l'outil utile sur un plan de 120
     * lignes, et elle ne doit pas dépendre de l'écran.
     */
    private ResumePreControleDto resume(Ppm ppm) {
        ContexteLecture contexte = contexteDeLecture(ppm);
        boolean vuePrmp = vuePrmp();
        List<SignalementDto> signalements = anomalieRepository
                .findByIdPpmOrderByIdAnomalie(ppm.getIdPpm()).stream()
                .map(a -> versDto(a, contexte, vuePrmp))
                .sorted(Comparator
                        .comparingInt(SignalementPreControleService::rangStatut)
                        .thenComparingInt(SignalementPreControleService::rangGravite)
                        .thenComparing(SignalementDto::id))
                .toList();
        int ouverts = (int) signalements.stream()
                .filter(s -> StatutSignalement.OUVERT.name().equals(s.statut())).count();
        int prioritaires = (int) signalements.stream()
                .filter(s -> StatutSignalement.OUVERT.name().equals(s.statut()))
                .filter(s -> GraviteSignalement.PRIORITAIRE.name().equals(s.gravite())).count();
        int ecartes = (int) signalements.stream()
                .filter(s -> StatutSignalement.ECARTE.name().equals(s.statut())).count();
        int leves = (int) signalements.stream()
                .filter(s -> StatutSignalement.LEVE_MODIFICATION.name().equals(s.statut())).count();
        return new ResumePreControleDto(ppm.getIdPpm(), ppm.getExercice(), LocalDateTime.now(),
                ouverts, prioritaires, ecartes, leves, signalements);
    }

    private static int rangStatut(SignalementDto s) {
        if (StatutSignalement.OUVERT.name().equals(s.statut())) {
            return 0;
        }
        return StatutSignalement.ECARTE.name().equals(s.statut()) ? 1 : 2;
    }

    private static int rangGravite(SignalementDto s) {
        return GraviteSignalement.PRIORITAIRE.name().equals(s.gravite()) ? 0 : 1;
    }

    /**
     * Un signalement en DTO. {@code vuePrmp} commande la seule différence de contenu entre les deux
     * côtés : l'écartement d'un contrôleur n'est pas servi à la PRMP, et le statut qu'elle lit reste
     * « ouvert » — l'appréciation du contrôle se dit dans le PV.
     */
    private SignalementDto versDto(Anomalie a, ContexteLecture contexte, boolean vuePrmp) {
        boolean ecartementDuControle = TypeActeur.CONTROLEUR.name().equals(a.getTypeActeurTraitement());
        boolean masquer = vuePrmp && ecartementDuControle;
        SignalementDto.EcartementDto ecartement = masquer || a.getCommentaireTraitement() == null
                || !StatutSignalement.ECARTE.name().equals(a.getStatut())
                        ? null
                        : new SignalementDto.EcartementDto(a.getTypeActeurTraitement(), a.getImTraitement(),
                                a.getDateTraitement(), a.getCommentaireTraitement());
        String statut = masquer ? StatutSignalement.OUVERT.name() : a.getStatut();
        List<SignalementDto.LigneViseeDto> lignes = contexte.lignesVisees()
                .getOrDefault(a.getIdAnomalie(), List.of()).stream()
                .sorted(Comparator.comparing(AnomalieLigne::getIdDetail))
                .map(l -> new SignalementDto.LigneViseeDto(l.getIdDetail(),
                        contexte.designations().getOrDefault(l.getIdDetail(), ""), l.getMontant()))
                .toList();
        return new SignalementDto(a.getIdAnomalie(), a.getTypeAnomalie(),
                Optional.ofNullable(a.getIdRegleAnomalie()).map(contexte.libellesRegles()::get).orElse(null),
                a.getGravite(),
                a.getSource() == null ? SourceSignalement.REGLE.name() : a.getSource(),
                statut, a.getDescription(), a.getSuggestion(), a.getIdDetail(),
                a.getIdDetail() == null ? null : contexte.designations().get(a.getIdDetail()),
                lignes,
                a.getIdPointCtrl(),
                a.getIdPointCtrl() == null ? null : contexte.libellesPoints().get(a.getIdPointCtrl()),
                a.getDateDetection(), ecartement, a.getFige(), a.getDateLevee(), a.getDetailLevee());
    }
}
