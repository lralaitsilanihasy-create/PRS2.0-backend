-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — lot D4, travaux (demande front du 2026-09-29,
-- demande-backend-2026-09-29-lot-d4-travaux.md §B1, §B2, §B3), avec deux reliquats §B6 :
-- demande-backend-2026-09-29-lot-d2-fournitures.md (B05-TP-03, B08-PA-08).
--
-- §B1   — les six pièces B11-FR-01..06 retirées : le CCAP-T imprime lui-même ses annexes (réactivables).
-- §B2.1 — six champs créés (B02-MW-04, B02-LT-06/07, B04-VL-02, B05-GE-05, B09-BT-01) ; B09-DT-01 prend l'option
--         « À la notification de l'ordre de service de commencer les travaux ».
-- §B2.2 — B02-LT-01 conditionné à alloti = OUI ; B04-OV-02 en DATE_HEURE ; B09-PE-01 retiré (la question de cadrage
--         penalites, B09-PR-01, est ouverte aux travaux par la migration V54).
-- §B3   — harmonisation du contrat-cadre : 89 champs du contrat-cadre des fournitures cités par DPAC-CC/AE-CC ouverts aux
--         travaux ; leurs 47 doublons exacts (même libellé) du contrat-cadre de travaux retirés, valeurs conservées.
-- §B6 fournitures — B05-TP-03 facultatif, libellé « Montant maximum annuel estimé du marché (Ariary) » ; B08-PA-08 retiré.
--
-- ⚠️ À passer APRÈS le redémarrage du serveur : la migration V54 crée la rubrique B09-BT (sans elle, l'insertion de
-- B09-BT-01 échouerait) et élargit les rubriques. Le script s'arrête net si V54 manque.
--
-- Ce n'est PAS une migration : les fichiers de correspondance portent ces valeurs ; ce script aligne une base où ils ont
-- déjà été chargés. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-29-lot-d4-travaux.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.tr_rubrique_fiche_marche WHERE "CODE" = 'B09-BT') THEN
        RAISE EXCEPTION 'Rubrique B09-BT absente : redémarrer le serveur (migration V54) avant ce script.';
    END IF;
END $$;

-- §B2.1 — les six champs.
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "CONTROLE", "OPTIONS", "PAR_LOT", "ACTIF") VALUES
    ('B02-MW-04', 'B02-MW', 4, 'Maître d''ouvrage délégué : nom et coordonnées', 'TEXTE_LONG', 'SAISIE', 'AE', 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, false, true),
    ('B02-LT-06', 'B02-LT', 6, 'Délai d''affermissement de la tranche conditionnelle 1', 'TEXTE', 'SAISIE', 'CCAP', 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', 'tranches = OUI', false, NULL, NULL, false, true),
    ('B02-LT-07', 'B02-LT', 7, 'Délai d''affermissement de la tranche conditionnelle 2', 'TEXTE', 'SAISIE', 'CCAP', 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', 'tranches = OUI', false, NULL, NULL, false, true),
    ('B04-VL-02', 'B04-VL', 2, 'Visite des lieux obligatoire', 'OUI_NON', 'SAISIE', 'DPAO', 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, false, true),
    ('B05-GE-05', 'B05-GE', 5, 'Taux de la garantie de bonne exécution (%, au plus 5 %)', 'POURCENTAGE', 'SAISIE', 'CCAP', 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, false, true),
    ('B09-BT-01', 'B09-BT', 1, 'Travaux de bâtiment (CPC, TBM, responsabilité décennale)', 'OUI_NON', 'SAISIE', 'CCAP', 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, false, true)
ON CONFLICT ("CODE") DO UPDATE SET "CODE_RUBRIQUE" = EXCLUDED."CODE_RUBRIQUE", "RANG" = EXCLUDED."RANG", "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "SOURCE" = EXCLUDED."SOURCE", "DOCUMENT_MAITRE" = EXCLUDED."DOCUMENT_MAITRE", "TYPES_MARCHE" = EXCLUDED."TYPES_MARCHE", "CATEGORIES" = EXCLUDED."CATEGORIES", "CONDITION" = EXCLUDED."CONDITION", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE", "CONTROLE" = EXCLUDED."CONTROLE", "OPTIONS" = EXCLUDED."OPTIONS", "PAR_LOT" = EXCLUDED."PAR_LOT", "ACTIF" = EXCLUDED."ACTIF";

UPDATE public.tr_champ_fiche_marche SET "OPTIONS" = 'À la notification de l''approbation à l''entrepreneur,À la notification de la décision d''affermissement de la tranche conditionnelle,À la notification de la tranche ferme au titulaire,À la notification de l''ordre de service de commencer les travaux'
 WHERE "CODE" = 'B09-DT-01';

-- §B2.2 — les corrections ; §B1 — les six pièces retirées.
UPDATE public.tr_champ_fiche_marche SET "CONDITION" = 'alloti = OUI' WHERE "CODE" = 'B02-LT-01';
UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'DATE_HEURE' WHERE "CODE" = 'B04-OV-02';
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false
 WHERE "CODE" IN ('B09-PE-01', 'B11-FR-01', 'B11-FR-02', 'B11-FR-03', 'B11-FR-04', 'B11-FR-05', 'B11-FR-06');

-- §B3 — les champs du contrat-cadre ouverts aux travaux.
UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX'
 WHERE "CODE" IN (
                  'B02-AL-02', 'B02-DC-01', 'B02-DC-02', 'B02-DC-03', 'B02-DC-04', 'B02-OE-01', 'B02-PC-01', 'B02-PC-02',
                  'B02-PC-03', 'B02-SG-01', 'B02-SG-02', 'B02-SG-03', 'B02-SG-04', 'B04-CP-01', 'B04-CP-02', 'B04-CP-03',
                  'B04-CP-04', 'B04-CP-05', 'B04-CP-06', 'B04-CP-07', 'B04-CP-08', 'B04-DS-01', 'B04-DS-04', 'B04-DS-05',
                  'B04-DS-07', 'B04-DS-08', 'B04-DS-09', 'B04-DS-10', 'B04-PO-01', 'B04-PO-02', 'B04-RC-01', 'B04-RC-02',
                  'B04-RQ-01', 'B04-RQ-02', 'B04-RQ-03', 'B05-MT-01', 'B05-MT-02', 'B05-PM-01', 'B05-PM-02', 'B05-PM-03',
                  'B05-PM-04', 'B05-PM-05', 'B05-UM-01', 'B06-AN-03', 'B06-CA-01', 'B06-SC-02', 'B06-SO-05', 'B07-DE-01',
                  'B07-DE-02', 'B07-DE-03', 'B07-DU-02', 'B07-DU-03', 'B07-DU-04', 'B07-DU-05', 'B07-DU-07', 'B07-FS-01',
                  'B07-FS-02', 'B07-MA-01', 'B07-MA-02', 'B07-MA-04', 'B07-MA-06', 'B07-PE-01', 'B07-PE-02', 'B07-PE-03',
                  'B07-PS-02', 'B07-PS-03', 'B07-TN-01', 'B07-TN-02', 'B08-FI-02', 'B08-FI-05', 'B08-FP-01', 'B08-FP-02',
                  'B08-FP-04', 'B09-AU-01', 'B09-AU-03', 'B09-EA-01', 'B09-EA-02', 'B09-GP-01', 'B09-GP-02', 'B09-GP-03',
                  'B09-GP-04', 'B09-GP-05', 'B09-VA-01', 'B09-VA-02', 'B10-MT-01', 'B10-MT-02', 'B10-RS-02', 'B10-RS-03',
                  'B10-VR-01'
                 );

-- §B3 — leurs doublons du contrat-cadre de travaux retirés (valeurs saisies conservées).
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false
 WHERE "CODE" IN (
                  'B07-DT-04', 'B02-SW-01', 'B02-SW-02', 'B04-CT-01', 'B04-CT-02', 'B04-CT-03', 'B04-CT-04', 'B04-DK-03',
                  'B04-DK-04', 'B04-RT-01', 'B05-MC-01', 'B05-MC-02', 'B05-PX-01', 'B05-PX-02', 'B05-PX-03', 'B05-UT-01',
                  'B06-ET-02', 'B07-XE-01', 'B07-XE-02', 'B07-XE-03', 'B07-DT-02', 'B07-DT-06', 'B07-FT-01', 'B07-FT-02',
                  'B07-AT-01', 'B07-AT-02', 'B07-PY-01', 'B07-PY-02', 'B07-PY-03', 'B07-PT-02', 'B07-PT-03', 'B07-NC-01',
                  'B07-NC-02', 'B08-AT-05', 'B08-FT-01', 'B08-FT-04', 'B09-AT-01', 'B09-AT-03', 'B09-XM-01', 'B09-XM-02',
                  'B09-GQ-01', 'B09-GQ-02', 'B09-VT-01', 'B09-VT-02', 'B10-MC-01', 'B10-MC-02', 'B10-LT-01'
                 );

-- §B6 fournitures.
UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false, "LIBELLE" = 'Montant maximum annuel estimé du marché (Ariary)'
 WHERE "CODE" = 'B05-TP-03';
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" = 'B08-PA-08';

-- Contrôles (information) : les valeurs saisies sur les champs retirés, conservées par la fiche.
SELECT "CODE_CHAMP", count(*) AS "VALEURS_CONSERVEES" FROM public.t_fiche_marche_valeur v
  JOIN public.tr_champ_fiche_marche c ON c."CODE" = v."CODE_CHAMP"
 WHERE c."ACTIF" = false AND (c."CODE" IN ('B09-PE-01', 'B08-PA-08') OR c."CODE" LIKE 'B11-FR-%')
 GROUP BY 1 ORDER BY 1;

SELECT count(*) FILTER (WHERE "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX' AND "TYPES_MARCHE" LIKE '%CONTRAT_CADRE%') AS "CC_OUVERTS_AUX_TRAVAUX",
       count(*) FILTER (WHERE "CODE" IN ('B02-MW-04', 'B02-LT-06', 'B02-LT-07', 'B04-VL-02', 'B05-GE-05', 'B09-BT-01')) AS "CREES"
  FROM public.tr_champ_fiche_marche;

SELECT "CODE", "TYPE", "ACTIF", "OBLIGATOIRE", coalesce("CONDITION", '-') AS "CONDITION", left("LIBELLE", 60) AS "LIBELLE"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B02-MW-04', 'B02-LT-01', 'B02-LT-06', 'B02-LT-07', 'B04-VL-02', 'B04-OV-02', 'B05-GE-05', 'B09-BT-01',
                  'B09-DT-01', 'B09-PE-01', 'B09-PR-01', 'B05-TP-03', 'B08-PA-08', 'B11-FR-01')
 ORDER BY 1;

COMMIT;
