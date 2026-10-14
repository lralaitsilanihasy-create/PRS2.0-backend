-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V113 — ⚠️ 2026-10-14 : manuel révisé pour l'application, lot 2, tranche R10 (§B5, H2 du pilote :
-- « le dépôt scellé vaut signature pendant la procédure ; l'original signé n'est exigé que de
-- l'attributaire, avant la signature du marché »).
--
-- 1. t_attribution_piece : nature ACTE_ENGAGEMENT_ORIGINAL — l'original signé de l'acte d'engagement,
--    déposé par l'attributaire ou par la PRMP qui l'a reçu, vérifié par la PRMP ; DATE_DELIVRANCE porte
--    la date de sa remise. Exigé avant la signature d'un marché de procédure en ligne (409
--    AE_ORIGINAL_MANQUANT).
-- 2. t_parametre_interne_procedure.SIGNATURE_OFFRE : SCELLE_PLUS_ORIGINAL (défaut, nul) ou CERTIFIEE
--    (réservé : sans effet tant que la signature certifiée n'est pas branchée).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_attribution_piece ALTER COLUMN "NATURE" TYPE character varying(30);   -- le code fait 24 caractères
ALTER TABLE public.t_attribution_piece DROP CONSTRAINT IF EXISTS ck_attribution_piece_nature;
ALTER TABLE public.t_attribution_piece ADD CONSTRAINT ck_attribution_piece_nature CHECK ("NATURE" IN ('MISE_AU_POINT', 'MARCHE_SIGNE',
    'ENREGISTREMENT', 'RECOURS', 'DECISION_RECOURS', 'FISCALE', 'SOCIALE', 'APPROBATION', 'ACTE_ENGAGEMENT_ORIGINAL'));

ALTER TABLE public.t_parametre_interne_procedure ADD COLUMN IF NOT EXISTS "SIGNATURE_OFFRE" character varying(30);
ALTER TABLE public.t_parametre_interne_procedure DROP CONSTRAINT IF EXISTS ck_parametre_interne_signature_offre;
ALTER TABLE public.t_parametre_interne_procedure ADD CONSTRAINT ck_parametre_interne_signature_offre
    CHECK ("SIGNATURE_OFFRE" IS NULL OR "SIGNATURE_OFFRE" IN ('SCELLE_PLUS_ORIGINAL', 'CERTIFIEE'));
