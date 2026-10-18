-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V135 — La grille d'examen du DAO cite aussi les informations d'une fiche de travaux (demande front du 2026-10-18
-- `demande-backend-2026-10-18-grille-travaux.md`, accord du pilote le 18/10 ; suite de V133).
--
-- Les points détaillés de DAOO (saisis par l'Administrateur le 26/09) ont été rédigés sur le DAO de fournitures : sur une fiche de
-- travaux à quantités fixes ou à commandes, 33 des 48 codes cités n'y mènent à rien. Chaque notion (une parenthèse du texte) donne
-- désormais aussi le code des travaux (« travaux : B05-GQ-03 ») ; une notion que la fiche de travaux ne porte pas le dit
-- (« travaux : non saisi »), et réciproquement pour un code des seuls travaux (« fournitures : non saisi »). Mêmes points, même
-- grille : l'écran ne propose que les codes que la fiche porte. Q1 : « Spécifications techniques » cite, en travaux, le DQE
-- (rubrique B12-DQ) et les pièces jointes en PDF.
--
-- La description passe de 255 à 1000 caractères (les textes en faisaient déjà jusqu'à 248). Un texte n'est remplacé que s'il
-- est encore celui de V133 (une modification de l'Administrateur n'est pas écrasée). Idempotente ; sans effet sur une base sans
-- ces points (la base de test).
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_points_ctrl ALTER COLUMN "DECRIPT_POINT_CTRL" TYPE varchar(1000);

DROP TABLE IF EXISTS v135_texte;
CREATE TEMPORARY TABLE v135_texte (libelle varchar(255), forme varchar(20), ancien varchar(1000), nouveau varchar(1000)) ON COMMIT DROP;

INSERT INTO v135_texte (libelle, forme, ancien, nouveau) VALUES
('Allotissement', 'AUTRE',
 'Nombre et intitulés des lots, attribution lot par lot ou en totalité, offres partielles, nombre maximal de lots par candidat : identiques entre le DPAO, l''acte d''engagement et le besoin (B02-LV, B02-AU-02, B02-AU-07, B12) ?',
 'Nombre et intitulés des lots (B02-LV), attribution lot par lot ou en totalité, offres partielles (B02-AU-02 ; travaux : B02-LT-02), nombre maximal de lots par candidat (B02-AU-07 ; fournitures : non saisi) : identiques entre le DPAO, l''acte d''engagement et le besoin (B12) ?'),
('Montants et estimation', 'AUTRE',
 'Minimum et maximum de chaque lot (B05-TP-03) dans l''enveloppe du plan ; somme des maxima ≤ estimation de la ligne ; cohérence avec les quantités min/max des articles (B12) ?',
 'Minimum et maximum de chaque lot (B05-TP-03 ; travaux : montant par lot, B02-LV-03) dans l''enveloppe du plan ; somme des maxima ≤ estimation de la ligne ; cohérence avec les quantités min/max des articles (B12) ?'),
('Garantie de soumission', 'AUTRE',
 'Formes admises (B05-GS-02) conformes au CMP ; montant par lot (B05-GS-03) entre 1 et 2 % du maximum du lot ; validité (B05-GS-04) couvrant la validité des offres plus trente jours (B04-VO-01) ?',
 'Formes admises (B05-GS-02 ; travaux : B05-GQ-02) conformes au CMP ; montant par lot (B05-GS-03 ; travaux : B05-GQ-03) entre 1 et 2 % du maximum du lot ; validité (B05-GS-04 ; travaux : non saisi) couvrant la validité des offres plus trente jours (B04-VO-01) ?'),
('Délais de la consultation', 'AUTRE',
 'Validité des offres (B04-VO-01), délais d''éclaircissement (B04-DE-02, B04-DE-03), date et heure de remise et d''ouverture (B04-LR-03, B04-LR-04, B04-OP-02, B04-OP-03) : cohérents entre eux, avec le calendrier du plan et le délai légal de publicité ?',
 'Validité des offres (B04-VO-01), délais d''éclaircissement (B04-DE-02, B04-DE-03 ; travaux : B04-EQ-01, B04-EQ-02), date et heure de remise et d''ouverture (B04-LR-03, B04-LR-04, B04-OP-02, B04-OP-03 ; travaux : B04-OV-01, B04-OV-02) : cohérents entre eux, avec le calendrier du plan et le délai légal de publicité ?'),
('Candidats : pièces et capacités', 'AUTRE',
 'Pièces exigées (B03-CQ-01, B04-CO-01), fiches de renseignements jointes (B04-CD-01), capacités technique et financière demandées (B03-CQ-02, B03-CQ-03, B03-CQ-09, B03-CQ-10) : proportionnées à l''objet et non discriminantes ?',
 'Pièces exigées (B03-CQ-01, B04-CO-01 ; travaux : B04-PI-01), fiches de renseignements jointes (B04-CD-01), capacités technique et financière demandées (B03-CQ-02, B03-CQ-03, B03-CQ-09, B03-CQ-10 ; travaux : B03-QT-07, B03-QT-08, B03-QT-13, B03-QT-14) : proportionnées à l''objet et non discriminantes ?'),
('Évaluation et attribution', 'AUTRE',
 'Évaluation par lot ou sur l''ensemble (B06-EO-01), critères additionnels (B06-EO-02), détermination de l''offre évaluée, offres anormalement hautes ou basses (B06-EO-07), préférence nationale (B03-CQ-08, B06-EO-09) : conformes au CMP ?',
 'Évaluation par lot ou sur l''ensemble (B06-EO-01 ; travaux : B06-EV-01), critères additionnels (B06-EO-02 ; travaux : non saisi), détermination de l''offre évaluée, offres anormalement hautes ou basses (B06-EO-07 ; fournitures : non saisi), préférence nationale (B03-CQ-08, B06-EO-09 ; travaux : B06-PN-01, B06-PN-02) : conformes au CMP ?'),
('Paiements, avance et garanties financières', 'AUTRE',
 'Avance (taux ≤ 20 %, garantie de restitution, remboursement : B08-AV), acomptes (B08-PA-05), garantie de bonne exécution et retenue (B08-GB, B08-RG), intérêts moratoires (B08-IM-01), délai de paiement : conformes au CCAG ?',
 'Avance (taux ≤ 20 %, garantie de restitution, remboursement : B08-AV ; travaux : B08-AF-01, B05-GA-01), acomptes (B08-PA-05 ; travaux : B08-AP-01, B08-MR-05), garantie de bonne exécution et retenue (B08-GB, B08-RG ; travaux : B05-GE-01, B05-GE-05, B05-RG-01, B05-RG-02), intérêts moratoires (B08-IM-01 ; travaux : non saisi), délai de paiement : conformes au CCAG ?'),
('Pénalités, garantie et dérogations', 'AUTRE',
 'Régime et plafond des pénalités (B09-PR-02, B09-PR-03), délai de garantie (B09-DG-01, B09-DG-04) ; toute dérogation au CCAG est-elle justifiée ET récapitulée à l''article « dérogations » du CCAP (B10-DD-01) ?',
 'Régime et plafond des pénalités (B09-PR-02, B09-PR-03 ; travaux : B09-PR-01, B09-PE-02, B09-PE-03), délai de garantie (B09-DG-01, B09-DG-04 ; travaux : B09-GT-01) ; toute dérogation au CCAG est-elle justifiée ET récapitulée à l''article « dérogations » du CCAP (B10-DD-01 ; travaux : B10-DR-01) ?'),
('Exécution et livraison', 'AUTRE',
 'Délai de livraison par lot (B06-EO-12), lieux de livraison (B09-LL-01), passation des commandes (B09-DX-03), transport, inspection et réception (B09-RT-01, B09-IV-01, B09-DI-01) : réalistes et cohérents avec le besoin ?',
 'Délai de livraison par lot (B06-EO-11, B06-EO-12 ; travaux : délai d''exécution, B09-DL-01), lieux de livraison (B09-LL-01 ; travaux : B02-OT-03), passation des commandes (B09-DX-03 ; travaux : non saisi), transport, inspection et réception (B09-RT-01, B09-IV-01, B09-DI-01 ; travaux : B09-VQ-01, B09-RP-04) : réalistes et cohérents avec le besoin ?'),
('Spécifications techniques', NULL,
 'Articles, quantités minimum et maximum, caractéristiques exigées (B12) : précises, mesurables, sans référence à une marque ou à un fournisseur, cohérentes avec les lots et les montants ?',
 'Articles, quantités minimum et maximum, caractéristiques exigées (B12 ; travaux : DQE, B12-DQ, descriptif et plans joints en PDF) : précises, mesurables, sans référence à une marque ou à un fournisseur, cohérentes avec les lots et les montants ?'),
('Cohérence des mentions du dossier', 'AUTRE',
 'Références, intitulés, dates et mentions reprises d''un document à l''autre — numéro de l''appel d''offres sur les plis (B04-RO-02), description de l''objet (B02-OB-02), dates laissées en blanc, restes d''un autre dossier — sans contradiction ?',
 'Références, intitulés, dates et mentions reprises d''un document à l''autre — numéro de l''appel d''offres sur les plis (B04-RO-02 ; travaux : B04-FP-02), description de l''objet (B02-OB-02 ; travaux : B02-OT-02), dates laissées en blanc, restes d''un autre dossier — sans contradiction ?'),
('Garantie de soumission', 'CONTRAT_CADRE',
 'Garantie exigée et forme de remise (B05-GS-01, B05-GS-10) conformes au CMP ; montant (B05-GS-03 ; travaux : B05-GQ-03) entre 1 et 2 % du montant indicatif ; modèle joint (B04-CD-02) ; validité des offres (B04-VO-01) ?',
 'Garantie exigée et forme de remise (B05-GS-01, B05-GS-10 ; travaux : non saisi) conformes au CMP ; montant (B05-GS-03 ; travaux : B05-GQ-03) entre 1 et 2 % du montant indicatif ; modèle joint (B04-CD-02) ; validité des offres (B04-VO-01) ?');

UPDATE public.tr_points_ctrl p SET "DECRIPT_POINT_CTRL" = t.nouveau
FROM v135_texte t
WHERE p."ID_TYPE_DOSSIER" = 'DMC' AND p."ID_SOUS_TYPE" = 'DAOO' AND p."LIBEL_POINT_CTRL" = t.libelle
  AND p."FORME" IS NOT DISTINCT FROM t.forme AND p."DECRIPT_POINT_CTRL" = t.ancien;

DROP TABLE v135_texte;
