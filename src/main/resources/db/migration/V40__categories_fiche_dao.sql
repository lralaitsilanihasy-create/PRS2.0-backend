-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V40 — La fiche DAO a trois catégories (demande front du 2026-09-24, lot 5, §B1 et §B3)
--
-- Second axe du référentiel, à côté du type de marché : la CATÉGORIE de la fiche (FOURNITURES_SERVICES, TRAVAUX,
-- PRESTATIONS_INTELLECTUELLES), dérivée de la nature de la ligne du plan.
--
-- 1. Blocs, rubriques et champs portent CATEGORIES (liste séparée par des virgules, comme TYPES_MARCHE). Les blocs
--    sont la colonne vertébrale commune (les trois catégories) ; tout ce qui est chargé jusqu'ici est de la catégorie
--    Fournitures et Services — SAUF les informations reprises du plan (source PPM, 23 champs, rubriques B01-AC,
--    B02-OB, B02-LV), qui ne dépendent pas de la catégorie : une fiche de travaux relit, elle aussi, l'entité, le
--    montant ou la nature de sa ligne.
-- 2. tr_nature.CATEGORIE_DAO : la correspondance des natures vers les catégories, administrable (écran des
--    natures). Semée par le libellé, selon la proposition du front ; une nature sans catégorie rend ses lignes non
--    préparables, avec un message qui le dit.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_bloc_fiche_marche ADD COLUMN IF NOT EXISTS "CATEGORIES" character varying(80) NOT NULL
    DEFAULT 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES';
ALTER TABLE public.tr_rubrique_fiche_marche ADD COLUMN IF NOT EXISTS "CATEGORIES" character varying(80) NOT NULL
    DEFAULT 'FOURNITURES_SERVICES';
ALTER TABLE public.tr_champ_fiche_marche ADD COLUMN IF NOT EXISTS "CATEGORIES" character varying(80) NOT NULL
    DEFAULT 'FOURNITURES_SERVICES';

UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES'
 WHERE "SOURCE" = 'PPM';
UPDATE public.tr_rubrique_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES'
 WHERE "CODE" IN (SELECT DISTINCT "CODE_RUBRIQUE" FROM public.tr_champ_fiche_marche WHERE "SOURCE" = 'PPM');

ALTER TABLE public.tr_nature ADD COLUMN IF NOT EXISTS "CATEGORIE_DAO" character varying(30);
ALTER TABLE public.tr_nature ADD CONSTRAINT ck_nature_categorie_dao CHECK ("CATEGORIE_DAO" IS NULL
    OR "CATEGORIE_DAO" IN ('FOURNITURES_SERVICES', 'TRAVAUX', 'PRESTATIONS_INTELLECTUELLES'));

UPDATE public.tr_nature SET "CATEGORIE_DAO" = CASE upper(trim("LIBELLE"))
        WHEN 'TRAVAUX' THEN 'TRAVAUX'
        WHEN 'PRESTATIONS INTELLECTUELLES' THEN 'PRESTATIONS_INTELLECTUELLES'
        WHEN 'FOURNITURES' THEN 'FOURNITURES_SERVICES'
        WHEN 'SERVICES' THEN 'FOURNITURES_SERVICES'
        WHEN 'FOURNITURES ET SERVICES' THEN 'FOURNITURES_SERVICES'
        WHEN 'PRESTATIONS DE SERVICE' THEN 'FOURNITURES_SERVICES'
        WHEN 'PRESTATIONS' THEN 'FOURNITURES_SERVICES'
    END
 WHERE "CATEGORIE_DAO" IS NULL;

COMMENT ON COLUMN public.tr_nature."CATEGORIE_DAO" IS
    'Categorie de fiche DAO des lignes de cette nature (FOURNITURES_SERVICES, TRAVAUX, PRESTATIONS_INTELLECTUELLES) ; '
    'NULL : lignes non preparables. Administrable.';
