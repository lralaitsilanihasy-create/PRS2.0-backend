-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- DÉMONSTRATION — retrait de la ligne « [DÉMO] » posée par ligne-travaux-100328.sql (plan 100328).
--
-- Retire, dans l'ordre des clés étrangères, ce que la recette a pu créer sur la ligne : pièces jointes produites par
-- sa fiche et documents générés, valeurs et versions de la fiche, lien du dossier soumis (le dossier lui-même reste,
-- à supprimer depuis « Mes brouillons » s'il a été créé), le DMC, les lots, puis la ligne. Sans ligne [DÉMO] : rien.
--
-- Usage : psql -U postgres -d DBPRS20 -f docs/demo/ligne-travaux-100328-retrait.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

BEGIN;

CREATE TEMP TABLE demo_ligne ON COMMIT DROP AS
    SELECT "ID_DETAIL" FROM public.t_marche WHERE "DESIGNATION_MARCHE" = '[DÉMO] Travaux de réhabilitation du bâtiment administratif' AND "ID_DOSSIER" = 100328;
CREATE TEMP TABLE demo_dmc ON COMMIT DROP AS
    SELECT "ID_DMC" FROM public.t_dossier_mec WHERE "ID_DETAIL" IN (SELECT "ID_DETAIL" FROM demo_ligne);
CREATE TEMP TABLE demo_fiche ON COMMIT DROP AS
    SELECT "ID_FICHE" FROM public.t_fiche_marche WHERE "ID_DMC" IN (SELECT "ID_DMC" FROM demo_dmc);

DELETE FROM public.t_piece_jointe_dossier WHERE "ID_DOCUMENT_FICHE" IN
    (SELECT "ID_DOCUMENT" FROM public.t_document_fiche_marche WHERE "ID_FICHE" IN (SELECT "ID_FICHE" FROM demo_fiche));
DELETE FROM public.t_document_fiche_marche WHERE "ID_FICHE" IN (SELECT "ID_FICHE" FROM demo_fiche);
DELETE FROM public.t_fiche_marche_valeur WHERE "ID_FICHE" IN (SELECT "ID_FICHE" FROM demo_fiche);
DELETE FROM public.t_fiche_marche WHERE "ID_FICHE" IN (SELECT "ID_FICHE" FROM demo_fiche);
UPDATE public.t_dossier SET "ID_DMC" = NULL WHERE "ID_DMC" IN (SELECT "ID_DMC" FROM demo_dmc);
DELETE FROM public.t_dossier_mec WHERE "ID_DMC" IN (SELECT "ID_DMC" FROM demo_dmc);
DELETE FROM public.t_lot WHERE "ID_DETAIL" IN (SELECT "ID_DETAIL" FROM demo_ligne);
DELETE FROM public.t_marche WHERE "ID_DETAIL" IN (SELECT "ID_DETAIL" FROM demo_ligne);

COMMIT;
