-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V123 — ⚠️ 2026-10-16 : demande front « marchés subséquents », tranche MT-b2 (plusieurs titulaires : pièces, signature,
-- notification, avis, retrait ; réponses du pilote du 15/10 ; décret n° 2019-1064, art. 15, 17, 18).
--
-- 1. Les pièces de l'attributaire (fiscale, sociale, original de l'acte d'engagement) se rattachent à l'OFFRE qui les produit :
--    chaque titulaire d'un contrat-cadre multi-attributaire a les siennes (réponse 1). Reprise : les pièces existantes de ces trois
--    natures sont celles de l'attributaire du lot.
-- 2. Un titulaire se retire seul (réponse 2 : le contrat-cadre continue avec les autres) et accuse seul la réception de la
--    notification.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_attribution_piece ADD COLUMN IF NOT EXISTS "ID_OFFRE" character varying(36);

UPDATE public.t_attribution_piece p
   SET "ID_OFFRE" = a."ID_OFFRE_ATTRIBUEE"
  FROM public.t_attribution a
 WHERE a."ID_DMC" = p."ID_DMC" AND a."LOT" = p."LOT"
   AND p."ID_OFFRE" IS NULL AND a."ID_OFFRE_ATTRIBUEE" IS NOT NULL
   AND p."NATURE" IN ('FISCALE', 'SOCIALE', 'ACTE_ENGAGEMENT_ORIGINAL');

ALTER TABLE public.t_attribution_titulaire ADD COLUMN IF NOT EXISTS "RETIRE_LE" timestamp without time zone;
ALTER TABLE public.t_attribution_titulaire ADD COLUMN IF NOT EXISTS "RETIRE_PAR" character varying(100);
ALTER TABLE public.t_attribution_titulaire ADD COLUMN IF NOT EXISTS "MOTIF_RETRAIT" text;
ALTER TABLE public.t_attribution_titulaire ADD COLUMN IF NOT EXISTS "NOTIFICATION_RECUE_LE" timestamp without time zone;
