-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — dix reprises documentaires alignées sur le classeur officiel « DAO Fournitures et
-- Services » (demande front du 2026-09-25, suite de l'audit docs/audit-2026-09-24-coherence-dao-fournitures.md §5).
--
-- Ce n'est PAS une migration : le référentiel des champs se charge par l'import du fichier de correspondance, et les
-- deux fichiers du front (referentiel-champs-fiche-marche-fournitures.csv, …-contrat-cadre.csv) ont été corrigés à
-- l'identique. Ce script aligne une base où ces fichiers ont déjà été chargés, sans réimporter le reste : il ne touche
-- que DOCUMENT_MAITRE et REPRISES de dix champs, par code. Idempotent.
--
-- Les versions de fiche déjà validées gardent leurs documents figés ; la correction vaut pour les versions suivantes.
--
-- Usage : psql -U postgres -d DBPRS20 -f docs/referentiel/2026-09-25-reprises-dao-fournitures.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

BEGIN;

-- B1 — Fournitures et services : quatre reprises en trop (le classeur ne les porte qu'au DPAO).
UPDATE public.tr_champ_fiche_marche SET "DOCUMENT_MAITRE" = 'DPAO', "REPRISES" = NULL
 WHERE "CODE" IN ('B05-GS-03', 'B02-AU-02', 'B02-AU-03', 'B02-AU-04');

-- B2 — Contrat-cadre : six champs mal orientés.
UPDATE public.tr_champ_fiche_marche SET "DOCUMENT_MAITRE" = 'DPAC', "REPRISES" = NULL WHERE "CODE" = 'B02-DC-02';
UPDATE public.tr_champ_fiche_marche SET "DOCUMENT_MAITRE" = 'DPAC', "REPRISES" = 'AE'  WHERE "CODE" = 'B02-DC-04';
UPDATE public.tr_champ_fiche_marche SET "DOCUMENT_MAITRE" = 'DPAC', "REPRISES" = NULL WHERE "CODE" = 'B05-UM-01';
UPDATE public.tr_champ_fiche_marche SET "DOCUMENT_MAITRE" = 'AE',   "REPRISES" = NULL WHERE "CODE" = 'B05-PM-02';
UPDATE public.tr_champ_fiche_marche SET "DOCUMENT_MAITRE" = 'DPAC', "REPRISES" = 'AE'  WHERE "CODE" IN ('B06-NO-01', 'B06-NO-02');

SELECT "CODE", "DOCUMENT_MAITRE", coalesce("REPRISES", '—') AS "REPRISES"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B05-GS-03', 'B02-AU-02', 'B02-AU-03', 'B02-AU-04', 'B02-DC-02', 'B02-DC-04', 'B05-UM-01',
                  'B05-PM-02', 'B06-NO-01', 'B06-NO-02')
 ORDER BY 1;

COMMIT;
