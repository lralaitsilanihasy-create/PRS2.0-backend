-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V111 — ⚠️ 2026-10-14 : manuel révisé pour l'application, lot 2, tranche R9a (§B4, bloquants de la
-- loi n° 13 et n° 14 ; arbitrage Q13 du pilote).
--
-- 1. n° 14 (art. 54) — l'approbation du marché par l'autorité compétente, entre la signature et la
--    notification : autorité, date, horodatage et auteur du geste ; sa pièce est de nature APPROBATION
--    dans t_attribution_piece. La notification est refusée sans elle (409 MARCHE_NON_APPROUVE).
-- 2. n° 13 (titre VIII) — t_parametre RECOURS_SUSPENSIFS : les types de recours qui suspendent la
--    signature, séparés par des virgules ; semé à « REVISION_ARMP,REFERE » (le comportement d'avant),
--    REEXAMEN activable par l'Administrateur.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "APPROBATION_AUTORITE" character varying(200);
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "APPROBATION_DATE" date;
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "APPROUVE_LE" timestamp without time zone;
ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "APPROUVE_PAR" character varying(100);

ALTER TABLE public.t_attribution_piece DROP CONSTRAINT IF EXISTS ck_attribution_piece_nature;
ALTER TABLE public.t_attribution_piece ADD CONSTRAINT ck_attribution_piece_nature CHECK ("NATURE" IN ('MISE_AU_POINT', 'MARCHE_SIGNE',
    'ENREGISTREMENT', 'RECOURS', 'DECISION_RECOURS', 'FISCALE', 'SOCIALE', 'APPROBATION'));

INSERT INTO public.t_parametre ("CLE", "VALEUR", "DATE_MAJ", "IM_ACTEUR")
SELECT 'RECOURS_SUSPENSIFS', 'REVISION_ARMP,REFERE', now(), NULL
WHERE NOT EXISTS (SELECT 1 FROM public.t_parametre WHERE "CLE" = 'RECOURS_SUSPENSIFS');
