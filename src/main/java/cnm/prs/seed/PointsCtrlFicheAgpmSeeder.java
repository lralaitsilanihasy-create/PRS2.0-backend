package cnm.prs.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.TypeDossierRepository;

/**
 * ⚠️ Règle du pilote (2026-09-02) — seed des <strong>six points de contrôle</strong> qui font entrer la
 * <strong>fiche de présentation</strong> et le <strong>projet d'AGPM</strong> dans l'examen de dossier.
 *
 * <h2>Pourquoi un seeder et non une migration SQL</h2>
 *
 * <p>{@code tr_points_ctrl} porte deux clés étrangères : {@code ID_TYPE_DOSSIER} vers
 * {@code tr_type_dossier} et {@code ID_SOUS_TYPE} vers {@code tr_sous_type_dossier}. Or <strong>aucune
 * migration ne crée ces référentiels</strong> — la baseline pose le schéma, pas les données. Un
 * {@code INSERT} de points depuis une migration échoue donc en 23503 sur toute base neuve, avant même
 * que l'application ait pu peupler ses référentiels. Le seed vit ici, au démarrage, sur le patron déjà
 * en place pour les délégations ({@link DelegationHierarchieSeeder}).</p>
 *
 * <h2>Rattachement : FICHE en commun, AGPM en spécifique</h2>
 *
 * <p>Les points {@code FICHE} sont <strong>communs à la famille DDP</strong> ({@code idSousType} nul).
 * La demande proposait de les attacher aux deux sous-types séparément, de crainte qu'un commun
 * « arrose DMC/DDM » ; vérification faite, la grille effective filtre <strong>déjà par famille</strong>,
 * et DDP ne contient exactement que {@code PPM} et {@code PPM-AGPM}. Un commun DDP atteint donc
 * précisément le besoin, sans imposer à l'Administrateur d'éditer deux lignes jumelles qui finiraient
 * par diverger.</p>
 *
 * <p>Les points {@code AGPM}, eux, restent <strong>spécifiques à {@code PPM-AGPM}</strong> : un plan sans
 * AGPM ne doit jamais voir cette grille.</p>
 *
 * <p><strong>Idempotent et non intrusif</strong> : crée un point absent (repéré par son libellé et sa
 * portée) ; ne touche <strong>jamais</strong> un point existant — un libellé ajusté par l'Administrateur,
 * ou un point qu'il aurait rendu facultatif, survit aux redémarrages. <strong>S'abstient</strong> si la
 * famille ou le sous-type référencé manque encore au référentiel, plutôt que d'échouer au démarrage.
 * Désactivable avec {@code app.seed.points-ctrl-fiche-agpm.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed.points-ctrl-fiche-agpm.enabled", havingValue = "true",
        matchIfMissing = true)
public class PointsCtrlFicheAgpmSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PointsCtrlFicheAgpmSeeder.class);

    /** Famille des dossiers de planification — la seule qui porte une fiche de présentation. */
    private static final String FAMILLE_DDP = "DDP";

    /** Sous-type qui, seul, comporte un projet d'AGPM. */
    private static final String SOUS_TYPE_AGPM = "PPM-AGPM";

    /** Les six points, dans l'ordre d'affichage. {@code sousType} nul = commun à la famille. */
    private static final List<Graine> GRAINES = List.of(
            new Graine("Listes de la fiche cohérentes avec le plan",
                    "Modes dérogatoires, délais aménagés et contrats-cadres : les trois listes de la fiche "
                            + "correspondent-elles aux marchés du plan ?",
                    PorteePointCtrl.FICHE, null),
            new Graine("Justifications par marché renseignées et recevables",
                    "Chaque marché dérogatoire porte-t-il la justification de son mode, et chaque marché à "
                            + "délai aménagé celle de son délai ? Sont-elles recevables sur le fond ?",
                    PorteePointCtrl.FICHE, null),
            new Graine("Justification globale de la fiche recevable",
                    "La « Justification : » du bas du formulaire couvre-t-elle les cas sans justification "
                            + "par ligne, à commencer par les contrats-cadres ?",
                    PorteePointCtrl.FICHE, null),
            new Graine("AGPM cohérent avec le PPM",
                    "Tous les marchés passés en appel d'offres figurent-ils à l'AGPM ?",
                    PorteePointCtrl.AGPM, SOUS_TYPE_AGPM),
            new Graine("Dates du DAO = dates prévisionnelles de lancement",
                    "Les dates annoncées au projet d'AGPM correspondent-elles aux dates de lancement du plan ?",
                    PorteePointCtrl.AGPM, SOUS_TYPE_AGPM),
            new Graine("Forme conforme au modèle officiel",
                    "Le projet d'AGPM respecte-t-il la forme du modèle officiel ?",
                    PorteePointCtrl.AGPM, SOUS_TYPE_AGPM));

    private final PointsCtrlRepository pointsCtrlRepository;
    private final TypeDossierRepository typeDossierRepository;
    private final SousTypeDossierRepository sousTypeDossierRepository;

    public PointsCtrlFicheAgpmSeeder(PointsCtrlRepository pointsCtrlRepository,
            TypeDossierRepository typeDossierRepository,
            SousTypeDossierRepository sousTypeDossierRepository) {
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.typeDossierRepository = typeDossierRepository;
        this.sousTypeDossierRepository = sousTypeDossierRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!typeDossierRepository.existsById(FAMILLE_DDP)) {
            log.info("[SEED] points FICHE/AGPM ignorés : la famille {} n'est pas encore au référentiel.",
                    FAMILLE_DDP);
            return;
        }
        boolean sousTypeAgpmConnu = sousTypeDossierRepository.existsById(SOUS_TYPE_AGPM);
        List<PointsCtrl> existants = pointsCtrlRepository.findAll();
        int prochainOrdre = existants.stream()
                .map(PointsCtrl::getOrdrePointCtrl).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        int crees = 0;
        for (Graine graine : GRAINES) {
            if (graine.sousType() != null && !sousTypeAgpmConnu) {
                continue;   // sous-type absent : on s'abstient plutôt que de violer la clé étrangère
            }
            if (existeDeja(existants, graine)) {
                continue;   // point déjà là (créé ici ou ajusté par l'Administrateur) : intouchable
            }
            PointsCtrl point = new PointsCtrl();
            point.setIdPointCtrl(pointsCtrlRepository.nextIdPointCtrl().intValue());
            point.setLibelPointCtrl(graine.libelle());
            point.setDecriptPointCtrl(graine.description());
            point.setOrdrePointCtrl(prochainOrdre++);
            point.setObligatoire(Boolean.TRUE);
            point.setIdTypeDossier(FAMILLE_DDP);
            point.setIdSousType(graine.sousType());
            point.setPortee(graine.portee());
            pointsCtrlRepository.save(point);
            crees++;
        }
        if (crees > 0) {
            log.info("[SEED] points de contrôle FICHE/AGPM créés : {}", crees);
        }
    }

    /** Un point est réputé déjà semé s'il porte le même libellé ET la même portée. */
    private static boolean existeDeja(List<PointsCtrl> existants, Graine graine) {
        return existants.stream().anyMatch(p -> graine.portee() == p.getPortee()
                && graine.libelle().equalsIgnoreCase(
                        p.getLibelPointCtrl() == null ? "" : p.getLibelPointCtrl().trim()));
    }

    /** Un point à semer. {@code sousType} nul = commun à la famille DDP. */
    private record Graine(String libelle, String description, PorteePointCtrl portee, String sousType) {
    }
}
