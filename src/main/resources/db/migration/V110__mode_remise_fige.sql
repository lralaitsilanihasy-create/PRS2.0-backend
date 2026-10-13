-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V110 — ⚠️ 2026-10-13 : manuel révisé pour l'application, lot 2, tranche R8 (§B3 : le mode de la
-- procédure est fixé à la création ; arbitrage Q1 du pilote).
--
-- Le cadrage modeRemise (PAPIER / ELECTRONIQUE) d'une fiche DAO devient non modifiable dès qu'une
-- version est validée. La seule exception est la migration documentée par l'Administrateur : elle
-- ouvre une révision avec le nouveau mode et la date, l'auteur et le motif sont portés par cette
-- version (MODE_MIGRE_LE, MODE_MIGRE_PAR, MODE_MIGRE_MOTIF). Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_fiche_marche ADD COLUMN IF NOT EXISTS "MODE_MIGRE_LE" timestamp without time zone;
ALTER TABLE public.t_fiche_marche ADD COLUMN IF NOT EXISTS "MODE_MIGRE_PAR" character varying(100);
ALTER TABLE public.t_fiche_marche ADD COLUMN IF NOT EXISTS "MODE_MIGRE_MOTIF" character varying(1000);
