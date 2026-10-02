-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel des travaux — quatre corrections relevées sur le DAO routier du MTP (demande front
-- demande-backend-2026-10-02-referentiel-travaux-routiers.md ; accord du pilote).
--
--   B1 — B02-MW-01 (maître d'œuvre) facultatif : le CCAP-T a une rédaction pour son absence (SANS-MOE) ;
--   B2 — B09-AC-03 (assurance décennale) facultatif, exigé pour un bâtiment par la règle bloquante ASSURANCE_DECENNALE
--        (rôles : B09-BT-01 = BATIMENT, B09-AC-03 = ASSURANCE) ;
--   B4 — B08-MO-01 (taux des intérêts moratoires) retiré : servi aux seuls travaux, aucun modèle ne l'imprime (le CCAP-T
--        écrit le taux en texte fixe). Inactif, ses valeurs éventuelles restent lisibles.
-- (B3 — la numérotation B1 / B2 des garanties des travaux est dans le code, pas dans le référentiel.)
-- Ce n'est pas une migration. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-02-travaux-routiers.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false WHERE "CODE" IN ('B02-MW-01', 'B09-AC-03');
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'ASSURANCE_DECENNALE:ASSURANCE' WHERE "CODE" = 'B09-AC-03';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'ASSURANCE_DECENNALE:BATIMENT' WHERE "CODE" = 'B09-BT-01';
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" = 'B08-MO-01';

-- Information.
SELECT "CODE", "OBLIGATOIRE", "ACTIF", coalesce("CONTROLE", '') AS "CONTROLE"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B02-MW-01', 'B09-AC-03', 'B09-BT-01', 'B08-MO-01') ORDER BY "CODE";
SELECT count(*) AS "VALEURS_B08_MO_01_CONSERVEES" FROM public.t_fiche_marche_valeur WHERE "CODE_CHAMP" = 'B08-MO-01';

COMMIT;
