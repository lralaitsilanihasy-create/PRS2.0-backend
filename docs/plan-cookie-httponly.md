# Plan — session en cookie `HttpOnly` (audit front, point 5)

> Statut : **phase 1 LIVRÉE (2026-08-17)** — cookie posé au login, résolveur double
> (Bearer d'abord, cookie sinon), `POST /api/auth/logout`, CSRF ciblé. Deux précisions apportées à
> la livraison : (1) l'exemption CSRF couvre aussi les requêtes **sans cookie de session** (rien que
> le navigateur attacherait automatiquement → pas de risque CSRF, et les mutations anonymes restent
> des **401**, pas des 403) ; (2) l'enforcement passe par une garde dédiée `CookieCsrfGarde`
> (double-submit stateless `X-XSRF-TOKEN` == `XSRF-TOKEN`) car le resource server OAuth2 exempte
> d'office du `CsrfFilter` standard toute requête où le résolveur trouve un jeton — cookie compris ;
> le `CsrfFilter` reste l'émetteur du cookie `XSRF-TOKEN`. Phases 0 (même origine, côté
> front/infra), 2, 3 et 4 : à venir.
> Objectif : le jeton de session n'est **plus jamais accessible au JavaScript** du front
> (fin du `localStorage`), sans big-bang : chaque phase est rétro-compatible et réversible.

---

## 1. État actuel (ancrage code)

| Élément | Aujourd'hui |
|---|---|
| Connexion | `POST /api/auth/login` → `LoginResponse` avec **`token` dans le corps** (+ `login`, `role`, `ref`, `expiresIn`…) |
| Stockage front | `localStorage`, envoi `Authorization: Bearer <token>` par interceptor |
| Jeton | JWT HMAC HS256 (`app.jwt.secret`), durée `app.jwt.expiration-seconds` (8 h), **stateless** |
| CSRF | `csrf.disable()` (cohérent avec l'auth par en-tête : un site tiers ne peut pas forger `Authorization`) |
| CORS | `CorsConfig` : `http://localhost:4200` (front et API sur des origines différentes en dev) |
| Déconnexion | côté front uniquement (suppression du jeton stocké) — pas d'endpoint |

**Risque visé par l'audit** : un XSS dans le front peut lire `localStorage` et exfiltrer le jeton.
Avec un cookie `HttpOnly`, le même XSS ne peut plus voler la session (il peut encore *agir* tant que
la page est ouverte — le cookie réduit la surface, il ne remplace pas l'hygiène XSS, que la CSP
livrée au point 4 renforce déjà).

## 2. Cible

- `POST /api/auth/login` **pose un cookie** de session ; le corps ne porte plus de jeton.
- Cookie : `HttpOnly; Secure; SameSite=Strict; Path=/`, durée alignée sur le JWT.
- Le backend authentifie depuis le cookie ; `Authorization: Bearer` **reste accepté**
  (clients non-navigateur, tests d'intégration — le risque XSS est éliminé dès lors que le front
  ne stocke plus de jeton, pas besoin de fermer le canal Bearer).
- Défense CSRF réactivée pour les requêtes authentifiées **par cookie**.

### Décisions techniques proposées

| Sujet | Décision | Pourquoi |
|---|---|---|
| Contenu du cookie | **Le même JWT** qu'aujourd'hui (mêmes claims, même décodeur) | Zéro changement du modèle d'auth (`CurrentUser`, rôles, délégations) ; seul le **transport** change |
| Nom du cookie | `PRS_SESSION` (phase 4 : `__Host-prs-session` en prod) | Le préfixe `__Host-` exige `Secure` + `Path=/` + pas de `Domain` — incompatible dev HTTP, donc réservé au durcissement final |
| Résolution côté serveur | `BearerTokenResolver` custom : `Authorization` d'abord, **sinon le cookie** | Double support natif, un seul point de code, `oauth2ResourceServer` inchangé |
| CSRF | `CookieCsrfTokenRepository.withHttpOnlyFalse()` (cookie `XSRF-TOKEN` → en-tête `X-XSRF-TOKEN`) ; **exemption des requêtes porteuses d'un `Authorization`** | Le pattern double-submit est géré **automatiquement par Angular** (`HttpClient` XSRF, mêmes noms par défaut) → zéro code front ; les requêtes Bearer sont immunisées par nature → les 435 tests d'intégration (tous en Bearer) restent verts sans retouche |
| Déconnexion | Nouvel endpoint `POST /api/auth/logout` (route publique `/api/auth/**`) → `Set-Cookie` `Max-Age=0` | Un cookie `HttpOnly` ne peut pas être supprimé par le JS |
| `Secure` en dev | Toggle `app.auth.cookie.secure` (défaut `true`) | Chrome/Firefox acceptent les cookies `Secure` sur `localhost`, mais on garde une échappatoire de dev |

## 3. Prérequis structurel — même origine (bloquant, à faire en premier)

`SameSite=Strict` ⇒ le cookie **n'est envoyé que same-site**. Aujourd'hui `localhost:4200 → localhost:8080`
est **cross-origin** : la bascule cookie est inutilisable tant que front et API ne partagent pas l'origine.

- **Dev** : proxy Angular (`proxy.conf.json` : `/api` → `http://localhost:8080`) et passage du front à des
  **URLs relatives** (`/api/...`). Le navigateur ne voit qu'une origine (`localhost:4200`).
- **Prod** : le reverse proxy sert le front statique **et** `/api` sur le **même domaine** (c'est aussi lui
  qui porte TLS — HSTS déjà traité au point 4 de l'audit).
- Effet de bord : `CorsConfig` devient inerte (conservée pendant la transition, retirée en fin de projet).

## 4. Phases

### Phase 0 — même origine (front + infra, backend inchangé)
Front : proxy dev + URLs relatives. Infra : vhost prod unique. L'auth reste en Bearer.
**Recette** : l'application fonctionne à l'identique via `/api` relatif ; plus aucune requête cross-origin
dans l'onglet réseau ; le front peut supprimer l'URL absolue de l'API de sa config.

### Phase 1 — backend pose et accepte le cookie (rétro-compatible)
1. `login` : pose `PRS_SESSION` (`ResponseCookie` — `HttpOnly`, `Secure` selon `app.auth.cookie.secure`,
   `SameSite=Strict`, `Path=/`, `Max-Age = app.jwt.expiration-seconds`) **et renvoie encore `token`
   dans le corps** (`app.auth.cookie.exclusif=false`).
2. `BearerTokenResolver` custom (en-tête d'abord, cookie sinon) branché sur `oauth2ResourceServer`.
3. `POST /api/auth/logout` (public) → cookie vidé (`Max-Age=0`).
4. CSRF : activé avec `CookieCsrfTokenRepository.withHttpOnlyFalse()` + handler SPA Spring Security 6,
   `ignoringRequestMatchers(/api/auth/**)` **et** exemption des requêtes portant `Authorization`.
**Recette** : l'app actuelle (Bearer) fonctionne sans changement ; un client « cookie seul » (curl avec
pot de cookies) passe le circuit complet ; une mutation « cookie sans `X-XSRF-TOKEN` » → **403** ;
la même avec le jeton XSRF → OK ; suite d'intégration verte **sans retouche des tests**.

### Phase 2 — bascule du front
Suppression du stockage `localStorage` et de l'interceptor `Authorization` ; le cookie fait tout
(same-origin ⇒ envoyé automatiquement, pas besoin de `withCredentials`) ; XSRF automatique (Angular) ;
gestion 401 globale → redirection login ; bouton déconnexion → `POST /api/auth/logout`.
**Bénéfice immédiat** : les téléchargements par **ancre `<a download>`** (PV, pièces, rapports, gabarits)
fonctionnent nativement — plus besoin de fetch+blob pour transporter le Bearer.
**Recette** : aucun jeton visible dans Application/Storage ; multi-onglets OK (cookie partagé) ;
rafraîchissement de page OK ; téléchargements par ancre OK ; expiration (8 h) → 401 → écran de connexion.

### Phase 3 — coupure du jeton dans le corps
`app.auth.cookie.exclusif=true` → `LoginResponse.token = null` (champ retiré du DTO à la version
suivante). Le canal `Authorization: Bearer` **reste accepté** (tests, outils, intégrations).
**Recette** : la réponse de login ne contient plus de jeton ; l'app complète fonctionne ; suite verte.

### Phase 4 — durcissement (optionnel, prod)
Cookie renommé `__Host-prs-session` (prod TLS uniquement) ; à étudier selon besoin réel :
renouvellement glissant (ré-émission du cookie quand il reste < N min) et révocation serveur
(liste noire) — non requis par l'audit, le JWT 8 h actuel reste la référence.

### Rollback
Chaque phase est réversible seule : ph. 3 → repasser `exclusif=false` ; ph. 2 → redéployer le front
Bearer (le backend accepte toujours l'en-tête) ; ph. 1 → le cookie posé est ignoré si le front n'en
dépend pas encore ; ph. 0 → revenir aux URLs absolues.

## 5. Points d'attention

- **Navigation `SameSite=Strict`** : le chargement du SPA depuis un lien externe est une ressource
  statique (pas d'API) ; tous les appels API sont ensuite des XHR same-origin → le cookie part
  normalement. Pas de cas bloquant identifié dans l'app.
- **Expiration silencieuse** : identique à aujourd'hui (JWT 8 h) — le front doit traiter le 401 global
  (déjà prévu phase 2). Pas de refresh token dans ce périmètre.
- **Tests d'intégration** : inchangés (Bearer accepté) — c'est un critère de conception, pas un hasard.
- **Ordre impératif** : la phase 0 (même origine) conditionne tout ; poser le cookie avant elle ne
  servirait à rien (`SameSite=Strict` le bloquerait cross-origin).

## 6. Estimation / répartition

| Phase | Côté | Taille |
|---|---|---|
| 0 | Front + infra | Petite (proxy dev : minutes ; vhost prod : selon infra) |
| 1 | Backend | Moyenne (cookie + resolver + logout + CSRF + tests dédiés) |
| 2 | Front | Moyenne (suppression interceptor/stockage, 401 global, logout) |
| 3 | Backend | Petite (toggle + nettoyage DTO) |
| 4 | Backend/infra | Optionnelle |

**Signal de départ** : dites « go phase 1 » quand la phase 0 (proxy same-origin) est en place côté
front/infra — le backend livrera la phase 1 complète avec ses tests.
