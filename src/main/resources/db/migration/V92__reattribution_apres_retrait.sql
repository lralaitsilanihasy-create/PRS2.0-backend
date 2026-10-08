-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V92 — Attribution (lot 2), tranche 2d-2 (demande front du 2026-10-07, `demande-backend-2026-10-07-attribution-notification.md`,
-- Q8 ; arbitrages du pilote : réattribution au candidat suivant après le retrait faute de pièces fiscales et sociales — sa
-- post-qualification (ou sa négociation, en prestations intellectuelles), une offre encore valide, une nouvelle information, un
-- nouveau délai ; nouveau rapport complet ; sans suivant éligible, l'infructuosité reste interdite (art. 56-VI)).
--
-- 1. ARCHIVE_LE sur les pièces, les recours et les lettres d'un lot : ceux du cycle retiré ne comptent plus pour le nouveau.
-- 2. t_attribution_reprise : TYPE (REPRISE après avis défavorable, REATTRIBUTION après retrait) et NOTE (validité des offres).
-- 3. t_negociation : l'état RETIREE (la négociation réussie dont le marché a été retiré).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_attribution_piece ADD COLUMN IF NOT EXISTS "ARCHIVE_LE" timestamp without time zone;
ALTER TABLE public.t_attribution_recours ADD COLUMN IF NOT EXISTS "ARCHIVE_LE" timestamp without time zone;
ALTER TABLE public.t_attribution_lettre ADD COLUMN IF NOT EXISTS "ARCHIVE_LE" timestamp without time zone;

ALTER TABLE public.t_attribution_reprise ADD COLUMN IF NOT EXISTS "TYPE" character varying(15) NOT NULL DEFAULT 'REPRISE';
ALTER TABLE public.t_attribution_reprise ADD COLUMN IF NOT EXISTS "NOTE" text;

ALTER TABLE public.t_negociation DROP CONSTRAINT IF EXISTS ck_negociation_etat;
ALTER TABLE public.t_negociation ADD CONSTRAINT ck_negociation_etat CHECK ("ETAT" IN ('EN_COURS', 'REUSSIE', 'ECHOUEE', 'RETIREE'));
