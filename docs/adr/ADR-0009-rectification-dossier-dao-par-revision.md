# ADR-0009 : La rectification d'un dossier DAO est une révision validée de sa fiche, liée au circuit du dossier

**Statut :** Adopté
**Date :** 2026-09-27
**Origine :** demande front `frontend/docs/demande-backend-2026-09-26-rectification-dossier-dao.md` (lot C, plan
`docs/plan-2026-09-26-lot-c-rectification-dossier-dao.md`), arbitrages du pilote du 2026-09-26 (Q2 remplacement
automatique des pièces produites, Q3 fiche verrouillée pendant l'examen, Q4 tous les points réévalués). Migration
`V49__rectification_dossier_dao.sql`.

## Contexte

Un dossier d'appel d'offres (famille DMC, né de `POST /api/fiches-marche/{idDmc}/dossier`) suit le circuit générique
jusqu'au Vérificateur (avis favorable avec réserves) ou jusqu'à la lettre de renvoi (avis défavorable). Au-delà, le
circuit supposait un plan de passation : la rectification était un ré-import (`PUT /api/saisies/ppm/{id}`), le réexamen
comparait deux versions de plan. Pour un DAO, la correction est une **révision de la fiche marché** (version n+1,
validation) — que rien ne reliait au dossier soumis : la validation ne touchait pas ses pièces, la resoumission
n'exigeait rien, le réexamen ne savait pas ce qui avait changé, et la fiche pouvait être révisée pendant que la
Commission examinait sa version précédente.

Le lot B (V44) avait rendu l'examen capable de viser une information précise de la fiche (`idDmc` + `champFiche`,
valeur figée à l'observation). Il fallait fermer la boucle : ramener ces observations à la PRMP, faire revenir la
fiche corrigée devant le Vérificateur ou la Commission, et dire ce qui a changé.

## Décision

1. **La rectification d'un dossier DAO est une révision validée de sa fiche** — jamais un nouveau dossier, jamais un
   ré-import. Le dossier garde sa référence, son circuit et son PV ; c'est la fiche qui change de version.
2. **La fiche est verrouillée tant que la Commission tient le dossier** (`SOUMIS` … `CLOTURE`, réexamen et vérification
   compris) : `reviser` répond 409 `DOSSIER_EN_EXAMEN`. Elle se révise quand le dossier revient à la PRMP
   (`EN_ATTENTE_DECISION_PRMP`, `EN_ATTENTE_PIECES`, `EN_ATTENTE_COMPLEMENTS_DEPOT`) ou n'est pas encore parti.
   *Pourquoi* : la Commission examine une version stable ; une fiche qui bouge sous ses yeux rendrait le PV
   incohérent avec les pièces du dossier.
3. **Le dossier mémorise la version soumise** (`VERSION_FICHE_SOUMISE`, posée à la soumission, avancée à chaque
   resoumission / transmission de compléments) **et la version examinée** (`VERSION_FICHE_EXAMINEE`, l'ancienne
   version soumise au moment où les compléments sont transmis). La première fonde la garde de rectification, la
   seconde la borne « avant » du réexamen. *Pourquoi deux colonnes* : dans la boucle FAVR, le Vérificateur statue
   plusieurs fois sans que la Commission réexamine — la version examinée reste celle du PV pendant que la version
   soumise avance.
4. **Resoumettre et transmettre les compléments exigent une version validée postérieure à la version soumise**
   (409 `FICHE_NON_REVISEE`, avec `versionSoumise`, `versionCourante`, `statutFiche`). Pour un DAO, la garde des
   compléments « au moins une pièce rattachée à la dernière lettre » est remplacée par celle-ci : le complément *est*
   la révision validée, dont les documents sont rattachés à la lettre par la validation.
5. **La validation d'une révision remplace les pièces produites du dossier rendu à la PRMP**, l'ancienne version
   conservée (mécanique des pièces corrigées d'un plan : `versionCorrigee = true`, et après une lettre de renvoi
   `apresLettreRenvoi = true`, `idLettre`). Les pièces déposées à part ne bougent pas. Journal `FICHE_REVISEE`.
6. **L'observation figée dit aussi la valeur actuelle** (`valeurChampFicheActuelle`, `versionFicheObservee`,
   `versionFicheActuelle`) ; la version dont la valeur a été figée est désormais stockée avec l'observation
   (`VERSION_FICHE`, sur la ligne d'examen et sa copie au PV).
7. **Le périmètre de réexamen d'un DAO liste les informations changées** entre la version examinée et la version
   courante, valeurs formatées comme dans les documents, mais **n'allège pas la complétude** : tous les points du
   sous-type sont réévalués (Q4).
8. **La lettre de renvoi nomme l'information** visée par chaque observation ancrée. Le corps de la lettre étant un
   texte libre, le bloc s'ajoute à sa suite (il ne s'insère pas ligne à ligne).

## Conséquences

- Un 409 à code stable peut porter des **`details` nommés** (`ErrorResponse.details`) : une troisième information à
  côté de `code` et `idDossier`, sans nouvelle forme de corps d'erreur (`erreurs[]` reste réservé au 400).
- Les observations posées avant V49 n'ont pas de `versionFicheObservee` (nulle) ; les dossiers DAO déjà soumis
  reçoivent, par la migration, la dernière version validée avant leur soumission comme version soumise et examinée.
- Un dossier sans fiche marché (plan de passation) ne voit **rien changer** : ni garde, ni champ non nul.
- Les tests de V44 qui révisaient une fiche dont le dossier était en examen jouent désormais la révision dossier
  rendu à la PRMP (`ObservationChampFicheIntegrationTest`).
