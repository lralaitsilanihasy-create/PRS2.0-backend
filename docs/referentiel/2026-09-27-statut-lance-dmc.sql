-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Rattrapage — la ligne du plan passe à « Lancé » quand son dossier de mise en concurrence existe
-- (règle du pilote du 2026-09-27, demande-backend-2026-09-27-statut-lance-dmc.md §B5).
--
-- Ce n'est PAS une migration : le serveur pose désormais LANCE à la création du DMC et rend PREVU à la suppression de la
-- fiche. Ce script aligne les lignes créées AVANT la règle : toute ligne PREVU (ou sans statut) qui porte un DMC
-- vivant — directement, ou par sa filiation (ID_LIGNE_ORIGINE) dans une version du plan non remplacée — passe LANCE.
-- Les statuts manuels (CHDP, DSS) ne sont pas touchés. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-27-statut-lance-dmc.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

-- Avant
SELECT m."ID_DETAIL", m."STATUT", m."ID_LIGNE_ORIGINE", m."ID_DOSSIER", d."ID_DMC"
  FROM public.t_marche m
  LEFT JOIN public.t_dossier_mec d ON d."ID_DETAIL" = m."ID_DETAIL"
 WHERE coalesce(m."ID_LIGNE_ORIGINE", m."ID_DETAIL") IN (
           SELECT coalesce(l."ID_LIGNE_ORIGINE", l."ID_DETAIL") FROM public.t_dossier_mec dm
             JOIN public.t_marche l ON l."ID_DETAIL" = dm."ID_DETAIL")
 ORDER BY 1;

UPDATE public.t_marche m
   SET "STATUT" = 'LANCE'
  FROM public.t_dossier dd
 WHERE dd."ID_DOSSIER" = m."ID_DOSSIER"
   AND dd."STATUT" <> 'REMPLACE'
   AND (m."STATUT" IS NULL OR btrim(m."STATUT") = '' OR upper(btrim(m."STATUT")) = 'PREVU')
   AND coalesce(m."ID_LIGNE_ORIGINE", m."ID_DETAIL") IN (
           SELECT coalesce(l."ID_LIGNE_ORIGINE", l."ID_DETAIL") FROM public.t_dossier_mec dm
             JOIN public.t_marche l ON l."ID_DETAIL" = dm."ID_DETAIL");

-- Après
SELECT m."ID_DETAIL", m."STATUT", m."ID_DOSSIER", d."ID_DMC"
  FROM public.t_marche m
  LEFT JOIN public.t_dossier_mec d ON d."ID_DETAIL" = m."ID_DETAIL"
 WHERE coalesce(m."ID_LIGNE_ORIGINE", m."ID_DETAIL") IN (
           SELECT coalesce(l."ID_LIGNE_ORIGINE", l."ID_DETAIL") FROM public.t_dossier_mec dm
             JOIN public.t_marche l ON l."ID_DETAIL" = dm."ID_DETAIL")
 ORDER BY 1;

COMMIT;
