-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V151 — ⚠️ 2026-10-30 : demande front « contrat-cadre-4b », §B3 (décret n° 2019-1064, art. 31 : « si la passation des marchés
-- subséquents est effectuée selon une périodicité prédéfinie, la remise en concurrence des titulaires des lots porte sur tous les
-- lots »).
--
-- t_remise_concurrence.ID_GROUPE — la remise commune à tous les lots d'un contrat-cadre alloti au rythme « Selon le calendrier fixé
-- ci-après » : une remise par contrat en vigueur (un par lot, avec ses titulaires et leurs réponses), reliées par le même groupe
-- (l'identifiant de la première). Nul pour une remise d'un seul lot.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_remise_concurrence ADD COLUMN IF NOT EXISTS "ID_GROUPE" bigint;
CREATE INDEX IF NOT EXISTS ix_remise_concurrence_groupe ON public.t_remise_concurrence ("ID_GROUPE");
