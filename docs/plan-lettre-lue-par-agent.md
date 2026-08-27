# Plan — lettre de renvoi « lue » : suivi par agent (PRMP / UGPM)

> Issu des **Suivis ouverts** de `PLAN_TRAVAUX_2026-08.md` (« Lettre lue par une UGPM »).
> **Décision métier du 2026-08-27 (PO)** : le marquage « lue » d'une lettre de renvoi passe d'un
> état partagé par toute la tutelle à un **suivi par agent** — la consultation par une UGPM ne
> doit plus éteindre le badge de sa PRMP de tutelle.
> Statut : **à implémenter** (aucun code écrit à ce jour). Rappel de livraison : commits locaux
> uniquement (push impossible, patron absent), atomiques et autoportants.

---

## 1. État actuel (ancrage code)

| Élément | Aujourd'hui |
|---|---|
| Table | `t_lettre_renvoi_lue` (`V1__baseline.sql` l.584) : `ID_LECTURE` (identity), `ID_LETTRE`, `ID_PRMP` varchar(10), `DATE_LECTURE`, `UNIQUE uk_lettre_lue (ID_LETTRE, ID_PRMP)` |
| Entité | `LettreRenvoiLue` (mêmes colonnes, pas d'association JPA) |
| Marquage | `LettreRenvoiService#findById` : à la consultation du détail d'une lettre `SIGNE` par la branche `estPrmpProprietaireSignee` (`Visibilite.estPrmp()` = **PRMP ou UGPM**), trace posée avec `CurrentUser.ref()` |
| Le nœud du bug | pour une UGPM, la claim `ref` du JWT porte l'**ID_PRMP de tutelle** (`AuthService#login`, branche UGPM) : la trace UGPM est indiscernable d'une trace PRMP |
| Flag DTO | `LettreRenvoiDto.lue`, peuplé par `existsByIdLettreAndIdPrmp(idLettre, ref)` (`peuplerLue`, `findById`) |
| Badge PRMP | `GET /api/kpis/badges` → `CompteursPrmpDto.lettresRenvoi` = `LettreRenvoiRepository#countSigneesNonLuesPourPrmp(idPrmp)` (not-exists sur `ID_PRMP`) → menu front `/prmp/resultat-examen` (`main-layout.ts#rafraichirBadges`) |
| Écran front | `features/circuit/lettre-renvoi-consultation.ts` (`source='mes'` → `afficherLue`, pastille « Non lue », marquage via `GET /{id}` à l'ouverture du détail) |
| UGPM côté front | pas d'entrée « Mes lettres de renvoi » (endpoint `mes-lettres` = `hasRole('PRMP')`, 403 UGPM) ni de badge ; elle atteint le détail d'une lettre par le **lien de notification** (notifications partagées avec la tutelle via `ref`) — c'est le chemin qui déclenche le bug |
| Tests touchés | `RetraitIntegrationTest` (fixture `LettreRenvoiLue` l.174), `CnmIntegrationTestSupport` |
| Doc | `api-endpoints.md` (§ Lettres de renvoi ~l.2195 et 2225-2230 ; § KPIs ~l.2461), `regles-gestion.md` (~l.215-219 : « effet de bord assumé, à confirmer côté métier ») |

## 2. Cible

### 2.1 Schéma — migration Flyway `V7__lettre_renvoi_lue_par_agent.sql`

> Numéro **V7** = prochain libre à ce jour (V1-V6 en place). Le vérifier au moment du commit :
> un autre chantier (409 du verrou optimiste) est mené en parallèle — a priori sans migration,
> mais c'est le premier arrivé qui prend le numéro.

Identifiant d'agent retenu : le **login du compte** (`t_compte_auth.LOGIN`, = claim `sub` du JWT).
C'est le **seul** identifiant individuel disponible dans le jeton — le `ref` d'une UGPM porte l'ID_PRMP
de tutelle, par construction (le commentaire d'`AuthService` le dit : « le login identifie l'UGPM »).
Aucune claim nouvelle, aucun aller-retour en base au marquage.

Cible de la table (quoter les identifiants comme dans la baseline) :

```sql
-- 1) Nouvelle colonne (longueur alignée sur t_compte_auth."LOGIN" varchar(100)).
ALTER TABLE t_lettre_renvoi_lue ADD COLUMN "LOGIN_AGENT" varchar(100);

-- 2) Reprise : attribuer chaque ligne existante au compte PRMP TITULAIRE de la tutelle.
UPDATE t_lettre_renvoi_lue l
   SET "LOGIN_AGENT" = (SELECT min(c."LOGIN") FROM t_compte_auth c
                         WHERE c."TYPE_ACTEUR" = 'PRMP' AND c."REF_ACTEUR" = l."ID_PRMP");

-- 3) Lignes inattribuables (tutelle sans compte PRMP) : trace orpheline, supprimée.
DELETE FROM t_lettre_renvoi_lue WHERE "LOGIN_AGENT" IS NULL;

-- 4) Verrouiller, et remplacer l'unicité tutelle par l'unicité agent.
ALTER TABLE t_lettre_renvoi_lue ALTER COLUMN "LOGIN_AGENT" SET NOT NULL;
ALTER TABLE t_lettre_renvoi_lue DROP CONSTRAINT uk_lettre_lue;
ALTER TABLE t_lettre_renvoi_lue ADD CONSTRAINT uk_lettre_lue_agent UNIQUE ("ID_LETTRE", "LOGIN_AGENT");
```

(Noms exacts des colonnes de `t_compte_auth` à vérifier contre `V1__baseline.sql` l.270 avant commit.)

`ID_PRMP` est **conservé** (NOT NULL) : il reste le périmètre de tutelle de la trace — purge par
dossier inchangée (`deleteParDossier` passe par `ID_LETTRE`), et il documente « qui, dans quelle
tutelle, a lu quoi ».

**Effet de la reprise (choix figé)** : les lettres déjà tracées restent « lues » pour la **PRMP
titulaire** → **aucune avalanche de badges au déploiement** (l'affichage actuel du badge PRMP est
préservé tel quel). En contrepartie, une UGPM reverra « Non lue » sur une lettre qu'elle seule avait
consultée — assumé : c'est le sens même de la décision (la lecture d'un agent ne vaut plus pour un
autre), et l'UGPM n'a de toute façon ni liste ni badge aujourd'hui. L'alternative (purger toutes les
lignes) aurait re-signalé « non lue » à toutes les PRMP : rejetée.

### 2.2 Contrat d'API — aucun changement de forme, sémantique seulement

| Endpoint | Avant | Après |
|---|---|---|
| `GET /api/lettre-renvois/{id}` | marque « lue » pour la **tutelle** (`ref`) | marque « lue » pour **l'agent connecté** (`sub`) — toujours restreint à la branche PRMP/UGPM propriétaire d'une lettre `SIGNE`, toujours idempotent et silencieux |
| `LettreRenvoiDto.lue` | « lue par la PRMP courante » (tutelle) | « lue par **l'agent connecté** » |
| `GET /api/kpis/badges` et `mes-compteurs-prmp` → `lettresRenvoi` | lettres `SIGNE` de mes dossiers sans trace **tutelle** | lettres `SIGNE` de mes dossiers sans trace **pour mon login** |
| `GET /api/lettre-renvois/mes-lettres` | `hasRole('PRMP')`, flag `lue` tutelle | inchangé (`hasRole('PRMP')`), flag `lue` par agent |

Pas de nouvel endpoint, pas de champ DTO ajouté ni retiré, pas de changement de codes d'erreur.
Le frontend n'a **aucune modification fonctionnelle** à faire : mêmes appels, mêmes formes.

### 2.3 Badge par profil (comportement attendu)

- **PRMP** : le badge `/prmp/resultat-examen` ne compte que les lettres `SIGNE` qu'**elle-même**
  n'a pas consultées. La consultation par une UGPM de sa tutelle ne le décrémente plus.
- **UGPM** : inchangé — pas de badge ni d'entrée « Mes lettres de renvoi » (menu UGPM curé,
  `mes-lettres` reste 403). Sa consultation via un lien de notification marque « lue » **pour elle
  seule** ; le flag `lue` qu'elle voit dans le détail reflète désormais **ses** lectures.
- Autres profils (CC, Président, Membre, Assistant) : jamais de trace posée (le marquage vit dans
  la seule branche propriétaire) — inchangé.

> **Question ouverte au PO (hors périmètre de ce chantier, ne bloque rien)** : maintenant que le
> suivi est individuel, veut-on ouvrir à l'UGPM une liste « Mes lettres de renvoi » et son badge
> (aujourd'hui 403/absents) ? Si oui : chantier séparé (ouvrir `mes-lettres` à `UGPM`, ajouter le
> rôle au rafraîchissement des badges front).

## 3. Tâches ordonnées

### T1 — backend-spring (ouvre le chantier ; un seul commit, la barrière doit rester verte)

`ddl-auto=validate` : migration + entité **dans le même commit**, fixtures de tests adaptées avec.

1. Migration `V7__lettre_renvoi_lue_par_agent.sql` (§2.1) dans `src/main/resources/db/migration/`
   — surtout **pas** dans `docs/migrations/` (historique gelé d'avant Flyway).
2. `LettreRenvoiLue` : champ `loginAgent` (`@Column(name = "LOGIN_AGENT", nullable = false, length = 100)`),
   contrainte d'unicité de `@Table` mise à jour (`{"ID_LETTRE", "LOGIN_AGENT"}`), javadoc réécrite
   (« une entrée par couple lettre/agent »). `idPrmp` conservé.
3. `LettreRenvoiLueRepository` : `existsByIdLettreAndLoginAgent(Integer, String)` remplace
   `existsByIdLettreAndIdPrmp` ; `deleteParDossier` inchangé.
4. `LettreRenvoiService` : dans `findById` et `peuplerLue`, le test d'existence et la trace passent
   sur `CurrentUser.login()` ; la ligne insérée porte **login + idPrmp (ref) + date**. La garde
   d'accès (`estPrmpProprietaireSignee`, `Visibilite.controler`) ne bouge **pas**.
5. `LettreRenvoiRepository#countSigneesNonLuesPourPrmp(idPrmp, login)` : le périmètre des lettres
   reste par `Ppm.idPrmp` (ref), le not-exists passe sur `lu.loginAgent = :login`.
6. `KpiService#mesCompteursPrmp` : passer aussi `CurrentUser.login()` au count.
7. Adapter les fixtures existantes (`RetraitIntegrationTest` l.174, `CnmIntegrationTestSupport`) :
   `loginAgent` désormais NOT NULL. `mvnw.cmd test` vert avant commit.

### T2 — qa-test (après T1)

1. **Le test qui prouve la décision** : deux comptes de la même tutelle (PRMP + UGPM) sur une
   lettre `SIGNE` du périmètre. L'UGPM consulte `GET /api/lettre-renvois/{id}` → sa réponse porte
   `lue=true` ; puis, pour la PRMP : `mes-lettres` renvoie `lue=false` **et**
   `mes-compteurs-prmp.lettresRenvoi` est **inchangé**. La PRMP consulte à son tour → son flag
   passe `lue=true` et son compteur décrémente. Symétrie : la lecture PRMP ne marque pas pour l'UGPM.
2. Idempotence par agent : deux `GET /{id}` du même agent → une seule ligne (unicité
   `(ID_LETTRE, LOGIN_AGENT)`).
3. Autorisation (conformité LOT 3a) : la consultation par un contrôleur du périmètre (Membre/CC/
   Assistant) ne crée **aucune** trace ; une PRMP non propriétaire reste 403.
4. Vérification sur l'application qui tourne (base dev) : rejouer le scénario UGPM → badge PRMP
   intact dans le menu ; contrôler en base la reprise V7 (lignes attribuées au login PRMP,
   orphelines supprimées).

### T3 — frontend-angular (parallélisable avec T2, après le contrat figé — pas de code fonctionnel)

1. `models/circuit.model.ts` : JSDoc de `lue` → « lue par l'**agent** connecté (PRMP ou UGPM) ».
2. `features/circuit/lettre-renvoi-consultation.ts` : commentaires du marquage (l.299) alignés.
3. Vérifier qu'aucun écran ne présume une lecture partagée de tutelle (revue rapide : le flux
   badge → `kpis/badges` → menu ne change pas de forme). `npm run lint` vert.

### T4 — docs (après T1, peut suivre T2/T3)

1. `regles-gestion.md` (~l.215-219) : remplacer l'« effet de bord assumé, à confirmer côté
   métier » par la règle décidée le **2026-08-27** : suivi de lecture **par agent** (login), la
   consultation UGPM ne vaut plus lecture de la tutelle.
2. `api-endpoints.md` : ligne `lue` du DTO (~l.2195), encart « Marquage “lu” à la consultation »
   (~l.2225-2230 : couple lettre/**agent**), ligne `lettresRenvoi` des compteurs PRMP (~l.2461).
3. `PLAN_TRAVAUX_2026-08.md` : passer le suivi ouvert « Lettre lue par une UGPM » à l'état décidé/
   livré avec renvoi vers le présent plan.
4. La doc est dédupliquée depuis le LOT 5 (source unique backend) : ne rien recopier côté frontend.

## 4. Points de vigilance

- **Deux chantiers en parallèle dans `backend/docs`** (409 du verrou optimiste) : ne pas toucher à
  ses fichiers ; revalider le numéro **V7** au moment du commit backend.
- **`ddl-auto=validate`** : migration et entité indissociables (même commit), sinon l'application
  refuse de démarrer.
- **Ne pas élargir les gardes** en passant au login : le marquage doit rester impossible hors de la
  branche propriétaire PRMP/UGPM (`Visibilite.estPrmp()` + `Ppm` du dossier) — c'est l'acquis LOT 3a.
- **Jamais `docs/migrations/`** pour ce changement (historique gelé) ; le script V7 est non
  idempotent par nature Flyway (une exécution unique, versionnée).
- **Reprise** : `min(LOGIN)` protège du cas (improbable) de plusieurs comptes PRMP pour un même
  `REF_ACTEUR` ; le `DELETE` des orphelines est volontaire et documenté ici.
- Aucun push : commits locaux sur branche dédiée, un commit par tâche (T1 backend, T2 tests,
  T3 front, T4 docs), messages autoportants pour la relecture différée.
