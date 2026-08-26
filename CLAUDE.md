# CLAUDE.md — Backend (PRS20)

Contexte projet pour Claude Code. Décrit la stack, les conventions et les commandes
du backend. À placer à la racine du projet Spring Boot, à côté de `pom.xml`.

## Stack
- Langage : Java 21
- Framework : Spring Boot
- Build : Maven via le wrapper `mvnw` / `mvnw.cmd` (toujours l'utiliser, ne pas exiger un Maven global)
- Packaging : jar exécutable, Tomcat embarqué (depuis le 2026-08-26 — voir `docs/adr/ADR-0001-packaging-jar-java-21.md`)
- Base de données : PostgreSQL en local, administrée avec pgAdmin
- Dépendances clés : Spring Web, Spring Data JPA, PostgreSQL Driver
- IDE : Eclipse (plugin Spring Tools)

## Rôle du projet
API REST consommée par un frontend Angular séparé (projet `frontendprs2`).
Ce backend n'expose **pas** de pages HTML : uniquement des endpoints JSON.

## Structure
- `src/main/java/...` : code source ; point d'entrée `...Application.java` annoté `@SpringBootApplication`
- `src/main/resources/application.properties` : configuration (connexion Postgres, port…)
- `src/test/java/...` : tests
- `pom.xml` : dépendances Maven

Organisation par couches (à respecter) :
- `controller/` : contrôleurs REST (`@RestController`)
- `service/` : logique métier
- `repository/` : interfaces Spring Data JPA
- `entity/` (ou `model/`) : entités JPA (`@Entity`)
- `dto/` : objets de transfert ; ne jamais exposer les entités JPA directement dans l'API
- `exception/` : gestion des erreurs

## Conventions
- API REST : préfixe `/api`, ressources au pluriel (ex. `/api/users`).
- Sortie d'API toujours sous forme de DTO, jamais d'entités JPA brutes.
- Gestion des erreurs centralisée dans une classe `@RestControllerAdvice` avec des `@ExceptionHandler`,
  renvoyant un code HTTP et un message cohérents.
- Validation des entrées avec `jakarta.validation` (`@Valid`, `@NotNull`, `@Size`…).
- CORS : origines autorisées portées par la propriété `app.cors.allowed-origins` (défaut `http://localhost:4200`), pas codées en dur.
- Ne jamais committer de mot de passe en clair ; privilégier des variables d'environnement.
- Secret JWT : `APP_JWT_SECRET` est **obligatoire** (>= 32 octets) — l'application refuse de démarrer sans lui, aucune valeur par défaut (voir `docs/adr/ADR-0002-secret-jwt-fail-fast.md`). En dev, `start-backend.ps1` en génère un et le conserve dans `.env.local` à la racine du projet (hors dépôt).

## Base de données
- SGBD : PostgreSQL (local), géré via pgAdmin.
- Connexion dans `application.properties` :
  - `spring.datasource.url=jdbc:postgresql://localhost:5432/DBPRS20`  ← à compléter
  - `spring.datasource.username=postgres`  (souvent `postgres`)
  - `spring.datasource.password=${DB_PASSWORD:}`  (jamais en clair — définir la variable d'environnement `DB_PASSWORD`)
  - `spring.jpa.hibernate.ddl-auto=update` (Hibernate crée/ajoute les tables à partir des entités — **en sursis**, voir ci-dessous)
- Stratégie de schéma : passage à **Flyway acté** (`docs/adr/ADR-0003-migrations-flyway.md`), implémentation en cours — baseline `V1` = dump du schéma actuel, `ddl-auto` passera d'`update` à `validate`. Les scripts manuels de `docs/migrations/` deviennent l'historique gelé ; toute nouvelle évolution de schéma doit anticiper ce passage plutôt que d'ajouter un 33ᵉ script narratif.

## Commandes
- Lancer : `mvnw.cmd spring-boot:run` (Windows) — ou dans Eclipse : clic droit → Run As → Spring Boot App
- Tests : `mvnw.cmd test` — en CI (`.github/workflows/ci.yml`, chaque poussée) : `mvnw test -DexcludedGroups=word`, qui exclut les 9 tests de génération du PV via MS Word (`documents4j`, tagués JUnit `word`) faute de Word sur les runners Linux ; ils restent exécutés en local.
- Build : `mvnw.cmd clean package`
- L'API démarre par défaut sur `http://localhost:8080`.

## Notes pour Claude
- Utiliser les annotations Spring standard et les conventions Spring Boot idiomatiques.
- Signaler clairement toute modification de `pom.xml` (ajout de dépendance).
- Proposer les tests unitaires/d'intégration en même temps que le code métier.
- Ne pas réintroduire de configuration de type `web.xml` / Deployment Descriptor : projet jar avec Tomcat embarqué.

## Règles de gestion
- Les règles métier de référence sont documentées dans `docs/regles-gestion.md`.
- Toujours les respecter et les consulter **avant** de modifier la logique métier.