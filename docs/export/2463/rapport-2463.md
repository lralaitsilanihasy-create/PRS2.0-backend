# Jeu 2463 — rapport de constitution (2026-09-26)

Base DBPRS20 remise à zéro (compteurs à 0, entité 11 et comptes de contrôle conservés). Tout est passé **par
l'API** (`rejouer-2463.mjs`, un commit par étape), sous le compte de la PRMP créée puis sous les comptes de
contrôle du référentiel. Seule source des valeurs : `frontendprs2/docs/jeu-donnees-2463-faits.md` (non modifiée).
Hypothèses ajoutées : `docs/jeu-2463-hypotheses-backend.md`. Correspondance fait → base : `verification.md`.

## 1. Ce qui existe maintenant en base

| Objet | Identifiant | Référence / état |
|---|---|---|
| PRMP | `LERAVO01` (compte `LERAVO`) | LERAVO Norbert Fidelys, rattachée à l'entité 11 (lien 10 actif ; lien 9 de `IMP001` désactivé) |
| Dossier de planification | `t_dossier` 100351 | **`00001/PPM-AGPM/CNM/2026`**, statut `EN_VERIFICATION` (PV signé favorable) |
| PPM | `t_ppm` 200337 | `00001/MLSRS/PPM-AGPM/2026`, exercice 2026, signé le 30/06/2026 [H], mise à jour n° 0 |
| Ligne | `t_marche` 303091 | objet du dossier, compte 2463, RPI, Fournitures, AOO, à commande, 457 000 000 Ar [H], statut `PREVU` |
| Lots | `t_lot` 2133-2137 | intitulés exacts, maximum déduit (80 / 108,5 / 80 / 108,5 / 80 M) |
| Calendrier | `t_marche_prevision` × 28 | §8 : lancement 09/10, remise/ouverture 09/11, attribution 10/12, notification 28/01 |
| Circuit CNM | réception 1100078, dispatch 28, examen 1 (12 points), PV 39 | `00001/PPM-AGPM/CNM/PV/2026`, `SIGNE`, avis `FAV` |
| DMC | `t_dossier_mec` 15 | type `DAO`, statut `A_PREPARER`, **référence `null`** (voir §2.10) |
| Fiche DAO | `t_fiche_marche` 26 | version 1 `VALIDEE` le 26/09 par `LERAVO01`, 117 valeurs, 23 informations importées du plan |
| Besoin | 12 articles, 58 caractéristiques | lots 4 et 5 par relecture/renvoi des lots 2 et 3, vérifiés identiques |
| Documents | `t_document_fiche_marche` × 54 | DPAO, CCAP, LF (docx+pdf), AE × 5 lots, A1-A4, C1/C2 × 5 lots, BP/TC × 5 lots |

Contrôles à la validation : 0 bloquant, 0 avertissement, 28 constats `ok` — `DATES_ORDRE` ok, `GARANTIE_TAUX` ok
sur les cinq lots (garantie = 2 % du maximum, référence administrée 2 %), `AVANCE_SUP_5_GARANTIE` ok (avance 20 %,
garantie de restitution `B08-AV-04` présente), `PENALITES_PLAFOND_15` ok (plafond 10 %), `VALIDITE_GARANTIE_SUP_OFFRE`
ok (105 > 75), `BESOIN_INCOMPLET` et `GARANTIE_MANQUANTE` ok. `QUANTITES_ORDRE` n'émet **aucune ligne** quand
min ≤ max (le contrôle ne produit qu'un bloquant en cas de violation) : l'absence de bloquant vaut « OK ».

## 2. Informations que le modèle ne sait pas stocker, et règles rencontrées

1. **Sigle de l'entité** (« MESupReS ») : pas de colonne sur `tr_entite_contract`. La référence du PPM porte
   l'acronyme que le serveur dérive du libellé : `MLSRS` (`ReferenceService`, mots vides retirés, apostrophe non
   coupée : « L'ENSEIGNEMENT » → L).
2. **Destination de chaque lot** : `t_lot` n'a que désignation, montant, quantité, unité. Portée par la fiche,
   `B09-LL-01#n`, et par la liste des fournitures.
3. **Montant minimum de chaque lot** : `t_lot.MONT_LOT` est unique (le maximum). Porté par la fiche, `B05-TP-02#n`.
4. `t_lot.QTE_LOT` / `UNITE_LOT` : laissés **vides** — la fiche des faits ne donne de quantité que par article.
5. **Imputation administrative** `00 84 0 100 00000` et **articles 30, 35 et 63 du CMP** : aucun champ au plan.
6. **Comptable assignataire** : aucun modèle ; il ne vit que dans la fiche (`B03-NA-02`, `B03-NA-03`).
7. **UGPM** : `t_ugpm` est une personne (nom, prénoms, CIN, dates) ; le dossier ne dit que « UGPM » → aucun compte
   créé, la PRMP a tout rédigé (rôles convenus).
8. **Marquage des hypothèses** : aucun champ commentaire / indicateur / note de version sur la fiche, le dossier,
   la version ou le PPM. **Les [H] d'un dossier ne sont pas listables en base** ; elles le sont dans
   `docs/jeu-2463-hypotheses-backend.md` et `verification.md`. Écart à porter au pilote.
9. **Étape de calendrier d'un jour impossible** : `ProcessusChronologie` exige `dateFin > dateDebut` puis
   `dateDebut[n] ≥ dateFin[n-1]`. Les étapes 111 (lancement), 119, 120, 121 finissent au jour ouvré suivant et 112
   débute le 12/10 (arbitrage du 26/09, ancres du §8 intactes).
10. **Référence du DAO** : `t_dossier_mec.REFERENCE` reste `null` à la création du DMC et à la validation de la
    fiche ; le compteur `DMC` de `t_sequence_reference` est **toujours à 0** — la référence `00001/DAO/CNM/2026`
    naît avec le **dossier à soumettre** (`POST /api/fiches-marche/15/dossier`, non demandé à l'étape 3, qui s'arrête
    à la version validée). En attendant, BP et TC impriment la référence du **plan** en « Dossier d'appel d'offres ».
11. **AGPM** : pas de rendu serveur (le front calcule et dessine le projet) ; `agpm-2026.html` est une reconstitution
    des données de l'API, marquée comme telle.
12. **Liste des fournitures** : **un** document (docx + pdf) avec un tableau par lot, pas cinq fichiers ; BP et TC
    sont bien produits par lot (feuilles protégées, prix unitaires seuls déverrouillés).
13. **23 informations importées du plan**, pas 22 : `B01-AC-01` à `B01-AC-19` (acheteur), `B02-LV-01` à `B02-LV-03`
    (lots et variantes), `B02-OB-01` (objet).
14. **`B03-CQ-09` / `B03-CQ-10`** (5 et 3 ans) : posés par le défaut du référentiel à la création de la fiche ;
    correspondent aux formulaires A1/A3 du dossier mais sont absents de la fiche des faits.
15. **TVA** : `FICHE_TAUX_TVA` est exposé et vaut 20 (défaut administré de V46) ; les bordereaux impriment
    « TVA (20 %) » / « Total TTC » — ce n'est pas une donnée du dossier (arbitrage du 26/09 : gardé).
16. **Signataire du PPM** : posé par le serveur « Norbert Fidelys LERAVO » (prénoms + nom) ; `DATE_PPM_INIT` vide.

## 3. Anomalies du dossier réel (§9) — conservées, jamais corrigées

| Anomalie | Où elle vit dans le jeu |
|---|---|
| DPAO 7.1 : mention des plis « AOO N° 2461/MT /MESupReS/PRMP/UGPM.2026 » pour le dossier 2463-MI | `B04-RO-02`, texte tel quel ; la référence juste est dans `B02-OB-03` et les formulaires A1-A4 / C1-C2 |
| DPAO 1.2 : « matériels et mobiliers de logements » pour des matériels informatiques | `B02-OB-02`, texte tel quel ; l'objet juste est la ligne du plan (`B02-OB-01`) |
| CCAP 9.1.a : avance en blanc | cadrage `avance = OUI`, `tauxAvance = 20` et `B08-AV-04/05/06` en [H] (§7, §10) — le blanc n'est pas reproductible dans une fiche qui exige une valeur |
| DPAO 7.2 / AE : date limite et date de notification en blanc | `B04-LR-03`, `B04-OP-02`, `B02-AU-05` posées en [H] depuis le §8 |
| CCAP art. 24 : tableau des dérogations sans le plafond de pénalités à 10 % | `B10-DD-01` ne cite que 23 ↔ 22 (comme le CCAP) ; `B09-PR-03` note que l'article 24 ne récapitule pas la dérogation ; le contrôle `PENALITES_PLAFOND_15` constate le plafond différent |
| DPAO 6.5.1 : une seule destination finale là où le CCAP en donne cinq | la fiche suit le CCAP : `B09-LL-01#1…#5` et `B09-LF-01` (cinq destinations) ; le texte du DPAO 6.5.1 n'a pas de champ où être conservé |

## 4. Tampon de la base (lignes par table du circuit)

| Table | Avant | Après | Table | Avant | Après |
|---|---|---|---|---|---|
| t_prmp | 1 | 2 | t_dossier_mec | 0 | 1 |
| t_prmp_entite | 7 | 8 | t_fiche_marche | 0 | 1 |
| t_compte_auth | 14 | 15 | t_fiche_marche_valeur | 0 | 117 |
| t_dossier | 0 | 1 | t_fiche_article | 0 | 12 |
| t_ppm | 0 | 1 | t_fiche_caracteristique | 0 | 58 |
| t_marche | 0 | 1 | t_document_fiche_marche | 0 | 54 |
| t_lot | 0 | 5 | t_piece_jointe_dossier | 0 | 0 |
| t_marche_prevision | 0 | 28 | t_version_dossier | 0 | 0 |
| t_service_beneficiaire | 0 | 0 | t_action_dossier | 0 | 5 |
| t_reception | 0 | 1 | t_tache_dossier | 0 | 5 |
| t_dispatch | 0 | 1 | t_notification | 0 | 10 |
| t_examen | 0 | 1 | t_pv_navette | 0 | 2 |
| t_examen_detail | 0 | 12 | t_verification | 0 | 0 |
| t_observation_controle | 0 | 0 | t_audit_log | 35 149 | 35 190 |
| t_pv_examen | 0 | 1 | t_sequence_reference | 5 (toutes à 0) | DDP = 1, MLSRS/PPM_REF = 1, DMC = 0 |

## 5. Commits

`8778183` étape 0 · `3ba8664` étape 1 · `913a139` étape 2 · `638f56d` étape 3 · `7b229fd` étape 4 · étape 5 = ce
rapport, `verification.md` et `jeu-2463.json › resultat.export`. (Le message de l'étape 3 annonce 61
caractéristiques : le compte exact est **58** — 10 + 13 + 11 + 13 + 11.)

## 6. Points ouverts

- **Créer et soumettre le dossier de mise en concurrence** (`POST /api/fiches-marche/15/dossier`, puis soumission)
  pour consommer le compteur `DMC` et obtenir `00001/DAO/CNM/2026` — non demandé, non fait.
- Côté fiche des faits (front) : ajouter en [R] `B06-EO-04` (CCAP 8.1 p.50), `B08-PA-01` (AE 6.1 p.38),
  `B09-PC-02` (AE p.38) et les 5 / 3 ans des formulaires A1 / A3 (p.23-30) ; trancher `B09-AS-01` (fiche : assurance
  par le fournisseur p.51 ; front : « selon l'incoterm », CCAP art. 18).
- Pour le pilote : un champ où marquer une hypothèse (note de version de la fiche, ou indicateur par valeur) ; une
  étape de calendrier d'un jour ; le sigle de l'entité.
