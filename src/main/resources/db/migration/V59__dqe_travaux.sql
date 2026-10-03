-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V59 — Le DQE des travaux dans la fiche (demande front du 2026-10-02,
-- demande-backend-2026-10-02-fiche-travaux-dqe-seuils.md, §B1).
--
-- 1. Le besoin (V45) s'ouvre à la catégorie TRAVAUX : le bloc B12 l'admet, avec sa rubrique propre B12-DQ « Détail
--    quantitatif et estimatif, par lot » (B12-BE reste celle des fournitures).
-- 2. Quantités à deux décimales (« 2 054,50 » m³) : integer → numeric(15,2), pour toutes les catégories ; les valeurs
--    entières existantes sont conservées telles quelles.
-- 3. Ce qu'un article de travaux porte de plus : numéro de prix, série (code et intitulé), libellé du bordereau (« Le
--    mètre cube »), prix soumis à sous-détail, plafond en pourcentage du montant des travaux. Hors travaux : NULL / false.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_fiche_article ALTER COLUMN "QUANTITE_MIN" TYPE numeric(15, 2);
ALTER TABLE public.t_fiche_article ALTER COLUMN "QUANTITE_MAX" TYPE numeric(15, 2);
ALTER TABLE public.t_fiche_article ALTER COLUMN "QUANTITE" TYPE numeric(15, 2);

ALTER TABLE public.t_fiche_article ADD COLUMN IF NOT EXISTS "NUMERO_PRIX" character varying(10);
ALTER TABLE public.t_fiche_article ADD COLUMN IF NOT EXISTS "SERIE" character varying(10);
ALTER TABLE public.t_fiche_article ADD COLUMN IF NOT EXISTS "SERIE_LIBELLE" character varying(200);
ALTER TABLE public.t_fiche_article ADD COLUMN IF NOT EXISTS "LIBELLE_BORDEREAU" character varying(200);
ALTER TABLE public.t_fiche_article ADD COLUMN IF NOT EXISTS "SOUS_DETAIL" boolean NOT NULL DEFAULT false;
ALTER TABLE public.t_fiche_article ADD COLUMN IF NOT EXISTS "PLAFOND" numeric(5, 2);
ALTER TABLE public.t_fiche_article DROP CONSTRAINT IF EXISTS ck_fiche_article_plafond;
ALTER TABLE public.t_fiche_article ADD CONSTRAINT ck_fiche_article_plafond
    CHECK ("PLAFOND" IS NULL OR ("PLAFOND" >= 0 AND "PLAFOND" <= 100));

COMMENT ON COLUMN public.t_fiche_article."NUMERO_PRIX" IS 'Travaux : numero de prix au DQE (unique dans le lot).';
COMMENT ON COLUMN public.t_fiche_article."SERIE" IS 'Travaux : serie (chapitre) du DQE qui regroupe les articles.';
COMMENT ON COLUMN public.t_fiche_article."SERIE_LIBELLE" IS 'Travaux : intitule de la serie (le meme pour toute la serie du lot).';
COMMENT ON COLUMN public.t_fiche_article."LIBELLE_BORDEREAU" IS 'Travaux a prix unitaires : libelle du bordereau (Le metre cube).';
COMMENT ON COLUMN public.t_fiche_article."SOUS_DETAIL" IS 'Travaux : prix soumis a sous-detail (annexe 3 de l AE).';
COMMENT ON COLUMN public.t_fiche_article."PLAFOND" IS 'Travaux : au plus n % du montant des travaux.';

UPDATE public.tr_bloc_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX'
 WHERE "CODE" = 'B12' AND "CATEGORIES" = 'FOURNITURES_SERVICES';

INSERT INTO public.tr_rubrique_fiche_marche
    ("CODE", "CODE_BLOC", "CODE_COURT", "LIBELLE", "RANG", "DOCUMENT_MAITRE", "NB_ATTENDU", "TYPES_MARCHE", "CATEGORIES")
VALUES ('B12-DQ', 'B12', 'DQ', 'Détail quantitatif et estimatif, par lot', 2, 'DPAO', 0,
        'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'TRAVAUX')
ON CONFLICT ("CODE") DO NOTHING;
