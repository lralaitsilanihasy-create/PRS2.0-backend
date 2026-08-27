# Plan de travaux — aout 2026

> Issu de l'audit backend et inspire des travaux du depot **Collegue** (lecture seule,
> `c:\dev\PRS2.0\Collegue`) : s'inspirer de son outillage (CI, structure des ADR, format
> de plan), **jamais de ses arbitrages metier inverses** (R10-R15 de sa comparaison patron)
> sans passage par le PO. Etabli le **2026-08-26**.

## Cadre de travail

- **Methode** : LOT 0 livre en une session ; LOT 1 mene en swarm ; LOT 2 a 5 restent a
  planifier dans l'ordre, chacun pouvant demarrer des que le precedent est stabilise.
- **Barriere** : `mvnw test` (444 tests, dont 9 tagues `word` exclus en CI Linux) +
  `npm run lint` cote front. Toute regression sur cette barriere bloque la suite.
- **Reference** : les ADR de `docs/adr/` tracent les decisions d'architecture issues de ce
  plan (ADR-0001 a ADR-0004 a ce jour) ; ce document trace le decoupage en chantiers et leur
  etat, pas le detail des decisions elles-memes.

---

## Vue d'ensemble

| Ordre | Chantier | Taille | Etat |
|---|---|---|---|
| **LOT 0** | Outillage (CI, ESLint, Mailpit, secret JWT, CORS, pom jar/Java 21) | ~1 j | **Livre** (2026-08-26) |
| **LOT 1** | Bugs actifs (scoping UGPM, audit tronque, blob revoque trop tot, CurrentUser) | ~1 j | **Livre** (2026-08-26 — back `82602fc`, front `7fc30d5`) |
| **LOT 2** | Schema et tests (Flyway, Testcontainers, decoupage de la suite d'integration) | ~3-4 j | **Livre** (`d557cef` + `587aacc` — 18 classes par domaine) |
| **LOT 3** | Autorisation et CRUD (services sans garde, anti-ecrasement, sequences) | ~3-5 j | **Livre** (2026-08-26 — `69c3863` + `cfa5a5d`, 28 tests securite/PK) |
| **LOT 4** | Robustesse (exceptions, verrou optimiste, entites, logs du circuit) | ~2-3 j | **Livre** (2026-08-26 — `d3c907f`..`71df209`, 4 volets) |
| **LOT 5** | Contrat d'API et documentation (OpenAPI, deduplication, ADR) | ~1-2 j | **Livre** (springdoc 3.1.0, dedup docs, api-endpoints a jour) |

**Parque en production** (decision utilisateur : rien ne demarre sur ce perimetre) :
anti-bruteforce (modele `LoginRateLimiter` du depot Collegue), refresh token revocable
transpose en cookie (phase 4 de `docs/plan-cookie-httponly.md`), Dockerfiles, HSTS/proxy,
prefixe `__Host-`. Ces sujets ne sont pas abandonnes : ils sont explicitement hors
perimetre tant qu'aucune decision produit ne les active.

---

## LOT 0 — Outillage

**Etat : livre le 2026-08-26** (commit `e28278e`).

- `pom.xml` : packaging **jar** (Tomcat embarque, `ServletInitializer` supprime),
  `java.version=21`. Voir `docs/adr/ADR-0001-packaging-jar-java-21.md`.
- Secret JWT : plus aucune valeur par defaut ; l'application refuse de demarrer sans
  `APP_JWT_SECRET` valide (garde dans `SecurityConfig#jwtSecretKey`). En dev,
  `start-backend.ps1` genere un secret local conserve dans `.env.local` a la racine du
  projet (hors depot). Voir `docs/adr/ADR-0002-secret-jwt-fail-fast.md`.
- CORS : origines portees par la propriete `app.cors.allowed-origins` (defaut
  `http://localhost:4200`) plutot que codees en dur dans `CorsConfig`.
- CI backend : `backend/.github/workflows/ci.yml`, `mvnw test` a chaque poussee. Les 9
  tests de generation du PV via MS Word (`documents4j`) sont tagues JUnit `word` et
  exclus en CI Linux (`-DexcludedGroups=word`) faute de Word sur les runners ; ils
  restent executes en local.
- Mailpit : `docker-compose.yml` a la racine du projet (service `mailpit` seul, SMTP
  1025, UI `http://localhost:8025`) ; la base reste le conteneur `prs20-db` existant,
  gere hors compose (cf. commentaire du fichier — l'integrer recreerait un conteneur
  vide et perdrait les donnees).
- Frontend : ESLint 9 (configuration plate `eslint.config.js`, script `npm run lint`,
  branche sur la CI).

---

## LOT 1 — Bugs actifs

**Etat : livre le 2026-08-26** (back `82602fc`, front `7fc30d5`).

Quatre anomalies actives, sans dependance entre elles — traitables en parallele.

### 1.1 — Scoping UGPM casse dans plusieurs services

`Visibilite.estPrmp()` reconnait a la fois `PRMP` et `UGPM` (une UGPM partage le
perimetre de sa PRMP de tutelle). Plusieurs services refont a la main la comparaison
`CurrentUser.profil().orElse(null) == ProfilUtilisateur.PRMP` au lieu d'appeler cette
methode centrale — l'UGPM, qui n'est jamais `PRMP` au sens strict, ne matche jamais ces
comparaisons manuelles et se retrouve sans acces la ou elle devrait en avoir. Correctif :
remplacer chaque comparaison manuelle par `Visibilite.estPrmp()`.

### 1.2 — Filtre a 7 caracteres de l'AuditInterceptor

`AuditInterceptor.afterCompletion` tronque l'acteur audite avec
`ref.length() <= 7 ? ref : null` : toute reference de plus de 7 caracteres (un
`ID_PRMP`, par exemple, qui en compte jusqu'a 10) est silencieusement remplacee par
`null` dans `t_audit_log.IM_ACTEUR` — la colonne, elle, est deja en `varchar(10)` depuis
la migration `2026-06-19_audit_log_im_acteur_len10.sql` : seul le code de
l'intercepteur n'a jamais ete aligne dessus. Correctif : remplacer la constante `7` par
`10` (ou la deriver de `AuditLog.imActeur` plutot que de la dupliquer en dur).

### 1.3 — Revocation de blob prematuree (lettre de renvoi)

`lettre-renvoi-consultation.ts#telecharger` appelle `URL.revokeObjectURL(url)`
immediatement apres `a.click()`. Le declenchement du telechargement par le navigateur
n'est pas garanti synchrone avec `click()` (variable selon le navigateur, la taille du
fichier, la charge de la machine) : revoquer l'URL trop tot peut faire echouer le
telechargement de facon intermittente, difficile a reproduire en test manuel. Correctif :
differer la revocation (par exemple `setTimeout`) ou l'accrocher a un evenement de fin
reel plutot qu'a la ligne suivante du code.

### 1.4 — `CurrentUser.profil()` leve au lieu de renvoyer `Optional.empty()`

`CurrentUser.profil()` fait `.map(ProfilUtilisateur::valueOf)` sur la claim `role` du
jeton : si cette chaine ne correspond a aucune constante de l'enum (role obsolete,
faute de frappe, jeton emis par une version anterieure du code), `valueOf` leve une
`IllegalArgumentException` non geree au lieu de renvoyer un `Optional` vide — un cas qui
devrait se traduire par un acces refuse propre (403) se traduit a la place par une
erreur serveur (500). Correctif : envelopper l'appel et retomber sur
`Optional.empty()` si la valeur est inconnue.

---

## LOT 2 — Schema et tests

**Etat : livre** — 2.1-2.2 le 2026-08-26 (`d557cef`, Flyway V1-V4 + Testcontainers PostgreSQL 17) ; 2.3 le 2026-08-27 (`587aacc`, 425 tests repartis verbatim en 18 classes par domaine sur le socle CnmIntegrationTestSupport, duree de suite inchangee).

### 2.1 — Baseline Flyway

Voir `docs/adr/ADR-0003-migrations-flyway.md`. Baseline `V1` = dump du schema actuel,
`ddl-auto` passe d'`update` a `validate`. Conversion des 3 migrations Java au demarrage
(`AssociationCcDispatchMigration`, `CategorieModePassationMigration`,
`FormeMarcheMigration`) en migrations SQL Flyway ordinaires. Les 32 scripts de
`docs/migrations/` deviennent l'historique gele, non convertis, non supprimes.

### 2.2 — Testcontainers PostgreSQL

Voir `docs/adr/ADR-0004-tests-testcontainers-postgres.md`. Remplacement de la base H2
en memoire par un conteneur PostgreSQL 16 reel, pilote par un socle
`AbstractIntegrationTest` a conteneur singleton. Depend de 2.1 : les tests doivent
appliquer les vraies migrations Flyway, pas un schema recree par Hibernate.

### 2.3 — Decoupage de `CnmWorkflowIntegrationTest`

422 des 444 tests du depot vivent dans une seule classe de 10 526 lignes. A decouper
par domaine metier une fois le socle Testcontainers en place (2.2) — un fichier de
cette taille sur un socle plus lourd a demarrer devient plus couteux a faire evoluer
qu'aujourd'hui.

---

## LOT 3 — Autorisation et CRUD

**Etat : livre le 2026-08-26** (`69c3863` fermeture des 13 CRUD + `cfa5a5d` anti-ecrasement et sequences V5).

### 3.1 — Fermeture des services sans garde d'autorisation

Les services suivants n'ont aujourd'hui aucune garde d'autorisation par profil :
`Lot`, `Tranche`, `Echeance`, `MarchePrevision`, `Anomalie`, `IndicateurCtrl`,
`IndicateurPrmp`, `SoaBeneficiaire`, `ServiceBeneficiaire`, `CopieDossier`,
`PvNavette` — dont le `PUT` qui viole l'immuabilite de la navette du PV posee au
§3.5 de `docs/regles-gestion.md` (« aucune navette ne peut etre supprimee ») —,
`SnapshotStats`, `Dmc`.

### 3.2 — Anti-ecrasement a la creation

Un `POST` sur une ressource existante ne devra plus ecraser silencieusement : retourner
**409** (doublon) plutot que de mettre a jour l'existant sous couvert d'une creation.

### 3.3 — Cles allouees par sequence

Generaliser l'allocation de cles numeriques par sequence serveur (le motif deja en
place pour `seq_dossier`, `seq_ppm`, `seq_marche`, `seq_reception`) plutot que de
laisser le client en proposer une.

### 3.4 — Matrice de tests d'autorisation

Une matrice profil x ressource x verbe HTTP, pour verifier systematiquement les gardes
posees en 3.1 et eviter qu'une regression future en rouvre une.

---

## LOT 4 — Robustesse

**Etat : livre le 2026-08-26** (`d3c907f` 500 journalisees, `f77dacd` verrou optimiste V6, `fc18caf` entites de-Lombok + open-in-view, `71df209` logs du circuit).

- `handleGeneric` (gestionnaire d'erreur generique) : journaliser l'exception cote
  serveur avant de renvoyer une reponse masquee au client — aujourd'hui l'un ou l'autre
  se perd.
- `@Version` (verrou optimiste JPA) sur les entites soumises a des ecritures
  concurrentes frequentes.
- Remplacer `@Data` par `@Getter`/`@Setter` sur les 76 entites du modele — `@Data`
  genere aussi `equals`/`hashCode`/`toString` sur des entites JPA, ce qui est une source
  connue de bugs (boucles infinies sur des relations bidirectionnelles, chargement
  involontaire de collections lazy dans un `toString` de log).
- `spring.jpa.open-in-view=false` : fermer la session Hibernate a la sortie du service
  plutot que de la laisser ouverte jusqu'a la vue, pour eviter le chargement paresseux
  hors transaction et rendre visibles les acces manquants des la couche service.
- Logger les transitions du circuit de controle (dispatch, reception, verification,
  rectification, PV, signature) pour faciliter le diagnostic a posteriori.

---

## LOT 5 — Contrat d'API et documentation

**Etat : livre le 2026-08-26** (ADR, springdoc 3.1.0 + OpenApiConfig + test, deduplication des docs partagees, api-endpoints.md aligne sur LOT 3a/3b).

- `springdoc-openapi` : generer un contrat d'API explorable a partir des controleurs et
  DTO existants, plutot que de ne s'appuyer que sur `docs/api-endpoints.md` tenu a la
  main.
- Deduplication de `docs/api-endpoints.md` et `docs/regles-gestion.md` entre les deux
  depots : source unique cote backend, le frontend s'y referant plutot que de
  maintenir sa propre copie en parallele.
- ADR : ce chantier a produit `docs/adr/ADR-0001` a `ADR-0004` ; a poursuivre pour
  toute future decision d'architecture significative.

---

## Suivis ouverts (issus des livraisons du 2026-08-26)

- **Lettre lue par une UGPM** : la consultation par une UGPM marque la lettre « lue » pour la
  tutelle entière (`t_lettre_renvoi_lue` est clé sur `ID_PRMP`, sans notion d'agent). Cohérent
  avec le périmètre partagé, mais **à confirmer côté métier** (badge PRMP).
- **409 du verrou optimiste** : le handler `ObjectOptimisticLockingFailureException` (LOT 4)
  renvoie un 409 sans `code` dédié — en ajouter un (modèle `VacancePrmpException.CODE`) pour que
  le front affiche « rechargez puis réessayez » au lieu d'une erreur générique. Petit chantier
  back + front.
- **Version dans les DTO** : par HTTP, les DTO ne portent pas la version — deux PUT séquentiels
  ne déclenchent pas le conflit (documenté dans `VerrouOptimisteIntegrationTest`). Faire transiter
  la version dans les DTO des écrans d'édition si le besoin apparaît.
- **Warnings a11y ESLint (front)** : ~124 avertissements assumés (`click-events-have-key-events`,
  `interactive-supports-focus`, `label-has-associated-control`) — chantier a11y dédié, cf. AUDIT.md.
