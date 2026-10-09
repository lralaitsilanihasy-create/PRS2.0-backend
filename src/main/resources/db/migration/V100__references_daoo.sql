-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V100 — Reprise des références du renommage DAO → DAOO (manuel de contrôle, tranche M1, Q1 du pilote du 08/10 : « références des
-- dossiers existants comprises »), signalée par la recette du front du 09/10 (dossier 100370 resté « 00013/DAO/CNM/2026 »).
--
-- V94 a repris les références des réceptions, des versions et une colonne de PV, mais ni la référence du DOSSIER (t_dossier.REFE_DOSSIER,
-- servie par GET /api/dossiers/{id} et affichée par l'examen) ni la référence imprimée du PV (t_pv_examen.REFE_PV : V94 visait
-- REFERENCE_PV, vide sur ces lignes). Le compteur des références est celui de la famille (DMC) : « …/DAOO/… » ne peut pas entrer en
-- collision avec une référence existante.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.t_dossier SET "REFE_DOSSIER" = replace("REFE_DOSSIER", '/DAO/', '/DAOO/')
 WHERE "REFE_DOSSIER" LIKE '%/DAO/%' AND "ID_SOUS_TYPE" = 'DAOO';

UPDATE public.t_pv_examen SET "REFE_PV" = replace("REFE_PV", '/DAO/', '/DAOO/')
 WHERE "REFE_PV" LIKE '%/DAO/%';
