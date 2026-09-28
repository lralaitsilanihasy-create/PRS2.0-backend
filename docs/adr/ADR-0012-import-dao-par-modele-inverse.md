# ADR-0012 : L'import du DAO propose, la fiche décide — la lecture par modèle inversé

**Statut :** Adopté
**Date :** 2026-09-28
**Origine :** demande front `frontend/docs/demande-backend-2026-09-28-import-dao.md` (décisions du pilote du 28/09 :
« suivre les recommandations », ancrages tirés des modèles du lot D) ; plan `docs/plan-2026-09-28-import-dao.md` ;
prototype et mesure `frontend/scripts/import-dao/` (`lire.mjs` = spécification de l'algorithme).

## Contexte

Le 22/09, le pilote avait tranché : **le DAO est un formulaire, jamais un import de PDF**. La fiche marché est saisie
bloc par bloc, et c'est elle qui produit le DAO (lot 2a, puis le document type officiel rempli au lot D, ADR-0011).

Le 28/09, il demande de pouvoir **partir d'un DAO déjà rédigé** : « importer le document DAO pour alimenter les données
de la fiche à la place de la saisie si on a ce document ; sinon, on procède à la saisie ». Un DAO Word écrit sur le
document type de l'ARMP contient, au mot près, le texte fixe que les modèles du lot D décrivent. Ce qui occupe la place
d'un jeton est une valeur de la fiche, et la rédaction retenue d'une section conditionnelle dit une réponse du cadrage.

Le risque est d'écrire dans la fiche une valeur fausse que personne n'a vue. Le front a mesuré l'algorithme sur neuf
passes (aller-retour de la fiche 27, puis bruit simulé : typographie, paragraphes fusionnés, paragraphes ajoutés). Il
n'a proposé **aucune valeur fausse en confiance haute** (315 justes) ni en moyenne (213 justes). Les 7 fausses sont
toutes en basse.

## Décision

1. **Renversement partiel du 22/09 : l'import propose, la fiche décide.** La fiche reste le formulaire et la seule source
   de vérité. La lecture (`POST /api/fiches-marche/{idDmc}/import`) n'écrit rien. La PRMP coche ce qu'elle retient, et
   l'écriture (`PUT …/import/appliquer`) n'écrit que cela, d'un seul coup, après la même validation que la saisie.
2. **Lecture par modèle inversé, avec les modèles du lot D.** Ce sont les fichiers de commande déjà chargés pour la
   production (`ModelesDao`). Un modèle se lit dans un document Word si sa forme et sa catégorie sont couvertes. Il n'y a
   pas de second jeu d'ancrages à entretenir : un document type ajouté pour la production devient importable.
3. **L'algorithme est celui du front, porté tel quel** (`LectureDao`, classe pure). Il garde les mêmes étapes, les mêmes
   seuils et les mêmes niveaux de confiance. La parité a été vérifiée sur les documents de la fiche 27 : même découpage en
   351 paragraphes, et mêmes propositions, confiances, réponses, ambiguïtés et champs non trouvés.
4. **Le plan fait foi.** Un champ repris du PPM n'est jamais proposé. Une valeur lue qui en diffère est rendue en
   *divergence*. Ne sont jamais proposés non plus les champs calculés, les paramètres internes et les pièces.
5. **Aucune proposition ne contourne la saisie.** Chaque valeur passe par `normaliser`. La condition d'affichage du champ
   est évaluée sur le cadrage de la fiche, complété des réponses déduites. Un refus devient une *anomalie* de la
   proposition, jamais une proposition valide.
6. **Ambiguïté signalée, jamais tranchée.** Deux jetons seuls dans le même intervalle ne sont pas départagés, par exemple
   `B07-DE-02` et `B07-DE-03`, dont le choix dépend d'une réponse que rien n'imprime. La même règle vaut pour un champ lu
   deux fois différemment.
7. **Le fichier n'est pas conservé.** Il est lu en mémoire par POI, qui n'exécute rien et garde contre les archives
   piégées, puis oublié. Seuls le nom et le début de l'empreinte SHA-256 vont au journal (`FICHE_IMPORTEE`), à
   l'application.

## Conséquences

- Une PRMP qui a déjà son DAO pré-remplit la fiche en une passe, puis relit les propositions de confiance moyenne et basse.
  Ce qui n'est pas trouvé reste à saisir, et `nonTrouves` le lui dit.
- La qualité de l'import dépend de la **fidélité du DAO au document type**. Un document libre ne donne presque rien. Il
  reçoit un avertissement « hors gabarit » sous 30 % du modèle reconnu.
- Trois éléments sont renvoyés à des lots ultérieurs :
  - le PDF texte, qui demande une extraction de texte ;
  - les champs par lot, où le lot se lira dans l'intitulé « Lot n° 1 : … », avec les fournitures (lot D2) ;
  - l'OCR, écarté.
- L'algorithme évolue **d'abord côté front**, avec sa mesure, puis se reporte ici. La vérification de parité, sur les
  mêmes documents, se refait à chaque report.

## Alternatives écartées

- **Écrire directement ce qui est lu** : c'est le risque d'une valeur fausse que personne n'a vue. La décision du 22/09
  tenait pour cette raison, et la revue par la PRMP est ce qui la lève.
- **Des ancrages propres à l'import** (libellés, expressions régulières par champ) : ce serait un second référentiel à
  tenir aligné sur les documents types. Le modèle du lot D est déjà l'ancrage exact.
- **Un modèle de langue ou l'OCR** : c'est une valeur non traçable jusqu'au texte fixe qui la borne, sans niveau de
  confiance mesurable. La mesure du front ne vaut que pour la lecture par modèle inversé.

## Marche arrière

Retirer les deux routes et `LectureDao` / `ImportDaoService`. Aucune donnée ne dépend de l'import : les valeurs écrites
sont des valeurs de saisie ordinaires, et le journal garde la trace des imports passés.
