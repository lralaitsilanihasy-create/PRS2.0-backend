-- V4 — Reprise de données (conversion de l'ex-AssociationCcDispatchMigration, runner Java
-- supprimé au chantier LOT 2, 2026-08-26 ; règle modifiée : 2026-08-15, spec dispatch).
--
-- Avant la règle, le CC de la localité était associé (IM_CTRL_CC) à TOUT dispatch, y compris
-- quand il dispatchait lui-même ou s'auto-attribuait le dossier — doublon « Rôle Membre +
-- Rôle CC » pour la même personne (cas constaté sur 00002/PPM/CNM/2026). Nettoyage : efface
-- IM_CTRL_CC quand il désigne l'attributaire ou le dispatcheur lui-même. Les associations
-- légitimes (Président → Membre, CC tiers) sont conservées. Idempotente.
UPDATE public.t_dispatch
SET "IM_CTRL_CC" = NULL
WHERE "IM_CTRL_CC" IS NOT NULL
  AND ("IM_CTRL_CC" = "IM_CTRL_MEMBRE" OR "IM_CTRL_CC" = "IM_CTRL_DISPATCH");
