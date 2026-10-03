-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Fiche DAO des travaux — le matériel et le personnel exigés, en listes (demande front
-- demande-backend-2026-10-03-materiel-personnel-travaux.md, §B3, H2).
--
--   B03-QT-09 (le matériel en texte libre) devient FACULTATIF : la règle bloquante MATERIEL_EXIGE (rôle TEXTE) exige le
--   matériel par la liste (V60, GET|PUT /api/fiches-marche/{idDmc}/materiel) OU par ce texte. Les listes elles-mêmes
--   (tables, bloc B13) sont dans la migration V60.
-- Le fichier de correspondance des travaux porte la même ligne. Ce n'est pas une migration. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-03-materiel-personnel-travaux.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false, "CONTROLE" = 'MATERIEL_EXIGE:TEXTE' WHERE "CODE" = 'B03-QT-09';

-- Information.
SELECT "CODE", "OBLIGATOIRE", coalesce("CONTROLE", '') AS "CONTROLE", left("LIBELLE", 60) AS "LIBELLE"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B03-QT-09', 'B03-QT-13') ORDER BY "CODE";
SELECT count(*) AS "VALEURS_B03_QT_09" FROM public.t_fiche_marche_valeur WHERE "CODE_CHAMP" = 'B03-QT-09';

COMMIT;
