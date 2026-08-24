# Contrat d'API REST — Backend CNM (PRS20)

> Document généré à partir du **code réel** des contrôleurs (`src/main/java/cnm/prs/controller`)
> et des DTO (`src/main/java/cnm/prs/dto`). Destiné au frontend Angular (`frontendprs2`) :
> un développeur doit pouvoir en déduire les interfaces TypeScript et les services HTTP
> sans lire le code Java. Règles de gestion : voir `docs/regles-gestion.md`.

## Conventions générales

### Base
- URL de base (dev) : `http://localhost:8080` ; toutes les ressources sont préfixées par `/api`.
- Sérialisation JSON : les champs sont en **camelCase** (identiques aux noms Java des DTO).

### Authentification
- Toutes les routes nécessitent un **jeton JWT**, **sauf** les routes publiques : `POST /api/auth/login`,
  `POST /api/auth/logout`, `POST /api/auth/register/prmp`, `POST /api/auth/register/ugpm`,
  `GET /api/auth/entites`.
- ⚠️ **Exception au sein de `/api/auth/**` (2026-08-24)** : `GET /api/auth/prmps` **n'est plus public** —
  il exige le rôle **`ADMINISTRATEUR`**. Servi anonymement, il livrait la liste des comptes de connexion
  existants alors que `POST /api/auth/login` n'est pas limité en débit (énumération de comptes puis
  martelage). Anonyme → **401**, autre profil authentifié → **403**.
- Obtenir le jeton via `POST /api/auth/login`, puis l'envoyer sur chaque requête :
  `Authorization: Bearer <token>`.
- Absence / invalidité du jeton → **401**. Rôle insuffisant → **403**.
- Dans les tableaux ci-dessous, **« Authentifié »** (ou « Ouvert ») = tout utilisateur connecté
  (un JWT valide suffit) ; ce n'est **pas** public.

### Pagination des grandes listes (⚠️ audit front 2026-08-16)
- `GET /api/dossiers`, `GET /api/ppms` et `GET /api/marches` acceptent `?page=&size=` (0-indexé) :
  la réponse devient l'**enveloppe `Page`** de Spring (`content[]`, `totalElements`, `totalPages`,
  `number`, `size`…) — même forme que `/api/dossiers/examines`. **Sans `page`, la liste plate est
  conservée** (rétro-compatible). Pagination **applicative** : la liste est d'abord constituée avec les
  filtres de périmètre habituels puis découpée — l'ordre est celui de la liste non paginée (le `sort`
  du paramètre n'est pas appliqué).

### Sécurité des réponses (⚠️ audit front 2026-08-16)
- **En-têtes** posés sur toutes les réponses : `Content-Security-Policy: default-src 'self';
  object-src 'none'; frame-ancestors 'self'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options:
  SAMEORIGIN` ; **HSTS** (`max-age=31536000; includeSubDomains`) émis sur les requêtes HTTPS — en prod
  derrière un proxy TLS, transmettre `X-Forwarded-Proto` (honoré via `server.forward-headers-strategy`)
  ou poser HSTS au proxy.
- **Sortie des pièces téléversées** (pièces de dossier, pièces d'inscription PRMP/UGPM/contrôleur) :
  `Content-Type` forcé sur **liste blanche** (`application/pdf`, `image/jpeg`, `image/png` — tout autre
  format stocké sort en `application/octet-stream`, un HTML téléversé n'est **jamais** servi comme tel)
  et `Content-Disposition: attachment` avec **nom de fichier assaini** (pas d'injection d'en-tête).

### Profils (rôles)
Le rôle de l'utilisateur est porté par le jeton (claim `role`). Valeurs possibles :
`PRMP`, `PRESIDENT`, `CHEF_COMMISSION`, `SECRETAIRE`, `MEMBRE`, `VERIFICATEUR`,
`ASSISTANT_CONTROLEUR`, `CHARGE_PUBLICATION`, `ADMINISTRATEUR`.
> `ASSISTANT_CONTROLEUR` : contrôleur **rattaché à une localité** (comme le Vérificateur), compte créé
> par l'**Administrateur** (`/api/controleurs`). Reçoit en lecture les **copies** des lettres de renvoi
> signées et des PV définitifs (avis ≠ FAVR immédiatement ; FAVR après clôture du dossier).

### Clés primaires — IMPORTANT
**Toutes les entités ont une clé primaire ASSIGNÉE par le client** (pas d'auto-génération).
Le champ identifiant (le **1er champ** de chaque DTO) **doit être fourni dans le corps d'un POST de
création** ; l'omettre renvoie **400** (« L'identifiant (clé primaire) est obligatoire à la création »).
Les exemples de requête ci-dessous incluent donc toujours l'identifiant.

### Visibilité par localité
Pour les ressources du circuit (`dossiers`, `receptions`, `dispatchs`, `examens`, `pv-examens`,
`verifications`, `demande-retraits`), les listes et accès directs sont **filtrés par localité** :
- **Président** et **Administrateur** voient toutes les localités ;
- les autres contrôleurs ne voient que **leur** localité ;
- une **PRMP** ne voit que **ses propres** dossiers / demandes : ceux dont elle est **propriétaire**
  (`t_dossier.idPrmp`, **y compris ses brouillons** — PPM, DAO, MAOO) ou rattachés à ses PPM / marchés.
  Elle peut donc **reprendre un brouillon** plus tard (`GET /api/dossiers`, filtrer sur `statut == "BROUILLON"`) ;
- un accès direct (`GET /{id}`) hors périmètre renvoie **403**.

### Référentiels & administration
- **Référentiels** (lecture ouverte, écriture POST/PUT/DELETE réservée à `ADMINISTRATEUR`) :
  `aviss`, `cat-comptes`, `categorie-entites`, `comptes`, `delegation-profils`, `entite-contracts`, `localites`,
  `ministeres`, `mode-passations`, `natures`, `points-ctrls`, `profiles`, `regle-alertes`,
  `regle-anomalies`, `regle-passations`, `seuils`, `situations`, `sous-type-dossiers`, `type-dossiers`, `type-dmc`.
  ⚠️ **Exception (2026-07-26)** : `POST /api/entite-contracts` est ouvert à la **PRMP** (en plus de l'Admin) —
  création d'entité à l'import PPM + auto-rattachement en attente ; PUT/DELETE restent Administrateur.
  ⚠️ **Exception (2026-07-29)** : `POST /api/ministeres` et `POST /api/organigrammes` sont ouverts à la
  **PRMP** (en plus de l'Admin) — ministère d'appartenance absent du référentiel lors de l'enregistrement
  d'une nouvelle entité (le front crée le ministère puis son organigramme actif) ; PUT/DELETE restent Administrateur.
- **Gestion des comptes / hiérarchie** (écriture `ADMINISTRATEUR`, lecture ouverte) :
  `controleurs`, `prmps`, `organigrammes`.
- **Réservé `ADMINISTRATEUR`** (lecture comprise) : `audit-logs`, `session-utilisateurs`, `comptes-auth`.

### Saisie d'un dossier & endpoints restreints
La création d'un dossier passe par la **façade `/api/saisies`** (réservée `PRMP`), pas par les endpoints
bruts. Récapitulatif des écritures **désormais restreintes** :

| Endpoint | Avant | Maintenant |
|---|---|---|
| `POST /api/saisies/ppm`, `POST /api/saisies/dossier` | *(n'existaient pas)* | **`PRMP`** (façade de saisie) |
| `POST /api/dossiers`, `PUT /api/dossiers/{id}` | authentifié | **`ADMINISTRATEUR`** |
| `POST /api/ppms` | authentifié | **`ADMINISTRATEUR`** |
| `DELETE /api/ppms/{id}` | authentifié | **`PRMP`** (propriétaire ; dossier en brouillon) |
| `PUT /api/ppms/{id}` | authentifié | **`PRMP`** ou `ADMINISTRATEUR` |
| `POST`/`PUT`/`DELETE /api/marches` | authentifié | **`PRMP`** (édition d'un brouillon PPM) |
| `POST /api/dossiers/{id}/soumettre` | `PRMP` | `PRMP` **propriétaire**, `BROUILLON → SOUMIS` |

Garde-fous appliqués dans un service partagé (toutes voies) : **propriété** (`t_dossier.idPrmp`), **statut
BROUILLON** pour l'édition, **cohérence type↔contenu** (PPM ⇒ a un PPM ; DAO/MAOO ⇒ pas de PPM).

### Codes de statut
| Code | Signification |
|---|---|
| 200 | OK (GET, PUT, actions) |
| 201 | Créé (POST) |
| 204 | Pas de contenu (DELETE) |
| 400 | Requête invalide (validation, identifiant de création manquant) |
| 401 | Non authentifié (JWT absent/invalide, ou compte désactivé) |
| 403 | Interdit (rôle ou périmètre de localité insuffisant) |
| 404 | Ressource introuvable |
| 409 | Conflit métier (transition d'état interdite, contrainte violée, doublon, suppression interdite) |

### Format d'erreur (`ErrorResponse`)
```json
{
  "timestamp": "2026-06-12T10:30:00",
  "status": 409,
  "error": "Conflict",
  "message": "Le commentaire de rectification est obligatoire (§3.2).",
  "path": "/api/pv-examens/1/retourner",
  "erreurs": [ { "champ": "idDossier", "message": "ne doit pas être nul" } ]
}
```
`erreurs` est un **tableau** d'objets `{ champ, message }`, renseigné uniquement pour les erreurs de
validation (400) ; **omis** (absent du corps) pour les autres erreurs.

Un champ **`code`** (string) s'ajoute au corps pour les erreurs métier que le front doit traiter
**spécifiquement** plutôt qu'en affichant le message brut ; il est **omis** partout ailleurs. Seule valeur
actuelle : **`VACANCE_PRMP`** (409, cf. *Mandats PRMP*).

### Détail des erreurs 400 / 403 / 409
Récapitulatif des trois codes d'erreur « métier » les plus fréquents, leur signification et
quand ils surviennent (mapping centralisé dans `GlobalExceptionHandler`). Côté Angular : afficher
`message`, et pour le **400** exploiter le tableau **`erreurs`** (`[{ champ, message }]`) champ par champ.

#### 400 — Bad Request *(requête invalide ; à corriger avant de renvoyer)*
| Cause | Quand ça survient | Indice |
|---|---|---|
| **Validation des champs** (`@Valid`) | un champ obligatoire manque ou ne respecte pas une contrainte (`@NotNull`, `@NotBlank`, `@Size`…) | `message` = « Validation échouée » + tableau **`erreurs`** (`[{ champ, message }]`) renseigné |
| **Corps illisible / mal formé** (`HttpMessageNotReadableException`) | JSON invalide, mauvais **type** (ex. `idEntiteContract` envoyé en **libellé** au lieu de l'id) ou **date hors ISO** `AAAA-MM-JJ` (ex. `23/06/2026`) | `message` = « Corps de requête invalide ou mal formé. » + **`erreurs`** `[{ champ, message }]` indiquant le **champ fautif** (ex. `dateSignature`, `marches[0].dateFin`) |
| **Identifiant de création manquant** | POST de création sans la clé primaire (toutes les PK sont **assignées par le client**, cf. *Clés primaires*) | « L'identifiant (clé primaire) est obligatoire à la création… » |
| **Règle d'entrée métier** (`BadRequestException`) | ex. `POST /api/mon-compte/changer-mot-de-passe` avec ancien mot de passe incorrect ou nouveau identique à l'ancien ; `POST /api/marches` quand la **localité du dossier** est introuvable (mode indéterminable) | message explicite |

#### 403 — Forbidden *(authentifié mais non autorisé ; ne pas réessayer tel quel)*
| Cause | Quand ça survient | Exemple |
|---|---|---|
| **Rôle insuffisant** (`@PreAuthorize`) | le profil de l'utilisateur n'a pas le droit d'exécuter l'action (ni en titulaire, ni par délégation) | Membre → `POST /api/dispatchs` ; Secrétaire → `…/accepter` un PV ; non-Admin → écriture d'un référentiel ou d'`audit-logs` |
| **Hors périmètre de localité** | accès direct `GET /{id}` ou écriture sur une ressource du circuit d'**une autre localité** (sauf Président/Administrateur) ; une PRMP hors de ses propres dossiers | CC d'ANT → `GET /api/receptions/{id}` d'un dossier TMS |

#### 409 — Conflict *(l'état actuel interdit l'opération)*
| Cause | Quand ça survient | Exemple |
|---|---|---|
| **Transition d'état du PV interdite** | l'action ne correspond pas au statut courant du PV | `accepter` hors `PROJET_SOUMIS` ; `signer` hors `PROJET_ACCEPTE` ; `retourner` **sans commentaire** ; `PUT` sur un PV déjà soumis/signé |
| **Précondition de circuit non remplie** | l'étape précédente n'est pas atteinte | `dispatch` d'un dossier non `PRET_DISPATCH` ou **doublon** de dispatch ; `examen` d'un dossier non `DISPATCHE` ; **édition d'un examen verrouillé** (dossier `PV_SIGNE`) ; `vérification` hors PV `SIGNE` / avis ≠ `FAVR` / dossier clos |
| **Autre règle de gestion** | contrainte métier violée | `NUM_PASSAGE = 1` ⟺ `TYPE_PASSAGE = INITIAL` ; `INTERIM_DISPATCH` incohérent avec la localité ; décision de retrait sans observation ; `sens` de navette invalide |
| **Suppression interdite (immuabilité)** | `DELETE` d'une ressource à traçabilité immuable | `pv-navettes`, `audit-logs` |
| **Vacance de PRMP** (`code: "VACANCE_PRMP"`) | aucune PRMP en fonction à la date de l'action : toute action de traitement côté PRMP/UGPM attend la nomination (pas d'intérim) | `POST /api/dossiers/{id}/soumettre` pendant une transition — message « En attente de nomination de la nouvelle PRMP » |
| **Mandat : règle de nomination** | 3ᵉ mandat pour la même personne, arrêté réutilisé, reconduction recouvrant le précédent (prolongation déguisée), durée > 3 ans | `POST /api/mandats` (cf. *Mandats PRMP*) |
| **Violation de contrainte BD** (`DataIntegrityViolationException`) | identifiant en **doublon**, valeur obligatoire manquante (NOT NULL) ou **clé étrangère** inexistante | POST avec un id déjà utilisé, ou référençant une entité inexistante |

> Rappel : **401** (non authentifié : JWT absent/invalide ou compte désactivé) et **404** (ressource introuvable) restent distincts des trois ci-dessus.

### Types
`Integer`/`Long` → `number` ; `String` → `string` ; `Boolean` → `boolean` ; `BigDecimal` → `number` ;
`LocalDate` → `string` `"yyyy-MM-dd"` ; `LocalDateTime` → `string` ISO `"2026-06-12T10:30:00"`.

---

## Actualités (⚠️ ressource ajoutée 2026-08-19, spec du 2026-08-18)
**Ressource** `/api/actualites` — modal d'actualités affiché à chaque ouverture de session des
utilisateurs **ciblés par leur profil**, édité par l'`ADMINISTRATEUR` sans redéploiement.
Tables : `t_actualite`, `t_actualite_profil` (ciblage), `t_actualite_image` (binaire dédié).

> ⚠️ **Visibilité (`/mes-actualites`) — filtrage entièrement serveur.** Une actualité est renvoyée si
> **toutes** les conditions tiennent : (1) interrupteur global `ACTUALITES_ACTIVES` à `true` (sinon liste
> **vide**) ; (2) `statut = ACTIF` ; (3) le **profil de l'utilisateur authentifié** (JWT/cookie, jamais un
> paramètre client) figure dans les profils cibles ; (4) `datePublication` nulle ou passée **et**
> `dateExpiration` nulle ou **non atteinte** (le jour J, elle est atteinte : l'actualité disparaît).
> Tri : **date de publication effective décroissante** (`datePublication`, sinon date de création).
> Le front ne reçoit jamais une actualité qu'il devrait masquer lui-même.

> ⚠️ **Cycle de vie.** Création → `statut` **forcé `INACTIF`** (l'activation est un second acte délibéré) ;
> `ACTIF`/`INACTIF` par le **PUT** (statut `ARCHIVE` au PUT → 400) ; **DELETE = archivage logique**
> (`ARCHIVE` + `dateArchivage` + `imArchiveur`), jamais de suppression physique — onglet « Historique ».
> **Expiration = archivage automatique** : la bascule `ACTIF`→`ARCHIVE` se fait **au fil des lectures**
> (pas de tâche planifiée), avec `imArchiveur` **null** (système). Une actualité `ARCHIVE` n'est **plus
> modifiable** (PUT/DELETE/ajout d'image → **409**). Toutes les écritures (création, activation,
> archivage, images, interrupteur) sont **journalisées** dans `t_audit_log` par l'intercepteur d'audit.

> ⚠️ **Contenu markdown brut — HTML refusé (400).** `contenuMd` est stocké tel quel et rendu sans
> injection HTML côté front ; toute **balise HTML** (ouvrante, fermante ou commentaire) est rejetée dès la
> saisie. Les usages markdown légitimes de « < » passent (autolien `<https://…>`, comparaison `a < b`).
> Les **profils cibles** sont les noms d'enum (`PRMP`, `UGPM`, `PRESIDENT`, `CHEF_COMMISSION`,
> `SECRETAIRE`, `MEMBRE`, `VERIFICATEUR`, `ASSISTANT_CONTROLEUR`, `CHARGE_PUBLICATION`,
> `ADMINISTRATEUR`) — au moins un (400 si vide ou inconnu) ; `t_actualite_profil` stocke le **nom**
> (colonne `PROFIL`), pas l'`ID_PROFILE` numérique dont la correspondance n'est pas spécifiée.

> ⚠️ **Images — mêmes garanties que la lettre de retrait (2026-08-17).** `POST /{id}/images` en
> multipart (partie `fichier`) : **JPEG obligatoire validé par magic-bytes** (`FF D8 FF`, jamais le
> Content-Type déclaré) → 400 sinon ; **> 10 Mo → 413** ; **redimensionnée au serveur** (largeur max
> **1600 px**, proportionnel) avant stockage (`bytea` + SHA-256) ; `ordre` = fin de la mini-page.
> Lecture `GET /{id}/images/{idImage}` : tout authentifié, sortie durcie (liste blanche MIME +
> `Content-Disposition` assaini + `nosniff` global). Le DTO ne porte que les **métadonnées** des images.

**Champs `ActualiteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idActualite | number | Non (auto) | IDENTITY ; ignoré en entrée |
| titre | string | Oui | @NotBlank, max 200 |
| contenuMd | string | Oui | Markdown brut — **aucun HTML accepté ni renvoyé** (400) |
| profilsCibles | string[] | Oui | Au moins un nom d'enum profil (400 si vide/inconnu) ; dédoublonnés |
| statut | string | Non | `INACTIF` forcé à la création ; `ACTIF`/`INACTIF` par le PUT (null = inchangé) ; `ARCHIVE` par le DELETE seul |
| datePublication | string (date) | Non | null = visible dès activation |
| dateExpiration | string (date) | Non | null = sans terme ; antérieure à datePublication → 400 ; atteinte → ARCHIVE auto |
| images | `ActualiteImageDto[]` | — | **Lecture seule** (serveur) : idImage, nomFichier, taille, ordre — jamais le binaire |
| dateCreation / imAuteur | — | — | Serveur (JWT) |
| dateArchivage / imArchiveur | — | — | Serveur ; imArchiveur null = archivage automatique à l'expiration |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/actualites/mes-actualites | — | `ActualiteDto[]` | 200 | Authentifié — modal d'ouverture de session |
| GET | /api/actualites | — | `ActualiteDto[]` | 200, 403 | ADMINISTRATEUR (Historique compris) ; `?page=&size=` → enveloppe `Page` |
| GET | /api/actualites/{id} | — | `ActualiteDto` | 200, 403, 404 | ADMINISTRATEUR |
| POST | /api/actualites | `ActualiteDto` | `ActualiteDto` | 201, 400, 403 | ADMINISTRATEUR — statut forcé INACTIF |
| PUT | /api/actualites/{id} | `ActualiteDto` | `ActualiteDto` | 200, 400, 403, 404, 409 | ADMINISTRATEUR — 409 si ARCHIVE |
| DELETE | /api/actualites/{id} | — | — | 204, 403, 404, 409 | ADMINISTRATEUR — **archive**, ne supprime pas |
| POST | /api/actualites/{id}/images | multipart (`fichier` JPEG) | `ActualiteImageDto` | 201, 400, 403, 404, 409, 413 | ADMINISTRATEUR |
| GET | /api/actualites/{id}/images/{idImage} | — | binaire `image/jpeg` | 200, 404 | Authentifié |
| DELETE | /api/actualites/{id}/images/{idImage} | — | — | 204, 403, 404 | ADMINISTRATEUR |

**Exemple — création (Administrateur)**
```json
{ "titre": "Nouvelle procédure de dispatch", "contenuMd": "## Ce qui change\n\n- délai ramené à 5 jours…",
  "profilsCibles": ["PRMP", "MEMBRE"], "datePublication": "2026-09-01", "dateExpiration": "2026-09-30" }
```

---

## Paramètres système (⚠️ ressource ajoutée 2026-08-19)
**Ressource** `/api/parametres` — paramètres généraux clé/valeur (`t_parametre` : `CLE`, `VALEUR`,
`DATE_MAJ`, `IM_ACTEUR`), éditables sans redéploiement. Première clé : **`ACTUALITES_ACTIVES`**,
l'interrupteur global du modal d'actualités (coupe/rétablit pour tous, d'un coup). **Ligne absente =
actif** : c'est un coupe-circuit, pas une seconde activation (chaque actualité naît de toute façon
`INACTIF`).

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/parametres/actualites-actives | — | `{ "actif": boolean }` | 200 | Authentifié |
| PUT | /api/parametres/actualites-actives | `{ "actif": boolean }` | `{ "actif": boolean }` | 200, 400, 403 | ADMINISTRATEUR |

---

## Anomalies
**Ressource** `/api/anomalies` — Lecture et écriture : tout utilisateur authentifié (CRUD standard, aucun rôle particulier).

**Champs `AnomalieDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idAnomalie | number | Oui (PK, au POST) | clé primaire |
| idDetail | number | Non | |
| idPpm | number | Non | |
| idRegleAnomalie | number | Oui | @NotNull |
| typeAnomalie | string | Non | max 50 |
| gravite | string | Non | max 10 |
| description | string | Non | |
| dateDetection | string (date-time) | Non | |
| source | string | Non | max 20 |
| statut | string | Non | max 20 |
| imTraitement | string | Non | max 7 |
| dateTraitement | string (date-time) | Non | |
| commentaireTraitement | string | Non | |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/anomalies | — | `AnomalieDto[]` | 200 | Authentifié |
| GET | /api/anomalies/{id} | — | `AnomalieDto` | 200, 404 | Authentifié |
| POST | /api/anomalies | `AnomalieDto` | `AnomalieDto` | 201, 400, 401 | Authentifié |
| PUT | /api/anomalies/{id} | `AnomalieDto` | `AnomalieDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/anomalies/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idAnomalie (number).

**Exemple — requête**
```json
{
  "idAnomalie": 1024, "idDetail": 305, "idPpm": 88, "idRegleAnomalie": 12,
  "typeAnomalie": "MONTANT_INCOHERENT", "gravite": "HAUTE",
  "description": "Montant engagé supérieur au crédit disponible",
  "dateDetection": "2026-06-12T10:30:00", "source": "CONTROLE_AUTO", "statut": "OUVERTE",
  "imTraitement": "CTRMEM", "dateTraitement": null, "commentaireTraitement": null
}
```

---

## Authentification
**Ressource** `/api/auth` — Routes **publiques** (aucun token requis), **sauf `GET /api/auth/prmps`**
qui exige le rôle `ADMINISTRATEUR` (cf. *Authentification* en tête de document). Pas de CRUD.

> ⚠️ **Cookie de session HttpOnly — phase 1 livrée (2026-08-17, plan `docs/plan-cookie-httponly.md`).**
> - `POST /api/auth/login` pose désormais **aussi** le cookie **`PRS_SESSION`** (`HttpOnly; Secure;
>   SameSite=Strict; Path=/`, durée = expiration du JWT). Il transporte **le même JWT** que le corps —
>   l'API authentifie **l'en-tête `Authorization: Bearer` d'abord, sinon le cookie** (double support,
>   le Bearer reste accepté définitivement). Rappel : `SameSite=Strict` ⇒ le cookie ne sert qu'en
>   **même origine** (phase 0 : proxy front → `/api`).
> - **`POST /api/auth/logout`** (public, 204) : vide le cookie (`Max-Age=0`) — un cookie HttpOnly
>   n'est pas supprimable par le JS du front.
> - **CSRF** : réactivé, ciblé sur le **seul canal cookie** — double-submit `XSRF-TOKEN` (cookie
>   lisible, posé dès la première réponse) → en-tête `X-XSRF-TOKEN` (automatique avec Angular
>   `HttpClient`). Une **mutation authentifiée par cookie sans `X-XSRF-TOKEN` → 403**. Exemptés :
>   `/api/auth/**`, les requêtes en `Authorization: Bearer` (en-tête non forgeable cross-site) et les
>   requêtes **sans cookie de session** (les mutations anonymes restent des **401**, pas des 403 CSRF).
>   Mise en œuvre : le `CsrfFilter` de Spring **émet** le jeton, la garde dédiée `CookieCsrfGarde`
>   l'**applique** (le resource server OAuth2 exempte d'office de l'enforcement standard toute requête
>   où le résolveur trouve un jeton — cookie compris — d'où l'exécuteur séparé).
> - Toggles : `app.auth.cookie.secure` (défaut `true`) ; `app.auth.cookie.exclusif` — **`true` depuis
>   la phase 3 (2026-08-17)** : le jeton ne sort plus dans le corps de la réponse de login
>   (`token: null`), le cookie fait tout côté navigateur ; rollback en repassant à `false`.

**Champs `LoginRequest`** (corps de `/login`)

| Champ (JSON) | Type | Obligatoire |
|---|---|---|
| login | string | Oui (@NotBlank) |
| motDePasse | string | Oui (@NotBlank) |

**Champs `LoginResponse`** (réponse de `/login`)

| Champ (JSON) | Type | Description |
|---|---|---|
| token | string \| null | ⚠️ **`null` depuis la phase 3 du plan cookie (2026-08-17)** — la session est portée par le cookie `PRS_SESSION`, le corps ne transporte plus de jeton (rollback : `app.auth.cookie.exclusif=false`). Le canal `Authorization: Bearer` reste accepté pour les clients API |
| login | string | login authentifié |
| role | string | profil métier (ou `null` si non reconnu) |
| typeActeur | string | `CONTROLEUR`, `PRMP` ou `UGPM` |
| ref | string | **périmètre** de l'acteur : matricule contrôleur, identifiant PRMP — et pour une **UGPM**, l'identifiant de sa **PRMP de tutelle** (⚠️ pas son propre matricule) |
| nomAffichage | string | ⚠️ **ajouté** — « Nom Prénoms » de la personne connectée, résolu serveur (voir note) |
| localite | string | localité de rattachement (`null` = toutes, cas Président) |
| expiresIn | number | durée de validité du jeton (secondes) |

> 📌 **`nomAffichage` (⚠️ champ ajouté).** Résolu côté serveur selon le type d'acteur —
> contrôleur (tous rôles CNM, Administrateur compris) → `NOM_CONT` + `PRENOMS_CONT` ; PRMP → `NOM_PRMP` +
> `PRENOMS_PRMP` ; UGPM → `NOM_UGPM` + `PRENOMS_UGPM`. Toujours renseigné : une fiche sans nom exploitable
> retombe sur le **login**, jamais `null` ni chaîne vide. Le front peut donc afficher
> « {nomAffichage} · {role} » **sans aucun appel de référentiel** à l'ouverture de session (avant :
> un `GET /prmps/{ref}` ou `/controleurs/{ref}` par connexion).
>
> C'est la **seule** voie pour une **UGPM** : son `ref` désigne sa PRMP de tutelle (c'est ce qui fait
> fonctionner son périmètre, et cela ne change pas), et `/api/ugpms/**` reste réservé à l'`ADMINISTRATEUR`
> — elle ne peut pas lire sa propre fiche. La réponse ne porte pas son matricule ; si le front en a besoin
> un jour, il faudra l'ajouter explicitement.

**Champs `RegisterPrmpRequest`** (corps de `/register/prmp` — **variante JSON historique**, sans entités ni pièces ; conservée le temps de la bascule du frontend puis retirée)

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| login | string | Oui | @NotBlank, max 100 |
| motDePasse | string | Oui | @NotBlank, min 8, max 72 |
| idPrmp | string | Oui | @NotBlank, max 10 — **= matricule** de la PRMP (identifiant unifié, unique) |
| nomPrmp | string | Oui | @NotBlank, max 100 |
| prenomsPrmp | string | Oui | @NotBlank, max 100 |
| arreteNomin | string | Oui | @NotBlank, max 100 |
| dateNomin | string (date) | Oui | @NotNull |
| cin | string | Oui | @NotBlank, max 12 |
| dateCin | string (date) | Oui | @NotNull |
| lieuCin | string | Oui | @NotBlank, max 50 |
| emailPrmp | string | Oui | @NotBlank, max 100 |
| telPrmp | string | Oui | @NotBlank, max 20 |

> La PRMP **n'a pas de localité propre** : l'inscription ne comporte plus de champ `idLocalite`.

**Champs `RegisterResponse`** (réponse de `/register/prmp`)

| Champ (JSON) | Type | Description |
|---|---|---|
| login | string | login choisi |
| refActeur | string | identifiant PRMP |
| typeActeur | string | `PRMP` |
| actif | boolean | toujours `false` (en attente de validation) |
| statut | string | statut du compte à l'inscription (toujours `EN_ATTENTE`) |
| message | string | message d'information |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/auth/entites | — | `EntitePubliqueDto[]` | 200 | PUBLIC |
| GET | /api/auth/prmps | — | `PrmpPubliqueDto[]` | 200, 401, 403 | **ADMINISTRATEUR** |
| POST | /api/auth/login | `LoginRequest` | `LoginResponse` | 200, 400, 401 | PUBLIC |
| POST | /api/auth/register/prmp | **`multipart/form-data`** (v2, ci-dessous) ou `RegisterPrmpRequest` (JSON, historique) | `RegisterResponse` | 201, 400, 409 | PUBLIC |
| POST | /api/auth/register/ugpm | **`multipart/form-data`** : part `data` (`RegisterUgpmRequest`) + `cin` (obligatoire) + `photo` (opt.) | `RegisterResponse` | 201, 400, 409 | PUBLIC |

> **Inscription v2 (`multipart/form-data`).** Le corps comporte une part **`data`** (`application/json`,
> `RegisterPrmpV2Request` = identité + **`idEntites: number[]`** (entités existantes) +
> **`entitesNonListees: []`** (proposées : `libelle`/`adresse`/`idLocalite`/`categorie`)) et les fichiers
> **`arrete`** et **`cin`** (obligatoires) + **`photo`** (optionnel). **Au moins une** entité (existante
> ou proposée) est requise. Pièces : **PDF / JPEG / PNG**, type vérifié par *magic-bytes*, arrêté ≤ 10 Mo,
> CIN / photo ≤ 5 Mo (sinon **400**). Le compte est créé **`EN_ATTENTE`** ; la connexion reste refusée
> (**401**) jusqu'à validation. Login ou identifiant PRMP déjà utilisés → **409**.
> À chaque inscription, les **Administrateurs sont notifiés** (`NOUVELLE_INSCRIPTION`).
>
> `GET /api/auth/entites` expose le **référentiel réduit** des entités contractantes (id, libellé,
> adresse, catégorie, localité) pour le formulaire d'inscription.
> *Les contrôleurs ne sont pas auto-inscriptibles : leurs comptes sont créés par l'Administrateur (§3.8).*
>
> **Inscription UGPM (`POST /api/auth/register/ugpm`, `multipart/form-data`).** Miroir de l'inscription PRMP
> **sans arrêté ni entités** : part **`data`** (`RegisterUgpmRequest` = identité UGPM — mêmes champs que la PRMP
> sauf arrêté/date de nomination — + **`idPrmpTutelle`** obligatoire) + fichiers **`cin`** (obligatoire) et
> **`photo`** (optionnel, **image seulement** JPEG/PNG). CIN : PDF/JPEG/PNG *magic-bytes* ≤ 5 Mo (sinon **400**).
> Le compte est créé **`EN_ATTENTE`** (`TYPE_ACTEUR=UGPM`, `refActeur=idUgpm`) ; la connexion reste refusée
> (**401**) jusqu'à validation. **`idPrmpTutelle` doit exister**, sinon **409** ; `idUgpm` ou `login` déjà pris
> → **409**. Les **Administrateurs sont notifiés** (`NOUVELLE_INSCRIPTION`). `GET /api/auth/prmps` expose le
> **référentiel réduit des PRMP** (`idPrmp`, `nomPrmp`, `prenomsPrmp`) pour le menu « PRMP de tutelle » —
> ⚠️ **route fermée depuis le 2026-08-24** (rôle `ADMINISTRATEUR` requis, cf. *Authentification*) : elle
> n'est **pas** appelable depuis l'écran d'inscription anonyme. Aucun écran ne la consommait.
> La validation suit le **même circuit Administrateur** que la PRMP (voir *Inscriptions* ci-dessous).

**Exemple — login (requête / réponse)**
```json
{ "login": "CTRMEM", "motDePasse": "Test@1234" }
```
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...", "login": "CTRMEM", "role": "MEMBRE",
  "typeActeur": "CONTROLEUR", "ref": "CTRMEM", "nomAffichage": "Rakoto Jean Claude",
  "localite": "ANT", "expiresIn": 28800
}
```
**Exemple — connexion d'une UGPM** (`ref` = tutelle, `nomAffichage` = l'UGPM elle-même)
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...", "login": "UGPM002", "role": "UGPM",
  "typeActeur": "UGPM", "ref": "PRMP001", "nomAffichage": "Rakoto Jean Claude",
  "expiresIn": 28800
}
```
**Exemple — inscription PRMP (requête / réponse)**
```json
{
  "login": "prmp.rabe", "motDePasse": "MotDePasse#2026", "idPrmp": "IM0050",
  "nomPrmp": "Rabe", "prenomsPrmp": "Hery",
  "arreteNomin": "ARR-2026-050", "dateNomin": "2026-01-15", "cin": "101011112222",
  "dateCin": "2010-05-05", "lieuCin": "Antananarivo", "emailPrmp": "hery.rabe@min.mg",
  "telPrmp": "0330000050"
}
```
```json
{
  "login": "prmp.rabe", "refActeur": "PRMP050", "typeActeur": "PRMP", "actif": false,
  "message": "Inscription enregistrée. Votre compte est en attente de validation par l'administrateur."
}
```

---

## Inscriptions PRMP / UGPM (validation Administrateur)
**Ressource** `/api/inscriptions` — Instruction des inscriptions **PRMP et UGPM** (§3.1). Consultation et écriture réservées à l'**Administrateur** ; le **téléchargement d'une pièce** est ouvert à l'Administrateur **ou** au propriétaire de l'inscription. `GET /en-attente` liste les deux types (champ **`type`** ∈ `PRMP`/`UGPM` sur `InscriptionEnAttenteDto`) : une UGPM a `idPrmpTutelle` renseigné et `entitesDeclarees` vide (pas d'entités propres). `POST /{login}/valider` d'une **UGPM** active directement le compte (aucune entité à instruire — corps `ValidationInscriptionRequest` inutile) ; `refuser` fonctionne à l'identique. **À la validation** (PRMP comme UGPM), les **pièces d'inscription** (stockées sous la clé `login`) sont **ré-affectées sur la clé `id` de l'acteur** (`idPrmp`/`idUgpm`) : elles deviennent accessibles via `GET /api/prmps|ugpms/{id}/pieces/{type}` et sont purgées au `DELETE` de la fiche (unification avec les pièces créées côté Admin). Le téléchargement pendant l'instruction reste `GET /api/inscriptions/{login}/pieces/{type}`.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/inscriptions/en-attente | — | `InscriptionEnAttenteDto[]` | 200, 403 | ADMINISTRATEUR |
| POST | /api/inscriptions/{login}/valider | `ValidationInscriptionRequest` (optionnel) | `ValidationInscriptionResponse` | 200, 400, 403, 404 | ADMINISTRATEUR |
| POST | /api/inscriptions/{login}/refuser | `RefusInscriptionRequest` | — | 204, 400, 403, 404 | ADMINISTRATEUR |
| GET | /api/inscriptions/{login}/pieces/{type} | — | fichier (octets) | 200, 403, 404 | ADMINISTRATEUR ou propriétaire |

`{login}` = login de l'inscription ; `{type}` ∈ `ARRETE_NOMIN` / `CIN` / `PHOTO`.

> **`en-attente`** liste les inscriptions au statut `EN_ATTENTE` (type PRMP), avec leurs entités
> déclarées (existantes/proposées + drapeau *disponible*) et les **métadonnées** des pièces.
>
> **`valider`** est **partielle** : chaque entité existante **disponible** est rattachée
> (`t_prmp_entite` active) ; une entité déjà prise est renvoyée dans **`conflits`** (non bloquant) ;
> une entité **proposée** est créée **seulement si** l'Administrateur l'accepte dans `entitesProposees`
> (`{idDemande, accepter:true, idOrganigramme}` — l'`idOrganigramme` est requis pour créer l'entité).
> Le compte passe **`ACTIF`** si **≥ 1** entité a été activée ; sinon il **reste `EN_ATTENTE`**.
> Réponse : `{ validees:[…], conflits:[{idEntiteContract|libelle, motif}], statutCompte }`.
>
> **`refuser`** passe le compte à **`REFUSE`** (+ `MOTIF_REFUS`), marque les déclarations `REFUSEE`
> et **notifie la PRMP** (`INSCRIPTION_REFUSEE`). La connexion reste refusée.

**Modèle de données associé**
- `t_compte_auth.STATUT` : `EN_ATTENTE` / `ACTIF` / `REFUSE` (+ `MOTIF_REFUS`, `DATE_DECISION`, `IM_VALIDATEUR`) ; le login reste piloté par `ACTIF` (`ACTIF=true` ⟺ `STATUT=ACTIF`).
- `t_prmp_entite_demande` : déclarations d'entités — existante (`ID_ENTITE_CONTRACT`) **ou** proposée (`*_PROPOSE`) ; `STATUT_DEMANDE` `EN_ATTENTE`/`VALIDEE`/`REFUSEE`.
- `t_piece_jointe` : pièces stockées en `bytea` (`TYPE_PIECE`, `FORMAT`, `TAILLE_OCTETS`, `HASH_SHA256`) ; une pièce active par (`LOGIN`, `TYPE_PIECE`).

---

## Avis
**Ressource** `/api/aviss` *(noter le double « s »)* — Référentiel : lecture ouverte ; écriture réservée à `ADMINISTRATEUR`.

**Champs `AvisDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idAvis | string | Oui (PK, au POST) | clé primaire |
| libelleAvis | string | Non | max 100 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/aviss | — | `AvisDto[]` | 200 | Authentifié |
| GET | /api/aviss/{id} | — | `AvisDto` | 200, 404 | Authentifié |
| POST | /api/aviss | `AvisDto` | `AvisDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/aviss/{id} | `AvisDto` | `AvisDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/aviss/{id} | — | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = idAvis (string).

**Exemple — requête**
```json
{ "idAvis": "FAV", "libelleAvis": "Favorable" }
```

---

## Catégories de compte
**Ressource** `/api/cat-comptes` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `CatCompteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idCatCompte | string | Oui (PK, au POST) | clé primaire |
| catCompte | string | Non | max 50 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/cat-comptes | — | `CatCompteDto[]` | 200 | Authentifié |
| GET | /api/cat-comptes/{id} | — | `CatCompteDto` | 200, 404 | Authentifié |
| POST | /api/cat-comptes | `CatCompteDto` | `CatCompteDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/cat-comptes/{id} | `CatCompteDto` | `CatCompteDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/cat-comptes/{id} | — | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = idCatCompte (string).

## Catégories d'entité (⚠️ référentiel ajouté 2026-07-26)
**Ressource** `/api/categorie-entites` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`. Source unique
du **niveau hiérarchique** d'une entité contractante (voir Entités contractantes : `niveauHierarchique` **dérivé**
de `categorieEntite`).

**Champs `CategorieEntiteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| libelle | string | Oui (PK) | max 20, non vide (ex. « MINISTERE ») |
| niveauHierarchique | number | Oui | entier > 0 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/categorie-entites | — | `CategorieEntiteDto[]` | 200 | Authentifié |
| GET | /api/categorie-entites/{id} | — | `CategorieEntiteDto` | 200, 404 | Authentifié |
| POST | /api/categorie-entites | `CategorieEntiteDto` | `CategorieEntiteDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/categorie-entites/{id} | `CategorieEntiteDto` | `CategorieEntiteDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/categorie-entites/{id} | — | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = libelle (string). **Seed** (`docs/migrations/2026-07-26_categorie_entite.sql`) : MINISTERE→1,
CABINET→2, SECRETARIAT GENERAL→2, DIRECTION GENERALE→3, DIRECTION→4, SERVICE→5, DIVISION→6.

**Exemple — requête**
```json
{ "idCatCompte": "FONC", "catCompte": "Dépenses de fonctionnement" }
```

---

## Comptes budgétaires
**Ressource** `/api/comptes` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `CompteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| numCompte | string | Oui (PK, au POST) | clé primaire |
| libelle | string | Non | max 100 |
| idCatCompte | string | Non | max 10 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/comptes | — | `CompteDto[]` | 200 | Authentifié |
| GET | /api/comptes/{id} | — | `CompteDto` | 200, 404 | Authentifié |
| POST | /api/comptes | `CompteDto` | `CompteDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/comptes/{id} | `CompteDto` | `CompteDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/comptes/{id} | — | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = numCompte (string).

**Exemple — requête**
```json
{ "numCompte": "6011001", "libelle": "Achats de fournitures de bureau", "idCatCompte": "FONC" }
```

---

## Comptes d'authentification
**Ressource** `/api/comptes-auth` — **Réservé `ADMINISTRATEUR`**. Gestion/validation des comptes de connexion (notamment les inscriptions PRMP en attente). Le mot de passe n'est jamais exposé.

**Champs `CompteAuthResumeDto`** (réponse)

| Champ (JSON) | Type | Description |
|---|---|---|
| login | string | login du compte |
| typeActeur | string | `CONTROLEUR` ou `PRMP` |
| refActeur | string | matricule contrôleur ou identifiant PRMP |
| actif | boolean | `true` si le compte peut se connecter |

**Champs `ReinitMotDePasseRequest`** (corps de `reinitialiser-mot-de-passe`)

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| nouveauMotDePasse | string | Oui | @NotBlank, min 8, max 72 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/comptes-auth/en-attente | — | `CompteAuthResumeDto[]` (comptes inactifs) | 200, 403 | ADMINISTRATEUR |
| POST | /api/comptes-auth/{login}/activer | — | `CompteAuthResumeDto` | 200, 403, 404 | ADMINISTRATEUR |
| POST | /api/comptes-auth/{login}/desactiver | — | `CompteAuthResumeDto` | 200, 403, 404 | ADMINISTRATEUR |
| POST | /api/comptes-auth/{login}/reinitialiser-mot-de-passe | `ReinitMotDePasseRequest` | `CompteAuthResumeDto` | 200, 400, 403, 404 | ADMINISTRATEUR |

`{login}` = login du compte (string). La réinitialisation impose un nouveau mot de passe à un
utilisateur (ex. mot de passe oublié) ; l'utilisateur pourra ensuite le changer via **Mon compte**.

**Exemple — requête (`/reinitialiser-mot-de-passe`) / réponse (`/activer`)**
```json
{ "nouveauMotDePasse": "MotProvisoire#2026" }
```
```json
{ "login": "prmp.rabe", "typeActeur": "PRMP", "refActeur": "PRMP050", "actif": true }
```

---

## Contrôleurs
**Ressource** `/api/controleurs` — Gestion des comptes (§3.8) : lecture ouverte ; écriture réservée à `ADMINISTRATEUR`.
`GET /par-localite/{idLocalite}` liste les contrôleurs **affectés** à une localité (`idLocalite = X`) — liste **vide**
si aucun (pas de 404) ; les **transversaux** (contrôleur à localité nulle, ex. Président/Publication) sont **exclus**.
`GET /par-profil/{idProfile}` liste les contrôleurs d'un **profil** (rôle, `tr_profile` : 1 PRMP… 8 Administrateur,
9 Assistant contrôleur) — liste **vide** si aucun (pas de 404).
`GET /par-superieur/{imSuperieur}` liste les **subordonnés directs** d'un contrôleur (ceux dont `ID_SUPERIEUR = imSuperieur`)
— liste **vide** si aucun (pas de 404).
`GET /par-nom/{nom}` — recherche **partielle** sur `nomCont` (**contient**, **insensible à la casse**) ; liste **vide**
si aucun résultat (pas de 404). `{nom}` est un fragment (URL-encoder si espaces/accents).

**Champs `ControleurDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| imControleur | string | Oui (PK, au POST) | clé primaire (matricule, max 7) |
| nomCont | string | Non | max 100 |
| prenomsCont | string | Non | max 100 |
| emailCont | string | Non | max 100 |
| telCont | string | Non | max 20 |
| idProfile | number | Non | |
| idLocalite | string | Non | max 5 (`null` = toutes, cas Président) |
| idSuperieur | string | Non | max 7 |
| transversal | boolean | Oui | @NotNull |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/controleurs | — | `ControleurDto[]` | 200 | Authentifié |
| GET | /api/controleurs/{id} | — | `ControleurDto` | 200, 404 | Authentifié |
| GET | /api/controleurs/par-localite/{idLocalite} | — | `ControleurDto[]` | 200 | Authentifié |
| GET | /api/controleurs/par-profil/{idProfile} | — | `ControleurDto[]` | 200 | Authentifié |
| GET | /api/controleurs/par-superieur/{imSuperieur} | — | `ControleurDto[]` | 200 | Authentifié |
| GET | /api/controleurs/par-nom/{nom} | — | `ControleurDto[]` | 200 | Authentifié |
| POST | /api/controleurs | `ControleurDto` (**JSON**) | `ControleurDto` | 201, 400, 403 | ADMINISTRATEUR |
| POST | /api/controleurs | **`multipart/form-data`** : part `data` (JSON `ControleurDto`) + `photo` (opt.) | `ControleurDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/controleurs/{id} | `ControleurDto` (**JSON**) | `ControleurDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| PUT | /api/controleurs/{id} | **`multipart/form-data`** : part `data` (JSON `ControleurDto`) + `photo` (opt.) | `ControleurDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/controleurs/{id} | — | — | 204, 403, 404, 409 | ADMINISTRATEUR |
| POST | /api/controleurs/suppression-lot | `SuppressionLotControleurRequest` `{matricules[]}` | `SuppressionLotControleurResult` | 200, 400, 403 | ADMINISTRATEUR |
| POST | /api/controleurs/{id}/pieces/{type} | `multipart/form-data` (part `fichier`) ; `type` = `PHOTO` | `PieceJointeMetaDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| GET | /api/controleurs/{id}/pieces/{type} | — ; `type` = `PHOTO` | fichier (binaire) | 200, 400, 403, 404 | Authentifié (⚠️ ouvert 2026-07-27 — affichage des photos hors admin ; dépôt/suppression restent ADMINISTRATEUR) |
| DELETE | /api/controleurs/{id}/pieces/{type} | — ; `type` = `PHOTO` | — | 204, 400, 403, 404 | ADMINISTRATEUR |

> **DELETE** supprime le contrôleur **et son compte d'authentification**, en nettoyant ses données **dérivées**
> (sessions, indicateurs). **Garde métier → 409** tant qu'il a une **activité** : supérieur hiérarchique d'un autre
> contrôleur, ou présent sur un examen / PV / vérification / dispatch / réception / demande de retrait / lettre
> signée — retirer d'abord ces éléments ; **404** si l'`imControleur` est inconnu.
>
> **POST `/suppression-lot`** — suppression **en lot par matricule**, **tolérante** : `SuppressionLotControleurRequest`
> = `{matricules: string[]}` (au moins un, sinon **400**) → **200** `SuppressionLotControleurResult` = `{supprimes:
> string[], introuvables: string[], bloques: string[]}`. Chaque contrôleur **sans activité métier** est supprimé
> (données dérivées + compte) → `supprimes` ; les absents → `introuvables` ; ceux **avec activité** (même garde que
> le 409 unitaire) → `bloques` (non supprimés). **Jamais d'échec global** ; doublons ignorés.
>
> **Photo (pièce jointe).** En plus de la variante **JSON pure** (rétro-compatible), `POST /api/controleurs`
> accepte une variante **`multipart/form-data`** : part `data` (JSON = `ControleurDto`) + part `photo`
> **optionnelle**. On peut aussi **déposer/remplacer** la photo via `POST /api/controleurs/{id}/pieces/{type}`
> (part `fichier`) et la **télécharger** via `GET /api/controleurs/{id}/pieces/{type}`. La **modification** `PUT
> /api/controleurs/{id}` accepte elle aussi une variante **`multipart/form-data`** (part `data` = JSON
> `ControleurDto` + `photo` optionnelle) qui met à jour la fiche **et remplace** la photo fournie — **photo absente
> = inchangée** ; la variante **JSON pure** du PUT reste disponible (rétro-compat). Le contrôleur n'a **ni CIN
> ni arrêté** → `type` limité à **`PHOTO`** (tout autre → **400**). La photo doit être une **image (JPEG/PNG**,
> magic-bytes), **≤ 5 Mo** (sinon **400**). Stockée sous la clé `imControleur` ; **404** si le contrôleur ou la
> photo est inconnu(e). On peut aussi **supprimer la photo seule** (sans supprimer le contrôleur) via `DELETE
> /api/controleurs/{id}/pieces/{type}` → **204** ; **400** si `type` ≠ `PHOTO`, **404** si le contrôleur ou la
> photo est inconnu(e). Le **DELETE** d'un contrôleur **purge sa photo** (`t_piece_jointe`) — pas d'orphelin.

`{id}` = imControleur (string).

**Exemple — requête**
```json
{
  "imControleur": "CTRMEM", "nomCont": "Rasoa", "prenomsCont": "Le Membre",
  "emailCont": "mem.ant@cnm.mg", "telCont": "0320000005",
  "idProfile": 5, "idLocalite": "ANT", "idSuperieur": "CCANT01", "transversal": false
}
```

---

## Copies de dossier
**Ressource** `/api/copie-dossiers` — Lecture / écriture : tout utilisateur authentifié.

**Champs `CopieDossierDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idCopie | number | Oui (PK, au POST) | clé primaire |
| idDispatch | number | Oui | @NotNull |
| idDossier | number | Oui | @NotNull |
| imDestinataire | string | Oui | @NotBlank, max 7 |
| typeCopie | string | Oui | @NotBlank, max 30 |
| dateTransmission | string (date-time) | Oui | @NotNull |
| accuseReception | boolean | Oui | @NotNull |
| dateAccuse | string (date-time) | Non | |
| observation | string | Non | max 300 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/copie-dossiers | — | `CopieDossierDto[]` | 200 | Authentifié |
| GET | /api/copie-dossiers/{id} | — | `CopieDossierDto` | 200, 404 | Authentifié |
| POST | /api/copie-dossiers | `CopieDossierDto` | `CopieDossierDto` | 201, 400 | Authentifié |
| PUT | /api/copie-dossiers/{id} | `CopieDossierDto` | `CopieDossierDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/copie-dossiers/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idCopie (number).

**Exemple — requête**
```json
{
  "idCopie": 5012, "idDispatch": 410, "idDossier": 2207, "imDestinataire": "CCANT01",
  "typeCopie": "DISPATCH_CC", "dateTransmission": "2026-06-12T09:15:00",
  "accuseReception": false, "dateAccuse": null, "observation": "Copie pour information"
}
```

---

## Délégations de profil
**Ressource** `/api/delegation-profils` — Référentiel (§3.8) : lecture ouverte ; écriture `ADMINISTRATEUR`.

> **Convention** : `idProfileDelegant` = profil qui **exerce** la tâche (ex. Président) ;
> `idProfileDelegue` = profil **dont la tâche est exercée** (ex. Secrétaire) ; `actif` active/désactive.

> ⚠️ **Délégation ascendante — SOURCE UNIQUE de la règle (2026-08-14).** Cette table pilote la garde
> centrale **`PermissionService.peutExercer(profilRequis)`** (`@perm.peutExercer('X')` dans les
> `@PreAuthorize`, même garde dans les services) : un utilisateur exerce la tâche d'un profil s'il en
> est **titulaire** OU si la paire (profil courant → profil requis) est **active** en base. Hiérarchie
> (rang décroissant) : Président > Secrétaire > Chef de commission > Membre > Contrôleur vérificateur >
> Assistant contrôleur ; PRMP, Administrateur et Chargé de publication **hors hiérarchie**. Les
> **9 paires autorisées** (seed `DelegationHierarchieSeeder`, idempotent — ne réactive jamais une paire
> désactivée par l'Admin) : **Président →** Secrétaire, Chef de commission, Membre, Vérificateur,
> Assistant (5) ; **Chef de commission →** Secrétaire, Membre, Vérificateur, Assistant (4).
> **Table explicite, PAS de comparaison de rangs** : le CC est SOUS le Secrétaire dans la hiérarchie mais
> hérite de ses droits parce que la paire CC → Secrétaire est LISTÉE — un modèle « rang ≥ rang requis »
> casserait ce cas. **Non transitive.** Désactiver une paire (`actif=false`) retire l'habilitation
> **sans changement de code** ; la réactiver la rend. L'accès `ADMINISTRATEUR` (via `hasRole`) et le
> **périmètre par localité** restent inchangés ; les actes d'**identité** (signatures du PV, signature
> régionale des lettres) restent non délégables.

**Champs `DelegationProfilDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idDelegation | number | Oui (PK, au POST) | clé primaire |
| idProfileDelegant | number | Oui | @NotNull — profil qui exerce |
| idProfileDelegue | number | Oui | @NotNull — profil dont la tâche est exercée |
| actif | boolean | Oui | @NotNull |

> **Unicité** : une seule ligne par paire (`idProfileDelegant`, `idProfileDelegue`) — contrainte
> `UQ_DELEGATION_PAIRE` (migration `docs/migrations/2026-08-14_delegation_unicite_paires.sql`) ;
> doublon au POST → **409**. L'habilitation se pilote par `actif`, jamais par des doublons.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/delegation-profils | — | `DelegationProfilDto[]` | 200 | Authentifié |
| GET | /api/delegation-profils/{id} | — | `DelegationProfilDto` | 200, 404 | Authentifié |
| POST | /api/delegation-profils | `DelegationProfilDto` | `DelegationProfilDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/delegation-profils/{id} | `DelegationProfilDto` | `DelegationProfilDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/delegation-profils/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idDelegation (number).

**Exemple — requête** (le Président — profil 2 — exerce la tâche du Secrétaire — profil 4)
```json
{ "idDelegation": 1, "idProfileDelegant": 2, "idProfileDelegue": 4, "actif": true }
```

---

## Demandes de retrait
**Ressource** `/api/demande-retraits` — Création (POST) réservée à `PRMP` ; décision (`POST /{id}/accepter` | `/{id}/refuser`) réservée à `CHEF_COMMISSION` ou `PRESIDENT` (contrôle **rôle↔localité dans le service**) ; suppression à `ADMINISTRATEUR`. Lecture filtrée : une PRMP ne voit que ses demandes, un contrôleur celles de sa localité, Président/Admin tout.

> ⚠️ **Identité & ID (règle ajoutée).** À la création : `idPrmp` = **utilisateur authentifié** (JWT, corps ignoré), `dateDemande` serveur, `statut` forcé `EN_ATTENTE`, `idDemandeRetrait` **auto-généré** (IDENTITY). Gardes (sinon **403/409**) : PRMP **propriétaire** du dossier ; dossier **« avant PV signé »** ; pas de demande déjà **`EN_ATTENTE`**. Liste déroulante des dossiers éligibles : **`GET /api/dossiers/retirables`** (PRMP).

> ⚠️ **Lettre de demande de retrait obligatoire (règle ajoutée 2026-08-17).** Le `POST` est désormais **multipart/form-data** : partie **`data`** = `DemandeRetraitDto` JSON (contrat inchangé), partie **`fichier`** = la **lettre de demande de retrait datée et signée**, **PDF obligatoire**. **400** si la pièce est absente, n'est pas un PDF (validation par **magic-bytes** `%PDF-`, pas le Content-Type déclaré) ou dépasse **10 Mo**. Un `POST` JSON pur → **415**. Stockage **dédié** `t_piece_demande_retrait` (une lettre par demande — nom, format, taille, SHA-256, contenu `bytea`), volontairement **hors** `t_piece_jointe_dossier` : la lettre **justifie la décision** et **survit à la purge du circuit** déclenchée par l'acceptation. Lecture : **`GET /{id}/document`** (PDF, `Content-Disposition: attachment`) — réservée à la **PRMP demanderesse** (périmètre partagé avec son UGPM) et au **décideur** (CC de la localité du dossier / Président ; Admin). **Rétro-compatibilité** : les demandes créées avant l'obligation **restent valides** — `nomFichier`/`tailleFichier` sont `null` (le front affiche « — ») et `GET /{id}/document` renvoie un **404** explicite ; l'obligation ne porte que sur les nouvelles créations.

> ⚠️ **Éligibilité « avant PV signé » (règle ajoutée §3.3).** Un retrait est possible **à toute étape du circuit tant que le PV n'est pas signé** — plus seulement « avant dispatch ». L'ensemble **exact** des statuts de dossier retirables est : **`SOUMIS`, `PRET_DISPATCH`, `DISPATCHE`, `EXAMINE`**. À partir de **`PV_SIGNE`** (puis `EN_VERIFICATION`, `EN_ATTENTE_DECISION_PRMP`, `RETIRE`, `CLOTURE`) le retrait est **refusé** (**409**). `BROUILLON` en est exclu (pré-circuit : supprimable, pas retirable). **`GET /api/dossiers/retirables`** et la garde du **POST** s'appuient sur **le même ensemble** (source unique serveur `StatutDossier.NOMS_AVANT_PV_SIGNE`) — la liste ne peut donc jamais proposer un dossier que le POST rejetterait.

> ⚠️ **Décision (règle ajoutée).** `POST /{id}/accepter` → statut `ACCEPTEE` + **dossier `BROUILLON`**, avec sa **référence de réception invalidée** : `refeDossier` est **restauré à la référence initiale du dossier** (celle générée à la création, stockée dans `t_ppm.REFERENCE`, ex. `00003/DGB/PPM/2026`) — la référence de réception (ex. `00002/PPM/CRM-ANT/2026`) est ainsi remplacée. `GET /api/dossiers` (« Mes brouillons ») réaffiche donc la référence d'origine, et le dossier **redevient entièrement modifiable** (métadonnées, lignes de marché, pièces). *(Dossier sans PPM → `refeDossier` remis à `null`.)* ⚠️ **Purge du circuit** : comme le retrait est possible jusqu'à `EXAMINE`, l'acceptation **supprime tout l'historique de circuit du dossier** en une transaction, dans l'ordre FK-safe (observations → détails d'examen → navettes → vérifications → projets de PV → accusés de lecture → lettres de renvoi → copies → examens → dispatchs → **réceptions**). Après `POST /api/dossiers/{id}/soumettre`, le dossier redevient **`SOUMIS` sans réception** et **réapparaît dans `GET /api/dossiers/a-receptionner`** (re-réception en `INITIAL`, passage 1, avec une **nouvelle** référence de réception). Pour un dossier `SOUMIS`/`PRET_DISPATCH` (jamais dispatché) seules les réceptions feuilles existent ; les autres suppressions portent sur 0 ligne. Le **journal d'audit** (`t_audit_log`, sans FK) est conservé. `POST /{id}/refuser` (corps `{ "motif"? }`) → `REFUSEE`, dossier **inchangé**. Le décideur réel (CC **ou** Président) est enregistré dans `IM_CTRL_CC` depuis le **JWT**. Hors CC-localité/Président → **403** ; demande déjà traitée → **409**. Notifs PRMP : `RETRAIT_ACCEPTE` / `RETRAIT_REFUSE`.

**Champs `DemandeRetraitDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idDemandeRetrait | number | Non (auto-généré) | ID serveur (IDENTITY) ; ignoré en entrée |
| idDossier | number | Oui | @NotNull |
| idPrmp | string | Non | max 10 — **ignoré** : dérivé du JWT |
| motifRetrait | string | Oui | @NotBlank |
| dateDemande | string (date-time) | Non | **ignoré** : posé côté serveur |
| statut | string | Non | max 20 — `EN_ATTENTE` / `ACCEPTEE` / `REFUSEE` ; **ignoré** en entrée (forcé) |
| imCtrlCc | string | — | max 7 — décideur (CC ou Président), posé serveur depuis le JWT |
| dateDecision | string (date-time) | — | posé serveur à la décision |
| obsDecision | string | — | max 500 — motif de refus (optionnel) |
| nomFichier | string | — | **sortie seule** — nom de la lettre jointe ; `null` si demande antérieure à l'obligation (front : « — ») |
| tailleFichier | number | — | **sortie seule** — taille de la lettre en octets ; `null` si aucune pièce |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/demande-retraits | — | `DemandeRetraitDto[]` | 200 | Authentifié (filtré) — worklist PRMP |
| GET | /api/demande-retraits/mes-demandes | — | `DemandeRetraitDto[]` | 200, 403 | **PRMP** — ses demandes ; **marque l'écran consulté** (voir ci-dessous) |
| GET | /api/demande-retraits/a-valider | — | `DemandeRetraitDto[]` | 200, 403 | CHEF_COMMISSION (localité) / PRESIDENT |
| GET | /api/demande-retraits/historique | — | `DemandeRetraitDto[]` | 200, 403 | CHEF_COMMISSION (localité) / PRESIDENT |
| GET | /api/demande-retraits/{id} | — | `DemandeRetraitDto` | 200, 403, 404 | Authentifié (filtré) |
| GET | /api/demande-retraits/{id}/document | — | PDF (binaire, attachment) | 200, 403, 404 | PRMP demanderesse / CC localité / PRESIDENT / ADMIN |
| POST | /api/demande-retraits | **multipart** : `data` (`DemandeRetraitDto` JSON) + `fichier` (lettre PDF) | `DemandeRetraitDto` | 201, 400, 403, 409, 415 | PRMP |
| POST | /api/demande-retraits/{id}/accepter | — | `DemandeRetraitDto` | 200, 403, 404, 409 | CHEF_COMMISSION / PRESIDENT |
| POST | /api/demande-retraits/{id}/refuser | `{ motif? }` | `DemandeRetraitDto` | 200, 403, 404, 409 | CHEF_COMMISSION / PRESIDENT |
| DELETE | /api/demande-retraits/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idDemandeRetrait (number). Le `PUT /{id}` générique est **supprimé** au profit de `accepter`/`refuser`.

**Exemple — requête (création, PRMP — multipart/form-data)**
```
POST /api/demande-retraits
Content-Type: multipart/form-data

-- partie "data" (application/json) :
{ "idDossier": 1023, "motifRetrait": "Dossier incomplet, pièces manquantes" }
-- partie "fichier" : lettre-retrait.pdf (application/pdf, ≤ 10 Mo)
```
*(le reste — `idPrmp`, `dateDemande`, `statut`, `idDemandeRetrait` — est dérivé/serveur, ignoré en entrée ; la réponse porte `nomFichier`/`tailleFichier`)*

> **Marquage de consultation à l'ouverture (⚠️ règle ajoutée).** `GET /api/demande-retraits/mes-demandes`
> (PRMP) renvoie ses demandes **et** met à jour, à chaque appel, sa **dernière consultation** de l'écran
> (`t_demande_retrait_vue.dateDerniereVue = now`, une seule ligne par PRMP). Cela **remet à zéro** le
> compteur **`demandesRetraitNouvelles`** du menu PRMP, qui compte les demandes passées à `ACCEPTEE`/`REFUSEE`
> (date `DATE_DECISION`) **après** cette dernière consultation (tout l'historique si jamais consulté).

---

## Détails d'examen
**Ressource** `/api/examen-details` — POST/PUT : profil `MEMBRE` (titulaire ou délégué) ; DELETE : `ADMINISTRATEUR`.

**Champs `ExamenDetailDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idDetailExamen | number | Oui (PK, au POST) | clé primaire |
| idExamen | number | Oui | @NotNull |
| idDetail | number | Non | ⚠️ **règle ajoutée 2026-07-21** — **ligne de marché** examinée (FK `t_marche`) : renseignée pour un point de **portée LIGNE** (résultat par marché), **`null`** pour un point **DOSSIER** (inter-lignes) ou un examen **historique** (résultat niveau dossier). Doit appartenir au dossier de l'examen (sinon **400** `idDetail`) ; un point **DOSSIER** avec `idDetail` renseigné → **400** `idDetail` |
| idPtControle | number | Oui | @NotNull |
| conforme | boolean | Oui | @NotNull |
| observations | `ObservationControleDto[]` | Non | lignes « AU LIEU DE / LIRE » (cf. *Observations de contrôle*) ; **`[]` si conforme**, **N lignes si non conforme** (sinon **400**, champ `observations`) ; persistées par le service (remplacement à l'enregistrement) |
| obsSiNonConforme | string | Non | max 500 |

> ⚠️ **Examen séquentiel par ligne de marché (règle ajoutée 2026-07-21).** Un dossier de planification (PPM)
> s'examine **ligne de marché par ligne de marché** : la grille s'applique à chaque marché. Le résultat est donc
> porté **par ligne** via `idDetail` — un `ExamenDetail` par **(idExamen × idDetail × idPtControle)**, triplet dont
> l'**unicité** est garantie côté serveur (400 `idPtControle` en cas de doublon — y compris pour un point DOSSIER
> `idDetail=null`, cas qu'une contrainte SQL ne couvre pas sous PostgreSQL). La **portée** du point (cf. *Points de
> contrôle* : `LIGNE`/`DOSSIER`) détermine l'attendu : point **LIGNE** → un résultat par marché (`idDetail`
> renseigné) ; point **DOSSIER** → un seul résultat (`idDetail=null`). **Rétro-compatible** : les examens
> historiques (résultats au niveau dossier, `idDetail=null`) restent lisibles. La complétude « toutes les lignes
> traitées » est vérifiée à la **soumission** de l'examen (cf. *Examens*), pas à chaque écriture de détail.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/examen-details | — | `ExamenDetailDto[]` | 200 | Authentifié |
| GET | /api/examen-details/{id} | — | `ExamenDetailDto` | 200, 404 | Authentifié |
| POST | /api/examen-details | `ExamenDetailDto` | `ExamenDetailDto` | 201, 400, 403 | MEMBRE (titulaire/délégué) |
| PUT | /api/examen-details/{id} | `ExamenDetailDto` | `ExamenDetailDto` | 200, 400, 404 | MEMBRE (titulaire/délégué) |
| DELETE | /api/examen-details/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idDetailExamen (number).

**Exemple — requête** (non conforme : au moins une ligne d'observation obligatoire)
```json
{ "idDetailExamen": 4501, "idExamen": 201, "idPtControle": 12, "conforme": false,
  "observations": [ { "auLieuDe": "500 000 Ar", "lire": "5 000 000 Ar", "ordre": 1 } ],
  "obsSiNonConforme": "Garantie de soumission absente" }
```

---

## Examen des pièces jointes
**Ressource** `/api/examen-pieces` (table `t_examen_piece`, ⚠️ règle ajoutée) — **Lecture** : authentifié ;
POST/PUT : profil **`MEMBRE`** (titulaire ou délégué) ; DELETE : `ADMINISTRATEUR`.

Résultat d'examen d'une **pièce jointe** du dossier, une par une (miroir des `examen-details` pour les
lignes de marché) : `conforme` = RAS, sinon `observation` (texte libre) porte le constat. **Unicité** du
couple (`idExamen`, `idPiece`) → **409** en cas de doublon (corriger via `PUT`). Purgés avec le circuit
(retrait accepté / annulation de dispatch).

**Champs `ExamenPieceDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idExamenPiece | number | Oui (PK, au POST) | clé primaire (assignée par le client) |
| idExamen | number | Oui | @NotNull — FK `t_examen` |
| idPiece | number | Oui | @NotNull — FK `t_piece_jointe_dossier` |
| conforme | boolean | Oui | @NotNull (true = RAS) |
| observation | string | Non | max 500 — constat si non conforme |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/examen-pieces[?examen={idExamen}] | — | `ExamenPieceDto[]` | 200 | Authentifié |
| GET | /api/examen-pieces/{id} | — | `ExamenPieceDto` | 200, 404 | Authentifié |
| POST | /api/examen-pieces | `ExamenPieceDto` | `ExamenPieceDto` | 201, 400, 403, 409 | MEMBRE (titulaire/délégué) |
| PUT | /api/examen-pieces/{id} | `ExamenPieceDto` | `ExamenPieceDto` | 200, 400, 404, 409 | MEMBRE (titulaire/délégué) |
| DELETE | /api/examen-pieces/{id} | — | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = idExamenPiece (number).

---

## Observations de contrôle
**Ressource** `/api/observation-controles` (table `t_observation_controle`) — **Lecture** : authentifié ;
**écriture** (POST/PUT/DELETE) : profil **`MEMBRE`** (titulaire ou délégué).

Lignes structurées **« AU LIEU DE / LIRE »** d'un point de contrôle d'examen (`ExamenDetail`), en
relation **1,N** : un point de contrôle a **0..N** lignes. Remplace l'ancien champ texte `observation`.

**Champs `ObservationControleDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idObservation | number | — (réponse) | PK **auto-générée** (IDENTITY) |
| idDetail | number | Oui | @NotNull — FK vers le point de contrôle (`t_examen_detail`) ; absent → **400** « Le point de contrôle est obligatoire. » |
| auLieuDe | string | Non | max 500 |
| lire | string | Non | max 500 |
| ordre | number | Oui | @NotNull — ordre de saisie (tri ASC) |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/observation-controles?detail={idDetail} | — | `ObservationControleDto[]` | 200 | Authentifié |
| POST | /api/observation-controles | `ObservationControleDto` | `ObservationControleDto` | 201, 400, 403 | **MEMBRE** |
| PUT | /api/observation-controles/{id} | `ObservationControleDto` | `ObservationControleDto` | 200, 400, 403, 404 | **MEMBRE** |
| DELETE | /api/observation-controles/{id} | — | — | 204, 403, 404 | **MEMBRE** |

---

## Dispatchs
**Ressource** `/api/dispatchs` — POST/PUT : `PRESIDENT` ou `CHEF_COMMISSION` ; DELETE : `ADMINISTRATEUR`. Lecture filtrée par localité.

> **Précondition de circuit (création) → 409** : le dossier rattaché à la réception doit être au statut **`PRET_DISPATCH`** (§2.2/§2.3), et **aucun dispatch ne doit déjà exister** pour cette réception (anti-doublon, §3.2 ; corriger via `PUT`).

> ⚠️ **Transition de statut (règle ajoutée).** À la **création** d'un dispatch, le dossier passe **`PRET_DISPATCH` → `DISPATCHE`** dans la **même transaction** que le dispatch. C'est ce statut `DISPATCHE` qui conditionne l'étape suivante (l'examen l'exige).

> ⚠️ **Annulation (règle ajoutée).** `POST /{id}/annuler` (Président / CC de la localité) **retire le dossier au Membre**, possible tant que le PV n'est pas signé (dossier **`DISPATCHE` ou `EXAMINE`**, **409** au-delà) : purge tout l'**aval du dispatch** (examen, détails, observations, projet de PV, navettes, lettres, copies — la **réception est conservée**) puis le dispatch, et fait revenir le dossier en **`PRET_DISPATCH`** (même transaction, re-dispatchable). Le Membre anciennement assigné est notifié (`DISPATCH_ANNULE`).

> **Règle `interimDispatch`** (sinon **409**) : Président → `false` ; CC dans sa localité → `false` ; CC hors de sa localité → `true` obligatoire.

> ⚠️ **Cohérence de l'attributaire (règle ajoutée 2026-08-15, POST et PUT) → 409** : `imCtrlMembre`, s'il est renseigné, doit désigner un contrôleur **capable d'exercer la tâche du Membre** — profil MEMBRE, **ou** paire (profil → Membre) **active** dans `t_delegation_profil`. Refus explicite sinon (« …n'est ni Membre ni couvert par une délégation active vers Membre… le dossier serait inexaminable (§2.4) ») ; matricule inconnu → **409** (« aucun contrôleur avec le matricule… »). Cette garde **autorise l'auto-attribution** du dispatcheur (Président via Président → Membre ; CC via CC → Membre active) : il examine et signe ensuite la part Membre lui-même — et, ⚠️ **décision produit 2026-08-15** (annule la séparation des signataires du même jour), **aussi sa part de rôle** (Président ou CC) tant que la paire « → Membre » est **active** : toute la signature du PV par une seule personne, en **deux actions successives** sur `POST /api/pv-examens/{id}/signer`. Paire désactivée → blocage data-driven (**403** à l'endpoint ; le 409 « auto-co-signature interdite » reste la garde de fond).

> ⚠️ **Association CC (règle MODIFIÉE 2026-08-15, §3.3).** L'association/copie CC d'un dispatch **ne vaut que lorsque le Président dispatche à un Membre** (le CC de la localité suit alors les dossiers de sa commission) :
> - **dispatcheur = CC** → **aucune association** (ni auto-association, ni copie `DISPATCH_CC`), quelle que soit l'attribution (Membre ou lui-même) : un `imCtrlCc` envoyé par le client est **ignoré** (forcé à `null` par le serveur — pas d'erreur) ;
> - **dispatcheur = Président, attributaire = Membre** → comportement conservé : `imCtrlCc` fourni respecté, à défaut le **CC de la localité du dossier est associé automatiquement** (POST) ; le CC associé reçoit la **copie de dispatch** (`DISPATCH_CC`) et la **copie d'annulation** (`DISPATCH_ANNULE`) ;
> - **dispatcheur = Président, attributaire = lui-même** (auto-attribution) → **pas d'association** (la copie n'a de sens que pour un dispatch « à un Membre ») ;
> - l'association ne désigne **jamais l'attributaire lui-même** (ex. Président → CC-par-délégation) — plus de **doublon « Rôle Membre + Rôle CC »** dans les attributions/statistiques dérivées.
>
> La même normalisation s'applique au **PUT** (sans auto-association : le corps est respecté puis épuré). **Reprise des données au démarrage** (`AssociationCcDispatchMigration`, désactivable via `app.migration.association-cc-dispatch.enabled=false`) : `IM_CTRL_CC` est effacé sur les dispatchs existants où il désigne l'attributaire ou le dispatcheur lui-même — le dispatch en double (ex. 00002/PPM/CNM/2026) est corrigé **sans retrait ni re-dispatch**.

**Champs `DispatchDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idDispatch | number | Oui (PK, au POST) | clé primaire |
| idReception | number | Oui | @NotNull |
| imCtrlDispatch | string | Non | max 7 |
| imCtrlCc | string | Non | max 7 |
| imCtrlMembre | string | Non | max 7 |
| dateDispatch | string (date-heure) | Non | format **`yyyy-MM-dd HH:mm`** (date **et heure** du dispatch) |
| datePredispatch | string (date-heure) | — (réponse) | **`yyyy-MM-dd HH:mm`** — date/heure de réception du dossier par le secrétaire (`t_reception.DATE_RECEPTION` la plus récente du dossier) ; lecture seule, **`null`** si aucune réception |
| dateCtrlAssigne | string (date) | Non | |
| instructions | string | Non | max 500 |
| interimDispatch | boolean | Oui | @NotNull (voir règle) |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/dispatchs | — | `DispatchDto[]` | 200 | Authentifié (filtré) |
| GET | /api/dispatchs/{id} | — | `DispatchDto` | 200, 404 | Authentifié (filtré) |
| POST | /api/dispatchs | `DispatchDto` | `DispatchDto` | 201, 400, 403, 409 | PRESIDENT / CHEF_COMMISSION |
| PUT | /api/dispatchs/{id} | `DispatchDto` | `DispatchDto` | 200, 400, 404, 409 | PRESIDENT / CHEF_COMMISSION |
| DELETE | /api/dispatchs/{id} | — | — | 204, 404 | ADMINISTRATEUR |
| POST | /api/dispatchs/{id}/annuler | — | — | 204, 403, 404, 409 | PRESIDENT / CHEF_COMMISSION |

`{id}` = idDispatch (number).

**Exemple — requête**
```json
{ "idDispatch": 88, "idReception": 305, "imCtrlCc": "CCANT01", "imCtrlMembre": "MEMANT1", "dateDispatch": "2026-05-02 09:30", "instructions": "Examiner en priorité", "interimDispatch": false }
```

> **Dates/heures (⚠️ règle ajoutée).** `dateDispatch` est une **date-heure** (`yyyy-MM-dd HH:mm`,
> colonne `t_dispatch.DATE_DISPATCH` en TIMESTAMP). `datePredispatch` (lecture seule) reprend la
> date/heure de **réception du dossier par le secrétaire** — `t_reception.DATE_RECEPTION` la **plus
> récente** du dossier rattaché (navettes) ; **`null`** si le dossier n'a aucune réception datée.

---

## Documents publics
**Ressource** `/api/document-publics` — Réservé à `CHARGE_PUBLICATION` (CRUD et actions d'intégrité).

**Champs `DocumentPublicDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idDocPublic | number | Oui (PK, au POST) | clé primaire |
| idPublication | number | Oui | @NotNull |
| typeDoc | string | Non | max 30 |
| libelleDoc | string | Non | max 200 |
| cheminFichier | string | Non | max 500 |
| format | string | Non | max 10 |
| tailleOctets | number | Non | |
| dateDepot | string (date-time) | Non | |
| hashSha256 | string | Non | max 64 — renseigné par l'action `empreinte` |

**Champs `EmpreinteRequest`** (corps des actions)

| Champ (JSON) | Type | Obligatoire |
|---|---|---|
| contenuBase64 | string | Oui (@NotBlank) — contenu du fichier en Base64 |

**Champs `VerificationIntegriteResult`** (réponse de `verifier-integrite`)

| Champ (JSON) | Type | Description |
|---|---|---|
| conforme | boolean | vrai si l'empreinte calculée = empreinte enregistrée |
| hashAttendu | string | empreinte SHA-256 enregistrée |
| hashCalcule | string | empreinte SHA-256 recalculée du contenu fourni |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/document-publics | — | `DocumentPublicDto[]` | 200 | CHARGE_PUBLICATION |
| GET | /api/document-publics/{id} | — | `DocumentPublicDto` | 200, 404 | CHARGE_PUBLICATION |
| POST | /api/document-publics | `DocumentPublicDto` | `DocumentPublicDto` | 201, 400, 403 | CHARGE_PUBLICATION |
| PUT | /api/document-publics/{id} | `DocumentPublicDto` | `DocumentPublicDto` | 200, 400, 404 | CHARGE_PUBLICATION |
| DELETE | /api/document-publics/{id} | — | — | 204, 404 | CHARGE_PUBLICATION |
| POST | /api/document-publics/{id}/empreinte | `EmpreinteRequest` | `DocumentPublicDto` | 200, 400, 404 | CHARGE_PUBLICATION |
| POST | /api/document-publics/{id}/verifier-integrite | `EmpreinteRequest` | `VerificationIntegriteResult` | 200, 404 | CHARGE_PUBLICATION |

`{id}` = idDocPublic (number).

**Exemple — requête / réponse (vérification)**
```json
{ "contenuBase64": "JVBERi0xLjQKJeLjz9MK..." }
```
```json
{
  "conforme": true,
  "hashAttendu": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "hashCalcule": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```

---

## Dossiers
**Ressource** `/api/dossiers` — Écriture : tout utilisateur authentifié. Lecture **filtrée par localité** (voir conventions) ; accès hors périmètre → 403.

> **Visibilité d'un dossier par localité.** Un dossier appartient à une localité par **l'une** de ces
> 3 sources : sa propre **`idLocalite`** (`t_dossier.ID_LOCALITE`, estampillée à la soumission), sa
> **réception** (`Reception → Contrôleur.idLocalite`), ou son **PPM** (`Ppm.idLocalite`). Ainsi un
> dossier soumis — **même sans PPM** (DAO, MAOO) — apparaît dans la liste et est consultable par les
> contrôleurs de sa localité (dont le Secrétaire) **avant** toute réception. Un dossier sans aucune de
> ces 3 sources n'est visible que du Président/Administrateur. (La PRMP, elle, voit ses dossiers via `Ppm.idPrmp`.)

**Champs `DossierDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idDossier | number | Oui (PK, au POST) | clé primaire |
| idTypeDossier | string | Non | max 10 — **famille** (`DDP` / `DMC` / `DDM`, ⚠️ codes renommés 2026-07-17) ; déduite du sous-type |
| idSousType | string | Non | max 20 — **sous-type** (référentiel `/api/sous-type-dossiers`) ; famille **DDP : dérivé serveur** (`PPM` / `PPM-AGPM` selon les marchés, valeur envoyée ignorée) ; **DMC/DDM : choisi à la saisie** |
| idDossierParent | number | Non | |
| refeDossier | string | Non | max 100 — **référence officielle, générée à la `…/réception`** ; **`null` avant** (BROUILLON/SOUMIS) ; laisser vide à la création |
| dateRef | string (date) | Non | renseignée à la soumission si vide |
| statut | string | Non | max 30 — cycle : `BROUILLON` → `SOUMIS` → `PRET_DISPATCH` → `DISPATCHE` → `EXAMINE` → `PV_SIGNE` → (`EN_VERIFICATION` si avis FAVR) → `CLOTURE` ; vérif. obs. non levées → `EN_ATTENTE_DECISION_PRMP` (ou `RETIRE`) ; posé par le système, **lecture seule** côté PRMP |
| idLocalite | string | Non | max 5 — localité (FK `tr_localite`) ; **dérivée de l'entité** du dossier (lecture seule à la saisie) |
| idPrmp | string | Non | max 10 — PRMP **d'attribution** (FK `t_prmp`) ; posée à la saisie, **jamais recalculée** ; la PRMP **en fonction** peut aussi agir (cf. *Mandats PRMP*) |
| idMandatAttrib | number | Non | **lecture seule** — mandat d'attribution (FK `t_mandat`), figé à la création et jamais recalculé ; `null` si la PRMP n'a pas de mandat déclaré (cf. *Mandats PRMP*) |
| idEntiteContract | number | Non | entité contractante (FK `tr_entite_contract`) ; **choisie à la saisie**, fixe la localité |
| creePar | string | — (réponse) | ⚠️ **ajouté 2026-08-19** — **login** de l'acteur ayant **créé** le dossier (PRMP ou UGPM de tutelle). **Lecture seule** : posé serveur à la création, toute valeur envoyée est ignorée |
| soumisPar | string | — (réponse) | **login** de l'acteur ayant **soumis** le dossier (PRMP seule). Lecture seule, posé serveur |
| creeParNom | string | — (réponse) | **Nom lisible** « Nom Prénoms » correspondant à `creePar`, **résolu serveur** ; `null` si le compte ou l'acteur est introuvable (le front garde alors le login brut) |
| soumisParNom | string | — (réponse) | Nom lisible correspondant à `soumisPar` ; `null` si non résolvable |

> ⚠️ **Auteur de la saisie (`creePar` / `soumisPar`) — ajouté 2026-08-19 (demande front).** Les deux colonnes
> existaient en base (`t_dossier.CREE_PAR` / `SOUMIS_PAR`) mais n'étaient pas exposées. Elles portent un
> **login de compte**, *pas* un identifiant d'acteur : le front ne peut donc pas les traduire lui-même. Le
> serveur joint donc `login → t_compte_auth → PRMP / UGPM / contrôleur` et renvoie en plus
> **`creeParNom` / `soumisParNom`** (« Nom Prénoms », même convention que le `nomAffichage` du login). La
> résolution est faite **en lot** : trois requêtes au plus, quelle que soit la taille de la liste — les
> listes de dossiers portent donc la même information sans coût par ligne. Un login non résolvable (compte
> supprimé) laisse le nom à `null`, jamais d'erreur.

> **Cycle de vie & saisie.** On **ne crée pas** un dossier brut : la **façade `/api/saisies`** (réservée PRMP)
> crée le dossier (statut **`BROUILLON`**) et son contenu. Un brouillon est **invisible des contrôleurs** ;
> il le devient (`SOUMIS`) via `…/soumettre`. Les endpoints bruts `POST`/`PUT /api/dossiers` sont **réservés
> `ADMINISTRATEUR`** (cf. *Saisies*).

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/dossiers | — | `DossierDto[]` | 200, 400 | Authentifié (filtré, hors BROUILLON) — filtres `?statut=` `&type=` `&sousType=` ; ⚠️ **paginable** (`?page=&size=` → enveloppe `Page`, cf. Conventions) |
| GET | /api/dossiers/a-receptionner | — | `DossierDto[]` | 200, 403 | `SECRETAIRE` (titulaire/délégué) ou `ADMINISTRATEUR` |
| GET | /api/dossiers/a-examiner | — | `DossierDto[]` | 200, 403 | `MEMBRE` (titulaire/délégué) ou `ADMINISTRATEUR` |
| GET | /api/dossiers/examines | — | `Page<DossierDto>` | 200, 403 | `MEMBRE` (titulaire/délégué) ou `ADMINISTRATEUR` |
| GET | /api/dossiers/a-verifier | — | `DossierDto[]` | 200, 403 | `VERIFICATEUR` (titulaire/délégué) ou `ADMINISTRATEUR` — EN_VERIFICATION + EN_ATTENTE_DECISION_PRMP + OBSERVATIONS_LEVEES |
| GET | /api/dossiers/verifies | — | `Page<DossierDto>` | 200, 403 | `VERIFICATEUR` (titulaire/délégué) ou `ADMINISTRATEUR` — DECISION_TRANSMISE_SIGMP + CLOTURE (PV signé) |
| GET | /api/dossiers/en-attente-prmp | — | `DossierDto[]` | 200, 403 | `VERIFICATEUR` (titulaire/délégué) ou `ADMINISTRATEUR` — lecture seule |
| GET | /api/dossiers/{id} | — | `DossierDto` | 200, 403, 404 | Authentifié (filtré) |
| GET | /api/dossiers/{id}/ppm | — | `PpmDto` | 200, 403, 404 | Authentifié (propriétaire pour un BROUILLON) |
| POST | /api/dossiers | `DossierDto` | `DossierDto` | 201, 400, 403 | **ADMINISTRATEUR** |
| PUT | /api/dossiers/{id} | `DossierDto` | `DossierDto` | 200, 400, 403, 404 | **ADMINISTRATEUR** |
| DELETE | /api/dossiers/{id} | — | — | 204, 403, 404, 409 | **PRMP** propriétaire — BROUILLON (cascade contenu + historique) |
| POST | /api/dossiers/{id}/soumettre | — | `DossierDto` | 200, 400, 403, 404, 409 | **PRMP** |
| POST | /api/dossiers/{id}/resoumettre | `DossierResoumissionRequest` | `DossierDto` | 200, 400, 403, 404, 409 | **PRMP** propriétaire |
| GET | /api/dossiers/{id}/historique-echanges | — | `EchangeDto[]` | 200, 403, 404 | **PRMP** / **VERIFICATEUR** (titulaire/délégué) / **ADMINISTRATEUR** |
| GET | /api/dossiers/{id}/journal | — | `ActionDossierDto[]` | 200, 403, 404 | Authentifié (périmètre de visibilité du dossier) |

`{id}` = idDossier (number). **`DossierResoumissionRequest`** = `{ motifRectification }` (String, **@NotBlank**, max 255).

> 📌 **Journal des actions (⚠️ spec « Mandats PRMP »).** `GET /api/dossiers/{id}/journal` renvoie, dans
> l'ordre chronologique, **qui a agi, quand et sous quel mandat**. À ne pas confondre avec
> `/api/audit-logs` (trace technique de toutes les écritures HTTP, réservée à l'Administrateur) : ce
> journal-ci est **métier** et suit le périmètre de visibilité du dossier (§1).
>
> **`ActionDossierDto`** = `{ idAction (number), idDossier (number), dateAction (date-time),
> typeAction (string), idPrmpOperateur (string), nomOperateur (string), auteur (string),
> idMandatOperateur (number|null), detail (string) }`.
> `typeAction` ∈ `CREATION`, `SOUMISSION`, `RESOUMISSION`, `TRANSMISSION_COMPLEMENTS`,
> `TRANSMISSION_COMPLEMENTS_DEPOT`, `SUPPRESSION`, `MISE_A_JOUR`.
> **`idPrmpOperateur` est la PRMP en fonction à la date de l'action** — après un changement de titulaire
> elle diffère de `idPrmp` / `idMandatAttrib` du dossier, qui eux ne bougent pas.

> 📌 **Résolution `idDossier → PPM` (règle ajoutée).** `GET /api/dossiers/{id}/ppm` renvoie le **`PpmDto`
> complet** du dossier, **y compris pour un `BROUILLON`** lu par son **propriétaire** (même critère de
> visibilité que `GET /api/ppms/{id}`, non filtré par statut). Résout le besoin front d'ouvrir un brouillon
> depuis « Mes brouillons » — dont le cas d'un **brouillon PPM sans aucun marché** (où `GET /api/marches` ne
> peut fournir aucun `idPpm`). Aucun PPM rattaché → **404** ; hors périmètre → **403**. *(Depuis le retrait des
> BROUILLON de `GET /api/ppms`, cf. §1/§3.1, c'est la voie recommandée pour obtenir le PPM d'un brouillon.)*

> 📌 **Filtres famille / sous-type (règle ajoutée 2026-07-17).** `GET /api/dossiers` accepte, en plus de
> `?statut=`, les filtres serveur **`?type=`** (famille : `DDP`/`DMC`/`DDM`) et **`?sousType=`** (ex.
> `PPM-AGPM`), combinables entre eux ; valeur inconnue du référentiel → **400**. Le scoping de visibilité
> (localité / PRMP) s'applique toujours d'abord.

> 📌 **Écran « Dossiers à rectifier » (PRMP).** Il n'existe **pas** d'endpoint dédié : la liste est alimentée
> par le **filtre serveur** existant `GET /api/dossiers?statut=EN_ATTENTE_DECISION_PRMP` (scopé à la PRMP),
> qui ne renvoie **que** les dossiers à ce statut. Cohérent avec le compteur KPI `dossiersARectifier`
> (`t_dossier.STATUT = EN_ATTENTE_DECISION_PRMP`).

> ⚠️ **Suppression de dossier (règle ajoutée).** `DELETE /api/dossiers/{id}` est réservée à la **PRMP propriétaire**
> (sinon **403**). Un dossier **`BROUILLON`** est **toujours supprimable** (sinon **409** « Ce dossier ne peut pas
> être supprimé. »), **y compris s'il porte un historique de circuit** (revenu BROUILLON via retrait incomplet).
> Cascade complète en une transaction : **contenu** (prévisions → marchés → PPM) **+ historique de circuit**
> (notifications, demandes de retrait, réceptions — un brouillon n'a jamais dépassé `PRET_DISPATCH`, donc des
> réceptions sans dispatch/examen/PV/vérification). Le **journal d'audit** (`t_audit_log`, immuable §3.8, sans FK) est
> **conservé**. Dossier inexistant → **404**.

> ⚠️ **Historique d'échanges (règle ajoutée).** `GET /api/dossiers/{id}/historique-echanges` retourne l'historique
> complet d'un dossier **`CLOTURE`** (sinon **403**), en **fil chronologique entrelacé** (chaîne de réponse : chaque
> observation est suivie de la rectification PRMP qui y répond) : les observations du vérificateur (source
> `t_verification`, dont le passage final `obsLevees=true` qui a déclenché la clôture) et les rectifications de la PRMP
> (source `t_audit_log`, `TYPE_ACTION=RECTIFICATION_PRMP`). **`EchangeDto`** = `{ type (`OBSERVATION` | `RECTIFICATION`),
> date (jour `yyyy-MM-dd` pour OBSERVATION, date-heure pour RECTIFICATION), acteur (matricule vérificateur ou idPrmp),
> texte (observation ou motif), obsLevees (renseigné pour OBSERVATION, `null` pour RECTIFICATION) }`.

> **Filtre serveur `?statut=` (nouveau).** `GET /api/dossiers?statut=SOUMIS` restreint la liste à ce
> statut **côté serveur**, en **conservant le périmètre** (localité / PRMP). Statut inconnu → **400**.
> Valeurs : `BROUILLON`, `SOUMIS`, `PRET_DISPATCH`, `DISPATCHE`, `EXAMINE`, `PV_SIGNE`, `EN_VERIFICATION`, `EN_ATTENTE_DECISION_PRMP`, `RETIRE`, `CLOTURE`. **Ne pas** l'utiliser pour la
> worklist du Secrétaire : un dossier réceptionné **mais incomplet** reste `SOUMIS` ; utiliser
> `GET /api/dossiers/a-receptionner` (filtre serveur « `SOUMIS` + sans réception », sans N+1).

> **File « à réceptionner » (§3.4).** `GET /api/dossiers/a-receptionner` retourne les dossiers
> **`SOUMIS`** **sans réception** de la **localité** du contrôleur (Président/Administrateur : toutes
> localités). C'est la file de travail du Secrétaire ; un dossier en sort dès qu'une réception est créée.

> **Files du Membre attributaire (§2.4).** `GET /api/dossiers/a-examiner` = ses dossiers **`DISPATCHE`**
> (pas encore examinés) **+ `A_REEXAMINER`** (⚠️ 2026-08-02 — réexamen après lettre de renvoi, pièces
> complémentaires transmises) ; `GET /api/dossiers/examines` = **historique** de ce qu'il a examiné
> (**`EXAMINE` + `PV_SIGNE` + `CLOTURE`**), **paginé** (`?page=&size=&sort=`, réponse `Page` :
> `content[]`, `totalElements`, …). Les deux sont **scopées au Membre courant** (`Dispatch.imCtrlMembre`)
> et **exclusives** : à la création de l'examen, un dossier quitte « à examiner » pour « examinés ». Un
> Membre ne voit que **ses** dossiers (ceux d'un autre Membre n'y figurent pas).

> ⚠️ **Files du Vérificateur (§3.6, règle ajoutée ; ⚠️ RÈGLE DE BASCULE MODIFIÉE 2026-08-04).**
> `GET /api/dossiers/a-verifier` = dossiers sur lesquels le vérificateur a encore une **action** :
> **`EN_VERIFICATION`** (à vérifier), **`EN_ATTENTE_DECISION_PRMP`** (lecture seule — toute vérification est
> refusée **409** tant que la PRMP n'a pas statué, cf. badge « En attente PRMP » côté UI) et
> **`OBSERVATIONS_LEVEES`** (approbation + levée à transmettre à SIGMP).
> `GET /api/dossiers/verifies` = **historique** paginé, **lecture seule**, des dossiers
> **`DECISION_TRANSMISE_SIGMP` ou `CLOTURE` ayant un PV `SIGNE`** — **y compris les auto-clôturés**
> à la signature (`FAV`/`DEF`/`NSP`). Les deux sont **scopées à la localité** du vérificateur (contrôleur
> réceptionnaire).
> ⚠️ **La bascule se fait à la transmission de la décision à SIGMP** (`POST /api/sigmp-transmissions`) :
> le dossier quitte `/a-verifier` et apparaît dans `/verifies` **au même instant** — le travail du
> vérificateur est terminé, l'archivage revient à l'Assistant. *(Avant le 2026-08-04, `DECISION_TRANSMISE_SIGMP`
> restait dans `/a-verifier` jusqu'au `CLOTURE`.)* Les deux files sont **complémentaires et disjointes**.
> Le compteur `aVerifier` de `GET /api/kpis/mes-compteurs-verificateur` en est le **miroir exact** (badge du menu).

> ⚠️ **File « En attente PRMP » du Vérificateur (règle ajoutée), lecture seule.** `GET /api/dossiers/en-attente-prmp`
> = dossiers **`EN_ATTENTE_DECISION_PRMP`** de sa localité (sous-vue dédiée ; ces dossiers figurent aussi dans
> `/a-verifier`). Le vérificateur ne peut ni modifier ni soumettre de nouvelle vérification tant que la PRMP n'a pas statué.

> ⚠️ **Resoumission après rectification (règle ajoutée).** `POST /api/dossiers/{id}/resoumettre` (réservé **PRMP
> propriétaire**) — corps `{ "motifRectification": "…" }` (**obligatoire**, non vide, sinon **400**). N'agit que
> sur un dossier **`EN_ATTENTE_DECISION_PRMP`** (sinon **409**) → transition **`EN_VERIFICATION`** (retour au
> vérificateur). Effets : notification **`RECTIFICATION_PRMP`** au vérificateur du dossier (référence, nom PRMP,
> motif, date) ; trace dans `t_audit_log` (NOM_TABLE=`t_dossier`, TYPE_ACTION=`RECTIFICATION_PRMP`,
> IM_ACTEUR=`<idPrmp>`, CHAMP_MODIFIE=`motifRectification`) ; le **motif** est enregistré sur la dernière
> vérification (`t_verification.MOTIF_RECTIF`) et exposé dans `VerificationDto.motifRectif` (visible côté vérificateur).

> **Soumission (§3.1, Module 03).** `POST /api/dossiers/{id}/soumettre` (réservé **PRMP propriétaire**) :
> passe le dossier de **`BROUILLON` → `SOUMIS`** (statut autre → **409**), vérifie la **cohérence
> type↔contenu** (PPM ⇒ a un PPM ; DAO/MAOO ⇒ pas de PPM, sinon **409**), propage la **localité** (du PPM,
> sinon de la PRMP ; **400** si indéterminable) et **notifie** le Secrétaire + CC (`DOSSIER_SOUMIS`).
> ⚠️ La soumission **ne génère plus** de référence : `refeDossier` reste **`null`** jusqu'à la **réception**
> (l'ancien format `CNM-{localité}-{exercice}-{idDossier}` est **abandonné**). Propriété non respectée → **403**.
>
> ⚠️ **Précondition « PPM ⇒ ≥ 1 marché » (règle ajoutée, cf. `regles-gestion.md` §3.1 Module 03).** Un
> dossier de type **PPM** sans aucune ligne de marché ne peut être soumis → **409** (« *Un PPM doit
> comporter au moins un marché avant soumission.* »). **DAO/MAOO non concernés.**

**Exemple — réponse après `…/soumettre`** (statut SOUMIS, `refeDossier` encore `null` — réf. posée à la réception)
```json
{ "idDossier": 1023, "idTypeDossier": "DAO", "refeDossier": null, "dateRef": "2026-03-10", "statut": "SOUMIS", "idLocalite": "ANT", "idPrmp": "PRMP001" }
```

---

## Types de pièces jointes (référentiel)
**Ressource** `/api/type-piece-jointes` (table `t_type_piece_jointe`) — Référentiel des pièces jointes
**attendues par type de dossier** : lecture pour tout utilisateur authentifié ; écriture réservée à
`ADMINISTRATEUR`. Une pièce marquée `obligatoire` doit être présente **à la soumission** du dossier (voir Dossiers).

**Champs `TypePieceJointeDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idTypePiece | integer | Non (PK auto, IDENTITY) | généré par le serveur ; ignoré au POST |
| libellePiece | string | **Oui** (`@NotNull`) | max 200 |
| obligatoire | boolean | **Oui** (`@NotNull`) | `true` ⇒ exigée à la soumission |
| idTypeDossier | string | Non | max 10 — FK `t_type_dossier` (`PPM`, `DAO`, …) |
| ordre | integer | Non | ordre d'affichage |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/type-piece-jointes | — | `TypePieceJointeDto[]` | 200 | Authentifié |
| GET | /api/type-piece-jointes?typeDossier={id} | — | `TypePieceJointeDto[]` (du type, triés par `ordre`) | 200 | Authentifié |
| GET | /api/type-piece-jointes/{id} | — | `TypePieceJointeDto` | 200, 404 | Authentifié |
| POST | /api/type-piece-jointes | `TypePieceJointeDto` | `TypePieceJointeDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/type-piece-jointes/{id} | `TypePieceJointeDto` | `TypePieceJointeDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/type-piece-jointes/{id} | — | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = idTypePiece (integer).

**Exemple — requête `POST`**
```json
{ "libellePiece": "Plan de passation des marchés", "obligatoire": true, "idTypeDossier": "PPM", "ordre": 1 }
```

---

## Pièces jointes d'un dossier
**Ressource** `/api/piece-jointe-dossiers` (table `t_piece_jointe_dossier`) — Fichiers réellement déposés
sur un dossier. **Upload `multipart/form-data`** par la **`PRMP` propriétaire**. Format vérifié par
**magic-bytes** (PDF/JPEG/PNG uniquement, sinon **400**) ; **pas de limite de taille**. Le champ
`apresLettreRenvoi` **distingue les pièces initiales** (déposées à la création, `false`) **des pièces
ajoutées après réception d'une lettre de renvoi** (`true`).

**Champs `PieceJointeDossierDto`** *(le contenu binaire n'est jamais exposé en JSON)*

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPiece | integer | Non (PK auto, IDENTITY) | généré par le serveur |
| idDossier | integer | **Oui** (`@NotNull`) | FK `t_dossier` |
| idTypePiece | integer | **Oui** (`@NotNull`) | FK `t_type_piece_jointe` |
| libellePiece | string | Non (lecture seule) | jointure `t_type_piece_jointe` |
| nomFichier | string | Non (lecture seule) | nom d'origine du fichier (max 255) |
| format | string | Non (lecture seule) | `PDF` / `JPEG` / `PNG` (déterminé par magic-bytes) |
| taille | integer (long) | Non (lecture seule) | octets |
| dateUpload | date-heure | Non (lecture seule) | posée par le serveur |
| apresLettreRenvoi | boolean | Non (lecture seule) | `false` = initiale ; `true` = après lettre de renvoi |
| idLettre | integer | Non (lecture seule) | FK `t_lettre_renvoi` si `apresLettreRenvoi` |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/piece-jointe-dossiers?dossier={idDossier} | — | `PieceJointeDossierDto[]` | 200 | Authentifié |
| GET | /api/piece-jointe-dossiers/{id} | — | `PieceJointeDossierDto` | 200, 404 | Authentifié |
| GET | /api/piece-jointe-dossiers/{id}/contenu | — | fichier (octets) | 200, 404 | Authentifié |
| POST | /api/piece-jointe-dossiers | `multipart/form-data` | `PieceJointeDossierDto` | 201, 400, 403, 404 | **PRMP** (propriétaire) |
| DELETE | /api/piece-jointe-dossiers/{id} | — | — | 204, 403, 404 | **PRMP** (dossier `BROUILLON`) ou ADMINISTRATEUR |

`{id}` = idPiece (integer).

**Upload (`POST`, `multipart/form-data`)** — deux parts :
- `data` : JSON `{ "idDossier": …, "idTypePiece": … }` (et `idLettre` pour un dépôt après lettre de renvoi) ;
- `fichier` : le fichier **PDF/JPEG/PNG** (magic-bytes ; sinon **400**).

**Règle `apresLettreRenvoi`** : si `idLettre` est fourni **et** le dossier est `SOUMIS`/`PRET_DISPATCH`
**ou `EN_ATTENTE_PIECES`** (⚠️ spec navette 2026-08-02 — dossier suspendu par la lettre signée), la pièce
est enregistrée `apresLettreRenvoi=true` (avec `idLettre`) ; sinon c'est une **pièce initiale** (`false`).

> ⚠️ **Reprise après lettre de renvoi (règle MODIFIÉE 2026-08-02, spec navette + réexamen).** Le dépôt
> d'une pièce pendant `EN_ATTENTE_PIECES` **ne réactive PAS l'examen** : la reprise est une action
> **EXPLICITE** de la PRMP (`POST /api/dossiers/{id}/transmettre-complements` → dossier **`A_REEXAMINER`**,
> même dispatch/Membre, notification `COMPLEMENTS_TRANSMIS` — cf. « Cas 3 » de la section Vérifications).
> L'ancienne ré-ouverture automatique au premier dépôt (`PRET_DISPATCH → DISPATCHE` +
> `PIECE_AJOUTEE_APRES_RENVOI`) ne subsiste que pour le flux historique `PRET_DISPATCH`.

> **Pièces obligatoires à la soumission.** `POST /api/dossiers/{id}/soumettre` vérifie que toutes les pièces
> `obligatoire` de la **famille** de dossier (référentiel ci-dessus, clé `DDP`/`DMC`/`DDM`) sont présentes.
> Sinon **400** : `{ "erreurs": [ { "champ": "piecesJointes", "message": "La pièce '<libellé>' est obligatoire." } ] }`.
>
> ⚠️ **AGPM conditionnel (règle ajoutée).** En plus des pièces `obligatoire` statiques, un dossier de la
> famille **DDP** comportant **≥1 marché en « appel d'offres ouvert »** — soit un sous-type dérivé
> **`PPM-AGPM`** — exige la pièce **AGPM** (Avis Général de Passation de Marché). Absente → même **400**
> `{ "champ": "piecesJointes", ... }` (message citant « AGPM »). Déclenchement **data-driven** : le mode de
> passation porte le drapeau `declencheAgpm` (`tr_mode_passation.DECLENCHE_AGPM`, administrable via
> `mode-passations`) ; la pièce AGPM est repérée par son **code stable `AGPM`**
> (`t_type_piece_jointe.CODE`, famille `DDP`, `obligatoire` statique = false). Le PPM lu (`GET /api/ppms`,
> `/api/ppms/{id}`) expose le dérivé **`agpmRequis`** (`true` ssi ≥1 marché déclencheur ; lecture seule,
> ignoré en écriture) — même source de vérité que le sous-type `PPM-AGPM` du dossier.

---

## Saisies (façade de création)
**Ressource** `/api/saisies` — Ouverte aux profils **`PRMP`** et **`UGPM`**. « Saisir un PPM/DAO/MAOO » **EST**
créer le dossier à soumettre : la façade crée le `t_dossier` (statut **`BROUILLON`**, propriété de la PRMP)
et son contenu **en une transaction** (rollback si une étape échoue). Remplace la création brute de
dossier/PPM (désormais réservée Admin).

> ⚠️ **Profil UGPM (Unité de Gestion de la Passation des Marchés) — règle ajoutée.** Une **UGPM** est rattachée
> à **exactement une PRMP de tutelle** (`t_ugpm.ID_PRMP_TUTELLE → t_prmp` ; une PRMP chapeaute plusieurs UGPM).
> Compte : `t_compte_auth` `TYPE_ACTEUR='UGPM'`, `REF_ACTEUR=ID_UGPM`. **Au login**, le rôle est `UGPM` mais le
> **claim `ref` porte l'ID_PRMP de tutelle** → l'UGPM voit / crée / édite **sous le périmètre de sa PRMP** (le
> scoping `ID_PRMP` fonctionne à l'identique). L'UGPM **crée, corrige et met à jour** les dossiers (`BROUILLON`),
> les marchés, les pièces (`@PreAuthorize hasAnyRole('PRMP','UGPM')`), **mais ne peut PAS soumettre** :
> `POST /api/dossiers/{id}/soumettre` reste **`hasRole('PRMP')`** (UGPM → **403**). La **PRMP voit et soumet** les
> dossiers créés par ses UGPM (ils portent son `ID_PRMP`). Traçabilité : `t_dossier.CREE_PAR` (login créateur —
> PRMP ou UGPM) et `SOUMIS_PAR` (login PRMP soumissionnaire). Création d'une UGPM : `POST /api/ugpms` (Admin).

**Administration des UGPM** `/api/ugpms` — Réservé au profil **`ADMINISTRATEUR`**. La création alloue à la fois la
`t_ugpm` (rattachée à sa PRMP de tutelle) et son **compte d'authentification actif** (`TYPE_ACTEUR=UGPM`).
`GET /par-tutelle/{idPrmp}` liste les UGPM d'une PRMP de tutelle (`idPrmp` = matricule) — **liste vide** si aucune
(ou PRMP inconnue), pas de 404 (filtre). ⚠️ **Exception d'accès — seule route de la ressource ouverte hors
Administrateur** :

- la **PRMP concernée** (et ses UGPM, qui partagent son périmètre `ref`) — ses **propres** unités
  rattachées ; toute **autre** tutelle que la sienne reste refusée (**403**) *(2026-08-19)* ;
- les **profils contrôleurs** — `PRESIDENT`, `CHEF_COMMISSION`, `SECRETAIRE`, `MEMBRE`, `VERIFICATEUR`,
  `ASSISTANT_CONTROLEUR` — pour **toute** tutelle et **sans filtre de localité** *(2026-08-20)* : ce sont eux
  qui instruisent les dossiers et doivent identifier l'unité qui a saisi celui qu'ils examinent. Deux raisons
  d'écarter le filtre par localité : le répertoire des **PRMP** (`GET /api/prmps`) est déjà lisible **sans
  filtre** par tout utilisateur authentifié — filtrer l'enfant serait incohérent ; et l'UGPM **n'a pas de
  localité propre** (elle hérite de celles des entités contractantes actives de sa tutelle, qui peuvent
  couvrir **plusieurs** localités) — le filtre masquerait précisément l'unité qu'un contrôleur d'une autre
  localité doit identifier. `CHARGE_PUBLICATION` n'est pas concerné (hors instruction) → **403**.

> ⚠️ **Étendue des données (2026-08-20).** Hors **Administrateur**, `par-tutelle` renvoie une **vue
> restreinte** : `idUgpm`, `libelle`, `idPrmpTutelle`, `nomUgpm`, `prenomsUgpm`, `emailUgpm`, `telUgpm` —
> exactement ce qu'affiche l'écran. **`cin`, `dateCin`, `lieuCin` et `login` sont `null`** : la pièce
> d'identité est une donnée d'état civil sans usage pour l'instruction, et le `login` un identifiant
> d'authentification qu'il n'y a aucune raison de diffuser (il n'existe que pour la réinitialisation de mot
> de passe côté Admin). L'Administrateur continue de recevoir la fiche complète. Effet de bord utile : la
> vue restreinte n'émet **aucune** requête de compte, là où la fiche complète en fait une par ligne.

Le reste de la ressource (liste complète, fiche unitaire, `par-localite`, `par-nom`, écritures, pièces)
demeure réservé à l'**Administrateur**. `GET /par-localite/{idLocalite}` liste les UGPM d'une localité **via la
localité de leur PRMP de tutelle** : l'UGPM n'a pas de localité propre, elle hérite du périmètre de sa PRMP
(rattachée à la localité par ses **entités contractantes actives**, même logique que `GET /api/prmps/par-localite`) —
**liste vide** si aucune PRMP dans la localité (ou aucune UGPM), pas de 404 (filtre). `GET /par-nom/{nom}`
recherche les UGPM par **`nomUgpm`** (contient, **insensible à la casse**) — **liste vide** si aucun résultat,
pas de 404 (filtre).

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| POST | /api/ugpms | `CreerUgpmRequest` (**JSON**, identité + compte) | `UgpmDto` | 201, 400, 403, 409 | **ADMINISTRATEUR** |
| POST | /api/ugpms | **`multipart/form-data`** : part `data` (JSON `CreerUgpmRequest`) + `cin`/`photo` (opt.) | `UgpmDto` | 201, 400, 403, 409 | **ADMINISTRATEUR** |
| GET | /api/ugpms | — | `UgpmDto[]` | 200, 403 | **ADMINISTRATEUR** |
| GET | /api/ugpms/{id} | — | `UgpmDto` | 200, 403, 404 | **ADMINISTRATEUR** |
| GET | /api/ugpms/par-tutelle/{idPrmp} | — | `UgpmDto[]` (vue **restreinte** hors Admin) | 200, 403 | **ADMINISTRATEUR** ; ⚠️ **+ la PRMP concernée** (2026-08-19) ; ⚠️ **+ les profils contrôleurs**, toute tutelle (2026-08-20) |
| GET | /api/ugpms/par-localite/{idLocalite} | — | `UgpmDto[]` | 200, 403 | **ADMINISTRATEUR** |
| GET | /api/ugpms/par-nom/{nom} | — | `UgpmDto[]` | 200, 403 | **ADMINISTRATEUR** |
| PUT | /api/ugpms/{id} | `ModifierUgpmRequest` (**JSON**) | `UgpmDto` | 200, 400, 403, 404, 409 | **ADMINISTRATEUR** |
| PUT | /api/ugpms/{id} | **`multipart/form-data`** : part `data` (JSON `ModifierUgpmRequest`) + `cin`/`photo` (opt.) | `UgpmDto` | 200, 400, 403, 404, 409 | **ADMINISTRATEUR** |
| DELETE | /api/ugpms/{id} | — | — | 204, 403, 404 | **ADMINISTRATEUR** |
| POST | /api/ugpms/suppression-lot | `SuppressionLotUgpmRequest` `{matricules[]}` | `SuppressionLotResult` | 200, 400, 403 | **ADMINISTRATEUR** |
| POST | /api/ugpms/{id}/pieces/{type} | `multipart/form-data` (part `fichier`) ; `type` ∈ `CIN`/`PHOTO` | `PieceJointeMetaDto` | 200, 400, 403, 404 | **ADMINISTRATEUR** |
| GET | /api/ugpms/{id}/pieces/{type} | — ; `type` ∈ `CIN`/`PHOTO` | fichier (binaire) | 200, 400, 403, 404 | **ADMINISTRATEUR** |
| DELETE | /api/ugpms/{id}/pieces/{type} | — ; `type` ∈ `CIN`/`PHOTO` | — | 204, 400, 403, 404 | **ADMINISTRATEUR** |

`CreerUgpmRequest` = `{idUgpm, libelle?, idPrmpTutelle, nomUgpm, prenomsUgpm, cin, dateCin (yyyy-MM-dd),
lieuCin, emailUgpm, telUgpm, login, motDePasse}`. **`idUgpm` = matricule** de l'UGPM (identifiant unifié, comme
les contrôleurs) ; **`idPrmpTutelle` = matricule** de la PRMP de tutelle. L'UGPM porte les **mêmes champs
d'identité que la PRMP, sauf `arreteNomin`/`dateNomin`** ; tous obligatoires (`libelle` reste optionnel). Pas
d'`idLocalite` : l'UGPM hérite du périmètre de sa PRMP de tutelle.

`UgpmDto` = `{idUgpm, libelle, idPrmpTutelle, nomUgpm, prenomsUgpm, cin, dateCin, lieuCin, emailUgpm,
telUgpm, login}`. **`login`** est le login du compte associé, **exposé en lecture seule** (GET/POST/PUT) — pour
pré-remplir la réinitialisation du mot de passe côté admin (`POST /api/comptes-auth/{login}/reinitialiser-mot-de-passe`) ;
le **mot de passe n'est jamais exposé**. **400** si un champ obligatoire manque/est trop long ; **409** si
`idPrmpTutelle` inconnue, `idUgpm` déjà pris, ou `login` déjà utilisé.

`ModifierUgpmRequest` = `{libelle?, idPrmpTutelle, nomUgpm, prenomsUgpm, cin, dateCin, lieuCin, emailUgpm,
telUgpm}` — **champs métier éditables uniquement** : ni `idUgpm` (matricule, porté par l'URL, non modifiable),
ni `login`/`motDePasse` (gestion du compte, hors contrat). **PUT** met à jour ces champs et renvoie le `UgpmDto`
à jour ; **404** si `idUgpm` inconnu, **409** si la nouvelle `idPrmpTutelle` est inconnue (réaffectation possible).

**DELETE** supprime l'UGPM, **ses pièces** (`t_piece_jointe`) **et son compte d'authentification** (créés ensemble) ;
**404** si `idUgpm` inconnu. Les dossiers créés par l'UGPM **restent** la propriété de sa PRMP de tutelle
(`CREE_PAR` est une trace, pas une FK).

**POST `/suppression-lot`** — suppression **en lot par matricule**, **tolérante** : `SuppressionLotUgpmRequest` =
`{matricules: string[]}` (au moins un, sinon **400**) → **200** `SuppressionLotResult` = `{supprimes: string[],
introuvables: string[]}`. Chaque UGPM existante est supprimée (avec son compte) ; les matricules absents sont
listés dans `introuvables` — **jamais d'échec global**. Doublons ignorés.

**Pièces jointes (CIN + photo, pas d'arrêté).** En plus de la variante **JSON pure** (rétro-compatible), `POST
/api/ugpms` accepte une variante **`multipart/form-data`** : part `data` (JSON = `CreerUgpmRequest`) + parts
`cin`/`photo` **optionnelles**. On peut aussi **déposer/remplacer** une pièce ultérieurement via `POST
/api/ugpms/{id}/pieces/{type}` (part `fichier`) et la **télécharger** via `GET /api/ugpms/{id}/pieces/{type}`. La
**modification** `PUT /api/ugpms/{id}` accepte elle aussi une variante **`multipart/form-data`** (part `data` = JSON
`ModifierUgpmRequest` + `cin`/`photo` optionnelles) qui met à jour l'identité **et remplace** les pièces fournies —
une **pièce absente est laissée inchangée** ; la variante **JSON pure** du PUT reste disponible (rétro-compat). Les
pièces sont stockées sous la clé `idUgpm`. Miroir de la PRMP, **sans arrêté** : l'UGPM n'a pas d'arrêté de nomination
→ `type` limité à **`CIN`/`PHOTO`** ; `ARRETE_NOMIN` → **400**. Contraintes fichiers : **PDF/JPEG/PNG** (magic-bytes),
**≤ 5 Mo** chacune ; la **photo doit être une image** (JPEG/PNG, un PDF → **400**). Fichier absent/invalide/trop
volumineux → **400** (annule la création si multipart) ; **404** si l'UGPM ou la pièce est inconnue. On peut
**supprimer une pièce** (sans supprimer l'UGPM) via `DELETE /api/ugpms/{id}/pieces/{type}` → **204** ; **400** si
`type` = `ARRETE_NOMIN`, **404** si l'UGPM ou la pièce est inconnue.

> ⚠️ **Règle ajoutée — PK attribuées par le serveur.** Les identifiants `dossier`/`PPM`/`marché` sont
> **alloués par une séquence serveur** (`seq_dossier`/`seq_ppm`/`seq_marche`) ; tout id envoyé par le
> client est **ignoré**. Les payloads de création **n'envoient plus** `idDossier`/`idPpm`/`idDetail` ;
> l'id figure **en sortie** (réponse). **Dette documentée** : choix d'une séquence applicative (et non
> `IDENTITY` JPA) pour éviter une refonte massive des fixtures sur 3 tables centrales — migration vers
> `IDENTITY` possible ultérieurement.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| POST | /api/saisies/ppm | `SaisiePpmRequest` (JSON) | `DossierDto` (le dossier créé) | 201, 400, 403 | **PRMP** |
| POST | /api/saisies/ppm | `multipart/form-data` (PPM **+ pièces jointes**) | `DossierDto` | 201, 400, 403 | **PRMP** |
| POST | /api/saisies/dossier | `SaisieDossierRequest` | `DossierDto` | 201, 400, 403, 409 | **PRMP** |
| POST | /api/saisies/ppm/import | `multipart/form-data` (part `fichier` = PPM **PDF**) | `SaisiePpmImportResult` | 200, 400, 403 | **PRMP** |
| POST | /api/saisies/ppm/import-xlsx | `multipart/form-data` (part `fichier` = **tableur .xlsx**) | `SaisiePpmImportResult` | 200, 400, 403 | **PRMP** |
| GET | /api/saisies/ppm/import-xlsx/gabarit | — | **`.xlsx`** (gabarit à remplir) | 200, 403 | **PRMP** |
| PUT | /api/saisies/ppm/{idDossier} | `EditionPpmRequest` | `DossierDto` | 200, 400, 403, 404, 409 | **PRMP** |
| POST | /api/saisies/ppm/{idDossier}/mise-a-jour | `MiseAJourRequest` (`{motif}`) | `DossierDto` (la nouvelle version) | 201, 400, 403, 404, 409 | **PRMP propriétaire** |
| POST | /api/saisies/ppm/{idDossier}/mise-a-jour/import | `multipart/form-data` (part `fichier` = PPM **PDF**) | `DiffDossierDto` | 200, 400, 403, 404, 409 | **PRMP propriétaire** |
| GET | /api/dossiers/{idDossier}/diff | — | `DiffDossierDto` | 200, 403, 404, 409 | **PRMP propriétaire OU contrôleur de la localité** (⚠️ lecture élargie 2026-08-15) |
| GET | /api/dossiers/{idDossier}/diff-rectification | — | `DiffDossierDto` | 200, 403, 404, 409 | **PRMP propriétaire OU contrôleur de la localité** (⚠️ nouveau 2026-08-15) |
| GET | /api/dossiers/{idDossier}/versions | — | `DossierDto[]` (plus récente d'abord) | 200, 404 | PRMP / PRESIDENT / CHEF_COMMISSION / MEMBRE / VERIFICATEUR / ADMINISTRATEUR |
| PATCH | /api/marches/{idDetail}/supprimer | — | — | 204, 403, 404, 409 | **PRMP propriétaire** |
| PATCH | /api/marches/{idDetail}/restaurer | — | — | 204, 403, 404, 409 | **PRMP propriétaire** |

> ⚠️ **Mise à jour d'un PPM — versionnement (règle ajoutée 2026-08-05).** Une mise à jour **ne modifie
> jamais** le dossier en place : `POST /api/saisies/ppm/{id}/mise-a-jour` crée un **nouveau dossier**
> `BROUILLON`, copie profonde du précédent (PPM, lignes de marché avec leurs lots, bénéficiaires et dates
> prévisionnelles, pièces jointes dupliquées), rattaché par `t_dossier.ID_DOSSIER_PARENT`. Le compteur
> métier `t_ppm.NUM_MAJ` s'incrémente (`NUM_MAJ_PREC`/`DATE_MAJ_PREC` rappellent la version précédente,
> `DATE_PPM_INIT` se propage inchangée) et **`MOTIF_MAJ` est obligatoire** (400 s'il est vide).
>
> ⚠️ **Ne pas confondre avec la rectification** (`PUT /api/saisies/ppm/{id}` sur un dossier
> `EN_ATTENTE_DECISION_PRMP`), qui corrige la version courante en réponse aux observations du PV : même
> dossier, même identité. Le versionnement, lui, s'applique à un PPM déjà instruit.
>
> ⚠️ **Visibilité des rectifications (règle ajoutée 2026-08-15).** La rectification modifiant la version
> courante **en place**, l'état des lignes **AVANT correction** est figé (`t_snapshot_rectif_ligne`) au
> **premier** `PUT /api/saisies/ppm/{id}` de chaque **cycle** (un cycle = de la transmission des
> observations à la resoumission ; les PUT suivants du même cycle ne re-figent pas). Le **diff du dernier
> cycle** est servi par **`GET /api/dossiers/{id}/diff-rectification`** — **même `DiffDossierDto`** que le
> diff des versions (le front réutilise tel quel son tableau partagé, surlignage `MODIFIEE` + légende) :
> `idDossierPrecedent`/`numMaj` **nuls** (ce n'est pas une comparaison de versions), `motifMaj` = motif de
> la **resoumission** qui a clos le cycle (`null` tant qu'il est ouvert), `fige` = cycle clos. Structure
> figée en rectification ⇒ uniquement des lignes `INCHANGEE`/`MODIFIEE` (appariement direct par
> `idDetail`). **409** si aucune rectification n'est enregistrée (aucun instantané). Après une nouvelle
> transmission d'observations puis une nouvelle rectification, c'est le **nouveau** cycle qui est servi
> (le vérificateur juge toujours le dernier). **Endpoint dédié** (décision backend) : `/diff` garde son
> contrat « mise à jour » (409 si pas de version précédente) — un dossier peut être à la fois une version
> ET porter une rectification, les deux diffs coexistent.
>
> **Lecture des deux diffs — élargie (2026-08-15, demande en attente depuis le 05/08)** : plus réservés à
> la PRMP — **tout-voyant** (Président/Admin), **PRMP propriétaire**, ou **contrôleur de la localité du
> dossier** (vérificateur **titulaire ou délégué** compris — même périmètre que la consultation du
> dossier). Le 403 antérieur privait le circuit du surlignage déjà livré côté front.
>
> **Gardes** — 409 si le dossier source n'est pas `DECISION_TRANSMISE_SIGMP`/`CLOTURE` (on ne versionne
> pas un dossier encore dans le circuit), s'il est déjà `REMPLACE` (versionner la version en vigueur), ou
> si une mise à jour est **déjà en cours** (un seul brouillon de mise à jour par dossier).
>
> **Bascule.** Le prédécesseur passe à **`REMPLACE`** — nouveau statut, jamais supprimé, toujours
> consultable — **à la SOUMISSION** de la nouvelle version, pas à la création du brouillon : une mise à
> jour abandonnée se supprime sans neutraliser le dossier en vigueur. Au même instant le diff est **figé**
> dans `t_changement_ligne` (append-only).
>
> **Identité de ligne.** `t_marche.ID_LIGNE_ORIGINE` porte l'identité d'une ligne **à travers les
> versions** (l'`ID_DETAIL` d'origine, hérité à chaque copie) ; `SUPPRIMEE` marque une suppression
> **logique** (ligne conservée, restaurable, jamais effacée — à exclure des documents officiels).
> Le rapprochement du diff ne dépend **jamais de la position** : des lignes réordonnées à contenu
> identique ressortent `INCHANGEE`. Repli d'appariement pour une ligne sans ancêtre (réimport PDF) :
> libellé normalisé + services bénéficiaires (`apparieePar = LIBELLE_SOA`).
>
> **`DiffDossierDto`** = `recap` (`inchangees`, `modifiees`, `nouvelles`, `supprimees`, `restaurees`,
> `total`) + `lignes[]` (`type` ∈ INCHANGEE | MODIFIEE | NOUVELLE | SUPPRIMEE | RESTAUREE, avec `champs[]`
> `{champ, avant, apres}` pour les modifiées). `fige = false` tant que la version est un brouillon (diff
> recalculé à chaque appel, il doit suivre la saisie) ; `true` une fois soumise (trace figée, qui fait foi).
>
> ⚠️ `GET /api/ppms` **exclut les brouillons** : l'en-tête d'une version en cours (n° de mise à jour,
> motif, référence) se lit à l'unité par `GET /api/ppms/{idPpm}` — l'`idPpm` est porté par ses lignes.

> ⚠️ **Pièces exigées d'une mise à jour (règle ajoutée 2026-08-05).** En plus des pièces obligatoires d'un
> dossier neuf, une version doit porter le **PV du dossier prédécesseur** (type `22`, code `PV_PRECEDENT`)
> et le **PPM daté et signé de CHAQUE version antérieure** (type `23`, `PPM_ANTERIEUR`, une pièce par
> ancêtre). Ces deux types restent `OBLIGATOIRE = false` au **référentiel** — ils n'ont aucun sens sur un
> dossier initial : l'exigence est portée par le code pour les seuls dossiers rattachés à un prédécesseur,
> comme l'obligation conditionnelle de l'AGPM.
>
> Les pièces du dossier d'origine sont **reprises** dans la version (contenu dupliqué) : la PRMP ne
> remplace que celles qui changent. Les deux pièces d'historique, elles, sont **constituées
> automatiquement** à `POST …/mise-a-jour` — l'application détient déjà ces documents (le PV est
> lu/régénéré depuis le FSX), il serait absurde de les faire redéposer. Elles ne sont
> donc jamais recopiées telles quelles du prédécesseur — sinon elles s'empileraient à chaque version — mais
> **reconstituées** depuis la chaîne. À la soumission, si elles manquent (version ouverte avant la règle,
> pièce supprimée par erreur), elles sont **reconstituées puis contrôlées** : un brouillon ne peut pas se
> retrouver dans l'impasse d'un document qu'il n'a aucun moyen de produire.
>
> ⚠️ **Corollaire** : `POST …/mise-a-jour` **409** si le dossier source n'a pas de PV signé — on ne
> versionne pas un plan dont la Commission n'a jamais rendu d'avis. En circuit normal, un dossier clôturé
> porte toujours son PV.

> ⚠️ **Mise à jour PAR IMPORT du PPM PDF (règle ajoutée 2026-08-05, voie NORMALE).** Une mise à jour
> arrive comme un **document**, exactement comme la création : la PRMP importe le plan modifié plutôt que
> de le ressaisir (même principe que la rectification après observations).
> `POST /api/saisies/ppm/{idDossier}/mise-a-jour/import` parse le PDF (même façade read-only que
> `/saisies/ppm/import`), **rapproche** chaque ligne importée d'une ligne de la version — empreinte métier
> (libellé normalisé + services bénéficiaires), puis libellé seul — et lui transmet son `idDetail`. Les
> lignes absentes du document passent en **supprimées** (logiques, restaurables) ; celles qui réapparaissent
> sont **restaurées** ; les inconnues sont créées. La persistance est déléguée à `PUT /api/saisies/ppm/{id}`
> (résolution des référentiels à la volée, validations de montants et de chronologie).
> Les **étapes** du PDF (`LANCEMENT`, `OUVERTURE`…) sont résolues en `idCapm` sur la grille effective du
> mode, avec la MÊME règle qu'à la saisie (égalité de libellé, sinon premier libellé qui le contient) —
> une ligne nouvelle exige au moins un processus. Retourne le **diff recalculé**, à vérifier avant de
> créer la mise à jour. 409 si le dossier n'est pas une version ou n'est plus un brouillon.
>
> ⚠️ **Une mise à jour ne change pas d'entité contractante (règle ajoutée 2026-08-06).** L'entité est
> **héritée** du prédécesseur (champ verrouillé à l'écran) : importer le plan d'un autre organisme
> produirait un dossier incohérent — mêmes identités de lignes, tout autre entité. `POST …/mise-a-jour/import`
> répond donc **409** lorsque le document relève d'une entité différente, **sans rien écrire**. Deux cas :
> l'autorité contractante lue est **résolue** au référentiel et diffère de celle du dossier (comparaison
> d'`idEntiteContract`) ; ou elle n'est **pas résolue** — le libellé lu est alors comparé à celui de
> l'entité du dossier, à la casse, aux accents et à la ponctuation près (inclusion admise), et le refus
> n'est prononcé que s'ils ne se recouvrent manifestement pas. Le message nomme les deux entités. La
> lecture préparatoire `POST /api/saisies/ppm/import` (création d'un dossier neuf) n'est **pas** concernée :
> elle ne vise aucun dossier existant.
>
> Sans ce rapprochement, réimporter le plan ferait apparaître l'intégralité des lignes comme « supprimées
> puis recréées » et l'identité inter-versions serait perdue. Vérifié sur un PPM réel de 32 lignes :
> réimport du même document dans une version suivante → **35 inchangées, 0 modifiée, 0 nouvelle**.

> **Saisie avec pièces jointes (multipart).** La variante `multipart/form-data` de `POST /api/saisies/ppm`
> accepte une part `data` = JSON `SaisiePpmRequest` et des parts fichiers nommées **`piece_<idTypePiece>`**
> (PDF/JPEG/PNG, magic-bytes). Chaque pièce est persistée avec `apresLettreRenvoi=false` (pièce initiale),
> dans la **même transaction** que la saisie (un format invalide annule toute la saisie). Voir
> *Pièces jointes d'un dossier*.

> ⚠️ **Import PPM PDF — pré-remplissage read-only (règle ajoutée).** `POST /api/saisies/ppm/import`
> (part `fichier` = PDF ; **PRMP**) **ne crée rien** : il parse le PDF (PDFBox) et renvoie
> **`SaisiePpmImportResult`** pour pré-remplir le formulaire — la création reste `POST /api/saisies/ppm`.
> Forme : `{ exercice, dateSignature` (« Fait à… le… » sinon **date d'établissement**, `null` sinon)`, autoriteContractante,
> idEntiteContract` (résolu depuis l'autorité si trouvé, sinon `null` → la PRMP choisit)`, marches[]`
> `{ designationMarche, formeMarche, montEstim, nouvMontEstim, idNature+natureLibelle, idMode+modeLibelle, financement,`
> `beneficiaires[]` `{ soaCode, soaLibelle, numCompte, ancMontBenef, nouvMontBenef }, previsions[]` `{ processus, dateDebut },`
> `lots[]` `{ designationLot, montLot?, qteLot?, uniteLot? } },`
> `avertissements[] }`. ⚠️ **`lots[]` — extraction best-effort depuis la désignation (règle révisée 2026-07-17,
> **généralisée 2026-07-22** — remplace « toujours vide »)** : quand la désignation décrit l'allotissement, le
> parser produit `lots[] = [{ designationLot }]` — désignation de lot **seule** (le texte ne porte ni montant ni
> quantité ; **aucun contrôle de somme**, règle actée). **Marqueurs reconnus** (généralisés) : `Lot 01 :`,
> `Lot 1`, `lot n1:`, `LOT N°02 :`, `Lot 1 -` (`n`/`°`/zéros de tête optionnels, terminateur `:` ou `-`),
> séparateur `-` ou accolé. **Annonce** : « répartis/répartie(s) en NN lots » avec le compte en **chiffres**
> et/ou en **lettres** (« deux 2 lots », « trois lots »). **Cas sans annonce** : au moins **2** marqueurs
> « LOT N°NN : » suffisent à extraire — y compris quand la désignation **commence** par le marqueur
> (`LOT N°01:…` en position 0, **aucun objet préalable requis**) et que les lots sont séparés par un simple
> espace. **Contrôle de cohérence** : extraction uniquement si aucun segment n'est vide et le nombre de
> marqueurs **égale** le compte annoncé (ou ≥2 sans annonce) ; sinon → **avertissement** dans
> `avertissements[]`, lots vides, **et anomalie `champ:lot` (`LOT_INCOHERENT`)** pour la revue front. **Décision revisitée (2026-07-18, remplace « désignation raccourcie »)** :
> extraction réussie ou non, `designationMarche` est **conservée intégrale** — l'énumération des lots
> (« répartis en NN Lots : Lot 01 : … ») y reste, **en plus** de `lots[]` ; le doublon texte/structure est
> accepté et voulu. Sans motif d'allotissement → inchangé (lots vides, pas d'avertissement).
> ⚠️ **`formeMarche` — relevée dans l'objet (règle ajoutée 2026-07-18)** : le parser détecte dans
> `designationMarche` les motifs « **contrat cadre** » (avec/sans parenthèses, casse/accents/pluriels libres)
> → `CONTRAT_CADRE` et « **à commande** » / « marché à commande » → `A_COMMANDE` ; sinon **défaut
> `QUANTITE_FIXE`**. Détection à frontières de mots (« la commande » ne matche pas). **Désignation conservée
> intégrale** (même décision que pour les lots : on relève, on ne retire pas) ; jamais null.
> ⚠️ **Fragment d'objet collé au montant — garde d'invariant (règle ajoutée 2026-07-22).** Quand l'objet
> d'un marché s'enroule sur plusieurs lignes et que son fragment final (typiquement un n° de route « RNT 33 »,
> « RNS 44 ») est isolé par PDFBox, deux défaillances survenaient : (a) le fragment collé au montant était
> **absorbé** par la regex de milliers (`33 590 000 000.00` au lieu de `590 000 000.00`) ; (b) le fragment
> seul sur sa ligne physique était pris pour un **n° de page** et **supprimé** (objet tronqué). Corrigé :
> les lignes réduites à un court nombre nu ne sont **plus** filtrées (ce sont des fragments d'objet, pas des
> n° de page — ceux-ci sont en « page X/Y »), et une **garde d'invariant `tête == Σ bénéficiaires`**
> (invariant du document) **détecte** les chiffres de tête excédentaires (1-3) contaminant un montant, les
> **recolle à l'objet** et réaligne. La correction est **tracée** dans `avertissements` (non silencieuse). La
> désignation reste **intégrale** (n° de route compris). ⚠️ **Symétrique (généralisé 2026-07-22)** : la garde
> s'applique à **toutes les colonnes numériques** — le fragment peut contaminer le **montant de tête**
> (`montEstim`/`nouvMontEstim` — « 33 590 000 000 ») **ou** un **montant bénéficiaire** (« 3 125 000 000 » pour
> un estimatif de 125 000 000). Colonne par colonne (ancien puis nouveau), si retirer 1-3 chiffres de tête du
> montant fautif rétablit l'égalité `tête == Σ`, ils sont recollés à l'objet et le montant réaligné. Un écart
> qui **n'est pas** un pur fragment de tête reste `MONTANT_INCOHERENT` (non auto-corrigé).
> ⚠️ **Encodage (règle ajoutée 2026-07-22).** Certains PPM ont une `ToUnicode` défaillante : le caractère
> de remplacement « ¿ » (`U+00BF`) code selon le contexte soit la ligature **œ** (« ¿uvre » = « œuvre »),
> soit une **apostrophe** d'élision (« jusqu¿à » = « jusqu'à »). Le serveur applique des **règles ancrées non
> ambiguës** (jamais un remplacement global aveugle) ; tout « ¿ » **résiduel** est signalé par l'anomalie
> `ENCODAGE_SUSPECT` (ci-dessous), jamais deviné en silence.
>
> ⚠️ **Anomalies de transcription structurées (règle ajoutée 2026-07-22) — clé de la revue front.** Chaque
> `marches[i]` porte **`anomalies[]`** (vide si RAS) et le résultat porte **`nbAVerifier`** (nombre de marchés
> avec ≥1 anomalie). Cela **remplace avantageusement** le balayage de `avertissements[]` à plat (conservé pour
> rétro-compatibilité) : le front pointe la **ligne + le champ exacts**. Forme d'une anomalie :
> `{ champ, type, gravite, corrige?, message }` —
> **`champ`** ∈ `objet|montEstim|nouvMontEstim|mode|nature|beneficiaire|date|lot` ;
> **`type`** ∈ `MONTANT_INCOHERENT|OBJET_TRONQUE_PROBABLE|ENCODAGE_SUSPECT|REFERENTIEL_INCONNU|CHAMP_MANQUANT|LOT_INCOHERENT` ;
> **`gravite`** ∈ `BLOQUANT|A_VERIFIER` ; **`corrige`** = `true` si le backend a **auto-corrigé** (à confirmer
> par l'humain) ; **`message`** prêt à afficher. Règles émises : `MONTANT_INCOHERENT` (montEstim ≠ Σ
> bénéficiaires — `A_VERIFIER` + `corrige:true` si auto-réaligné via l'invariant, sinon `BLOQUANT`) ;
> `OBJET_TRONQUE_PROBABLE` (objet finissant par un préfixe de route `RN|RNT|RNS|RNP|RNC|RIP|RR` sans numéro) ;
> `REFERENTIEL_INCONNU` (nature/mode/SOA/compte non résolus) ; `CHAMP_MANQUANT` (objet/montant/mode absent) ;
> `ENCODAGE_SUSPECT` (« ¿ » résiduel dans l'objet) ; **`LOT_INCOHERENT`** (allotissement décrit mais lots non
> extraits — `champ:lot`, `A_VERIFIER` ; le front la consomme au lieu de sa propre heuristique).
> **Read-only** : les référentiels manquants (`idNature`/`idMode`/`numCompte`/`soaCode`,
> entité) **ne sont pas créés** — renvoyés en libellé seul + listés dans `avertissements` **et** `anomalies` ; la
> création-à-la-volée se fait au `POST /api/saisies/ppm`.
>
> ⚠️ **Import tableur `.xlsx` — transcription exacte (règle ajoutée 2026-07-22).** `POST /api/saisies/ppm/import-xlsx`
> (part `fichier` = `.xlsx`) importe un PPM à **colonnes explicites** : la transcription est **exacte par
> construction** (chaque champ dans sa cellule), sans les pièges de mise en page du PDF (le PDF ne reste qu'un
> justificatif) — c'est la voie vers ~100 %. Renvoie le **même `SaisiePpmImportResult`** (mêmes `anomalies[]` +
> `nbAVerifier`) et reste **read-only** (la création reste `POST /api/saisies/ppm`). L'**assemblage** (résolution
> des référentiels par libellé, forme, anomalies) est **partagé** avec l'import PDF. `GET
> /api/saisies/ppm/import-xlsx/gabarit` télécharge le **gabarit** (`.xlsx` : en-têtes + exemples + notice).
> **Colonnes** (onglet « Marchés », 1 ligne par marché) : `objet` (obl.), `montant estimatif` (obl.), `forme`
> (`A_COMMANDE|CONTRAT_CADRE|QUANTITE_FIXE`, défaut `QUANTITE_FIXE`), `nature`, `nouveau montant`, `mode`,
> `financement`, `soa`, `compte`, `montant beneficiaire` (vide pour 1 seul bénéficiaire = montant estimatif),
> `nouveau montant beneficiaire`, `date lancement/ouverture/attribution` (jj/mm/aaaa ou date Excel),
> `lots` (désignations séparées par « | »), `exercice`, `date signature` (sur la 1re ligne). **Multi-bénéficiaire** :
> une ligne SOUS le marché avec l'`objet` **vide** (continuation : seuls `soa`/`compte`/`montant beneficiaire`).
> Colonnes obligatoires manquantes ou fichier non `.xlsx` → **400**.
> ⚠️ **Résolution des modes/natures (règle révisée 2026-07-18)** : normalisation **étendue** (trim + casse +
> accents + apostrophes/espaces typographiques + **pluriels simples** — un « s » final par token), **même
> fonction** que la création-à-la-volée du POST (source unique). En cas de résolution, `modeLibelle` /
> `natureLibelle` renvoient le **libellé canonique du référentiel** (pas le texte brut du PDF) — les aides
> front (badge/bandeau AGPM, datalist) comparent au libellé exact.
> **Suffixe de source de financement (règle ajoutée 2026-07-25)** : la résolution du **mode** (import **et**
> création-à-la-volée, source unique `LibelleNormalisation`) **ignore un token de source de financement en
> suffixe** — `RPI`, `PIP`. « ACHAT DIRECT **RPI** » → « Achat Direct » (idMode=5) ; « APPEL D'OFFRE OUVERT
> **RPI** » → « Appel d'offres ouvert » (idMode=1, **déclencheur AGPM** — résolu, **jamais recréé** sans le
> drapeau). Désambiguïsation : **source exacte d'abord** (« … **PIP** » → la variante PIP `idMode=8`), sinon
> **repli sur le mode base** sans suffixe (« … **RPI** » → `idMode=4`, **jamais** `idMode=8` « … PIP » :
> `RPI ≠ PIP`). `PPP` **n'est pas** une source (mode à part entière « MARCHE DE GRE A GRE PPP », `idMode=6`).
> Mode **non résolu** malgré la
> normalisation : si un mode du référentiel est **proche** (Levenshtein 1..3 sur formes normalisées, **noyau
> sans suffixe de source**),
> l'avertissement est enrichi — « Mode « X » non trouvé au référentiel — **vouliez-vous dire « Y » ?** » ;
> jamais d'auto-résolution fuzzy. PDF illisible / non-PDF / sans texte → **400** (message
> clair, pas de données partielles silencieuses).
>
> **Parsing du tableau — sémantique par enregistrement** (calibré sur `PPM_26-…` **et** `PPM_26-488-…` MIDSP).
> L'extraction **démarre à la 1ʳᵉ `NATURE` connue** (l'en-tête des colonnes, **même éclaté sur 30+ lignes** —
> `MONTANT`/`ESTIMATIF`/`INITIAL` etc. sur des lignes séparées — est ainsi ignoré) et se termine à la **dernière**
> « **Fait à … le …** » (ou « La personne responsable »). Chaque **enregistrement** (délimité par une `NATURE`) est
> **recomposé** (lignes jointes) puis lu **par position** : `NATURE` → `OBJET` (avant le 1ᵉʳ montant) →
> `montEstim [nouvMontEstim]` → **`mode` | `financement` | `service bénéficiaire`** (voir ci-dessous) → **codes
> SOA** → `compte` → **montants bénéficiaires** → **3 prévisions** `LANCEMENT`/`OUVERTURE`/`ATTRIBUTION`
> (`dd/MM/yyyy` → ISO).
>
> **⚠️ Découpage MODE | FINANCEMENT | SERVICE (règle révisée 2026-07-26 — SIGMP).** Ces trois colonnes sont
> **aplaties dans un même flux de mots** (cellules **multi-lignes** : mode et service enroulés sur plusieurs
> lignes physiques, financement court entre les deux). Un **libellé de FINANCEMENT** (`RPI`/`PIP`/`FR` —
> constante extensible, distincte du suffixe de mode `{RPI,PIP}`) délimite : **mode AVANT**, **financement = ce
> token**, **service APRÈS** (→ `beneficiaires[].soaLibelle`). On coupe sur la **dernière** source de la 1ʳᵉ plage
> contiguë : un mode à variante suffixée (« … **PIP** » = idMode 8) collé au vrai financement (« … PIP **RPI** »)
> **conserve son PIP**, financement = RPI ; un `FR` plus loin dans un service n'est pas capté. Sans financement
> reconnu → repli sur l'ancienne heuristique (dernier mot avant le 1ᵉʳ SOA, ex. `FCE` suivi d'un SOA codé). Un
> fragment d'en-tête enroulé (`FINAN-`, `NOUVEAU`, …) n'est plus filtré comme préfixe (« **Finan**cier »,
> « **Nouveau**x » restaient perdus) : filtrage **ancré en fin de ligne** uniquement.
>
> **`soaLibelle` (règle ajoutée 2026-07-26).** Quand la colonne « SERVICE BÉNÉFICIAIRE » est en **texte libre**
> (sans code SOA, ex. « Service Administratif et Financier », « TOUT SERVICE »), le nom alimente
> `beneficiaires[].soaLibelle` (`soaCode` reste `null`). À la **persistance** (`POST /api/saisies/ppm`), le
> service est **résolu-ou-créé par libellé** dans `tr_soa_beneficiaire` (dé-doublonnage sur libellé normalisé ;
> à la création, code SOA **dérivé du libellé**, slug ≤ 25). Un `soaCode` explicite (ancien format) reste résolu
> par PK.
>
> **Multi-bénéficiaires.** Un marché peut porter **plusieurs bénéficiaires** (colonnes SOA/compte/montants aplaties
> verticalement par l'extraction) : `n` codes SOA et `K` montants ⇒ `K = 2n` (ancien **et** nouveau montant par
> bénéficiaire) ou `K = n` (ancien seul) ; le `compte` est partagé. `beneficiaires[]` est renvoyé complet.
>
> **`NATURE`** reconnue en **MAJUSCULES** (`FOURNITURES`, `TRAVAUX`, `PRESTATIONS DE SERVICE` — y compris **sur 2
> lignes**) comme en casse « titre » (`Fournitures et services`, `Travaux`, …). **Autorité contractante** sur
> plusieurs lignes **recomposée** (lignes en majuscules jusqu'à la prochaine étiquette). Nature/mode hors
> référentiel → `id*` `null` + libellé conservé + avertissement.
>
> **Multi-pages.** Toutes les pages sont lues ; le bornage sur la **dernière** « Fait à … » évite qu'un pied de
> page répété tronque le tableau. En-têtes/sous-en-têtes de colonnes rejoués, filigrane (`powered by …`), numéro
> de page (`Page n [sur m]`, `n / m`, en-tête courant `PPM_… page n/m`) et « Fait à … » intermédiaire sont **ignorés**.

**`SaisiePpmRequest`** — crée dossier (type PPM) + PPM + lignes de marché (mode **auto**) :

| Champ | Type | Obligatoire |
|---|---|---|
| **idEntiteContract** | number | **Oui** — entité contractante concernée (fixe la localité) |
| exercice | number | Oui |
| dateSignature | string (date) | Oui |
| marches | `SaisieMarcheLigne[]` | Non |

*(plus de `idDossier`/`idPpm` : attribués par le serveur.)*

> ⚠️ **Référence & signataire auto-générés (règle ajoutée).** `signataire` et `reference` ne sont **plus saisis**
> (retirés de l'entrée). Le serveur les génère à la création du brouillon et les expose dans `PpmDto` (sortie) :
> - **`reference`** = `<séquence>/<acronyme entité>/PPM/<année>` (ex. `00001/DGB/PPM/2026`), compteur **par
>   (entité, année)** ; l'**acronyme** est dérivé du `LIBELLE_ENTITE` (initiales des mots significatifs :
>   « Direction Générale du Budget » → `DGB`).
> - **`signataire`** = « prénoms + nom » de la **PRMP connectée** (`t_prmp`), repli sur l'identifiant PRMP.
>
> Modifiables ensuite via la **rectification** (en attente de décision PRMP), pas à la création.

**`SaisieMarcheLigne`** : `designationMarche`, **`formeMarche`**, `numCompte`, `montEstim`, **`nouvMontEstim`** (→ `t_marche.NOUV_MONT_ESTIM`), `financement`, `statut`, `idNature`, `natureLibelle`, `idMode`, `modeLibelle`, **`beneficiaires[]`**, **`lots[]`**. `idDetail` est **facultatif** — **null à la création** (PK serveur), renseigné seulement pour **identifier une ligne existante** lors de l'édition (réconciliation). `idDossier`/`idPpm` sont renseignés par le service. **`idMode`** = mode **saisi** (facultatif) ; **conservé tel quel** (plus de détermination automatique — `t_situation`/`t_regle_passation`/`t_seuil` retirés). **`nouvMontEstim`**, **`beneficiaires[]`** et **`lots[]`** sont **optionnels** (rétro-compatible).

> ⚠️ **`formeMarche` — forme du marché (règle ajoutée 2026-07-18).** Notion réglementaire à **liste fermée**
> (enum contrôlé serveur, pas de référentiel en table) : **`A_COMMANDE`** (« Marché à commande »),
> **`CONTRAT_CADRE`** (« Contrat cadre »), **`QUANTITE_FIXE`** (« À quantité fixe »). **Optionnel** partout
> (rétro-compatible) : absent/vide → **défaut serveur `QUANTITE_FIXE`** — le champ n'est **jamais null** en base
> ni en sortie ; code inconnu → **400 ciblé** (« Forme de marché inconnue : … »). Accepté à `POST /api/saisies/ppm`
> et à l'édition (`PUT /api/saisies/ppm/{idDossier}`), comme à `POST`/`PUT /api/marches` (colonne
> `t_marche.FORME_MARCHE`). **Reprise des données** : les lignes historiques sont migrées au démarrage
> (`FormeMarcheMigration`, idempotente — ne touche que les colonnes `NULL`) en **dérivant la forme de la
> désignation** avec les mêmes motifs que l'import, sinon `QUANTITE_FIXE`.

> ⚠️ **Nature / mode / compte — résolution-ou-création à la volée (règle ajoutée).** Pour l'**import PPM** :
> si `idNature` (resp. `idMode`) est **absent** mais `natureLibelle` (resp. `modeLibelle`) est fourni, le service
> **résout** le référentiel par **libellé normalisé** dans `tr_nature` (resp. `tr_mode_passation`),
> ou le **crée à la volée** (PK = `max+1`) s'il n'existe pas — dé-doublonnage sur le libellé normalisé. De même, **`numCompte`**
> (compte du marché) est **résolu-ou-créé** dans `tr_compte` (PK = le numéro lui-même) pour éviter la violation FK
> `t_marche.NUM_COMPTE`. **Résolution = réutilisation de l'existant, jamais suppression/recréation.** Créations
> **tracées** dans `t_audit_log` (`TYPE_ACTION=CREATION_A_LA_VOLEE`). Si l'`id*` est **présent**, le libellé associé est **ignoré**.
>
> ⚠️ **Normalisation ÉTENDUE des libellés (règle révisée 2026-07-18 — ferme le contournement AGPM).** La
> normalisation de résolution (source unique serveur `LibelleNormalisation`, partagée import + création-à-la-volée)
> = trim + casse + accents **+ apostrophes/espaces typographiques neutralisés + pluriels simples** (suppression
> d'un « s » **final par token** ; pas de « x »). Ainsi « APPEL D'OFFRE OUVERT » (coquille PDF, singulier)
> **résout** vers « Appel d'offres ouvert » (`idMode=1`, `declencheAgpm`) au lieu de créer un quasi-doublon sans
> drapeau — la pièce AGPM reste exigée et le sous-type dérive bien en `PPM-AGPM`. **Portée** : libellés **modes
> et natures** (même mécanique) ; **pas** les codes SOA/comptes (identifiants exacts). **Signal de proximité** :
> si un mode est malgré tout créé à la volée avec un libellé **proche** (Levenshtein ≤ 3 sur formes normalisées)
> d'un mode `declencheAgpm=true`, la création est **permise** mais **signalée** — log WARN + audit
> `TYPE_ACTION=CREATION_MODE_PROCHE_AGPM` (pas d'avertissement de réponse : `DossierDto` n'a pas de champ
> avertissements) — pour arbitrage Admin (fusion / coche du drapeau).
> **Bénéficiaires par marché (règle ajoutée).** `beneficiaires[]` (optionnel) = une ligne **`t_service_beneficiaire`**
> par élément `{ soaCode, soaLibelle, numCompte, ancMontBenef, nouvMontBenef }`. Le service (SOA) est
> **résolu-ou-créé** dans `tr_soa_beneficiaire` (audit `CREATION_A_LA_VOLEE`) : par **PK** si `soaCode` fourni
> (ancien format), sinon par **libellé normalisé** si seul `soaLibelle` est fourni (⚠️ règle ajoutée 2026-07-26,
> texte libre SIGMP « TOUT SERVICE » — code SOA **dérivé du libellé**, slug ≤ 25) ; `numCompte` dans `tr_compte` —
> même logique (réutilisation, jamais suppression). **Cohérence des montants** (⚠️ **uniquement si `beneficiaires[]` non vide**,
> **égalité exacte** — Ariary entiers, pas de tolérance) : `Σ ancMontBenef = montEstim` ; et si `nouvMontEstim` est
> **fourni**, chaque bénéficiaire doit porter `nouvMontBenef` et `Σ nouvMontBenef = nouvMontEstim`. Écart → **400**
> ciblé : `{ "erreurs": [ { "champ": "marches[i].beneficiaires", "message": "La somme des montants par bénéficiaire
> (…) doit égaler le montant estimatif du marché (…)." } ] }`. `beneficiaires[]` absent/vide → **aucune vérification**.
>
> **Lots par marché (allotissement, règle ajoutée).** `lots[]` (optionnel) = une ligne **`t_lot`** par élément
> `{ designationLot, montLot?, qteLot?, uniteLot? }` (= `LotDto` **sans** `idLot`/`idDossier`/`idDetail`, renseignés
> par le serveur — PK allouée, dossier et marché du contexte). `designationLot` est **obligatoire** (`@NotBlank`,
> max 200) ; `montLot`/`qteLot`/`uniteLot` sont **descriptifs** → **aucun contrôle de somme** (contrairement aux
> bénéficiaires). `lots[]` absent/vide → **aucun lot** (rétro-compatible).

⚠️ **`processus`** : `ProcessusMarche[]` — **chaque marché doit comporter au moins un processus à la création** (`POST /api/saisies/ppm`), sinon **400** `{ "erreurs": [ { "champ": "marches[0].processus", "message": "Au moins un processus est obligatoire." } ] }`. Chaque **`ProcessusMarche`** = `idCapm` (FK `t_capm`, **obligatoire**), `dateDebut` (`yyyy-MM-dd`, **obligatoire**) et **`dateFin` (`yyyy-MM-dd`, OPTIONNELLE** — fin non connue / ouverte) — `idCapm`/`dateDebut` manquant → **400** au chemin `marches[i].processus[j].<champ>` (« Le processus est obligatoire. » / « La date de début est obligatoire. ») ; `idCapm` **inconnu** → **400**. `dateFin` **absente = accepté** (pas de 400). Le service crée **une ligne `t_marche_prevision` par processus**. *(À l'édition d'un brouillon, `processus` n'est pas exigé.)*

⚠️ **Cohérence chronologique des processus** (par marché, processus triés par `t_capm.ordre` ASC) — validée à la **création** (`POST /api/saisies/ppm`) et à l'**édition** (`POST`/`PUT /api/marche-previsions`). Chaque règle n'est appliquée **que si la/les `dateFin` concernée(s) sont présentes** (une `dateFin` absente = ouverte, non contraignante) :
> 1. **Interne** : `dateDebut < dateFin` pour chaque processus **dont la `dateFin` est fournie**, sinon **400** (champ `…dateFin` — « La date de fin doit être postérieure à la date de début. »).
> 2. **Séquence** : `dateDebut[n] >= dateFin[n-1]` entre processus consécutifs — **uniquement si `dateFin[n-1]` est présente** (sinon la contrainte est ignorée pour ce couple) ; violation → **400** (champ `…dateDebut` — « La date de début du processus *[libellé n]* doit être postérieure ou égale à la date de fin du processus précédent *[libellé n-1]*. »).
>
> À la saisie, le champ porte le chemin `marches[i].processus[j].<champ>` ; à l'édition d'une prévision, le nom du champ seul (`dateDebut`/`dateFin`).

**`SaisieDossierRequest`** (familles DMC/DDM, sans contenu) : **`idSousType`** (sous-type choisi, ex. `DAO`,
`DAOR`, `MAOO`, `MAOR` — la **famille s'en déduit** ; inconnu → **400** `{champ:"idSousType"}` ; sous-type de la
famille **DDP** → **409** « utilisez /api/saisies/ppm »), **`idEntiteContract` (oui)**. *(plus de `idDossier` :
attribué par le serveur.)* ⚠️ `idTypeDossier` est **déprécié** : accepté en repli quand `idSousType` est absent
et interprété comme un code de **sous-type** (les anciens payloads `{"idTypeDossier":"DAO"}` restent valides).

**`EditionPpmRequest`** (`PUT /api/saisies/ppm/{idDossier}`) — édite un **brouillon** PPM en une transaction :
`exercice`, `signataire`, `dateSignature`, `reference` (en-tête, tous obligatoires) + `marches` (liste désirée). Les lignes sont **réconciliées par `idDetail`** : ajout des nouvelles, mise à jour des existantes (mode **conservé tel quel** — saisi), **retrait** des absentes. La localité/le type/le propriétaire/l'entité ne changent pas. Dossier ni BROUILLON ni EN_ATTENTE_DECISION_PRMP → **409** ; non-propriétaire → **403**.

> ⚠️ **Rectification PAR IMPORT du PPM (règle 2026-08-02, demande user).** La rectification d'un dossier
> **`EN_ATTENTE_DECISION_PRMP`** se fait par l'**importation du PPM rectifié (PDF)** — plus de formulaire
> manuel : le front parse le PDF (`POST /api/saisies/ppm/import`, read-only), prévisualise dans la grille
> partagée, puis enregistre via **le même `PUT /api/saisies/ppm/{idDossier}`** (accepté à ce statut,
> propriétaire). En rectification la **STRUCTURE est FIGÉE** : chaque ligne fournie doit porter l'`idDetail`
> d'une ligne existante (mise à jour **en place** — l'examen et le périmètre des observations référencent
> les lignes) ; **ajout → 409**, **retrait → 409**. L'entité du PDF doit être celle du dossier (garde front
> au parse) ; signataire/référence actuels conservés. `PUT /api/ppms/{id}` et `PUT /api/marches/{id}`
> (appelés par la façade) acceptent aussi ce statut pour le propriétaire ; **create/delete restent
> BROUILLON uniquement**. Le statut reste `EN_ATTENTE_DECISION_PRMP` jusqu'à la resoumission
> (`POST /api/dossiers/{id}/resoumettre`). Les PATCH `…/rectifier` (édition manuelle champ à champ)
> subsistent côté API mais ne sont plus le parcours UI. ⚠️ La rectification couvre AUSSI les **pièces
> jointes** (observations « pièce » du PV) : la PRMP joint la **version corrigée** — nouvel upload du
> même type (`POST /api/piece-jointe-dossiers`, autorisé au statut, propriétaire), l'**original est
> conservé** (traçabilité, DELETE réservé brouillon). ⚠️ 2026-08-03 : un dépôt pendant la rectification
> est **marqué `versionCorrigee=true`** (colonne `VERSION_CORRIGEE`, posée serveur au statut
> `EN_ATTENTE_DECISION_PRMP`) — les listes de pièces la distinguent de l'originale : section
> « **Versions corrigées (rectification)** » + étiquette « Corrigée » (verte, comme « LR » pour les
> pièces après lettre de renvoi) dans la consultation du dossier et le détail PPM.
> L'écran « Rectifier » ne liste QUE les pièces
> **citées dans les observations du PV** (et les versions du même type, dont celles jointes en
> rectification) — badge « Observation du PV — version corrigée attendue » sur celles visées par une
> observation non levée (pont `ObservationPvDto.idExamenPiece` → `t_examen_piece.ID_PIECE`) ; aucune
> observation de pièce → section absente.

> ⚠️ **Sous-objets des lignes à l'édition (règle corrigée 2026-07-18).** Le PUT traitait l'en-tête et les
> colonnes du marché mais **ignorait silencieusement** `beneficiaires[]`, `lots[]` et `processus[]` (enfants des
> anciennes lignes supprimés par cascade, ceux des nouvelles jamais créés). Désormais, **mêmes traitements et
> validations qu'au POST** : bénéficiaires (+ contrôle **Σ**), lots, processus (+ **cohérence chronologique**,
> **≥1 processus obligatoire par ligne _nouvelle_**). **Sémantique par ligne _mise à jour_** (`idDetail`
> fourni) : liste **fournie** = **remplacement complet** des enfants de ce type ; liste **absente**
> (`undefined`/`null`) = enfants **conservés**. Remplacer les `processus[]` par une liste **vide** est refusé
> (**400**, invariant « ≥1 processus par marché ») ; `beneficiaires[]`/`lots[]` vides = retrait de tous.

> 📌 **Modification d'un dossier BROUILLON par la PRMP — parcours réel (endpoints existants).** Il n'existe **pas** de façade `/api/dossiers/{id}/...` pour l'édition partielle : chaque partie se modifie via sa ressource propre, **toutes gardées par la même règle** — *dossier en `BROUILLON`* **et** *`idPrmp` == PRMP connectée* (sinon **403**/**409**). L'**entité** et la **localité** ne sont **jamais** modifiables (elles déterminent la référence du dossier).
>
> | Cible | Endpoint réel | Corps | Réponse |
> |---|---|---|---|
> | En-tête (+ remplacement des lignes en une transaction) | `PUT /api/saisies/ppm/{idDossier}` | `EditionPpmRequest` (`exercice`, `signataire`, `dateSignature`, `reference`, `marches[]`) | 200 `DossierDto` |
> | Ajouter une ligne de marché | `POST /api/marches` | `MarcheDto` (`idDossier`, `designationMarche`, `montEstim`, `idNature`…) — **mode calculé auto** | 201 `MarcheDto` |
> | Modifier une ligne de marché | `PUT /api/marches/{idMarche}` | `MarcheDto` — **mode recalculé** si montant/nature change | 200 `MarcheDto` |
> | Supprimer une ligne de marché | `DELETE /api/marches/{idMarche}` | — (⚠️ cascade prévisions + bénéficiaires + lots/tranches) | 204 |
> | Ajouter une pièce jointe | `POST /api/piece-jointe-dossiers` (`multipart`) | part `data` = `PieceJointeDossierDto` (`idDossier`, `idTypePiece`, `apresLettreRenvoi`) + part `fichier` | 201 `PieceJointeDossierDto` |
> | Supprimer une pièce jointe | `DELETE /api/piece-jointe-dossiers/{idPj}` | — (fichier + entrée supprimés) | 204 |
>
> *(Aucun champ `libelle` d'en-tête : l'en-tête PPM se compose de `exercice`/`signataire`/`dateSignature`/`reference`. La désignation d'une ligne est `designationMarche`, son montant `montEstim`.)*

> **Localité dérivée de l'ENTITÉ.** Le champ `idLocalite` n'est **pas** saisi : la PRMP **choisit une
> entité contractante** parmi **ses** entités actives (`t_prmp_entite`), et la **localité du dossier en
> est dérivée** (`tr_entite_contract.idLocalite`). Une même PRMP liée à des entités de localités
> différentes peut donc déposer dans plusieurs localités. Erreurs : entité **non rattachée** à la PRMP
> → **403** ; entité **sans localité** → **400**. L'`idPrmp` propriétaire est **forcé** à l'utilisateur
> courant. Le dossier reste **BROUILLON** (invisible des contrôleurs) jusqu'à
> `POST /api/dossiers/{id}/soumettre`.

**Exemple — requête `POST /api/saisies/ppm`** (`idEntiteContract` fixe la localité, pas de `idLocalite`)
```json
{
  "idEntiteContract": 1, "exercice": 2026, "dateSignature": "2026-01-10",
  "marches": [ { "designationMarche": "Travaux X", "montEstim": 500000000, "idNature": 1, "idMode": 4, "statut": "PREVU" } ]
}
```

---

## Échéances
**Ressource** `/api/echeances` — Lecture / écriture : tout utilisateur authentifié.

**Champs `EcheanceDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idEcheance | number | Oui (PK, au POST) | clé primaire |
| idDetail | number | Oui | @NotNull |
| typeJalon | string | Oui | @NotBlank, max 30 |
| datePrevue | string (date) | Oui | @NotNull |
| dateReelle | string (date) | Non | |
| statutJalon | string | Non | max 20 |
| ecartJours | number | Non | |
| alerteEnvoyee | boolean | Non | |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/echeances | — | `EcheanceDto[]` | 200 | Authentifié |
| GET | /api/echeances/{id} | — | `EcheanceDto` | 200, 404 | Authentifié |
| POST | /api/echeances | `EcheanceDto` | `EcheanceDto` | 201, 400 | Authentifié |
| PUT | /api/echeances/{id} | `EcheanceDto` | `EcheanceDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/echeances/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idEcheance (number).

**Exemple — requête**
```json
{ "idEcheance": 510, "idDetail": 77, "typeJalon": "OUVERTURE", "datePrevue": "2026-06-01", "dateReelle": null, "statutJalon": "A_VENIR", "ecartJours": null, "alerteEnvoyee": false }
```

---

## Entités contractantes
**Ressource** `/api/entite-contracts` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `EntiteContractDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idEntiteContract | number | Oui (PK, au POST) | clé primaire |
| libelleEntite | string | Oui | @NotBlank, max 150 (aligné sur `libelleMinistere`) |
| adresse | string | Oui | @NotBlank, max 200 |
| categorieEntite | string | Non | max 20 — **validé** au référentiel `tr_categorie_entite` (400 si inconnu) |
| idOrganigramme | number | Oui | @NotNull |
| idEntiteParent | number | Non | |
| niveauHierarchique | number | **Dérivé (lecture seule)** | ⚠️ **DÉRIVÉ** de `categorieEntite` au POST/PUT (source unique) — la valeur envoyée par le client est **ignorée** |
| idLocalite | string | Non | max 5 — **localité de l'entité** (FK `tr_localite`) ; détermine la localité des dossiers la concernant |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/entite-contracts | — | `EntiteContractDto[]` | 200 | Authentifié |
| GET | /api/entite-contracts/{id} | — | `EntiteContractDto` | 200, 404 | Authentifié |
| POST | /api/entite-contracts | `EntiteContractDto` | `EntiteContractDto` | 201, 400, 403 | **PRMP ou ADMINISTRATEUR** |
| PUT | /api/entite-contracts/{id} | `EntiteContractDto` | `EntiteContractDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/entite-contracts/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idEntiteContract (number). **PK assignée client** : `idEntiteContract = max(ids)+1` (aussi bien PRMP qu'Admin).
`idEntiteParent` **facultatif** (null accepté — rattachement par organigramme seul, ex. ministère sans entité racine).

> ⚠️ **Création par la PRMP + auto-rattachement (règle ajoutée 2026-07-26).** Le **POST** est ouvert à la
> **PRMP** (en plus de l'Admin) — cas import PPM : autorité contractante hors périmètre → la PRMP enregistre une
> nouvelle entité. Quand l'appelant est une **PRMP**, le backend crée en même temps un lien
> [`/api/prmp-entites`](#ressource-apiprmp-entites--affectations-prmpentité-contractante-31) **`actif=false`
> (EN ATTENTE)** PRMP↔entité, qu'un **Administrateur approuve** (`PUT {actif:true}`) ou **rejette** (`DELETE`).
> PUT/DELETE de l'entité elle-même restent **Administrateur**.

> ⚠️ **Dérivation du niveau hiérarchique (règle ajoutée 2026-07-26).** À **POST** et **PUT**, `niveauHierarchique`
> est **dérivé** de `categorieEntite` via le référentiel [`/api/categorie-entites`](#catégories-dentité--référentiel-ajouté-2026-07-26)
> (`tr_categorie_entite`) — **source unique**, l'entité et sa catégorie ne peuvent plus diverger. Catégorie
> **inconnue** du référentiel → **400** ; catégorie absente/vide → `categorieEntite`=`null` et `niveauHierarchique`=`null`.
> La valeur `niveauHierarchique` du corps est **ignorée** en écriture (elle reste renseignée en lecture).

**Exemple — requête**
```json
{ "idEntiteContract": 7, "libelleEntite": "Direction Générale des Marchés", "adresse": "Antananarivo", "categorieEntite": "MINISTERE", "idOrganigramme": 2, "idEntiteParent": 1, "niveauHierarchique": 2, "idLocalite": "ANT" }
```

---

## Entités-PRMP
**Ressource** `/api/prmp-entites` — Affectations PRMP↔entité contractante (§3.1).
- **Lecture** : utilisateur authentifié, mais **scopée** — l'Administrateur voit toutes les
  affectations ; une **PRMP** ne voit que **les siennes** ; tout autre profil → liste vide
  (accès direct hors périmètre → **403**).
- **Écriture** (POST/PUT/DELETE) : réservée à l'**Administrateur**, qui gère les affectations.
- **Invariant d'unicité** : une entité ne peut être rattachée qu'à **une seule PRMP active** ;
  toute tentative d'**activer** une entité déjà rattachée activement → **409**. Une PRMP peut gérer
  **plusieurs** entités. Les affectations sont **stables** (pas de transfert d'une PRMP à une autre).
- ⚠️ **Auto-rattachement EN ATTENTE (règle ajoutée 2026-07-26).** Quand une **PRMP** crée une entité
  contractante (`POST /api/entite-contracts`, cf. Entités contractantes), le backend crée
  **automatiquement** un lien PRMP↔entité **`actif=false`** (en attente d'approbation). L'invariant
  d'unicité **ne bloque pas** cette création (le lien est en attente) ; il s'applique à l'**activation**.
  **Approuver** = `PUT /api/prmp-entites/{id}` `{actif:true}` (Administrateur → **409** si une autre PRMP
  est déjà active sur l'entité). **Rejeter** = `DELETE /api/prmp-entites/{id}`. Une fois **actif=true**,
  l'entité apparaît dans le `GET /api/prmp-entites` scopé de la PRMP (le front filtre `actif=true`).

**Champs `PrmpEntiteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPrmpEntite | number | Non (PK **générée côté serveur**) | clé primaire |
| idPrmp | string | Oui | @NotBlank, max 10 ; la PRMP doit exister (sinon 400) |
| idEntiteContract | number | Oui | @NotNull ; l'entité doit exister (sinon 400) |
| dateAffectation | string (date) | Non | défaut = date du jour |
| actif | boolean | Oui | @NotNull ; **création directe (POST) toujours active** ; l'**auto-rattachement** à la création d'entité par une PRMP est **EN ATTENTE** (`actif=false`) |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/prmp-entites | — | `PrmpEntiteDto[]` | 200 | Authentifié (scopé) |
| GET | /api/prmp-entites/{id} | — | `PrmpEntiteDto` | 200, 403, 404 | Authentifié (scopé) |
| POST | /api/prmp-entites | `PrmpEntiteDto` | `PrmpEntiteDto` | 201, 400, 403, 409 | Administrateur |
| PUT | /api/prmp-entites/{id} | `PrmpEntiteDto` | `PrmpEntiteDto` | 200, 400, 403, 404, 409 | Administrateur |
| DELETE | /api/prmp-entites/{id} | — | — | 204, 403, 404 | Administrateur |

`{id}` = idPrmpEntite (number).

**Exemple — requête** (le serveur génère `idPrmpEntite` et la date par défaut)
```json
{ "idPrmp": "PRMP001", "idEntiteContract": 8, "actif": true }
```

---

## Examens
**Ressource** `/api/examens` — POST/PUT : profil `MEMBRE` (titulaire ou délégué) ; DELETE : `ADMINISTRATEUR`. Écriture limitée à sa localité (dossier hors localité → 403, sauf Président). Lecture filtrée par localité.

> **Précondition de circuit (création) → 409** : le dossier (via `dispatch → réception`) doit avoir été **dispatché**, statut **`DISPATCHE`** (§2.4). ⚠️ *Changé* : l'examen n'accepte plus `PRET_DISPATCH` — il faut d'abord créer le dispatch (qui pose `DISPATCHE`).
>
> **Autorisation (création) → 403** : un **Membre titulaire** n'examine que les dossiers **qui lui sont attribués** (`Dispatch.imCtrlMembre`) ; un CC/Président **par délégation** (§3.5) reste autorisé.
>
> **Transition (⚠️ règle déplacée 2026-08-01)** : le dossier ne passe **`DISPATCHE` → `EXAMINE`** qu'à la **SOUMISSION** de l'examen (`POST /{id}/soumettre`, même transaction que le projet de PV) — il quitte alors « à examiner ». La **création** d'un examen est désormais un **brouillon de progression** (le front sauvegarde les résultats à chaque étape ; le dossier reste `DISPATCHE`, reprise possible). Le verrou d'écriture des examens/détails accepte donc `DISPATCHE` (brouillon) **ou** `EXAMINE` — refus 409 dès `PV_SIGNE`, inchangé.
>
> **Verrou (édition) → 409** : `PUT /api/examens/{id}` **et** les écritures sur `/api/examen-details` (création/MAJ/suppression) sont **refusées dès `PV_SIGNE`** : l'examen est modifiable tant que le dossier est `EXAMINE` (navette ouverte), **définitif** après signature du PV.

**Champs `ExamenDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idExamen | number | Oui (PK, au POST) | clé primaire |
| idDispatch | number | Oui | @NotNull |
| imCtrlMembre | string | Non | max 7 |
| dateExamen | string (date) | Non | |
| avisSuggere | string | — (réponse, `GET /{id}`) | ⚠️ **règle ajoutée 2026-07-21** — avis **suggéré** non contraignant : **`DEF`** (défavorable) si ≥1 point non conforme, sinon **`FAV`** ; **`null`** tant qu'aucun point n'est évalué. Le membre reste **maître de l'avis final** (`idAvis` à la soumission) ; sert au pré-remplissage front |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/examens | — | `ExamenDto[]` | 200 | Authentifié (filtré) |
| GET | /api/examens/{id} | — | `ExamenDto` | 200, 404 | Authentifié (filtré) |
| POST | /api/examens | `ExamenDto` | `ExamenDto` | 201, 400, 403 | MEMBRE (titulaire/délégué) |
| PUT | /api/examens/{id} | `ExamenDto` | `ExamenDto` | 200, 400, 403, 404 | MEMBRE (titulaire/délégué) |
| DELETE | /api/examens/{id} | — | — | 204, 404 | ADMINISTRATEUR |
| POST | /api/examens/{id}/soumettre | `ExamenSoumissionRequest` | `PvExamenDto` | 201, 400, 403, 404 | MEMBRE |

`{id}` = idExamen (number).

> ⚠️ **Soumission de l'examen (règle MODIFIÉE 2026-08-01).** `POST /api/examens/{id}/soumettre` produit
> **toujours un Projet de PV** (`PvExamenService`, `idPv` alloué serveur). Corps `ExamenSoumissionRequest`
> `{ idAvis?, idSecretaireSeance? }` : les deux champs sont désormais **OPTIONNELS** — le Membre ne renseigne
> que la **synthèse des observations** (PUT du PV) ; l'avis global et le Secrétaire de séance sont posés à la
> **clôture de la navette** (`POST /api/pv-examens/{id}/accepter`, Président/CC — voir section PV). S'ils sont
> fournis, `idSecretaireSeance` doit être un VERIFICATEUR **titulaire** de la **localité du dossier** ou un
> contrôleur couvert par une paire « → Vérificateur » **active** (⚠️ élargi 2026-08-15 ; sinon **400**
> `{ erreurs:[{ champ:"idSecretaireSeance", message }] }`). Le projet de PV = **résultats des points de
> contrôle + synthèse du Membre** (avis `null` à ce stade). *(La lettre de renvoi appartient aussi à la
> clôture de navette — ressource `/api/lettre-renvois`, Président/CC.)*
>
> ⚠️ **Complétude de l'examen à la soumission (règle ajoutée 2026-07-21).** Avant de produire le Projet de PV,
> le serveur vérifie que **toutes les lignes ont été traitées** : chaque point de **portée LIGNE** de la
> **grille effective** du dossier doit être évalué **pour chaque marché**, et chaque point de **portée DOSSIER**
> évalué **une fois**. Sinon → **400** `{ erreurs:[{ champ:"grille", message }] }` (le message liste les
> évaluations manquantes : point × marché). C'est ce contrôle **données** qui garantit « toutes les lignes
> traitées » — le workflow séquentiel/couleurs est géré côté front. **Vacant** (aucune exigence) si le dossier
> n'a **pas de grille** (famille/sous-type sans points) → examens historiques et non-PPM non contraints.
>
> ⚠️ **PV — document généré (règle ajoutée ; modèles étendus 2026-08-03 ; génération post-commit 2026-08-19).**
> À la **signature finale** du PV (passage à `SIGNE`), le **PDF officiel** est généré **en tâche de fond
> après commit** (la signature répond immédiatement ; `CHEMIN_DOCUMENT` est renseigné quand le document est
> prêt — cf. le contrat `documentDisponible` §PvExamenDto), **s'il existe un modèle Word pour le cas** — conditions
> communes : dossier de **localité centrale** (`ANT`, seuls modèles fournis) et **PPM** comportant au moins une
> ligne de marché, **quel que soit le mode de passation**. Choix du modèle (`PvDocumentService.modelePour`) :
>
> **12 modèles** `PV_{AFSR|AF|ANF}_{PPMAGPM|PPM}_{CENTRALE|REGIONALE}.docx`, choisis sur **trois axes** :
>
> | axe | valeurs | effet sur le document |
> |---|---|---|
> | **avis** | `FAVR` → `AFSR` / `FAV` → `AF` / `DEF` → `ANF` | `AFSR` : clause « sous réserve … » + **ANNEXE** des observations ; `AF` : « émet un **AVIS FAVORABLE** … », ni clause ni annexe (1 page) ; `ANF` : « émet un **AVIS NON FAVORABLE** … », ni clause ni annexe |
> | **sous-type** (dérivé serveur des marchés déclencheurs) | `PPM-AGPM` / `PPM` | mention « … et d'Avis Général de Passation des Marchés INITIAL » (intitulé) et « … et à la publication de l'AGPM » (avis) |
> | **localité** du circuit (réception) | `ANT` → `CENTRALE` / autre → `REGIONALE` | en-tête « COMMISSION **REGIONALE** DES MARCHES » + ligne **localité**, titre et mentions « Commission **Régionale** » |
>
> Seul l'avis `NSP` (« ne se prononce pas ») n'a **aucun modèle** → aucun document produit.
> ⚠️ La variante `FAVR` **sans AGPM** (centrale et régionale) est **dérivée** de la règle AGPM des modèles
> fournis — **à valider par le métier** : « … émet un AVIS FAVORABLE à l'affichage du PPM sous réserve
> qu'il soit tenu compte des observations portées en annexe. ».
>
> 📌 **Lieu d'établissement.** La ligne de signature (« A …, le … ») porte le **chef-lieu**
> (`tr_localite.CHEF_LIEU`, repli sur le libellé) — marqueur dédié `<CHEF LIEU>` (la graphie
> `<CHEF-LIEU>` est également acceptée) — tandis que l'en-tête régional porte la **localité** (région)
> via `<LOCALITE>`.
>
> 📌 **Résilience de la conversion (2026-08-04).** Word (documents4j) peut s'arrêter entre deux
> conversions : le convertisseur en cache est désormais **recréé** s'il n'est plus opérationnel et la
> conversion est **retentée une fois**. Auparavant, un seul arrêt de Word faisait échouer (409) toutes
> les générations de PV **et** de lettres jusqu'au redémarrage du serveur. Le PDF est produit à partir du
> modèle Word retenu (copie du modèle + remplacement des placeholders ; date d'examen
> formatée et **en toutes lettres** dans « L'an … » ; bloc « Étaient présents » filtré sur les signataires
> effectifs ; ANNEXE = une ligne par observation des points non conformes, **préfixée par la ligne de marché**
concernée — « [Marché « désignation »] point » pour un résultat par ligne, « [Dossier] point » pour un point
inter-lignes ou historique (⚠️ 2026-07-21, sans modification du gabarit Word) ; ⚠️ 2026-08-01 : les **pièces
jointes non conformes** (`t_examen_piece`) sont ajoutées à la suite de l'ANNEXE — RÉFÉRENCES = **libellé de
la pièce seul** (sans préfixe), OBSERVATIONS = texte libre de l'observation, les libellés « Au lieu de : /
Lire : » de la ligne modèle étant retirés) puis converti via Microsoft Word
> (documents4j) et **stocké sur le FSX** (`storage.pv-examen.path`, sous-répertoire `PV/`), chemin conservé dans
> `t_pv_examen.CHEMIN_DOCUMENT`. Hors de ces conditions, le PV reste **sans document**. Le téléchargement
> **régénère le document à la demande** si le chemin est absent ou le fichier introuvable (migration des PV
> signés avant ce correctif). _Pré-requis machine/CI : Word installé._

**Exemple — requête (examen)**
```json
{ "idExamen": 201, "idDispatch": 88, "imCtrlMembre": "MEMANT1", "dateExamen": "2026-05-08" }
```
**Exemple — corps `…/soumettre`** *(⚠️ 2026-08-01 : corps vide accepté — avis/secrétaire posés à la clôture de navette)*
```json
{}
```

---

## Lettres de renvoi
**Ressource** `/api/lettre-renvois` (table `t_lettre_renvoi`) — ⚠️ **règle MODIFIÉE 2026-08-01** : la lettre
de renvoi est une action de la **clôture de la navette du projet de PV**, réservée au **Président / Chef de
Commission** (auparavant : Membre pendant l'examen). N lettres possibles par examen (indépendamment du Projet
de PV). Lecture filtrée par profil/localité. Cycle : `BROUILLON → SOUMIS → SIGNE` (signature CC ou Président).

**Champs `LettreRenvoiDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idLettre | number | — (réponse) | PK **auto-générée** (IDENTITY) |
| idExamen | number | Oui | @NotNull (« L'examen est obligatoire. ») — FK `t_examen` (**non unique** : N lettres/examen) |
| idDossier | number | — (réponse) | **lecture seule** (dérivé de l'examen) |
| refLettre | string | — (réponse) | **générée serveur** : `<seqLettreGlobal>/<sous_type>/<code_localite>/LR/<année>` (ex. `00001/PPM/CNM/LR/2026` en central, `00001/PPM-AGPM/CRM-TMS/LR/2026` en région). Le **segment sous-type / localité / année** est **repris tel quel** du `refeDossier` du dossier (donc suit automatiquement le segment sous-type, tiret compris), mais le **numéro de séquence est un compteur GLOBAL dédié aux lettres** (par année, **strictement unique et continu** tous dossiers/entités/localités confondus — ≠ le numéro du dossier). `null` si `refeDossier` non structuré |
| corpsLettre | string | Non | corps libre de la lettre (TEXT, sans limite de taille) |
| dateExamen | string (date) | — (réponse) | **lecture seule** (date d'examen) |
| dateLettre | string (date) | — (réponse) | **posée serveur** (jour) |
| statut | string | — (réponse) | `BROUILLON`/`SOUMIS`/`SIGNE` — **forcé** (ignoré en entrée) |
| imSignataire | string | — (réponse) | **posé à la signature** (JWT) — ignoré en entrée |
| nomSignataire | string | — (réponse) | **nom complet du signataire** (« prénoms nom »), peuplé serveur — lecture seule |
| lue | boolean | — (réponse) | **lecture seule** — `true` si la lettre a déjà été lue par la PRMP courante (trace `t_lettre_renvoi_lue`) |

> **Objet fixe** : l'objet de la lettre est constant (« lettre de renvoi », déjà inscrit en dur dans les modèles Word) — il n'est **plus saisi ni retourné** (champ `objetLettre` supprimé du DTO). S'il est encore envoyé dans le corps de la requête, il est **ignoré** (compat rétroactive du frontend). La colonne `t_lettre_renvoi.OBJET_LETTRE` reste en base pour l'historique mais n'est plus alimentée.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/lettre-renvois | — | `LettreRenvoiDto[]` | 200 | Authentifié (filtré, voir ci-dessous) |
| GET | /api/lettre-renvois/mes-lettres | — | `LettreRenvoiDto[]` | 200 | **PRMP** — lettres `SIGNE` de ses dossiers (lecture seule) |
| GET | /api/lettre-renvois/{id} | — | `LettreRenvoiDto` | 200, 403, 404 | Authentifié (dans le périmètre) **ou PRMP propriétaire** (lettre `SIGNE`) — voir marquage « lu » |
| GET | /api/lettre-renvois/{id}/document | — | fichier **PDF** | 200, 403, 404 | Authentifié (périmètre) — document de la lettre signée |
| POST | /api/lettre-renvois | `LettreRenvoiDto` | `LettreRenvoiDto` | 201, 400, 403 | **CHEF_COMMISSION / PRESIDENT** (⚠️ 2026-08-01) — création à la clôture de navette (BROUILLON) |
| PUT | /api/lettre-renvois/{id} | `LettreRenvoiDto` | `LettreRenvoiDto` | 200, 400, 404, 409 | **CHEF_COMMISSION / PRESIDENT** (brouillon : corps) |
| POST | /api/lettre-renvois/{id}/soumettre | — | `LettreRenvoiDto` | 200, 403, 404, 409 | **CHEF_COMMISSION / PRESIDENT** (BROUILLON→SOUMIS) |
| POST | /api/lettre-renvois/{id}/signer | — | `LettreRenvoiDto` | 200, 403, 404, 409 | **CHEF_COMMISSION** (toutes localités) ou **PRESIDENT** (localité **centrale ANT** uniquement) — voir règle |
| DELETE | /api/lettre-renvois/{id} | — | — | 204, 404 | ADMINISTRATEUR |

> **Scoping `GET /api/lettre-renvois`** : MEMBRE → **ses** lettres (par ses examens) ; CHEF_COMMISSION →
> lettres `SOUMIS` de sa localité ; ASSISTANT_CONTROLEUR → lettres `SIGNE` de sa localité ;
> Président/Administrateur → toutes.
>
> **Création** (POST) : examen **inexistant ou hors périmètre** → **403**. **Signature** (`signer`, CC ou
> Président — jamais le Membre → **403**) `SOUMIS → SIGNE` (`imSignataire` = JWT) → **notifie la PRMP**
> du dossier (`LETTRE_RENVOI_RECUE`) et les **Assistants contrôleurs** de la localité (`LETTRE_RENVOI_COPIE`).
> Statut incorrect → **409**. ⚠️ **La signature rouvre le circuit** : le dossier examiné repasse
> **`EXAMINE → PRET_DISPATCH`** (réception/dispatch/examen/lettre **conservés**), afin que la PRMP puisse déposer
> les pièces manquantes (`apresLettreRenvoi=true`) ; le **premier** dépôt le fait avancer à `DISPATCHE` et notifie
> le Membre (cf. `POST /api/piece-jointe-dossiers`).
>
> **Marquage « lu » à la consultation PRMP (⚠️ règle ajoutée).** La **PRMP propriétaire** du dossier peut
> consulter le détail d'une lettre **`SIGNE`** via `GET /api/lettre-renvois/{id}` (au-delà du périmètre de
> localité habituel, qui sinon lui renvoie 403). À cette consultation, la lettre est **marquée lue** pour
> elle (trace `t_lettre_renvoi_lue`, **une seule entrée** par couple lettre/PRMP, opération idempotente et
> silencieuse). Le champ `lue` du DTO reflète cet état, et le compteur **« Mes lettres de renvoi »** du
> menu PRMP ne compte que les lettres `SIGNE` **non encore lues** (voir KPIs / `CompteursPrmpDto`).
>
> **Signature selon la localité (⚠️ règle ajoutée).** La localité de la lettre est celle du **dossier**
> (`idLocalite`), avec **repli** sur la localité de **réception** si absente. Localité **centrale `ANT`** →
> signature par **CC ou Président** ; localité **régionale** (toute autre) → **Chef de Commission
> uniquement** (Président → **403**, message « Seul le Chef de Commission peut signer une lettre de renvoi
> pour une localité régionale. »).
>
> **Document PDF (⚠️ règle ajoutée).** À la signature, le **PDF** de la lettre est **généré** puis **stocké
> sur le système de fichiers (FSX)** dans le répertoire **`LR/`** (`storage.lettre-renvoi.path`), sous le nom
> **`{refLettre}.pdf`** (les `/` remplacés par `_`, ex. `00007_PPM_CNM_LR_2026.pdf`) ; le chemin est
> conservé dans `t_lettre_renvoi.CHEMIN_DOCUMENT`. Téléchargeable via `GET /api/lettre-renvois/{id}/document`
> (PDF), dans le périmètre de la lettre (lecture du fichier FSX, repli sur `DOCUMENT_PDF` pour les anciennes
> lettres). Le PDF est produit **à partir du modèle Word fourni** (`resources/templates/LR_CENTRALE.docx`
> pour la localité centrale `ANT`, `LR_REGIONALE.docx` sinon) : **copie du `.docx`** + **remplacement des
> placeholders** (`<DATE_LETTRE>`, `<NOM_ENTITE_CONTRACT>`, `<REFERENCE DOSSIER>`, `<DATE EXAMEN>`,
> `<CORPS DE LA LETTRE>`, le nom du signataire, et `<LOCALITE DOSSIER>` pour le régional) **avec fusion des
> runs scindés** (Apache POI XWPF), puis **conversion docx→PDF via Microsoft Word** (documents4j local) pour
> un rendu **fidèle au modèle** (positionnement des pointillés d'en-tête et du signataire conformes à Word).
> La mise en forme et l'**emblème** du modèle sont conservés ; le nom du signataire remplace uniquement le
> placeholder (aucun libellé de rôle ajouté). _Pré-requis machine/CI : Microsoft Word installé (automation COM)._

---

## Indicateurs contrôleur
**Ressource** `/api/indicateur-ctrls` — Lecture / écriture : tout utilisateur authentifié.

**Champs `IndicateurCtrlDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idIndicateur | number | Oui (PK, au POST) | clé primaire |
| imControleur | string | Oui | @NotBlank, max 7 |
| periode | string | Oui | @NotBlank, max 7 |
| nbExamens | number | Non | |
| nbConformes | number | Non | |
| delaiMoyenExamen | number | Non | |
| nbObsEmises | number | Non | |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/indicateur-ctrls | — | `IndicateurCtrlDto[]` | 200 | Authentifié |
| GET | /api/indicateur-ctrls/{id} | — | `IndicateurCtrlDto` | 200, 404 | Authentifié |
| POST | /api/indicateur-ctrls | `IndicateurCtrlDto` | `IndicateurCtrlDto` | 201, 400 | Authentifié |
| PUT | /api/indicateur-ctrls/{id} | `IndicateurCtrlDto` | `IndicateurCtrlDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/indicateur-ctrls/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idIndicateur (number).

**Exemple — requête**
```json
{ "idIndicateur": 101, "imControleur": "MEMANT1", "periode": "2026-05", "nbExamens": 42, "nbConformes": 35, "delaiMoyenExamen": 3.5, "nbObsEmises": 18 }
```

---

## Indicateurs PRMP
**Ressource** `/api/indicateur-prmps` — Lecture / écriture : tout utilisateur authentifié.

**Champs `IndicateurPrmpDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idIndicateurPrmp | number | Oui (PK, au POST) | clé primaire |
| idPrmp | string | Oui | @NotBlank, max 10 |
| exercice | number | Oui | @NotNull |
| nbPpmSoumis | number | Oui | @NotNull |
| nbDossiersSoumis | number | Oui | @NotNull |
| nbDossiersConformes | number | Oui | @NotNull |
| nbDossiersNonConformes | number | Oui | @NotNull |
| nbRetours | number | Oui | @NotNull |
| nbRetraits | number | Oui | @NotNull |
| tauxConformite | number | Non | |
| delaiMoyCorrectionJours | number | Non | |
| montTotalSoumis | number | Non | |
| dateMaj | string (date-time) | Non | |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/indicateur-prmps | — | `IndicateurPrmpDto[]` | 200 | Authentifié |
| GET | /api/indicateur-prmps/{id} | — | `IndicateurPrmpDto` | 200, 404 | Authentifié |
| POST | /api/indicateur-prmps | `IndicateurPrmpDto` | `IndicateurPrmpDto` | 201, 400 | Authentifié |
| PUT | /api/indicateur-prmps/{id} | `IndicateurPrmpDto` | `IndicateurPrmpDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/indicateur-prmps/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idIndicateurPrmp (number).

**Exemple — requête**
```json
{
  "idIndicateurPrmp": 7, "idPrmp": "PRMP001", "exercice": 2026, "nbPpmSoumis": 12,
  "nbDossiersSoumis": 58, "nbDossiersConformes": 47, "nbDossiersNonConformes": 11,
  "nbRetours": 9, "nbRetraits": 2, "tauxConformite": 81.03,
  "delaiMoyCorrectionJours": 4.25, "montTotalSoumis": 1450000000.0, "dateMaj": "2026-06-10T14:32:00"
}
```

---

## Instantanés de statistiques
**Ressource** `/api/snapshot-statss` *(double « s » final)* — Lecture / écriture : tout utilisateur authentifié.

**Champs `SnapshotStatsDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idSnapshot | number | Oui (PK, au POST) | clé primaire |
| dateSnapshot | string (date) | Oui | @NotNull |
| idLocalite | string | Non | max 5 |
| exercice | number | Oui | @NotNull |
| nbDossiersRecus | number | Non | |
| nbDossiersClotures | number | Non | |
| nbDossiersEnCours | number | Non | |
| tauxConformite | number | Non | |
| delaiMoyenJours | number | Non | |
| montTotalControle | number | Non | |
| nbRetoursMoyen | number | Non | |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/snapshot-statss | — | `SnapshotStatsDto[]` | 200 | Authentifié |
| GET | /api/snapshot-statss/{id} | — | `SnapshotStatsDto` | 200, 404 | Authentifié |
| POST | /api/snapshot-statss | `SnapshotStatsDto` | `SnapshotStatsDto` | 201, 400 | Authentifié |
| PUT | /api/snapshot-statss/{id} | `SnapshotStatsDto` | `SnapshotStatsDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/snapshot-statss/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idSnapshot (number).

---

## Journaux d'audit
**Ressource** `/api/audit-logs` — Réservé à `ADMINISTRATEUR` pour **toutes** les opérations (lecture comprise). Le journal est alimenté **automatiquement** par le système. **DELETE interdit → 409** (journal immuable, §3.8).

**Champs `AuditLogDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idLog | number | Oui (PK, au POST) | clé primaire |
| dateAction | string (date-time) | Oui | @NotNull |
| imActeur | string | Non | max 7 |
| nomTable | string | Non | max 50 |
| idEnregistrement | string | Non | max 20 |
| typeAction | string | Non | max 10 |
| champModifie | string | Non | max 50 |
| ancienneValeur | string | Non | |
| nouvelleValeur | string | Non | |
| ipAdresse | string | Non | max 45 |
| sessionId | string | Non | max 100 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/audit-logs | — | `AuditLogDto[]` | 200 | ADMINISTRATEUR |
| GET | /api/audit-logs/{id} | — | `AuditLogDto` | 200, 404 | ADMINISTRATEUR |
| POST | /api/audit-logs | `AuditLogDto` | `AuditLogDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/audit-logs/{id} | `AuditLogDto` | `AuditLogDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/audit-logs/{id} | — | — | **409 (interdit)** | ADMINISTRATEUR |

`{id}` = idLog (number).

---

## KPIs / Tableau de bord
**Ressource** `/api/kpis` — `GET /api/kpis/tableau-bord` réservé à `PRESIDENT`, `ADMINISTRATEUR` et
`CHEF_COMMISSION`. Lecture seule.

> **Périmètre selon le profil (§3.3).** Président/Administrateur → **toutes localités** (global) ;
> **Chef de commission** → KPIs **filtrés sur sa localité** (pipeline, conformité et non-conformité du
> périmètre de sa localité ; CC sans localité → tableau vide). Aucun paramètre : le périmètre découle du profil.

**Champs `TableauBordDto`** (réponse)

| Champ (JSON) | Type | Description |
|---|---|---|
| pipelineParStatut | object (`Map<string, number>`) | nombre de dossiers par statut |
| nbDossiersSoumis | number | dossiers **soumis** (statut ≠ `BROUILLON`) du périmètre — dénominateur du taux ; les brouillons restent visibles dans `pipelineParStatut` |
| nbDossiersConformes | number | dossiers conformes (observations levées) |
| tauxConformitePct | number | conformes / soumis × 100 |
| topNonConformite | `PointNonConformiteDto[]` | top 5 des points de contrôle non conformes |
| compteurs | `CompteursDto` | compteurs de contenu par section du menu (Président) — voir ci-dessous |

**Champs `CompteursDto`** — par section du menu ; **globaux** (toutes localités) pour Président/Administrateur, **filtrés sur la localité** pour le Chef de commission

| Champ (JSON) | Type | Description |
|---|---|---|
| predispatch | number | dossiers prêts à dispatcher (`t_dossier.STATUT = PRET_DISPATCH`) |
| dispatch | number | dossiers dispatchés (`t_dossier.STATUT = DISPATCHE`) |
| projetsPV | number | projets de PV non signés (`t_pv_examen.STATUT_PV ≠ SIGNE`) |
| lettresRenvoi | number | lettres de renvoi soumises (`t_lettre_renvoi.STATUT = SOUMIS`) |
| pvDefinitifs | number | PV signés (`t_pv_examen.STATUT_PV = SIGNE`) |
| demandesRetrait | number | demandes de retrait en attente (`t_demande_retrait.STATUT = EN_ATTENTE`) |

**Champs `PointNonConformiteDto`**

| Champ (JSON) | Type | Description |
|---|---|---|
| idPointCtrl | number | identifiant du point de contrôle |
| libelle | string | libellé du point |
| nbTotal | number | total d'occurrences examinées |
| nbNonConforme | number | occurrences non conformes |
| tauxNonConformitePct | number | taux de non-conformité (%) |

**Champs `CompteursPrmpDto`** (réponse de `mes-compteurs`) — par section du menu **PRMP**, tous filtrés sur la PRMP authentifiée (JWT)

| Champ (JSON) | Type | Description |
|---|---|---|
| brouillons | number | mes dossiers en brouillon (`t_dossier.STATUT = BROUILLON`) |
| ppmMarches | number | mes PPM & marchés (PPM de la PRMP, `t_ppm.ID_PRMP`, **hors BROUILLON** — colle à la liste `GET /api/ppms`) |
| dossiersARectifier | number | mes dossiers à rectifier non traités (`t_dossier.STATUT = EN_ATTENTE_DECISION_PRMP`) |
| dossiersVerifies | number | mes dossiers vérifiés (`t_dossier.STATUT IN (PV_SIGNE, CLOTURE)`) |
| lettresRenvoi | number | mes lettres de renvoi signées **non encore lues** (`STATUT = SIGNE` sans trace dans `t_lettre_renvoi_lue` pour la PRMP) — voir marquage « lu » dans *Lettres de renvoi* |
| demandesRetraitNouvelles | number | mes demandes de retrait passées à `ACCEPTEE`/`REFUSEE` (`DATE_DECISION`) **depuis ma dernière consultation** de l'écran « Demandes de retrait » — voir marquage dans *Demandes de retrait* |

**Champs `CompteursVerificateurDto`** (réponse de `mes-compteurs-verificateur`) — par section du menu **Vérificateur**, filtrés sur sa localité (miroir de ses worklists)

| Champ (JSON) | Type | Description |
|---|---|---|
| aVerifier | number | dossiers à vérifier (`STATUT IN (EN_VERIFICATION, EN_ATTENTE_DECISION_PRMP)`) |
| verifies | number | dossiers vérifiés/clôturés (`STATUT = CLOTURE` avec PV `SIGNE`) |
| enAttentePrmp | number | dossiers en attente de décision PRMP (`STATUT = EN_ATTENTE_DECISION_PRMP`) |

**Champs `CompteursSecretaireDto`** (réponse de `mes-compteurs-secretaire`) — par section du menu **Secrétaire**, filtrés sur sa localité

| Champ (JSON) | Type | Description |
|---|---|---|
| aReceptionner | number | dossiers à réceptionner (`STATUT = SOUMIS`, sans réception, de sa localité) |
| receptions | number | réceptions enregistrées dans sa localité (historique) |

**Champs `CompteursMembreDto`** (réponse de `mes-compteurs-membre`) — par section du menu **Membre**, filtrés sur le Membre attributaire (son IM)

| Champ (JSON) | Type | Description |
|---|---|---|
| aExaminer | number | dossiers à examiner (`STATUT = DISPATCHE` qui lui sont attribués) |
| examines | number | dossiers examinés (`STATUT IN (EXAMINE, PV_SIGNE, EN_VERIFICATION, CLOTURE)`) |

**Champs `CompteursPublicationDto`** (réponse de `mes-compteurs-publication`) — workflow de publication, comptes **globaux**

| Champ (JSON) | Type | Description |
|---|---|---|
| aPublier | number | publications à publier (`t_publication.STATUT_PUBLI = EN_ATTENTE`) |
| publiees | number | publications publiées (`STATUT_PUBLI = PUBLIE`) |
| retirees | number | publications retirées (`STATUT_PUBLI = RETIRE`) |

**Champs `CompteursAssistantDto`** (réponse de `mes-compteurs-assistant`) — documents signés de sa localité

| Champ (JSON) | Type | Description |
|---|---|---|
| lettresRenvoi | number | lettres de renvoi signées de sa localité (`t_lettre_renvoi.STATUT = SIGNE`) |
| pvDefinitifs | number | PV définitifs (signés) de sa localité (`t_pv_examen.STATUT_PV = SIGNE`) |

**Champs `CompteursAdminDto`** (réponse de `mes-compteurs-admin`) — comptes **globaux** (rôle transversal)

| Champ (JSON) | Type | Description |
|---|---|---|
| inscriptionsEnAttente | number | inscriptions PRMP en attente de validation (`t_compte_auth.STATUT = EN_ATTENTE`, type PRMP) |
| comptes | number | nombre total de comptes d'authentification |
| journalAudit | number | nombre total d'entrées du journal d'audit |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/kpis/badges | — | `BadgesDto` | 200 | **Tout authentifié** — ⚠️ audit front 2026-08-16, voir note ci-dessous |
| GET | /api/kpis/tableau-bord | — | `TableauBordDto` | 200, 403 | PRESIDENT / ADMINISTRATEUR / CHEF_COMMISSION |
| GET | /api/kpis/mes-compteurs | — | `CompteursPrmpDto` | 200, 403 | **PRMP** (compteurs de son propre périmètre) |
| GET | /api/kpis/mes-compteurs-verificateur | — | `CompteursVerificateurDto` | 200, 403 | **VERIFICATEUR** (ou délégué) / ADMINISTRATEUR — compteurs de sa localité |
| GET | /api/kpis/mes-compteurs-secretaire | — | `CompteursSecretaireDto` | 200, 403 | **SECRETAIRE** (ou délégué) / ADMINISTRATEUR — compteurs de sa localité |
| GET | /api/kpis/mes-compteurs-membre | — | `CompteursMembreDto` | 200, 403 | **MEMBRE** (ou délégué) / ADMINISTRATEUR — compteurs de ses dossiers attribués |
| GET | /api/kpis/mes-compteurs-publication | — | `CompteursPublicationDto` | 200, 403 | **CHARGE_PUBLICATION** / ADMINISTRATEUR — workflow de publication (global) |
| GET | /api/kpis/mes-compteurs-assistant | — | `CompteursAssistantDto` | 200, 403 | **ASSISTANT_CONTROLEUR** / ADMINISTRATEUR — documents signés de sa localité |
| GET | /api/kpis/mes-compteurs-admin | — | `CompteursAdminDto` | 200, 403 | **ADMINISTRATEUR** — inscriptions, comptes, audit (global) |

> ⚠️ **Badges de menu agrégés (audit front 2026-08-16).** `GET /api/kpis/badges` renvoie en **un appel** les compteurs du **rôle du connecté** (routage serveur sur le profil du JWT) : `{ "profil": "<PROFIL>", "compteurs": { … } }`. Le champ `compteurs` porte **le même DTO** que l'endpoint `mes-compteurs*` du rôle (`CompteursPrmpDto` pour la PRMP ; **`CompteursDto` du tableau de bord** pour Président — global — et Chef de commission — sa localité, dont `predispatch` ; `CompteursSecretaireDto` avec `aReceptionner` ; etc.) — le front réutilise ses lecteurs existants et **cesse de rejouer les endpoints de liste pour lire des `.length`**. Profil sans compteurs → `compteurs` vide.

**Exemple — réponse**
```json
{
  "pipelineParStatut": { "PRET_DISPATCH": 5, "DISPATCHE": 8, "EXAMINE": 21, "PV_SIGNE": 6, "CLOTURE": 47, "RETIRE": 2 },
  "nbDossiersSoumis": 75, "nbDossiersConformes": 47, "tauxConformitePct": 62.67,
  "topNonConformite": [
    { "idPointCtrl": 14, "libelle": "Absence de pièce justificative", "nbTotal": 58, "nbNonConforme": 22, "tauxNonConformitePct": 37.93 }
  ],
  "compteurs": { "predispatch": 5, "dispatch": 8, "projetsPV": 3, "lettresRenvoi": 1, "pvDefinitifs": 6, "demandesRetrait": 2 }
}
```

> **Compteurs de contenu (⚠️ règle ajoutée).** L'objet `compteurs` donne, par section (pré-dispatch,
> dispatch, projets de PV, lettres de renvoi soumises, PV signés, demandes de retrait en attente), des
> comptes **globaux** (toutes localités) pour le **Président/Administrateur** et **filtrés sur sa
> localité** pour le **Chef de commission** — cohérent avec le périmètre du reste du tableau de bord.
> Scope localité : Dossier/Demande via `idLocalite`, PV/Lettre via la localité de la réception.

**Exemple — réponse `GET /api/kpis/mes-compteurs`** (PRMP)
```json
{ "brouillons": 3, "ppmMarches": 12, "dossiersARectifier": 1, "dossiersVerifies": 7, "lettresRenvoi": 2 }
```

> **Compteurs PRMP (⚠️ règle ajoutée).** `GET /api/kpis/mes-compteurs` (réservé **PRMP**) renvoie les
> compteurs des sections du menu PRMP, **tous filtrés sur la PRMP authentifiée** (JWT) : « Mes brouillons »,
> « Mes PPM & marchés », « Dossiers à rectifier » non traités (`EN_ATTENTE_DECISION_PRMP`), « Dossiers
> vérifiés » (`PV_SIGNE`/`CLOTURE`), « Mes lettres de renvoi » signées.

**Exemple — réponse `GET /api/kpis/mes-compteurs-verificateur`** (Vérificateur)
```json
{ "aVerifier": 4, "verifies": 18, "enAttentePrmp": 1 }
```

> **Compteurs Vérificateur (⚠️ règle ajoutée).** `GET /api/kpis/mes-compteurs-verificateur` (réservé
> **VERIFICATEUR** ou délégué) renvoie les compteurs de ses trois worklists, **filtrés sur sa localité**
> (via la réception) : « à vérifier », « vérifiés/clôturés », « en attente décision PRMP ». Un dossier
> `EN_ATTENTE_DECISION_PRMP` est compté **à la fois** dans `aVerifier` (lecture seule) et `enAttentePrmp`,
> comme dans les écrans.

**Exemple — réponse `GET /api/kpis/mes-compteurs-secretaire`** (Secrétaire)
```json
{ "aReceptionner": 6, "receptions": 23 }
```

> **Compteurs Secrétaire (⚠️ règle ajoutée).** `GET /api/kpis/mes-compteurs-secretaire` (réservé
> **SECRETAIRE** ou délégué) renvoie, **filtrés sur sa localité** : `aReceptionner` (sa file de dossiers
> `SOUMIS` sans réception, miroir de `/api/dossiers/a-receptionner`) et `receptions` (nombre de réceptions
> de sa localité, via le contrôleur réceptionnaire).

**Exemple — réponse `GET /api/kpis/mes-compteurs-membre`** (Membre)
```json
{ "aExaminer": 2, "examines": 15 }
```

> **Compteurs Membre (⚠️ règle ajoutée).** `GET /api/kpis/mes-compteurs-membre` (réservé **MEMBRE** ou
> délégué) renvoie, **filtrés sur le Membre attributaire** (son IM via `Dispatch.imCtrlMembre`) :
> `aExaminer` (ses dossiers `DISPATCHE` + `A_REEXAMINER`, miroir de `/api/dossiers/a-examiner`) et
> `examines` (son historique : `EXAMINE`/`PV_SIGNE`/`EN_VERIFICATION`/`CLOTURE`).

**Exemple — réponse `GET /api/kpis/mes-compteurs-publication`** (Chargé de publication)
```json
{ "aPublier": 4, "publiees": 31, "retirees": 2 }
```

> **Compteurs Chargé de publication (⚠️ règle ajoutée).** `GET /api/kpis/mes-compteurs-publication`
> (réservé **CHARGE_PUBLICATION**) renvoie des comptes **globaux** (rôle transversal, sans localité) du
> workflow de publication : `aPublier` (`EN_ATTENTE`), `publiees` (`PUBLIE`), `retirees` (`RETIRE`).

**Exemple — réponse `GET /api/kpis/mes-compteurs-assistant`** (Assistant contrôleur)
```json
{ "lettresRenvoi": 3, "pvDefinitifs": 9 }
```

> **Compteurs Assistant contrôleur (⚠️ règle ajoutée).** `GET /api/kpis/mes-compteurs-assistant`
> (réservé **ASSISTANT_CONTROLEUR**) renvoie, **filtrés sur sa localité** (via la réception), les
> documents signés qu'il distribue : `lettresRenvoi` (lettres de renvoi `SIGNE`) et `pvDefinitifs`
> (PV `SIGNE`).

**Exemple — réponse `GET /api/kpis/mes-compteurs-admin`** (Administrateur)
```json
{ "inscriptionsEnAttente": 2, "comptes": 48, "journalAudit": 1530 }
```

> **Compteurs Administrateur (⚠️ règle ajoutée).** `GET /api/kpis/mes-compteurs-admin` (réservé
> **ADMINISTRATEUR**) renvoie des comptes **globaux** : `inscriptionsEnAttente` (inscriptions PRMP
> `EN_ATTENTE` à valider), `comptes` (total des comptes d'authentification), `journalAudit` (total des
> entrées du journal d'audit). L'Administrateur conserve par ailleurs la vue globale du `tableau-bord`.

---

## Localités
**Ressource** `/api/localites` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

> ⚠️ **Champs `referencement` et `localite` (code max 3) RETIRÉS du contrat (2026-07-17).** Colonnes
> héritées du MLD **sans aucune sémantique** : jamais lues par la génération des références, les documents
> (PV/lettres) ni les jobs ; valeurs dupliquant (`localite` = ANT/TMS = PK) ou dérivant
> (« REF-&lt;id&gt; ») la PK. L'admin devait pourtant les saisir (@NotBlank). Retirées du DTO, de la
> validation et de l'entité ; colonnes BD **dépréciées** (conservées, rendues nullables — migrations
> `2026-07-17_localite_referencement_deprecie.sql` et `2026-07-17_localite_code_deprecie.sql`). Des valeurs
> encore envoyées par un ancien front sont **ignorées**. → L'écran admin se réduit à **id / libellé**.
>
> 📌 **Étiquetage front.** Le segment localité des références officielles est bâti sur la **PK
> `idLocalite`** : **`CNM`** pour la localité **centrale** (`ANT`, cf. `Localite.ID_CENTRALE` — ex.
> `00013/PPM/CNM/2026`), **`CRM-<idLocalite>`** pour les régions (ex. `00014/PPM/CRM-TMS/2026`).

**Champs `LocaliteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idLocalite | string | Oui (PK, au POST) | clé primaire, max 5 — sert de segment localité des références (`CRM-<id>`) |
| libelleLocalite | string | Oui | @NotBlank, max 50 — libellé affiché (aussi dans les documents PV/lettres) |
| chefLieu | string | Non | max 50 — ⚠️ **ajouté 2026-08-03** : **chef-lieu** de la localité (ville de siège de la Commission régionale, lieu porté par les documents officiels « A &lt;chef-lieu&gt;, le … »). **Facultatif** : à défaut, les documents retombent sur `libelleLocalite`. Colonne `CHEF_LIEU` (nullable), éditable dans l'écran admin **Référentiels → Localités** |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/localites | — | `LocaliteDto[]` | 200 | Authentifié |
| GET | /api/localites/{id} | — | `LocaliteDto` | 200, 404 | Authentifié |
| POST | /api/localites | `LocaliteDto` | `LocaliteDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/localites/{id} | `LocaliteDto` | `LocaliteDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/localites/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idLocalite (string).

**Exemple — requête**
```json
{ "idLocalite": "ANT", "libelleLocalite": "Antananarivo", "referencement": "REF-ANT", "localite": "ANT" }
```

---

## Lots
**Ressource** `/api/lots` — Lecture / écriture : tout utilisateur authentifié. `GET /par-marche/{idDetail}` liste
les lots d'une **ligne de marché** (`t_lot.ID_DETAIL`) — **liste vide** si aucun (ou marché inconnu), pas de 404 (filtre).
`GET /par-dossier/{idDossier}` agrège **tous les lots d'un dossier** (`t_lot.ID_DOSSIER`, toutes ses lignes de marché) en **un seul appel** — même sémantique filtre (liste vide, pas de 404).

**Champs `LotDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idLot | number | Oui (PK, au POST) | clé primaire |
| idDossier | number | Oui | @NotNull |
| idDetail | number | Oui | @NotNull |
| designationLot | string | Oui | @NotBlank, max 200 |
| montLot | number | Non | |
| qteLot | number | Non | |
| uniteLot | string | Non | max 10 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/lots | — | `LotDto[]` | 200 | Authentifié |
| GET | /api/lots/{id} | — | `LotDto` | 200, 404 | Authentifié |
| GET | /api/lots/par-marche/{idDetail} | — | `LotDto[]` | 200 | Authentifié |
| GET | /api/lots/par-dossier/{idDossier} | — | `LotDto[]` | 200 | Authentifié |
| POST | /api/lots | `LotDto` | `LotDto` | 201, 400 | Authentifié |
| PUT | /api/lots/{id} | `LotDto` | `LotDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/lots/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idLot (number).

**Exemple — requête**
```json
{ "idLot": 88, "idDossier": 320, "idDetail": 1205, "designationLot": "Fourniture de mobilier - Lot 1", "montLot": 85000000.0, "qteLot": 150, "uniteLot": "unite" }
```

---

## Mandats PRMP
**Ressource** `/api/mandats` (table `t_mandat`) — ⚠️ **règle ajoutée (spec « Mandats PRMP »)**.
**Écriture réservée `ADMINISTRATEUR`** (un mandat matérialise un arrêté de nomination : personne ne
déclare le sien). **Lecture ouverte aux authentifiés**, mais une `PRMP` / `UGPM` reste cantonnée à son
propre périmètre (§3.1) — un filtre pointant ailleurs → **403**.

> **Le mandat est l'habilitation, pas l'attribution.** Un mandat dure **3 ans**. Une **reconduction est un
> mandat distinct** — nouvel arrêté, nouvelles dates, `numeroMandat = 2` — **jamais une prolongation** : il
> n'existe volontairement **ni `PUT` ni `DELETE`** sur cette ressource. Le **renouvellement est unique** :
> un **3ᵉ mandat** pour la même personne → **409**.

> **Reprise de l'existant.** Une PRMP sans aucun mandat déclaré se voit reconstituer un mandat **implicite**
> depuis `t_prmp` (`DATE_NOMIN` → `DATE_NOMIN + 3 ans`, arrêté = `ARRETE_NOMIN`), signalé par
> `implicite: true` et sans `idMandat`. Dès qu'un mandat est déclaré pour cette PRMP, `t_mandat` fait seul
> autorité. Aucune reprise de données n'est donc nécessaire — mais la règle « expiration = `DATE_NOMIN` + 3 ans »
> (§3.1) devient **opposable** aux PRMP dont la nomination remonte à plus de 3 ans.
>
> ⚠️ Le mandat implicite **ne compte pas** dans le plafond de 2 : pour une PRMP déjà en poste, déclarer son
> **mandat initial** (et pas seulement la reconduction) est ce qui rend la garde de renouvellement exacte.

**Champs `MandatDto`** *(lecture)*

| Champ (JSON) | Type | Contraintes |
|---|---|---|
| idMandat | number | PK serveur (IDENTITY) ; **`null`** pour un mandat implicite |
| idPrmp | string | max 10 — titulaire (FK `t_prmp`) |
| titulaire | string | max 200 — nom **figé** à la nomination |
| dateDebut | string (date) | prise de fonction |
| dateFin | string (date) | par défaut `dateDebut + 3 ans − 1 jour` |
| refArrete | string | max 100 — arrêté de nomination, **jamais réutilisé** |
| statut | string | `ACTIF` / `EN_TRANSITION` / `ACHEVE` / `ABROGE` — **dérivé à la date du jour** (voir ci-dessous) |
| numeroMandat | number | `1` (initial) ou `2` (reconduction) — **calculé serveur**, jamais reçu du client |
| dateAbrogation | string (date) | renseignée en cas de fin avant terme |
| motifAbrogation | string | max 255 |
| implicite | boolean | `true` = mandat reconstitué depuis `t_prmp` |

**Statuts** — `ABROGE` prime (acte explicite) ; sinon la période décide : avant `dateDebut` →
`EN_TRANSITION` (nomination prise, pas encore effective — **n'autorise pas** le traitement), pendant →
`ACTIF`, après `dateFin` → `ACHEVE`.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/mandats | — | `MandatDto[]` | 200, 403, 404 | Authentifié — filtres `?ugpm=` / `?prmp=` |
| GET | /api/mandats/actif | — | `MandatDto` | **200 / 404**, 400, 403 | Authentifié — `?ugpm=` / `?prmp=` **requis** pour un profil CNM |
| GET | /api/mandats/{id} | — | `MandatDto` | 200, 404 | Authentifié |
| POST | /api/mandats | `CreerMandatRequest` | `MandatDto` | 201, 400, 403, 404, **409** | **ADMINISTRATEUR** |
| POST | /api/mandats/{id}/abroger | `AbrogerMandatRequest` | `MandatDto` | 200, 400, 403, 404, 409 | **ADMINISTRATEUR** |

`{id}` = idMandat (number).
**`CreerMandatRequest`** = `{ idPrmp (@NotBlank, max 10), refArrete (@NotBlank, max 100), dateDebut (@NotNull),
dateFin (optionnel), titulaire (optionnel, max 200) }`.
**`AbrogerMandatRequest`** = `{ motif (@NotBlank, max 255), dateAbrogation (optionnel, défaut = aujourd'hui) }`.

> 📌 **`GET /api/mandats?ugpm=`** renvoie l'historique **chronologique** (du plus ancien au plus récent) de la
> PRMP de **tutelle** de cette UGPM (`t_ugpm.ID_PRMP_TUTELLE`), statut inclus. `?prmp=` l'emporte sur
> `?ugpm=`. Sans filtre : une PRMP / UGPM obtient son propre historique, un profil CNM obtient tout.

> 📌 **`GET /api/mandats/actif` est le signal de vacance du front** : **200** = quelqu'un est en fonction,
> **404** = personne ne l'est (« en attente de nomination de la nouvelle PRMP »). À interroger pour griser
> les actions de traitement avant même de les tenter.

**409 refusés à la création** — `Renouvellement unique` (3ᵉ mandat) · `Arrêté déjà utilisé` ·
`pas une prolongation` (reconduction recouvrant le mandat précédent) · `ne peut excéder 3 ans` ·
chevauchement de périodes.

### Standby de transition (`409 VACANCE_PRMP`)
Sans mandat actif à la date de l'action, **toute action de traitement côté PRMP / UGPM est bloquée** —
il n'y a **aucune obligation d'intérim**, le dossier attend. Réponse :

```json
{ "timestamp": "...", "status": 409, "error": "Conflict",
  "code": "VACANCE_PRMP",
  "message": "En attente de nomination de la nouvelle PRMP",
  "path": "/api/dossiers/12/soumettre" }
```

Le **déblocage est automatique** dès qu'un mandat redevient actif : rien à rejouer, rien à débloquer à la
main. L'action en attente est alors faite par le **nouveau titulaire en tant qu'opérateur** — l'attribution
des dossiers, elle, ne change pas.

**Endpoints concernés** (côté PRMP / UGPM uniquement — le circuit interne CNM n'est jamais suspendu par la
vacance d'une PRMP) : `POST /api/saisies/**` (création), toute édition de PPM / marché / pièce jointe,
`POST /api/dossiers/{id}/soumettre` · `…/resoumettre` · `…/transmettre-complements` ·
`…/transmettre-complements-depot`, `DELETE /api/dossiers/{id}`, `POST /api/demande-retraits`, et les
mises à jour de PPM.

### Attribution figée vs opérateur courant
| | Porté par | Recalculé ? |
|---|---|---|
| **Attribution** | `t_dossier.ID_PRMP` + `ID_MANDAT_ATTRIB` | **Jamais.** Figée à la création ; un changement de PRMP ne réattribue **rien** rétroactivement |
| **Opérateur courant** | `t_action_dossier` (cf. `GET /api/dossiers/{id}/journal`) | À **chaque action** : la PRMP en fonction à cette date |

La **garde de propriété** accepte donc **deux titres** : la PRMP d'attribution, **et** la PRMP en fonction
sur le périmètre du dossier — c'est-à-dire celle qui a *à la fois* un mandat actif *et* une affectation
active (`t_prmp_entite.ACTIF`) sur l'entité contractante du dossier. C'est ce second titre qui permet la
**reprise du traitement** par le successeur. Une PRMP en fonction **ailleurs** reste refusée (**403**).

---

## Marchés
**Ressource** `/api/marches` — Lecture **scopée au périmètre de l'appelant** (⚠️ changement de portée, voir note). **Écriture (POST/PUT/DELETE) réservée `PRMP`** : édition des lignes d'un dossier **PPM en BROUILLON** dont elle est propriétaire (sinon 403/409). Le **mode** est **saisi** (plus de détermination auto, cf. note). ⚠️ **Règle ajoutée** : à la **suppression** (`DELETE`), **tous les enregistrements liés** au marché sont supprimés **en cascade applicative** (même transaction, ordre FK-safe) : **tranches** de ses lots → **lots** (`t_lot`), **bénéficiaires** (`t_service_beneficiaire`) et **dates prévisionnelles** (`t_marche_prevision`). *(Un marché supprimable est BROUILLON → jamais dispatché : ni anomalie ni échéance possibles.)* Même cascade réutilisée par `DELETE /api/ppms/{id}` pour chacun de ses marchés.

> **⚠️ Scoping serveur (changement de portée, §1/§3.1).** `GET /api/marches` ne renvoie **plus toute
> la table** : Président/Administrateur → tout ; **PRMP → ses marchés** (ceux de ses PPM) ; contrôleur
> → ceux de **sa localité** (dossier non brouillon) ; autre profil → liste vide. `GET /api/marches/{id}`
> hors périmètre → **403**. Le front n'a plus à filtrer côté client (corrige la fuite inter‑PRMP/localité).

**Champs `MarcheDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idDetail | number | Oui (PK, au POST) | clé primaire |
| idDossier | number | Oui | @NotNull |
| idPpm | number | Oui | @NotNull |
| designationMarche | string | Non | max 500 |
| numCompte | string | Non | max 20 |
| montEstim | number | Non | |
| ancienMontEstim | number | Non | |
| nouvMontEstim | number | Non | |
| financement | string | Non | max 20 |
| statut | string | Non | max 20 |
| idNature | number | Non | nature du marché |
| idMode | number | Non | mode de passation **saisi** (PRMP/import), conservé tel quel — FK `tr_mode` |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/marches | — | `MarcheDto[]` (scopé) | 200 | Authentifié — ⚠️ **paginable** (`?page=&size=`, cf. Conventions) |
| GET | /api/marches/{id} | — | `MarcheDto` | 200, 403, 404 | Authentifié (dans son périmètre) |
| POST | /api/marches | `MarcheDto` | `MarcheDto` | 201, 400 | Authentifié |
| PUT | /api/marches/{id} | `MarcheDto` | `MarcheDto` | 200, 400, 404 | Authentifié |
| PATCH | /api/marches/{id}/rectifier | `MarcheDto` | `MarcheDto` | 200, 403, 404, 409 | PRMP (propriétaire) |
| DELETE | /api/marches/{id} | — | — | 204, 403, 404, 409 | PRMP (propriétaire, brouillon) — ⚠️ cascade prévisions + bénéficiaires + lots/tranches |

`{id}` = idDetail (number).

> ⚠️ **Édition restreinte (rectification) — règle ajoutée.** `PATCH /api/marches/{id}/rectifier` permet à la
> PRMP propriétaire de corriger une ligne de marché dont le **dossier est `EN_ATTENTE_DECISION_PRMP`**, **sans
> repasser par le brouillon**. Statut du dossier **inchangé** (reste `EN_ATTENTE_DECISION_PRMP` jusqu'à
> `POST /api/dossiers/{id}/resoumettre`). Hors `EN_ATTENTE_DECISION_PRMP` → **409** ; non-propriétaire → **403** ;
> profil **PRMP strict** (Admin/vérificateur → **403**). Identité **figée** (idDossier, idPpm — **non requis** dans
> le corps, ignorés s'ils sont envoyés ; le PATCH ne valide pas ces champs). Le `idMode` fourni est conservé
> tel quel. Tracé `t_audit_log` (`MODIFICATION_RECTIFICATION`, `NOM_TABLE=t_marche`).

> ⚠️ **`formeMarche` — forme du marché (règle ajoutée 2026-07-18).** Champ de `MarcheDto` (colonne
> `t_marche.FORME_MARCHE`), liste fermée : **`A_COMMANDE`** (« Marché à commande »), **`CONTRAT_CADRE`**
> (« Contrat cadre »), **`QUANTITE_FIXE`** (« À quantité fixe »). **Optionnel en entrée** (`POST`/`PUT`/`PATCH
> rectifier` — absent/vide → défaut **`QUANTITE_FIXE`** ; code inconnu → **400 ciblé**), **toujours renseigné
> en sortie** (jamais null — les lignes historiques sont reprises au démarrage par `FormeMarcheMigration`,
> forme dérivée de la désignation : motifs « contrat cadre » / « à commande », sinon `QUANTITE_FIXE`).
> Pré-remplie par l'**import PPM** (`SaisiePpmImportResult.marches[].formeMarche`, relevée dans l'objet —
> désignation conservée intégrale).

> Les **dates prévisionnelles** ne sont pas des colonnes du marché : elles sont en relation **1,N**
> dans **Marchés — dates prévisionnelles** (`/api/marche-previsions`), **une ligne par processus**
> (`idCapm` → **CAPM**). ⚠️ À la **création du brouillon** (`POST /api/saisies/ppm`), au moins un
> **processus** (`idCapm` + `dateDebut` + `dateFin`) est **obligatoire par marché** (sinon **400**) et le
> serveur crée d'office les lignes `t_marche_prevision`. La ressource `/api/marche-previsions` reste
> utilisée pour **consulter/éditer** ces dates ensuite (triées par `t_capm.ordre`).
>
> ⚠️ **Mode de passation — purement saisi (règle modifiée).** La **détermination automatique** du mode
> (référentiels `t_situation` / `t_regle_passation` / `t_seuil` + endpoint `suggestion-mode`) a été **retirée**.
> Le `idMode` fourni (PRMP ou import PPM) est **conservé tel quel** à la création/mise à jour/rectification ;
> aucune validation par situation/seuil, plus de notification `MODE_NON_DETERMINE`. Seule la **FK `tr_mode`**
> garantit l'existence du mode. Le PPM officiel porte le mode directement (« Achat Direct », « Gré à gré »…).

**Exemple — requête / réponse** (`idMode` = mode saisi, conservé)
```json
{
  "idDetail": 1205, "idDossier": 320, "idPpm": 45,
  "designationMarche": "Acquisition de matériel informatique", "numCompte": "6011001", "montEstim": 620000000.0,
  "financement": "RPI", "statut": "PREVU", "idNature": 2, "idMode": 3
}
```

---

## CAPM — processus de marché
**Ressource** `/api/capm` (table référentielle `t_capm`) — **Lecture** : tout utilisateur authentifié ;
**écriture** (POST/PUT/DELETE) : **`ADMINISTRATEUR`** (comme les autres référentiels).

Processus de marché (LANCEMENT, DAO, OUVERTURE, ATTRIBUTION…), référencés par les dates
prévisionnelles (`t_marche_prevision.ID_CAPM`). L'`ordre` fixe l'affichage des processus.

> ⚠️ **Modèle mixte par mode de passation (règle ajoutée)** — comme les points de contrôle par sous-type :
> `idMode` **null** = processus **commun** (modèle par défaut) ; sinon processus **spécifique** au mode
> (`t_mode_passation.ID_MODE`). `GET /api/capm?mode={idMode}` renvoie la **grille effective** du mode :
> ses spécifiques s'ils existent, sinon ceux de son **mode modèle partagé**
> (`tr_mode_passation.ID_MODE_MODELE_CAPM`, administrable — ex. « Consultation des Prix Ouverte »,
> « CPO PIP » et « Appel à manifestation d'intérêt » → modèle AOO), sinon les communs — triée par
> `ordre` ASC. Le champ `groupe` porte la **phase** du modèle (regroupement à l'affichage, ex. « 2 — Lancement »).
> **Modèle détaillé « Appel d'offres ouvert »** (`idMode=1`) : 30 tâches en 6 phases (ids/ordre 101-130),
> reprises du modèle officiel CAPM AOO (étape préalable, lancement, ouverture des plis, attribution,
> circuit administratif de validation, notification et exécution).

**Champs `CapmDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idCapm | number | Oui (PK, au POST) | clé primaire (assignée par le client) |
| libelleProcessus | string | Non | max 300 |
| ordre | number | Oui | @NotNull |
| idMode | number | Non | null = commun ; sinon spécifique au mode de passation |
| groupe | string | Non | max 150 — phase du modèle (regroupement) |

**Données initiales** : `(1,'LANCEMENT',1)`, `(2,'DAO',2)`, `(3,'OUVERTURE',3)`, `(4,'ATTRIBUTION',4)`
(communes, `idMode` null) + le modèle AOO (ids 101-130, `idMode=1`).

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/capm[?mode={idMode}] | — | `CapmDto[]` (grille effective si `mode`) | 200 | Authentifié |
| GET | /api/capm/{id} | — | `CapmDto` | 200, 404 | Authentifié |
| POST | /api/capm | `CapmDto` | `CapmDto` | 201, 400, 403 | **ADMINISTRATEUR** |
| PUT | /api/capm/{id} | `CapmDto` | `CapmDto` | 200, 400, 403, 404 | **ADMINISTRATEUR** |
| DELETE | /api/capm/{id} | — | — | 204, 403, 404 | **ADMINISTRATEUR** |

---

## Types de DMC (dossier de mise en concurrence)
**Ressource** `/api/type-dmc` (table référentielle `t_type_dmc`) — **Lecture** : tout utilisateur authentifié ;
**écriture** (POST/PUT/DELETE) : **`ADMINISTRATEUR`**. Référentiel **administrable** des types de dossier de mise
en concurrence : `DAO` (Dossier d'Appel d'Offres), `DC` (Dossier de Consultation), `BC` (Bon de Commande)… **Liste
ouverte**, complétable sans livraison. Le mapping **mode de passation → type de DMC** est porté par
`tr_mode_passation.ID_TYPE_DMC` (champ **`idTypeDmc`** de `ModePassationDto`, réglé via `PUT /api/mode-passations/{id}`).

**Champs `TypeDmcDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idTypeDmc | number | Non (PK, IDENTITY) | assignée par la base |
| code | string | Oui | max 10, **unique** (409 si déjà pris) |
| libelle | string | Oui | max 120 |
| actif | boolean | Non | défaut `true` |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/type-dmc | — | `TypeDmcDto[]` | 200 | Authentifié |
| GET | /api/type-dmc/{id} | — | `TypeDmcDto` | 200, 404 | Authentifié |
| POST | /api/type-dmc | `TypeDmcDto` | `TypeDmcDto` | 201, 400, 403, 409 | **ADMINISTRATEUR** |
| PUT | /api/type-dmc/{id} | `TypeDmcDto` | `TypeDmcDto` | 200, 400, 403, 404, 409 | **ADMINISTRATEUR** |
| DELETE | /api/type-dmc/{id} | — | — | 204, 403, 404 | **ADMINISTRATEUR** |

---

## Dossiers de mise en concurrence (DMC)
**Ressource** `/api/dmcs` (table `t_dossier_mec`) — Authentifié. **Un DMC par ligne de marché** (relation 1-1 sur
`idDetail`). Son **type est dérivé du mode de passation** du marché (`tr_mode_passation.ID_TYPE_DMC`, pas d'enum codé
en dur). Création par un **service dédié** (non câblé automatiquement sur la saisie/soumission). Si le mode n'est pas
mappé à un type **actif** → **400** avec message de configuration (aucun DMC créé). Une 2ᵉ création pour le même
marché → **409** (unicité). Au **changement de mode** d'un marché, si son DMC est encore `A_PREPARER`, son type est
**re-dérivé** ; la **suppression du marché supprime son DMC** (cascade applicative).

**Champs `DmcDto`** : `idDmc`, `idDetail`, `idTypeDmc`, `typeDmcCode`/`typeDmcLibelle` (dérivés, lecture seule),
`reference` (nullable), `statut` (`A_PREPARER`/`ENGAGE`), `dateCreation`.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| POST | /api/dmcs/par-marche/{idDetail} | — | `DmcDto` | 201, 400, 404, 409 | Authentifié |
| GET | /api/dmcs/par-marche/{idDetail} | — | `DmcDto` | 200, 404 | Authentifié |
| GET | /api/dmcs/{id} | — | `DmcDto` | 200, 404 | Authentifié |

---

## Marchés — dates prévisionnelles
**Ressource** `/api/marche-previsions` — Lecture / écriture : tout utilisateur authentifié.

Dates prévisionnelles d'un marché, en relation **1,N** avec `/api/marches` : **une ligne par
processus** (`idCapm` → **CAPM**), chacune avec une `dateDebut` (obligatoire) et une `dateFin`
**optionnelle** (fin non connue / ouverte). Le filtre `?marche={idDetail}` renvoie les lignes
**triées par `t_capm.ordre` ASC**.

**Champs `MarchePrevisionDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPrevision | number | Oui (PK, au POST) | @NotNull, clé primaire |
| idDetail | number | Oui | @NotNull — FK vers le marché |
| idCapm | number | Oui | @NotNull — FK vers `t_capm` (processus) |
| dateDebut | string (date) | Oui | @NotNull — `yyyy-MM-dd` |
| dateFin | string (date) | **Non** | `yyyy-MM-dd` — **optionnelle** ; chronologie vérifiée seulement si présente |
| ordre | number | — (réponse) | **lecture seule**, porté par `t_capm.ordre` |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/marche-previsions | — | `MarchePrevisionDto[]` | 200 | Authentifié |
| GET | /api/marche-previsions?marche={idDetail} | — | `MarchePrevisionDto[]` | 200 | Authentifié |
| GET | /api/marche-previsions/{id} | — | `MarchePrevisionDto` | 200, 404 | Authentifié |
| POST | /api/marche-previsions | `MarchePrevisionDto` | `MarchePrevisionDto` | 201, 400 | Authentifié |
| PUT | /api/marche-previsions/{id} | `MarchePrevisionDto` | `MarchePrevisionDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/marche-previsions/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idPrevision (number). Le paramètre `marche` filtre par marché (idDetail).

**Exemple — réponse** (`?marche=1`, triée par `ordre`)
```json
[
  { "idPrevision": 1, "idDetail": 1, "idCapm": 1, "dateDebut": "2026-03-01", "dateFin": "2026-03-31", "ordre": 1 },
  { "idPrevision": 2, "idDetail": 1, "idCapm": 3, "dateDebut": "2026-04-15", "dateFin": "2026-05-15", "ordre": 3 }
]
```

---

## Messagerie
**Ressource** `/api/messages` — Tout utilisateur authentifié, avec **confidentialité** : on ne voit que les messages dont on est expéditeur ou destinataire. À l'envoi, l'expéditeur est **forcé** à l'utilisateur courant.

**Champs `MessageDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idMessage | number | Oui (PK, au POST générique) | clé primaire |
| idDossier | number | Non | |
| expediteurIm | string | Oui | @NotBlank, max 7 (forcé à l'utilisateur courant) |
| destinataireIm | string | Oui | @NotBlank, max 7 |
| sujet | string | Non | max 200 |
| corps | string | Non | |
| dateEnvoi | string (date-time) | Non | |
| lu | boolean | Non | |
| idMessageParent | number | Non | |

**Champs `MessageEnvoiRequest`** (corps de `POST /api/messages/envoyer`)

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| destinataireIm | string | Oui | @NotBlank, max 7 |
| sujet | string | Non | max 200 |
| corps | string | Non | |
| idDossier | number | Non | |
| idMessageParent | number | Non | |

> Via `/envoyer`, l'id du message est généré par le serveur et l'expéditeur est l'utilisateur courant.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/messages | — | `MessageDto[]` (filtré à l'utilisateur) | 200 | Authentifié |
| GET | /api/messages/{id} | — | `MessageDto` | 200, 403, 404 | Expéditeur / destinataire |
| POST | /api/messages | `MessageDto` | `MessageDto` | 201, 400 | Authentifié |
| PUT | /api/messages/{id} | `MessageDto` | `MessageDto` | 200, 400, 403, 404 | Expéditeur / destinataire |
| DELETE | /api/messages/{id} | — | — | 204, 403, 404 | Expéditeur / destinataire |
| POST | /api/messages/envoyer | `MessageEnvoiRequest` | `MessageDto` | 201, 400 | Authentifié |
| GET | /api/messages/recus | — | `MessageDto[]` | 200 | Authentifié |
| GET | /api/messages/envoyes | — | `MessageDto[]` | 200 | Authentifié |
| POST | /api/messages/{id}/lu | — | `MessageDto` | 200, 403, 404 | Destinataire uniquement |

`{id}` = idMessage (number).

**Exemple — requête (`/envoyer`) / réponse**
```json
{ "destinataireIm": "CCANT01", "sujet": "Question dossier 320", "corps": "Merci de vérifier le lot 1.", "idDossier": 320, "idMessageParent": null }
```
```json
{
  "idMessage": 4521, "idDossier": 320, "expediteurIm": "MEMANT1", "destinataireIm": "CCANT01",
  "sujet": "Question dossier 320", "corps": "Merci de vérifier le lot 1.",
  "dateEnvoi": "2026-06-12T09:15:00", "lu": false, "idMessageParent": null
}
```

---

## Ministères
**Ressource** `/api/ministeres` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `MinistereDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idMinistere | number | Oui (PK, au POST) | clé primaire |
| libelleMinistere | string | Oui | @NotBlank, max 150 |
| sigle | string | Non | max 20 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/ministeres | — | `MinistereDto[]` | 200 | Authentifié |
| GET | /api/ministeres/{id} | — | `MinistereDto` | 200, 404 | Authentifié |
| POST | /api/ministeres | `MinistereDto` | `MinistereDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/ministeres/{id} | `MinistereDto` | `MinistereDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/ministeres/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idMinistere (number).

**Exemple — requête**
```json
{ "idMinistere": 12, "libelleMinistere": "Ministère de l'Économie et des Finances", "sigle": "MEF" }
```

---

## Modes de passation
**Ressource** `/api/mode-passations` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `ModePassationDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idMode | number | Oui (PK, au POST) | clé primaire |
| libelle | string | Non | max 100 |
| description | string | Non | max 500 |
| publiciteRequise | boolean | Non | |
| delaiMinJours | number | Non | |
| baseLegale | string | Non | max 200 |
| idTypeDmc | number | Non | **mapping vers le type de DMC** (`t_type_dmc`) dérivé pour les marchés de ce mode |
| categorie | string enum | Non | `NORMAL` \| `DEROGATOIRE` \| `null` (= non classé) ; valeur hors enum → **400** `{ "champ": "categorie", ... }` |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/mode-passations | — | `ModePassationDto[]` | 200 | Authentifié |
| GET | /api/mode-passations/{id} | — | `ModePassationDto` | 200, 404 | Authentifié |
| POST | /api/mode-passations | `ModePassationDto` | `ModePassationDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/mode-passations/{id} | `ModePassationDto` | `ModePassationDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/mode-passations/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idMode (number).

> **Auto-mapping du type de DMC à la création.** À la **création** d'un mode (POST, **et** création à la volée
> pendant la saisie/import PPM), si **`idTypeDmc` n'est pas fourni**, il est **dérivé du libellé** (heuristique par
> mots-clés, insensible casse/accents) : « appel d'offres » → **DAO** ; « consultation »/« cotation » → **DC** ;
> « gré à gré »/« achat direct » → **BC** ; sinon **`null`** (à mapper ensuite via `PUT`). Un `idTypeDmc` **fourni
> explicitement est conservé** (pas d'écrasement). Le `PUT` ne dérive pas (mapping explicite de l'admin).

> **Catégorie `NORMAL` / `DEROGATOIRE` (règle ajoutée 2026-08-13).** Classification **purement déclarative**
> (aucun comportement dérivé pour l'instant, au même titre que `publiciteRequise`) : le Code des marchés
> publics fait de l'appel d'offres ouvert le mode de **droit commun** (`NORMAL`), les autres modes étant
> **dérogatoires** (`DEROGATOIRE`). `null` = **non classé** — les modes créés à la volée (import PPM)
> naissent non classés et l'Administrateur les classe via l'écran référentiel. **Reprise au démarrage**
> (`CategorieModePassationMigration`, désactivable par `app.migration.categorie-mode.enabled=false`) :
> `NORMAL` est posé sur les modes marqués `declencheAgpm` (marqueur AOO administré — jamais de mot-clé
> de libellé) **dont la catégorie est `null`** ; aucun classement admin n'est écrasé.

**Exemple — requête**
```json
{ "idMode": 3, "libelle": "Appel d'offres ouvert", "description": "Procédure ouverte avec publicité.", "publiciteRequise": true, "delaiMinJours": 30, "baseLegale": "Code des marchés publics, art. 25" }
```

---

## Mon compte
**Ressource** `/api/mon-compte` — Actions de l'utilisateur **authentifié** sur son propre compte (contrôleur ou PRMP, tout rôle).

**Champs `ChangePasswordRequest`** (corps)

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| ancienMotDePasse | string | Oui | @NotBlank |
| nouveauMotDePasse | string | Oui | @NotBlank, min 8, max 72 |

**Champs `MessageResponse`** (réponse)

| Champ (JSON) | Type | Description |
|---|---|---|
| message | string | message d'information |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| POST | /api/mon-compte/changer-mot-de-passe | `ChangePasswordRequest` | `MessageResponse` | 200, 400, 401 | Authentifié |

> Vérifie l'ancien mot de passe ; **400** si l'ancien est incorrect ou si le nouveau est
> identique à l'actuel ; **401** sans jeton valide. Après changement, le nouveau mot de passe
> est requis à la prochaine connexion.

**Exemple — requête / réponse**
```json
{ "ancienMotDePasse": "Test@1234", "nouveauMotDePasse": "Nouveau#2026" }
```
```json
{ "message": "Mot de passe modifié avec succès." }
```

---

## Natures
**Ressource** `/api/natures` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `NatureDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idNature | number | Oui (PK, au POST) | clé primaire |
| libelle | string | Non | max 100 |
| description | string | Non | max 500 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/natures | — | `NatureDto[]` | 200 | Authentifié |
| GET | /api/natures/{id} | — | `NatureDto` | 200, 404 | Authentifié |
| POST | /api/natures | `NatureDto` | `NatureDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/natures/{id} | `NatureDto` | `NatureDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/natures/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idNature (number).

**Exemple — requête**
```json
{ "idNature": 1, "libelle": "Fournitures", "description": "Marchés de fournitures courantes" }
```

---

## Navettes de PV
**Ressource** `/api/pv-navettes` — Lecture / écriture (POST/PUT) : tout utilisateur authentifié. **DELETE interdit → 409** (traçabilité immuable, §3.5). `sens` ∈ {`SOUMISSION`, `RETOUR_RECTIF`, `ACCEPTATION`} (sinon **409**).

**Champs `PvNavetteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idNavette | number | Oui (PK, au POST) | clé primaire |
| idPv | number | Oui | @NotNull |
| numNavette | number | Oui | @NotNull |
| sens | string | Oui | @NotBlank, max 20 — valeur contrôlée |
| imActeur | string | Oui | @NotBlank, max 7 |
| dateAction | string (date-time) | Oui | @NotNull |
| commentaire | string | Non | |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/pv-navettes | — | `PvNavetteDto[]` | 200 | Authentifié |
| GET | /api/pv-navettes/{id} | — | `PvNavetteDto` | 200, 404 | Authentifié |
| POST | /api/pv-navettes | `PvNavetteDto` | `PvNavetteDto` | 201, 400, 409 | Authentifié |
| PUT | /api/pv-navettes/{id} | `PvNavetteDto` | `PvNavetteDto` | 200, 400, 404, 409 | Authentifié |
| DELETE | /api/pv-navettes/{id} | — | — | **409 (interdit)** | — |

`{id}` = idNavette (number). *En pratique, les navettes sont créées automatiquement par les actions du PV.*

**Exemple — requête**
```json
{ "idNavette": 905, "idPv": 312, "numNavette": 1, "sens": "SOUMISSION", "imActeur": "MEMANT1", "dateAction": "2026-06-12T09:35:00", "commentaire": "Première soumission" }
```

---

## Notifications
**Ressource** `/api/notifications` (table `t_notification`) — Notifications système, émises **automatiquement à chaque transmission** (dossier, PV, navette, message).
- **Mes notifications** (`/mes`, `/mes/non-lues/count`, `/{id}/lu`, `/{id}/non-lu`, `/lire-tout`) : **scopées** à l'utilisateur courant — chacun ne voit/agit que sur **les siennes** (clé `DESTINATAIRE_REF` + `DESTINATAIRE_TYPE` ; repli e-mail pour les PRMP).
- **Liste globale** et **CRUD** : réservés à l'**Administrateur** (supervision).

> ⚠️ **Temps réel & écran dédié (spec notifications 2026-08-02).**
> - `POST /{id}/non-lu` : marquage manuel **NON LU** unitaire (inverse de `/{id}/lu`).
> - `GET /api/notifications/stream` (`text/event-stream`) : flux **SSE** par utilisateur — un événement
>   `maj` est poussé à **tous les flux du destinataire** à chaque émission de notification et à chaque
>   marquage lu / non-lu / lire-tout (synchronisation **entre onglets et sessions**). Le front s'y
>   connecte en fetch-stream (Bearer — EventSource ne porte pas d'en-tête) et recharge alors le compteur
>   `GET /mes/non-lues/count` — **le compteur est toujours calculé côté serveur**. Repli : polling 60 s.
>   Flux expirant (~30 min) avec reconnexion automatique côté client.
> - Front : écran transverse `/notifications` (tous profils, entrée de menu + lien « Voir toutes les
>   notifications » de la cloche) — regroupement Aujourd'hui / Hier / dates, filtres toutes / non-lues et
>   par type, « Charger plus » (défilement progressif), clic = lu + ouverture de l'objet, badge cloche
>   plafonné « 99+ » (`NotificationsStore` : SSE + BroadcastChannel + polling).

**Champs `NotificationDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idNotification | number | Oui (PK, au POST) | clé primaire |
| idDossier | number | Non | |
| typeNotif | string | Oui | @NotBlank, max 30 |
| destinataireIm | string | Non | max 7 — destinataire contrôleur (compat.) |
| destinataireEmail | string | Non | max 100 — destinataire PRMP/externe |
| **destinataireRef** | string | Non | max 10 — clé unifiée (matricule ou id PRMP) |
| **destinataireType** | string | Non | max 20 — `CONTROLEUR` / `PRMP` |
| **idObjet** | number | Non | objet concerné (selon `typeObjet`) |
| **typeObjet** | string | Non | max 20 — `DOSSIER` / `PV` / `MESSAGE` |
| titre | string | Non | max 200 |
| corps | string | Non | |
| dateEnvoi | string (date-time) | Non | |
| lu | boolean | Non | |
| dateLecture | string (date-time) | Non | |
| canal | string | Non | max 20 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/notifications/mes`?lu=` | — | `NotificationDto[]` | 200 | Authentifié (scopé) |
| GET | /api/notifications/mes/non-lues/count | — | `{ "nonLues": number }` | 200 | Authentifié (scopé) |
| POST | /api/notifications/{id}/lu | — | `NotificationDto` | 200, 403, 404 | Destinataire |
| POST | /api/notifications/lire-tout | — | `{ "traitees": number }` | 200 | Authentifié (scopé) |
| GET | /api/notifications | — | `NotificationDto[]` | 200, 403 | ADMINISTRATEUR |
| GET | /api/notifications/{id} | — | `NotificationDto` | 200, 403, 404 | ADMINISTRATEUR |
| POST | /api/notifications | `NotificationDto` | `NotificationDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/notifications/{id} | `NotificationDto` | `NotificationDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/notifications/{id} | — | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = idNotification (number). `?lu=true|false` filtre lues/non-lues ; `POST /{id}/lu` refuse (403) une notification qui ne vous appartient pas.

**Types (`TYPE_NOTIF`) émis à la transmission**
| Type | Événement | Destinataire | Objet |
|---|---|---|---|
| `DOSSIER_SOUMIS` | soumission du dossier | Secrétaire / CC de la localité | DOSSIER |
| `PRET_DISPATCH` | dossier complet | Président + CC de la localité | DOSSIER |
| `EXAMEN_A_FAIRE` | dossier dispatché | Membre assigné | DOSSIER |
| `PV_A_VALIDER` | projet de PV soumis | CC + Président de la localité | PV |
| `PV_A_RECTIFIER` | navette retournée (commentaire) | Membre auteur | PV |
| `PV_ACCEPTE` | projet de PV accepté | Membre auteur | PV |
| `PV_SIGNE` | PV signé | PRMP | DOSSIER |
| `PV_A_VERIFIER` | PV signé `FAVR` à vérifier | Vérificateur de la localité | DOSSIER |
| `PV_POUR_INFO` | PV signé auto-clôturé (FAV/DEF/NSP) | Vérificateur de la localité | DOSSIER |
| `OBSERVATION_VERIFICATION` | observations de vérification non levées à traiter | PRMP du dossier | DOSSIER |
| `RECTIFICATION_PRMP` | dossier rectifié par la PRMP et resoumis | Vérificateur du dossier | DOSSIER |
| `LETTRE_RENVOI_RECUE` | lettre de renvoi signée reçue | PRMP du dossier | DOSSIER |
| `LETTRE_RENVOI_COPIE` | copie d'une lettre de renvoi signée | Assistant contrôleur de la localité | DOSSIER |
| `PV_DEFINITIF_COPIE` | copie d'un PV définitif (avis ≠ FAVR) | Assistant contrôleur de la localité | DOSSIER |
| `CLOTURE_COPIE_ASSISTANT` | copie d'un PV FAVR après clôture du dossier | Assistant contrôleur de la localité | DOSSIER |
| `PIECE_AJOUTEE_APRES_RENVOI` | dossier complété par la PRMP après lettre de renvoi, à ré-examiner | Membre attributaire | DOSSIER |
| `CLOTURE_ELIGIBLE` | dossier clôturé éligible | Chargé de publication | DOSSIER |
| `NOUVEAU_MESSAGE` | message reçu (messagerie) | destinataire | MESSAGE |

*(Autres types existants : `NOUVELLE_INSCRIPTION`, `INSCRIPTION_VALIDEE/REFUSEE`, `DEMANDE_RETRAIT_A_VALIDER`, `RETRAIT_ACCEPTE/REFUSE`, `FIN_MANDAT`, `ALERTE_DELAI`, `DISPATCH_CC`.)*

**Exemple — réponse `/mes`**
```json
[{
  "idNotification": 1042, "typeNotif": "EXAMEN_A_FAIRE",
  "destinataireRef": "CTRMEM", "destinataireType": "CONTROLEUR",
  "idObjet": 312, "typeObjet": "DOSSIER", "idDossier": 312,
  "titre": "Dossier à examiner", "corps": "Le dossier 312 vous a été dispatché pour examen.",
  "dateEnvoi": "2026-06-16T09:15:30", "lu": false, "dateLecture": null, "canal": "SYSTEME"
}]
```

---

## Organigrammes
**Ressource** `/api/organigrammes` — Gestion de la hiérarchie (§3.8) : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `OrganigrammeDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idOrganigramme | number | Oui (PK, au POST) | clé primaire |
| idMinistere | number | Oui | @NotNull |
| libelle | string | Non | max 200 |
| version | string | Non | max 20 |
| dateValidation | string (date) | Non | |
| actif | boolean | Oui | @NotNull |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/organigrammes | — | `OrganigrammeDto[]` | 200 | Authentifié |
| GET | /api/organigrammes/{id} | — | `OrganigrammeDto` | 200, 404 | Authentifié |
| POST | /api/organigrammes | `OrganigrammeDto` | `OrganigrammeDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/organigrammes/{id} | `OrganigrammeDto` | `OrganigrammeDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/organigrammes/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idOrganigramme (number).

**Exemple — requête**
```json
{ "idOrganigramme": 7, "idMinistere": 3, "libelle": "Organigramme MEF", "version": "2026.1", "dateValidation": "2026-01-15", "actif": true }
```

---

## Points de contrôle
**Ressource** `/api/points-ctrls` — Référentiel (grille de contrôle) : lecture ouverte ; écriture `ADMINISTRATEUR`.

> ⚠️ **Grille affinée par sous-type (règle ajoutée, 2026-07-17).** Un point porte sa **famille**
> (`idTypeDossier` = `DDP`/`DMC`/`DDM`) et, **facultativement**, un **sous-type ciblé** (`idSousType`) :
> `null` = point **commun** à toute la famille ; renseigné = point **spécifique** à ce sous-type (ex. le
> contrôle « AGPM joint et conforme » du seul `PPM-AGPM` — migration `2026-07-17_points_ctrl_sous_type.sql`).
> La **grille effective** d'un dossier = points communs de sa famille **+** points spécifiques de son
> sous-type : c'est ce que renvoie **`GET /api/points-ctrls?sousType=X`** (la famille est déduite), la
> requête de l'**écran d'examen** — la grille d'un `PPM` (7 points) ≠ celle d'un `PPM-AGPM` (8 points).
> Chaque point renvoyé porte sa **`portee`** (`LIGNE`/`DOSSIER`, cf. table) : le front sait ainsi lesquels
> s'évaluent **par ligne de marché** et lesquels **une fois pour le dossier**.
> `?typeDossier=` seul liste **tous** les points de la famille (écran admin). Gardes → **400** : sous-type
> inconnu ; sous-type hors de la famille (`?typeDossier=` + `?sousType=` incohérents, ou POST/PUT dont
> `idSousType` n'appartient pas à `idTypeDossier`). **Écran admin** : le dropdown sous-type se remplit via
> `GET /api/sous-type-dossiers/par-famille/{famille}` selon la famille choisie.

**Champs `PointsCtrlDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPointCtrl | number | Oui (PK, au POST) | clé primaire |
| libelPointCtrl | string | Non | |
| decriptPointCtrl | string | Non | |
| ordrePointCtrl | number | Non | |
| obligatoire | boolean | Oui | @NotNull |
| idTypeDossier | string | Oui | @NotBlank — **famille** (`DDP`/`DMC`/`DDM`) |
| idSousType | string | Non | max 20 — sous-type ciblé (doit appartenir à la famille, sinon 400) ; `null` = commun |
| portee | string | Non | ⚠️ **règle ajoutée 2026-07-21** — **`LIGNE`** (point évalué par ligne de marché) ou **`DOSSIER`** (inter-lignes, ex. « fractionnement illicite »). Absent/vide en entrée → défaut **`LIGNE`** ; code inconnu → **400**. **Toujours renseigné en sortie** — le front lit ce champ pour placer chaque point (par ligne / au niveau dossier). Migration `2026-07-21_points_ctrl_portee.sql` (fractionnement → DOSSIER, autres → LIGNE) |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/points-ctrls | — | `PointsCtrlDto[]` | 200, 400 | Authentifié — filtres `?typeDossier=` `&sousType=` (grille effective) |
| GET | /api/points-ctrls/{id} | — | `PointsCtrlDto` | 200, 404 | Authentifié |
| POST | /api/points-ctrls | `PointsCtrlDto` | `PointsCtrlDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/points-ctrls/{id} | `PointsCtrlDto` | `PointsCtrlDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/points-ctrls/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idPointCtrl (number).

**Exemple — requête** (point spécifique au sous-type PPM-AGPM)
```json
{ "idPointCtrl": 12, "libelPointCtrl": "Avis Général de Passation de Marché joint et conforme", "decriptPointCtrl": "L'AGPM accompagne le PPM et couvre les marchés en appel d'offres ouvert.", "ordrePointCtrl": 8, "obligatoire": true, "idTypeDossier": "DDP", "idSousType": "PPM-AGPM" }
```

---

## PPM
**Ressource** `/api/ppms` — Lecture **scopée au périmètre de l'appelant** (⚠️ changement de portée, voir note). **`POST` réservé `ADMINISTRATEUR`** (la saisie passe par `/api/saisies/ppm`) ; **`PUT` réservé `PRMP`/`ADMINISTRATEUR`** (édition de l'en-tête d'un brouillon) ; **`DELETE` réservé `PRMP` propriétaire** — uniquement si le **dossier rattaché est en BROUILLON** (sinon **403**/**409**), avec ⚠️ **cascade** des marchés du PPM **et** de leurs dates prévisionnelles (même transaction). Un PPM ne se rattache qu'à un dossier de **type PPM, en BROUILLON, propriété de la PRMP** (sinon **409**/**403**).

> **⚠️ Scoping serveur (changement de portée, §1/§3.1).** `GET /api/ppms` ne renvoie **plus toute la
> table** : Président/Administrateur → tout ; **PRMP → les siens** (`t_ppm.ID_PRMP`) **hors BROUILLON**
> (écran « Mes PPM & marchés ») ; contrôleur → ceux de **sa localité** (dossier non brouillon) ; autre
> profil → liste vide. `GET /api/ppms/{id}` hors périmètre → **403**. Corrige la fuite inter‑PRMP/localité
> (plus de filtrage côté client).
>
> ⚠️ **« Mes PPM & marchés » exclut les BROUILLON (filtrage serveur).** La liste `GET /api/ppms` de la PRMP
> ne comporte **plus les dossiers `BROUILLON`** : ils relèvent de l'écran **« Mes brouillons »**
> (`GET /api/dossiers?statut=BROUILLON`). Le détail d'un brouillon reste lisible par son propriétaire via
> **`GET /api/ppms/{id}`** (non filtré par statut). Filtrage **côté serveur** (sécurité), pas un simple
> masquage front.

**Champs `PpmDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPpm | number | Oui (PK, au POST) | clé primaire |
| idDossier | number | Oui | @NotNull |
| exercice | number | Oui | @NotNull |
| signataire | string | Oui | @NotBlank, max 210 (auto-rempli « prénoms + nom » PRMP, couvre prénoms 100 + nom 100 + marge ; idem `EditionPpmRequest`) |
| dateSignature | string (date) | Oui | @NotNull |
| datePpmInit | string (date) | Non | |
| numMajPrec | number | Non | |
| dateMajPrec | string (date) | Non | |
| numMaj | number | Non | |
| dateMaj | string (date) | Non | |
| reference | string | Oui | @NotBlank, max 100 |
| libelle | string | Non | max 200 |
| dateReceptionCnm | string (date) | Non | |
| idLocalite | string | Non | max 5 |
| vu | string | Non | max 100 |
| idPrmp | string | Non | max 10 |
| motifMaj | string | Non | max 500 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/ppms | — | `PpmDto[]` (scopé) | 200 | Authentifié — ⚠️ **paginable** (`?page=&size=`, cf. Conventions) |
| GET | /api/ppms/{id} | — | `PpmDto` | 200, 403, 404 | Authentifié (dans son périmètre) |
| POST | /api/ppms | `PpmDto` | `PpmDto` | 201, 400 | Authentifié |
| PUT | /api/ppms/{id} | `PpmDto` | `PpmDto` | 200, 400, 404 | Authentifié |
| PATCH | /api/ppms/{id}/rectifier | `PpmDto` | `PpmDto` | 200, 403, 404, 409 | PRMP (propriétaire) |
| DELETE | /api/ppms/{id} | — | — | 204, 403, 404, 409 | PRMP (propriétaire, brouillon) — ⚠️ cascade marchés + prévisions ; **+ dossier si brouillon vide** |

`{id}` = idPpm (number).

> ⚠️ **Suppression cohérente (règle ajoutée).** `DELETE /api/ppms/{id}` supprime le PPM et ses marchés/prévisions
> (cascade), **et** — si le **dossier** devient un **brouillon pur** (plus aucun PPM ni marché, **et sans
> historique de circuit** : ni réception ni demande de retrait) — supprime aussi le **dossier** (sinon un brouillon
> vide subsisterait dans « Mes brouillons » = `GET /api/dossiers?statut=BROUILLON`). **Conservés** : un dossier
> portant un **autre PPM** (cas multi-PPM) ; un dossier **revenu BROUILLON via retrait** (il porte des traces FK —
> réception, demande de retrait, notifications — non supprimées).

> ⚠️ **Édition restreinte (rectification) — règle ajoutée.** `PATCH /api/ppms/{id}/rectifier` permet à la PRMP
> propriétaire de corriger l'en-tête d'un PPM dont le **dossier est `EN_ATTENTE_DECISION_PRMP`**, **sans repasser
> par le brouillon**. Statut du dossier **inchangé** (reste `EN_ATTENTE_DECISION_PRMP` jusqu'à
> `POST /api/dossiers/{id}/resoumettre`). Hors `EN_ATTENTE_DECISION_PRMP` → **409** ; non-propriétaire → **403** ;
> profil **PRMP strict** (Admin/vérificateur → **403**). Identité **figée** (idDossier, idPrmp, idLocalite —
> **non requis** dans le corps, ignorés s'ils sont envoyés ; le PATCH ne valide pas ces champs).
> Tracé `t_audit_log` (`MODIFICATION_RECTIFICATION`, `NOM_TABLE=t_ppm`).
> *(DAO/MAOO : sans contenu éditable, donc non concernés. Les lignes de marché se corrigent via
> `PATCH /api/marches/{id}/rectifier` ; pas d'ajout/suppression de lignes en rectification.)*

**Exemple — requête**
```json
{ "idPpm": 88, "idDossier": 312, "exercice": 2026, "signataire": "Le Signataire", "dateSignature": "2026-02-10", "reference": "PPM-2026-0312", "libelle": "PPM exercice 2026", "idLocalite": "ANT", "idPrmp": "PRMP001" }
```

---

## Profils
**Ressource** `/api/profiles` — Référentiel RBAC (§3.8) : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `ProfileDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idProfile | number | Oui (PK, au POST) | clé primaire |
| profile | string | Non | max 50 — libellé du profil |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/profiles | — | `ProfileDto[]` | 200 | Authentifié |
| GET | /api/profiles/{id} | — | `ProfileDto` | 200, 404 | Authentifié |
| POST | /api/profiles | `ProfileDto` | `ProfileDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/profiles/{id} | `ProfileDto` | `ProfileDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/profiles/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idProfile (number). *Le rôle métier est déduit du libellé `profile` (ex. « Chef de commission »).*

**Exemple — requête**
```json
{ "idProfile": 2, "profile": "Président" }
```

---

## PRMP
**Ressource** `/api/prmps` — Gestion des comptes PRMP (§3.8) : lecture ouverte ; écriture `ADMINISTRATEUR`. *(Fiche de la personne PRMP, distincte des PPM/marchés qu'elle soumet. Voir aussi l'auto-inscription dans **Authentification**.)*

**Champs `PrmpDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPrmp | string | Oui (PK, au POST) | clé primaire = **matricule** de la PRMP (identifiant unifié), max 10 |
| nomPrmp | string | Oui | @NotBlank, max 100 |
| prenomsPrmp | string | Oui | @NotBlank, max 100 |
| arreteNomin | string | Oui | @NotBlank, max 100 |
| dateNomin | string (date) | Oui | @NotNull |
| cin | string | Oui | @NotBlank, max 12 |
| dateCin | string (date) | Oui | @NotNull |
| lieuCin | string | Oui | @NotBlank, max 50 |
| emailPrmp | string | Oui | @NotBlank, max 100 |
| telPrmp | string | Oui | @NotBlank, max 20 |

> La PRMP n'a **pas de localité propre** : `PrmpDto` ne porte plus de champ `idLocalite` (la
> localité d'un dossier vient de l'entité contractante choisie à la saisie).
>
> **`GET /par-localite/{idLocalite}`** liste les PRMP rattachées à une localité **via leurs entités contractantes
> actives** (`t_prmp_entite` actif → `tr_entite_contract.ID_LOCALITE`) — puisque la PRMP n'a pas de localité propre.
> Liste **distincte**, **vide** si aucune (rattachement inactif exclu ; pas de 404).
>
> **`GET /par-entite/{idEntiteContract}`** renvoie la PRMP rattachée à une entité contractante **via son affectation
> active** (`t_prmp_entite`) — **0 ou 1** (invariant : une seule PRMP active par entité), en **liste** (vide si aucune,
> affectation inactive exclue ; pas de 404).
>
> **`GET /par-nom/{nom}`** — recherche **partielle** sur `nomPrmp` (**contient**, **insensible à la casse**) ;
> liste **vide** si aucun résultat (pas de 404). `{nom}` est un fragment (URL-encoder si espaces/accents).

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/prmps | — | `PrmpDto[]` | 200 | Authentifié |
| GET | /api/prmps/{id} | — | `PrmpDto` | 200, 404 | Authentifié |
| GET | /api/prmps/par-localite/{idLocalite} | — | `PrmpDto[]` | 200 | Authentifié |
| GET | /api/prmps/par-entite/{idEntiteContract} | — | `PrmpDto[]` | 200 | Authentifié |
| GET | /api/prmps/par-nom/{nom} | — | `PrmpDto[]` | 200 | Authentifié |
| POST | /api/prmps | `CreerPrmpRequest` (**JSON**) | `PrmpDto` | 201, 400, 403, 409 | ADMINISTRATEUR |
| POST | /api/prmps | **`multipart/form-data`** : part `data` (JSON `CreerPrmpRequest`) + `arrete`/`cin`/`photo` (opt.) | `PrmpDto` | 201, 400, 403, 409 | ADMINISTRATEUR |
| PUT | /api/prmps/{id} | `PrmpDto` | `PrmpDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/prmps/{id} | — | — | 204, 404, 409 | ADMINISTRATEUR |
| POST | /api/prmps/suppression-lot | `SuppressionLotPrmpRequest` `{matricules[]}` | `SuppressionLotPrmpResult` | 200, 400, 403 | ADMINISTRATEUR |
| POST | /api/prmps/{id}/pieces/{type} | `multipart/form-data` (part `fichier`) | `PieceJointeMetaDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| GET | /api/prmps/{id}/pieces/{type} | — | fichier (binaire) | 200, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/prmps/{id}/pieces/{type} | — ; `type` ∈ `ARRETE_NOMIN`/`CIN`/`PHOTO` | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = idPrmp (= matricule ; string).

> **Création avec compte (credentials optionnels).** `CreerPrmpRequest` = champs de `PrmpDto` **+ `login`/`motDePasse`
> optionnels** (`login` ≤ 100, `motDePasse` 8–72). **Fournis (ensemble)** → crée aussi le **compte PRMP actif**
> (`TYPE_ACTEUR=PRMP`, `refActeur=idPrmp`), connectable immédiatement (parité `POST /api/ugpms`, pas de workflow
> `EN_ATTENTE`). **Absents** → fiche seule (rétro-compat). **400** si un seul des deux credentials est fourni ou si
> `motDePasse` < 8 ; **409** si `idPrmp` ou `login` déjà pris. Valable pour les deux variantes (JSON et multipart).
>
> **Création avec pièces (multipart).** En plus de la variante **JSON pure** (rétro-compatible), `POST /api/prmps`
> accepte une variante **`multipart/form-data`** — miroir de l'inscription : part `data` (JSON `CreerPrmpRequest`) + parts
> fichiers **`arrete`/`cin`/`photo`**, toutes **optionnelles** (l'Admin crée la fiche et complète les pièces
> ensuite). Contraintes fichiers : **PDF/JPEG/PNG** (magic-bytes), **arrêté ≤ 10 Mo**, **CIN/photo ≤ 5 Mo** → sinon
> **400**. Pièces stockées sous la clé `idPrmp` (types `ARRETE_NOMIN`/`CIN`/`PHOTO`). Dépôt/remplacement ultérieur
> via `POST /{id}/pieces/{type}` (**404** si PRMP inconnue) ; téléchargement via `GET /{id}/pieces/{type}` (**404**
> si la pièce est absente) ; **suppression** d'une pièce (sans supprimer la PRMP) via `DELETE /{id}/pieces/{type}`
> → **204**, **404** si la PRMP ou la pièce est inconnue. Ces sous-chemins pièces sont réservés **ADMINISTRATEUR**.
>
> **DELETE** supprime la PRMP, **ses pièces** (`t_piece_jointe`) **et son compte d'authentification**. **Garde** :
> **409** tant que la PRMP porte des données liées (dossiers, PPM, entités rattachées, demandes de retrait,
> indicateurs, ou UGPM de tutelle) — retirer d'abord ces éléments ; **404** si l'`idPrmp` est inconnu.
>
> **POST `/suppression-lot`** — suppression **en lot par matricule**, **tolérante** : `SuppressionLotPrmpRequest`
> = `{matricules: string[]}` (au moins un, sinon **400**) → **200** `SuppressionLotPrmpResult` = `{supprimes:
> string[], introuvables: string[], bloques: string[]}`. Chaque PRMP existante **sans données liées** est supprimée
> (avec son compte) → `supprimes` ; les absents → `introuvables` ; les PRMP **à données liées** (même garde que le
> 409 unitaire) → `bloques` (non supprimées). **Jamais d'échec global** ; doublons ignorés.

**Exemple — requête**
```json
{
  "idPrmp": "IMP001", "nomPrmp": "Randria", "prenomsPrmp": "La Personne",
  "arreteNomin": "ARR-2024-001", "dateNomin": "2024-01-15", "cin": "101011112222",
  "dateCin": "2010-05-05", "lieuCin": "Antananarivo", "emailPrmp": "prmp@ministere.mg",
  "telPrmp": "0330000001", "idLocalite": "ANT"
}
```

---

## Publications
**Ressource** `/api/publications` — Portail de transparence (§3.7). CRUD + `publier`/`retirer` réservés à `CHARGE_PUBLICATION` ; `consulter` ouvert à tout authentifié. À la création, `statutPubli` est forcé à `EN_ATTENTE` et `nbConsultations` à `0`.

**Champs `PublicationDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPublication | number | Oui (PK, au POST) | clé primaire |
| typeObjet | string | Oui | @NotBlank, max 20 |
| idObjet | number | Oui | @NotNull |
| datePublication | string (date-time) | Non | renseigné à la publication |
| imPubliePar | string | Non | max 7 — renseigné à la publication |
| statutPubli | string | Non | max 20 — `EN_ATTENTE` / `PUBLIE` / `RETIRE` |
| dateRetrait | string (date) | Non | renseigné au retrait |
| motifRetrait | string | Non | max 300 — renseigné au retrait |
| nbConsultations | number | Non | |

**Champs `RetraitPublicationRequest`** (corps de `retirer`)

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| motifRetrait | string | Oui | @NotBlank, max 300 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/publications | — | `PublicationDto[]` | 200 | CHARGE_PUBLICATION |
| GET | /api/publications/{id} | — | `PublicationDto` | 200, 404 | CHARGE_PUBLICATION |
| POST | /api/publications | `PublicationDto` | `PublicationDto` | 201, 400, 403 | CHARGE_PUBLICATION |
| PUT | /api/publications/{id} | `PublicationDto` | `PublicationDto` | 200, 400, 404 | CHARGE_PUBLICATION |
| DELETE | /api/publications/{id} | — | — | 204, 404 | CHARGE_PUBLICATION |
| POST | /api/publications/{id}/publier | — | `PublicationDto` | 200, 404, 409 | CHARGE_PUBLICATION |
| POST | /api/publications/{id}/retirer | `RetraitPublicationRequest` | `PublicationDto` | 200, 400, 404, 409 | CHARGE_PUBLICATION |
| POST | /api/publications/{id}/consulter | — | `PublicationDto` | 200, 404 | Authentifié |

`{id}` = idPublication (number). `publier` : EN_ATTENTE→PUBLIE (409 sinon) ; `retirer` : PUBLIE→RETIRE (409 sinon) ; `consulter` : incrémente `nbConsultations`.

**Exemple — requête (création) / retrait**
```json
{ "idPublication": 87, "typeObjet": "PPM", "idObjet": 4521 }
```
```json
{ "motifRetrait": "Document erroné, republication à suivre" }
```

---

## PV d'examen
**Ressource** `/api/pv-examens` — CRUD : POST/PUT = `MEMBRE`/`CHEF_COMMISSION`/`PRESIDENT` ; DELETE = `ADMINISTRATEUR`. Lecture filtrée par localité. À la création, `statutPv` est forcé à `BROUILLON` et `nbNavettes` à `0`. Cycle : `BROUILLON → PROJET_SOUMIS → EN_RECTIFICATION → PROJET_ACCEPTE → SIGNE`.

**Champs `PvExamenDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPv | number | Oui (PK, au POST) | clé primaire |
| idExamen | number | Oui | @NotNull |
| idAvis | string | Non (⚠️ 2026-08-01) | max 10 — **nullable** : `null` jusqu'à la **clôture de navette** (`…/accepter`, Président/CC) ; requis pour `signer` (409 sinon) |
| imCtrlPresident | string | Non | max 7 |
| imCtrlCc | string | Non | max 7 |
| imCtrlMembre | string | Oui (validation) | @NotBlank, max 7 — **valeur ignorée** : dérivée de l'attribution (`Examen → Dispatch.imCtrlMembre`) ; examen sans attributaire → 409 |
| syntheseObservations | string | Non | |
| statutPv | string | Oui | @NotBlank, max 20 (forcé `BROUILLON` à la création) |
| nbNavettes | number | Oui | @NotNull (forcé `0` à la création) |
| dateSoumissionInitiale | string (date) | Non | |
| dateAcceptation | string (date) | Non | |
| dateSignaturePresident | string (date) | Non | |
| dateSignatureCc | string (date) | Non | |
| dateSignatureMembre | string (date) | Non | |
| datePv | string (date) | Non | |
| referencePv | string | Non | max 100 — référence libre (saisie ; reprise dans les notifications) |
| refePv | string | — (réponse) | max 120 — **référence officielle dérivée du dossier**, générée serveur, **unique** (lecture seule) |
| idSecretaireSeance | string | — (⚠️ posé à la **clôture de navette**, 2026-08-01) | max 7 — **Secrétaire de séance** : Vérificateur titulaire de la localité **ou** contrôleur couvert par une paire « → Vérificateur » active (⚠️ élargi 2026-08-15 ; validé à `…/pv-examens/{id}/accepter` ; encore accepté à `…/examens/{id}/soumettre` si fourni) |
| nomSecretaireSeance | string | — (réponse) | nom complet du secrétaire de séance (« prénoms nom »), peuplé serveur — lecture seule |
| documentDisponible | boolean | — (réponse) | ⚠️ **Contrat révisé 2026-08-19** — PV **`SIGNE`** : `true` seulement quand le **fichier est prêt maintenant** (`CHEMIN_DOCUMENT` non nul) ; **`false` pendant la fenêtre de génération post-commit** qui suit la signature. PV **non signé** (projet) : sens historique conservé — `true` si le PV est **éligible** (un **modèle Word existe pour le cas** : avis `FAVR`/`FAV`/`DEF` + PPM avec ≥ 1 ligne de marché, **quel que soit le mode de passation** et la localité ; cf. tableau des modèles §PV). Lecture seule, peuplé serveur → le front masque « Télécharger le PDF » tant que c'est `false` |

> ⚠️ **Disponibilité du document (`documentDisponible`) — contrat révisé (2026-08-19, génération post-commit).**
> La génération du PDF (conversion Word, plusieurs secondes) est **sortie du chemin de la signature** : la
> signature finale marque le PV `SIGNE` et **répond immédiatement** ; le document est produit **après commit**
> en tâche de fond (`PvDocumentTache`), qui renseigne `CHEMIN_DOCUMENT` quand il est prêt. Dans cet
> intervalle, `documentDisponible` est **`false`** (option retenue avec le front — `pv-definitifs` et
> `detail-pv-modal` savent afficher un PV signé sans document) ; il passe à `true` au rafraîchissement
> suivant. Un **échec de génération ne peut plus faire échouer la signature** (journalisé ; le
> téléchargement conserve sa **régénération paresseuse** en filet). **Rattrapage des PV signés sans
> fichier** (antérieurs au correctif ou génération échouée) : leur consultation déclenche la production en
> arrière-plan — disponible au rafraîchissement suivant, sans requête lente. Un PV non éligible (ex. avis
> **`NSP`**, ou dossier **sans PPM**) → `false` définitif, et `…/document` renvoie **404**. Le
> convertisseur Word est **préchauffé au démarrage** (`app.pv.document.prechauffage=true`) pour que la
> première génération ne paie pas le lancement de Word.

> ⚠️ **Référence du PV (`refePv`) — règle ajoutée.** À la création, le serveur dérive `refePv` du `refeDossier`
> du dossier rattaché en insérant **`/PV` avant l'année** : `00003/PPM/CNM/2026` → `00003/PPM/CNM/PV/2026`
> (dossier central), `00004/PPM/CRM-TMS/2026` → `00004/PPM/CRM-TMS/PV/2026` (régional) — le **segment
> localité est donc hérité du dossier**, jamais recalculé.
> Dérivée **uniquement** si `refeDossier` est au format `…/YYYY` (sinon `null`). **Unique** : créer un 2ᵉ PV sur le
> même dossier (même `refePv`) → **409**. Distincte du champ libre `referencePv`.

**Champs `PvActionRequest`** (corps des actions de workflow)

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| imActeur | string | Oui | @NotBlank, max 7. **Non utilisé pour l'identité** : `signer` enregistre l'utilisateur authentifié (JWT), pas ce champ |
| commentaire | string | Conditionnel | obligatoire pour `retourner` (sinon 409) |
| role | string | Conditionnel | max 20 — obligatoire pour `signer` : `MEMBRE` / `PRESIDENT` / `CC` |
| idAvis | string | Conditionnel (⚠️ 2026-08-01) | max 10 — **obligatoire pour `accepter`** (clôture de navette : pose l'avis global du PV, 400 sinon) ; ignoré ailleurs |
| idSecretaireSeance | string | Conditionnel (⚠️ 2026-08-01) | max 7 — **obligatoire pour `accepter`** : Vérificateur **titulaire** de la localité du dossier **OU** contrôleur couvert par une paire « → Vérificateur » **active** (⚠️ élargi 2026-08-15 ; **409** sinon) ; ignoré ailleurs |

> ⚠️ **Décisions (2026-08-15, circuit court) — vérification par délégation et Secrétaire de séance.**
> - Le **passage vérificateur** (décisions levée/maintenue sur les observations, `POST /api/observations-pv/passage`, et la suite de la navette) est une **tâche de profil** : Vérificateur **titulaire OU** contrôleur couvert par une paire « → Vérificateur » **active** (garde centrale) — il n'est **pas** restreint au contrôleur désigné `idSecretaireSeance`. Dans le circuit court, le décideur (CC/Président par délégation) **peut être l'attributaire du même dossier** (auteur des observations) : **assumé, sans garde de séparation** — la vérification juge la levée par la **PRMP**, et chaque décision est tracée avec l'identité du décideur.
> - **Désignation `idSecretaireSeance` ÉLARGIE (décision produit 2026-08-15 — annule le statu quo du même jour)** : Vérificateur **titulaire** de la localité du dossier, **OU** contrôleur couvert par une paire « → Vérificateur » **active** de `t_delegation_profil` — même modèle data-driven que la garde attributaire du dispatch (paire désactivée en base → retour au refus **409** sans changement de code). Le délégué reste dans **son périmètre** : sa localité doit être celle du dossier ; un contrôleur **sans localité** (Président) est accepté partout. Le Président/CC peut donc **se désigner lui-même** au panneau d'acceptation (« moi-même ⤴ »). Message de refus : « Le Secrétaire de séance doit être un Vérificateur de la localité du dossier, ou un contrôleur couvert par une délégation active vers Vérificateur. » Même garde à `POST /api/examens/{id}/soumettre` quand le champ y est fourni (400 `champ:idSecretaireSeance`).
> - **Conséquence assumée (circuit court)** : au **bloc Signataires du PV**, le CC auto-attributaire et auto-désigné peut apparaître sur **toutes les mentions** (Membre attributaire, Secrétaire de séance — et, depuis la levée du verrou d'auto-co-signature du 2026-08-15, les **deux parts de signature**). Sur le **document PV** généré, la **ligne Membre** et le nom du **Secrétaire de séance** sont suffixés **« (par délégation) »** quand le titulaire du rôle n'est pas le profil attendu (Membre / Vérificateur) ; la ligne du **propre rôle** du signataire (Président/CC) reste sans mention.

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/pv-examens | — | `PvExamenDto[]` | 200 | Authentifié (filtré) — **projets de PV** (non signés) |
| GET | /api/pv-examens/definitifs | — | `PvExamenDto[]` | 200 | Authentifié (filtré) — **PV signés** uniquement |
| GET | /api/pv-examens/{id} | — | `PvExamenDto` | 200, 404 | Authentifié (filtré) — tout PV (y c. signé) |
| GET | /api/pv-examens/{id}/document | — | `application/pdf` | 200, 403, 404 | Authentifié (périmètre localité) — **PDF du Projet de PV** |
| POST | /api/pv-examens | `PvExamenDto` | `PvExamenDto` | 201, 400, 403 | MEMBRE / CC / PRESIDENT |
| PUT | /api/pv-examens/{id} | `PvExamenDto` | `PvExamenDto` | 200, 400, 404, 409 | MEMBRE / CC / PRESIDENT |
| DELETE | /api/pv-examens/{id} | — | — | 204, 404 | ADMINISTRATEUR |
| POST | /api/pv-examens/{id}/soumettre | `PvActionRequest` | `PvExamenDto` | 200, 400, 403, 404, 409 | MEMBRE / CC / PRESIDENT |
| POST | /api/pv-examens/{id}/retourner | `PvActionRequest` | `PvExamenDto` | 200, 400, 403, 404, 409 | CC / PRESIDENT |
| POST | /api/pv-examens/{id}/accepter | `PvActionRequest` | `PvExamenDto` | 200, 403, 404, 409 | CC / PRESIDENT |
| POST | /api/pv-examens/{id}/signer | `PvActionRequest` | `PvExamenDto` | 200, 400, 403, 404, 409 | MEMBRE / CC / PRESIDENT |

`{id}` = idPv (number). `soumettre` : BROUILLON|EN_RECTIFICATION→PROJET_SOUMIS ; `retourner` : PROJET_SOUMIS→EN_RECTIFICATION (`commentaire` obligatoire) ; `accepter` : PROJET_SOUMIS→PROJET_ACCEPTE — ⚠️ **clôture de la navette (2026-08-01)** : `idAvis` + `idSecretaireSeance` **obligatoires** (400 sinon ; le serveur pose l'avis global et le secrétaire sur le PV) ; `signer` : passe à SIGNE quand le Membre **et** (le Président **ou** le CC) ont signé — **409 si l'avis global n'est pas posé** (navette non close).

> ⚠️ **Cohérence avis ↔ observations (règle ajoutée 2026-08-01).** À `accepter`, le serveur vérifie la cohérence
> de l'avis avec les **observations de l'examen** (points de contrôle non conformes **+ pièces jointes non
> conformes**, `t_examen_detail` + `t_examen_piece`) : **≥ 1 observation → `FAV` (« Favorable » sans réserve)
> refusé** (400 — choisir `FAVR` ou `DEF`) ; **0 observation → `FAVR` (« Favorable avec réserves ») refusé**
> (400 — choisir `FAV`). `DEF`/`NSP` restent libres (appréciation souveraine). Le front (panneau de clôture)
> pré-sélectionne l'avis suggéré et affiche le nombre d'observations.

> ⚠️ **Garde-fou de cohérence dossier↔PV (règle ajoutée).** `DELETE /api/pv-examens/{id}` réaligne le dossier : si, après suppression, le dossier n'a **plus aucun PV `SIGNE`** et se trouve encore **`EN_VERIFICATION`**, il est ramené à **`EXAMINE`** (état « examiné, en attente de PV »). Un dossier ne peut donc plus rester bloqué `EN_VERIFICATION` (« PV signé introuvable » côté vérification) alors que son PV signé n'existe plus. Les autres statuts sont laissés inchangés.

> ⚠️ **Liste scindée projets / définitifs (règle ajoutée).** `GET /api/pv-examens` ne retourne que les **projets de PV** (statut ≠ `SIGNE`) ; dès qu'un PV est **signé** (`SIGNE`) il **quitte** cette liste et apparaît dans **`GET /api/pv-examens/definitifs`** (PV signés uniquement). Les deux listes restent **scopées par localité**. L'accès direct `GET /api/pv-examens/{id}` reste valable pour **tout** PV, signé ou non.

> ⚠️ **PV définitifs consultables par la PRMP (2026-08-02).** La PRMP (hors périmètre localité) voit les
> **PV `SIGNE` de SES dossiers** (via PPM, même périmètre que `/lettre-renvois/mes-lettres`) — elle en a
> besoin pour **rectifier selon les observations du PV** : `GET /definitifs` (liste scopée),
> `GET /{id}` et `GET /{id}/document` (PDF) autorisés **si le PV est SIGNÉ et relève d'un de ses
> dossiers** (403 sinon). Les **projets** restent invisibles (liste vide). La notification `PV_SIGNE`
> est désormais **actionnable** (ref = idPrmp, objet `PV` + `idDossier` — avant : e-mail seul, sans
> objet) ; front : menu PRMP « PV définitifs » (`/prmp/pv-definitifs`, écran partagé lecture seule),
> clic notification PV → cet écran. ⚠️ La PRMP ne reçoit que la **VERSION PDF** du PV (document officiel
> signé), **affichée directement** au clic (« Afficher le PV » → visionneuse iframe blob, URL révoquée à
> la fermeture ; le lecteur du navigateur offre impression/enregistrement) — pas de modal de détail
> (reconstruction interne réservée aux contrôleurs).

> ⚠️ **Téléchargement du PDF du PV (règle ajoutée).** `GET /api/pv-examens/{id}/document` renvoie le **PDF du PV** (`application/pdf`, en pièce jointe) **lu sur le FSX** (`t_pv_examen.CHEMIN_DOCUMENT`). ⚠️ 2026-08-01 : le fichier porte la **référence du PV** (`refePv`, repli `referencePv`, « / » → « - » — ex. `00020-PPM-CRM-ANT-PV-2026.pdf`) ; le front télécharge via une ancre `download` du même nom (plus de blob anonyme). Accès dans le **périmètre de localité** (même contrôle que `GET /api/pv-examens/{id}`). Si le chemin est absent (PV signé avant le correctif) ou le fichier introuvable, le document est **régénéré à la demande** (si le PV est éligible). **404** seulement si le PV n'est **pas éligible** à la génération (cf. règle « PV — document généré »).

**`signer` — authentification de la signature (dans le service).** L'endpoint autorise largement (`MEMBRE`/`CHEF_COMMISSION`/`PRESIDENT`) mais le service vérifie que le **signataire authentifié** correspond au `role` signé et enregistre son identité (`IM_CTRL_MEMBRE`/`IM_CTRL_PRESIDENT`/`IM_CTRL_CC` = matricule du signataire) :
- `role=MEMBRE` → l'appelant doit être le **Membre attributaire** du PV (`IM_CTRL_MEMBRE`), non déléguable → **403** sinon ;
- `role=PRESIDENT` → profil **PRESIDENT** réel → **403** sinon ;
- `role=CC` → profil **CHEF_COMMISSION** **et localité du dossier** → **403** sinon ;
- co-signataire (Président/CC) **≠ Membre signataire** : auto-co-signature interdite → **409** — **sauf** (⚠️ décision produit 2026-08-15, circuit court) signataire couvert par une paire « → Membre » **active** : le Président/CC attributaire signe **les deux parts** (deux actions successives ; paire désactivée → blocage rétabli sans changement de code) ;
- `signer` hors `PROJET_ACCEPTE` → **409** ;
- ⚠️ **une signature par rôle (2026-08-02)** : si la date de signature du rôle est **déjà posée**
  (`dateSignatureMembre`/`dateSignaturePresident`/`dateSignatureCc`), re-signer → **409** (« Le PV est
  déjà signé pour le rôle … »). Le front désactive le bouton « Signer » (libellé « Signé ✓ » + rappel
  « en attente des autres signataires ») dès que le signataire courant a signé ;
- ⚠️ **anti-doublon concurrent (2026-08-02)** : `signer` charge le PV avec un **verrou pessimiste**
  (la génération du PDF rend la signature longue ; des clics répétés lisaient tous `PROJET_ACCEPTE`
  et notifiaient la signature plusieurs fois). Les requêtes concurrentes sont sérialisées : la 2ᵉ
  voit l'état commité et reçoit 409. Le front désactive aussi le bouton **pendant** la requête
  (« Signature… »).

**Exemple — requête (création) / signature**
```json
{ "idPv": 312, "idExamen": 201, "idAvis": "FAV", "imCtrlMembre": "MEMANT1", "statutPv": "BROUILLON", "nbNavettes": 0, "syntheseObservations": "RAS" }
```
```json
{ "imActeur": "CTRPRE", "role": "PRESIDENT" }
```

---

## Rapports
**Ressource** `/api/rapports` — Ouvert à `PRESIDENT`, `ADMINISTRATEUR` et `CHEF_COMMISSION`. **Réponses binaires** (téléchargement), pas de JSON. Côté Angular : `responseType: 'blob'`.

**Endpoints**

| Méthode | URL | Paramètres (query) | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/rapports/dossiers | `from`, `to` (date `yyyy-MM-dd`), `localite` (facultatifs) | `application/pdf` (`rapport-dossiers.pdf`) | 200, 403 | PRESIDENT / ADMINISTRATEUR / CHEF_COMMISSION |
| GET | /api/rapports/dossiers/excel | `from`, `to` (date `yyyy-MM-dd`), `localite` (facultatifs) | `.xlsx` (`...spreadsheetml.sheet`) | 200, 403 | PRESIDENT / ADMINISTRATEUR / CHEF_COMMISSION |

`from`/`to` bornent la période (sur `DATE_REF`) ; absents → tous les dossiers.

**Portée par localité (§3.3).** Le contenu (et la colonne **Localité** des deux formats) est filtré selon le profil :
- **Chef de commission** : rapport **toujours forcé sur sa propre localité** ; le paramètre `localite` est **ignoré**. Si le CC n'a aucune localité associée → **403**.
- **Président / Administrateur** : **toutes commissions** par défaut ; peuvent cibler une commission précise via `?localite=ANT`.

**Exemples**
```
GET /api/rapports/dossiers?from=2026-01-01&to=2026-12-31
→ 200 OK, application/pdf, attachment; filename="rapport-dossiers.pdf"  (toutes localités si Président)

GET /api/rapports/dossiers?localite=TMS            (Président : cible la commission TMS)
GET /api/rapports/dossiers/excel                   (Chef de commission : forcé sur sa localité)
```

---

## Contrôle de complétude des pièces au dépôt (spec recevabilité 2026-08-02)
**Ressource** `/api/verification-pieces-depot` (table `t_verification_piece_depot`, **append-only** —
chaque décision est une nouvelle ligne, l'état courant d'une pièce attendue = sa DERNIÈRE décision) :
AVANT tout enregistrement de la réception, le **SECRÉTAIRE** vérifie **pièce par pièce** la liste de
référence du type (référentiel `type-piece-jointes` : libellé + obligatoire/facultatif, paramétrable en
admin) confrontée aux pièces déposées. Objet **distinct de la lettre de renvoi** (recevabilité formelle
au dépôt, **aucun archivage** — simple événement tracé).

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/verification-pieces-depot?dossier= | — | `VerificationPieceDepotDto[]` (historique ASC) | 200 | Authentifié |
| POST | /api/verification-pieces-depot | `{ idDossier, idTypePiece, idPiece?, decision, observation? }` | `VerificationPieceDepotDto` | 201, 400, 403, 404, 409 | **SECRETAIRE** |

> `decision` ∈ `CONFORME` / `NON_CONFORME` / `MANQUANTE` ; dossier requis `SOUMIS` ou
> `EN_ATTENTE_COMPLEMENTS_DEPOT` (409 sinon). Auteur (`imSecretaire`) et horodatage posés serveur (JWT) —
> traçabilité §6 : pièce, décision, observation, auteur, horodatage, historisés à chaque passage.

> ⚠️ **Signalement PRMP** : `POST /api/dossiers/{id}/signaler-pieces-manquantes` (**SECRETAIRE**, dossier
> `SOUMIS`, ≥ 1 défaut sinon 409) → notification `PIECES_MANQUANTES_DEPOT` à la PRMP (liste des pièces en
> défaut + observations), dossier → **`EN_ATTENTE_COMPLEMENTS_DEPOT`** (non enregistrable), événement
> `t_audit_log`. La PRMP dépose ses pièces (upload pièce initiale autorisé à ce statut) puis
> `POST /api/dossiers/{id}/transmettre-complements-depot` (**PRMP propriétaire**) → retour `SOUMIS` +
> notification `COMPLEMENTS_DEPOT_TRANSMIS` aux Secrétaires de la localité. À la reprise, les décisions
> `CONFORME` restent acquises (état courant par type) : seules les pièces en défaut sont à re-vérifier.

## Réceptions
**Ressource** `/api/receptions` — POST/PUT : profil `SECRETAIRE` (titulaire ou délégué) ; DELETE : `ADMINISTRATEUR`. Écriture limitée à sa localité (dossier hors localité → 403, sauf Président). Lecture filtrée par localité.

> ⚠️ **Garde de complétude (spec recevabilité 2026-08-02).** `POST /api/receptions` (réception initiale)
> est **refusé (409)** tant que toutes les pièces **obligatoires** du type n'ont pas une dernière décision
> `CONFORME` dans `t_verification_pieces_depot` — le message liste les pièces en cause. Le front reflète la
> règle (bouton « Enregistrer » désactivé + message explicite + progression x/y) ; la case « Dossier
> complet » manuelle est remplacée par cet état dérivé.

> **Garde de localité dès la 1ʳᵉ réception.** La localité du dossier est résolue par ordre :
> `t_dossier.idLocalite` → PPM (`Ppm.idLocalite`) → réception existante. Si elle est connue, un
> contrôleur d'une **autre** localité ne peut pas réceptionner (→ **403**), **y compris au premier
> passage** ; Président/Administrateur ne sont pas contraints. Si aucune localité n'est déterminable,
> aucune contrainte (la réception l'établit).
>
> **Pas de réception d'un brouillon** : si le dossier est au statut `BROUILLON` (non soumis), la réception
> est refusée (→ **409**).

> **Règles (sinon 409)** : `numPassage` ≥ 1 ; `numPassage = 1` ⟺ `typePassage = "INITIAL"`.
> **Effet `[Auto]`** : si `complet = true`, le dossier passe au statut `PRET_DISPATCH`.
>
> **Référence officielle générée à la réception (⚠️ règle ajoutée ; segment révisé 2026-07-20).** Au POST, le
> serveur génère et renvoie `reference` au format **`xxxxx/sous_type/code_localite/annee_exercice`**
> (ex. `00013/PPM/CRM-ANT/2026`, `00014/PPM-AGPM/CRM-ANT/2026`, `00015/DAO/CRM-ANT/2026`) :
> le segment central est le **sous-type** du dossier (`t_dossier.ID_SOUS_TYPE` : PPM, PPM-AGPM, DAO, DAOR…),
> **verbatim** (un sous-type dérivé comme PPM-AGPM apparaît tel quel — le `-` ne crée pas d'ambiguïté vis-à-vis
> des `/`) ; **repli** sur la **famille** (`ID_TYPE_DOSSIER`) si le sous-type est absent (dossier historique).
> `xxxxx` = compteur 5 chiffres incrémenté par la base, **indexé sur la FAMILLE** (`type_dossier`, `code_localite`
> fixe `DOSSIER`, `annee_exercice`) — table `t_sequence_reference`, sans compteur applicatif : **la numérotation
> reste continue au sein d'une famille** (un PPM puis un PPM-AGPM se suivent : `00013`, `00014`), seul le libellé
> du segment change ;
> `code_localite` = ⚠️ **règle CORRIGÉE (2026-08-04)** — dépend de la **localité DU DOSSIER**, jamais de
> celle de l'agent qui enregistre : dossier de la localité **centrale** (`Localite.ID_CENTRALE` = `ANT`)
> → **`CNM`** (ex. `00023/PPM/CNM/2026`) ; dossier **régional** → **`CRM-<localité>`** (ex.
> `00023/PPM/CRM-TMS/2026`). *(Auparavant le test portait sur la localité de l'utilisateur courant : un
> Secrétaire d'Antananarivo produisait `CRM-ANT` alors qu'un Président — sans localité — aurait produit
> `CNM` pour le même dossier.)* `annee_exercice` = exercice du PPM, sinon année courante.
> La référence est **persistée** sur le dossier (`REFE_DOSSIER`, vide depuis la soumission)
> **et sur la réception elle-même** (`t_reception.REFERENCE`) comme **snapshot immuable** : `GET /api/receptions`
> la renvoie telle qu'à la réception, **même après** une mutation ultérieure de `refeDossier` (ex. restauration
> de la référence PPM après un **retrait accepté**, cf. `POST /api/demande-retraits/{id}/accepter`). L'historique
> des réceptions reste ainsi correct indépendamment du dossier.
> Exemples : `00001/PPM/CNM/2026` et `00002/PPM-AGPM/CNM/2026` (dossiers centraux), `00003/PPM/CRM-TMS/2026`
> (dossier régional). La référence du **PV** et celle de la **lettre de renvoi** en dérivent, donc portent
> le même segment (`00001/PPM/CNM/PV/2026`, `00001/PPM/CNM/LR/2026`).
> *(Dossier sans `type_dossier` → `reference` non générée, la réception reste valide.)*
>
> **PK technique auto (⚠️ règle ajoutée).** Le secrétaire ne saisit plus de « N° de réception » : `idReception`
> est **allouée par le serveur** (`seq_reception`, Voie B) et **tout id fourni en entrée est ignoré**. Elle reste
> **présente en réponse** (le dispatch la référence). Le client n'a donc plus à l'envoyer ; il n'y a plus de
> conflit de doublon de PK sur ce champ.

**Champs `ReceptionDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idReception | number | Non (alloué serveur) | PK technique — **allouée par séquence** (`seq_reception`), ignorée si fournie en entrée ; **présente en réponse** (référencée par le dispatch) |
| idDossier | number | Oui | @NotNull |
| numPassage | number | Oui | @NotNull (≥ 1) |
| typePassage | string | Oui | @NotBlank, max 10 — `INITIAL` ⟺ numPassage=1 |
| imCtrlRecept | string | Non | max 7 |
| dateReception | string (date-heure) | Non | format **`yyyy-MM-dd HH:mm`** (date **et heure** de réception) |
| dateSoumission | string (date-heure) | — (réponse) | format **`yyyy-MM-dd HH:mm`** — date/heure de soumission du dossier rattaché (lecture seule) ; **`null`** pour un dossier ancien sans date de soumission |
| observation | string | Non | max 500 |
| complet | boolean | Non | si `true` → dossier `PRET_DISPATCH` |
| idReceptionPrec | number | Non | |
| reference | string | — (réponse) | référence officielle **persistée** (`t_reception.REFERENCE`), snapshot immuable posé au POST — renvoyée par `GET` ; lecture seule, indépendante des mutations ultérieures de `refeDossier` |

> **Dates/heures (⚠️ règle ajoutée).** `dateReception` est désormais une **date-heure** (`yyyy-MM-dd HH:mm`,
> colonne `t_reception.DATE_RECEPTION` en TIMESTAMP). `dateSoumission` (lecture seule) reprend la
> date/heure de soumission du **dossier rattaché** (`t_dossier.DATE_SOUMISSION`, posée à la saisie —
> `POST /api/saisies/ppm`) ; **`null`** pour un dossier antérieur à cette règle.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/receptions | — | `ReceptionDto[]` | 200 | Authentifié (filtré) |
| GET | /api/receptions?idDossier={n} | — | `ReceptionDto[]` | 200 | Authentifié (filtré) |
| GET | /api/receptions/dossier/{idDossier}/existe | — | `ReceptionExisteDto` | 200 | Authentifié (filtré) |
| GET | /api/receptions/{id} | — | `ReceptionDto` | 200, 403, 404 | Authentifié (filtré) |
| POST | /api/receptions | `ReceptionDto` | `ReceptionDto` | 201, 400, 403, 409 | SECRETAIRE (titulaire/délégué) |
| PUT | /api/receptions/{id} | `ReceptionDto` | `ReceptionDto` | 200, 400, 403, 404, 409 | SECRETAIRE (titulaire/délégué) |
| DELETE | /api/receptions/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idReception (number).

> **Ne charger que l'utile (anti sur‑fetch).** `?idDossier={n}` restreint la liste aux réceptions de
> ce dossier (filtre serveur, dans le périmètre). `…/dossier/{idDossier}/existe` → `{ "idDossier": n,
> "recu": true|false }` : test **léger** « déjà réceptionné ? » avant d'enregistrer une réception, sans
> charger l'historique. La PRMP (ressource interne) obtient liste vide / `recu=false`. **Pour la worklist
> du Secrétaire, utiliser `GET /api/dossiers/a-receptionner`** (et non un `…/existe` par dossier — ce
> serait un N+1).

**Exemple — requête**
```json
{ "idReception": 1543, "idDossier": 7720, "numPassage": 1, "typePassage": "INITIAL", "imCtrlRecept": "SECANT1", "dateReception": "2026-06-12", "observation": "Dossier reçu complet", "complet": true }
```

---

## Règles d'alerte
**Ressource** `/api/regle-alertes` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `RegleAlerteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idRegleAlerte | number | Oui (PK, au POST) | clé primaire |
| typeJalon | string | Oui | @NotBlank, max 30 |
| joursAvant | number | Oui | @NotNull |
| destinataireProfil | number | Non | |
| actif | boolean | Non | |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/regle-alertes | — | `RegleAlerteDto[]` | 200 | Authentifié |
| GET | /api/regle-alertes/{id} | — | `RegleAlerteDto` | 200, 404 | Authentifié |
| POST | /api/regle-alertes | `RegleAlerteDto` | `RegleAlerteDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/regle-alertes/{id} | `RegleAlerteDto` | `RegleAlerteDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/regle-alertes/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idRegleAlerte (number).

**Exemple — requête**
```json
{ "idRegleAlerte": 12, "typeJalon": "OUVERTURE", "joursAvant": 7, "destinataireProfil": 1, "actif": true }
```

---

## Règles d'anomalie
**Ressource** `/api/regle-anomalies` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

**Champs `RegleAnomalieDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idRegleAnomalie | number | Oui (PK, au POST) | clé primaire |
| codeRegle | string | Oui | @NotBlank, max 30 |
| libelle | string | Non | max 200 |
| parametreNum | number | Non | |
| parametreTxt | string | Non | max 200 |
| actif | boolean | Non | |
| graviteDefaut | string | Non | max 10 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/regle-anomalies | — | `RegleAnomalieDto[]` | 200 | Authentifié |
| GET | /api/regle-anomalies/{id} | — | `RegleAnomalieDto` | 200, 404 | Authentifié |
| POST | /api/regle-anomalies | `RegleAnomalieDto` | `RegleAnomalieDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/regle-anomalies/{id} | `RegleAnomalieDto` | `RegleAnomalieDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/regle-anomalies/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idRegleAnomalie (number).

**Exemple — requête**
```json
{ "idRegleAnomalie": 27, "codeRegle": "MONTANT_HORS_SEUIL", "libelle": "Montant supérieur au seuil", "parametreNum": 50000000.0, "parametreTxt": null, "actif": true, "graviteDefaut": "MAJEURE" }
```

---

## Services bénéficiaires
**Ressource** `/api/service-beneficiaires` — Lecture / écriture : tout utilisateur authentifié.

**Champs `ServiceBeneficiaireDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idBenef | number | Oui (PK, au POST) | clé primaire |
| ancMontBenef | number | Non | montant **par bénéficiaire** (ancien / initial) |
| nouvMontBenef | number | Non | montant **par bénéficiaire** (nouveau) |
| soaCode | string | Non | **max 25** — FK `tr_soa_beneficiaire` (ex. `00-21-0-J00-00000`) |
| numCompte | string | Non | **max 20** — FK `tr_compte` : **compte budgétaire du bénéficiaire** (compte et montant sont par bénéficiaire) |
| idDetail | number | Oui | @NotNull — FK `t_marche` |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/service-beneficiaires | — | `ServiceBeneficiaireDto[]` | 200 | Authentifié |
| GET | /api/service-beneficiaires/{id} | — | `ServiceBeneficiaireDto` | 200, 404 | Authentifié |
| POST | /api/service-beneficiaires | `ServiceBeneficiaireDto` | `ServiceBeneficiaireDto` | 201, 400 | Authentifié |
| PUT | /api/service-beneficiaires/{id} | `ServiceBeneficiaireDto` | `ServiceBeneficiaireDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/service-beneficiaires/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idBenef (number).

**Exemple — requête**
```json
{ "idBenef": 4501, "ancMontBenef": 120000.0, "nouvMontBenef": 135000.0, "soaCode": "00-21-0-J00-00000", "numCompte": "CPT-BENEF-01", "idDetail": 88 }
```

---

## Sessions utilisateur
**Ressource** `/api/session-utilisateurs` — Données de sécurité (§3.8) : réservé à `ADMINISTRATEUR` pour **toutes** les opérations (lecture comprise).

**Champs `SessionUtilisateurDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idSession | string | Oui (PK, au POST) | clé primaire, max 100 |
| imControleur | string | Non | max 7 |
| dateConnexion | string (date-time) | Non | |
| dateDeconnexion | string (date-time) | Non | |
| ipAdresse | string | Non | max 45 |
| userAgent | string | Non | max 300 |
| succes | boolean | Non | |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/session-utilisateurs | — | `SessionUtilisateurDto[]` | 200, 403 | ADMINISTRATEUR |
| GET | /api/session-utilisateurs/{id} | — | `SessionUtilisateurDto` | 200, 403, 404 | ADMINISTRATEUR |
| POST | /api/session-utilisateurs | `SessionUtilisateurDto` | `SessionUtilisateurDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/session-utilisateurs/{id} | `SessionUtilisateurDto` | `SessionUtilisateurDto` | 200, 400, 403, 404 | ADMINISTRATEUR |
| DELETE | /api/session-utilisateurs/{id} | — | — | 204, 403, 404 | ADMINISTRATEUR |

`{id}` = idSession (string).

**Exemple — requête**
```json
{ "idSession": "SESS-2026-0007", "imControleur": "CTRMEM", "dateConnexion": "2026-06-11T08:32:17", "ipAdresse": "192.168.1.42", "userAgent": "Mozilla/5.0", "succes": true }
```

---

## SOA bénéficiaires
**Ressource** `/api/soa-beneficiaires` — Lecture / écriture : tout utilisateur authentifié.

**Champs `SoaBeneficiaireDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| soaCode | string | Oui (PK, au POST) | clé primaire — **max 25** (ex. `00-21-0-J00-00000`) |
| libelle | string | Non | max 100 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/soa-beneficiaires | — | `SoaBeneficiaireDto[]` | 200 | Authentifié |
| GET | /api/soa-beneficiaires/{id} | — | `SoaBeneficiaireDto` | 200, 404 | Authentifié |
| POST | /api/soa-beneficiaires | `SoaBeneficiaireDto` | `SoaBeneficiaireDto` | 201, 400 | Authentifié |
| PUT | /api/soa-beneficiaires/{id} | `SoaBeneficiaireDto` | `SoaBeneficiaireDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/soa-beneficiaires/{id} | — | — | 204, 404 | Authentifié |

`{id}` = soaCode (string).

**Exemple — requête**
```json
{ "soaCode": "SOA-014", "libelle": "Service des opérations - Antananarivo" }
```

---

## Tranches
**Ressource** `/api/tranches` — Lecture / écriture : tout utilisateur authentifié.

**Champs `TrancheDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idTranche | number | Oui (PK, au POST) | clé primaire |
| lieuTrc | string | Non | max 100 |
| montTrc | number | Non | |
| idLot | number | Oui | @NotNull |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/tranches | — | `TrancheDto[]` | 200 | Authentifié |
| GET | /api/tranches/{id} | — | `TrancheDto` | 200, 404 | Authentifié |
| POST | /api/tranches | `TrancheDto` | `TrancheDto` | 201, 400 | Authentifié |
| PUT | /api/tranches/{id} | `TrancheDto` | `TrancheDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/tranches/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idTranche (number).

**Exemple — requête**
```json
{ "idTranche": 305, "lieuTrc": "Antananarivo - Analakely", "montTrc": 7500000.0, "idLot": 42 }
```

---

## Types de dossier (FAMILLES)
**Ressource** `/api/type-dossiers` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

> ⚠️ **Restructuration famille → sous-type (règle ajoutée, 2026-07-17).** `tr_type_dossier` porte désormais
> les **familles** de dossier — codes **renommés** : `PPM`→**`DDP`** « Dossier de Planification »,
> `DAO`→**`DMC`** « Dossier de Mise en Concurrence », `MAOO`→**`DDM`** « Dossier de Marché » (migration
> `2026-07-17_familles_sous_types.sql`, FK re-pointées sur `t_dossier`, `t_type_piece_jointe`,
> `tr_points_ctrl`). Les anciens codes deviennent des **sous-types** (référentiel ci-dessous). Les
> **pièces attendues** et **points de contrôle** restent rattachés à la **famille**. Les **nouvelles
> références** portent le segment **sous-type** (⚠️ révisé 2026-07-20 — ex. `00013/PPM/CRM-ANT/2026`,
> `00014/PPM-AGPM/CRM-ANT/2026`), mais la **numérotation reste indexée sur la famille** (continue : un PPM
> puis un PPM-AGPM se suivent `00013`/`00014`) ; les références déjà générées (dont l'ancien segment famille
> `…/DDP/…`) sont **conservées telles quelles** (identifiants immuables) ; la référence initiale PPM
> (`xxxxx/<acronyme>/PPM/<année>`) garde son segment `PPM` (elle nomme le document, pas la famille).
> NB : la famille `DMC` est distincte du référentiel `type-dmc` (types de **documents** DMC d'un marché : BC…).

**Champs `TypeDossierDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idTypeDossier | string | Oui (PK, au POST) | clé primaire, max 10 |
| libelleType | string | Non | max 100 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/type-dossiers | — | `TypeDossierDto[]` | 200 | Authentifié |
| GET | /api/type-dossiers/{id} | — | `TypeDossierDto` | 200, 404 | Authentifié |
| POST | /api/type-dossiers | `TypeDossierDto` | `TypeDossierDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/type-dossiers/{id} | `TypeDossierDto` | `TypeDossierDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/type-dossiers/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idTypeDossier (string).

**Exemple — requête**
```json
{ "idTypeDossier": "DMC", "libelleType": "Dossier de Mise en Concurrence" }
```

---

## Sous-types de dossier
**Ressource** `/api/sous-type-dossiers` (table `tr_sous_type_dossier`, ⚠️ règle ajoutée) — Référentiel
**administrable** (liste OUVERTE) : lecture ouverte, écritures `ADMINISTRATEUR`. Chaque sous-type est
rattaché à une **famille** (`idTypeDossier` = `DDP` / `DMC` / `DDM`). Jeu initial :
**DDP** ⊃ `PPM` « Plan de Passation de Marché », `PPM-AGPM` « Plan de Passation de Marché et Avis Général
de Passation de Marché » ; **DMC** ⊃ `DAO` « Dossier d'Appel d'Offres », `DAOR` « Dossier d'Appel d'Offres
Restreint » ; **DDM** ⊃ `MAOO` « Marché sur Appel d'Offres Ouvert », `MAOR` « Marché sur Appel d'Offres
Ouvert Restreint ».

> Le **dossier porte son sous-type** (`DossierDto.idSousType`), la famille s'en déduit. **Famille DDP** :
> sous-type **dérivé serveur** (`PPM-AGPM` ssi ≥1 marché en appel d'offres ouvert — même source de vérité
> que `agpmRequis` : `tr_mode_passation.DECLENCHE_AGPM`), recalculé à chaque écriture de marché et à la
> soumission ; toute valeur envoyée est ignorée. **Familles DMC/DDM** : sous-type **choisi à la saisie**
> (`POST /api/saisies/dossier`, dropdown rempli via `GET /par-famille/{famille}`).

**Champs `SousTypeDossierDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idSousType | string | Oui (PK, au POST) | clé primaire, max 20 (ex. `PPM-AGPM`) |
| libelleSousType | string | Non | max 150 |
| idTypeDossier | string | Oui | FK famille (`tr_type_dossier`) ; famille inconnue → 404 |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/sous-type-dossiers | — | `SousTypeDossierDto[]` | 200 | Authentifié |
| GET | /api/sous-type-dossiers/par-famille/{idTypeDossier} | — | `SousTypeDossierDto[]` | 200, 404 | Authentifié |
| GET | /api/sous-type-dossiers/{id} | — | `SousTypeDossierDto` | 200, 404 | Authentifié |
| POST | /api/sous-type-dossiers | `SousTypeDossierDto` | `SousTypeDossierDto` | 201, 400, 403, 404 | ADMINISTRATEUR |
| PUT | /api/sous-type-dossiers/{id} | `SousTypeDossierDto` | `SousTypeDossierDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/sous-type-dossiers/{id} | — | — | 204, 404, 409 | ADMINISTRATEUR |

`{id}` = idSousType (string). DELETE d'un sous-type référencé par un dossier → **409**.

**Exemple — requête**
```json
{ "idSousType": "DAOR", "libelleSousType": "Dossier d'Appel d'Offres Restreint", "idTypeDossier": "DMC" }
```

---

## Vérifications
**Ressource** `/api/verifications` — autorisation large à l'entrée, mais le **service exige strictement le profil `VERIFICATEUR`** (⚠️ règle ajoutée — **pas de délégation** CC/Président) ; DELETE : `ADMINISTRATEUR`. Écriture limitée à sa localité. Lecture filtrée par localité.

> ⚠️ **Identité & ID (règle ajoutée).** L'identité du vérificateur (`imCtrlVerif`) et la `dateVerif` sont **prises du JWT / serveur**, jamais du corps. L'`idVerification` est **auto-généré** (colonne IDENTITY) ; ne pas le fournir.

> **Préconditions de circuit (création/MAJ) → 403/409** : profil non `VERIFICATEUR` → **403** ; sinon le PV référencé (`idPv`) doit être **`SIGNE`** **et** d'avis **`FAVR`** (favorable avec réserves) **et** le dossier **non clos** → sinon **409**. La vérification est **itérative** sur le même dossier.

> ⚠️ **Circuit des observations FAVR — PÉRIMÈTRE FIGÉ (spec 2026-08-02).** Les observations transmises à
> la PRMP sont **exclusivement celles arrêtées dans le PV** (snapshot à la **signature** du PV FAVR :
> une observation trackée par ligne « Au lieu de / Lire » de point non conforme + une par pièce non
> conforme, libellés **figés** — `t_observation_pv` ; rattrapage paresseux au premier accès pour les
> dossiers FAVR signés avant la règle). **Aucun acteur ne peut élargir ce périmètre** à aucun stade :
> - `GET /api/observations-pv?dossier=` (vérificateur localité / PRMP propriétaire / tout-voyant) :
>   observations + **statut courant** (`EMISE` / `LEVEE` / `MAINTENUE`) + **historique par itération**
>   (`t_suivi_observation` : décision, précision, auteur, horodatage) + **`leveePossible`** (⚠️ décision
>   produit 2026-08-15 : `true` dès qu'une **resoumission** de la PRMP est intervenue depuis la
>   **signature du PV** — même valeur pour toutes les observations du dossier ; le front grise le
>   bouton « Levée » en miroir, sans heuristique sur l'historique).
> - `POST /api/observations-pv/passage` (**VERIFICATEUR**, dossier `EN_VERIFICATION`) :
>   `{ idDossier, decisions:[{ idObservationPv, decision: LEVEE|MAINTENUE, precision? }] }` — **chaque
>   observation restante doit être statuée** (400 sinon) ; **hors périmètre → 409** (aucune création) ;
>   **re-décision sur une LEVÉE → 409** (« levée = acquise », décision user 02/08) ; la précision
>   (facultative, MAINTENUE seulement) est un rappel de **ce qui manque**, jamais une exigence nouvelle.
>   ⚠️ **PAS de levée avant la première rectification de la PRMP (décision produit 2026-08-15)** : les
>   observations arrêtées au PV sont **réputées avec objet** (validées par toute la chaîne — examen,
>   acceptation, co-signature) ; tant qu'aucune **resoumission** (`POST /api/dossiers/{id}/resoumettre`,
>   action `RESOUMISSION` de `t_action_dossier`) n'est **postérieure à la signature du PV**, la décision
>   `LEVEE` → **409** « Levée impossible avant la première rectification de la PRMP… ». Le **premier
>   passage = émission du rappel** (tout `MAINTENUE`) ; après la première resoumission, levée/maintenue
>   libres à chaque passage (boucle inchangée jusqu'à tout levé).
>   Le passage `t_verification` est **créé par le serveur** (observation = rappel auto-généré des
>   maintenues, `obsLevees` dérivé) puis la transition [Auto] s'applique : toutes levées →
>   `OBSERVATIONS_LEVEES` (cap SIGMP) ; sinon → `EN_ATTENTE_DECISION_PRMP` + notification PRMP
>   (`OBSERVATION_VERIFICATION`) au contenu **auto-généré** (les maintenues + précisions, rien d'autre).
> - **Saisie libre interdite** : dès que le périmètre existe, `POST`/`PUT /api/verifications` avec un
>   texte d'observation client → **409** (rejet backend, pas seulement masquage UI). Le premier envoi à
>   la PRMP (PV signé) reprend automatiquement les observations du PV — aucun champ de rédaction.
> - Front : écran Vérificateur = liste des observations (radio Levée / Maintenue + précision), plus
>   aucun champ libre ; écran « Rectifier » PRMP = panneau lecture seule (statuts + précisions).
> - Purge retrait : `t_suivi_observation` puis `t_observation_pv` en tête de cascade.

> **Effet `[Auto]`** (sur un dossier `EN_VERIFICATION`) : ⚠️ **règle MODIFIÉE (2026-08-02, spec navette)** — `obsLevees = true` → dossier **`OBSERVATIONS_LEVEES`** (la clôture n'est PLUS posée ici : le vérificateur doit transmettre l'approbation + la levée à SIGMP via `POST /api/sigmp-transmissions`, puis l'Assistant archive le PV, ce qui clôt). `obsLevees = false` → dossier **`EN_ATTENTE_DECISION_PRMP`** : l'observation est **transmise à la PRMP** du dossier (notification `OBSERVATION_VERIFICATION` : référence dossier, vérificateur, texte de l'observation, date) et l'événement est **tracé** dans `t_audit_log`. Le vérificateur ne peut plus modifier ni soumettre de vérification tant que la PRMP n'a pas statué (nouvelle tentative → **409**) ; il voit le dossier en lecture seule dans `GET /api/dossiers/en-attente-prmp`. La PRMP le retrouve via `GET /api/dossiers?statut=EN_ATTENTE_DECISION_PRMP` et lit l'observation complète dans sa notification.

## Transmissions SIGMP (spec navette 2026-08-02)
**Ressource** `/api/sigmp-transmissions` (table `t_transmission_sigmp`) — ⚠️ le **VÉRIFICATEUR** transmet le
**sens de la décision de la Commission** vers **SIGMP** (interop PRS 2.0 ↔ SIGMP). En l'absence de contrat
d'API SIGMP réel, la transmission est **enregistrée côté PRS** (`STATUT_ENVOI = ENREGISTREE`) — aucun endpoint
tiers inventé ; l'envoi réel sera branché plus tard.

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/sigmp-transmissions[?dossier=] | — | `TransmissionSigmpDto[]` | 200 | Authentifié |
| POST | /api/sigmp-transmissions | `{ idDossier }` | `TransmissionSigmpDto` | 201, 400, 403, 404, 409 | **VERIFICATEUR** (localité du dossier) |

> Le **sens est dérivé serveur** de l'avis du PV **signé** du dossier : **cas 1** (dossier `EN_VERIFICATION`,
> avis ≠ FAVR) — `FAV` → `APPROUVE`, `DEF`/`NSP` → `NON_APPROUVE` ; **cas 2** (dossier `OBSERVATIONS_LEVEES`,
> fin de boucle FAVR) — `APPROUVE` + `leveeObservations = true`. Autre statut/avis → **409**. Effets : dossier →
> **`DECISION_TRANSMISE_SIGMP`** + notification **`PV_A_ARCHIVER`** aux Assistants contrôleurs de la localité.

> ⚠️ **Branchement post-signature MODIFIÉ (2026-08-02).** À la signature du PV, **TOUS les avis** passent par le
> vérificateur : dossier → `EN_VERIFICATION` (plus de clôture directe pour FAV/DEF/NSP). Notifications : PRMP
> (`PV_SIGNE`), vérificateurs (`PV_A_VERIFIER` si FAVR, sinon `DECISION_A_TRANSMETTRE`). La copie assistant
> (`PV_DEFINITIF_COPIE`) est remplacée par `PV_A_ARCHIVER` (à la transmission SIGMP).

> ⚠️ **Archivage (2026-08-02).** `POST /api/pv-examens/{id}/archiver` (**ASSISTANT_CONTROLEUR**, localité) :
> PV `SIGNE` + dossier `DECISION_TRANSMISE_SIGMP` → pose `DATE_ARCHIVAGE`/`IM_ARCHIVEUR`, **clôt** le dossier
> (`CLOTURE`) et émet `CLOTURE_ELIGIBLE`. `POST /api/lettre-renvois/{id}/archiver` (idem) archive une lettre
> **SIGNE** (le dossier n'est pas modifié). Statuts dossier ajoutés : `OBSERVATIONS_LEVEES`,
> `DECISION_TRANSMISE_SIGMP`, `EN_ATTENTE_PIECES`, `A_REEXAMINER` (réexamen après lettre de renvoi).

> ⚠️ **Cas 3 — lettre de renvoi (2026-08-02, RÉEXAMEN ajouté).** À la **signature** de la lettre, le dossier
> `EXAMINE` (ou `A_REEXAMINER`, nouvelle lettre pendant un réexamen) passe **`EN_ATTENTE_PIECES`** (examen
> suspendu, non modifiable par les Membres — remplace l'ancien retour PRET_DISPATCH), et un projet de PV
> resté `PROJET_SOUMIS` repasse **`EN_RECTIFICATION`** (la lettre vaut retour de navette : le Membre pourra
> re-soumettre après réexamen). La PRMP dépose les pièces demandées (`apresLettreRenvoi=true`) puis appelle
> `POST /api/dossiers/{id}/transmettre-complements` (**PRMP propriétaire**, 409 hors `EN_ATTENTE_PIECES` ;
> **409 aussi tant qu'aucune pièce n'est rattachée à la lettre du cycle courant** — le réexamen n'a lieu
> qu'une fois les pièces nécessaires présentes) : dossier → **`A_REEXAMINER`** — retour dans la **file
> « à examiner » du Membre attributaire** (`GET /api/dossiers/a-examiner` = `DISPATCHE` + `A_REEXAMINER`,
> compteurs idem ; verrous d'examen rouverts à ce statut) ; notification `COMPLEMENTS_TRANSMIS`. Le Membre
> **réexamine** à la lumière des pièces reçues (reprise sur les pièces non statuées, examen/PV conservés)
> puis **re-soumet le projet de PV** (`POST /api/pv-examens/{id}/soumettre`) : le dossier repasse
> **`EXAMINE`** (même transaction) et la navette reprend son circuit normal (acceptation P/CC → signature).

**Champs `VerificationDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idVerification | number | Non (auto-généré) | ID serveur (IDENTITY) ; ignoré en entrée |
| idReception | number | Oui | @NotNull |
| idPv | number | Oui | @NotNull — PV `SIGNE` d'avis `FAVR` |
| imCtrlVerif | string | Non | max 7 — **ignoré** : identité = JWT (`CurrentUser.ref`) |
| dateVerif | string (date) | Non | **ignoré** : posée côté serveur (date du jour) |
| observation | string | Non | max 500 |
| obsLevees | boolean | Non | `true` → `CLOTURE` ; `false` → `EN_ATTENTE_DECISION_PRMP` + notif PRMP `OBSERVATION_VERIFICATION` + trace audit (si dossier `EN_VERIFICATION`) |
| motifRectif | string | — (sortie) | max 255 — motif de rectification PRMP, posé serveur à la resoumission ; **lecture seule** (visible côté vérificateur) |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/verifications | — | `VerificationDto[]` | 200 | Authentifié (filtré) |
| GET | /api/verifications/{id} | — | `VerificationDto` | 200, 404 | Authentifié (filtré) |
| POST | /api/verifications | `VerificationDto` | `VerificationDto` | 201, 400, 403, 409 | VERIFICATEUR strict (service) |
| PUT | /api/verifications/{id} | `VerificationDto` | `VerificationDto` | 200, 400, 403, 404, 409 | VERIFICATEUR strict (service) |
| DELETE | /api/verifications/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idVerification (number).

**Exemple — requête**
```json
{ "idVerification": 9012, "idReception": 5500, "idPv": 7321, "imCtrlVerif": "VERANT1", "dateVerif": "2026-06-11", "observation": "Observations levées", "obsLevees": true }
```
