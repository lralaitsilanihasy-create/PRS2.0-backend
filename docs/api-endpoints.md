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
- Toutes les routes nécessitent un **jeton JWT**, **sauf** `POST /api/auth/login` et
  `POST /api/auth/register/prmp` (seules routes publiques).
- Obtenir le jeton via `POST /api/auth/login`, puis l'envoyer sur chaque requête :
  `Authorization: Bearer <token>`.
- Absence / invalidité du jeton → **401**. Rôle insuffisant → **403**.
- Dans les tableaux ci-dessous, **« Authentifié »** (ou « Ouvert ») = tout utilisateur connecté
  (un JWT valide suffit) ; ce n'est **pas** public.

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
  `aviss`, `cat-comptes`, `comptes`, `delegation-profils`, `entite-contracts`, `localites`,
  `ministeres`, `mode-passations`, `natures`, `points-ctrls`, `profiles`, `regle-alertes`,
  `regle-anomalies`, `regle-passations`, `seuils`, `situations`, `type-dossiers`.
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
| **Violation de contrainte BD** (`DataIntegrityViolationException`) | identifiant en **doublon**, valeur obligatoire manquante (NOT NULL) ou **clé étrangère** inexistante | POST avec un id déjà utilisé, ou référençant une entité inexistante |

> Rappel : **401** (non authentifié : JWT absent/invalide ou compte désactivé) et **404** (ressource introuvable) restent distincts des trois ci-dessus.

### Types
`Integer`/`Long` → `number` ; `String` → `string` ; `Boolean` → `boolean` ; `BigDecimal` → `number` ;
`LocalDate` → `string` `"yyyy-MM-dd"` ; `LocalDateTime` → `string` ISO `"2026-06-12T10:30:00"`.

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
**Ressource** `/api/auth` — Routes **publiques** (aucun token requis). Pas de CRUD.

**Champs `LoginRequest`** (corps de `/login`)

| Champ (JSON) | Type | Obligatoire |
|---|---|---|
| login | string | Oui (@NotBlank) |
| motDePasse | string | Oui (@NotBlank) |

**Champs `LoginResponse`** (réponse de `/login`)

| Champ (JSON) | Type | Description |
|---|---|---|
| token | string | JWT à placer dans `Authorization: Bearer ...` |
| login | string | login authentifié |
| role | string | profil métier (ou `null` si non reconnu) |
| typeActeur | string | `CONTROLEUR` ou `PRMP` |
| ref | string | matricule contrôleur ou identifiant PRMP |
| localite | string | localité de rattachement (`null` = toutes, cas Président) |
| expiresIn | number | durée de validité du jeton (secondes) |

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
| POST | /api/auth/login | `LoginRequest` | `LoginResponse` | 200, 400, 401 | PUBLIC |
| POST | /api/auth/register/prmp | **`multipart/form-data`** (v2, ci-dessous) ou `RegisterPrmpRequest` (JSON, historique) | `RegisterResponse` | 201, 400, 409 | PUBLIC |

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

**Exemple — login (requête / réponse)**
```json
{ "login": "CTRMEM", "motDePasse": "Test@1234" }
```
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...", "login": "CTRMEM", "role": "MEMBRE",
  "typeActeur": "CONTROLEUR", "ref": "CTRMEM", "localite": "ANT", "expiresIn": 28800
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

## Inscriptions PRMP (validation Administrateur)
**Ressource** `/api/inscriptions` — Instruction des inscriptions PRMP (§3.1). Consultation et écriture réservées à l'**Administrateur** ; le **téléchargement d'une pièce** est ouvert à l'Administrateur **ou** au propriétaire de l'inscription.

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
| GET | /api/controleurs/{id}/pieces/{type} | — ; `type` = `PHOTO` | fichier (binaire) | 200, 400, 403, 404 | ADMINISTRATEUR |
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

**Champs `DelegationProfilDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idDelegation | number | Oui (PK, au POST) | clé primaire |
| idProfileDelegant | number | Oui | @NotNull — profil qui exerce |
| idProfileDelegue | number | Oui | @NotNull — profil dont la tâche est exercée |
| actif | boolean | Oui | @NotNull |

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

> ⚠️ **Identité & ID (règle ajoutée).** À la création : `idPrmp` = **utilisateur authentifié** (JWT, corps ignoré), `dateDemande` serveur, `statut` forcé `EN_ATTENTE`, `idDemandeRetrait` **auto-généré** (IDENTITY). Gardes (sinon **403/409**) : PRMP **propriétaire** du dossier ; dossier **`SOUMIS`/`PRET_DISPATCH`** ; pas de demande déjà **`EN_ATTENTE`**. Liste déroulante des dossiers éligibles : **`GET /api/dossiers/retirables`** (PRMP).

> ⚠️ **Décision (règle ajoutée).** `POST /{id}/accepter` → statut `ACCEPTEE` + **dossier `BROUILLON`**, avec sa **référence de réception invalidée** : `refeDossier` est **restauré à la référence initiale du dossier** (celle générée à la création, stockée dans `t_ppm.REFERENCE`, ex. `00003/DGB/PPM/2026`) — la référence de réception (ex. `00002/PPM/CRM-ANT/2026`) est ainsi remplacée. `GET /api/dossiers` (« Mes brouillons ») réaffiche donc la référence d'origine, et le dossier **redevient entièrement modifiable** (métadonnées, lignes de marché, pièces). *(Dossier sans PPM → `refeDossier` remis à `null`.)* ⚠️ **La/les réception(s) résiduelle(s) du dossier sont supprimées** (`t_reception`) : après `POST /api/dossiers/{id}/soumettre`, le dossier redevient **`SOUMIS` sans réception** et **réapparaît dans `GET /api/dossiers/a-receptionner`** (re-réception en `INITIAL`, passage 1, avec une **nouvelle** référence de réception). Un dossier retirable (`SOUMIS`/`PRET_DISPATCH`) n'est jamais dispatché → aucune dépendance (dispatch/examen) sur ces réceptions. `POST /{id}/refuser` (corps `{ "motif"? }`) → `REFUSEE`, dossier **inchangé**. Le décideur réel (CC **ou** Président) est enregistré dans `IM_CTRL_CC` depuis le **JWT**. Hors CC-localité/Président → **403** ; demande déjà traitée → **409**. Notifs PRMP : `RETRAIT_ACCEPTE` / `RETRAIT_REFUSE`.

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

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/demande-retraits | — | `DemandeRetraitDto[]` | 200 | Authentifié (filtré) — worklist PRMP |
| GET | /api/demande-retraits/mes-demandes | — | `DemandeRetraitDto[]` | 200, 403 | **PRMP** — ses demandes ; **marque l'écran consulté** (voir ci-dessous) |
| GET | /api/demande-retraits/a-valider | — | `DemandeRetraitDto[]` | 200, 403 | CHEF_COMMISSION (localité) / PRESIDENT |
| GET | /api/demande-retraits/historique | — | `DemandeRetraitDto[]` | 200, 403 | CHEF_COMMISSION (localité) / PRESIDENT |
| GET | /api/demande-retraits/{id} | — | `DemandeRetraitDto` | 200, 403, 404 | Authentifié (filtré) |
| POST | /api/demande-retraits | `DemandeRetraitDto` | `DemandeRetraitDto` | 201, 400, 403, 409 | PRMP |
| POST | /api/demande-retraits/{id}/accepter | — | `DemandeRetraitDto` | 200, 403, 404, 409 | CHEF_COMMISSION / PRESIDENT |
| POST | /api/demande-retraits/{id}/refuser | `{ motif? }` | `DemandeRetraitDto` | 200, 403, 404, 409 | CHEF_COMMISSION / PRESIDENT |
| DELETE | /api/demande-retraits/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idDemandeRetrait (number). Le `PUT /{id}` générique est **supprimé** au profit de `accepter`/`refuser`.

**Exemple — requête (création, PRMP)**
```json
{ "idDossier": 1023, "motifRetrait": "Dossier incomplet, pièces manquantes" }
```
*(le reste — `idPrmp`, `dateDemande`, `statut`, `idDemandeRetrait` — est dérivé/serveur, ignoré en entrée)*

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
| idPtControle | number | Oui | @NotNull |
| conforme | boolean | Oui | @NotNull |
| observations | `ObservationControleDto[]` | Non | lignes « AU LIEU DE / LIRE » (cf. *Observations de contrôle*) ; **`[]` si conforme**, **N lignes si non conforme** (sinon **400**, champ `observations`) ; persistées par le service (remplacement à l'enregistrement) |
| obsSiNonConforme | string | Non | max 500 |

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

> **Règle `interimDispatch`** (sinon **409**) : Président → `false` ; CC dans sa localité → `false` ; CC hors de sa localité → `true` obligatoire.

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
| idTypeDossier | string | Non | max 10 |
| idDossierParent | number | Non | |
| refeDossier | string | Non | max 100 — **référence officielle, générée à la `…/réception`** ; **`null` avant** (BROUILLON/SOUMIS) ; laisser vide à la création |
| dateRef | string (date) | Non | renseignée à la soumission si vide |
| statut | string | Non | max 30 — cycle : `BROUILLON` → `SOUMIS` → `PRET_DISPATCH` → `DISPATCHE` → `EXAMINE` → `PV_SIGNE` → (`EN_VERIFICATION` si avis FAVR) → `CLOTURE` ; vérif. obs. non levées → `EN_ATTENTE_DECISION_PRMP` (ou `RETIRE`) ; posé par le système, **lecture seule** côté PRMP |
| idLocalite | string | Non | max 5 — localité (FK `tr_localite`) ; **dérivée de l'entité** du dossier (lecture seule à la saisie) |
| idPrmp | string | Non | max 10 — PRMP **propriétaire** (FK `t_prmp`) ; posée à la saisie ; seule elle édite/soumet |
| idEntiteContract | number | Non | entité contractante (FK `tr_entite_contract`) ; **choisie à la saisie**, fixe la localité |

> **Cycle de vie & saisie.** On **ne crée pas** un dossier brut : la **façade `/api/saisies`** (réservée PRMP)
> crée le dossier (statut **`BROUILLON`**) et son contenu. Un brouillon est **invisible des contrôleurs** ;
> il le devient (`SOUMIS`) via `…/soumettre`. Les endpoints bruts `POST`/`PUT /api/dossiers` sont **réservés
> `ADMINISTRATEUR`** (cf. *Saisies*).

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/dossiers | — | `DossierDto[]` | 200, 400 | Authentifié (filtré, hors BROUILLON) |
| GET | /api/dossiers/a-receptionner | — | `DossierDto[]` | 200, 403 | `SECRETAIRE` (titulaire/délégué) ou `ADMINISTRATEUR` |
| GET | /api/dossiers/a-examiner | — | `DossierDto[]` | 200, 403 | `MEMBRE` (titulaire/délégué) ou `ADMINISTRATEUR` |
| GET | /api/dossiers/examines | — | `Page<DossierDto>` | 200, 403 | `MEMBRE` (titulaire/délégué) ou `ADMINISTRATEUR` |
| GET | /api/dossiers/a-verifier | — | `DossierDto[]` | 200, 403 | `VERIFICATEUR` (titulaire/délégué) ou `ADMINISTRATEUR` — EN_VERIFICATION + EN_ATTENTE_DECISION_PRMP |
| GET | /api/dossiers/verifies | — | `Page<DossierDto>` | 200, 403 | `VERIFICATEUR` (titulaire/délégué) ou `ADMINISTRATEUR` |
| GET | /api/dossiers/en-attente-prmp | — | `DossierDto[]` | 200, 403 | `VERIFICATEUR` (titulaire/délégué) ou `ADMINISTRATEUR` — lecture seule |
| GET | /api/dossiers/{id} | — | `DossierDto` | 200, 403, 404 | Authentifié (filtré) |
| GET | /api/dossiers/{id}/ppm | — | `PpmDto` | 200, 403, 404 | Authentifié (propriétaire pour un BROUILLON) |
| POST | /api/dossiers | `DossierDto` | `DossierDto` | 201, 400, 403 | **ADMINISTRATEUR** |
| PUT | /api/dossiers/{id} | `DossierDto` | `DossierDto` | 200, 400, 403, 404 | **ADMINISTRATEUR** |
| DELETE | /api/dossiers/{id} | — | — | 204, 403, 404, 409 | **PRMP** propriétaire — BROUILLON (cascade contenu + historique) |
| POST | /api/dossiers/{id}/soumettre | — | `DossierDto` | 200, 400, 403, 404, 409 | **PRMP** |
| POST | /api/dossiers/{id}/resoumettre | `DossierResoumissionRequest` | `DossierDto` | 200, 400, 403, 404, 409 | **PRMP** propriétaire |
| GET | /api/dossiers/{id}/historique-echanges | — | `EchangeDto[]` | 200, 403, 404 | **PRMP** / **VERIFICATEUR** (titulaire/délégué) / **ADMINISTRATEUR** |

`{id}` = idDossier (number). **`DossierResoumissionRequest`** = `{ motifRectification }` (String, **@NotBlank**, max 255).

> 📌 **Résolution `idDossier → PPM` (règle ajoutée).** `GET /api/dossiers/{id}/ppm` renvoie le **`PpmDto`
> complet** du dossier, **y compris pour un `BROUILLON`** lu par son **propriétaire** (même critère de
> visibilité que `GET /api/ppms/{id}`, non filtré par statut). Résout le besoin front d'ouvrir un brouillon
> depuis « Mes brouillons » — dont le cas d'un **brouillon PPM sans aucun marché** (où `GET /api/marches` ne
> peut fournir aucun `idPpm`). Aucun PPM rattaché → **404** ; hors périmètre → **403**. *(Depuis le retrait des
> BROUILLON de `GET /api/ppms`, cf. §1/§3.1, c'est la voie recommandée pour obtenir le PPM d'un brouillon.)*

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
> (pas encore examinés) ; `GET /api/dossiers/examines` = **historique** de ce qu'il a examiné
> (**`EXAMINE` + `PV_SIGNE` + `CLOTURE`**), **paginé** (`?page=&size=&sort=`, réponse `Page` :
> `content[]`, `totalElements`, …). Les deux sont **scopées au Membre courant** (`Dispatch.imCtrlMembre`)
> et **exclusives** : à la création de l'examen, un dossier quitte « à examiner » pour « examinés ». Un
> Membre ne voit que **ses** dossiers (ceux d'un autre Membre n'y figurent pas).

> ⚠️ **Files du Vérificateur (§3.6, règle ajoutée).** `GET /api/dossiers/a-verifier` = dossiers **encore
> actifs** côté vérification : **`EN_VERIFICATION`** (à vérifier) **OU** **`EN_ATTENTE_DECISION_PRMP`** (en
> lecture seule — le dossier **ne disparaît pas** de la liste tant qu'il n'est pas clôturé ; toute
> vérification est refusée **409** tant que la PRMP n'a pas statué, cf. badge « En attente PRMP » côté UI).
> `GET /api/dossiers/verifies` = **historique** paginé, **lecture seule**, des dossiers **`CLOTURE` ayant un
> PV `SIGNE`** — **y compris les auto-clôturés** à la signature (`FAV`/`DEF`/`NSP`). Les deux sont **scopées
> à la localité** du vérificateur (contrôleur réceptionnaire). Seul **`CLOTURE`** quitte « à vérifier » (→ `/verifies`).

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

**Règle `apresLettreRenvoi`** : si `idLettre` est fourni **et** le dossier est `SOUMIS`/`PRET_DISPATCH`, la pièce
est enregistrée `apresLettreRenvoi=true` (avec `idLettre`) ; sinon c'est une **pièce initiale** (`false`).

> ⚠️ **Ré-ouverture de l'examen après lettre de renvoi (règle ajoutée).** Au **premier** dépôt
> `apresLettreRenvoi=true` (dossier `PRET_DISPATCH`, cf. signature de la lettre ci-dessous), le serveur **réutilise
> le dispatch existant** (le Membre y est déjà désigné) et fait avancer le dossier **`PRET_DISPATCH → DISPATCHE`**
> — **pas de nouveau dispatch** (aucun doublon). Le dossier **réapparaît alors dans `GET /api/dossiers/a-examiner`**
> du Membre attributaire, qui peut ré-examiner. Une **unique** notification **`PIECE_AJOUTEE_APRES_RENVOI`**
> (`typeObjet=DOSSIER`, `idObjet=idDossier`) est émise vers ce Membre. Les **dépôts suivants** trouvent le dossier
> déjà `DISPATCHE` (donc `apresLettreRenvoi=false`) : **ni ré-avance, ni notification en double** (regroupement
> naturel).

> **Pièces obligatoires à la soumission.** `POST /api/dossiers/{id}/soumettre` vérifie que toutes les pièces
> `obligatoire` du type de dossier (référentiel ci-dessus) sont présentes. Sinon **400** :
> `{ "erreurs": [ { "champ": "piecesJointes", "message": "La pièce '<libellé>' est obligatoire." } ] }`.

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
(ou PRMP inconnue), pas de 404 (filtre). `GET /par-localite/{idLocalite}` liste les UGPM d'une localité **via la
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
| GET | /api/ugpms/par-tutelle/{idPrmp} | — | `UgpmDto[]` | 200, 403 | **ADMINISTRATEUR** |
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

**DELETE** supprime l'UGPM **et son compte d'authentification** (créés ensemble) ; **404** si `idUgpm` inconnu.
Les dossiers créés par l'UGPM **restent** la propriété de sa PRMP de tutelle (`CREE_PAR` est une trace, pas une FK).

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
| PUT | /api/saisies/ppm/{idDossier} | `EditionPpmRequest` | `DossierDto` | 200, 400, 403, 404, 409 | **PRMP** |

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
> `{ designationMarche, montEstim, nouvMontEstim, idNature+natureLibelle, idMode+modeLibelle, financement,`
> `beneficiaires[]` `{ soaCode, numCompte, ancMontBenef, nouvMontBenef }, previsions[]` `{ processus, dateDebut } },`
> `avertissements[] }`. **Read-only** : les référentiels manquants (`idNature`/`idMode`/`numCompte`/`soaCode`,
> entité) **ne sont pas créés** — renvoyés en libellé seul + listés dans `avertissements` ; la
> création-à-la-volée se fait au `POST /api/saisies/ppm`. PDF illisible / non-PDF / sans texte → **400** (message
> clair, pas de données partielles silencieuses).
>
> **Parsing du tableau — sémantique par enregistrement** (calibré sur `PPM_26-…` **et** `PPM_26-488-…` MIDSP).
> L'extraction **démarre à la 1ʳᵉ `NATURE` connue** (l'en-tête des colonnes, **même éclaté sur 30+ lignes** —
> `MONTANT`/`ESTIMATIF`/`INITIAL` etc. sur des lignes séparées — est ainsi ignoré) et se termine à la **dernière**
> « **Fait à … le …** » (ou « La personne responsable »). Chaque **enregistrement** (délimité par une `NATURE`) est
> **recomposé** (lignes jointes) puis lu **par position** : `NATURE` → `OBJET` (avant le 1ᵉʳ montant) →
> `montEstim [nouvMontEstim]` → `mode` (**multi-mots/multi-lignes**, ex. « Consultation de prix ouverte ») +
> `financement` (dernier mot avant le 1ᵉʳ SOA, ex. `RPI`/`PIP`) → **codes SOA** → `compte` → **montants
> bénéficiaires** → **3 prévisions** `LANCEMENT`/`OUVERTURE`/`ATTRIBUTION` (`dd/MM/yyyy` → ISO).
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

**`SaisieMarcheLigne`** : `designationMarche`, `numCompte`, `montEstim`, **`nouvMontEstim`** (→ `t_marche.NOUV_MONT_ESTIM`), `financement`, `statut`, `idNature`, `natureLibelle`, `idMode`, `modeLibelle`, **`beneficiaires[]`**. `idDetail` est **facultatif** — **null à la création** (PK serveur), renseigné seulement pour **identifier une ligne existante** lors de l'édition (réconciliation). `idDossier`/`idPpm` sont renseignés par le service. **`idMode`** = mode **saisi** (facultatif) ; **conservé tel quel** (plus de détermination automatique — `t_situation`/`t_regle_passation`/`t_seuil` retirés). **`nouvMontEstim`** et **`beneficiaires[]`** sont **optionnels** (rétro-compatible).

> ⚠️ **Nature / mode / compte — résolution-ou-création à la volée (règle ajoutée).** Pour l'**import PPM** :
> si `idNature` (resp. `idMode`) est **absent** mais `natureLibelle` (resp. `modeLibelle`) est fourni, le service
> **résout** le référentiel par **libellé normalisé** (trim + casse + accents) dans `tr_nature` (resp. `tr_mode_passation`),
> ou le **crée à la volée** (PK = `max+1`) s'il n'existe pas — dé-doublonnage sur le libellé normalisé. De même, **`numCompte`**
> (compte du marché) est **résolu-ou-créé** dans `tr_compte` (PK = le numéro lui-même) pour éviter la violation FK
> `t_marche.NUM_COMPTE`. **Résolution = réutilisation de l'existant, jamais suppression/recréation.** Créations
> **tracées** dans `t_audit_log` (`TYPE_ACTION=CREATION_A_LA_VOLEE`). Si l'`id*` est **présent**, le libellé associé est **ignoré**.
> **Bénéficiaires par marché (règle ajoutée).** `beneficiaires[]` (optionnel) = une ligne **`t_service_beneficiaire`**
> par élément `{ soaCode, numCompte, ancMontBenef, nouvMontBenef }`. `soaCode` est **résolu-ou-créé** dans
> `tr_soa_beneficiaire` (PK = `soaCode`, audit `CREATION_A_LA_VOLEE`), `numCompte` dans `tr_compte` — même logique
> (réutilisation, jamais suppression). **Cohérence des montants** (⚠️ **uniquement si `beneficiaires[]` non vide**,
> **égalité exacte** — Ariary entiers, pas de tolérance) : `Σ ancMontBenef = montEstim` ; et si `nouvMontEstim` est
> **fourni**, chaque bénéficiaire doit porter `nouvMontBenef` et `Σ nouvMontBenef = nouvMontEstim`. Écart → **400**
> ciblé : `{ "erreurs": [ { "champ": "marches[i].beneficiaires", "message": "La somme des montants par bénéficiaire
> (…) doit égaler le montant estimatif du marché (…)." } ] }`. `beneficiaires[]` absent/vide → **aucune vérification**.

⚠️ **`processus`** : `ProcessusMarche[]` — **chaque marché doit comporter au moins un processus à la création** (`POST /api/saisies/ppm`), sinon **400** `{ "erreurs": [ { "champ": "marches[0].processus", "message": "Au moins un processus est obligatoire." } ] }`. Chaque **`ProcessusMarche`** = `idCapm` (FK `t_capm`, `@NotNull`), `dateDebut` et `dateFin` (`yyyy-MM-dd`, `@NotNull`) — un champ manquant → **400** au chemin `marches[i].processus[j].<champ>` (« Le processus est obligatoire. » / « La date de début est obligatoire. » / « La date de fin est obligatoire. ») ; `idCapm` **inconnu** → **400**. Le service crée **une ligne `t_marche_prevision` par processus**. *(À l'édition d'un brouillon, `processus` n'est pas exigé.)*

⚠️ **Cohérence chronologique des processus** (par marché, processus triés par `t_capm.ordre` ASC) — validée à la **création** (`POST /api/saisies/ppm`) et à l'**édition** (`POST`/`PUT /api/marche-previsions`) :
> 1. **Interne** : `dateDebut < dateFin` pour chaque processus, sinon **400** (champ `…dateFin` — « La date de fin doit être postérieure à la date de début. »).
> 2. **Séquence** : `dateDebut[n] >= dateFin[n-1]` entre processus consécutifs, sinon **400** (champ `…dateDebut` — « La date de début du processus *[libellé n]* doit être postérieure ou égale à la date de fin du processus précédent *[libellé n-1]*. »).
>
> À la saisie, le champ porte le chemin `marches[i].processus[j].<champ>` ; à l'édition d'une prévision, le nom du champ seul (`dateDebut`/`dateFin`).

**`SaisieDossierRequest`** (DAO/MAOO, sans contenu) : `idTypeDossier` (oui, ≠ `PPM` sinon **409**), **`idEntiteContract` (oui)**. *(plus de `idDossier` : attribué par le serveur.)*

**`EditionPpmRequest`** (`PUT /api/saisies/ppm/{idDossier}`) — édite un **brouillon** PPM en une transaction :
`exercice`, `signataire`, `dateSignature`, `reference` (en-tête, tous obligatoires) + `marches` (liste désirée). Les lignes sont **réconciliées par `idDetail`** : ajout des nouvelles, mise à jour des existantes (mode **recalculé**), **retrait** des absentes. La localité/le type/le propriétaire/l'entité ne changent pas. Dossier non BROUILLON → **409** ; non-propriétaire → **403**.

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
| categorieEntite | string | Non | max 20 |
| idOrganigramme | number | Oui | @NotNull |
| idEntiteParent | number | Non | |
| niveauHierarchique | number | Non | |
| idLocalite | string | Non | max 5 — **localité de l'entité** (FK `tr_localite`) ; détermine la localité des dossiers la concernant |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/entite-contracts | — | `EntiteContractDto[]` | 200 | Authentifié |
| GET | /api/entite-contracts/{id} | — | `EntiteContractDto` | 200, 404 | Authentifié |
| POST | /api/entite-contracts | `EntiteContractDto` | `EntiteContractDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/entite-contracts/{id} | `EntiteContractDto` | `EntiteContractDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/entite-contracts/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idEntiteContract (number).

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
  toute tentative d'affecter une entité déjà rattachée → **409**. Une PRMP peut gérer **plusieurs**
  entités. Les affectations sont **stables** (pas de transfert d'une PRMP à une autre).

**Champs `PrmpEntiteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPrmpEntite | number | Non (PK **générée côté serveur**) | clé primaire |
| idPrmp | string | Oui | @NotBlank, max 10 ; la PRMP doit exister (sinon 400) |
| idEntiteContract | number | Oui | @NotNull ; l'entité doit exister (sinon 400) |
| dateAffectation | string (date) | Non | défaut = date du jour |
| actif | boolean | Oui | @NotNull ; une création est toujours active |

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
> **Transition** : à la création de l'examen, le dossier passe **`DISPATCHE` → `EXAMINE`** (même transaction) — il **quitte « à examiner »**.
>
> **Verrou (édition) → 409** : `PUT /api/examens/{id}` **et** les écritures sur `/api/examen-details` (création/MAJ/suppression) sont **refusées dès `PV_SIGNE`** : l'examen est modifiable tant que le dossier est `EXAMINE` (navette ouverte), **définitif** après signature du PV.

**Champs `ExamenDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idExamen | number | Oui (PK, au POST) | clé primaire |
| idDispatch | number | Oui | @NotNull |
| imCtrlMembre | string | Non | max 7 |
| dateExamen | string (date) | Non | |

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

> ⚠️ **Soumission de l'examen (règle ajoutée).** `POST /api/examens/{id}/soumettre` produit **toujours un
> Projet de PV** (`PvExamenService`, `idPv` alloué serveur). Corps `ExamenSoumissionRequest`
> `{ idAvis, idSecretaireSeance }` : `idAvis` = avis du PV (FAV/FAVR/DEF/NSP), obligatoire ;
> `idSecretaireSeance` = matricule du **Vérificateur désigné Secrétaire de séance**, **obligatoire** et qui
> doit être un VERIFICATEUR de la **localité du dossier** (circuit/réception). Absent ou invalide → **400**
> `{ erreurs:[{ champ:"idSecretaireSeance", message }] }`. *(La lettre de renvoi est une action séparée
> pendant l'examen — ressource `/api/lettre-renvois` ; `ExamenDto` n'a pas de champ `typeResultat`.)*
>
> ⚠️ **PV — document généré (règle ajoutée).** À la **signature finale** du PV (passage à `SIGNE`), si le PV
> est éligible — avis **favorable sous réserve** (`FAVR`), dossier de **localité centrale** (`ANT`) et **PPM**
> comportant au moins une ligne de marché, **quel que soit le mode de passation** (le gabarit AFSR/PPM/central
> ne dépend pas du mode) — le **PDF du PV** est généré à partir du
> modèle Word `PV_AFSR_PPMAGPM_CENTRALE.docx` (copie du modèle + remplacement des placeholders ; date d'examen
> formatée et **en toutes lettres** dans « L'an … » ; bloc « Étaient présents » filtré sur les signataires
> effectifs ; ANNEXE = une ligne par observation des points non conformes) puis converti via Microsoft Word
> (documents4j) et **stocké sur le FSX** (`storage.pv-examen.path`, sous-répertoire `PV/`), chemin conservé dans
> `t_pv_examen.CHEMIN_DOCUMENT`. Hors de ces conditions, le PV reste **sans document**. Le téléchargement
> **régénère le document à la demande** si le chemin est absent ou le fichier introuvable (migration des PV
> signés avant ce correctif). _Pré-requis machine/CI : Word installé._

**Exemple — requête (examen)**
```json
{ "idExamen": 201, "idDispatch": 88, "imCtrlMembre": "MEMANT1", "dateExamen": "2026-05-08" }
```
**Exemple — corps `…/soumettre`**
```json
{ "idAvis": "FAVR", "idSecretaireSeance": "VERANT1" }
```

---

## Lettres de renvoi
**Ressource** `/api/lettre-renvois` (table `t_lettre_renvoi`) — **action séparée pendant l'examen** : le
Membre peut créer **N lettres de renvoi** par examen (indépendamment du Projet de PV). Lecture filtrée par
profil/localité. Cycle : `BROUILLON → SOUMIS → SIGNE` (signature CC ou Président).

**Champs `LettreRenvoiDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idLettre | number | — (réponse) | PK **auto-générée** (IDENTITY) |
| idExamen | number | Oui | @NotNull (« L'examen est obligatoire. ») — FK `t_examen` (**non unique** : N lettres/examen) |
| idDossier | number | — (réponse) | **lecture seule** (dérivé de l'examen) |
| refLettre | string | — (réponse) | **générée serveur** : `<seqLettreGlobal>/<type>/<code_localite>/LR/<année>` (ex. `00001/PPM/CRM-ANT/LR/2026`). Le **type/localité/année** proviennent du `refeDossier` du dossier, mais le **numéro de séquence est un compteur GLOBAL dédié aux lettres** (par année, **strictement unique et continu** tous dossiers/entités/localités confondus — ≠ le numéro du dossier). `null` si `refeDossier` non structuré |
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
| POST | /api/lettre-renvois | `LettreRenvoiDto` | `LettreRenvoiDto` | 201, 400, 403 | **MEMBRE** — création pendant l'examen (BROUILLON) |
| PUT | /api/lettre-renvois/{id} | `LettreRenvoiDto` | `LettreRenvoiDto` | 200, 400, 404, 409 | **MEMBRE** (brouillon : objet/corps) |
| POST | /api/lettre-renvois/{id}/soumettre | — | `LettreRenvoiDto` | 200, 403, 404, 409 | **MEMBRE propriétaire** (BROUILLON→SOUMIS) |
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
> **`{refLettre}.pdf`** (les `/` remplacés par `_`, ex. `00007_PPM_CRM-ANT_LR_2026.pdf`) ; le chemin est
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
| GET | /api/kpis/tableau-bord | — | `TableauBordDto` | 200, 403 | PRESIDENT / ADMINISTRATEUR / CHEF_COMMISSION |
| GET | /api/kpis/mes-compteurs | — | `CompteursPrmpDto` | 200, 403 | **PRMP** (compteurs de son propre périmètre) |
| GET | /api/kpis/mes-compteurs-verificateur | — | `CompteursVerificateurDto` | 200, 403 | **VERIFICATEUR** (ou délégué) / ADMINISTRATEUR — compteurs de sa localité |
| GET | /api/kpis/mes-compteurs-secretaire | — | `CompteursSecretaireDto` | 200, 403 | **SECRETAIRE** (ou délégué) / ADMINISTRATEUR — compteurs de sa localité |
| GET | /api/kpis/mes-compteurs-membre | — | `CompteursMembreDto` | 200, 403 | **MEMBRE** (ou délégué) / ADMINISTRATEUR — compteurs de ses dossiers attribués |
| GET | /api/kpis/mes-compteurs-publication | — | `CompteursPublicationDto` | 200, 403 | **CHARGE_PUBLICATION** / ADMINISTRATEUR — workflow de publication (global) |
| GET | /api/kpis/mes-compteurs-assistant | — | `CompteursAssistantDto` | 200, 403 | **ASSISTANT_CONTROLEUR** / ADMINISTRATEUR — documents signés de sa localité |
| GET | /api/kpis/mes-compteurs-admin | — | `CompteursAdminDto` | 200, 403 | **ADMINISTRATEUR** — inscriptions, comptes, audit (global) |

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
> `aExaminer` (ses dossiers `DISPATCHE`, miroir de `/api/dossiers/a-examiner`) et `examines` (son
> historique : `EXAMINE`/`PV_SIGNE`/`EN_VERIFICATION`/`CLOTURE`).

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

**Champs `LocaliteDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idLocalite | string | Oui (PK, au POST) | clé primaire, max 5 |
| libelleLocalite | string | Oui | @NotBlank, max 50 |
| referencement | string | Oui | @NotBlank, max 50 |
| localite | string | Oui | @NotBlank, max 3 |

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
**Ressource** `/api/lots` — Lecture / écriture : tout utilisateur authentifié.

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
| POST | /api/lots | `LotDto` | `LotDto` | 201, 400 | Authentifié |
| PUT | /api/lots/{id} | `LotDto` | `LotDto` | 200, 400, 404 | Authentifié |
| DELETE | /api/lots/{id} | — | — | 204, 404 | Authentifié |

`{id}` = idLot (number).

**Exemple — requête**
```json
{ "idLot": 88, "idDossier": 320, "idDetail": 1205, "designationLot": "Fourniture de mobilier - Lot 1", "montLot": 85000000.0, "qteLot": 150, "uniteLot": "unite" }
```

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
| GET | /api/marches | — | `MarcheDto[]` (scopé) | 200 | Authentifié |
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

**Champs `CapmDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idCapm | number | Oui (PK, au POST) | clé primaire (assignée par le client) |
| libelleProcessus | string | Non | max 100 |
| ordre | number | Oui | @NotNull |

**Données initiales** : `(1,'LANCEMENT',1)`, `(2,'DAO',2)`, `(3,'OUVERTURE',3)`, `(4,'ATTRIBUTION',4)`.

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/capm | — | `CapmDto[]` | 200 | Authentifié |
| GET | /api/capm/{id} | — | `CapmDto` | 200, 404 | Authentifié |
| POST | /api/capm | `CapmDto` | `CapmDto` | 201, 400, 403 | **ADMINISTRATEUR** |
| PUT | /api/capm/{id} | `CapmDto` | `CapmDto` | 200, 400, 403, 404 | **ADMINISTRATEUR** |
| DELETE | /api/capm/{id} | — | — | 204, 403, 404 | **ADMINISTRATEUR** |

---

## Marchés — dates prévisionnelles
**Ressource** `/api/marche-previsions` — Lecture / écriture : tout utilisateur authentifié.

Dates prévisionnelles d'un marché, en relation **1,N** avec `/api/marches` : **une ligne par
processus** (`idCapm` → **CAPM**), chacune avec une `dateDebut` et une `dateFin`. Le filtre
`?marche={idDetail}` renvoie les lignes **triées par `t_capm.ordre` ASC**.

**Champs `MarchePrevisionDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPrevision | number | Oui (PK, au POST) | @NotNull, clé primaire |
| idDetail | number | Oui | @NotNull — FK vers le marché |
| idCapm | number | Oui | @NotNull — FK vers `t_capm` (processus) |
| dateDebut | string (date) | Oui | @NotNull — `yyyy-MM-dd` |
| dateFin | string (date) | Oui | @NotNull — `yyyy-MM-dd` |
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

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/mode-passations | — | `ModePassationDto[]` | 200 | Authentifié |
| GET | /api/mode-passations/{id} | — | `ModePassationDto` | 200, 404 | Authentifié |
| POST | /api/mode-passations | `ModePassationDto` | `ModePassationDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/mode-passations/{id} | `ModePassationDto` | `ModePassationDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/mode-passations/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idMode (number).

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
- **Mes notifications** (`/mes`, `/mes/non-lues/count`, `/{id}/lu`, `/lire-tout`) : **scopées** à l'utilisateur courant — chacun ne voit/agit que sur **les siennes** (clé `DESTINATAIRE_REF` + `DESTINATAIRE_TYPE` ; repli e-mail pour les PRMP).
- **Liste globale** et **CRUD** : réservés à l'**Administrateur** (supervision).

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

**Champs `PointsCtrlDto`**

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| idPointCtrl | number | Oui (PK, au POST) | clé primaire |
| libelPointCtrl | string | Non | |
| decriptPointCtrl | string | Non | |
| ordrePointCtrl | number | Non | |
| obligatoire | boolean | Oui | @NotNull |
| idTypeDossier | string | Oui | @NotBlank |

**Endpoints**

| Méthode | URL | Corps | Réponse | Statuts | Rôle |
|---|---|---|---|---|---|
| GET | /api/points-ctrls | — | `PointsCtrlDto[]` | 200 | Authentifié |
| GET | /api/points-ctrls/{id} | — | `PointsCtrlDto` | 200, 404 | Authentifié |
| POST | /api/points-ctrls | `PointsCtrlDto` | `PointsCtrlDto` | 201, 400, 403 | ADMINISTRATEUR |
| PUT | /api/points-ctrls/{id} | `PointsCtrlDto` | `PointsCtrlDto` | 200, 400, 404 | ADMINISTRATEUR |
| DELETE | /api/points-ctrls/{id} | — | — | 204, 404 | ADMINISTRATEUR |

`{id}` = idPointCtrl (number).

**Exemple — requête**
```json
{ "idPointCtrl": 12, "libelPointCtrl": "Présence de l'avis d'appel d'offres", "decriptPointCtrl": "Vérifier que l'avis est joint et signé.", "ordrePointCtrl": 3, "obligatoire": true, "idTypeDossier": "AO" }
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
| GET | /api/ppms | — | `PpmDto[]` (scopé) | 200 | Authentifié |
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
> si la pièce est absente). Ces sous-chemins pièces sont réservés **ADMINISTRATEUR**.
>
> **DELETE** supprime la PRMP **et son compte d'authentification**. **Garde** : **409** tant que la PRMP porte des
> données liées (dossiers, PPM, entités rattachées, demandes de retrait, indicateurs, ou UGPM de tutelle) — retirer
> d'abord ces éléments ; **404** si l'`idPrmp` est inconnu.
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
| idAvis | string | Oui | @NotBlank, max 10 |
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
| idSecretaireSeance | string | — (posé à la soumission) | max 7 — Vérificateur désigné **Secrétaire de séance** (validé à `…/examens/{id}/soumettre`) |
| nomSecretaireSeance | string | — (réponse) | nom complet du secrétaire de séance (« prénoms nom »), peuplé serveur — lecture seule |
| documentDisponible | boolean | — (réponse) | **`true`** si un PDF officiel est réellement disponible : `CHEMIN_DOCUMENT` non nul **ou** PV **éligible** (avis `FAVR` + localité centrale `ANT` + PPM avec ≥ 1 ligne de marché, **quel que soit le mode de passation**, donc régénérable à la demande) ; **`false`** sinon. Lecture seule, peuplé serveur → le front masque « Télécharger le PDF » et évite un 404 |

> ⚠️ **Disponibilité du document (`documentDisponible`) — règle ajoutée.** Le flag reflète la règle « PV — document généré » **et** l'existence effective du fichier : `true` si `t_pv_examen.CHEMIN_DOCUMENT` est renseigné, ou si le PV est éligible à la génération à la demande (`GET /api/pv-examens/{id}/document` régénère alors le PDF). Il reste donc juste après une (re)génération. Un PV non éligible (ex. avis **≠ FAVR**, ou localité **non centrale**, ou dossier **sans PPM**) → `false`, et `…/document` renvoie **404**. *(Le **mode de passation** n'entre plus dans l'éligibilité : un PV FAVR/ANT/PPM en « Demande de cotation » est désormais éligible.)*

> ⚠️ **Référence du PV (`refePv`) — règle ajoutée.** À la création, le serveur dérive `refePv` du `refeDossier`
> du dossier rattaché en insérant **`/PV` avant l'année** : `00003/PPM/CRM-ANT/2026` → `00003/PPM/CRM-ANT/PV/2026`.
> Dérivée **uniquement** si `refeDossier` est au format `…/YYYY` (sinon `null`). **Unique** : créer un 2ᵉ PV sur le
> même dossier (même `refePv`) → **409**. Distincte du champ libre `referencePv`.

**Champs `PvActionRequest`** (corps des actions de workflow)

| Champ (JSON) | Type | Obligatoire | Contraintes |
|---|---|---|---|
| imActeur | string | Oui | @NotBlank, max 7. **Non utilisé pour l'identité** : `signer` enregistre l'utilisateur authentifié (JWT), pas ce champ |
| commentaire | string | Conditionnel | obligatoire pour `retourner` (sinon 409) |
| role | string | Conditionnel | max 20 — obligatoire pour `signer` : `MEMBRE` / `PRESIDENT` / `CC` |

**Endpoints**

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

`{id}` = idPv (number). `soumettre` : BROUILLON|EN_RECTIFICATION→PROJET_SOUMIS ; `retourner` : PROJET_SOUMIS→EN_RECTIFICATION (`commentaire` obligatoire) ; `accepter` : PROJET_SOUMIS→PROJET_ACCEPTE ; `signer` : passe à SIGNE quand le Membre **et** (le Président **ou** le CC) ont signé.

> ⚠️ **Garde-fou de cohérence dossier↔PV (règle ajoutée).** `DELETE /api/pv-examens/{id}` réaligne le dossier : si, après suppression, le dossier n'a **plus aucun PV `SIGNE`** et se trouve encore **`EN_VERIFICATION`**, il est ramené à **`EXAMINE`** (état « examiné, en attente de PV »). Un dossier ne peut donc plus rester bloqué `EN_VERIFICATION` (« PV signé introuvable » côté vérification) alors que son PV signé n'existe plus. Les autres statuts sont laissés inchangés.

> ⚠️ **Liste scindée projets / définitifs (règle ajoutée).** `GET /api/pv-examens` ne retourne que les **projets de PV** (statut ≠ `SIGNE`) ; dès qu'un PV est **signé** (`SIGNE`) il **quitte** cette liste et apparaît dans **`GET /api/pv-examens/definitifs`** (PV signés uniquement). Les deux listes restent **scopées par localité**. L'accès direct `GET /api/pv-examens/{id}` reste valable pour **tout** PV, signé ou non.

> ⚠️ **Téléchargement du PDF du PV (règle ajoutée).** `GET /api/pv-examens/{id}/document` renvoie le **PDF du PV** (`application/pdf`, en pièce jointe) **lu sur le FSX** (`t_pv_examen.CHEMIN_DOCUMENT`). Accès dans le **périmètre de localité** (même contrôle que `GET /api/pv-examens/{id}`). Si le chemin est absent (PV signé avant le correctif) ou le fichier introuvable, le document est **régénéré à la demande** (si le PV est éligible). **404** seulement si le PV n'est **pas éligible** à la génération (cf. règle « PV — document généré »).

**`signer` — authentification de la signature (dans le service).** L'endpoint autorise largement (`MEMBRE`/`CHEF_COMMISSION`/`PRESIDENT`) mais le service vérifie que le **signataire authentifié** correspond au `role` signé et enregistre son identité (`IM_CTRL_MEMBRE`/`IM_CTRL_PRESIDENT`/`IM_CTRL_CC` = matricule du signataire) :
- `role=MEMBRE` → l'appelant doit être le **Membre attributaire** du PV (`IM_CTRL_MEMBRE`), non déléguable → **403** sinon ;
- `role=PRESIDENT` → profil **PRESIDENT** réel → **403** sinon ;
- `role=CC` → profil **CHEF_COMMISSION** **et localité du dossier** → **403** sinon ;
- co-signataire (Président/CC) **≠ Membre signataire** : auto-co-signature interdite → **409** ;
- `signer` hors `PROJET_ACCEPTE` → **409**.

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

## Réceptions
**Ressource** `/api/receptions` — POST/PUT : profil `SECRETAIRE` (titulaire ou délégué) ; DELETE : `ADMINISTRATEUR`. Écriture limitée à sa localité (dossier hors localité → 403, sauf Président). Lecture filtrée par localité.

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
> **Référence officielle générée à la réception (⚠️ règle ajoutée).** Au POST, le serveur génère et
> renvoie `reference` au format **`xxxxx/type_dossier/code_localite/annee_exercice`** :
> `xxxxx` = compteur 5 chiffres incrémenté par la base, **par combinaison** (`type_dossier`, `code_localite`,
> `annee_exercice`) — table `t_sequence_reference`, sans compteur applicatif ;
> `code_localite` = **`CNM`** si réception centrale (utilisateur transversal, sans localité, ex. Président),
> sinon **`CRM-<localité>`** ; `annee_exercice` = exercice du PPM, sinon année courante.
> La référence est **persistée** sur le dossier (`REFE_DOSSIER`, vide depuis la soumission)
> **et sur la réception elle-même** (`t_reception.REFERENCE`) comme **snapshot immuable** : `GET /api/receptions`
> la renvoie telle qu'à la réception, **même après** une mutation ultérieure de `refeDossier` (ex. restauration
> de la référence PPM après un **retrait accepté**, cf. `POST /api/demande-retraits/{id}/accepter`). L'historique
> des réceptions reste ainsi correct indépendamment du dossier.
> Exemples : `00001/PPM/CNM/2026`, `00001/PPM/CRM-ANT/2026`, `00002/PPM/CRM-ANT/2026`, `00001/PPM/CRM-TMS/2026`.
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

## Types de dossier
**Ressource** `/api/type-dossiers` — Référentiel : lecture ouverte ; écriture `ADMINISTRATEUR`.

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
{ "idTypeDossier": "DAO", "libelleType": "Dossier d'appel d'offres" }
```

---

## Vérifications
**Ressource** `/api/verifications` — autorisation large à l'entrée, mais le **service exige strictement le profil `VERIFICATEUR`** (⚠️ règle ajoutée — **pas de délégation** CC/Président) ; DELETE : `ADMINISTRATEUR`. Écriture limitée à sa localité. Lecture filtrée par localité.

> ⚠️ **Identité & ID (règle ajoutée).** L'identité du vérificateur (`imCtrlVerif`) et la `dateVerif` sont **prises du JWT / serveur**, jamais du corps. L'`idVerification` est **auto-généré** (colonne IDENTITY) ; ne pas le fournir.

> **Préconditions de circuit (création/MAJ) → 403/409** : profil non `VERIFICATEUR` → **403** ; sinon le PV référencé (`idPv`) doit être **`SIGNE`** **et** d'avis **`FAVR`** (favorable avec réserves) **et** le dossier **non clos** → sinon **409**. La vérification est **itérative** sur le même dossier.

> **Effet `[Auto]`** (sur un dossier `EN_VERIFICATION`) : `obsLevees = true` → dossier **`CLOTURE`** + notification `CLOTURE_ELIGIBLE`. ⚠️ **Règle ajoutée** — `obsLevees = false` → dossier **`EN_ATTENTE_DECISION_PRMP`** : l'observation est **transmise à la PRMP** du dossier (notification `OBSERVATION_VERIFICATION` : référence dossier, vérificateur, texte de l'observation, date) et l'événement est **tracé** dans `t_audit_log`. Le vérificateur ne peut plus modifier ni soumettre de vérification tant que la PRMP n'a pas statué (nouvelle tentative → **409**) ; il voit le dossier en lecture seule dans `GET /api/dossiers/en-attente-prmp`. La PRMP le retrouve via `GET /api/dossiers?statut=EN_ATTENTE_DECISION_PRMP` et lit l'observation complète dans sa notification.

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
