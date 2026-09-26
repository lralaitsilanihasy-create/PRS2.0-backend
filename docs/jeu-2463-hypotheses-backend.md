# Jeu 2463 — hypothèses ajoutées par le backend

*Constitution du jeu de données du dossier réel 2463 sur DBPRS20 (2026-09-26), par l'API, depuis la seule source
`frontendprs2/docs/jeu-donnees-2463-faits.md` (non modifiée). Ce fichier liste ce que le **circuit** ou le **modèle**
ont exigé et que le dossier ne donne pas : chaque ligne est une hypothèse [H], à ne jamais présenter comme venant du
dossier. Les [H] déjà écrites dans la fiche des faits (§5, §7, §8, §10) n'y sont pas répétées : elles sont reprises
telles quelles et repérées dans `docs/export/2463/verification.md`.*

*⚠️ Le modèle n'a aujourd'hui **aucun champ** (commentaire, indicateur, note de version) où marquer une valeur [H] : ni
`t_fiche_marche`, ni `t_dossier`, ni `t_version_dossier`, ni `t_ppm` (hors `MOTIF_MAJ` / `JUSTIFICATION_FICHE`, qui ont
un autre sens métier). Les hypothèses d'un dossier ne sont donc **pas listables en base** ; elles le sont ici et dans
`verification.md`. Écart à porter au pilote.*

## Étape 0 — entité et acteurs

| # | Où | Valeur posée | Pourquoi |
|---|---|---|---|
| H0-1 | `t_prmp.ID_PRMP` | `LERAVO01` | matricule = clé du modèle, le dossier n'en donne pas |
| H0-2 | `t_prmp.ARRETE_NOMIN` | `[H] non fourni par le dossier` | champ obligatoire (`@NotBlank`) |
| H0-3 | `t_prmp.DATE_NOMIN` | `2026-01-01` | champ obligatoire (`@NotNull`) — date de convention |
| H0-4 | `t_prmp.CIN` | `NON FOURNI` | champ obligatoire, 12 caractères au plus |
| H0-5 | `t_prmp.DATE_CIN` | `2026-01-01` | champ obligatoire — date de convention |
| H0-6 | `t_prmp.LIEU_CIN` | `[H] non fourni` | champ obligatoire |
| H0-7 | `t_prmp.TEL_PRMP` | `[H] non fourni` | champ obligatoire |
| H0-8 | `t_compte_auth.LOGIN` | `LERAVO`, mot de passe commun de développement | un compte PRMP est nécessaire à la saisie |
| H0-9 | `t_prmp_entite` | lien 9 (`IMP001` ↔ entité 11) **désactivé**, lien 10 (`LERAVO01` ↔ 11) créé actif | une seule PRMP active par entité (invariant du modèle) ; IMP001 est la persona de démonstration rattachée le 25/09 |

Repris du dossier, sans hypothèse : nom `LERAVO`, prénoms `Norbert Fidelys`, e-mail `prmp.mesupres@gmail.com` (p.17).

Non créés, faute de personne nommée par le dossier : **le compte UGPM** (`t_ugpm` attend nom, prénoms, CIN, dates —
le dossier ne dit que « UGPM ») et **le comptable assignataire** (aucun modèle : il ne vit que dans la fiche,
`B03-NA-02` / `B03-NA-03`). Le sigle « MESupReS » n'avait pas de colonne sur `tr_entite_contract` au rejeu ; depuis
V48 (26/09, demande front « sigle de l'entité »), l'étape 0 le pose — il ne vaut que pour les prochaines références.

## Étape 1 — plan de passation et circuit CNM

| # | Où | Valeur posée | Pourquoi |
|---|---|---|---|
| H1-1 | `t_ppm.DATE_SIGNATURE` | `2026-06-30` | déjà [H] §10 de la fiche des faits (rappel) |
| H1-2 | `t_marche.MONT_ESTIM` | `457 000 000` | déjà [H] §5 (= total maximum des lots) |
| H1-3 | `t_marche_prevision` (28 étapes) | dates de `jeu-2463.json › entree.plan.processus` | §8 fait foi pour 111 / 112-113 / 123 / 129 ; les étapes intermédiaires en jours ouvrés (§10, dernier alinéa) |
| H1-3b | étapes 111, 119, 120, 121 (un jour) et début de 112 | fin au **jour ouvré suivant** : 111 = 09/10→12/10, 112 débute le 12/10 (fin 09/11 inchangée), 119 = 07/12→08/12, 120 = 08/12→09/12, 121 = 09/12→10/12 | le serveur exige `dateFin > dateDebut` puis `dateDebut[n] ≥ dateFin[n-1]` (`ProcessusChronologie`) : **une étape d'un jour est impossible** — écart du modèle à porter au pilote ; ancres du §8 (09/10, 09/11, 10/12, 28/01) intactes — arbitrage du 26/09 |
| H1-4 | `t_lot.QTE_LOT`, `UNITE_LOT` | **vides** | absents de la fiche des faits (la quantité d'un lot n'y est qu'article par article) |
| H1-5 | réception `t_reception` | `SECANT1` (Rasoa, Secrétaire ANT), date du jour, complet, « Dossier complet : plan de passation 2026 du MESupReS et fiche de présentation. » | le circuit exige un réceptionnaire, une date et une observation |
| H1-6 | dispatch `t_dispatch` | `PRES001` (RANDRIANARISON) → `MEMANT1` (RAFIDIMANANA Rina), date du jour, instructions | le circuit exige un dispatcheur, un examinateur et des instructions |
| H1-7 | examen `t_examen`, `t_examen_detail` | tous les points de la grille `PPM-AGPM` **conformes**, aucune observation, avis **FAV** | le dossier ne dit rien de l'examen de son plan ; l'avis favorable est ce qui rend la ligne préparable en DAO |
| H1-8 | PV `t_pv_examen` | visa `PRES001` « Plan de passation conforme : avis favorable. », co-signataire `MEMANT2` (RAKOTOARISOA Hanta), signature du co-signataire | le PV signé exige deux personnes distinctes |

Les acteurs de contrôle sont les comptes du référentiel conservé à la remise à zéro (aucun contrôleur créé).

## Étape 3 — fiche DAO

| # | Où | Valeur posée | Pourquoi |
|---|---|---|---|
| H3-1 | `B03-CQ-09`, `B03-CQ-10` | `5`, `3` (défauts du référentiel, V47) | posés à la création de la fiche ; correspondent aux formulaires A1/A3 du dossier (p.23-30) mais **absents de la fiche des faits** — à y ajouter ou à confirmer |
| H3-2 | `B06-EO-04`, `B08-PA-01`, `B09-PC-02` | transcriptions du front (CCAP 8.1 p.50, AE 6.1 p.38, AE p.38) | absentes de la fiche des faits ; les deux premières sont obligatoires — **arbitrage du 26/09 : reprises**, à ajouter à la fiche des faits en [R] |
| H3-3 | `B09-AS-01` | « À la charge du fournisseur jusqu'à la livraison » | la fiche des faits (§7, p.51) l'emporte sur la transcription du front (« Selon l'incoterm », CCAP art. 18) — **arbitrage du 26/09** |
| H3-4 | `B08-AV-04`, `B08-AV-05`, `B08-AV-06` | garantie bancaire, remboursement intégral à 80 %, précompte 20 % | déjà [H] §7 / §10 (rappel) |
| H3-5 | `B06-AN-02`, `B06-EO-06`, `B06-EO-08` | paraphrases des IC | déjà [H] §10 (rappel) |
| H3-6 | `B08-IM-01`, `B08-PA-08`, `B02-AU-05`, `B04-LR-03` | 9, 30, 28/01/2027, 09/11/2026 | déjà [H] §8 / §10 (rappel) |
| H3-7 | paramètre `FICHE_TAUX_TVA` | `20` (défaut administré de V46) | le bordereau imprime TVA 20 % / TTC ; ce n'est pas une donnée du dossier — **arbitrage du 26/09 : gardé** |

## Étape 2 — AGPM

Aucune valeur posée : le projet d'AGPM est **dérivé** du plan (rien n'est persisté). Le backend n'a pas de rendu ;
`docs/export/2463/agpm-2026.html` est une **reconstitution** des données servies par l'API dans la mise en page du
composant `agpm-doc.ts` du front (arbitrage du 26/09).
