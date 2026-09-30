-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — travaux : B04-CD-03 « Plans joints au dossier » repris dans le CCAP (demande front
-- demande-backend-2026-09-29-lot-d4-travaux.md §B6, réponse du 2026-09-30).
--
-- Le CCAP-T recopié imprime B04-CD-03 dans son annexe « Liste des Plans », sous la condition PLANS (champ renseigné).
-- Son document maître reste le DPAO ; il est désormais REPRIS dans le CCAP. Il reste facultatif.
--
-- Ce n'est PAS une migration : le fichier de correspondance des travaux porte cette valeur. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-30-plans-ccap-travaux.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "REPRISES" = 'CCAP' WHERE "CODE" = 'B04-CD-03';

SELECT "CODE", "DOCUMENT_MAITRE", "REPRISES", "ACTIF", "OBLIGATOIRE" FROM public.tr_champ_fiche_marche WHERE "CODE" = 'B04-CD-03';

COMMIT;
