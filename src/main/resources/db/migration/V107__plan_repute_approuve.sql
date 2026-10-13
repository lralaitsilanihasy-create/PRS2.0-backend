-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V107 — ⚠️ 2026-10-13 : manuel révisé pour l'application, lot 2, tranche R6b (§B1, règle 5 : le plan
-- réputé approuvé, art. 26-IV de la loi n° 2016-055 ; arbitrage Q6 du pilote).
--
-- Un plan de passation (famille DDP) soumis depuis dix jours ouvrables sans PV signé est réputé approuvé :
-- le planificateur pose REPUTE_APPROUVE_LE (marqueur daté), journalise et notifie ; le circuit continue (un
-- avis tardif de la Commission vaut observation) ; la création d'un DMC accepte ce plan comme un PV favorable.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_dossier ADD COLUMN IF NOT EXISTS "REPUTE_APPROUVE_LE" timestamp without time zone;
