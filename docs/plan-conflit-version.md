# Plan — conflit de verrou optimiste exploitable de bout en bout

> Issu des « Suivis ouverts » de `PLAN_TRAVAUX_2026-08.md` (livraisons du 2026-08-26) :
> **« 409 du verrou optimiste »** (code dédié manquant) et **« Version dans les DTO »**
> (deux PUT séquentiels ne déclenchent jamais le conflit). Établi le **2026-08-27**.
> ⚠️ Push impossible (patron absent) : commits **locaux uniquement**, sur branche dédiée.

## Constat (ancré dans le code au 2026-08-27)

- Le verrou optimiste JPA est en place (LOT 4, commit `f77dacd`, migration `V6`) sur les
  **six entités chaudes** du circuit : `Dossier`, `Ppm`, `Marche`, `PvExamen`,
  `LettreRenvoi`, `DemandeRetrait` — champ `@Version private Integer version`, colonne
  `VERSION integer NOT NULL DEFAULT 0`.
- `GlobalExceptionHandler#handleConflitVersion` rend déjà le 409 avec le bon message
  (« La donnée a été modifiée par une autre opération entre-temps. Rechargez puis
  réessayez. ») mais **sans champ `code`** : le front ne peut pas distinguer ce 409
  des autres (règle métier, doublon, FK) — son toast titre « Action impossible ».
- Les DTO ne portent pas la version, et tous les `update()` sont en
  « charger-puis-modifier » sur entité managée : **deux PUT séquentiels ne se voient
  jamais** (limite documentée dans la javadoc de `VerrouOptimisteIntegrationTest`).
  Le verrou ne protège que l'entrelacement de deux transactions, pas deux formulaires
  ouverts dans deux navigateurs.
- Précédent à imiter : `VacancePrmpException.CODE = "VACANCE_PRMP"`, rendu en 409 avec
  `code` par le handler dédié, reconnu côté front.

---

## Contrat d'API (figé — point de rendez-vous back/front)

### 1. Code d'erreur

- **`CONFLIT_VERSION`**, constante `ConflitVersionException.CODE`.
- Nouvelle exception `cnm.prs.exception.ConflitVersionException extends RuntimeException`,
  sur le modèle exact de `VacancePrmpException` (javadoc en français, message porté par
  l'exception).

### 2. Forme de la réponse 409 (les DEUX chemins rendent le même corps)

`ErrorResponse` existant, **aucun champ nouveau** — seul `code` est désormais renseigné :

```json
{
  "timestamp": "2026-08-27T10:15:00",
  "status": 409,
  "error": "Conflict",
  "message": "La donnée a été modifiée par une autre opération entre-temps. Rechargez puis réessayez.",
  "path": "/api/ppms/12",
  "code": "CONFLIT_VERSION"
}
```

Deux déclencheurs, un seul contrat :
- **Chemin transactionnel** (existant) : `handleConflitVersion`
  (`ObjectOptimisticLockingFailureException`) — ajouter le passage de
  `ConflitVersionException.CODE` à `build(...)`. Message inchangé.
- **Chemin HTTP** (nouveau) : handler `@ExceptionHandler(ConflitVersionException.class)`
  → 409 + même message + même code. Journalisation en `warn` (cas prévu, pas de pile),
  comme le handler existant.

### 3. Champ `version` dans les DTO

| Propriété | Valeur |
|---|---|
| Nom | `version` (identique côté back et front) |
| Type | `Integer` (Java) / `number` (TypeScript) |
| Nullable | **Oui** — aucune annotation `@NotNull`, champ optionnel côté TS (`version?`) |
| En sortie (GET, POST, PUT) | **Toujours renseigné** depuis l'entité |
| En entrée PUT, présent | Comparé à la version courante ; **différent → 409 `CONFLIT_VERSION`**, l'écriture n'a pas lieu |
| En entrée PUT, absent/null | **Comportement historique conservé** (dernier écrit gagne) — compatibilité ascendante : façade `/api/saisies`, scripts et tests existants continuent de fonctionner |
| En entrée POST | Ignoré (la création part à 0, posé par la base/Hibernate) |

Décision assumée (révisable plus tard en durcissement) : `version` **toléré absent** en
entrée. L'exiger d'emblée casserait tous les clients existants d'un coup.

⚠️ Le contrôle est une **comparaison explicite en service** avant les `set...()` :

```java
if (dto.getVersion() != null && !dto.getVersion().equals(existing.getVersion())) {
    throw new ConflitVersionException(...);
}
```

**Jamais** `existing.setVersion(dto.getVersion())` : Hibernate ignore l'écriture manuelle
de `@Version` sur une entité managée — ce serait un contrôle silencieusement mort.

### 4. DTO et endpoints touchés

| DTO (champ `version` ajouté) | Endpoint PUT contrôlé | Profils (état actuel) |
|---|---|---|
| `DossierDto` | `PUT /api/dossiers/{id}` | ADMINISTRATEUR |
| `PpmDto` | `PUT /api/ppms/{id}` | PRMP, UGPM, ADMINISTRATEUR |
| `MarcheDto` | `PUT /api/marches/{id}` | PRMP, UGPM |
| `PvExamenDto` | `PUT /api/pv-examens/{id}` | MEMBRE (`@perm.peutExercer`) |
| `LettreRenvoiDto` | `PUT /api/lettre-renvois/{id}` | CHEF_COMMISSION (`@perm.peutExercer`) |

**Exclu, avec motif** : `DemandeRetraitDto` — la ressource n'a **aucun PUT**
(`DemandeRetraitController` : création POST multipart, décisions `POST /{id}/accepter`
et `/{id}/refuser` sans état client à protéger). Le verrou `@Version` transactionnel
continue seul de couvrir ses écritures concurrentes. Aucune modification.

**Hors périmètre également** : les PATCH granulaires (`/rectifier`, `/supprimer`,
`/restaurer`), la façade `/api/saisies` et les actions de circuit (`/soumettre`,
`/signer`…) — gestes atomiques côté serveur, couverts par le verrou transactionnel.

⚠️ **Le PUT renvoie la version incrémentée.** L'incrément `@Version` se fait au *flush* :
mapper le DTO de retour après un `saveAndFlush(...)` (ou équivalent), sinon la réponse
rend l'ancienne version et le client re-conflicte à coup sûr au PUT suivant.
À prouver par test (voir Q1).

### 5. Côté front (contrat de comportement)

- **Où intercepter** : centralement — `toApiError()` (`core/errors/api-error.ts`) expose
  `code?: string` lu dans le corps `ErrorResponse` ; l'`error.interceptor.ts` titre le
  toast **« Donnée modifiée entre-temps »** quand `code === 'CONFLIT_VERSION'` (au lieu
  du générique « Action impossible » du 409). Le message affiché reste celui du backend.
- **Ce que fait l'écran** : sur `isApiError(err) && err.code === 'CONFLIT_VERSION'`,
  l'écran d'édition **recharge la ressource** (les autres saisies de l'utilisateur sont
  perdues : le toast le dit — « rechargez puis réessayez »). Pas de fusion automatique.
- **Renvoyer la version** : les corps de PUT construits par *spread* de l'objet chargé
  (`{ ...pv, ... }`) l'embarquent automatiquement dès que les interfaces la portent ;
  les corps construits champ à champ doivent l'ajouter explicitement.

---

## Découpage en tâches

### B1 — backend-spring : code dédié du 409 (petit, isolé)

1. Créer `cnm.prs.exception.ConflitVersionException` (modèle `VacancePrmpException` :
   constante `CODE = "CONFLIT_VERSION"`, message par défaut = celui du handler existant).
2. `GlobalExceptionHandler` : ajouter le handler `ConflitVersionException` (409 + code,
   `log.warn` sans pile) ; compléter `handleConflitVersion` existant pour passer
   `ConflitVersionException.CODE` à `build(...)`.
3. Barrière : `mvnw.cmd test` vert.

**Commit local n°1** (backend) : exception + handlers + ajustement du test handler
existant (`handler_conflitVersion_rendu409` doit désormais asserter `code`, cf. Q1).

### B2 — backend-spring : version dans les DTO (après B1)

1. Ajouter `private Integer version;` (avec javadoc « verrou optimiste, cf.
   `docs/plan-conflit-version.md` ») aux 5 DTO : `DossierDto`, `PpmDto`, `MarcheDto`,
   `PvExamenDto`, `LettreRenvoiDto`.
2. Renseigner `version` dans les mappings sortants (méthodes `dto(...)` /
   `LettreRenvoiMapper.toDto`, etc.).
3. Dans les 5 `update()` (`DossierService`, `PpmService`, `MarcheService`,
   `PvExamenService`, `LettreRenvoiService`) : comparaison explicite (cf. contrat §3),
   placée **après** le 404 et les gardes d'autorisation/statut existantes, **avant**
   les `set...()`.
4. Retour du PUT : version incrémentée (flush avant mapping, cf. contrat §4).
5. **Aucune migration** : la colonne `VERSION` existe (V6). Ne rien ajouter à
   `db/migration/` ni à `docs/migrations/`.

**Commit local n°2** (backend) : DTO + services + tests de Q1 + docs de D1 (le dépôt
committe la doc avec le changement qu'elle décrit).

### F1 — frontend-angular : modèles, interception, écrans (parallèle à B1/B2, contrat figé)

1. Modèles : `version?: number` sur `Dossier`, `LettreRenvoi`, `PvExamen`
   (`models/circuit.model.ts`) et `Ppm`, `Marche` (`models/prmp.model.ts`) ;
   `code?: string` sur `ErrorResponse` (`models/common.model.ts`).
2. `core/errors/api-error.ts` : `code?: string` dans `ApiError`, renseigné par
   `toApiError()` ; `core/interceptors/error.interceptor.ts` : titre de toast dédié
   quand `code === 'CONFLIT_VERSION'`.
3. Écrans d'édition — vérifier que la version chargée repart dans le PUT, et recharger
   sur conflit :
   - `shared/prmp/detail-ppm-modal.ts` — PUT ppms (~l. 1342) et PUT marches (~l. 2018) :
     vérifier la construction du `body` (spread ou champ à champ) ;
   - `features/prmp/mise-a-jour-ppm.ts` — PUT ppms (l. 258, spread `{...p}` : porté dès
     que le modèle l'a — vérifier que `p` est l'objet fraîchement chargé) ;
   - `features/prmp/soumettre-dossier.ts` — PUT marches (l. 2001, corps construit champ
     à champ `{ idDetail, idDossier, ...champs }` : **ajouter `version` explicitement**) ;
   - `features/membre/examen-dossier.ts` — PUT pv-examens (l. 1051 et 1239, spread
     `{...pv}` : porté via le modèle).
   - NB : `LettreRenvoiService.modifier()` (PUT lettre-renvois) n'a **aucun appelant**
     aujourd'hui — rien à faire côté écran, le service typé suffit.
4. Barrière : `npm run lint` + `ng test` verts.

**Commit local** (frontend, un seul) : modèles + interception + écrans.

### Q1 — qa-test : tests backend (après B2)

1. `VerrouOptimisteIntegrationTest` :
   - **mettre à jour la javadoc de classe** — le paragraphe « les DTO ne portent pas le
     numéro de version / limite assumée » devient faux ;
   - compléter `handler_conflitVersion_rendu409` : asserter `code == "CONFLIT_VERSION"`.
2. Tests HTTP (MockMvc, socle `CnmIntegrationTestSupport`), sur au moins un endpoint
   représentatif de chaque garde (suggéré : `ppms` et `pv-examens`, extensible) :
   - PUT avec `version` périmée → **409**, corps avec `code = CONFLIT_VERSION`, message
     exact, et la donnée en base **non écrasée** ;
   - PUT avec `version` courante → 200, réponse portant `version` **incrémentée** ;
   - PUT **sans** `version` → 200 (compatibilité, comportement historique) ;
   - GET → `version` présente dans le corps.
3. Barrière : `mvnw.cmd test` complet vert (444+ tests).

### Q2 — qa-test : vérification de bout en bout (après B2 + F1)

Sur l'application qui tourne (`ng serve` + backend local, session cookie) :
1. Deux sessions (deux navigateurs/profils adaptés) ouvrent le même en-tête de PPM ;
   A enregistre, B enregistre ensuite → B voit le toast « Donnée modifiée entre-temps /
   Rechargez puis réessayez », l'écriture de A survit, l'écran de B recharge.
2. Même scénario sur le PV (MEMBRE, brouillon) via `examen-dossier`.
3. Contrôle de non-régression : un PUT « normal » (une seule session) reste fluide,
   aucun 409 parasite en enchaînant deux modifications successives du même écran
   (preuve que la version renvoyée par le PUT est bien la version incrémentée).

### D1 — docs : documentation (contrat figé ; committé avec B2)

1. `docs/api-endpoints.md` : champ `version` sur les 5 ressources, sémantique du PUT
   (présent/absent), réponse 409 `CONFLIT_VERSION` (exemple de corps).
2. `docs/adr/ADR-0005-version-optimiste-dto.md` : décision de contrat (code dédié,
   version nullable en entrée pour compatibilité, exclusion DemandeRetrait, durcissement
   futur possible).
3. `PLAN_TRAVAUX_2026-08.md`, « Suivis ouverts » : basculer les deux entrées (« 409 du
   verrou optimiste », « Version dans les DTO ») en traitées, avec renvoi vers ce plan.
4. Messages de commit des trois commits, dans le style de chaque dépôt.

### Ordre et parallélisme

```
B1 (back, code 409)  ──►  B2 (back, version DTO)  ──►  Q1 (tests back)
        │                        │                          │
        └── contrat figé ──► F1 (front, en parallèle) ──►  Q2 (bout en bout)
                                 D1 (docs, en parallèle, committé avec B2)
```

---

## Points de vigilance propres à ce chantier

- **Pas de migration** : `VERSION` est en base depuis V6 — quiconque propose un script
  SQL ici fait fausse route.
- **Pas de `@NotNull` sur `version`** : la compatibilité ascendante est un choix de
  contrat, pas un oubli (façade `/api/saisies`, tests existants, scripts).
- **`setVersion` sur entité managée = contrôle mort** : la comparaison explicite en
  service est le seul chemin fiable (cf. contrat §3).
- **Version renvoyée par le PUT** : sans flush avant mapping, le client reçoit
  l'ancienne version et re-conflicte systématiquement — c'est le bug le plus probable
  de ce chantier, Q1 point 2 le verrouille.
- **Javadoc de `VerrouOptimisteIntegrationTest`** : elle documente précisément la limite
  que ce chantier supprime — la laisser en l'état serait un piège pour le prochain
  lecteur.
- **Permissions inchangées** : ce chantier ne touche à aucune garde `@PreAuthorize` ;
  tout écart sur ce point dans une revue est une régression.
- **Livraison** : commits locaux atomiques sur branche dédiée, **aucun push** (403,
  patron absent), messages autoportants pour la relecture différée.
