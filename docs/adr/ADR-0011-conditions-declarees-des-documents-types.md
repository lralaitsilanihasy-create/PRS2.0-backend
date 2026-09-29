# ADR-0011 : Les documents types officiels choisissent leurs rédactions par des conditions déclarées dans le fichier

**Statut :** Adopté
**Date :** 2026-09-28
**Origine :** demande front `frontend/docs/demande-backend-2026-09-28-lot-d-dao-complet.md` (lot D, « DAO complet »,
feu vert du pilote du 28/09) ; plan `docs/plan-2026-09-28-lot-d-dao-complet.md` ; chaîne de décalque
`frontend/scripts/modeles-dao/`.

## Contexte

Depuis le lot 2a (23/09), les documents produits à la validation d'une fiche DAO sont des listes « libellé : valeur » par
bloc (`SelectionDocumentsFiche`). Le lot D les remplace par **le document type de l'ARMP, rempli** : texte fixe repris
tel quel, trous remplis par la fiche, et **une seule rédaction** parmi celles que le modèle propose (« Choix 1 / Choix
2 », « mono / multi-attributaire », « reconductible ou non »…).

Les formulaires du candidat (V47) avaient déjà la mécanique : un fichier de commande copié du front, des sections
`{{SI:NOM}}` … `{{FINSI:NOM}}`. Mais leurs trois noms (`A1B`, `B04-SE`, `A3B-NATURES`) étaient **écrits en dur** dans
`FormulairesCandidat`, et un nom inconnu valait « vrai » : le texte était gardé en silence. Le contrat-cadre en déclare 14
(DPAC) et 53 (AE), le DPAO des fournitures en aura davantage — les écrire en Java ferait du code le dépositaire des règles
de rédaction du modèle, et chaque nouveau document type une livraison backend.

## Décision

1. **Les conditions vivent dans le fichier de commande**, en tête : `CONDITION<TAB>NOM<US>expression`. Le fichier est
   la source du rendu ; le backend le copie tel quel (`modeles/dao/`), comme les formulaires du candidat.
2. **Une grammaire, pas un langage** — celle des conditions du référentiel, étendue : `cle = valeur`, `cle != valeur`,
   `cle contient texte`, `cle renseigne`, `cle vide`, reliés par `et` (prioritaire) puis `ou`, sans parenthèses.
   Comparaisons sans casse, blancs réduits, apostrophes confondues. Un `et` / `ou` ne sépare que s'il précède un terme
   complet : une valeur peut contenir « et » (« Au fur et à mesure des besoins »). `cle` est une **clé de cadrage** (avec
   ses défauts : `modeRemise` absent = `PAPIER`) ou un **code de champ** (valeur de la version figée, celle du lot pour
   un document de lot). Moteur pur : `ConditionsModele`.
3. **Échouer au chargement plutôt que se tromper en silence** : une condition illisible, déclarée deux fois, une section
   utilisée sans être déclarée ou mal emboîtée **empêchent le démarrage** (`ModelesDao`, et désormais aussi
   `ModelesCandidat`, où les trois noms historiques restent admis sans déclaration).
4. **Les sections s'emboîtent** : une pile ; une section fausse omet tout jusqu'à son `FINSI`, sections internes
   comprises (elles ne sont pas même évaluées).
5. **Le remplacement est par type de document et par forme, jamais général** : une forme dont le document type est décrit
   (`ModelesDao.COUVERTURES` ; aujourd'hui le contrat-cadre, fournitures et services : DPAC, AE par lot) reçoit le
   document type rempli ; les autres gardent le lot 2a jusqu'à ce que leur fichier existe.
6. **La preuve est externe** : le rendu **brut** du serveur (jetons et marqueurs non substitués) est jugé par le
   comparateur du front (`verifier.mjs`), dans les deux sens — ce que le document type dit se retrouve dans le rendu, et
   le rendu ne dit rien d'autre.

## Conséquences

- Ajouter un document type (DPAO des fournitures, avis spécifique) est une livraison de **fichier** et d'une ligne de
  couverture, pas de règle en Java ; le moteur ne décide rien, il évalue.
- Une valeur de liste se compare à l'option **telle que le référentiel la sert** : renommer une option impose de
  reprendre les conditions qui la citent (d'où « les options de `B09-GP-04` s'écrivent exactement ainsi »).
- Un jeton garde le contrat existant (`{{CODE}}` imprime un montant avec « Ariary », un pourcentage avec « % ») : le
  modèle qui écrit lui-même l'unité doit employer `{{CODE.chiffres}}`.
- La fiche reste consultable à l'écran telle quelle ; c'est elle que la Commission contrôle point par point (Q1).

## Alternatives écartées

- **Des conditions nommées en Java** (prolonger `condition()`) : les règles de rédaction du modèle dans le code, une
  livraison par document, et le silence sur un nom inconnu.
- **Un langage d'expressions général** (SpEL, script) : plus que le besoin, une surface d'erreur et d'injection pour des
  fichiers qui ne demandent que des égalités de réponses.
- **Des parenthèses** : aucune condition du contrat-cadre n'en a besoin ; `et` prioritaire suffit, et l'imbrication des
  sections couvre le reste.

## Complément du 2026-09-29 — lot D2, les fournitures

Demande front `frontend/docs/demande-backend-2026-09-29-lot-d2-fournitures.md`. Le DPAO des fournitures est un tableau
« clause des IC | données particulières », et ses rédactions au choix sont dans les cellules. La décision s'étend sans
changer de nature : le moteur évalue toujours, le fichier décide toujours.

1. **Marqueurs dans une cellule.** Un paragraphe de cellule qui n'est que `{{SI:NOM}}` / `{{FINSI:NOM}}` ouvre et ferme
   une section interne à la cellule. Il suit la même évaluation et ne s'imprime jamais.
2. **Marqueurs de rangée.** Une rangée dont la première cellule est exactement `{{SI:NOM}}` / `{{FINSI:NOM}}`, les autres
   étant vides, ouvre et ferme une section de rangées. La rangée-marqueur ne s'imprime jamais. La plage historique
   `A3B-NATURES`, dont le marqueur est collé au texte d'une cellule, garde sa lecture.
3. **`{{CODE.parLot}}`.** Dans un document commun, la valeur d'un champ saisi par lot s'énumère « Lot n° 1 : v1 ; Lot n° 2 :
   v2 », chaque valeur formatée comme `{{CODE}}`. Sur une ligne non allotie, ou dans un document de lot, c'est la valeur
   seule.
4. **`typeMarche` et `categorie`** se lisent comme des clés. Un seul modèle par document sert ainsi la quantité fixe et le
   marché à commande.
5. **Un modèle peut couvrir plusieurs formes.** `ModelesDao.COUVERTURES` associe un sigle à chaque couple forme et
   catégorie, et le fichier n'est chargé qu'une fois.

La preuve reste externe. Le rendu brut des trois fichiers est jugé fidèle par le comparateur du front : 234/234, 455/455
et 456/456. Le contrat-cadre est inchangé, à 174/174 et 378/378.
