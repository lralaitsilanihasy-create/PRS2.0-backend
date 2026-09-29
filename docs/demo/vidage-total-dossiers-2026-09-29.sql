-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- DBPRS20 — vidage TOTAL des données de dossiers (décision du pilote du 2026-09-29, SANS resemis)
--
-- Demande front `demande-backend-2026-09-29-vidage-total-dossiers.md`. Même périmètre que le vidage du 25/09
-- (vidage-total-dossiers-2026-09-25.sql, laissé tel quel), mis à jour d'après les CLÉS ÉTRANGÈRES de la base au 29/09 :
-- cinq tables de dossier nées depuis (V45 : t_fiche_article, t_fiche_caracteristique — le besoin de la fiche ; V50 :
-- t_parametre_interne_procedure, t_parametre_interne_journal, t_responsable_procedure — la remise électronique, par
-- DMC). V44 (observations sur un champ de la fiche), V49 (rectification du dossier DAO) et l'import du DAO (journal
-- FICHE_IMPORTEE) n'ont ajouté que des colonnes à des tables déjà vidées. Aucune table sans clé étrangère ne porte une
-- donnée de dossier (t_snapshot_stats : indicateurs agrégés, gardés).
--
-- Supprime TOUS les dossiers, PPM, lignes de marché, DMC et leurs fiches (versions, valeurs, documents produits, besoin,
-- paramètres internes et responsable de la procédure), avec tout
-- ce qui en dépend : lots, tranches, bénéficiaires, prévisions, échéances, anomalies, pièces jointes, circuit
-- (réceptions, dispatchs, copies, tâches, suspensions, vérifications de dépôt), examens, PV (navettes, vérifications,
-- périmètre d'observations et suivi, transmissions SIGMP), lettres de renvoi, versions archivées et instantanés,
-- changements de ligne, journal, notifications, messages, demandes de retrait.
--
-- Attendu, assumé par le pilote : GET /api/dossiers et GET /api/dmcs/eligibles renvoient une liste vide ; les écrans
-- PPM, DAO, examen, PV et suivi n'ont plus de données. NE RESÈME RIEN.
-- Ne sont PAS touchés : référentiels, comptes, mandats, intérims, journal technique (t_audit_log), indicateurs
-- agrégés, compteurs de référence (t_sequence_reference).
--
-- Rejouable (une base déjà vide ne perd rien) ; une transaction, une erreur annule tout. Les comptes s'affichent avant
-- et après ; le contrôle final porte sur TOUTES les tables du périmètre (attendu : 0 partout).
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/demo/vidage-total-dossiers-2026-09-29.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

-- Le périmètre : TOUT.
CREATE TEMP TABLE v_dossier ON COMMIT DROP AS SELECT "ID_DOSSIER" AS id FROM public.t_dossier;
CREATE TEMP TABLE v_ppm ON COMMIT DROP AS
SELECT "ID_PPM" AS id FROM public.t_ppm;
CREATE TEMP TABLE v_ligne ON COMMIT DROP AS
SELECT "ID_DETAIL" AS id FROM public.t_marche;
CREATE TEMP TABLE v_lot ON COMMIT DROP AS
SELECT "ID_LOT" AS id FROM public.t_lot
 WHERE "ID_DETAIL" IN (SELECT id FROM v_ligne) OR "ID_DOSSIER" IN (SELECT id FROM v_dossier);
CREATE TEMP TABLE v_dmc ON COMMIT DROP AS
SELECT "ID_DMC" AS id FROM public.t_dossier_mec;
CREATE TEMP TABLE v_fiche ON COMMIT DROP AS
SELECT "ID_FICHE" AS id FROM public.t_fiche_marche;
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

-- Comptes AVANT.
SELECT 'AVANT' AS moment, (SELECT count(*) FROM public.t_dossier) AS dossiers, (SELECT count(*) FROM public.t_ppm) AS ppm,
       (SELECT count(*) FROM public.t_marche) AS lignes, (SELECT count(*) FROM public.t_dossier_mec) AS dmc,
       (SELECT count(*) FROM public.t_fiche_marche) AS versions_fiche,
       (SELECT count(*) FROM public.t_fiche_marche_valeur) AS valeurs,
       (SELECT count(*) FROM public.t_document_fiche_marche) AS documents,
       (SELECT count(*) FROM public.t_examen) AS examens, (SELECT count(*) FROM public.t_pv_examen) AS pv,
       (SELECT count(*) FROM public.t_piece_jointe_dossier) AS pieces;

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

-- Fiches DAO et DMC. ⚠️ 2026-09-29 : le besoin de la fiche (V45) et la remise électronique par DMC (V50).
DELETE FROM public.t_fiche_caracteristique
 WHERE "ID_ARTICLE" IN (SELECT "ID_ARTICLE" FROM public.t_fiche_article WHERE "ID_FICHE" IN (SELECT id FROM v_fiche));
DELETE FROM public.t_fiche_article WHERE "ID_FICHE" IN (SELECT id FROM v_fiche);
DELETE FROM public.t_parametre_interne_journal WHERE "ID_DMC" IN (SELECT id FROM v_dmc);
DELETE FROM public.t_parametre_interne_procedure WHERE "ID_DMC" IN (SELECT id FROM v_dmc);
DELETE FROM public.t_responsable_procedure WHERE "ID_DMC" IN (SELECT id FROM v_dmc);
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

-- Comptes APRÈS (attendu : 0 partout).
SELECT 'APRES' AS moment, (SELECT count(*) FROM public.t_dossier) AS dossiers, (SELECT count(*) FROM public.t_ppm) AS ppm,
       (SELECT count(*) FROM public.t_marche) AS lignes, (SELECT count(*) FROM public.t_dossier_mec) AS dmc,
       (SELECT count(*) FROM public.t_fiche_marche) AS versions_fiche,
       (SELECT count(*) FROM public.t_fiche_marche_valeur) AS valeurs,
       (SELECT count(*) FROM public.t_document_fiche_marche) AS documents,
       (SELECT count(*) FROM public.t_examen) AS examens, (SELECT count(*) FROM public.t_pv_examen) AS pv,
       (SELECT count(*) FROM public.t_piece_jointe_dossier) AS pieces;

-- Contrôle de TOUT le périmètre (49 tables : clés étrangères vers t_dossier, t_ppm, t_marche, t_dossier_mec,
-- t_fiche_marche, relevées le 29/09, et tables du script du 25/09) : un reste annule toute la transaction.
DO $$
DECLARE restes text;
BEGIN
  SELECT string_agg(t || ' = ' || n, ', ') INTO restes FROM (
              SELECT 't_action_dossier' AS t, (SELECT count(*) FROM public.t_action_dossier) AS n
    UNION ALL SELECT 't_anomalie', (SELECT count(*) FROM public.t_anomalie)
    UNION ALL SELECT 't_anomalie_ligne', (SELECT count(*) FROM public.t_anomalie_ligne)
    UNION ALL SELECT 't_changement_ligne', (SELECT count(*) FROM public.t_changement_ligne)
    UNION ALL SELECT 't_copie_dossier', (SELECT count(*) FROM public.t_copie_dossier)
    UNION ALL SELECT 't_demande_retrait', (SELECT count(*) FROM public.t_demande_retrait)
    UNION ALL SELECT 't_dispatch', (SELECT count(*) FROM public.t_dispatch)
    UNION ALL SELECT 't_document_fiche_marche', (SELECT count(*) FROM public.t_document_fiche_marche)
    UNION ALL SELECT 't_dossier', (SELECT count(*) FROM public.t_dossier)
    UNION ALL SELECT 't_dossier_mec', (SELECT count(*) FROM public.t_dossier_mec)
    UNION ALL SELECT 't_echeance', (SELECT count(*) FROM public.t_echeance)
    UNION ALL SELECT 't_examen', (SELECT count(*) FROM public.t_examen)
    UNION ALL SELECT 't_examen_detail', (SELECT count(*) FROM public.t_examen_detail)
    UNION ALL SELECT 't_examen_piece', (SELECT count(*) FROM public.t_examen_piece)
    UNION ALL SELECT 't_fiche_article', (SELECT count(*) FROM public.t_fiche_article)
    UNION ALL SELECT 't_fiche_caracteristique', (SELECT count(*) FROM public.t_fiche_caracteristique)
    UNION ALL SELECT 't_fiche_marche', (SELECT count(*) FROM public.t_fiche_marche)
    UNION ALL SELECT 't_fiche_marche_valeur', (SELECT count(*) FROM public.t_fiche_marche_valeur)
    UNION ALL SELECT 't_lettre_renvoi', (SELECT count(*) FROM public.t_lettre_renvoi)
    UNION ALL SELECT 't_lettre_renvoi_lue', (SELECT count(*) FROM public.t_lettre_renvoi_lue)
    UNION ALL SELECT 't_lot', (SELECT count(*) FROM public.t_lot)
    UNION ALL SELECT 't_marche', (SELECT count(*) FROM public.t_marche)
    UNION ALL SELECT 't_marche_prevision', (SELECT count(*) FROM public.t_marche_prevision)
    UNION ALL SELECT 't_message', (SELECT count(*) FROM public.t_message)
    UNION ALL SELECT 't_notification', (SELECT count(*) FROM public.t_notification)
    UNION ALL SELECT 't_observation_controle', (SELECT count(*) FROM public.t_observation_controle)
    UNION ALL SELECT 't_observation_pv', (SELECT count(*) FROM public.t_observation_pv)
    UNION ALL SELECT 't_parametre_interne_journal', (SELECT count(*) FROM public.t_parametre_interne_journal)
    UNION ALL SELECT 't_parametre_interne_procedure', (SELECT count(*) FROM public.t_parametre_interne_procedure)
    UNION ALL SELECT 't_piece_demande_retrait', (SELECT count(*) FROM public.t_piece_demande_retrait)
    UNION ALL SELECT 't_piece_jointe_dossier', (SELECT count(*) FROM public.t_piece_jointe_dossier)
    UNION ALL SELECT 't_ppm', (SELECT count(*) FROM public.t_ppm)
    UNION ALL SELECT 't_pv_examen', (SELECT count(*) FROM public.t_pv_examen)
    UNION ALL SELECT 't_pv_navette', (SELECT count(*) FROM public.t_pv_navette)
    UNION ALL SELECT 't_reception', (SELECT count(*) FROM public.t_reception)
    UNION ALL SELECT 't_responsable_procedure', (SELECT count(*) FROM public.t_responsable_procedure)
    UNION ALL SELECT 't_service_beneficiaire', (SELECT count(*) FROM public.t_service_beneficiaire)
    UNION ALL SELECT 't_snapshot_rectif_beneficiaire', (SELECT count(*) FROM public.t_snapshot_rectif_beneficiaire)
    UNION ALL SELECT 't_snapshot_rectif_ligne', (SELECT count(*) FROM public.t_snapshot_rectif_ligne)
    UNION ALL SELECT 't_snapshot_rectif_lot', (SELECT count(*) FROM public.t_snapshot_rectif_lot)
    UNION ALL SELECT 't_snapshot_rectif_prevision', (SELECT count(*) FROM public.t_snapshot_rectif_prevision)
    UNION ALL SELECT 't_suivi_observation', (SELECT count(*) FROM public.t_suivi_observation)
    UNION ALL SELECT 't_suspension_dossier', (SELECT count(*) FROM public.t_suspension_dossier)
    UNION ALL SELECT 't_tache_dossier', (SELECT count(*) FROM public.t_tache_dossier)
    UNION ALL SELECT 't_tranche', (SELECT count(*) FROM public.t_tranche)
    UNION ALL SELECT 't_transmission_sigmp', (SELECT count(*) FROM public.t_transmission_sigmp)
    UNION ALL SELECT 't_verification', (SELECT count(*) FROM public.t_verification)
    UNION ALL SELECT 't_verification_piece_depot', (SELECT count(*) FROM public.t_verification_piece_depot)
    UNION ALL SELECT 't_version_dossier', (SELECT count(*) FROM public.t_version_dossier)
  ) x WHERE n > 0;
  IF restes IS NOT NULL THEN
    RAISE EXCEPTION 'Vidage incomplet, rien n''est écrit : %', restes;
  END IF;
  RAISE NOTICE 'Périmètre vide : 49 tables à 0.';
END $$;

COMMIT;
