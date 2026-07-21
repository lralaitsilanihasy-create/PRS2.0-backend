-- 2026-07-21 — Portée des points de contrôle (⚠️ règle ajoutée) : examen séquentiel par ligne de marché.
--
-- tr_points_ctrl porte désormais une colonne PORTEE (créée par Hibernate ddl-auto) :
--   LIGNE   = point évalué PAR LIGNE de marché (un ExamenDetail par marché × point) — défaut ;
--   DOSSIER = point INTER-LIGNES, évalué une seule fois pour le dossier (idDetail = null).
--
-- Ce script GARNIT la colonne sur les points existants (idempotent) :
--   - tous les points → LIGNE par défaut (là où la colonne est encore NULL) ;
--   - « fractionnement illicite » (découpage ENTRE marchés) → DOSSIER.
-- « Cohérence » reste LIGNE (basculable en DOSSIER ici si la règle métier l'exige — data-driven).
--
-- Ordre d'exécution : démarrer l'application (ddl-auto crée PORTEE), PUIS exécuter ce script.

SET client_encoding TO 'UTF8';
BEGIN;

-- Défaut LIGNE pour tout point sans portée (colonne ajoutée NULL sur l'existant).
UPDATE tr_points_ctrl SET "PORTEE" = 'LIGNE' WHERE "PORTEE" IS NULL;

-- Point inter-lignes : le fractionnement illicite s'apprécie sur l'ensemble des marchés du dossier.
UPDATE tr_points_ctrl SET "PORTEE" = 'DOSSIER'
 WHERE "ID_TYPE_DOSSIER" = 'DDP' AND lower("LIBEL_POINT_CTRL") LIKE '%fractionnement%';

COMMIT;

-- Vérifications :
--   SELECT "LIBEL_POINT_CTRL", "PORTEE" FROM tr_points_ctrl WHERE "ID_TYPE_DOSSIER"='DDP' ORDER BY "ORDRE_POINT_CTRL";
--   -> « fractionnement illicite » = DOSSIER ; les autres = LIGNE.

-- Réversion :
-- UPDATE tr_points_ctrl SET "PORTEE" = 'LIGNE' WHERE "ID_TYPE_DOSSIER"='DDP';
