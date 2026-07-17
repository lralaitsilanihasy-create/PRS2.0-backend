-- ============================================================================
-- OUTIL — Purge complète d'UN dossier et de tout son arbre (données de test).
-- ============================================================================
-- Usage :
--   psql -U postgres -h localhost -d DBPRS20 -v ON_ERROR_STOP=1 \
--        -v id=<ID_DOSSIER> -f docs/outils/purge-dossier.sql
--
-- Supprime, en UNE transaction et en ordre FK-safe (feuilles → racine), tout
-- l'historique du dossier :
--   observations → détails d'examen → navettes → vérifications → PV
--   → accusés de lecture → lettres de renvoi → copies → examens → dispatchs
--   → réceptions → demandes de retrait → notifications → messages
--   → tranches → lots → bénéficiaires → prévisions → DMC → anomalies → échéances
--   → marchés → PPM → pièces jointes → dossier.
-- Le journal d'audit (t_audit_log, sans FK) est CONSERVÉ. Les référentiels ne
-- sont jamais touchés.
--
-- Gardes : id obligatoire (-v id=…) ; dossier inexistant → abandon ;
-- dossier ayant des dossiers ENFANTS (ID_DOSSIER_PARENT) → abandon (purger
-- d'abord les enfants). ⚠️ Outil de DEV : ne pas exécuter en production.
-- ============================================================================

\if :{?id}
\else
\echo 'ERREUR : id manquant. Usage : psql -v id=<ID_DOSSIER> -f docs/outils/purge-dossier.sql'
\quit
\endif

SET client_encoding TO 'UTF8';

-- Garde 1 : le dossier doit exister.
SELECT count(*) = 0 AS dossier_introuvable FROM t_dossier WHERE "ID_DOSSIER" = :id \gset
\if :dossier_introuvable
\echo 'ABANDON : aucun dossier avec cet id.'
\quit
\endif

-- Garde 2 : pas de dossiers enfants (les purger d'abord).
SELECT count(*) > 0 AS a_des_enfants FROM t_dossier WHERE "ID_DOSSIER_PARENT" = :id \gset
\if :a_des_enfants
\echo 'ABANDON : ce dossier a des dossiers enfants (ID_DOSSIER_PARENT) — purger d''abord les enfants.'
\quit
\endif

\echo '=== Dossier à purger ==='
SELECT "ID_DOSSIER", "STATUT", "ID_TYPE_DOSSIER", "ID_SOUS_TYPE", "REFE_DOSSIER", "ID_PRMP", "ID_LOCALITE"
FROM t_dossier WHERE "ID_DOSSIER" = :id;

BEGIN;

-- 1. Feuilles de l'examen : observations → détails de grille
DELETE FROM t_observation_controle WHERE "ID_DETAIL" IN (
    SELECT ed."ID_DETAIL_EXAMEN" FROM t_examen_detail ed
    JOIN t_examen e ON e."ID_EXAMEN" = ed."ID_EXAMEN"
    JOIN t_dispatch di ON di."ID_DISPATCH" = e."ID_DISPATCH"
    JOIN t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
    WHERE r."ID_DOSSIER" = :id);
DELETE FROM t_examen_detail WHERE "ID_EXAMEN" IN (
    SELECT e."ID_EXAMEN" FROM t_examen e
    JOIN t_dispatch di ON di."ID_DISPATCH" = e."ID_DISPATCH"
    JOIN t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
    WHERE r."ID_DOSSIER" = :id);

-- 2. PV : navettes → vérifications → PV
DELETE FROM t_pv_navette WHERE "ID_PV" IN (
    SELECT pv."ID_PV" FROM t_pv_examen pv
    JOIN t_examen e ON e."ID_EXAMEN" = pv."ID_EXAMEN"
    JOIN t_dispatch di ON di."ID_DISPATCH" = e."ID_DISPATCH"
    JOIN t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
    WHERE r."ID_DOSSIER" = :id);
DELETE FROM t_verification WHERE "ID_RECEPTION" IN (
    SELECT r."ID_RECEPTION" FROM t_reception r WHERE r."ID_DOSSIER" = :id);
DELETE FROM t_pv_examen WHERE "ID_EXAMEN" IN (
    SELECT e."ID_EXAMEN" FROM t_examen e
    JOIN t_dispatch di ON di."ID_DISPATCH" = e."ID_DISPATCH"
    JOIN t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
    WHERE r."ID_DOSSIER" = :id);

-- 3. Lettres de renvoi : accusés de lecture → lettres ; copies
DELETE FROM t_lettre_renvoi_lue WHERE "ID_LETTRE" IN (
    SELECT l."ID_LETTRE" FROM t_lettre_renvoi l WHERE l."ID_DOSSIER" = :id);
DELETE FROM t_lettre_renvoi WHERE "ID_DOSSIER" = :id;
DELETE FROM t_copie_dossier WHERE "ID_DOSSIER" = :id;

-- 4. Circuit : examens → dispatchs → réceptions
DELETE FROM t_examen WHERE "ID_DISPATCH" IN (
    SELECT di."ID_DISPATCH" FROM t_dispatch di
    JOIN t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
    WHERE r."ID_DOSSIER" = :id);
DELETE FROM t_dispatch WHERE "ID_RECEPTION" IN (
    SELECT r."ID_RECEPTION" FROM t_reception r WHERE r."ID_DOSSIER" = :id);
DELETE FROM t_reception WHERE "ID_DOSSIER" = :id;

-- 5. Historique direct du dossier
DELETE FROM t_demande_retrait WHERE "ID_DOSSIER" = :id;
DELETE FROM t_notification WHERE "ID_DOSSIER" = :id;
DELETE FROM t_message WHERE "ID_DOSSIER" = :id;

-- 6. Contenu : tranches → lots ; enfants du marché → marchés → PPM
DELETE FROM t_tranche WHERE "ID_LOT" IN (
    SELECT lo."ID_LOT" FROM t_lot lo WHERE lo."ID_DOSSIER" = :id);
DELETE FROM t_lot WHERE "ID_DOSSIER" = :id;
DELETE FROM t_service_beneficiaire WHERE "ID_DETAIL" IN (
    SELECT m."ID_DETAIL" FROM t_marche m WHERE m."ID_DOSSIER" = :id);
DELETE FROM t_marche_prevision WHERE "ID_DETAIL" IN (
    SELECT m."ID_DETAIL" FROM t_marche m WHERE m."ID_DOSSIER" = :id);
DELETE FROM t_dossier_mec WHERE "ID_DETAIL" IN (
    SELECT m."ID_DETAIL" FROM t_marche m WHERE m."ID_DOSSIER" = :id);
DELETE FROM t_anomalie WHERE "ID_DETAIL" IN (
    SELECT m."ID_DETAIL" FROM t_marche m WHERE m."ID_DOSSIER" = :id);
DELETE FROM t_echeance WHERE "ID_DETAIL" IN (
    SELECT m."ID_DETAIL" FROM t_marche m WHERE m."ID_DOSSIER" = :id);
DELETE FROM t_marche WHERE "ID_DOSSIER" = :id;
DELETE FROM t_ppm WHERE "ID_DOSSIER" = :id;

-- 7. Pièces jointes puis le dossier lui-même
DELETE FROM t_piece_jointe_dossier WHERE "ID_DOSSIER" = :id;
DELETE FROM t_dossier WHERE "ID_DOSSIER" = :id;

COMMIT;

\echo '=== Vérification (0 attendu partout) ==='
SELECT 'dossier' t, count(*) FROM t_dossier WHERE "ID_DOSSIER" = :id
UNION ALL SELECT 'reception', count(*) FROM t_reception WHERE "ID_DOSSIER" = :id
UNION ALL SELECT 'demande_retrait', count(*) FROM t_demande_retrait WHERE "ID_DOSSIER" = :id
UNION ALL SELECT 'notification', count(*) FROM t_notification WHERE "ID_DOSSIER" = :id
UNION ALL SELECT 'marche', count(*) FROM t_marche WHERE "ID_DOSSIER" = :id
UNION ALL SELECT 'ppm', count(*) FROM t_ppm WHERE "ID_DOSSIER" = :id
UNION ALL SELECT 'piece_jointe', count(*) FROM t_piece_jointe_dossier WHERE "ID_DOSSIER" = :id
UNION ALL SELECT 'lettre_renvoi', count(*) FROM t_lettre_renvoi WHERE "ID_DOSSIER" = :id;

\echo 'Purge terminée (journal d''audit conservé).'
