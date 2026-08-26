package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.ServletWebRequest;

import cnm.prs.entity.Dossier;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.GlobalExceptionHandler;
import cnm.prs.repository.DossierRepository;

/**
 * Verrou optimiste (⚠️ LOT 4 — 2026-08-26, migration V6) : une écriture concurrente perdante
 * est refusée (409) au lieu d'écraser silencieusement la précédente.
 *
 * <p><b>Pourquoi ce test n'est PAS un test HTTP.</b> Par l'API, les DTO <em>ne portent pas</em>
 * le numéro de version : le client envoie un PUT sans version, et tous les services du circuit
 * mettent à jour en « charger-puis-modifier » sur une entité <em>managée</em> (jamais un
 * {@code save()} d'entité reconstruite depuis le DTO). Deux PUT séquentiels sur la même
 * ressource ne déclenchent donc jamais le conflit : chacun recharge la version courante.
 * C'est une <b>limite assumée</b> du dispositif — le verrou protège de l'entrelacement de deux
 * <em>transactions</em>, pas de deux formulaires ouverts côté navigateur (protéger ce cas-là
 * demanderait de faire transiter la version dans les DTO, hors périmètre de ce lot).</p>
 *
 * <p><b>Ce qui est donc vérifié</b> : le comportement transactionnel réel, avec deux
 * transactions distinctes sur la même ligne — la seconde à partir d'un état périmé échoue.
 * Cette classe n'est <b>pas</b> {@code @Transactional} : chaque étape doit COMMITTER pour que
 * la suivante voie l'écriture de la précédente (sous une transaction de test unique, les deux
 * « transactions » partageraient le même contexte de persistance et le conflit n'existerait
 * pas). Le nettoyage est donc explicite.</p>
 */
@SpringBootTest
class VerrouOptimisteIntegrationTest extends AbstractIntegrationTest {

    /** Les six entités chaudes du circuit portées au verrou par la migration V6. */
    private static final List<String> TABLES_VERROUILLEES = List.of(
            "t_dossier", "t_ppm", "t_marche", "t_pv_examen", "t_lettre_renvoi", "t_demande_retrait");

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private TransactionTemplate tx;

    private Integer idDossierTest;

    @Autowired
    void construireTemplate(PlatformTransactionManager gestionnaire) {
        // REQUIRES_NEW : chaque étape s'exécute et COMMITTE dans sa propre transaction.
        tx = new TransactionTemplate(gestionnaire);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @AfterEach
    void nettoyer() {
        // Pas de rollback automatique ici (classe non @Transactional) : la ligne créée doit partir.
        if (idDossierTest != null) {
            tx.executeWithoutResult(s -> dossierRepository.deleteById(idDossierTest));
            idDossierTest = null;
        }
    }

    @Test
    @DisplayName("Verrou optimiste : deux transactions sur la même ligne, la seconde partant d'un "
            + "état périmé lève ObjectOptimisticLockingFailureException")
    void ecritureConcurrente_surEtatPerime_estRefusee() {
        // — Étape 0 : une ligne neuve, committée. VERSION vaut 0 (défaut de la migration V6).
        idDossierTest = tx.execute(s -> {
            Dossier d = new Dossier();
            d.setIdDossier(dossierRepository.nextIdDossier().intValue());
            d.setStatut("BROUILLON");
            return dossierRepository.save(d).getIdDossier();
        });
        assertThat(versionEnBase(idDossierTest)).isZero();

        // — Étape 1 : la transaction A charge la ligne. L'entité rendue est DÉTACHÉE au commit :
        //   elle fige la version 0, exactement comme un acteur qui a ouvert le dossier.
        Dossier vuParA = tx.execute(s -> dossierRepository.findById(idDossierTest).orElseThrow());
        assertThat(vuParA.getVersion()).isZero();

        // — Étape 2 : la transaction B, entre-temps, modifie et COMMITTE la même ligne. Hibernate
        //   incrémente VERSION : 0 → 1. A ne le sait pas.
        tx.executeWithoutResult(s -> dossierRepository.findById(idDossierTest).orElseThrow()
                .setStatut("SOUMIS"));
        assertThat(versionEnBase(idDossierTest)).isOne();

        // — Étape 3 : A enregistre son état périmé. L'UPDATE porte « WHERE VERSION = 0 », ne touche
        //   aucune ligne, et Hibernate le signale au lieu d'écraser le travail de B.
        vuParA.setStatut("RETIRE");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> dossierRepository.saveAndFlush(vuParA)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // — L'écriture de B a survécu : c'est tout l'objet du verrou (sans lui, « RETIRE » l'aurait écrasée).
        assertThat(jdbc.queryForObject(
                "SELECT \"STATUT\" FROM public.t_dossier WHERE \"ID_DOSSIER\" = ?", String.class, idDossierTest))
                .isEqualTo("SOUMIS");
    }

    @Test
    @DisplayName("Migration V6 : les six tables du circuit portent VERSION integer NOT NULL DEFAULT 0")
    void colonneVersion_presenteSurLesSixTables() {
        for (String table : TABLES_VERROUILLEES) {
            List<java.util.Map<String, Object>> colonnes = jdbc.queryForList(
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

    @Test
    @DisplayName("Handler : un conflit de verrou optimiste est rendu en 409, pas en 500")
    void handler_conflitVersion_rendu409() {
        ServletWebRequest requete = new ServletWebRequest(
                new MockHttpServletRequest("PUT", "/api/dossiers/42"));

        var reponse = new GlobalExceptionHandler().handleConflitVersion(
                new ObjectOptimisticLockingFailureException(Dossier.class, 42), requete);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse corps = reponse.getBody();
        assertThat(corps).isNotNull();
        assertThat(corps.message())
                .isEqualTo("La donnée a été modifiée par une autre opération entre-temps. Rechargez puis réessayez.");
    }

    /** Version telle qu'elle est RÉELLEMENT en base (hors cache de persistance). */
    private Integer versionEnBase(Integer idDossier) {
        return jdbc.queryForObject(
                "SELECT \"VERSION\" FROM public.t_dossier WHERE \"ID_DOSSIER\" = ?", Integer.class, idDossier);
    }
}
