-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V148 — ⚠️ 2026-10-29 : demande front « bons-reception » (§B2, §B3 ; propositions Q1, Q3, Q4 retenues).
--
-- 1. t_bon_commande — le taux de TVA (en %, figé à l'émission), la TVA et le TTC du bon, arrondis au centime. Nuls pour un bon émis
--    avant : son taux n'est pas connu, rien n'est recalculé.
-- 2. t_reception_ligne — les quantités d'une réception provisoire, par ligne du bon (RANG) : livrées (à la déclaration de la
--    livraison), puis reçues (au PV). Une réception provisoire sans ligne (avant V148) vaut pour tout le bon.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_bon_commande ADD COLUMN IF NOT EXISTS "TAUX_TVA" numeric(7,4);
ALTER TABLE public.t_bon_commande ADD COLUMN IF NOT EXISTS "MONTANT_TVA" numeric(18,2);
ALTER TABLE public.t_bon_commande ADD COLUMN IF NOT EXISTS "MONTANT_TTC" numeric(18,2);

CREATE TABLE IF NOT EXISTS public.t_reception_ligne (
    "ID_RECEPTION"     bigint NOT NULL REFERENCES public.t_reception_commande ("ID"),
    "RANG"             integer NOT NULL,
    "QUANTITE_LIVREE"  numeric(18,3) NOT NULL,
    "QUANTITE_RECUE"   numeric(18,3),
    PRIMARY KEY ("ID_RECEPTION", "RANG")
);
