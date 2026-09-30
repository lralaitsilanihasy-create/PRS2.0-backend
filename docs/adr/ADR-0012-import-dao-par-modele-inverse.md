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

## Complément du 2026-09-29 — fournitures, PDF et trois règles de prudence

Demande front `frontend/docs/demande-backend-2026-09-29-lot-d2-fournitures.md`, §B1 et §B5. La lecture a été mesurée
sur le vrai dossier 2463, adapté du document type. Le front n'y a trouvé que 2 valeurs sur 62, mais aucune fausse en
confiance haute et 6 réponses de cadrage toutes justes.

- **Les fournitures s'importent.** Les trois modèles du lot D2 (DPAO, CCAP, AE) sont lus comme ceux du contrat-cadre.
  Leurs marqueurs de cellule et de rangée délimitent les sections comme au rendu. Chaque paragraphe de cellule est une
  unité, et une tabulation dans un paragraphe n'y coupe rien.
- **`{{CODE.parLot}}` se lit à l'envers.** L'énumération « Lot n° k : valeur » devient la proposition du champ pour le
  lot k.
- **Trois règles de prudence**, portées telles quelles :
  - un paragraphe dont le texte fixe n'a aucune lettre (« {{CODE}}. ») est un jeton seul, jamais un motif ;
  - une ancre de moins de 8 lettres ne donne jamais la confiance haute ;
  - un paragraphe n'atteste ses sections que s'il a au moins 20 lettres de texte fixe et qu'aucun paragraphe de même texte
    n'existe hors de ces sections.
- **Le PDF « texte » est lu** avec PDFBox, déjà une dépendance : texte horizontal dans le cadre de la page, filigrane
  écarté, colonnes séparées, paragraphes refaits par l'interligne, en-têtes, pieds et numéros de page écartés. Un PDF sans
  texte est refusé : il n'y a pas d'OCR.
- **La parité est vérifiée de nouveau** sur trois entrées : les DPAC et AE `.docx` de la fiche 27, le PDF réel du 2463 et
  le rendu brut des trois modèles D2. L'extraction est identique, et la lecture aussi aux écarts documentés près.
- **La forme et la catégorie restent au plan.** Une rédaction qui les dit autrement est une divergence, jamais une réponse
  de cadrage.

Deux limites sont mesurées ici et signalées au front (la première est levée le soir même, voir plus bas) :
- Sur les PDF que le serveur produit lui-même (OpenPDF), la lecture du front détache la première lettre de chaque ligne
  (« A ttestations… ») et ne rejoint pas les lignes d'un paragraphe. Elle ne reconnaît que 31 unités du DPAC, contre 118
  sur le `.docx`, et peut garder la lettre détachée dans une valeur de confiance moyenne. Aucune valeur n'est fausse en
  confiance haute.
- La lecture « par clause » des DAO adaptés, recommandée par le front, attend la décision du pilote. Elle n'est pas
  livrée.

## Complément du 2026-09-29 (soir) — nos propres PDF

Le front a reporté deux corrections de la lecture PDF (`a3217b3`), portées ici telles quelles :
- une espace qui commence à l'intérieur de la lettre précédente est ignorée, puisqu'OpenPDF la pose sous la première lettre
  de chaque ligne ;
- l'interligne se mesure sur chaque page : c'est le plus petit écart fréquent entre deux lignes d'une même colonne.

Le front a de son côté repris l'écart du backend sur la ponctuation d'un jeton seul. La parité est de nouveau vérifiée
sur quatre entrées : les `.docx` de la fiche 27, le PDF réel du 2463, le rendu brut des modèles D2 et le PDF du DPAC de
la fiche 27 produit par le serveur. L'extraction est identique, la lecture aussi aux conflits de cadrage près. Le PDF du
serveur donne 117 unités reconnues sur 141, contre 31 avant, et aucune valeur fausse en confiance haute ni moyenne.

## Complément du 2026-09-29 (lot D3) — trois règles de prudence de plus

Le front les a mesurées sur un banc synthétique (`scripts/import-dao/banc.mjs`) : huit modèles, sans bruit et sur douze
graines de bruit. Ce banc remplace les fiches 27 et 16 disparues avec le vidage de DBPRS20. Les règles sont portées dans
`LectureDao` telles quelles :
- **un jumeau n'est jamais haut** : un paragraphe dont un autre paragraphe du modèle a le même texte fixe peut prendre sa
  place ;
- **la coupe vaut après un texte fixe final** : une valeur fusionnée n'avale plus la phrase suivante, et le texte fixe
  revient au reste relu ;
- **plusieurs jetons séparés de ponctuation seule** ne donnent rien.

La parité avec `lire.mjs` (522ed99) est vérifiée sur cinq entrées : les `.docx` et le PDF du contrat-cadre de la fiche 27,
le PDF réel du 2463, et le rendu brut des modèles D2 et PI. L'extraction est identique, et la lecture aussi aux conflits
de cadrage près. Un test rejoue chaque règle sur un cas qui échouait avant, vérifié sur la version précédente du lecteur.

## Complément du 2026-09-30 (lot D4) — deux règles de plus

Le front les a mesurées sur le banc synthétique, étendu aux travaux (commit d1e3c90). Avec bruit, sur huit graines et
tous modèles, la lecture donne 144 valeurs justes de plus pour 5 fausses de plus, et aucune en confiance haute. Les deux
règles sont portées dans `LectureDao` telles quelles :
- **R-a, texte répété** : un paragraphe dont le texte fixe se répète ailleurs dans le modèle (« Non applicable ») ne se
  cherche que dans les 3 paragraphes qui suivent le curseur (`FENETRE_REPETE`), et non plus 60. Absent du document, il
  se raccrochait à la répétition d'un article plus loin, et la lecture sautait tout ce qui les séparait.
- **R-b, section absente** : une section est absente quand elle a au moins un paragraphe distinctif et qu'aucun de ses
  paragraphes n'est reconnu. Ses jetons seuls sont écartés d'un intervalle à plusieurs jetons. S'il n'en reste qu'un,
  il est lu, mais en confiance basse.

Le front a aussi aligné sa lecture des conditions sur `DEBUT_TERME` : « et » et « ou » ne séparent deux termes que
devant une clé et un opérateur. L'écart « Au fur et à mesure des besoins » est donc résorbé. La parité est vérifiée sur
sept entrées : les cinq précédentes, plus les rendus bruts des travaux et du contrat-cadre recopié. L'extraction et la
lecture sont identiques, y compris les conflits. Il ne reste que l'écart de sortie sur une clé de cadrage en conflit :
le front la garde aussi dans `cadrage`.
