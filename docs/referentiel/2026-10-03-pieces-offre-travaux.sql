-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Fiche DAO des travaux — les pièces de l'offre, typées (demande front
-- demande-backend-2026-10-03-pieces-offre-travaux.md, §B3, H2 et H3).
--
--   B04-PI-01 (pièces de l'offre en texte libre) devient FACULTATIF : la règle bloquante PIECES_OFFRE_EXIGEES (rôle
--   TEXTE) exige les pièces par la liste OFFRE (V61, GET|PUT /api/fiches-marche/{idDmc}/pieces) OU par ce texte.
--   B03-CQ-01 (pièces administratives en texte, valeur par défaut = la liste du document type) porte le rôle
--   PIECES_EN_DOUBLE:TEXTE : avertissement quand la liste ADMINISTRATIVE est remplie et que le texte garde son défaut.
-- La liste elle-même (table, bloc B14) est dans la migration V61. Les fichiers de correspondance portent les mêmes lignes.
-- Ce n'est pas une migration. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-03-pieces-offre-travaux.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false, "CONTROLE" = 'PIECES_OFFRE_EXIGEES:TEXTE' WHERE "CODE" = 'B04-PI-01';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'PIECES_EN_DOUBLE:TEXTE' WHERE "CODE" = 'B03-CQ-01';

-- Information.
SELECT "CODE", "OBLIGATOIRE", coalesce("CONTROLE", '') AS "CONTROLE", left("LIBELLE", 60) AS "LIBELLE"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B04-PI-01', 'B03-CQ-01') ORDER BY "CODE";
SELECT "CODE_CHAMP", count(*) AS "VALEURS" FROM public.t_fiche_marche_valeur
 WHERE "CODE_CHAMP" IN ('B04-PI-01', 'B03-CQ-01') GROUP BY 1 ORDER BY 1;

COMMIT;
