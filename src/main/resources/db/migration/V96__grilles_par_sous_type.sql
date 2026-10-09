-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V96 — Le référentiel du contrôle aligné sur le Manuel de contrôle a priori, tranche M3 : les grilles par sous-type (demande front du
-- 2026-10-08 `demande-backend-2026-10-08-manuel-controle-a-priori.md`, §B3 ; arbitrages du pilote du 08/10 : les 5 points du manuel
-- s'ajoutent à la grille des plans ; un point conditionné reste servi, à examiner, sur un dossier sans fiche).
--
-- 1. tr_points_ctrl : la condition d'un point — CATEGORIE de la fiche (FOURNITURES_SERVICES, TRAVAUX, PRESTATIONS_INTELLECTUELLES) et
--    FORME (CONTRAT_CADRE ; AUTRE = toute autre forme).
-- 2. tr_sous_type_dossier : GRILLE_BASE (le sous-type dont la grille s'ajoute : DAOR = DAOO + ses points) et GRILLE_PROPRE (les points
--    communs de la famille ne s'y appliquent pas : MPI, MGG, DC, AVN…).
-- Les points eux-mêmes sont semés au démarrage (PointsCtrlManuelSeeder), comme ceux du dossier de marché : un point existant n'est
-- jamais réécrit.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_points_ctrl ADD COLUMN IF NOT EXISTS "CATEGORIE" character varying(40);
ALTER TABLE public.tr_points_ctrl ADD COLUMN IF NOT EXISTS "FORME" character varying(20);
ALTER TABLE public.tr_points_ctrl DROP CONSTRAINT IF EXISTS ck_points_ctrl_forme;
ALTER TABLE public.tr_points_ctrl ADD CONSTRAINT ck_points_ctrl_forme CHECK ("FORME" IS NULL OR "FORME" IN ('CONTRAT_CADRE', 'AUTRE'));

ALTER TABLE public.tr_sous_type_dossier ADD COLUMN IF NOT EXISTS "GRILLE_BASE" character varying(20);
ALTER TABLE public.tr_sous_type_dossier ADD COLUMN IF NOT EXISTS "GRILLE_PROPRE" boolean NOT NULL DEFAULT false;

UPDATE public.tr_sous_type_dossier SET "GRILLE_BASE" = 'DAOO' WHERE "ID_SOUS_TYPE" IN ('DAOOI', 'DAOOPREQUAL', 'DAOR');
UPDATE public.tr_sous_type_dossier SET "GRILLE_BASE" = 'DAOR' WHERE "ID_SOUS_TYPE" = 'DAORI';
UPDATE public.tr_sous_type_dossier SET "GRILLE_BASE" = 'MAOO' WHERE "ID_SOUS_TYPE" IN ('MAOOI', 'MAOOPREQUAL', 'MAOR');
UPDATE public.tr_sous_type_dossier SET "GRILLE_BASE" = 'MAOR' WHERE "ID_SOUS_TYPE" = 'MAORI';
UPDATE public.tr_sous_type_dossier SET "GRILLE_PROPRE" = true
 WHERE "ID_SOUS_TYPE" IN ('MPI', 'MGG', 'DC', 'RJ', 'DPREQUAL', 'DP', 'AVN', 'DSS');
