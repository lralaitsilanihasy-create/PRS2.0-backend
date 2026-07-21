-- 2026-07-21 — Portée des points de contrôle (⚠️ règle ajoutée) : examen séquentiel par ligne de marché.
--
-- tr_points_ctrl porte désormais une colonne PORTEE (créée par Hibernate ddl-auto) :
--   LIGNE   = point évalué PAR LIGNE de marché (un ExamenDetail par marché × point) — défaut ;
--   DOSSIER = point INTER-LIGNES, évalué une seule fois pour le dossier (idDetail = null).
--
-- Ce script GARNIT la colonne sur les points existants (idempotent) :
--   - tous les points → LIGNE par défaut (là où la colonne est encore NULL) ;
--   - points INTER-LIGNES → DOSSIER : « fractionnement illicite » (découpage ENTRE marchés) et
--     « Cohérence » (cohérence d'ensemble des marchés du dossier — décision utilisateur 2026-07-21).
--
-- Ordre d'exécution : démarrer l'application (ddl-auto crée PORTEE), PUIS exécuter ce script.

SET client_encoding TO 'UTF8';
BEGIN;

-- Défaut LIGNE pour tout point sans portée (colonne ajoutée NULL sur l'existant).
UPDATE tr_points_ctrl SET "PORTEE" = 'LIGNE' WHERE "PORTEE" IS NULL;

-- Points inter-lignes (s'apprécient sur l'ensemble des marchés du dossier) → DOSSIER.
UPDATE tr_points_ctrl SET "PORTEE" = 'DOSSIER'
 WHERE "ID_TYPE_DOSSIER" = 'DDP' AND lower("LIBEL_POINT_CTRL") LIKE '%fractionnement%';
UPDATE tr_points_ctrl SET "PORTEE" = 'DOSSIER'
 WHERE "ID_TYPE_DOSSIER" = 'DDP' AND lower("LIBEL_POINT_CTRL") = lower('Cohérence');

COMMIT;

-- Vérifications :
--   SELECT "LIBEL_POINT_CTRL", "PORTEE" FROM tr_points_ctrl WHERE "ID_TYPE_DOSSIER"='DDP' ORDER BY "ORDRE_POINT_CTRL";
--   -> « fractionnement illicite » et « Cohérence » = DOSSIER ; les autres = LIGNE.

-- Réversion (« Cohérence » seule, si on veut la remettre en LIGNE) :
-- UPDATE tr_points_ctrl SET "PORTEE" = 'LIGNE'
--  WHERE "ID_TYPE_DOSSIER"='DDP' AND lower("LIBEL_POINT_CTRL") = lower('Cohérence');
-- Réversion totale :
-- UPDATE tr_points_ctrl SET "PORTEE" = 'LIGNE' WHERE "ID_TYPE_DOSSIER"='DDP';
