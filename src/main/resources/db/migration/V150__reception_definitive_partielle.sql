-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V150 — ⚠️ 2026-10-30 : demande front « ccag-penalites-reception » (décision du pilote « suivre les textes en vigueur » ; §B3, §B5 ;
-- CCAG fournitures, art. 22.1 et 22.2).
--
-- t_reception_commande :
-- 1. ID_PROVISOIRE — une réception DÉFINITIVE porte sur une réception provisoire (une livraison) : la réception définitive partielle de
--    chaque livraison au terme de son délai de garantie (art. 22.2). Nul pour une définitive d'avant V150 : elle vaut pour les
--    réceptions provisoires qui la précèdent.
-- 2. RAPPEL_DECISION_LE, RETARD_DECISION_LE — les repères des deux rappels au président de la commission sur l'échéance de décision
--    (livraison + 15 jours, art. 22.1) : à son approche, puis une fois dépassée ; une fois chacun.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_reception_commande ADD COLUMN IF NOT EXISTS "ID_PROVISOIRE" bigint REFERENCES public.t_reception_commande ("ID");
ALTER TABLE public.t_reception_commande ADD COLUMN IF NOT EXISTS "RAPPEL_DECISION_LE" timestamp without time zone;
ALTER TABLE public.t_reception_commande ADD COLUMN IF NOT EXISTS "RETARD_DECISION_LE" timestamp without time zone;
