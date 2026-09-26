-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V47 — Formulaires du candidat, modèles officiels (arbitrages du pilote du 2026-09-26, demande §B8 R1-R12)
--
-- 1. R6 — Un champ peut porter une VALEUR PAR DÉFAUT (tr_champ_fiche_marche.VALEUR_DEFAUT), administrable (import et
--    API), RECOPIÉE dans la fiche à sa création : un changement de défaut ne touche pas les fiches existantes.
--    Premiers champs à en porter : B03-CQ-09 « Durée des antécédents juridiques » (5 ans) et B03-CQ-10 « Durée des
--    antécédents financiers » (3 ans), chargés par le fichier de correspondance (docs/referentiel/… pour une base déjà
--    chargée).
-- 2. R4 / R5 — La rubrique B03-CQ « Capacité et qualifications des candidats » vaut pour les trois catégories : les
--    fiches de renseignements A1 à A4 s'impriment pour tout appel d'offres, et elles lisent B03-CQ-01, -09, -10.
--    (B02-OB, qui porte le numéro de l'appel d'offres B02-OB-03, est déjà des trois catégories.)
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_champ_fiche_marche ADD COLUMN IF NOT EXISTS "VALEUR_DEFAUT" character varying(200);
COMMENT ON COLUMN public.tr_champ_fiche_marche."VALEUR_DEFAUT" IS
    'Valeur recopiee dans la fiche a sa creation (source SAISIE) ; administrable ; sans effet sur les fiches existantes.';

UPDATE public.tr_rubrique_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES'
 WHERE "CODE" = 'B03-CQ';
