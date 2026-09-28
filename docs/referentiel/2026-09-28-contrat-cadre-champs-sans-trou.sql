-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — contrat-cadre : dix informations obligatoires qu'aucun document n'imprime (demande front
-- du 2026-09-28, demande-backend-2026-09-28-contrat-cadre-champs-sans-trou.md ; décision Q2 du pilote le 28/09).
--
-- Le DPAC et l'AE du contrat-cadre sont rendus depuis le document type officiel (lot D) : ces informations ne remplissent
-- aucun trou, le modèle écrit la chose en dur. B1 : neuf deviennent facultatives (restent actives et saisissables ;
-- le contrôle DELAI_PAIEMENT_75 de B08-FP-03 reste). B2 : B07-DU-06 (durée totale), doublon de B02-DC-01 (durée maximale,
-- imprimée), désactivé — jamais de DELETE. Contrat-cadre seul. Ce n'est PAS une migration : le fichier de correspondance
-- du contrat-cadre porte la même chose. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-28-contrat-cadre-champs-sans-trou.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false WHERE "CODE" IN ('B04-DS-02', 'B04-DS-03', 'B06-SC-01', 'B06-SO-04', 'B07-DU-01', 'B07-MA-03', 'B07-PI-01', 'B08-FP-03', 'B10-RS-01');
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" IN ('B07-DU-06');

SELECT "CODE", "TYPES_MARCHE", "OBLIGATOIRE", coalesce("CONTROLE", '-') AS "CONTROLE", "ACTIF"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B04-DS-02', 'B04-DS-03', 'B06-SC-01', 'B06-SO-04', 'B07-DU-01', 'B07-MA-03', 'B07-PI-01', 'B08-FP-03', 'B10-RS-01', 'B07-DU-06') ORDER BY 1;

COMMIT;
