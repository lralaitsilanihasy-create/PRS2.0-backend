# ADR-0006 : Journal des connexions — alimenter `t_session_utilisateur` plutôt qu'étendre le journal d'audit

**Statut :** Adopté
**Date :** 2026-09-17
**Origine :** demande front `frontend/docs/demande-backend-2026-09-17-espace-admin.md`, §B4 (lot 6,
espace d'administration). Migration `V31__journal_connexions.sql`.

## Contexte

**Aucune connexion n'était tracée durablement, nulle part.** Le constat, vérifié sur le code du
2026-09-17 :

- `AuditConfig` **exclut** `/api/auth/**` du journal d'audit : aucune connexion n'y entrait. Et
  `AuditInterceptor` ignore de toute façon toute réponse ≥ 400 — les échecs, précisément ce qu'on veut
  voir, n'y seraient jamais entrés même sans cette exclusion.
- `t_session_utilisateur` existait depuis la baseline mais n'était écrite par **aucun code
  applicatif** : le seul `new SessionUtilisateur()` du backend vivait dans le mapper du CRUD
  générique, c'est-à-dire que la table n'était alimentée que par l'écran d'administration lui-même.
  Cet écran, `/api/session-utilisateurs`, était en **écriture complète** : un Administrateur pouvait y
  créer une fausse trace de connexion, modifier une date, supprimer la sienne.
- Les échecs ne vivaient que dans les `ConcurrentHashMap` de `LoginRateLimiter`, effacées à chaque
  redémarrage. Il n'existait donc aucune réponse à « qui a essayé de se connecter cette nuit ? ».
- `t_audit_log.SESSION_ID` porte pourtant une clé étrangère vers `t_session_utilisateur(ID_SESSION)`
  (`V1__baseline.sql`) : le schéma prévoyait que chaque action pointe vers sa session. Cette FK est
  morte depuis la baseline.

Trois mesures de l'espace d'administration (sessions ouvertes, échecs de connexion sur 24 h, dernière
connexion d'une personne) étaient de ce fait **retirées des écrans** plutôt qu'affichées à zéro :
« une mesure fausse sur un tableau de bord de sécurité est pire qu'une mesure absente »
(plan `frontend/docs/plan-refonte-L6-espace-admin.md`, §6).

## Décision

**Alimenter `t_session_utilisateur` au login et au logout** (`JournalConnexionService`), et l'exposer
en **lecture seule** par `GET /api/sessions`. Le CRUD `/api/session-utilisateurs` est **retiré**.

L'option envisagée et **écartée** était de retirer l'exclusion d'`/api/auth/**` du journal d'audit et
de tout faire porter par `t_audit_log`. Trois raisons :

1. **Ce ne sont pas les mêmes objets.** Une ligne d'audit décrit une **écriture** — table,
   enregistrement, champ, avant/après. Une session décrit une **durée** : un début, une fin, un poste,
   une adresse. Les loger dans la même table obligerait à laisser vides la moitié des colonnes de
   chaque côté, et à inventer une convention pour la fermeture.
2. **L'intercepteur d'audit ignore tout ce qui renvoie ≥ 400.** C'est un choix juste pour l'audit — on
   journalise les écritures *réussies* — mais il perdrait exactement ce que ce besoin cherche : les
   tentatives refusées.
3. **La table et sa clé étrangère existaient déjà.** Il n'y avait rien à concevoir, seulement à
   écrire.

### Trois points de conception qui engagent

**L'identifiant de session est l'empreinte SHA-256 du jeton émis.** Le `logout` la recalcule depuis le
cookie `PRS_SESSION` (ou l'en-tête `Bearer`) et retrouve *sa* ligne. L'autre voie possible était un
claim `jti` dans le JWT ; elle a été écartée parce qu'elle change le contrat du jeton et oblige à
faire remonter l'identifiant à travers `AuthService` puis `LoginResponse`, donc jusqu'au client, pour
un besoin purement interne. L'empreinte donne la même identité, sans stocker aucun jeton en base :
une fuite de la table ne rend aucune session.

**Le journal ne casse jamais la connexion.** C'est le principe déjà retenu par `AuditInterceptor`
(« l'audit ne doit jamais casser la requête »), et il est plus impératif encore ici : une écriture qui
remonterait rendrait l'application *inconnectable*, pour tout le monde, d'un coup. En pratique :
tout ce qui vient du client est tronqué à la longueur de sa colonne (un `User-Agent` de plus de 300
caractères ou un `X-Forwarded-For` forgé sont des entrées ordinaires, pas des incidents), et ce qui
remonterait malgré tout est avalé et consigné en `WARN`.

**Les refus du quota (429) ne sont pas journalisés.** Une tentative refusée par `LoginRateLimiter`
avant tout examen des identifiants n'est pas une tentative de connexion. Surtout, la journaliser
retirerait au verrou son effet de plafond sur le volume — c'est pendant le verrou qu'un attaquant
frappe le plus.

## Conséquences

**Ce qui devient possible :**

- Répondre à « qui s'est connecté, quand, depuis où » et à « qui a essayé et échoué », y compris sur
  des identifiants qui n'existent pas — la ligne qui manquait le plus.
- Les trois mesures retirées des écrans peuvent revenir : `sessionsOuvertes` et `echecsConnexion24h`
  sur l'accueil, `derniereConnexion` et `echecs30j` sur la fiche d'annuaire.
- Le journal n'est plus falsifiable : il n'existe aucune route d'écriture. Même raisonnement, et même
  conclusion, que pour `/api/audit-logs` le 2026-08-27.

**Ce qu'il faut surveiller :**

- **Le volume.** Une ligne par tentative examinée. Le débit d'échecs est borné par `LoginRateLimiter`
  (20 par adresse et par quart d'heure, soit au plus 1 920 lignes par jour et par adresse) ; les
  connexions **réussies**, elles, ne sont bornées par rien d'autre que le coût de BCrypt et la
  possession d'identifiants valides. Trois index couvrent les lectures prévues.
- **Aucune purge n'est implémentée, et c'est délibéré.** La durée de conservation d'un journal de
  preuve est une décision produit — combien de temps la CNM doit-elle pouvoir prouver qui s'est
  connecté ? — pas un choix d'implémentation. **À arbitrer.**
- **`t_session_utilisateur.IM_CONTROLEUR` porte désormais la référence de n'importe quel acteur** et
  n'a plus de clé étrangère vers `tr_controleur` (V31) : une PRMP n'y figure pas. Le nom de la colonne
  est trompeur et a été conservé ; le sens réel est écrit dans le `COMMENT` de la colonne, dans la
  javadoc de l'entité et dans `docs/api-endpoints.md`.
- **`t_audit_log.SESSION_ID` reste vide.** La FK qui la relie à ce journal redevient *utilisable* —
  une session existe enfin à pointer — mais rien ne la renseigne : `AuditInterceptor` n'écrit pas la
  session de l'acteur. Relier les deux journaux est possible et n'a pas été fait ici, faute d'être
  demandé.

## Marche arrière

Le journal est en ajout seul et n'est lu par rien d'autre que l'espace d'administration : cesser
d'écrire revient à ne plus appeler `JournalConnexionService` dans `AuthController`, sans rien casser.
Ce qui ne se défait pas sans migration, c'est V31 — l'élargissement de la colonne et le retrait de la
clé étrangère. Reposer cette FK exigerait d'abord de vider la table de toutes les lignes de PRMP et
d'UGPM, c'est-à-dire de renoncer à tracer leurs connexions : c'est le défaut qu'on vient de corriger.
