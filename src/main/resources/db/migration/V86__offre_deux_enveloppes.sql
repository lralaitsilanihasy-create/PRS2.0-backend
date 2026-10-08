-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V86 — Évaluation des offres, lot 3 (prestations intellectuelles), tranche PI-b (demande front du 2026-10-07,
-- `demande-backend-2026-10-07-evaluation-pi.md`, §B2, §B3 ; arbitrage du pilote : deux enveloppes pour toutes les méthodes, Q1).
--
-- Une proposition de prestations intellectuelles se dépose en DEUX conteneurs scellés séparément, chacun avec sa clé partagée
-- (ADR-0013) : l'enveloppe TECHNIQUE et l'enveloppe FINANCIERE, d'un même candidat pour un même lot. NULL : une offre ordinaire,
-- en un seul conteneur (fournitures, travaux). La première séance n'ouvre que les enveloppes techniques ; les financières restent
-- scellées jusqu'à la seconde séance (tranche PI-d).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_offre ADD COLUMN IF NOT EXISTS "ENVELOPPE" character varying(12);
ALTER TABLE public.t_offre DROP CONSTRAINT IF EXISTS ck_offre_enveloppe;
ALTER TABLE public.t_offre ADD CONSTRAINT ck_offre_enveloppe CHECK ("ENVELOPPE" IS NULL OR "ENVELOPPE" IN ('TECHNIQUE', 'FINANCIERE'));
