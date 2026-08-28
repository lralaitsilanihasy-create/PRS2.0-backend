# ADR-0004 : Tests d'intégration — bascule de H2 vers Testcontainers PostgreSQL 16

**Statut :** Adopté — implémentation en cours (plan de travaux 2026-08, LOT 2.2)
**Date :** 2026-08-26

## Contexte

Les tests d'intégration tournent aujourd'hui contre une base **H2 en mémoire** (`src/test/resources/application.properties`, `MODE=PostgreSQL`, `ddl-auto=create-drop`). Pour que des constructions propres à PostgreSQL fonctionnent malgré tout sous H2, la configuration de test recrée à la main : un domaine `text` (H2 n'a pas le type `text` de PostgreSQL), un domaine `bytea`, et **4 séquences** (`seq_dossier`, `seq_ppm`, `seq_marche`, `seq_reception`) qui doivent rester des copies conformes de séquences qui existent réellement côté PostgreSQL. Chaque fois qu'une migration PostgreSQL ajoute ou modifie une séquence, un type ou une contrainte spécifique, quelqu'un doit penser à répercuter le même changement à la main dans cette chaîne d'initialisation H2 — sans quoi le schéma de test diverge silencieusement du schéma réel.

**H2 valide le code Java, pas le schéma PostgreSQL réel.** `ddl-auto=create-drop` masque exactement la même catégorie de dérive que Flyway (ADR-0003) referme côté production : la suite ne prouve jamais que les entités **plus** les vraies migrations Flyway produisent un schéma PostgreSQL qui fonctionne — elle prouve seulement que les entités sont assez cohérentes entre elles pour qu'H2 les simule.

Le poids de ce socle est concentré : **422 des 444 tests du dépôt** vivent dans une seule classe, `CnmWorkflowIntegrationTest` (10 526 lignes) — changer le socle d'intégration déplace donc d'un coup l'essentiel de la suite.

## Décision

Basculer les tests d'intégration vers **Testcontainers**, avec un vrai conteneur **PostgreSQL 16** (la version de production). Introduire un socle commun `AbstractIntegrationTest` qui démarre un conteneur **singleton** — un seul conteneur pour toute l'exécution de la JVM de test, réutilisé entre les classes plutôt qu'un conteneur par classe — pour garder un temps d'exécution raisonnable. Les tests appliquent alors les **vraies migrations Flyway** sur le conteneur, et non un schéma recréé par Hibernate.

## Conséquences

**Plus facile :**
- La suite prouve enfin que le schéma réel (migrations Flyway) et le dialecte réel (SQL spécifique PostgreSQL, séquences, contraintes) fonctionnent ensemble.
- Plus de domaines ni de séquences à mirroiter à la main dans la configuration de test — une migration oubliée dans le miroir H2 ne peut plus masquer un vrai bug.

**À surveiller :**
- Docker doit être disponible partout où les tests tournent — déjà vrai en CI (runners `ubuntu-latest`, Docker préinstallé) et en local (déjà requis pour le conteneur `prs20-db`).
- Le démarrage d'un conteneur est plus lent qu'une base en mémoire ; le motif singleton limite ce coût à une seule fois par exécution de suite, pas par classe de test.
- Le découpage de `CnmWorkflowIntegrationTest` (LOT 2.3) devient plus pressant une fois ce socle en place : un fichier de 10 526 lignes sur un socle plus coûteux à démarrer coûte plus cher à faire évoluer qu'avant.

## Marche arrière

Revenir à H2 : retirer la dépendance Testcontainers, restaurer `application-test.properties` en configuration H2. Fait perdre la garantie de fidélité au dialecte réel et réintroduit la nécessité de mirroiter à la main les domaines et séquences PostgreSQL.

## Révision (2026-08-28) — la version est PostgreSQL 18, et c'était l'ADR qui avait tort

**Cette décision a annoncé trois versions différentes sans que personne ne vérifie laquelle
tournait réellement en production.** Le titre et la section « Décision » ci-dessus disent
**16** ; l'aboutissement dit **17** ; le socle de test appliquait **17** ; la CI a été montée
en **18**. La production, elle, tourne en **18** — confirmé par le pilote le 28/08.

L'argument central de l'ADR — « un vrai conteneur PostgreSQL, la version de production » — était
donc juste dans son principe et faux dans son chiffre, depuis le premier jour.

**Ce qui change :**

- `AbstractIntegrationTest.IMAGE_POSTGRES` a pour défaut `postgres:18`. C'est le **seul** endroit
  où la version est déclarée.
- La surcharge `PRS_TEST_PG_IMAGE: postgres:18` a été retirée du workflow. Elle y avait été posée
  le 28/08 comme instrument de diagnostic, puis conservée comme choix ; devenue redondante, elle
  ne subsiste plus que comme réglage — utile pour reproduire un défaut sur une autre version, pas
  pour porter la configuration de référence.
- Le titre et la section « Décision » ne sont pas réécrits : un ADR est un enregistrement daté,
  pas un document vivant. Cette révision fait foi.

**Ce que l'épisode a coûté et enseigné.** Le passage de la CI en 18 visait à trancher un
diagnostic : deux tests de `MiseAJourPpmIntegrationTest` répondaient 409 au lieu de 201. La
version du moteur n'était pas en cause — le coupable était Word piloté en synchrone dans une
transaction métier (`c2fdeb1`, troisième occurrence du même défaut). Une version dupliquée en
trois endroits a servi de fausse piste à un bug qui n'avait aucun rapport avec elle.

**À surveiller :** la clause « Docker doit être disponible partout où les tests tournent » s'est
révélée fausse en local — aucun poste de développement n'a Docker. D'où l'aiguillage
`PRS_TEST_DB_URL` (`5d6651f`), qui branche la suite sur un PostgreSQL local. La CI, elle, reste
sur Testcontainers : c'est le mode de référence.

## Aboutissement (2026-08-27)

Décision entièrement livrée : bascule H2 → Testcontainers PostgreSQL 17 + Flyway (`d557cef`),
puis découpage de la classe unique en **18 classes par domaine** sur le socle
`CnmIntegrationTestSupport` (`587aacc`) — la mention « 422 des 444 tests dans une seule classe »
ci-dessus décrit l'état d'avant. La bascule a immédiatement révélé 4 fixtures violant la contrainte
`t_pv_examen_cosignataire_check` du schéma réel, invisibles sous H2 — la classe de défauts exacte
que cette décision visait.
