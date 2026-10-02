-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel des travaux — libellé de B05-GQ-04 (demande front demande-backend-2026-10-02-recette-dao-men.md, §B6.3).
--
-- Le bénéficiaire des chèques s'insère après « libéllé au nom de » (DPAO-T) et « à l'ordre de » (CCAP-T) : l'exemple
-- guide vers une forme qui se lit bien (« …au nom de Monsieur le Receveur Général d'Antananarivo »).
-- Ce n'est pas une migration. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-02-libelle-beneficiaire-cheques.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche
   SET "LIBELLE" = 'Bénéficiaire des chèques de banque, pour toutes les garanties (ex. « Monsieur le Receveur Général d''Antananarivo »)'
 WHERE "CODE" = 'B05-GQ-04';

SELECT "CODE", "LIBELLE" FROM public.tr_champ_fiche_marche WHERE "CODE" = 'B05-GQ-04';

COMMIT;
