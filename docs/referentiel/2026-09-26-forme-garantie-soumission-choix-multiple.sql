-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — forme de la garantie de soumission : plusieurs formes admises (demande front du
-- 2026-09-26, demande-backend-2026-09-26-forme-garantie-soumission-choix-multiple.md, §B1 ; arbitrage du pilote du 26/09
-- sur la fiche des faits du dossier réel 2463, DPAO clause 6.6).
--
-- Ce n'est PAS une migration : le champ se charge par l'import du fichier de correspondance des fournitures, corrigé à
-- l'identique. Ce script aligne une base où il a déjà été chargé. Aucun schéma ne change (le type LISTE_MULTIPLE est
-- admis depuis V45) : à lancer sur une base au moins en V45.
--
--   B05-GS-02  forme de la garantie de soumission  → LISTE_MULTIPLE ; options, condition (garantieSoumission = OUI),
--              obligatoire, documents (DPAO, repris AE et CCAP) inchangés
--
-- Aucune donnée à reprendre : une valeur à une seule forme est déjà une liste valide à un élément (l'écriture range les
-- formes retenues dans l'ordre des options, séparées par des virgules — aucune option n'en contient). Les documents des
-- versions déjà validées ne sont pas régénérés : la tournure « l'une des formes suivantes : – soit … » s'imprime à la
-- prochaine validation. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-26-forme-garantie-soumission-choix-multiple.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'LISTE_MULTIPLE'
 WHERE "CODE" = 'B05-GS-02' AND "TYPE" = 'LISTE';

SELECT "CODE", "TYPE", coalesce("OPTIONS", '—') AS "OPTIONS", coalesce("CONDITION", '—') AS "CONDITION", "OBLIGATOIRE",
       "DOCUMENT_MAITRE", coalesce("REPRISES", '—') AS "REPRISES", "ACTIF"
  FROM public.tr_champ_fiche_marche WHERE "CODE" = 'B05-GS-02';

COMMIT;
