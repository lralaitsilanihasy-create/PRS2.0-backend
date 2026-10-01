-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — le montant du dossier se saisit par lot (demande front
-- demande-backend-2026-09-30-avis-specifique.md §B7.4, avis aligné sur un avis réel le 2026-10-01).
--
-- B04-DS-05 « Montant à payer pour le dossier de consultation (Ariary) » devient PAR LOT : sur une ligne allotie, il se
-- saisit sous B04-DS-05#n, et l'avis l'imprime une ligne par lot ({{B04-DS-05.lignesParLot}}). Une valeur déjà saisie
-- sous B04-DS-05 reste lisible hors allotissement.
--
-- Ce n'est PAS une migration : le fichier de correspondance du contrat-cadre porte cette valeur. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-01-avis-montant-dao-par-lot.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "PAR_LOT" = true WHERE "CODE" = 'B04-DS-05';

-- Information : les valeurs déjà saisies sous le code nu (à ressaisir par lot sur une ligne allotie).
SELECT count(*) AS "VALEURS_B04_DS_05_SANS_LOT" FROM public.t_fiche_marche_valeur WHERE "CODE_CHAMP" = 'B04-DS-05';

SELECT "CODE", "PAR_LOT", "TYPES_MARCHE", "OBLIGATOIRE" FROM public.tr_champ_fiche_marche WHERE "CODE" = 'B04-DS-05';

COMMIT;
