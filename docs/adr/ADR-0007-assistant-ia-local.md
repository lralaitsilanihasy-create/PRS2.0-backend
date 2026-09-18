# ADR-0007 : Assistant IA local — modèle hors du JVM derrière une API compatible OpenAI, réponses sur corpus seul

**Statut :** Adopté
**Date :** 2026-09-18
**Origine :** demande du pilote (2026-09-17) ; cadrage `docs/plan-assistant-ia.md`, lot 1 livré sur la
branche `chantier/assistant-ia`.

## Contexte

Le pilote veut un assistant IA **local** — aucune donnée de marché ne doit sortir du réseau — qui aide
les dix profils, **lit sans modifier** et **n'est pas décisionnaire**. Trois contraintes pèsent sur
l'architecture :

- **Le matériel n'est pas arrêté.** Le développement se fait sur le poste du pilote (RTX 5050, 8 Go de
  mémoire graphique) avec `qwen3.5:9b-q4_K_M` ; la production aura « un modèle plus puissant » sur un
  serveur à définir. Le code ne doit pas dépendre du modèle.
- **Les habilitations sont fines** (localité, périmètre PRMP, rattachement) et l'audit du 2026-09-14
  avait classé une fuite PRMP en critique. Un assistant branché sur la base les contournerait toutes.
- **Un modèle de langage invente.** Interrogé sans document, le modèle de développement cite le droit
  français (« code de la commande publique ») ; sur un texte de tableau mal extrait, il a inversé les
  seuils des offres anormalement hautes et basses (recette du 2026-09-18).

## Décision

1. **Le modèle tourne hors du JVM**, servi par un serveur d'inférence à l'**API compatible OpenAI**
   (`POST /chat/completions` en flux) : Ollama en développement, vLLM ou Ollama en production. Le
   backend n'en connaît que `app.ia.base-url` et `app.ia.modele` (`ClientModeleIa`). Changer de
   modèle, ou de serveur, ne recompile rien.
2. **Le lot 1 ne lit aucune donnée métier.** Il répond sur un **corpus documentaire** identique pour
   tous — le manuel de contrôle a priori de la CNM et `docs/regles-gestion.md` — découpé en passages
   (page du PDF, section du Markdown) et interrogé par une recherche lexicale BM25 (`IndexLexicalIa`).
   Il n'y a donc pas de périmètre à protéger. Les lots suivants liront des données **uniquement** par
   une liste blanche de méthodes de contrôleurs, où vivent les gardes `@PreAuthorize`
   (`docs/plan-assistant-ia.md` §2) — jamais par la base ni par un service non gardé.
3. **Le modèle ne répond que sur extraits, et les cite.** La consigne l'y oblige ; sans passage
   pertinent, la réponse est un texte fixe et le modèle n'est pas appelé. Le flux SSE envoie les
   extraits **avant** la réponse, pour que chaque citation `[n]` soit vérifiable à l'écran.
4. **Chaque échange est journalisé** dans `t_audit_log` (`NOM_TABLE = assistant_ia`) : qui, quand,
   quel modèle, question, réponse, sources.
5. **Désactivé par défaut** (`app.ia.actif=false`).

Options écartées :

- **Modèle embarqué dans le JVM** (bibliothèque d'inférence Java) : il lierait le cycle de vie du
  modèle à celui de l'API, imposerait le GPU à la machine qui sert l'API, et figerait le moteur.
- **Recherche par vecteurs (`pgvector`)** : extension absente de l'image PostgreSQL 18, second modèle
  à servir, schéma de production touché — pour un corpus de quelques centaines de passages où la
  recherche lexicale trouve la bonne page 38 fois sur 40 (batterie `AssistantIaBatterieCorpusTest`).
  À reconsidérer si ce taux baisse.
- **Text-to-SQL** : refusé par principe — un utilisateur ne doit pas pouvoir influencer une requête.

## Conséquences

**Plus facile :**
- Changer de modèle : une propriété, puis la batterie de référence
  (`AssistantIaBatterieModeleTest`, sur demande) pour vérifier qu'on ne régresse pas.
- Ajouter un document au corpus : une entrée `app.ia.corpus[n]` (PDF ou Markdown).
- Vérifier une réponse : l'extrait cité est à un clic.

**Plus difficile / à surveiller :**
- **Un service de plus à exploiter** : le serveur d'inférence, joignable par le backend seul (son API
  n'a pas d'authentification) — `docs/deploiement.md` §12.
- **La qualité dépend de l'extraction du texte.** Le manuel est fait de tableaux : l'extraction PDF
  suit l'ordre des cellules (pas le tri par position, qui fondait les colonnes), et chaque page emporte
  le début de la suivante (une liste coupée par un saut de page arrivait tronquée). Un nouveau
  document devra être vérifié de la même façon.
- **Le contexte du modèle doit tenir cinq extraits** : 8 192 jetons au minimum ; le défaut d'Ollama
  (4 096) tronque sans erreur.

**Marche arrière :** `app.ia.actif=false` masque l'assistant et coupe son API, sans migration à
défaire — le lot 1 n'a créé aucune table. Les lignes `assistant_ia` du journal d'audit restent.
