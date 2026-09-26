-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel des entités — le sigle de l'entité 11 du jeu 2463 (demande front du 2026-09-26,
-- demande-backend-2026-09-26-sigle-entite.md, §B1 : « la seule exception utile est l'entité 11 du jeu 2463 »).
--
-- Ce n'est PAS une migration : les sigles se posent à la main (Administrateur → Entités) ou, pour le jeu 2463, par le
-- script de rejeu (docs/export/2463/rejouer-2463.mjs, étape 0, PUT /api/entite-contracts/11). Ce script fait la même
-- chose en SQL sur une base déjà chargée. Schéma : colonne SIGLE de la migration V48, à lancer AVANT.
--
-- Le sigle ne sert qu'aux PROCHAINES références (nouvelle série `(PPM_REF, MESupReS, année)`) : la référence déjà
-- attribuée au plan du jeu (00001/MLSRS/PPM-AGPM/2026) ne change pas. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-26-sigle-entite-11.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_entite_contract SET "SIGLE" = 'MESupReS'
 WHERE "ID_ENTITE_CONTRACT" = 11 AND "SIGLE" IS DISTINCT FROM 'MESupReS';

SELECT "ID_ENTITE_CONTRACT", "LIBELLE_ENTITE", coalesce("SIGLE", '-') AS "SIGLE" FROM public.tr_entite_contract WHERE "ID_ENTITE_CONTRACT" = 11;

COMMIT;
