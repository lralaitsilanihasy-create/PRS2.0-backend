# ADR-0003 : Migrations de schéma — adoption de Flyway

**Statut :** Adopté — implémentation en cours (plan de travaux 2026-08, LOT 2.1)
**Date :** 2026-08-26

## Contexte

Le schéma est aujourd'hui piloté par `spring.jpa.hibernate.ddl-auto=update` : Hibernate déduit les évolutions de schéma à partir des entités et les applique tout seul au démarrage. Ce mécanisme ne garde aucune trace rejouable : pour laisser malgré tout une piste aux développeurs, **32 scripts SQL manuels** ont été rédigés a posteriori dans `docs/migrations/` (nommés `AAAA-MM-JJ_sujet.sql`) — de la documentation narrative de ce que `ddl-auto` a fait, pas des migrations exécutées automatiquement. En complément, **3 classes `CommandLineRunner`** (`AssociationCcDispatchMigration`, `CategorieModePassationMigration`, `FormeMarcheMigration`) exécutent au démarrage, à chaque lancement de l'application, des reprises de données idempotentes sur les lignes historiques — du code Java qui tient lieu de migration SQL versionnée.

Limites de `ddl-auto=update` : aucun ordre d'exécution garanti, aucune suppression/renommage fiable de colonne (Hibernate sait surtout ajouter), aucune garantie de reproductibilité entre une base de développement fraîche et la base partagée de longue date (elles peuvent diverger silencieusement), et aucune source unique de vérité sur « quel est le schéma actuel » — il faut croiser l'inspection de la base vivante, les 32 scripts en texte libre et la mémoire de l'équipe.

## Décision

Adopter **Flyway**. Une baseline **V1** — un dump complet du schéma actuel (pris le 2026-08-26) — devient la première migration. Toute évolution future du schéma est un fichier SQL numéroté, ordonné, exécuté automatiquement au démarrage et tracé dans `flyway_schema_history`. `spring.jpa.hibernate.ddl-auto` passe d'`update` à **`validate`** : Hibernate se contente désormais de vérifier que les entités correspondent au schéma produit par Flyway, il ne le modifie plus jamais.

**Sort des 32 scripts manuels** (`docs/migrations/`) : gelés comme historique — déjà appliqués, déjà absorbés dans le dump V1. Ils ne sont ni convertis en migrations Flyway ni supprimés ; ils cessent simplement d'être le mécanisme vivant d'évolution du schéma. Toute nouvelle évolution est directement une migration Flyway, plus un script narratif séparé.

**Sort des 3 migrations Java au démarrage** : converties en migrations SQL Flyway ordinaires (des `UPDATE` versionnés). Ce sont des reprises de données ponctuelles et idempotentes : une fois exécutées par Flyway et tracées dans son historique, elles n'ont plus besoin de tourner à chaque démarrage — ce qui retire trois traitements qui s'exécutaient jusqu'ici (sans dégât, mais sans utilité non plus) à chaque lancement de l'application, pour toujours.

## Conséquences

**Plus facile :**
- Le schéma est reproductible sur n'importe quel environnement à partir d'une base vide.
- Une seule source de vérité sur l'état du schéma (`flyway_schema_history` + les fichiers de migration).

**À surveiller :**
- Discipline obligatoire : ne jamais modifier une migration déjà appliquée (Flyway en vérifie le checksum) — toute correction passe par une nouvelle migration.
- `ddl-auto=validate` change le confort quotidien : un développeur qui ajoute un champ JPA sans écrire la migration correspondante obtient un échec de démarrage immédiat au lieu d'une création de colonne silencieuse. C'est le but recherché, mais c'est un changement d'habitude à connaître avant d'en être surpris.

## Marche arrière

Revenir à `ddl-auto=update` est possible tant qu'aucune migration n'a encore été appliquée après la baseline V1 sur un environnement partagé. Une fois des migrations réelles empilées dessus, le retour en arrière suppose de réconcilier à la main ce que `ddl-auto` aurait fait entre-temps contre l'historique Flyway déjà posé — à trancher avant que ça arrive, pas après.
