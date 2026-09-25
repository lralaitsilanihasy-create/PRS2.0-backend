-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V43 — La fiche DAO « à commande » au niveau d'un dossier réel (demande front du 2026-09-25,
-- demande-backend-2026-09-25-dao-a-commande-par-lot.md)
--
-- 1. §B2 — Un champ peut valoir PAR LOT (tr_champ_fiche_marche.PAR_LOT) : ses valeurs s'enregistrent sous
--    « CODE#n » (n = rang du lot au plan) quand la ligne est allotie. Quatre champs du référentiel des fournitures le
--    sont (garantie de soumission, montants minimum et maximum, délai maximum de livraison) ; le fichier de
--    correspondance porte la même information (colonne parLot) pour une base chargée après cette migration.
-- 2. §B2 — L'acte d'engagement étant établi par lot, un document généré porte son LOT (NULL : document commun) ;
--    l'unicité (fiche, type, extension) devient (fiche, type, extension, lot).
-- 3. §B3 — Les rubriques « Composition du dossier » (B04-CD, travaux) et « Remise par voie électronique » (B04-VE,
--    prestations intellectuelles) valent aussi pour les fournitures et services : leurs champs y sont réemployés.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_champ_fiche_marche ADD COLUMN IF NOT EXISTS "PAR_LOT" boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN public.tr_champ_fiche_marche."PAR_LOT" IS
    'Vrai : une valeur par lot (cle CODE#n, n = rang du lot au plan) quand la ligne est allotie ; source SAISIE seulement.';

UPDATE public.tr_champ_fiche_marche SET "PAR_LOT" = true
 WHERE "CODE" IN ('B05-GS-03', 'B05-TP-02', 'B05-TP-03', 'B06-EO-12');

ALTER TABLE public.t_document_fiche_marche ADD COLUMN IF NOT EXISTS "LOT" integer;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_lot CHECK ("LOT" IS NULL OR "LOT" >= 1);
ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT IF EXISTS uq_document_fiche_marche;
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_fiche_marche
    ON public.t_document_fiche_marche ("ID_FICHE", "TYPE", "EXTENSION", COALESCE("LOT", 0));
COMMENT ON COLUMN public.t_document_fiche_marche."LOT" IS
    'Rang du lot pour un document etabli par lot (acte d''engagement d''une ligne allotie) ; NULL : document commun.';

UPDATE public.tr_rubrique_fiche_marche SET "CATEGORIES" = "CATEGORIES" || ',FOURNITURES_SERVICES'
 WHERE "CODE" IN ('B04-CD', 'B04-VE') AND ',' || "CATEGORIES" || ',' NOT LIKE '%,FOURNITURES_SERVICES,%';
