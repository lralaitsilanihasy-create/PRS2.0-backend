package cnm.prs;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Socle commun des tests d'intégration (chantier LOT 2, 2026-08-26 — modèle : dépôt Collegue) :
 * UN SEUL conteneur PostgreSQL 17 pour toute la JVM de test (pattern « singleton container »).
 *
 * <p><b>Pourquoi plus H2</b> : la suite validait le code Java, pas le schéma réel — ni les
 * contraintes CHECK des enums, ni les séquences, ni les types PostgreSQL (l'INIT de test créait
 * des domaines de compatibilité). Le schéma est désormais rejoué par <b>Flyway</b> (V1 baseline
 * issue de pg_dump + reprises) sur un vrai PostgreSQL — même moteur et même version qu'en
 * production (conteneur {@code prs20-db} : PostgreSQL 17).</p>
 *
 * <p><b>Démarrage</b> : conteneur {@code static} lancé dans un bloc statique — volontairement
 * SANS {@code @Testcontainers}/{@code @Container}, sinon l'extension JUnit l'arrêterait à la fin
 * de la première classe. Il vit toute la JVM ; le reaper Testcontainers (Ryuk) le supprime en fin
 * de JVM.</p>
 *
 * <p><b>Isolation</b> : les tests restent {@code @Transactional} (annulation après chaque test) —
 * une seule base partagée suffit, Flyway ne la migre qu'une fois par contexte Spring (les
 * exécutions suivantes ne trouvent rien à rejouer).</p>
 */
public abstract class AbstractIntegrationTest {

    /** Conteneur PostgreSQL partagé par toute la suite (une seule instance par JVM de test). */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void proprietesBase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
