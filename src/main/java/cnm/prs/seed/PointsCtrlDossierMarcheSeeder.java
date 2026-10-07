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
import cnm.prs.repository.TypeDossierRepository;
import cnm.prs.service.ClePrimaire;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, lot 2, §B2.3) — la grille d'examen du <strong>dossier de marché</strong> (famille {@code DDM}),
 * tirée de la check-list du guide d'évaluation des offres (p. 8) : neuf points de portée {@code DOSSIER}, <strong>communs à la
 * famille</strong> ({@code MAOO} et {@code MAOR}), administrables ensuite. Même patron que {@link PointsCtrlFicheAgpmSeeder} : un
 * point absent (repéré par son libellé et sa portée) est créé sous une PK libre ; un point existant n'est jamais touché ; rien si la
 * famille manque au référentiel. Désactivable avec {@code app.seed.points-ctrl-dossier-marche.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.seed.points-ctrl-dossier-marche.enabled", havingValue = "true", matchIfMissing = true)
public class PointsCtrlDossierMarcheSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PointsCtrlDossierMarcheSeeder.class);

    static final String FAMILLE_DDM = "DDM";

    /** Les points semés, dans l'ordre d'affichage : {libellé, description}. */
    static final List<String[]> GRAINES = List.of(
            new String[] { "CAO désignée, membres déclarés",
                    "La commission d'appel d'offres a-t-elle été désignée par décision, et ses membres ont-ils signé une déclaration "
                            + "d'absence de conflit d'intérêts ?" },
            new String[] { "PV d'ouverture signé et publié",
                    "Le PV d'ouverture est-il signé et publié ? Les plis hors délai ont-ils été écartés sans ouverture ?" },
            new String[] { "Rejets motivés par le DAO", "Chaque rejet d'offre est-il motivé par une clause du DAO ?" },
            new String[] { "Montant évalué conforme au DAO",
                    "Les erreurs arithmétiques sont-elles corrigées selon les IC, et le montant évalué n'inclut-il que le rabais, la "
                            + "préférence et les critères prévus par le DAO ?" },
            new String[] { "Offres anormales : justification demandée",
                    "Aucune offre anormalement basse n'a-t-elle été rejetée sans demande écrite de justification ?" },
            new String[] { "Classement et attributaire proposé",
                    "Le classement suit-il le montant évalué croissant, et l'attributaire proposé est-il le mieux classé qualifié ?" },
            new String[] { "Post-qualification sur les seuls critères du DAO",
                    "La post-qualification n'a-t-elle retenu que les critères du DAO ?" },
            new String[] { "Rapport signé de tous les membres",
                    "Le rapport d'évaluation est-il signé de tous les membres, observations de désaccord comprises ?" },
            new String[] { "Projet de marché conforme à l'offre retenue",
                    "Le projet de marché reprend-il l'offre retenue sans modification substantielle ?" });

    private final PointsCtrlRepository pointsCtrlRepository;
    private final TypeDossierRepository typeDossierRepository;

    public PointsCtrlDossierMarcheSeeder(PointsCtrlRepository pointsCtrlRepository, TypeDossierRepository typeDossierRepository) {
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.typeDossierRepository = typeDossierRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        semer();
    }

    /** Sème ce qui manque ; rend le nombre de points créés. */
    @Transactional
    public int semer() {
        if (!typeDossierRepository.existsById(FAMILLE_DDM)) {
            log.info("[SEED] points du dossier de marché ignorés : la famille {} n'est pas encore au référentiel.", FAMILLE_DDM);
            return 0;
        }
        List<PointsCtrl> existants = pointsCtrlRepository.findAll();
        int prochainOrdre = existants.stream().map(PointsCtrl::getOrdrePointCtrl).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        int crees = 0;
        for (String[] g : GRAINES) {
            boolean deja = existants.stream().anyMatch(p -> FAMILLE_DDM.equals(p.getIdTypeDossier()) && p.getPortee() == PorteePointCtrl.DOSSIER
                    && g[0].equalsIgnoreCase(p.getLibelPointCtrl() == null ? "" : p.getLibelPointCtrl().trim()));
            if (deja) {
                continue;
            }
            PointsCtrl point = new PointsCtrl();
            point.setIdPointCtrl(ClePrimaire.allouerLibre(pointsCtrlRepository::existsById, pointsCtrlRepository::nextIdPointCtrl));
            point.setLibelPointCtrl(g[0]);
            point.setDecriptPointCtrl(g[1]);
            point.setOrdrePointCtrl(prochainOrdre++);
            point.setObligatoire(Boolean.TRUE);
            point.setIdTypeDossier(FAMILLE_DDM);
            point.setIdSousType(null);
            point.setPortee(PorteePointCtrl.DOSSIER);
            pointsCtrlRepository.save(point);
            crees++;
        }
        if (crees > 0) {
            log.info("[SEED] points de contrôle du dossier de marché créés : {}", crees);
        }
        return crees;
    }
}
