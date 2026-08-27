# Plan de cloture — audit global du 27 aout 2026

> Origine : `AUDIT-GLOBAL-2026-08-27.md` (racine `c:\dev\PRS2.0`, **hors depot**, 7 agents en parallele,
> aucun fichier modifie pendant l'audit). Verdict initial : sante generale tres bonne (490/490 tests
> backend, 97/97 front, 0 vulnerabilite npm) mais 4 constats critiques inedits, tous backend
> (cloisonnement des lectures oublie, chemins secondaires qui ne rejouent pas leurs preconditions).
> Chantier mene sur la branche `chantier/audit-2026-08` des deux depots. Etabli le **2026-08-27**.
> Push impossible (patron absent, 403) : commits locaux uniquement, comme tous les chantiers precedents.

---

## 1. Lots et commits

L'audit proposait 6 lots (section "Plan d'action propose", `AUDIT-GLOBAL-2026-08-27.md`). Les lots A, B,
D, E ont chacun produit un changement de contrat d'API (documente dans `docs/api-endpoints.md`) ; les
lots C et F sont restes des libelles internes de l'audit, retrouves ici a partir du contenu reel des
commits plutot que d'un marquage explicite dans leurs messages.

### Lot A — Cloisonnement des lectures (bloquant securite)

Backend, 5 commits :

| Commit | Objet |
|---|---|
| `f27fe25` | Pieces jointes de dossier bornees au perimetre (constat C1) |
| `06f263f` | Examen (details/pieces/observations) cloisonne en lecture (constat C2) |
| `dbc898f` | Retrait : precondition d'etat rejouee a l'acceptation (constat C3) |
| `0e971f9` | Historique des echanges et transmissions SIGMP cloisonnes (meme famille que C1/C2) |
| `b66a62b` | Journal d'audit : ajout seul, POST/PUT/DELETE refuses en 409 |

### Lot B — Gardes des chemins secondaires

Backend, 8 commits :

| Commit | Objet |
|---|---|
| `1f0e65e` | Navette du PV : identite du redacteur, localite a la cloture, acteur depuis le JWT |
| `657fc71` | Examen : garde attributaire hors creation, details et pieces verrouilles |
| `27ba5e6` | Reception : liste blanche de statuts, reference au premier passage, anti-doublon |
| `f04e365` | Dispatch : le PUT regarde comme le POST, dispatcheur depuis le JWT |
| `938278f` | Lettre de renvoi et depot de piece : signature et depot bornes a leur perimetre |
| `d42f3ad` | Mandat PRMP : la reconduction rouvre le compte eteint en fin de mandat |
| `e493c35` | Validation : montants bornes, exercice borne, les deux PATCH rectifier valides |
| `7f90d62` | Suppressions generiques : les traces decidees du circuit refusees en 409 |

Plus `c7e692c` (notifications PRMP portees par la ref, et 404 JPA rendu en francais) — commit-pont dont
le premier volet (404 JPA) releve du lot B et le second (notifications) du lot D.

### Lot C — Tests qui manquaient

Backend, 6 commits (aucun changement de comportement metier, uniquement des tests et leur cablage) :

| Commit | Objet |
|---|---|
| `2e995df` | Scheduler d'alertes : horloge injectee (Clock), paliers de mandat et expiration testes (constat C4) |
| `87f270d` | Referentiels : matrice lecture-tous / ecriture-admin sur les 10 sans aucun test |
| `7ffc9e5` | Verrou optimiste HTTP : les 3 ressources manquantes du contrat (Dossier, Marche, Lettre de renvoi) |
| `c0ffd43` | MiseAJourPpmController de bout en bout par HTTP |
| `bc4e840` | Lettre de renvoi : circuit complet par HTTP, soumettre et archiver enfin appeles |
| `f32ba2a` | Endpoints pagines : contrat Page verifie sur dossiers, ppms, marches et actualites |

### Lot D — Donnees, performance et adoption de la pagination

Backend, 7 commits :

| Commit | Objet |
|---|---|
| `7aadc32` | Suppression d'un dossier : fermeture de la cascade, plus d'orphelin ni de 409 de cle etrangere |
| `94d7de4` | Migration V8 : index sur les colonnes de cle etrangere, FK manquante posee |
| `b277167` | Lettres de renvoi : nom du signataire et flag lue resolus en lot, fin du N+1 |
| `1a83b05` | Pagination des dossiers, PPM et marches : decoupage en SQL au lieu du decoupage en memoire |
| `a3c32f2` | Journal d'audit et notifications : lecture paginee et filtree, liste historique plafonnee |
| `ee7957c` | Recherche de la topbar : nouvel endpoint `GET /api/dossiers/recherche` |
| `4e11aaf` | Listes paginees : tri PK decroissant, les plus recents d'abord |

Hors des 4 lots types mais de la meme famille (robustesse des donnees, non repris explicitement dans le
plan d'action initial) : `71e48ae` (migration V9, verrou optimiste sur l'examen et ses details).

Frontend, adoption de la pagination outillee mais jamais branchee (constat P1 de l'audit precedent,
enfin corrige) — 9 commits sur les 20 du chantier front :

| Commit | Objet |
|---|---|
| `8a44426` | La barre de recherche de la topbar resout la reference cote serveur |
| `382a173` | La recherche relancee depuis la liste affichee retrouve son dossier |
| `2f51efa` | « Mes dossiers » pagine cote serveur au lieu de tout charger |
| `582a795` | Le pipeline pagine son tableau de bord et rend ses files par paquets |
| `af6739a` | La statistique des dispatchs cesse de demander tous les dossiers |
| `ec66347` | Dire l'attente d'une page sans estomper le tableau |
| `ad40144` | Le journal d'audit se lit page par page, filtre par table, acteur et periode |
| `238293b` | Verrouiller le contrat de la recherche serveur et de la pagination (tests) |
| `54392b4` | Realigner trois interfaces sur les DTO du backend |

### Lot E — Parc de production

Backend, 4 commits :

| Commit | Objet |
|---|---|
| `efc9299` | Limitation de debit : le login et les inscriptions publiques enfin brides (429) |
| `32b9741` | Mots de passe : 8 caracteres dont une lettre et un chiffre, sur les nouveaux seuls |
| `c4e5f54` | Documentation d'API : exposition pilotee par app.docs.publics, Admin si fermee |
| `5dbcccd` | Journaux : show-sql a false par defaut |

Voir `docs/deploiement.md` pour la consigne de mise en production correspondante (CSP frontend,
reverse proxy, fuseau horaire, rotation du mot de passe postgres...), nouvellement redigee par ce
chantier de documentation.

### Lot F — Hygiene

Frontend, 11 commits :

| Commit | Objet |
|---|---|
| `9a3e603` | « Changer mon mot de passe » enfin branche |
| `b103221` | Fin du double chargement de la liste de notifications au montage |
| `294579f` | Le depliage d'un PV recu passe par un vrai bouton (accessibilite) |
| `4bb270b` | Avertir quand une ressource locale non versionnee manque (logo MEF) |
| `f9440a9` | Verrou ESLint interdisant l'URL d'objet brute hors du module des fichiers surs |
| `73eb2ce` | Un seul chemin pour remettre un blob au navigateur |
| `7dfc4d8` | Sortir les deux sous-dialogs d'information du detail PPM |
| `bf991eb` | Sortir la consultation des dates previsionnelles du detail PPM |
| `2faf937` | Sortir les dialogues beneficiaires et lots du detail PPM |
| `f5cf8ec` | Retirer du detail PPM les regles parties avec les sous-dialogs |
| `77d8c80` | Retirer deux chemins d'appel qui n'existent plus cote serveur |

Aucun commit backend ne correspond a ce lot dans cette plage (`1b6e58c..HEAD`) : le cote backend du
"hygiene" (brancher le changement de mot de passe, code mort, mise a jour de `regles-gestion.md`) etait
soit deja fait cote serveur (l'endpoint existait), soit traite par ce chantier de documentation
lui-meme (`docs/regles-gestion.md`, `docs/api-endpoints.md`).

---

## 2. Etat final des suites (verifie au 27/08/2026)

| Suite | Resultat |
|---|---|
| Backend (`mvnw test`) | **604 / 604 verts** |
| Frontend (`ng test`) | **106 / 106 verts** (verifie par execution reelle, 12 fichiers spec) |
| Lint frontend (`npm run lint`) | 0 erreur, 0 avertissement |
| Build de prod frontend (`ng build`) | OK |

---

## 3. Decisions PO en attente

Liste factuelle des points que ce chantier a deliberement laisses ouverts, sans trancher a la place du
produit :

1. **Suppression d'un PV signe non archive toujours possible.** Porte de sortie assumee par test
   (`7f90d62`) pour rattraper un PV signe par erreur — le dossier redescend a `EXAMINE`. A confirmer
   que c'est le comportement voulu a long terme, ou si une garde supplementaire est souhaitee.
2. **Perimetre de lecture d'une lettre de renvoi = localite de reception, pas du dossier.** Divergence
   relevee pendant le lot B (le repli sur la localite de reception, quand `idLocalite` du dossier est
   absent, peut differer de la localite reelle du circuit dans de rares cas historiques).
3. **La PRMP ne voit plus la grille point par point dans « PV definitifs ».** Effet du cloisonnement du
   constat C2 — coherent avec §3.1/§3.5 de `regles-gestion.md`, mais c'est un changement de comportement
   observable pour l'utilisateur final (avant : aucune garde, elle pouvait tout lire). A confirmer cote
   metier que c'est bien l'intention (voir note dans `docs/regles-gestion.md`, §3.1 PRMP).
4. **~158 endpoints backend jamais appeles par le front (~37%).** Inventaire a trancher : reserve
   d'evolution deliberee, ou code mort a retirer. Non arbitre par ce chantier.
5. **Etats suspendus non retirables** (`EN_ATTENTE_PIECES`, `A_REEXAMINER`...) : `StatutDossier.
   NOMS_AVANT_PV_SIGNE` reste fige a `{SOUMIS, PRET_DISPATCH, DISPATCHE, EXAMINE}` — un dossier suspendu
   par une lettre de renvoi ne peut pas etre retire tant qu'il n'a pas repris son cours. A confirmer si
   c'est voulu (le circuit de retrait n'a jamais couvert ces etats, mais l'audit ne les a pas non plus
   signales comme un manque).
6. **Secretaire non restreint aux passages INITIAL malgre §3.4.** La liste blanche de reception
   (lot B) couvre tous les passages (INITIAL et RETOUR) sans distinguer le profil qui les saisit ;
   §3.4 de `regles-gestion.md` decrit le Secretaire comme charge de la reception en general, sans
   trancher explicitement ce cas.
7. **Alerte FIN_MANDAT sans garde d'idempotence.** Trouvaille testee pendant le lot C
   (`AlerteScheduler.alerterFinMandat`) : contrairement aux jalons du calendrier (drapeau
   `alerteEnvoyee`), rien n'empeche un renvoi si le job tournait plusieurs fois le meme jour (improbable
   en usage normal, un cron quotidien, mais non garde par le code).
8. **`t_piece_jointe_dossier.ID_LETTRE` reste un lien pendant apres un retrait accepte.** La purge du
   circuit a l'acceptation d'un retrait ne nettoie pas cette colonne sur les pieces deja deposees
   apres une lettre de renvoi restee jointe a un dossier redevenu BROUILLON.
9. **`/dossiers/examines` et `/dossiers/verifies` acceptent le parametre `sort` du client**,
   contrairement aux listes paginees generiques (`/dossiers`, `/ppms`, `/marches`) qui l'ignorent au
   profit du tri PK impose par le serveur (corrige par `4e11aaf`). Incoherence de contrat entre listes
   paginees, non harmonisee par ce chantier.
10. **Ouverture eventuelle de « mes lettres de renvoi » a l'UGPM.** Question deja ouverte par le plan
    `plan-lettre-lue-par-agent.md` (2026-08-27) : le suivi de lecture est desormais individuel par
    agent, ce qui rendrait techniquement possible d'ouvrir une liste et un badge a l'UGPM — toujours
    pas fait, toujours une decision produit en attente.

---

## 4. Suites possibles (non engagees par ce chantier)

- **Endpoint "attributions en cours"** qui remplacerait les 6 appels a `dispatchs-controleurs` par un
  seul GET consolide.
- **Filtre "statut different de" cote serveur**, pour les ecrans qui filtrent aujourd'hui une liste
  deja recuperee.
- **Version obligatoire sur la facade `/api/saisies`** — dette documentee dans
  `docs/plan-conflit-version.md` : les PUT qui passent par cette facade n'envoient pas de `version`,
  le verrou optimiste HTTP ne les couvre donc pas.
- **E2E Playwright** (T3 de l'audit frontend precedent, toujours ouvert).
- **Revocation JWT / refresh tokens** — logout sans revocation serveur (§3.4 de l'audit), attenue par
  le cookie HttpOnly/SameSite mais pas elimine ; sujet explicitement "parque en production" depuis le
  `PLAN_TRAVAUX_2026-08.md` (anti-bruteforce leve par le lot E de ce chantier, refresh token toujours
  hors perimetre).

---

*Rappel de contexte : push toujours impossible (patron absent, 403) — tout ce chantier existe en
commits locaux, sur `chantier/audit-2026-08`, dans les deux depots.*
