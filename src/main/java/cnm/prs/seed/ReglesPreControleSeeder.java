package cnm.prs.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.ModePassation;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.enums.ProcedureAttendue;
import cnm.prs.enums.TypeSignalement;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.repository.TypeDossierRepository;
import cnm.prs.service.ClePrimaire;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2) — sème les <strong>trois données de
 * référentiel</strong> dont le moteur de règles a besoin.
 *
 * <ol>
 *   <li><strong>Une ligne de {@code t_regle_anomalie} par règle.</strong> C'est elle qui porte l'{@code
 *       ACTIF} : une règle s'éteint depuis l'administration, <strong>sans redéploiement</strong>. C'est
 *       toute la réponse à la fatigue d'alerte (plan, 3.e) — une règle écartée dans 80 % des cas est une
 *       mauvaise règle. C'est aussi une clé étrangère obligatoire de {@code t_anomalie} : sans ces lignes,
 *       aucun signalement ne peut s'enregistrer.</li>
 *   <li><strong>Le point de contrôle « Fractionnement illicite ».</strong> La grille du PPM n'en avait
 *       aucun, alors que le manuel en fait un point de vérification à part entière. De portée
 *       {@code DOSSIER}, puisqu'il concerne plusieurs lignes.</li>
 *   <li><strong>Le palier de l'arrêté de chaque mode de passation</strong> ({@code PROCEDURE_SEUIL},
 *       V33), déduit du libellé pour les modes qui n'en portent pas encore.</li>
 * </ol>
 *
 * <p><strong>Pourquoi un seeder et non des INSERT dans la migration</strong> : {@code tr_points_ctrl}
 * porte deux clés étrangères vers des référentiels qu'aucune migration ne crée (voir l'encadré de V16), et
 * le libellé d'une règle doit rester <strong>ajustable</strong> sans qu'un redémarrage l'écrase.</p>
 *
 * <p><strong>Idempotent et non intrusif</strong>, comme {@link PointsCtrlFicheAgpmSeeder} : il crée ce qui
 * manque et ne touche <strong>jamais</strong> ce qui existe — un libellé réécrit, une règle que
 * l'Administrateur a éteinte ou un palier qu'il a corrigé survivent aux redémarrages. Désactivable avec
 * {@code app.seed.regles-pre-controle.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed.regles-pre-controle.enabled", havingValue = "true",
        matchIfMissing = true)
public class ReglesPreControleSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReglesPreControleSeeder.class);

    /** Famille des dossiers de planification — celle dont la grille porte les points du PPM. */
    private static final String FAMILLE_DDP = "DDP";

    /** Le point que la grille du PPM n'avait pas, alors que le manuel le vérifie (manuel, p. 15). */
    private static final String LIBELLE_FRACTIONNEMENT =
            TypeSignalement.PointDeGrille.FRACTIONNEMENT.libelle();

    private static final String DESCRIPTION_FRACTIONNEMENT =
            "Plusieurs prestations identiques d'un même compte, même financement et même forme de marché : "
                    + "y a-t-il lieu d'exiger de les fusionner, et éventuellement de les allotir ? "
                    + "(articles 27 et 28 du code des marchés publics)";

    private final RegleAnomalieRepository regleAnomalieRepository;
    private final PointsCtrlRepository pointsCtrlRepository;
    private final TypeDossierRepository typeDossierRepository;
    private final ModePassationRepository modePassationRepository;

    public ReglesPreControleSeeder(RegleAnomalieRepository regleAnomalieRepository,
            PointsCtrlRepository pointsCtrlRepository, TypeDossierRepository typeDossierRepository,
            ModePassationRepository modePassationRepository) {
        this.regleAnomalieRepository = regleAnomalieRepository;
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.typeDossierRepository = typeDossierRepository;
        this.modePassationRepository = modePassationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        semerRegles();
        semerPointFractionnement();
        classerLesModes();
    }

    /** Une ligne par règle, active par défaut, avec sa gravité par défaut. Jamais réécrite. */
    private void semerRegles() {
        int crees = 0;
        for (TypeSignalement type : TypeSignalement.values()) {
            if (regleAnomalieRepository.findByCodeRegle(type.name()).isPresent()) {
                continue;
            }
            RegleAnomalie regle = new RegleAnomalie();
            regle.setIdRegleAnomalie(ClePrimaire.allouerLibre(regleAnomalieRepository::existsById,
                    regleAnomalieRepository::nextIdRegleAnomalie));
            regle.setCodeRegle(type.name());
            regle.setLibelle(type.libelle());
            regle.setActif(Boolean.TRUE);
            regle.setGraviteDefaut(type.graviteDefaut().name());
            regleAnomalieRepository.save(regle);
            crees++;
        }
        if (crees > 0) {
            log.info("[SEED] règles de pré-contrôle du PPM créées : {}", crees);
        }
    }

    /**
     * Le point « Fractionnement illicite », de portée {@code DOSSIER} et commun à la famille DDP : le
     * fractionnement s'apprécie entre lignes, il ne peut pas être un point par ligne. S'abstient si la
     * famille manque encore au référentiel, plutôt que d'échouer au démarrage.
     */
    private void semerPointFractionnement() {
        if (!typeDossierRepository.existsById(FAMILLE_DDP)) {
            log.info("[SEED] point « {} » ignoré : la famille {} n'est pas encore au référentiel.",
                    LIBELLE_FRACTIONNEMENT, FAMILLE_DDP);
            return;
        }
        List<PointsCtrl> existants = pointsCtrlRepository.findAll();
        boolean dejaLa = existants.stream().anyMatch(p -> LIBELLE_FRACTIONNEMENT.equalsIgnoreCase(
                p.getLibelPointCtrl() == null ? "" : p.getLibelPointCtrl().trim()));
        if (dejaLa) {
            return;
        }
        int prochainOrdre = existants.stream().map(PointsCtrl::getOrdrePointCtrl)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        PointsCtrl point = new PointsCtrl();
        // Même précaution que le seeder FICHE/AGPM : la PK doit être LIBRE, l'écran d'administration
        // calculant la sienne sans consommer la séquence — un nextval nu écraserait un point existant.
        point.setIdPointCtrl(ClePrimaire.allouerLibre(pointsCtrlRepository::existsById,
                pointsCtrlRepository::nextIdPointCtrl));
        point.setLibelPointCtrl(LIBELLE_FRACTIONNEMENT);
        point.setDecriptPointCtrl(DESCRIPTION_FRACTIONNEMENT);
        point.setOrdrePointCtrl(prochainOrdre);
        point.setObligatoire(Boolean.TRUE);
        point.setIdTypeDossier(FAMILLE_DDP);
        point.setIdSousType(null);
        point.setPortee(PorteePointCtrl.DOSSIER);
        pointsCtrlRepository.save(point);
        log.info("[SEED] point de contrôle « {} » créé (portée DOSSIER).", LIBELLE_FRACTIONNEMENT);
    }

    /**
     * Pose le palier de l'arrêté sur les modes qui n'en portent pas, d'après leur libellé — exactement
     * comme {@code DECLENCHE_AGPM} est posé sur un mode créé par un import. Un libellé non reconnu laisse
     * la colonne vide : la règle des seuils restera muette pour ce mode, ce qui est le comportement sûr.
     */
    private void classerLesModes() {
        int classes = 0;
        for (ModePassation mode : modePassationRepository.findAll()) {
            if (mode.getProcedureSeuil() != null) {
                continue;   // valeur posée ici ou corrigée par l'Administrateur : intouchable
            }
            ProcedureAttendue palier = ModePassation.procedureSeuilDepuisLibelle(mode.getLibelle());
            if (palier == null) {
                continue;
            }
            mode.setProcedureSeuil(palier);
            modePassationRepository.save(mode);
            classes++;
        }
        if (classes > 0) {
            log.info("[SEED] modes de passation classés au barème de l'arrêté : {}", classes);
        }
    }
}
