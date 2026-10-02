-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Données — les « ¿ » (U+00BF) du plan 00004 (demande front demande-backend-2026-10-02-recette-dao-men.md, §B4).
--
-- 14 lignes de plan (t_marche, toutes du dossier 100359) et leurs 14 lots (t_lot) portent un caractère de remplacement
-- « ¿ » là où la source avait une apostrophe (« Travaux d¿aménagement ») ou, une fois, un tiret (« Antsiranana ¿ Lot
-- n°02 », entre deux espaces, comme les « - » qui séparent les autres lots de la même phrase). Un import ancien du plan
-- l'a recopié : l'objet des documents produits le reprend.
--
-- Mêmes règles que l'import du plan (SaisiePpmImportService.nettoyerEncodage, étendu le 2026-10-02), dans le même ordre :
--   1. ligature œ (« ¿ » devant uvre, il, ur(s), ud, uf(s)) ;
--   2. élision des mots toujours élidés (jusqu, aujourd, lorsqu, puisqu, quelqu) ;
--   3. élision d'un mot d'une lettre (d, l, n, s, j, m, t, c) ou de « qu », devant une lettre ;
--   4. « ¿ » seul entre deux espaces → « - » (ici, le séparateur des lots) ;
--   5. « ¿ » en fin de texte, après un blanc → retiré (le même séparateur, resté au bout du lot 1 de la ligne 303288).
-- L'apostrophe est l'apostrophe droite « ' », celle du reste du plan (« Travaux d'urgence ») et de l'import.
-- Les documents déjà produits (fiche 40, v1) sont figés et gardent l'ancien objet ; une nouvelle version reprendra le bon.
-- Idempotent : ne touche que les textes qui portent encore un « ¿ ».
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-02-apostrophes-plan.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.sans_remplacement(t text) RETURNS text LANGUAGE sql IMMUTABLE AS $fn$
    SELECT regexp_replace(
             replace(
               regexp_replace(
                 regexp_replace(
                   regexp_replace(t, chr(191) || '(?=uvre|il|urs?\M|ud\M|ufs?\M)', 'œ', 'g'),
                   '(jusqu|aujourd|lorsqu|puisqu|quelqu)' || chr(191), '\1''', 'gi'),
                 '(?<![[:alpha:]])([dlnsjmtc]|qu)' || chr(191) || '(?=[[:alpha:]])', '\1''', 'gi'),
               ' ' || chr(191) || ' ', ' - '),
             '\s+' || chr(191) || '\s*$', '')
$fn$;

SELECT count(*) AS "LIGNES_AVANT" FROM public.t_marche WHERE "DESIGNATION_MARCHE" LIKE '%' || chr(191) || '%';
SELECT count(*) AS "LOTS_AVANT" FROM public.t_lot WHERE "DESIGNATION_LOT" LIKE '%' || chr(191) || '%';

UPDATE public.t_marche SET "DESIGNATION_MARCHE" = pg_temp.sans_remplacement("DESIGNATION_MARCHE")
 WHERE "DESIGNATION_MARCHE" LIKE '%' || chr(191) || '%';
UPDATE public.t_lot SET "DESIGNATION_LOT" = pg_temp.sans_remplacement("DESIGNATION_LOT")
 WHERE "DESIGNATION_LOT" LIKE '%' || chr(191) || '%';

-- Contrôle : plus aucun « ¿ » (sinon, à corriger à la main : un cas que les règles ne tranchent pas).
SELECT count(*) AS "LIGNES_RESTANTES" FROM public.t_marche WHERE "DESIGNATION_MARCHE" LIKE '%' || chr(191) || '%';
SELECT count(*) AS "LOTS_RESTANTS" FROM public.t_lot WHERE "DESIGNATION_LOT" LIKE '%' || chr(191) || '%';
SELECT "ID_DETAIL", left("DESIGNATION_MARCHE", 90) AS "OBJET" FROM public.t_marche WHERE "ID_DETAIL" IN (303288, 303287, 303329)
 ORDER BY 1;
SELECT "ID_LOT", "DESIGNATION_LOT" FROM public.t_lot WHERE "ID_LOT" = 2354;

COMMIT;
