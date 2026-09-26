# Jeu 2463 — tableau de correspondance

Généré par `rejouer-2463.mjs --etape 5` le 2026-09-26 depuis l'API (valeurs en base telles que servies). Source des faits : `frontendprs2/docs/jeu-donnees-2463-faits.md` ; nature recopiée : **[R]** repris, **[D]** déduit, **[H]** hypothèse. Les [H] sont aussi listées dans `docs/jeu-2463-hypotheses-backend.md`.

## 1. Autorité contractante et acteurs

| Fait | Stockage | Valeur en base | Nature |
|---|---|---|---|
| Autorité contractante | `tr_entite_contract.LIBELLE_ENTITE` (id 11) | MINISTERE DE L'ENSEIGNEMENT SUPERIEUR ET DE LA RECHERCHE SCIENTIFIQUE | [R] p.1, p.17 |
| Sigle MESupReS | *(aucune colonne)* | — | [R] p.1, p.17 — non stockable (pas de champ sigle sur tr_entite_contract) |
| Adresse | `tr_entite_contract.ADRESSE` | Fiadanana, 2ème étage porte 204 — Antananarivo 101 | [R] p.17, p.49 |
| PRMP · nomPrmp | `t_prmp.NOM_PRMP` | LERAVO | [R] p.17, p.36 |
| PRMP · prenomsPrmp | `t_prmp.PRENOMS_PRMP` | Norbert Fidelys | [R] p.17, p.36 |
| PRMP · emailPrmp | `t_prmp.EMAIL_PRMP` | prmp.mesupres@gmail.com | [R] p.17 |
| PRMP · idPrmp | `t_prmp.ID_PRMP` | LERAVO01 | [H] matricule (identifiant technique, non donné par le dossier) |
| PRMP · arreteNomin | `t_prmp.ARRETE_NOMIN` | [H] non fourni par le dossier | [H] champ obligatoire du modèle, non donné par le dossier |
| PRMP · dateNomin | `t_prmp.DATE_NOMIN` | 2026-01-01 | [H] idem (date de convention) |
| PRMP · cin | `t_prmp.CIN` | NON FOURNI | [H] idem |
| PRMP · dateCin | `t_prmp.DATE_CIN` | 2026-01-01 | [H] idem (date de convention) |
| PRMP · lieuCin | `t_prmp.LIEU_CIN` | [H] non fourni | [H] idem |
| PRMP · telPrmp | `t_prmp.TEL_PRMP` | [H] non fourni | [H] idem |
| PRMP · login | `t_prmp.LOGIN` → `t_compte_auth.LOGIN` | LERAVO | [H] compte de saisie ; mot de passe = mot de passe commun de développement |
| Rattachement PRMP ↔ entité 11 | `t_prmp_entite` (lien 10, actif) | LERAVO01 ↔ 11 | [D] nécessaire au modèle (une PRMP active par entité) ; lien(s) désactivé(s) : [] |
| UGPM | *(t_ugpm : personne nominative, non créée)* | — | UGPM (Unité de Gestion de la Passation des Marchés) [R] p.1 — aucun compte créé : le modèle attend une personne (nom, CIN…) que le dossier ne nomme pas |
| Comptable assignataire | fiche `B03-NA-03` / `B03-NA-02` | Trésorier ministériel chargé de l’Enseignement | Trésorier ministériel chargé de l'Enseignement [R] p.38 — aucun modèle en dehors du texte de la fiche (B03-NA-02 / B03-NA-03) |

## 2. Dossier de planification et ligne 2463

| Fait | Stockage | Valeur en base | Nature |
|---|---|---|---|
| Référence du dossier | `t_dossier.REFE_DOSSIER` (id 100351) | 00001/PPM-AGPM/CNM/2026 | serveur (compteur `t_sequence_reference`) |
| Statut / sous-type | `t_dossier.STATUT` / `ID_SOUS_TYPE` | EN_VERIFICATION / PPM-AGPM | serveur |
| Exercice | `t_ppm.EXERCICE` (id 200337) | 2026 | [D] de la référence (§2) |
| Date de signature du PPM | `t_ppm.DATE_SIGNATURE` | 2026-06-30 | [H] §10 — date de signature du PPM |
| Signataire du PPM | `t_ppm.SIGNATAIRE` | Norbert Fidelys LERAVO | serveur (prénoms + nom de la PRMP) |
| Référence du PPM / n° de mise à jour | `t_ppm.REFERENCE` / `NUM_MAJ` | 00001/MLSRS/PPM-AGPM/2026 / 0 | serveur |
| Objet | `t_marche.DESIGNATION_MARCHE` (id 303091) | Fourniture et livraison des matériels informatiques répartis en cinq (5) lots (à commande) | [R] p.1 (§2, objet) |
| Mode AOO | `t_marche.ID_MODE` | 1 (Appel d'offres ouvert) | [R] p.36 (§2, appel d'offres ouvert → mode 1) |
| Catégorie fournitures | `t_marche.ID_NATURE` | 2 (Fournitures) | [R] p.36, p.67 (§2, catégorie fournitures → nature 2 « Fournitures ») |
| Marché à commande | `t_marche.FORME_MARCHE` | A_COMMANDE | [R] p.17, p.50 (§2, marché à commande) |
| Financement RPI | `t_marche.FINANCEMENT` | RPI | [R] p.1, p.36 (§2) |
| Compte 2463 | `t_marche.NUM_COMPTE` | 2463 | [R] p.1, p.36 (§2) |
| Montant estimatif | `t_marche.MONT_ESTIM` | 457 000 000 | [H] §5 — total maximum des cinq lots |
| Imputation administrative | *(aucune colonne)* | — | 00 84 0 100 00000 [R] p.1 — non stockable (le plan n'a pas de champ d'imputation, §10) |
| Articles 30, 35, 63 du CMP | *(aucune colonne)* | — | articles 30, 35 et 63 du CMP [R] p.36 — non stockable |

### Lots (`t_lot`)

| Lot | `DESIGNATION_LOT` | `MONT_LOT` (max) | Minimum (fiche `B05-TP-02#n`) | Destination (fiche `B09-LL-01#n`) | Nature |
|---|---|---|---|---|---|
| 1 (id 2133) | ORDINATEURS ET DIVERS POUR LE MINISTERE | 80 000 000 | 40 000 000 | MINISTERE FIADANANA | intitulé [R] p.40-44, p.49 (§3, intitulé exact) ; max [D] §5 — maximum = garantie ÷ 2 % ; min [D] §5 — non stockable au plan (t_lot n'a qu'un montant) ; porté par la fiche B05-TP-02 ; destination [R] p.49, p.51 (§3) — non stockable au plan ; portée par la fiche B09-LL-01 |
| 2 (id 2134) | ORDINATEUR POUR AMBATONDRAZAKA | 108 500 000 | 54 250 000 | AMBATONDRAZAKA | intitulé [R] p.40-44, p.49 (§3, intitulé exact) ; max [D] §5 — maximum = garantie ÷ 2 % ; min [D] §5 — non stockable au plan (t_lot n'a qu'un montant) ; porté par la fiche B05-TP-02 ; destination [R] p.49, p.51 (§3) — non stockable au plan ; portée par la fiche B09-LL-01 |
| 3 (id 2135) | DIVERS MATERIELS POUR AMBATONDRAZAKA | 80 000 000 | 40 000 000 | AMBATONDRAZAKA | intitulé [R] p.40-44, p.49 (§3, intitulé exact) ; max [D] §5 — maximum = garantie ÷ 2 % ; min [D] §5 — non stockable au plan (t_lot n'a qu'un montant) ; porté par la fiche B05-TP-02 ; destination [R] p.49, p.51 (§3) — non stockable au plan ; portée par la fiche B09-LL-01 |
| 4 (id 2136) | ORDINATEUR POUR FORT DAUPHIN | 108 500 000 | 54 250 000 | FORT DAUPHIN | intitulé [R] p.40-44, p.49 (§3, intitulé exact) ; max [D] §5 — maximum = garantie ÷ 2 % ; min [D] §5 — non stockable au plan (t_lot n'a qu'un montant) ; porté par la fiche B05-TP-02 ; destination [R] p.49, p.51 (§3) — non stockable au plan ; portée par la fiche B09-LL-01 |
| 5 (id 2137) | DIVERS MATERIELS POUR FORT DAUPHIN | 80 000 000 | 40 000 000 | FORT DAUPHIN | intitulé [R] p.40-44, p.49 (§3, intitulé exact) ; max [D] §5 — maximum = garantie ÷ 2 % ; min [D] §5 — non stockable au plan (t_lot n'a qu'un montant) ; porté par la fiche B05-TP-02 ; destination [R] p.49, p.51 (§3) — non stockable au plan ; portée par la fiche B09-LL-01 |

### Calendrier prévisionnel (`t_marche_prevision`) — [H] §8 (lancement 09/10/2026, remise/ouverture 09/11/2026, attribution 10/12/2026, notification 28/01/2027) et §10 dernier alinéa (étapes intermédiaires en jours ouvrés) ; ⚠️ arbitrage du 26/09 : les étapes d'un jour (111, 119, 120, 121) finissent le jour ouvré suivant et 112 débute le 12/10, le serveur exigeant dateFin > dateDebut (ProcessusChronologie) — ancres du §8 intactes

| Étape CAPM | Début | Fin | En base (id) |
|---|---|---|---|
| 101 | 2026-06-01 | 2026-06-15 | identique (4271) |
| 102 | 2026-06-16 | 2026-06-30 | identique (4272) |
| 103 | 2026-07-01 | 2026-07-07 | identique (4273) |
| 104 | 2026-07-08 | 2026-08-28 | identique (4274) |
| 106 | 2026-08-31 | 2026-09-02 | identique (4275) |
| 107 | 2026-09-03 | 2026-09-17 | identique (4276) |
| 108 | 2026-09-18 | 2026-09-22 | identique (4277) |
| 109 | 2026-09-23 | 2026-09-25 | identique (4278) |
| 110 | 2026-09-28 | 2026-10-08 | identique (4279) |
| 111 | 2026-10-09 | 2026-10-12 | identique (4280) |
| 112 | 2026-10-12 | 2026-11-09 | identique (4281) |
| 113 | 2026-11-09 | 2026-11-10 | identique (4282) |
| 114 | 2026-11-10 | 2026-11-18 | identique (4283) |
| 115 | 2026-11-19 | 2026-11-20 | identique (4284) |
| 116 | 2026-11-23 | 2026-11-25 | identique (4285) |
| 117 | 2026-11-26 | 2026-11-27 | identique (4286) |
| 118 | 2026-11-30 | 2026-12-04 | identique (4287) |
| 119 | 2026-12-07 | 2026-12-08 | identique (4288) |
| 120 | 2026-12-08 | 2026-12-09 | identique (4289) |
| 121 | 2026-12-09 | 2026-12-10 | identique (4290) |
| 123 | 2026-12-10 | 2026-12-11 | identique (4291) |
| 124 | 2026-12-14 | 2026-12-15 | identique (4292) |
| 125 | 2026-12-16 | 2027-01-08 | identique (4293) |
| 126 | 2027-01-11 | 2027-01-15 | identique (4294) |
| 127 | 2027-01-18 | 2027-01-22 | identique (4295) |
| 128 | 2027-01-25 | 2027-01-27 | identique (4296) |
| 129 | 2027-01-28 | 2027-01-29 | identique (4297) |
| 130 | 2027-02-01 | 2028-01-31 | identique (4298) |

## 3. Circuit CNM du plan — [H] tout le circuit CNM (acteurs, dates du jour, textes, avis favorable et grille entièrement conforme) — le dossier ne dit rien de l'examen de son plan

| Acte | Stockage | Valeur |
|---|---|---|
| Soumission | `t_dossier.DATE_SOUMISSION`, `SOUMIS_PAR` | 2026-09-26T17:33:40.527765 · LERAVO |
| Réception | `t_reception` (id 1100078) | SECANT1 · 2026-09-26 · complet · « Dossier complet : plan de passation 2026 du MESupReS et fiche de présentation. » |
| Dispatch | `t_dispatch` (id 28) | PRES001 → MEMANT1 · « Examen du plan de passation 2026 du MESupReS : ligne unique de matériels informatiques à commande, allotie en cinq lots. » |
| Examen | `t_examen` (id 1), `t_examen_detail` | MEMANT1 · 12 points conformes · avis FAV |
| PV | `t_pv_examen` (id 39) | 00001/PPM-AGPM/CNM/PV/2026 · SIGNE · visa PRES001 « Plan de passation conforme : avis favorable. » · co-signataire MEMANT2 |

## 4. Projet d'AGPM (dérivé, non persisté)

| Champ | Source API | Valeur |
|---|---|---|
| agpmRequis / sous-type | `GET /api/ppms/{id}`, `GET /api/dossiers/{id}` | true / PPM-AGPM |
| compte | calcul `calculerAgpm` (front) | 2463 |
| nature | calcul `calculerAgpm` (front) | Fournitures |
| objet | calcul `calculerAgpm` (front) | Fourniture et livraison des matériels informatiques répartis en cinq (5) lots (à commande) |
| montant | calcul `calculerAgpm` (front) | 457 000 000 |
| financement | calcul `calculerAgpm` (front) | RPI |
| modeLibelle | calcul `calculerAgpm` (front) | Appel d'offres ouvert |
| dateDao | calcul `calculerAgpm` (front) | 2026-10-09 |

## 5. Dossier de mise en concurrence et fiche DAO

| Fait | Stockage | Valeur en base | Nature |
|---|---|---|---|
| Référence du DAO | `t_dossier_mec.REFERENCE` (id 15) | — | serveur (compteur DMC) |
| Version validée | `t_fiche_marche` (versions 1) | statut VALIDEE, version 1, validée le 2026-09-26T17:34:43.641022 | serveur |
| Informations importées du plan | `FicheMarcheDto.valeursPpm` | 23 clés : B01-AC-01, B01-AC-02, B01-AC-03, B01-AC-04, B01-AC-05, B01-AC-06, B01-AC-07, B01-AC-08, B01-AC-09, B01-AC-10, B01-AC-11, B01-AC-12, B01-AC-13, B01-AC-14, B01-AC-15, B01-AC-16, B01-AC-17, B01-AC-18, B01-AC-19, B02-LV-01, B02-LV-02, B02-LV-03, B02-OB-01 | [R] via le plan |

### Cadrage (`t_fiche_marche.CADRAGE`)

| Clé | Valeur en base | Nature |
|---|---|---|
| alloti | OUI | [R] p.17 (§2) |
| nbLots | 5 | [R] p.17 (§2) |
| variantes | NON | [R] p.17 (§2) |
| groupement | NON | [R] p.17, p.49 (§2) |
| provenance | NATIONAL | [R] p.18 (§6, fournitures nationales) |
| typePrix | UNITAIRES | [R] p.40-44 (§6, prix unitaires) |
| prixRevisable | NON | [R] p.18, p.50 (§6, fermes et non révisables) |
| garantieSoumission | OUI | [R] p.18 (§4) |
| avance | OUI | [H] §7 |
| tauxAvance | 20 | [H] §7 — 20 % |
| penalites | PLAFOND_DIFFERENT | [R] p.50 (§7, plafond 10 % ≠ CCAG) |

### Valeurs communes (`t_fiche_marche_valeur`, clé = code)

| Code | Valeur en base | Nature |
|---|---|---|
| B02-AU-02 | Lot par lot (attribution divisible) | [R] DPAO 1.1 p.17 : « Chaque lot est indivisible. Toute offre partielle est irrecevable. » |
| B02-AU-04 | 12 | [R] DPAO 1.2 p.17, CCAP 10.b p.50 |
| B02-AU-05 | 2027-01-28 | [H] §8 : notification / date d'effet |
| B02-AU-07 | 2 | [R] DPAO 1.1 p.17 : « ne peut prétendre qu'à deux lots au maximum » |
| B02-OB-02 | L'appel d'offres porte sur un marché à commandes des matériels et mobiliers de logements dont les quantités m… | [R] DPAO 1.2 p.17 — anomalie §9 conservée (« matériels et mobiliers de logements ») |
| B02-OB-03 | AOO n° 2463-MI/MESupReS/PRMP/UGPM.2026 | [R] p.1, AE p.36 |
| B03-CQ-01 | Photocopie certifiée conforme à l'original de la Carte d'Immatriculation Fiscale 2026 ou 2025 validée, datée … | [R] DPAO 6.1 p.17 (§6) |
| B03-CQ-02 | Une fiche de renseignements relative à sa capacité technique (modèle A2), signée avec la mention « certifiée … | [R] DPAO 6.3 |
| B03-CQ-03 | Une fiche de renseignements relative à sa capacité financière (modèle A3), signée avec la mention « certifiée… | [R] DPAO 6.3 |
| B03-CQ-04 | Non exigé : la clause 6.3 du DPAO ne demande que les fiches d'identification, de capacité technique et de cap… | [D] §10 |
| B03-CQ-05 | NON | [R] sommaire p.2 : « Modèle d'attestation du fabricant – Non utilisé » |
| B03-CQ-06 | Aucune qualification particulière au-delà des pièces de la clause 6.1 et des fiches de la clause 6.3 du DPAO. | [D] §10 |
| B03-CQ-07 | Non prévu par le DPAO. | [D] §10 |
| B03-CQ-08 | NON | [R] DPAO 9.5 p.19 |
| B03-NA-01 | OUI | [R] AE art. 4 p.38 |
| B03-NA-02 | Est désigné comme comptable assignataire des paiements le Trésorier ministériel chargé de l'Enseignement ; le… | [R] AE art. 4 p.38 |
| B03-NA-03 | Trésorier ministériel chargé de l’Enseignement | [R] AE art. 4 p.38 |
| B03-ST-01 | NON | [R] AE art. 3 p.38 |
| B04-CD-01 | A1,A2,A3,A4 | [R] sommaire p.2, DPAO 5.1 : modèles de fiches de renseignements joints |
| B04-CD-02 | C1 et C2 | [R] DPAO 5.1 : modèles de garantie de soumission joints (p.33-34) |
| B04-CO-01 | Documents ou pièces à remettre en sus de ceux mentionnés à la clause 6.2 des IC : photocopie certifiée confor… | [R] DPAO 6.1 |
| B04-DE-01 | Personne Responsable des Marchés Publics — Attention de : Monsieur LERAVO Norbert Fidelys — Porte 204, 2ème E… | [R] DPAO 5.2 |
| B04-DE-02 | 10 | [R] DPAO 5.2 |
| B04-DE-03 | 5 | [R] DPAO 5.2 |
| B04-LA-01 | NON | [D] §10 : le DPAO ne prévoit aucune langue en plus du français |
| B04-LR-01 | Monsieur LERAVO Norbert Fidelys, Personne Responsable des Marchés Publics | [R] DPAO 7.2 |
| B04-LR-02 | Porte 204, 2ème Etage - MESupReS, Fiadanana - Antananarivo, code postal 101. | [R] DPAO 7.2 |
| B04-LR-03 | 2026-11-09 | [H] §8 (le dossier laisse la date en pointillés — anomalie §9) |
| B04-LR-04 | Dix (10) heures | [R] DPAO 7.2 |
| B04-OP-01 | Bureau : Porte 204, 2ème Etage - MESupReS | [R] DPAO 8 |
| B04-OP-02 | 2026-11-09 | [R] DPAO 8 : « le même jour que la date limite fixée pour la remise des offres » |
| B04-OP-03 | DIX HEURES (10H) | [R] DPAO 8 |
| B04-RO-01 | 1 | [R] DPAO 7.1 : « UNE (01) copie » |
| B04-RO-02 | AOO N° 2461/MT /MESupReS/PRMP/UGPM.2026 — Offre relative à « FOURNITURE ET LIVRAISON DES MATERIELS INFORMATIQ… | [R] DPAO 7.1 p.18, tel quel — ⚠️ anomalie §9 conservée : la mention cite « AOO N° 2461/MT » pour le dossier 2463-MI. |
| B04-RO-03 | Les offres devront être dans des plis séparés présentées pour chacun des lots. Outre l'original de l'offre, l… | [R] DPAO 7.1 |
| B04-VE-01 | NON | [R] DPAO 7.3 |
| B04-VO-01 | 75 | [R] DPAO 6.4 |
| B05-CP-02 | Pour les Fournitures acquises sur le territoire national, le prix comprend : i) le prix des fournitures EXW, … | [R] DPAO 6.5.1 p.18 (§6) |
| B05-GS-02 | Caution personnelle et solidaire d'un organisme agréé par le MEF,Garantie bancaire,Chèque de banque | [R] DPAO 6.6 p.18 : « dans l'une des formes suivantes : soit une garantie bancaire, soit une caution personnelle et solidaire, soit un chèque de banque libellé au nom du Receveur Général d'Antananarivo ». Liste à choix multiples depuis le 26/09 (demande-backend-2026-09-26-forme-garantie-soumission-choix-multiple.md) : options dans l'ordre du référentiel, séparées par des virgules — le serveur les range de toute façon. |
| B05-GS-04 | 105 | [R] modèles C1/C2 p.33-34 : « jusqu'au 105ème jour » |
| B05-MO-01 | Ariary | [R] CCAP 9.3, AE art. 2 |
| B06-AN-02 | Tout candidat écarté peut demander par écrit les motifs du rejet de sa candidature ou de son offre ; la Perso… | [H] §10 — paraphrase des IC (recours), DPAO muet |
| B06-EO-01 | Par lot | [R] DPAO 9.4 |
| B06-EO-02 | Non applicable. | [R] DPAO 9.4 : « Critère additionnel : non applicable » |
| B06-EO-04 | Le prix du Marché est supposé comprendre l'ensemble des impôts, droits et taxes de toute nature dus par le Fo… | [R] CCAP 8.1 p.50 — HORS fiche des faits (transcription du front reprise, arbitrage du 26/09) |
| B06-EO-05 | Les offres seront évaluées par lot et le marché portera sur le lot ou les lots attribués au candidat qualifié… | [R] DPAO 9.4 |
| B06-EO-06 | Après évaluation, la Personne Responsable des Marchés Publics compare toutes les offres substantiellement con… | [H] §10 — paraphrase des IC (comparaison des offres), DPAO muet |
| B06-EO-07 | Afin d'identifier le caractère anormalement bas ou haut d'une offre, la CAO effectuera les calculs suivants :… | [R] DPAO 9.4.5 |
| B06-EO-08 | La Personne Responsable des Marchés Publics vérifie, avant attribution, que le candidat ayant présenté l'offr… | [H] §10 — paraphrase des IC (vérification a posteriori), DPAO muet |
| B06-EP-01 | 3 | [R] DPAO 9.1 |
| B08-AC-01 | NON | [R] CCAP 9.1.b |
| B08-AV-04 | Garantie bancaire | [H] §7 — garantie de restitution bancaire (CCAP 9.1.a en blanc) |
| B08-AV-05 | L'avance forfaitaire est remboursée par précompte sur les sommes dues au titulaire, au fur et à mesure des li… | [H] §10 — remboursement intégral quand les paiements atteignent 80 % |
| B08-AV-06 | 20 | [H] §10 — précompte 20 % |
| B08-GB-01 | NON | [R] CCAP 12.1 : « Non applicable » |
| B08-IM-01 | 9 | [H] §10 : le CCAP 9.4 dit « taux directeur de la BCM … augmenté d'un (01) point » ; le référentiel veut un nombre. |
| B08-PA-01 | L'Acheteur se libérera des sommes dues au titre du présent marché en en faisant porter le montant au crédit d… | [R] AE art. 6.1 p.38 — HORS fiche des faits (transcription du front reprise, arbitrage du 26/09) |
| B08-PA-03 | Les factures seront établies en quatre (04) exemplaires : un original et 3 copies portant, outre les mentions… | [R] CCAP 9.2 |
| B08-PA-04 | Les factures seront établies à la livraison. | [R] CCAP 9.2 |
| B08-PA-05 | À la livraison | [R] CCAP 9.2 |
| B08-PA-08 | 30 | [H] §10 |
| B08-RG-01 | NON | [R] CCAP 12.2 : « Aucune retenue de garantie ne sera pratiquée » |
| B09-AS-01 | À la charge du fournisseur jusqu'à la livraison | [R] §7 p.51 — transport et assurance par le fournisseur (arbitrage du 26/09 ; le front avait « Selon l'incoterm », CCAP art. 18) |
| B09-CR-01 | NON | [R] CCAP art. 19 |
| B09-DG-01 | 2 | [R] CCAP art. 22 : DEUX (02) MOIS — dérogation à l'article 23 du CCAG (CCAP art. 24) |
| B09-DG-02 | Les fournitures doivent être garanties contre tout risque de fabrication ou de matière pendant DEUX (02) MOIS… | [R] CCAP art. 22 |
| B09-DI-01 | Sur demande du fournisseur, pour chaque commande, la réception prononcée à la livraison par une commission de… | [R] CCAP art. 21 |
| B09-DX-01 | 30 | [R] CCAP 10.a |
| B09-DX-02 | À compter du lendemain de la date de notification de chaque bon de commande. | [R] CCAP 10.a |
| B09-DX-03 | Le délai de livraison est fixé dans le bon de commande sans toutefois dépasser TRENTE (30) JOURS à compter du… | [R] CCAP 10.a et 10.b |
| B09-EM-01 | Emballage d’origine. | [R] CCAP art. 15 |
| B09-EM-02 | Aucun document particulier n'est exigé dans les emballages (CCAP art. 15 : emballage d'origine). | [D] §10 |
| B09-IV-01 | Les vérifications et inspections des fournitures sont effectuées au lieu de destination finale, au moment de … | [R] CCAP art. 20 |
| B09-LF-01 | Les fournitures seront livrées au : lot n° 1 : MINISTERE FIADANANA ; lot n° 2 : AMBATONDRAZAKA ; lot n° 3 : A… | [R] CCAP art. 17 |
| B09-LF-02 | Cinq (05) exemplaires de la facture du Fournisseur indiquant la description des Fournitures, leurs quantités,… | [R] CCAP art. 17 |
| B09-MC-01 | NON | [R] CCAP art. 13 : « Sans objet » |
| B09-OM-01 | 10 | [R] CCAP art. 6 |
| B09-OM-02 | 20 | [R] CCAP art. 6 |
| B09-OM-03 | 12 | [R] CCAP art. 6 et 10.b |
| B09-PC-01 | Aucune pièce supplémentaire : l'ordre de priorité des pièces contractuelles est celui fixé par l'article 6 du… | [D] §10 |
| B09-PC-02 | Annexe n° 1 : cadre du bordereau de prix. Annexe n° 2 : état des sommes versées à des tiers. Annexe n° 3 : fo… | [R] AE p.38 — HORS fiche des faits (transcription du front reprise, arbitrage du 26/09) |
| B09-PR-02 | 10 | [R] CCAP art. 11 p.50 (§7 : plafond 10 %) |
| B09-PR-03 | CCAP art. 11 : « En cas de retard dans l'exécution de chaque commande, il est appliqué une pénalité journaliè… | [R] + constat §9 |
| B09-PS-01 | NON | [R] CCAP art. 7 |
| B09-RT-01 | Transport par le fournisseur jusqu'à la destination finale | [R] CCAP art. 16 |
| B09-SK-01 | OUI | [R] CCAP art. 14 : « Quantité minimale prévue dans le bordereau de prix » |
| B10-AR-01 | Aucune clause particulière au CCAP : le règlement des différends suit le CCAG. | [D] §10 |
| B10-DD-01 | Article 22 du CCAP (délai de garantie de deux mois) dérogeant à l'article 23 du CCAG — seule dérogation récap… | [R] CCAP art. 24 |
| B10-IR-01 | OUI | [R] CCAP art. 23 p.52 (§7 : indemnité de résiliation, CCAG art. 32) |
| B10-IR-02 | Les dispositions de l'article 32 du CCAG s'appliquent. | [R] CCAP art. 23 |

### Valeurs par lot (clé = `CODE#n`)

| Code | Lot 1 | Lot 2 | Lot 3 | Lot 4 | Lot 5 | Nature |
|---|---|---|---|---|---|---|
| B05-GS-03 | 1600000 | 2170000 | 1600000 | 2170000 | 1600000 | [R] DPAO 6.6 p.18 (§4) — montants exacts de la garantie de soumission |
| B05-TP-02 | 40000000 | 54250000 | 40000000 | 54250000 | 40000000 | [D] §5 — minimum = maximum ÷ 2 |
| B05-TP-03 | 80000000 | 108500000 | 80000000 | 108500000 | 80000000 | [D] §5 — maximum = garantie ÷ 2 % |
| B06-EO-12 | 30 | 30 | 30 | 30 | 30 | [R] p.19, p.38, p.50 (§3) — 30 jours au plus par bon de commande |
| B09-LL-01 | MINISTERE FIADANANA | AMBATONDRAZAKA | AMBATONDRAZAKA | FORT DAUPHIN | FORT DAUPHIN | [R] CCAP art. 1 et 17 p.49, 51 (§3) — destination de chaque lot |

### Défauts posés par le référentiel (non issus de la fiche des faits)

| Code | Valeur en base | Provenance |
|---|---|---|
| B03-CQ-09 | 5 | 5 — valeur par défaut du référentiel (V47, formulaire A1 p.23-25 du dossier) ; absente de la fiche des faits |
| B03-CQ-10 | 3 | 3 — valeur par défaut du référentiel (V47, formulaire A3 p.28-30 du dossier) ; absente de la fiche des faits |

### Besoin par lot (`t_fiche_article`, `t_fiche_caracteristique`) — [R] p.40-44 (quantités), p.57-61 (spécifications), p.62-66 (calendrier) — §3

| Lot | N° | Désignation | Unité | Min | Max | Caractéristiques | Nature |
|---|---|---|---|---|---|---|---|
| 1 | 1 | Ordinateur de bureau complet Core i5 | U | 15 | 30 | 8 | [R] §3 |
| 1 | 2 | Onduleur | U | 10 | 20 | 2 | [R] §3 |
| 2 | 1 | Kit de matériels informatiques : ordinateur de bureau complet Core i3 + onduleur + imprimante jet d'encre | U | 8 | 16 | 7 | [R] §3 |
| 2 | 2 | Kit de matériels informatiques : ordinateur de bureau complet Core i5 + onduleur | U | 13 | 26 | 6 | [R] §3 |
| 3 | 1 | Imprimante laser noir multifonction A4 | U | 3 | 6 | 4 | [R] §3 |
| 3 | 2 | Photocopieuse noir A4 et A3 | U | 1 | 2 | 4 | [R] §3 |
| 3 | 3 | Duplicopieur noir et blanc A4 | U | 1 | 2 | 3 | [R] §3 |
| 4 | 1 | Kit de matériels informatiques : ordinateur de bureau complet Core i3 + onduleur + imprimante jet d'encre | U | 8 | 16 | 7 | [R] identique au lot 2 (vérifié identique) |
| 4 | 2 | Kit de matériels informatiques : ordinateur de bureau complet Core i5 + onduleur | U | 13 | 26 | 6 | [R] identique au lot 2 (vérifié identique) |
| 5 | 1 | Imprimante laser noir multifonction A4 | U | 3 | 6 | 4 | [R] identique au lot 3 (vérifié identique) |
| 5 | 2 | Photocopieuse noir A4 et A3 | U | 1 | 2 | 4 | [R] identique au lot 3 (vérifié identique) |
| 5 | 3 | Duplicopieur noir et blanc A4 | U | 1 | 2 | 3 | [R] identique au lot 3 (vérifié identique) |

### Contrôles au moment de la validation

```json
{
  "DATES_ORDRE": {
    "bloquant": 0,
    "avertissement": 0,
    "ok": 1
  },
  "QUANTITES_ORDRE": {
    "bloquant": 0,
    "avertissement": 0,
    "ok": 0
  },
  "GARANTIE_TAUX": {
    "bloquant": 0,
    "avertissement": 0,
    "ok": 5
  },
  "AVANCE_SUP_5_GARANTIE": {
    "bloquant": 0,
    "avertissement": 0,
    "ok": 1
  }
}
```
Bloquants : 0 · avertissements : 0 · ok : 28.

## 6. Documents produits (`t_document_fiche_marche`, version 1)

| Type | Lot | Extension | Fichier | Octets |
|---|---|---|---|---|
| DPAO | — | docx | `DPAO_00001-PPM-AGPM-CNM-2026_303091_v1.docx` | 7947 |
| DPAO | — | pdf | `DPAO_00001-PPM-AGPM-CNM-2026_303091_v1.pdf` | 9592 |
| CCAP | — | docx | `CCAP_00001-PPM-AGPM-CNM-2026_303091_v1.docx` | 7104 |
| CCAP | — | pdf | `CCAP_00001-PPM-AGPM-CNM-2026_303091_v1.pdf` | 7889 |
| AE | 1 | docx | `AE_00001-PPM-AGPM-CNM-2026_303091_lot1_v1.docx` | 4915 |
| AE | 1 | pdf | `AE_00001-PPM-AGPM-CNM-2026_303091_lot1_v1.pdf` | 4303 |
| AE | 2 | docx | `AE_00001-PPM-AGPM-CNM-2026_303091_lot2_v1.docx` | 4932 |
| AE | 2 | pdf | `AE_00001-PPM-AGPM-CNM-2026_303091_lot2_v1.pdf` | 4338 |
| AE | 3 | docx | `AE_00001-PPM-AGPM-CNM-2026_303091_lot3_v1.docx` | 4911 |
| AE | 3 | pdf | `AE_00001-PPM-AGPM-CNM-2026_303091_lot3_v1.pdf` | 4299 |
| AE | 4 | docx | `AE_00001-PPM-AGPM-CNM-2026_303091_lot4_v1.docx` | 4930 |
| AE | 4 | pdf | `AE_00001-PPM-AGPM-CNM-2026_303091_lot4_v1.pdf` | 4336 |
| AE | 5 | docx | `AE_00001-PPM-AGPM-CNM-2026_303091_lot5_v1.docx` | 4910 |
| AE | 5 | pdf | `AE_00001-PPM-AGPM-CNM-2026_303091_lot5_v1.pdf` | 4297 |
| LF | — | docx | `LF_00001-PPM-AGPM-CNM-2026_303091_v1.docx` | 3505 |
| LF | — | pdf | `LF_00001-PPM-AGPM-CNM-2026_303091_v1.pdf` | 3170 |
| A1 | — | docx | `A1_00001-PPM-AGPM-CNM-2026_303091_v1.docx` | 4106 |
| A1 | — | pdf | `A1_00001-PPM-AGPM-CNM-2026_303091_v1.pdf` | 3347 |
| A2 | — | docx | `A2_00001-PPM-AGPM-CNM-2026_303091_v1.docx` | 3316 |
| A2 | — | pdf | `A2_00001-PPM-AGPM-CNM-2026_303091_v1.pdf` | 1762 |
| A3 | — | docx | `A3_00001-PPM-AGPM-CNM-2026_303091_v1.docx` | 4163 |
| A3 | — | pdf | `A3_00001-PPM-AGPM-CNM-2026_303091_v1.pdf` | 3826 |
| A4 | — | docx | `A4_00001-PPM-AGPM-CNM-2026_303091_v1.docx` | 3192 |
| A4 | — | pdf | `A4_00001-PPM-AGPM-CNM-2026_303091_v1.pdf` | 1497 |
| C1 | 1 | docx | `C1_00001-PPM-AGPM-CNM-2026_303091_lot1_v1.docx` | 4017 |
| C1 | 1 | pdf | `C1_00001-PPM-AGPM-CNM-2026_303091_lot1_v1.pdf` | 2536 |
| C1 | 2 | docx | `C1_00001-PPM-AGPM-CNM-2026_303091_lot2_v1.docx` | 4026 |
| C1 | 2 | pdf | `C1_00001-PPM-AGPM-CNM-2026_303091_lot2_v1.pdf` | 2542 |
| C1 | 3 | docx | `C1_00001-PPM-AGPM-CNM-2026_303091_lot3_v1.docx` | 4017 |
| C1 | 3 | pdf | `C1_00001-PPM-AGPM-CNM-2026_303091_lot3_v1.pdf` | 2536 |
| C1 | 4 | docx | `C1_00001-PPM-AGPM-CNM-2026_303091_lot4_v1.docx` | 4026 |
| C1 | 4 | pdf | `C1_00001-PPM-AGPM-CNM-2026_303091_lot4_v1.pdf` | 2542 |
| C1 | 5 | docx | `C1_00001-PPM-AGPM-CNM-2026_303091_lot5_v1.docx` | 4017 |
| C1 | 5 | pdf | `C1_00001-PPM-AGPM-CNM-2026_303091_lot5_v1.pdf` | 2536 |
| C2 | 1 | docx | `C2_00001-PPM-AGPM-CNM-2026_303091_lot1_v1.docx` | 4264 |
| C2 | 1 | pdf | `C2_00001-PPM-AGPM-CNM-2026_303091_lot1_v1.pdf` | 2786 |
| C2 | 2 | docx | `C2_00001-PPM-AGPM-CNM-2026_303091_lot2_v1.docx` | 4278 |
| C2 | 2 | pdf | `C2_00001-PPM-AGPM-CNM-2026_303091_lot2_v1.pdf` | 2795 |
| C2 | 3 | docx | `C2_00001-PPM-AGPM-CNM-2026_303091_lot3_v1.docx` | 4264 |
| C2 | 3 | pdf | `C2_00001-PPM-AGPM-CNM-2026_303091_lot3_v1.pdf` | 2786 |
| C2 | 4 | docx | `C2_00001-PPM-AGPM-CNM-2026_303091_lot4_v1.docx` | 4278 |
| C2 | 4 | pdf | `C2_00001-PPM-AGPM-CNM-2026_303091_lot4_v1.pdf` | 2795 |
| C2 | 5 | docx | `C2_00001-PPM-AGPM-CNM-2026_303091_lot5_v1.docx` | 4264 |
| C2 | 5 | pdf | `C2_00001-PPM-AGPM-CNM-2026_303091_lot5_v1.pdf` | 2786 |
| BP | 1 | xlsx | `BP_00001-PPM-AGPM-CNM-2026_303091_lot1_v1.xlsx` | 4152 |
| BP | 2 | xlsx | `BP_00001-PPM-AGPM-CNM-2026_303091_lot2_v1.xlsx` | 4186 |
| BP | 3 | xlsx | `BP_00001-PPM-AGPM-CNM-2026_303091_lot3_v1.xlsx` | 4225 |
| BP | 4 | xlsx | `BP_00001-PPM-AGPM-CNM-2026_303091_lot4_v1.xlsx` | 4184 |
| BP | 5 | xlsx | `BP_00001-PPM-AGPM-CNM-2026_303091_lot5_v1.xlsx` | 4225 |
| TC | 1 | xlsx | `TC_00001-PPM-AGPM-CNM-2026_303091_lot1_v1.xlsx` | 4615 |
| TC | 2 | xlsx | `TC_00001-PPM-AGPM-CNM-2026_303091_lot2_v1.xlsx` | 4755 |
| TC | 3 | xlsx | `TC_00001-PPM-AGPM-CNM-2026_303091_lot3_v1.xlsx` | 4540 |
| TC | 4 | xlsx | `TC_00001-PPM-AGPM-CNM-2026_303091_lot4_v1.xlsx` | 4754 |
| TC | 5 | xlsx | `TC_00001-PPM-AGPM-CNM-2026_303091_lot5_v1.xlsx` | 4543 |

## 7. Paramètres

| Paramètre | Valeur | Provenance |
|---|---|---|
| FICHE_TAUX_TVA | 20 | paramètre administré (V46), pas une donnée du dossier ; gardé (arbitrage du 26/09) |

