-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Les pièces de l'offre ouvertes aux fournitures (demande front
-- demande-backend-2026-10-03-pieces-offre-fournitures.md, choix A du pilote, H2).
--
--   B04-CO-01 (pièces de l'offre des fournitures, texte libre) devient FACULTATIF : la règle bloquante
--   PIECES_OFFRE_EXIGEES (rôle TEXTE) exige les pièces par la liste OFFRE (/pieces, ouverte aux fournitures par V62) OU
--   par ce texte, comme B04-PI-01 aux travaux. B03-CQ-01 porte déjà le rôle PIECES_EN_DOUBLE (script du 03/10, V61).
-- Le fichier de correspondance des fournitures porte la même ligne. Ce n'est pas une migration. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-03-pieces-offre-fournitures.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false, "CONTROLE" = 'PIECES_OFFRE_EXIGEES:TEXTE' WHERE "CODE" = 'B04-CO-01';

-- Information.
SELECT "CODE", "OBLIGATOIRE", coalesce("CONTROLE", '') AS "CONTROLE", left("LIBELLE", 60) AS "LIBELLE"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B04-CO-01', 'B03-CQ-01') ORDER BY "CODE";
SELECT count(*) AS "VALEURS_B04_CO_01" FROM public.t_fiche_marche_valeur WHERE "CODE_CHAMP" = 'B04-CO-01';

COMMIT;
