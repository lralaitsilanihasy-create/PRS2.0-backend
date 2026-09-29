-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — fournitures, champs non imprimés (demande front du 2026-09-29,
-- demande-backend-2026-09-29-champs-non-imprimes-fournitures.md §B1 ; arbitrage du pilote du 29/09).
--
-- Retire 25 champs de la fiche des fournitures (quantité fixe et à commande) : ACTIF = false. Ils n'existent que pour
-- les fournitures (CATEGORIES = FOURNITURES_SERVICES), donc rien ne change pour les travaux ni pour les prestations
-- intellectuelles. Les valeurs déjà saisies (t_fiche_marche_valeur) sont CONSERVÉES : elles ne sont plus proposées, ne
-- comptent plus dans l'avancement ni au bilan, et l'enregistrement d'un bloc ne les efface plus.
--
-- Quatre des 29 codes demandés RESTENT actifs, parce qu'une règle du serveur les lit :
--   B04-OP-02, B04-OP-03  date et heure d'ouverture des plis, calculées en remise électronique (V50, Q11) et lues par
--                         SE_OUVERTURE_PLIS et DATES_ORDRE — liées à la clause 7.3 en attente du juriste (décision 2) ;
--   B05-TP-03             montant maximum annuel (à commande), rôle MAXIMUM du contrôle GARANTIE_TAUX ;
--   B08-PA-08             délai de paiement, rôle DELAI du contrôle DELAI_PAIEMENT_75.
--
-- Ce n'est PAS une migration : le fichier de correspondance des fournitures porte actif = non ; ce script aligne une base
-- où il a déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-29-fournitures-champs-non-imprimes.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false
 WHERE "CODE" IN ('B02-AU-05', 'B05-TP-02', 'B08-PA-01', 'B08-PA-02', 'B03-NA-01', 'B03-NA-02', 'B03-ST-02', 'B04-RO-03',
                  'B06-EO-04', 'B06-EO-05', 'B06-EO-06', 'B08-AC-01', 'B08-AC-02', 'B08-AV-03', 'B08-AV-05', 'B08-AV-06',
                  'B08-PA-04', 'B10-IR-02', 'B02-AU-07', 'B05-CP-03', 'B06-EO-03', 'B06-EO-07', 'B06-EO-08', 'B06-AN-02',
                  'B09-DG-02')
   AND "CATEGORIES" = 'FOURNITURES_SERVICES';

SELECT count(*) FILTER (WHERE NOT "ACTIF") AS "RETIRES (25 attendus)",
       count(*) FILTER (WHERE "ACTIF")     AS "RESTES_ACTIFS (0 attendu)"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B02-AU-05', 'B05-TP-02', 'B08-PA-01', 'B08-PA-02', 'B03-NA-01', 'B03-NA-02', 'B03-ST-02', 'B04-RO-03',
                  'B06-EO-04', 'B06-EO-05', 'B06-EO-06', 'B08-AC-01', 'B08-AC-02', 'B08-AV-03', 'B08-AV-05', 'B08-AV-06',
                  'B08-PA-04', 'B10-IR-02', 'B02-AU-07', 'B05-CP-03', 'B06-EO-03', 'B06-EO-07', 'B06-EO-08', 'B06-AN-02',
                  'B09-DG-02');

SELECT "CODE", "ACTIF" FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B04-OP-02', 'B04-OP-03', 'B05-TP-03', 'B08-PA-08') ORDER BY 1;

COMMIT;
