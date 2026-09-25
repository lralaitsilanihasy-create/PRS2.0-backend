-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- DBPRS20 — remise à zéro des fiches DAO d'essai (demande front du 2026-09-25)
--
-- Supprime les DMC 2 à 7 avec leurs fiches (toutes versions), leurs valeurs et leurs documents générés ; le DMC 1
-- (ligne 302873, dossier soumis 100332, démonstration en cours) n'est pas touché. Après exécution,
-- GET /api/dmcs/eligibles sert dejaDao=false sur 302874, 302896, 302886, 303069, 303070, 303071.
--
-- Ce n'est PAS une migration : données de recette d'une seule base. L'API ne sait pas défaire une fiche (le besoin
-- permanent est décrit dans frontend/docs/demande-backend-2026-09-25-supprimer-une-fiche-sans-historique.md).
--
-- Chaque DMC est désigné par son couple (DMC, ligne) : un identifiant qui désignerait une autre ligne n'est pas
-- supprimé. Garde : le script s'ARRÊTE, sans rien supprimer, si l'un de ces DMC porte un dossier (t_dossier.ID_DMC),
-- une pièce jointe produite par sa fiche, ou une observation d'examen qui le vise. Rejouable : un second passage ne
-- trouve plus rien et ne supprime rien.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/demo/remise-a-zero-fiches-dao-essai-2026-09-25.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

CREATE TEMP TABLE dmc_a_supprimer ON COMMIT DROP AS
SELECT d."ID_DMC"
  FROM public.t_dossier_mec d
  JOIN (VALUES (2::bigint, 302874), (3, 302896), (4, 302886), (5, 303069), (6, 303070), (7, 303071))
       AS v(id_dmc, id_detail) ON v.id_dmc = d."ID_DMC" AND v.id_detail = d."ID_DETAIL";

DO $$
DECLARE
    n_dossiers integer;
    n_pieces integer;
    n_observations integer;
BEGIN
    SELECT count(*) INTO n_dossiers FROM public.t_dossier WHERE "ID_DMC" IN (SELECT "ID_DMC" FROM dmc_a_supprimer);
    SELECT count(*) INTO n_pieces
      FROM public.t_piece_jointe_dossier p
      JOIN public.t_document_fiche_marche x ON x."ID_DOCUMENT" = p."ID_DOCUMENT_FICHE"
      JOIN public.t_fiche_marche f ON f."ID_FICHE" = x."ID_FICHE"
     WHERE f."ID_DMC" IN (SELECT "ID_DMC" FROM dmc_a_supprimer);
    SELECT (SELECT count(*) FROM public.t_observation_controle WHERE "ID_DMC_FICHE" IN (SELECT "ID_DMC" FROM dmc_a_supprimer))
         + (SELECT count(*) FROM public.t_observation_pv WHERE "ID_DMC_FICHE" IN (SELECT "ID_DMC" FROM dmc_a_supprimer))
      INTO n_observations;
    IF n_dossiers + n_pieces + n_observations > 0 THEN
        RAISE EXCEPTION 'Arrêt, rien supprimé : % dossier(s), % pièce(s) produite(s), % observation(s) portent ces DMC.',
            n_dossiers, n_pieces, n_observations;
    END IF;
    IF 1 IN (SELECT "ID_DMC" FROM dmc_a_supprimer) THEN
        RAISE EXCEPTION 'Arrêt : le DMC 1 ne doit jamais être supprimé.';
    END IF;
END $$;

SELECT d."ID_DMC" AS dmc, d."ID_DETAIL" AS ligne,
       (SELECT count(*) FROM public.t_fiche_marche f WHERE f."ID_DMC" = d."ID_DMC") AS versions,
       (SELECT count(*) FROM public.t_document_fiche_marche x JOIN public.t_fiche_marche f ON f."ID_FICHE" = x."ID_FICHE"
         WHERE f."ID_DMC" = d."ID_DMC") AS documents
  FROM public.t_dossier_mec d WHERE d."ID_DMC" IN (SELECT "ID_DMC" FROM dmc_a_supprimer) ORDER BY 1;

DELETE FROM public.t_document_fiche_marche
 WHERE "ID_FICHE" IN (SELECT "ID_FICHE" FROM public.t_fiche_marche WHERE "ID_DMC" IN (SELECT "ID_DMC" FROM dmc_a_supprimer));
DELETE FROM public.t_fiche_marche_valeur
 WHERE "ID_FICHE" IN (SELECT "ID_FICHE" FROM public.t_fiche_marche WHERE "ID_DMC" IN (SELECT "ID_DMC" FROM dmc_a_supprimer));
DELETE FROM public.t_fiche_marche WHERE "ID_DMC" IN (SELECT "ID_DMC" FROM dmc_a_supprimer);
DELETE FROM public.t_dossier_mec WHERE "ID_DMC" IN (SELECT "ID_DMC" FROM dmc_a_supprimer);

-- Ce qui reste : le DMC 1 seul (attendu : 1 | 302873 | dossier 100332).
SELECT d."ID_DMC" AS dmc, d."ID_DETAIL" AS ligne, s."ID_DOSSIER" AS dossier,
       (SELECT count(*) FROM public.t_fiche_marche f WHERE f."ID_DMC" = d."ID_DMC") AS versions
  FROM public.t_dossier_mec d LEFT JOIN public.t_dossier s ON s."ID_DMC" = d."ID_DMC" ORDER BY 1;

COMMIT;
