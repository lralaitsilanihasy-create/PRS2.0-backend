# ADR-0001 : Packaging jar (Tomcat embarqué) et Java 21

**Statut :** Adopté
**Date :** 2026-08-26

## Contexte

Le `pom.xml` déclarait `<packaging>war</packaging>`, une dépendance `spring-boot-starter-tomcat` en `scope=provided`, et une classe `ServletInitializer` (`extends SpringBootServletInitializer`) — le montage classique d'un déploiement dans un conteneur Tomcat externe. `java.version` était fixé à 17.

Or aucun Tomcat externe n'a jamais existé dans l'exploitation réelle du projet : le backend démarre depuis toujours via `mvnw spring-boot:run` en développement. Le mode war n'était utilisé nulle part — c'était un reliquat du starter Spring Initializr d'origine, jamais retiré. Le `CLAUDE.md` du projet affirmait déjà « projet jar avec Tomcat embarqué » dans ses notes pour Claude : l'écart entre le `pom.xml` réel (war) et la documentation était une source de confusion silencieuse pour quiconque reprenait le dossier.

## Décision

Repasser en packaging **jar** par défaut (retrait de `<packaging>war</packaging>`, de la dépendance `spring-boot-starter-tomcat` en `provided`, suppression de `ServletInitializer`), Tomcat restant embarqué dans le jar exécutable comme le fait Spring Boot nativement. `java.version` fixé à **21** (LTS) : aucune API dépréciée du projet ne bloquait la bascule depuis 17, et 21 ouvre l'accès aux threads virtuels et au pattern matching récent pour les futurs chantiers d'E/S (génération de documents, appels réseau).

## Conséquences

**Plus facile :**
- Un seul artefact autonome (`java -jar prs.jar`), aucun serveur d'application externe à installer ou maintenir — conforme à ce qui a toujours été exploité en pratique.
- Le `CLAUDE.md` décrit enfin l'état réel du projet ; plus d'écart doc/code sur ce point.

**À surveiller :**
- Ne jamais réintroduire un `web.xml` ou un `ServletInitializer` (déjà noté dans les « Notes pour Claude » du `CLAUDE.md`) : ce serait un retour vers un mode qui n'a jamais été réellement exploité.
- S'assurer qu'un JDK 21 est disponible sur tout poste ou pipeline qui construit le projet.

## Marche arrière

Remettre `<packaging>war</packaging>`, restaurer la dépendance Tomcat `provided` et `ServletInitializer` (récupérables dans l'historique git, avant le commit `e28278e`). Coût nul techniquement, mais aucun besoin de déploiement WAR n'a jamais été identifié sur ce projet.
