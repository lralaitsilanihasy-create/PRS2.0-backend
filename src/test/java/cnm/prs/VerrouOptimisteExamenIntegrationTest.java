package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import cnm.prs.entity.ExamenDetail;
import cnm.prs.repository.ExamenDetailRepository;

/**
 * Verrou optimiste de l'EXAMEN (⚠️ audit 2026-08-27, lot D §7, migration V9) — prolongement de
 * {@link VerrouOptimisteIntegrationTest}, qui couvre les six entités de la V6.
 *
 * <p>L'examen est l'écriture la plus concurrente du circuit : il se remplit <b>point de contrôle par
 * point de contrôle</b>, et à plusieurs mains (Membre attributaire, Chef de commission, Président).
 * C'est exactement le motif « je charge, je réfléchis, j'enregistre » où deux enregistrements
 * successifs se recouvrent sans que personne ne l'apprenne : en dernier-écrit-gagne, un point déclaré
 * <b>non conforme</b> redevient conforme sans laisser de trace.</p>
 *
 * <p><b>Pourquoi ce test n'est PAS un test HTTP</b> (même raison qu'en V6) : il vérifie le chemin
 * <b>transactionnel</b>, deux transactions distinctes s'entrelaçant sur la même ligne — ce que MockMvc,
 * mono-thread, ne sait pas reproduire. Et contrairement aux cinq DTO du chantier « conflit de version »,
 * le contrat HTTP de l'examen est <b>délibérément inchangé</b> : ni {@code ExamenDto} ni
 * {@code ExamenDetailDto} ne portent de champ {@code version}, le client n'a rien à envoyer. Il n'y a
 * donc rien à tester côté HTTP — la protection joue au seul niveau de l'entrelacement.</p>
 *
 * <p>Cette classe n'est pas {@code @Transactional} : chaque étape doit COMMITTER pour que la suivante
 * voie l'écriture de la précédente. Le nettoyage est explicite.</p>
 */
@SpringBootTest
class VerrouOptimisteExamenIntegrationTest extends AbstractIntegrationTest {

    /** Les deux entités de l'examen portées au verrou par la migration V9. */
    private static final List<String> TABLES_VERROUILLEES = List.of("t_examen", "t_examen_detail");

    /** Identifiants hors des plages utilisées par les fixtures des autres classes. */
    private static final int ID_EXAMEN = 99_100;
    private static final int ID_DETAIL_EXAMEN = 99_101;
    private static final int ID_PT_CONTROLE = 99_102;

    @Autowired private ExamenDetailRepository examenDetailRepository;
    @Autowired private cnm.prs.repository.ExamenRepository examenRepository;
    @Autowired private cnm.prs.repository.PointsCtrlRepository pointsCtrlRepository;
    @Autowired private cnm.prs.repository.DispatchRepository dispatchRepository;
    @Autowired private cnm.prs.repository.ReceptionRepository receptionRepository;
    @Autowired private cnm.prs.repository.DossierRepository dossierRepository;
    @Autowired private cnm.prs.repository.TypeDossierRepository typeDossierRepository;
    @Autowired private JdbcTemplate jdbc;

    /** Famille de dossier propre à cette classe (tr_points_ctrl.ID_TYPE_DOSSIER est NOT NULL + FK). */
    private static final String TYPE_DOSSIER = "VLD9";

    private TransactionTemplate tx;

    @Autowired
    void construireTemplate(PlatformTransactionManager gestionnaire) {
        // REQUIRES_NEW : chaque étape s'exécute et COMMITTE dans sa propre transaction.
        tx = new TransactionTemplate(gestionnaire);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @AfterEach
    void nettoyer() {
        // Pas de rollback automatique ici (classe non @Transactional) : les lignes créées doivent partir.
        tx.executeWithoutResult(s -> {
            examenDetailRepository.deleteById(ID_DETAIL_EXAMEN);
            examenRepository.deleteById(ID_EXAMEN);
            dispatchRepository.deleteById(ID_EXAMEN);
            receptionRepository.deleteById(ID_EXAMEN);
            dossierRepository.deleteById(ID_EXAMEN);
            pointsCtrlRepository.deleteById(ID_PT_CONTROLE);
            typeDossierRepository.deleteById(TYPE_DOSSIER);
        });
    }

    @Test
    @DisplayName("Verrou optimiste de l'examen : deux transactions sur le même détail, la seconde "
            + "partant d'un état périmé lève ObjectOptimisticLockingFailureException")
    void saisieConcurrente_surDetailPerime_estRefusee() {
        // — Étape 0 : la chaîne FK minimale (dossier → réception → dispatch → examen) puis un détail
        //   d'examen neuf, committé. VERSION vaut 0 (défaut de la migration V9).
        tx.executeWithoutResult(s -> {
            cnm.prs.entity.Dossier d = new cnm.prs.entity.Dossier();
            d.setIdDossier(ID_EXAMEN);
            d.setStatut("EXAMINE");
            dossierRepository.save(d);
            cnm.prs.entity.Reception r = new cnm.prs.entity.Reception();
            r.setIdReception(ID_EXAMEN);
            r.setIdDossier(ID_EXAMEN);
            r.setNumPassage(1);
            r.setTypePassage("INITIAL");
            r.setComplet(true);
            receptionRepository.save(r);
            cnm.prs.entity.Dispatch di = new cnm.prs.entity.Dispatch();
            di.setIdDispatch(ID_EXAMEN);
            di.setIdReception(ID_EXAMEN);
            di.setInterimDispatch(false);
            dispatchRepository.save(di);
            cnm.prs.entity.Examen e = new cnm.prs.entity.Examen();
            e.setIdExamen(ID_EXAMEN);
            e.setIdDispatch(ID_EXAMEN);
            e.setDateExamen(LocalDate.of(2026, 6, 4));
            examenRepository.save(e);
            typeDossierRepository.save(new cnm.prs.entity.TypeDossier(TYPE_DOSSIER, "Famille du test verrou"));
            cnm.prs.entity.PointsCtrl pc = new cnm.prs.entity.PointsCtrl();
            pc.setIdPointCtrl(ID_PT_CONTROLE);
            pc.setLibelPointCtrl("Montant estimatif");
            pc.setObligatoire(true);
            pc.setIdTypeDossier(TYPE_DOSSIER);
            pointsCtrlRepository.save(pc);
            ExamenDetail ed = new ExamenDetail();
            ed.setIdDetailExamen(ID_DETAIL_EXAMEN);
            ed.setIdExamen(ID_EXAMEN);
            ed.setIdPtControle(ID_PT_CONTROLE);
            ed.setConforme(false);
            ed.setObsSiNonConforme("Montant hors seuil");
            examenDetailRepository.save(ed);
        });
        assertThat(versionDuDetail()).isZero();

        // — Étape 1 : le Membre charge le point de contrôle. L'entité rendue est DÉTACHÉE au commit :
        //   elle fige la version 0, exactement comme un formulaire ouvert.
        ExamenDetail vuParLeMembre = tx.execute(s -> examenDetailRepository.findById(ID_DETAIL_EXAMEN).orElseThrow());
        assertThat(vuParLeMembre.getVersion()).isZero();

        // — Étape 2 : le Chef de commission, entre-temps, tranche le même point et COMMITTE.
        tx.executeWithoutResult(s -> {
            ExamenDetail vuParLeCc = examenDetailRepository.findById(ID_DETAIL_EXAMEN).orElseThrow();
            vuParLeCc.setConforme(true);
            vuParLeCc.setObsSiNonConforme(null);
        });
        assertThat(versionDuDetail()).isOne();

        // — Étape 3 : le Membre enregistre son état périmé. L'UPDATE porte « WHERE VERSION = 0 », ne
        //   touche aucune ligne, et Hibernate le signale au lieu d'écraser l'arbitrage du CC.
        vuParLeMembre.setObsSiNonConforme("Montant hors seuil (confirme)");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> examenDetailRepository.saveAndFlush(vuParLeMembre)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // — L'arbitrage du CC a survécu : c'est tout l'objet du verrou.
        assertThat(jdbc.queryForObject(
                "SELECT \"CONFORME\" FROM public.t_examen_detail WHERE \"ID_DETAIL_EXAMEN\" = ?",
                Boolean.class, ID_DETAIL_EXAMEN)).isTrue();
    }

    @Test
    @DisplayName("Migration V9 : t_examen et t_examen_detail portent VERSION integer NOT NULL DEFAULT 0")
    void colonneVersion_presenteSurLesDeuxTables() {
        for (String table : TABLES_VERROUILLEES) {
            List<Map<String, Object>> colonnes = jdbc.queryForList(
                    "SELECT data_type, is_nullable, column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ? AND column_name = 'VERSION'",
                    table);
            assertThat(colonnes).as("colonne VERSION sur %s", table).hasSize(1);
            assertThat(colonnes.get(0).get("data_type")).as("type sur %s", table).isEqualTo("integer");
            // NOT NULL DEFAULT 0 : une version NULL sur l'existant casserait Hibernate.
            assertThat(colonnes.get(0).get("is_nullable")).as("nullabilité sur %s", table).isEqualTo("NO");
            assertThat(String.valueOf(colonnes.get(0).get("column_default"))).as("défaut sur %s", table)
                    .startsWith("0");
        }
    }

    /** Version telle qu'elle est RÉELLEMENT en base (hors cache de persistance). */
    private Integer versionDuDetail() {
        return jdbc.queryForObject(
                "SELECT \"VERSION\" FROM public.t_examen_detail WHERE \"ID_DETAIL_EXAMEN\" = ?",
                Integer.class, ID_DETAIL_EXAMEN);
    }
}
