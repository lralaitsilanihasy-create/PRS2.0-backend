-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V55 — Un seul délai de validité des offres pour les fournitures et les travaux (demande front du 2026-09-29,
-- demande-backend-2026-09-29-lot-d4-travaux.md §B3, point ouvert tranché le 2026-09-30).
--
-- AE-CC, commun aux contrats-cadres des deux catégories, et désormais DPAO-T citent {{B04-VO-01}}. Le champ est ouvert
-- aux travaux par le fichier de correspondance des fournitures (et docs/referentiel/2026-09-30-validite-offres-unique.sql) ;
-- ses deux doublons propres aux travaux, B04-DV-01 (quantité fixe, à commande) et B04-VT-01 (contrat-cadre), sont retirés.
-- La rubrique B04-VO, jusqu'ici réservée aux fournitures, est élargie de même : le référentiel filtre les rubriques par
-- catégorie (voir V54), et le champ serait sinon servi hors rubrique. Les rubriques B04-DV et B04-VT, sans champ actif,
-- ne sont plus servies d'elles-mêmes.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_rubrique_fiche_marche
   SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX'
 WHERE "CODE" = 'B04-VO' AND "CATEGORIES" = 'FOURNITURES_SERVICES';
