-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- DBPRS20 — vidage complet du suivi des dossiers (demande front du 2026-09-25, pilote)
--
-- Supprime les dossiers 100328 (PPM 00001, CLOTURE), 100329 (PPM 00002, EXAMINE) et 100332 (DMC DAO, SOUMIS) — les
-- trois dossiers de la base — avec tout ce qui en dépend : PPM et lignes de marché (lots, tranches, bénéficiaires,
-- prévisions, échéances, anomalies), DMC et fiches DAO de ces lignes (versions, valeurs, documents), pièces jointes,
-- circuit (réceptions, dispatchs, copies, tâches, suspensions, vérifications de dépôt), examens (résultats,
-- observations, pièces examinées), PV (navettes, vérifications, périmètre d'observations et leur suivi, transmissions
-- SIGMP), lettres de renvoi (et leurs lectures), versions archivées et instantanés de rectification, changements de
-- ligne, journal, notifications, messages, demandes de retrait.
--
-- Conséquence assumée par le pilote : GET /api/dmcs/eligibles renvoie une liste vide ; les écrans PPM, examen et PV
-- n'ont plus de données. Ne sont PAS touchés : les référentiels, comptes, mandats, intérims, le journal technique
-- (t_audit_log), les indicateurs agrégés et les compteurs de référence (t_sequence_reference : le prochain PPM de
-- JSRM ou de DGSR prendra le numéro suivant, pas 00001).
--
-- Ce n'est PAS une migration. Rejouable : un dossier déjà supprimé n'est plus trouvé, un second passage ne supprime
-- rien. Tout en une transaction : une erreur annule tout.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/demo/vidage-suivi-dossiers-2026-09-25.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

-- Le périmètre : les trois dossiers et leurs dossiers enfants éventuels.
CREATE TEMP TABLE v_dossier ON COMMIT DROP AS
SELECT "ID_DOSSIER" AS id FROM public.t_dossier
 WHERE "ID_DOSSIER" IN (100328, 100329, 100332) OR "ID_DOSSIER_PARENT" IN (100328, 100329, 100332);
CREATE TEMP TABLE v_ppm ON COMMIT DROP AS
SELECT "ID_PPM" AS id FROM public.t_ppm WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
CREATE TEMP TABLE v_ligne ON COMMIT DROP AS
SELECT "ID_DETAIL" AS id FROM public.t_marche
 WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier) OR "ID_PPM" IN (SELECT id FROM v_ppm);
CREATE TEMP TABLE v_lot ON COMMIT DROP AS
SELECT "ID_LOT" AS id FROM public.t_lot
 WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne) OR "ID_DOSSIER" IN (SELECT id FROM v_dossier);
CREATE TEMP TABLE v_dmc ON COMMIT DROP AS
SELECT "ID_DMC" AS id FROM public.t_dossier_mec WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne)
UNION SELECT "ID_DMC" FROM public.t_dossier WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier) AND "ID_DMC" IS NOT NULL;
CREATE TEMP TABLE v_fiche ON COMMIT DROP AS
SELECT "ID_FICHE" AS id FROM public.t_fiche_marche WHERE "ID_DMC" IN (SELECT id FROM v_dmc);
CREATE TEMP TABLE v_reception ON COMMIT DROP AS
SELECT "ID_RECEPTION" AS id FROM public.t_reception WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
CREATE TEMP TABLE v_dispatch ON COMMIT DROP AS
SELECT "ID_DISPATCH" AS id FROM public.t_dispatch WHERE "ID_RECEPTION" IN (SELECT id FROM v_reception);
CREATE TEMP TABLE v_examen ON COMMIT DROP AS
SELECT "ID_EXAMEN" AS id FROM public.t_examen WHERE "ID_DISPATCH" IN (SELECT id FROM v_dispatch);
CREATE TEMP TABLE v_resultat ON COMMIT DROP AS
SELECT "ID_DETAIL_EXAMEN" AS id FROM public.t_examen_detail WHERE "ID_EXAMEN" IN (SELECT id FROM v_examen);
CREATE TEMP TABLE v_pv ON COMMIT DROP AS
SELECT "ID_PV" AS id FROM public.t_pv_examen WHERE "ID_EXAMEN" IN (SELECT id FROM v_examen);
CREATE TEMP TABLE v_obs_pv ON COMMIT DROP AS
SELECT "ID_OBSERVATION_PV" AS id FROM public.t_observation_pv
 WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier) OR "ID_PV" IN (SELECT id FROM v_pv);
CREATE TEMP TABLE v_lettre ON COMMIT DROP AS
SELECT "ID_LETTRE" AS id FROM public.t_lettre_renvoi
 WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier) OR "ID_EXAMEN" IN (SELECT id FROM v_examen);
CREATE TEMP TABLE v_version ON COMMIT DROP AS
SELECT "ID_VERSION" AS id FROM public.t_version_dossier WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
CREATE TEMP TABLE v_snapshot ON COMMIT DROP AS
SELECT "ID_SNAPSHOT" AS id FROM public.t_snapshot_rectif_ligne
 WHERE "ID_VERSION" IN (SELECT id FROM v_version) OR "ID_DOSSIER" IN (SELECT id FROM v_dossier);
CREATE TEMP TABLE v_retrait ON COMMIT DROP AS
SELECT "ID_DEMANDE_RETRAIT" AS id FROM public.t_demande_retrait WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);

SELECT (SELECT count(*) FROM v_dossier) AS dossiers, (SELECT count(*) FROM v_ppm) AS ppm,
       (SELECT count(*) FROM v_ligne) AS lignes, (SELECT count(*) FROM v_lot) AS lots,
       (SELECT count(*) FROM v_dmc) AS dmc, (SELECT count(*) FROM v_fiche) AS versions_fiche,
       (SELECT count(*) FROM v_examen) AS examens, (SELECT count(*) FROM v_pv) AS pv;

-- Examen, PV, observations, lettres de renvoi.
DELETE FROM public.t_suivi_observation WHERE "ID_OBSERVATION_PV" IN (SELECT id FROM v_obs_pv);
DELETE FROM public.t_observation_pv WHERE "ID_OBSERVATION_PV" IN (SELECT id FROM v_obs_pv);
DELETE FROM public.t_observation_controle WHERE "ID_DETAIL" IN (SELECT id FROM v_resultat);
DELETE FROM public.t_examen_detail WHERE "ID_DETAIL_EXAMEN" IN (SELECT id FROM v_resultat);
DELETE FROM public.t_examen_piece WHERE "ID_EXAMEN" IN (SELECT id FROM v_examen);
DELETE FROM public.t_transmission_sigmp
 WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier) OR "ID_PV" IN (SELECT id FROM v_pv);
DELETE FROM public.t_verification
 WHERE "ID_PV" IN (SELECT id FROM v_pv) OR "ID_RECEPTION" IN (SELECT id FROM v_reception);
DELETE FROM public.t_pv_navette WHERE "ID_PV" IN (SELECT id FROM v_pv);
DELETE FROM public.t_lettre_renvoi_lue WHERE "ID_LETTRE" IN (SELECT id FROM v_lettre);
DELETE FROM public.t_piece_jointe_dossier WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_lettre_renvoi WHERE "ID_LETTRE" IN (SELECT id FROM v_lettre);
DELETE FROM public.t_pv_examen WHERE "ID_PV" IN (SELECT id FROM v_pv);

-- Circuit.
DELETE FROM public.t_copie_dossier
 WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier) OR "ID_DISPATCH" IN (SELECT id FROM v_dispatch);
DELETE FROM public.t_examen WHERE "ID_EXAMEN" IN (SELECT id FROM v_examen);
DELETE FROM public.t_dispatch WHERE "ID_DISPATCH" IN (SELECT id FROM v_dispatch);
UPDATE public.t_reception SET "ID_RECEPTION_PREC" = NULL WHERE "ID_RECEPTION" IN (SELECT id FROM v_reception);
DELETE FROM public.t_reception WHERE "ID_RECEPTION" IN (SELECT id FROM v_reception);
DELETE FROM public.t_verification_piece_depot WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_action_dossier WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_tache_dossier WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_notification WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
UPDATE public.t_message SET "ID_MESSAGE_PARENT" = NULL WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_message WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_suspension_dossier WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_piece_demande_retrait WHERE "ID_DEMANDE_RETRAIT" IN (SELECT id FROM v_retrait);
DELETE FROM public.t_demande_retrait WHERE "ID_DEMANDE_RETRAIT" IN (SELECT id FROM v_retrait);

-- Versions archivées, instantanés de rectification, changements de ligne.
DELETE FROM public.t_snapshot_rectif_beneficiaire WHERE "ID_SNAPSHOT" IN (SELECT id FROM v_snapshot);
DELETE FROM public.t_snapshot_rectif_lot WHERE "ID_SNAPSHOT" IN (SELECT id FROM v_snapshot);
DELETE FROM public.t_snapshot_rectif_prevision WHERE "ID_SNAPSHOT" IN (SELECT id FROM v_snapshot);
DELETE FROM public.t_snapshot_rectif_ligne WHERE "ID_SNAPSHOT" IN (SELECT id FROM v_snapshot);
DELETE FROM public.t_version_dossier WHERE "ID_VERSION" IN (SELECT id FROM v_version);
DELETE FROM public.t_changement_ligne WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);

-- Fiches DAO et DMC.
DELETE FROM public.t_document_fiche_marche WHERE "ID_FICHE" IN (SELECT id FROM v_fiche);
DELETE FROM public.t_fiche_marche_valeur WHERE "ID_FICHE" IN (SELECT id FROM v_fiche);
DELETE FROM public.t_fiche_marche WHERE "ID_FICHE" IN (SELECT id FROM v_fiche);
UPDATE public.t_dossier SET "ID_DMC" = NULL WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_dossier_mec WHERE "ID_DMC" IN (SELECT id FROM v_dmc);

-- Lignes de marché et PPM.
DELETE FROM public.t_tranche WHERE "ID_LOT" IN (SELECT id FROM v_lot);
DELETE FROM public.t_lot WHERE "ID_LOT" IN (SELECT id FROM v_lot);
DELETE FROM public.t_anomalie_ligne WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne);
DELETE FROM public.t_anomalie WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne) OR "ID_PPM" IN (SELECT id FROM v_ppm);
DELETE FROM public.t_echeance WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne);
DELETE FROM public.t_service_beneficiaire WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne);
DELETE FROM public.t_marche_prevision WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne);
DELETE FROM public.t_marche WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne);
DELETE FROM public.t_ppm WHERE "ID_PPM" IN (SELECT id FROM v_ppm);

-- Les dossiers (enfants d'abord détachés de leur parent).
UPDATE public.t_dossier SET "ID_DOSSIER_PARENT" = NULL WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);
DELETE FROM public.t_dossier WHERE "ID_DOSSIER" IN (SELECT id FROM v_dossier);

-- Ce qui reste (attendu : tout à 0).
SELECT (SELECT count(*) FROM public.t_dossier) AS dossiers, (SELECT count(*) FROM public.t_ppm) AS ppm,
       (SELECT count(*) FROM public.t_marche) AS lignes, (SELECT count(*) FROM public.t_dossier_mec) AS dmc,
       (SELECT count(*) FROM public.t_fiche_marche) AS fiches, (SELECT count(*) FROM public.t_examen) AS examens,
       (SELECT count(*) FROM public.t_pv_examen) AS pv, (SELECT count(*) FROM public.t_piece_jointe_dossier) AS pieces;

COMMIT;
