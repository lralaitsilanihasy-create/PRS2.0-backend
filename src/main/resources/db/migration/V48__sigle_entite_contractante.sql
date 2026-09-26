-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V48 — Le sigle de l'entité contractante (demande front du 2026-09-26, demande-backend-2026-09-26-sigle-entite.md,
-- arbitrage du pilote : « oui, je souhaite »).
--
-- Le dossier réel 2463 nomme son autorité contractante par son sigle (« MESupReS ») ; le référentiel n'en avait pas et la
-- référence du plan était bâtie sur un acronyme dérivé du libellé (« MLSRS »). Colonne FACULTATIVE, 20 caractères, sans
-- unicité (deux directions régionales peuvent partager un sigle) : lettres, chiffres, tirets et points seulement (c'est
-- un segment de référence), validé par l'API. Aucune reprise : les entités existantes restent à NULL et se renseignent à
-- la main (Administrateur → Entités) ; l'entité 11 du jeu 2463 est posée par le script de rejeu, pas ici.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_entite_contract ADD COLUMN IF NOT EXISTS "SIGLE" varchar(20);

COMMENT ON COLUMN public.tr_entite_contract."SIGLE" IS
    'Sigle de l''entité tel qu''il figure dans les références (MESupReS, JIRAMA…) — facultatif ; s''il est renseigné, il remplace l''acronyme dérivé du libellé dans la référence du PPM (V48, 2026-09-26)';
