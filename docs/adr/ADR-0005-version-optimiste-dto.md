# ADR-0005 : Version optimiste transmise dans les DTO, contrôlée en service

**Statut :** Adopté — livré
**Date :** 2026-08-27

## Contexte

Le verrou optimiste JPA (`@Version`, LOT 4, commit `f77dacd`, migration `V6`) protège les six entités
chaudes du circuit (`Dossier`, `Ppm`, `Marche`, `PvExamen`, `LettreRenvoi`, `DemandeRetrait`) contre
l'entrelacement de deux transactions concurrentes — le cas où deux requêtes HTTP écrivent la même
ligne au même instant. Il ne protège pas le cas, bien plus fréquent en usage réel, de **deux
formulaires ouverts dans deux navigateurs** : chaque `update()` charge l'entité, la modifie et
l'enregistre en « charger-puis-modifier » sur une entité managée — deux `PUT` séquentiels sur le même
enregistrement ne se voient jamais, chacun repartant de la version qu'il a chargée lui-même. Limite
documentée dans la javadoc de `VerrouOptimisteIntegrationTest`.

De plus, le 409 déjà rendu par `GlobalExceptionHandler#handleConflitVersion`
(`ObjectOptimisticLockingFailureException`) ne portait aucun `code` : le front ne pouvait pas le
distinguer des autres 409 (règle métier, doublon, clé étrangère) et affichait un toast générique
« Action impossible » plutôt qu'une invitation à recharger.

## Décision

Faire transiter la version JPA dans les DTO des cinq ressources qui exposent un `PUT` de formulaire :
`DossierDto`, `PpmDto`, `MarcheDto`, `PvExamenDto`, `LettreRenvoiDto`. Champ **`version`**
(`Integer`), toujours renseigné en sortie, **nullable en entrée** — choix de compatibilité ascendante
assumé plutôt que subi : l'exiger d'emblée aurait cassé la façade `/api/saisies` et tous les clients
existants d'un coup. Absent en entrée → comportement historique (dernier écrit gagne). Présent et
différent de la version en base → **409** avec le nouveau code dédié **`CONFLIT_VERSION`**
(`ConflitVersionException`, modèle exact de `VacancePrmpException`), sur le modèle du 409 déjà rendu
par le chemin transactionnel — les deux chemins produisent désormais le même corps.

Le contrôle est une **comparaison explicite en service**, avant les `set...()`, jamais un
`existing.setVersion(dto.getVersion())` : Hibernate ignore silencieusement l'écriture manuelle d'un
champ `@Version` sur une entité managée, ce qui rendrait le contrôle mort sans le signaler. Utilitaire
partagé `VerrouOptimiste.exigerVersionCourante(...)`, dans l'idiome de `ClePrimaire`. Les cinq
`update()` passent de `save()` à `saveAndFlush()` : l'incrément de `@Version` se fait au flush, et le
`PUT` doit renvoyer la version **incrémentée** — sans quoi le client reçoit l'ancienne version et
re-conflicte systématiquement au `PUT` suivant.

**Exclu, avec motif** : `DemandeRetraitDto` — la ressource n'a aucun `PUT` (création `POST`
multipart, décisions `POST /{id}/accepter` et `/refuser`, sans formulaire d'édition à protéger) ; le
verrou `@Version` transactionnel continue seul de couvrir ses écritures concurrentes.

## Conséquences

**Plus facile :**
- Le front distingue enfin ce 409 des autres (`code === 'CONFLIT_VERSION'`) et peut afficher un
  message dédié — « Donnée modifiée entre-temps » — au lieu du générique « Action impossible ».
- Deux formulaires ouverts en parallèle sur la même ressource ne s'écrasent plus silencieusement :
  le second enregistrement rencontre un 409 explicite, la donnée du premier survit.

**À surveiller :**
- **`version` reste nullable en entrée** par choix de compatibilité, pas par oubli : un futur
  durcissement (la rendre obligatoire) casserait la façade `/api/saisies` et tout script existant tant
  qu'ils ne portent pas le champ — décision à reprendre explicitement le jour où ce filet n'est plus
  nécessaire.
- Tout nouveau `update()` sur une de ces cinq ressources doit reprendre
  `VerrouOptimiste.exigerVersionCourante(...)` **avant** les `set...()`, et renvoyer la version
  post-flush — un oubli réintroduit silencieusement le trou que ce chantier ferme.
- `setVersion(...)` sur une entité managée reste un piège classique JPA (Hibernate l'ignore sans
  erreur) : la comparaison doit toujours être explicite en service, jamais déléguée à un `set` direct.

## Marche arrière

Retirer le champ `version` des cinq DTO, `VerrouOptimiste` et `ConflitVersionException`, et repasser
`handleConflitVersion` sans `code`. Le verrou `@Version` transactionnel (LOT 4, `V6`) reste en place
sans changement — seule la détection anticipée par HTTP disparaît, avec elle le cas « deux
navigateurs » qui redevient invisible.
