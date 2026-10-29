-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V149 — ⚠️ 2026-10-29 : demande front « chaine-attribution », décision du pilote « suivre les textes en vigueur » (B1, B2, B4 ;
-- circulaire de régulation des marchés publics 2024, § XV et p. 31 ; loi n° 2016-055, art. 54).
--
-- 1. t_attribution — le VISA du contrôleur financier, entre la signature et l'approbation : date, référence facultative, ou
--    dispense (« exception de contrôle préalable, CHED ») et son motif ; le geste (VISE_LE, VISE_PAR).
-- 2. t_attribution — le document de la DÉCISION D'ATTRIBUTION provisoire, produit au geste « Attribuer » (PDF et Word).
-- 3. t_attribution_piece — la nature VISA_CF (le marché visé).
-- 4. t_type_piece_jointe — « Décision d'attribution » (code DECISION_ATTRIBUTION, famille DDM), jointe d'office au dossier de
--    marché à l'attribution (identifiant explicite puis recalage de la séquence, cf. V109).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "VISA_DATE" date;
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "VISA_REFERENCE" character varying(100);
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "VISA_DISPENSE" boolean NOT NULL DEFAULT false;
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "VISA_MOTIF" text;
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "VISE_LE" timestamp without time zone;
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "VISE_PAR" character varying(100);
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "DECISION_PDF" bytea;
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "DECISION_DOCX" bytea;

ALTER TABLE public.t_attribution_piece DROP CONSTRAINT IF EXISTS ck_attribution_piece_nature;
ALTER TABLE public.t_attribution_piece ADD CONSTRAINT ck_attribution_piece_nature CHECK ("NATURE" IN ('MISE_AU_POINT', 'MARCHE_SIGNE',
    'ENREGISTREMENT', 'RECOURS', 'DECISION_RECOURS', 'FISCALE', 'SOCIALE', 'APPROBATION', 'ACTE_ENGAGEMENT_ORIGINAL', 'VISA_CF'));

INSERT INTO public.t_type_piece_jointe ("ID_TYPE_PIECE", "ID_TYPE_DOSSIER", "LIBELLE_PIECE", "OBLIGATOIRE", "ORDRE", "CODE")
SELECT COALESCE((SELECT MAX("ID_TYPE_PIECE") FROM public.t_type_piece_jointe), 0) + 1, 'DDM', 'Décision d''attribution',
       false, 99, 'DECISION_ATTRIBUTION'
WHERE NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe WHERE "CODE" = 'DECISION_ATTRIBUTION')
  AND EXISTS (SELECT 1 FROM public.tr_type_dossier WHERE "ID_TYPE_DOSSIER" = 'DDM');

SELECT setval(pg_get_serial_sequence('public.t_type_piece_jointe', 'ID_TYPE_PIECE'),
              GREATEST((SELECT COALESCE(MAX("ID_TYPE_PIECE"), 0) FROM public.t_type_piece_jointe), 1));
