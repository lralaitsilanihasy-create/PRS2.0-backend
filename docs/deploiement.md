# Deploiement — consignes de mise en production

> Consolide les points de mise en production releves par l'audit du 27 aout 2026
> (`c:\dev\PRS2.0\AUDIT-GLOBAL-2026-08-27.md`, section 3.4 "Mise en production") et le travail du
> chantier `chantier/audit-2026-08` (lot E) qui en a leve une partie. Document neuf : rien n'existait
> avant pour rassembler ces points, ils etaient epars entre le code, les commentaires et les ADR.
> Aucun push (patron absent, 403) : ce fichier est committe en local comme le reste du chantier.

Ce document ne repete pas ce que `application.properties` dit deja en commentaire ; il explique
pourquoi chaque point compte et ce qu'il faut faire au moment de deployer.

---

## 1. Secret JWT — deja verrouille, rien a faire au deploiement sinon le fournir

`APP_JWT_SECRET` est obligatoire (>= 32 octets) : `SecurityConfig#jwtSecretKey` refuse de demarrer
l'application si la variable est absente, trop courte, ou si elle vaut encore l'ancien secret de dev
publie dans le depot (`dev-secret-please-change...`). C'est un fail-fast volontaire (ADR-0002) : mieux
vaut un demarrage qui echoue franchement qu'un serveur qui tourne avec un secret devine. En production,
generer un secret propre et le fournir par variable d'environnement (jamais commite, jamais celui du
`.env.local` de dev genere par `start-backend.ps1`).

## 2. Documentation d'API — `app.docs.publics=false` en production

`app.docs.publics` (par defaut `true` dans `application.properties`, commentaire "FALSE EN PRODUCTION"
deja present) pilote l'acces a Swagger UI et `/v3/api-docs` : ouvert a tous si `true`, reserve a
`ADMINISTRATEUR` si `false`. Avant l'audit, ces routes etaient publiques sans condition
d'environnement (`SecurityConfig` ne faisait aucune distinction) — expose le contrat d'API complet
(structure des DTO, endpoints, exemples) a quiconque trouve l'URL. **A positionner explicitement a
`false` en production** (le defaut `true` reste pratique en dev pour explorer l'API sans jeton Admin).

## 3. Cookie de session — `Secure` toujours actif

`app.auth.cookie.secure` vaut `true` par defaut (et l'est dans le seul `application.properties` du
depot) : le cookie `PRS_SESSION` est deja pose avec `Secure`. Ce n'est **pas** une bascule automatique
par profil Spring — une seule propriete, a positionner manuellement si un environnement en a besoin
autrement (Chrome/Firefox acceptent `Secure` sur `localhost` en dev, donc il n'y a normalement pas
besoin de la desactiver). Rien a changer au deploiement sauf verification que la valeur n'a pas ete
alteree localement.

## 4. CSP du document frontend — a la charge du serveur qui l'heberge

Le backend **ne sert pas le frontend** (aucun `WebMvcConfigurer` ne declare de ressource statique,
aucun controleur ne renvoie de HTML) : c'est une API separee, consommee par un frontend Angular compile
et deploye ailleurs. La CSP posee par `SecurityConfig` (`default-src 'self'; object-src 'none';
frame-ancestors 'self'`, plus HSTS) protege les **reponses JSON de l'API** — elle ne couvre en rien les
pages HTML du frontend.

**Aucune CSP n'existe aujourd'hui cote frontend** (pas de meta tag dans `index.html`, pas de
configuration serveur front dans le depot). C'est au serveur qui heberge le build Angular (reverse
proxy, nginx, service statique) de la poser, par exemple :

```
Content-Security-Policy: default-src 'self'; object-src 'none'; frame-src 'self' blob:; frame-ancestors 'self'
```

Le `frame-src 'self' blob:` est **indispensable** : les apercus PDF du frontend (PV, lettres de renvoi,
pieces jointes, rapports) sont rendus dans une `<iframe>` dont le `src` est une URL `blob:` construite
par `core/securite/fichiers-surs.ts` (`urlBlobSure()` / `ouvrirBlobSur()`), consommee par une vingtaine
d'ecrans (`inscriptions-admin`, `dossier-consultation`, `detail-pv-modal`...). Omettre `blob:` de
`frame-src` casse silencieusement tous ces apercus — **tester explicitement l'ouverture d'un PDF avant
la mise en production** apres avoir pose la CSP, ce n'est pas le genre de regression qu'un build vert
detecte.

## 5. Reverse proxy — ecraser `X-Forwarded-For`, jamais le completer

Le limiteur de debit (`LoginRateLimiter`, lot E) cle ses quotas sur `HttpServletRequest.getRemoteAddr()`
apres reecriture par le `ForwardedHeaderFilter` de Spring (`server.forward-headers-strategy=framework`),
qui reporte le `X-Forwarded-For` recu du proxy. Le code documente lui-meme le risque
(`AuthController#adresse`) : si le reverse proxy **complete** l'en-tete au lieu de l'**ecraser**, un
client forge `X-Forwarded-For` a chaque requete et s'attribue une adresse neuve — donc un quota neuf —
a volonte, ce qui vide le limiteur de son effet. **En production, configurer le reverse proxy pour
qu'il pose son propre `X-Forwarded-For` a partir de l'adresse TCP reelle du client, sans jamais faire
confiance a un en-tete entrant.** Ce n'est pas une option de configuration Spring : c'est une
responsabilite du proxy, a verifier explicitement (une requete avec un `X-Forwarded-For` force manuel
ne doit pas passer telle quelle jusqu'au backend).

## 6. Limiteur de debit — en memoire, donc mono-instance

`LoginRateLimiter` stocke ses compteurs dans des `ConcurrentHashMap` en memoire JVM (pas de table, pas
de Redis) : un choix assume tant que PRS 2.0 tourne en **un seul exemplaire**. Consequence directe pour
le deploiement :
- un redemarrage du service **vide tous les compteurs** (pas de persistance, attendu) ;
- **plusieurs instances derriere un repartiteur de charge ne partagent aucun etat** — chaque instance
  compte pour elle-meme, ce qui multiplie de fait le quota reel par le nombre d'instances (5 echecs
  par instance, pas 5 echecs globaux).

Si un deploiement multi-instances devient necessaire, ce limiteur doit etre remplace par un stockage
partage (Redis) ou une limitation portee par le repartiteur lui-meme — **pas un correctif a faire a la
legere au dernier moment**, c'est un changement d'architecture du composant.

## 7. Journalisation SQL — `show-sql` a false, ne pas le rallumer en production

`spring.jpa.show-sql=false` (corrige par l'audit, lot E — c'etait `true` avant). Le laisser `true` en
production journalise **chaque requete SQL** en clair dans les logs applicatifs : bruit disqualifiant
sur un parc de production, et une fuite potentielle de donnees selon la politique de retention des
logs. Ne le remettre a `true` que ponctuellement, en diagnostic local, jamais par defaut.

## 8. Fuseau horaire du serveur — a figer sur celui de Madagascar

Aucun fuseau horaire n'est fixe explicitement dans le code (pas de `-Duser.timezone`, pas de `ZoneId`
code en dur) : `AlerteScheduler` utilise une horloge injectee (`Clock.systemDefaultZone()`, testable),
mais `MandatService` — qui porte la regle metier des paliers de mandat PRMP (`ACTIF` / `EN_TRANSITION` /
`ACHEVE`, calculs J-90/J-30/J-7) — appelle `LocalDate.now()` **sans horloge injectee** en une dizaine
d'endroits, ce qui resout implicitement le fuseau horaire de la JVM hote. **Le serveur (ou le
conteneur) qui heberge le backend doit etre regle sur `Indian/Antananarivo` (UTC+3)** : un serveur sur
un autre fuseau decalerait potentiellement d'un jour le basculement de statut d'un mandat autour de
minuit, avec des effets de bord sur l'acces des comptes PRMP (reactivation, expiration).

## 9. Mot de passe PostgreSQL — a faire tourner, jamais commite

`spring.datasource.password=${DB_PASSWORD:}` n'a pas de defaut en clair dans le depot ; en dev,
`start-backend.ps1` le recupere a l'execution directement depuis le conteneur Docker existant
(`docker exec prs20-db printenv POSTGRES_PASSWORD`), donc rien n'est commite. C'est la reserve S1 de
l'audit frontend precedent, encore ouverte : **le mot de passe Postgres n'a jamais fuite dans
l'historique git**, mais sa rotation en production reste une tache manuelle a faire au moment du
deploiement (et periodiquement ensuite), pas quelque chose que le code automatise.

## 10. Asset frontend `public/mef-logo.png` — a fournir manuellement sur chaque poste de build

`frontend/public/mef-logo.png` est **volontairement non versionne** (present dans `.gitignore` du
depot frontend) mais **reference en dur** par l'ecran de connexion (`login.html`). Consequence : tout
build fait depuis un poste qui n'a pas recu ce fichier a la main produit une application qui fonctionne
mais affiche un logo casse, **silencieusement** — aucune erreur de build, aucun test ne le detecte.
**Avant tout build de production, verifier explicitement la presence du fichier** sur le poste ou le
pipeline qui build ; il ne suffit pas de cloner le depot.

## 11. Donnees de reference minimales — a charger AVANT le seed de comptes

`CompteSeeder` (`app.seed.comptes.enabled=true`, dev uniquement) ne cree que les **comptes** de
`t_compte_auth` : il parcourt `tr_controleur` et `t_prmp` et ouvre un compte par fiche trouvee. **Sur
une base vierge il cree donc 0 compte**, sans erreur ni avertissement autre que son propre
« 0 compte(s) ... cree(s) » — l'application demarre, le seed a bien tourne, et personne ne peut se
connecter (constat de recette du 27/08/2026). Flyway ne comble pas ce trou : `V1` cree le **schema**,
pas le contenu metier. **Charger d'abord les donnees de reference minimales** — profils
(`tr_profile`), puis fiches controleur (`tr_controleur`) et/ou PRMP (`t_prmp`) — et seulement ensuite
activer le seed, qui leur ouvrira les comptes.

---

*Rappel de contexte (comme dans tous les plans de ce chantier) : push impossible vers les deux depots
(403, proprietaire absent) — ce document, comme le reste du chantier, existe en commit local
uniquement.*
