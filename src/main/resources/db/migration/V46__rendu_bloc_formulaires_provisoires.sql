-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V46 — Formulaires du candidat, second tour (demande front du 2026-09-25, §B6 et §B8)
--
-- 1. §B6 — Un bloc DÉCLARE son rendu (tr_bloc_fiche_marche.RENDU) : NULL, la liste de ses champs (tous les blocs
--    actuels) ; 'BESOIN', la grille du besoin (lots → articles → caractéristiques). B12 est le premier bloc à rendu
--    propre ; l'écran ne l'identifie plus par son code.
-- 2. §B8 — Les fiches de renseignements A1 à A4 et les garanties de soumission C1 (bancaire) et C2 (caution
--    personnelle et solidaire) sont produites, en attendant les modèles officiels du pilote, sur un GABARIT PROVISOIRE
--    filigrané « MODÈLE PROVISOIRE – NON OFFICIEL » : six types de documents de plus.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_bloc_fiche_marche ADD COLUMN IF NOT EXISTS "RENDU" character varying(20);
ALTER TABLE public.tr_bloc_fiche_marche DROP CONSTRAINT IF EXISTS ck_bloc_fiche_marche_rendu;
ALTER TABLE public.tr_bloc_fiche_marche ADD CONSTRAINT ck_bloc_fiche_marche_rendu
    CHECK ("RENDU" IS NULL OR "RENDU" IN ('BESOIN'));
UPDATE public.tr_bloc_fiche_marche SET "RENDU" = 'BESOIN' WHERE "CODE" = 'B12';
COMMENT ON COLUMN public.tr_bloc_fiche_marche."RENDU" IS
    'Rendu du bloc a l''ecran : NULL = la liste de ses champs ; BESOIN = la grille du besoin (/articles).';

ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT IF EXISTS ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'LF', 'BP', 'TC', 'A1', 'A2', 'A3', 'A4', 'C1', 'C2'));
