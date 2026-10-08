-- ════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — prestations intellectuelles, lot 3 de l'évaluation, tranche PI-a (demande front du 2026-10-07,
-- demande-backend-2026-10-07-evaluation-pi.md, Q6 ; arbitrages du pilote du 07/10 et du 08/10) :
--
-- 1. B02-MS-01 « Mode de sélection du consultant » : cinquième méthode de l'art. 42-IV, « Qualification du consultant »
--    (séparateur « | », car une option contient une virgule).
-- 2. B05-PF-13 « Budget disponible » : le budget prédéterminé, lu HORS TAXES par le serveur ; le libellé le dit. Le champ existait
--    déjà (imprimé par la DPIC sous la condition BUDGET-DISPONIBLE) : aucun champ B06-CS-04 n'est créé (arbitrage du 08/10).
--
-- Ce n'est PAS une migration : le fichier de correspondance des prestations intellectuelles porte ces valeurs ; ce script aligne
-- une base où il a déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-08-pi-methode-qualification-budget.sql
-- ════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche
   SET "OPTIONS" = 'Qualité technique, expérience et proposition financière|Budget prédéterminé dont le candidat propose la meilleure utilisation|Meilleure proposition financière parmi les candidats ayant obtenu la note technique minimale|Qualité technique exclusivement|Qualification du consultant'
 WHERE "CODE" = 'B02-MS-01';

UPDATE public.tr_champ_fiche_marche SET "LIBELLE" = 'Budget disponible (Ariary HT)' WHERE "CODE" = 'B05-PF-13';

SELECT "CODE", "LIBELLE", right(coalesce("OPTIONS", '-'), 60) AS "OPTIONS (fin)"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B02-MS-01', 'B05-PF-13') ORDER BY 1;

COMMIT;
