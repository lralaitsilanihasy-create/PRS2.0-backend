-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V147 — ⚠️ 2026-10-29 : demande front « contrat-cadre-4a » (§B2, §B3).
--
-- t_contrat_vigueur :
-- 1. le calendrier STRUCTURÉ des remises en concurrence, figé à la notification depuis la fiche (B02-PC-04 périodicité en mois,
--    B02-PC-05 date de la première remise) quand le rythme est « Selon le calendrier fixé ci-après » ; le texte (CALENDRIER,
--    B02-PC-03) reste celui imprimé. Nuls pour un contrat créé avant : pas de prochaine remise, pas d'alerte.
-- 2. les repères des rappels de la tâche planifiée, pour n'émettre chacun qu'une fois :
--    RAPPEL_RECONDUCTION = la fin de période (DATE_FIN) pour laquelle RECONDUCTION_A_DECIDER est partie ;
--    RAPPEL_REMISE       = l'échéance du calendrier pour laquelle REMISE_A_LANCER est partie ;
--    ECHU_NOTIFIE_LE     = l'envoi de CONTRAT_ECHU.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_contrat_vigueur ADD COLUMN IF NOT EXISTS "PERIODICITE_MOIS" integer;
ALTER TABLE public.t_contrat_vigueur ADD COLUMN IF NOT EXISTS "PREMIERE_REMISE" date;
ALTER TABLE public.t_contrat_vigueur ADD COLUMN IF NOT EXISTS "RAPPEL_RECONDUCTION" date;
ALTER TABLE public.t_contrat_vigueur ADD COLUMN IF NOT EXISTS "RAPPEL_REMISE" date;
ALTER TABLE public.t_contrat_vigueur ADD COLUMN IF NOT EXISTS "ECHU_NOTIFIE_LE" timestamp without time zone;

-- 3. Rien n'est rejoué : un contrat déjà échu est marqué notifié.
UPDATE public.t_contrat_vigueur SET "ECHU_NOTIFIE_LE" = now()::timestamp(0) WHERE "DATE_FIN" < current_date AND "ECHU_NOTIFIE_LE" IS NULL;
