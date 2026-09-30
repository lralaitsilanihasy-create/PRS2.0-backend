-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Rattrapage du statut « Lancé » (décision du pilote du 2026-09-30, demande-backend-2026-09-30-statut-lance-avis.md §B5).
--
-- Depuis le 30/09, une ligne du plan passe « Lancé » à la PREMIÈRE IMPRESSION de son avis spécifique, et non plus à la
-- création de son DMC (règle du 27/09, retirée). Une ligne que le serveur a lancée à la création de son DMC (événement
-- LIGNE_LANCEE « Ligne n : DMC m créé, statut … → LANCE » au journal) et dont aucun avis spécifique n'est imprimé
-- redevient « Prévu », avec toute sa filiation (même origine à travers les versions du plan). Une ligne mise à « Lancé »
-- à la main (sans cet événement) n'est pas touchée : la requête de contrôle en fin de script les liste.
--
-- Idempotent. À passer avec l'accord du pilote.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-30-statut-lance-avis.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

CREATE TEMP TABLE lancees_par_dmc ON COMMIT DROP AS
SELECT DISTINCT substring(a."DETAIL" FROM '^Ligne ([0-9]+) : DMC [0-9]+ créé, statut .* → LANCE$')::int AS id_detail
  FROM public.t_action_dossier a
 WHERE a."TYPE_ACTION" = 'LIGNE_LANCEE'
   AND a."DETAIL" ~ '^Ligne [0-9]+ : DMC [0-9]+ créé, statut .* → LANCE$';

CREATE TEMP TABLE origines_avec_avis ON COMMIT DROP AS
SELECT DISTINCT coalesce(m."ID_LIGNE_ORIGINE", m."ID_DETAIL") AS origine
  FROM public.t_document_fiche_marche doc
  JOIN public.t_fiche_marche f ON f."ID_FICHE" = doc."ID_FICHE"
  JOIN public.t_dossier_mec d ON d."ID_DMC" = f."ID_DMC"
  JOIN public.t_marche m ON m."ID_DETAIL" = d."ID_DETAIL"
 WHERE doc."TYPE" = 'AVIS';

-- Les lignes (et leur filiation) lancées par le serveur à la création du DMC, sans avis imprimé : retour à PREVU.
UPDATE public.t_marche m SET "STATUT" = 'PREVU'
 WHERE m."STATUT" = 'LANCE'
   AND coalesce(m."ID_LIGNE_ORIGINE", m."ID_DETAIL") IN (
       SELECT coalesce(o."ID_LIGNE_ORIGINE", o."ID_DETAIL") FROM public.t_marche o
        WHERE o."ID_DETAIL" IN (SELECT id_detail FROM lancees_par_dmc))
   AND coalesce(m."ID_LIGNE_ORIGINE", m."ID_DETAIL") NOT IN (SELECT origine FROM origines_avec_avis);

-- Contrôle : les lignes encore « Lancé », et pourquoi (avis imprimé, ou mises à « Lancé » à la main).
SELECT m."ID_DETAIL", m."ID_DOSSIER", m."STATUT",
       CASE WHEN coalesce(m."ID_LIGNE_ORIGINE", m."ID_DETAIL") IN (SELECT origine FROM origines_avec_avis)
            THEN 'avis imprimé' ELSE 'mise à « Lancé » à la main (non touchée)' END AS "RAISON"
  FROM public.t_marche m
 WHERE m."STATUT" = 'LANCE'
 ORDER BY 1;

COMMIT;
