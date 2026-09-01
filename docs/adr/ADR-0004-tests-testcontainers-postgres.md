# ADR-0004 : Tests d'intégration — bascule de H2 vers Testcontainers PostgreSQL

**Statut :** Adopté — révisé le 2026-08-28, complété le 2026-09-01 (sections datées en fin de document,
par ordre chronologique)
**Date :** 2026-08-26

> ⚠️ **La version ne se lit pas dans ce document.** Elle est déclarée à un seul
> endroit exécutable : le défaut d'`AbstractIntegrationTest.IMAGE_POSTGRES`.
>
> **Tout chiffre cité ici est daté**, y compris dans la révision : 16 dans la
> décision d'origine, 17 dans l'aboutissement, 18 dans la révision du 28/08. Ce
> sont des étapes, conservées parce qu'un ADR enregistre ce qui a été décidé
> quand — pas ce qui est vrai aujourd'hui. Pour connaître la version en vigueur,
> ouvrir le code.
>
> Le titre a perdu son numéro pour cette raison : il en portait un (16) qui n'a
> jamais été implémenté, et qui a survécu deux montées de version.

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

## Aboutissement (2026-08-27)

Décision entièrement livrée : bascule H2 → Testcontainers PostgreSQL 17 + Flyway (`d557cef`),
puis découpage de la classe unique en **18 classes par domaine** sur le socle
`CnmIntegrationTestSupport` (`587aacc`) — la mention « 422 des 444 tests dans une seule classe »
ci-dessus décrit l'état d'avant. La bascule a immédiatement révélé 4 fixtures violant la contrainte
`t_pv_examen_cosignataire_check` du schéma réel, invisibles sous H2 — la classe de défauts exacte
que cette décision visait.

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
- La section « Décision » n'est pas réécrite : un ADR est un enregistrement daté, pas un document
  vivant. Cette révision fait foi. Le **titre**, lui, a perdu son numéro de version — il n'était
  pas un enregistrement mais une étiquette, et il annonçait un chiffre (16) qui n'a jamais été
  implémenté.

**La règle à tenir, plus importante que le chiffre du jour.** Aligner les cinq endroits sur « 18 »
aurait recréé le même piège pour la prochaine montée de version. La version se déclare à **un seul
endroit exécutable** — le défaut d'`AbstractIntegrationTest` — et la prose y renvoie au lieu de la
répéter. Un document qui cite un numéro de version le périme tôt ou tard ; celui qui pointe vers
la source reste juste.

**Ce que l'épisode a coûté et enseigné.** Le passage de la CI en 18 visait à trancher un
diagnostic : deux tests de `MiseAJourPpmIntegrationTest` répondaient 409 au lieu de 201. La
version du moteur n'était pas en cause — le coupable était Word piloté en synchrone dans une
transaction métier (`c2fdeb1`, troisième occurrence du même défaut). Une version dupliquée en
trois endroits a servi de fausse piste à un bug qui n'avait aucun rapport avec elle.

**À surveiller :** la clause « Docker doit être disponible partout où les tests tournent » s'est
révélée fausse — le poste de développement sur lequel la suite a été reprise n'a pas Docker, et son
installation y exige une élévation et un redémarrage. D'où l'aiguillage `PRS_TEST_DB_URL`
(`5d6651f`), qui branche la suite sur un PostgreSQL local. La CI, elle, reste sur Testcontainers :
c'est le mode de référence.

*(Rectification du 2026-09-01 : cette phrase affirmait « aucun poste de développement n'a Docker ».
Un seul poste avait été constaté ; « aucun » revendiquait une connaissance de tous. Le fait observé
suffisait à justifier l'aiguillage, la généralisation n'apportait rien et pouvait égarer.)*

## Note (2026-09-01) — ce que cette décision ne prouve pas

Une semaine d'usage a montré que la promesse de cet ADR est **juste et plus étroite que ce qu'on en
retient**. « La suite prouve que le schéma réel et le dialecte réel fonctionnent ensemble » : oui.
Elle ne prouve pas que le **canal HTTP** fonctionne.

Trois défauts sont passés au travers, tous trouvés en recette, aucun par la suite :

| Défaut | Pourquoi la suite ne pouvait pas le voir |
|---|---|
| Garde CSRF rendant 401 au lieu de 403 (`624982d`) | MockMvc ne rejoue pas le ré-aiguillage `ERROR` du conteneur vers `/error` — il voyait déjà le 403 attendu |
| Rotation du jeton CSRF en rafale concurrente (`a1cdd56`) | La suite émet des requêtes séquentielles ; la course n'existe qu'avec un vrai navigateur |
| `PUT /api/examen-pieces` nominal jamais couvert | Trou de couverture ordinaire — le seul test du verbe vérifiait un 409, jamais le cas passant |

Les deux premiers ne sont pas des oublis : ce sont les **limites du harnais**. Testcontainers a
supprimé l'écart entre le schéma de test et le schéma réel ; il ne dit rien de l'écart entre le
serveur de test et le serveur réel. Le premier de ces défauts était même **documenté en commentaire**
dans notre propre suite depuis le 27/08, décrit comme une fatalité du conteneur — il n'en était pas
une, et personne n'a rouvert la question tant que le vert de la CI paraissait suffire.

**À retenir avant d'invoquer la CI comme arbitre :** verte, elle atteste le code et le schéma. Sur le
comportement du canal — codes rendus au client, concurrence, sessions — elle est muette, et une recette
navigateur reste nécessaire.
