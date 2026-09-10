-- =====================================================================================================
-- V26 — Élargir tr_points_ctrl.PORTEE : « SUPPRESSION » ne tient pas dans varchar(10).
--
-- La colonne fait 10 caractères depuis l'origine ; la valeur ouverte par V25 en compte 11. Sans cet
-- élargissement, PointsCtrlFicheAgpmSeeder échoue au démarrage sur la longueur — avant même d'atteindre
-- le CHECK que V25 vient d'ouvrir. Les deux moitiés sont indissociables ; elles sont ici en deux
-- migrations parce que V25 était déjà appliquée sur l'environnement pilote quand le manque est apparu,
-- et qu'une migration appliquée ne se réécrit pas : elle se corrige en avant.
--
-- Idempotente de fait : élargir un varchar est une opération de métadonnées en PostgreSQL (pas de
-- réécriture de table), et la rejouer sur une colonne déjà en varchar(20) ne change rien.
-- =====================================================================================================

ALTER TABLE public.tr_points_ctrl ALTER COLUMN "PORTEE" TYPE varchar(20);
