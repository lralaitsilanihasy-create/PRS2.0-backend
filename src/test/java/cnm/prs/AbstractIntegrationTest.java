package cnm.prs;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Socle commun des tests d'intégration (chantier LOT 2, 2026-08-26 — modèle : dépôt Collegue) :
 * UN SEUL conteneur PostgreSQL pour toute la JVM de test (pattern « singleton container »).
 * La version est celle de {@link #IMAGE_POSTGRES} — seul endroit où elle est déclarée.
 *
 * <p><b>Pourquoi plus H2</b> : la suite validait le code Java, pas le schéma réel — ni les
 * contraintes CHECK des enums, ni les séquences, ni les types PostgreSQL (l'INIT de test créait
 * des domaines de compatibilité). Le schéma est désormais rejoué par <b>Flyway</b> (V1 baseline
 * issue de pg_dump + reprises) sur un vrai PostgreSQL — même moteur et même version qu'en
 * production (conteneur {@code prs20-db}), la version étant portée par {@link #IMAGE_POSTGRES}.</p>
 *
 * <p><b>Démarrage</b> : conteneur {@code static} lancé dans un bloc statique — volontairement
 * SANS {@code @Testcontainers}/{@code @Container}, sinon l'extension JUnit l'arrêterait à la fin
 * de la première classe. Il vit toute la JVM ; le reaper Testcontainers (Ryuk) le supprime en fin
 * de JVM.</p>
 *
 * <p><b>Isolation</b> : les tests restent {@code @Transactional} (annulation après chaque test) —
 * une seule base partagée suffit, Flyway ne la migre qu'une fois par contexte Spring (les
 * exécutions suivantes ne trouvent rien à rejouer).</p>
 *
 * <h2>⚠️ Aiguillage vers un PostgreSQL local (dépannage, 2026-08-28)</h2>
 *
 * <p>Testcontainers exige un démon Docker. Sur un poste qui n'en a pas (Docker Desktop réclame
 * WSL2, donc une élévation et un redémarrage), la suite entière était inexécutable : les 39
 * classes héritent de ce socle. Définir <b>{@code PRS_TEST_DB_URL}</b> (variable d'environnement
 * ou propriété système {@code prs.test.db.url}) branche alors la suite sur un PostgreSQL déjà
 * installé, <b>sans démarrer de conteneur</b>. Sans cette variable, rien ne change : conteneur
 * comme avant. La CI ({@code .github/workflows/ci.yml}) ne la définit pas et reste donc sur
 * Testcontainers — le mode de référence.</p>
 *
 * <p><b>C'est un dépannage, pas une équivalence.</b> Une base locale n'est ni jetable ni forcément
 * à la version de {@link #IMAGE_POSTGRES} : on perd l'argument central de l'ADR-0004 (même moteur
 * et même version qu'en production). Un verdict de test obtenu par cette voie vaut pour le code
 * métier, pas pour la conformité du schéma. En cas de doute, l'arbitre reste la CI.</p>
 *
 * <p><b>Garde-fou</b> : la bascule <b>refuse</b> une base dont le nom ne se termine pas par
 * {@code _TEST}. Flyway migre ce qu'on lui donne et les tests y écrivent ; pointer la base de
 * développement par étourderie coûterait les données de développement. Créer la base dédiée :</p>
 *
 * <pre>{@code
 * createdb -U postgres DBPRS20_TEST
 * $env:PRS_TEST_DB_URL      = "jdbc:postgresql://localhost:5432/DBPRS20_TEST"
 * $env:PRS_TEST_DB_PASSWORD = $env:DB_PASSWORD
 * mvnw.cmd test
 * }</pre>
 *
 * <p>La base locale <b>persiste</b> entre deux exécutions, là où le conteneur repart à neuf.
 * Les tests étant {@code @Transactional}, ils annulent leurs écritures et la base reste
 * logiquement vide ; si une exécution interrompue y laisse des résidus, la remettre à plat par
 * un {@code dropdb} / {@code createdb} — Flyway rejouera V1 → V9 au démarrage suivant.</p>
 */
public abstract class AbstractIntegrationTest {

    /** URL JDBC d'un PostgreSQL déjà installé ; si absente, on démarre un conteneur. */
    private static final String URL_LOCALE = reglage("PRS_TEST_DB_URL", "prs.test.db.url");

    private static final String UTILISATEUR_LOCAL =
            valeurOuDefaut(reglage("PRS_TEST_DB_USERNAME", "prs.test.db.username"), "postgres");

    private static final String MOT_DE_PASSE_LOCAL =
            valeurOuDefaut(reglage("PRS_TEST_DB_PASSWORD", "prs.test.db.password"), "");

    /** Vrai quand la suite se branche sur un PostgreSQL local au lieu d'un conteneur. */
    static final boolean BASE_LOCALE = URL_LOCALE != null && !URL_LOCALE.isBlank();

    /**
     * Image du conteneur, pilotable par {@code PRS_TEST_PG_IMAGE} (ou {@code prs.test.pg.image}).
     *
     * <p>⚠️ 2026-08-28 — le défaut passe de {@code postgres:17} à {@code postgres:18}, <strong>version
     * de production confirmée par le pilote</strong>. C'est le seul point où la version est déclarée :
     * la surcharge {@code PRS_TEST_PG_IMAGE} posée dans le workflow a été retirée du même coup, une
     * version déclarée à deux endroits finissant toujours par diverger — ce qui venait précisément
     * d'arriver.</p>
     *
     * <p>Historique de la dérive, pour qu'elle ne se rejoue pas : l'ADR-0004 annonçait 16 dans son
     * titre, 17 dans son aboutissement, et justifiait le conteneur par « même version qu'en
     * production » sans que personne n'ait vérifié laquelle. La CI a ensuite été montée en 18 comme
     * <em>instrument de diagnostic</em> — deux tests de {@code MiseAJourPpmIntegrationTest}
     * répondaient 409 au lieu de 201. La version n'était pas en cause : le vrai coupable était Word
     * piloté en synchrone dans la transaction ({@code c2fdeb1}). Le passage en 18 reste néanmoins
     * juste, mais pour une autre raison que celle qui l'avait motivé.</p>
     *
     * <p>Le réglage reste surchargeable : il sert à reproduire un défaut sur une autre version, non
     * à porter la configuration de référence.</p>
     */
    private static final String IMAGE_POSTGRES =
            valeurOuDefaut(reglage("PRS_TEST_PG_IMAGE", "prs.test.pg.image"), "postgres:18");

    /** Conteneur partagé par toute la suite — {@code null} en mode base locale (jamais démarré). */
    static final PostgreSQLContainer<?> POSTGRES =
            BASE_LOCALE ? null : new PostgreSQLContainer<>(IMAGE_POSTGRES);

    static {
        if (BASE_LOCALE) {
            exigerBaseDeTest(URL_LOCALE);
            exigerMemeMajeure(URL_LOCALE);
        } else {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void proprietesBase(DynamicPropertyRegistry registry) {
        if (BASE_LOCALE) {
            registry.add("spring.datasource.url", () -> URL_LOCALE);
            registry.add("spring.datasource.username", () -> UTILISATEUR_LOCAL);
            registry.add("spring.datasource.password", () -> MOT_DE_PASSE_LOCAL);
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /** Propriété système d'abord (surchargeable en ligne de commande), puis variable d'environnement. */
    private static String reglage(String variableEnv, String proprieteSysteme) {
        String valeur = System.getProperty(proprieteSysteme);
        return valeur != null && !valeur.isBlank() ? valeur : System.getenv(variableEnv);
    }

    private static String valeurOuDefaut(String valeur, String defaut) {
        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }

    /**
     * ⚠️ Mode local officialisé (2ᵉ révision de l'ADR-0004, 2026-09-01) — le PostgreSQL du poste doit
     * être de la <strong>même majeure</strong> que {@link #IMAGE_POSTGRES}, sinon la suite s'arrête net.
     *
     * <p><strong>Pourquoi un garde-fou et pas une phrase.</strong> La contrainte aurait pu rester
     * documentée ; cette semaine a montré trois fois qu'une règle écrite sans exécutant dérive — la
     * version déclarée à trois endroits qui ont divergé, la clause « Docker partout » démentie dès le
     * premier poste, le 403 mué en 401 consigné en commentaire et jamais corrigé. Une majeure qui
     * s'écarte ne casse rien de visible : elle rend simplement le vert local moins probant que le vert
     * de la CI, silencieusement. C'est exactement le fail-fast de l'ADR-0002 : mieux vaut un arrêt franc
     * qu'un test qui passe pour de mauvaises raisons.</p>
     *
     * <p>Seule la MAJEURE est comparée : PostgreSQL garantit la compatibilité au sein d'une majeure, et
     * exiger 18.3 contre 18.4 ferait échouer pour un motif sans portée. Aucun chiffre n'est écrit ici —
     * la référence est lue dans {@link #IMAGE_POSTGRES}, conformément à la règle posée par l'ADR.</p>
     */
    private static void exigerMemeMajeure(String url) {
        String attendue = majeureDeLImage(IMAGE_POSTGRES);
        if (attendue == null) {
            return;   // image sans version explicite (« postgres ») : rien à comparer
        }
        String serveur;
        try (java.sql.Connection cnx = java.sql.DriverManager.getConnection(url, UTILISATEUR_LOCAL, MOT_DE_PASSE_LOCAL);
                java.sql.Statement st = cnx.createStatement();
                java.sql.ResultSet rs = st.executeQuery("select current_setting('server_version')")) {
            serveur = rs.next() ? rs.getString(1) : null;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("PRS_TEST_DB_URL : connexion impossible à « " + url + " » — "
                    + e.getMessage(), e);
        }
        String majeureServeur = serveur == null ? null : serveur.split("[.\\s]")[0];
        if (!attendue.equals(majeureServeur)) {
            throw new IllegalStateException("Le PostgreSQL du poste est en majeure " + majeureServeur
                    + " alors que la référence est " + IMAGE_POSTGRES + " (majeure " + attendue + "). "
                    + "Le mode local n'a d'intérêt que s'il teste le même moteur que la CI : sur une "
                    + "majeure différente, un vert local ne dit rien du vert de la CI. Installez la "
                    + "majeure attendue, ou lancez la suite sans PRS_TEST_DB_URL (Testcontainers).");
        }
    }

    /** « postgres:18 » → « 18 » ; « postgres:18.3 » → « 18 » ; « postgres » → {@code null}. */
    private static String majeureDeLImage(String image) {
        int deuxPoints = image.lastIndexOf(':');
        if (deuxPoints < 0 || deuxPoints == image.length() - 1) {
            return null;
        }
        return image.substring(deuxPoints + 1).split("[.\\-]")[0];
    }

    /**
     * Refuse toute base dont le nom ne finit pas par {@code _TEST} : Flyway migrerait le schéma et
     * les tests écriraient dedans. Le nom est le dernier segment de l'URL JDBC, paramètres exclus.
     */
    private static void exigerBaseDeTest(String url) {
        String base = url.substring(url.lastIndexOf('/') + 1);
        int parametres = base.indexOf('?');
        if (parametres >= 0) {
            base = base.substring(0, parametres);
        }
        if (!base.toUpperCase().endsWith("_TEST")) {
            throw new IllegalStateException(
                    "PRS_TEST_DB_URL doit désigner une base de test dont le nom se termine par « _TEST » "
                            + "(reçu : « " + base + " »). Les tests migrent le schéma et y écrivent : "
                            + "pointer la base de développement lui coûterait ses données.");
        }
    }
}
