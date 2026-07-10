-- Migration DBPRS20 — Dossier de mise en concurrence (DMC) : référentiel des types + mapping mode → type.
-- Contexte projet : ddl-auto=update crée les tables/colonnes depuis les entités
--   (t_type_dmc, t_dossier_mec, tr_mode_passation.ID_TYPE_DMC) ; ce script ne fait que le SEED
--   et le MAPPING (données), à appliquer une fois les entités déployées.
-- globally_quoted_identifiers=true : identifiants quotés (table minuscule, colonnes MAJUSCULES).

-- 1) Seed du référentiel des types de DMC (liste ouverte, complétable en administration).
INSERT INTO "t_type_dmc" ("CODE", "LIBELLE", "ACTIF") VALUES
    ('DAO', 'Dossier d''Appel d''Offres', TRUE),
    ('DC',  'Dossier de Consultation',    TRUE),
    ('BC',  'Bon de Commande',            TRUE)
ON CONFLICT ("CODE") DO NOTHING;

-- 2) Mapping mode de passation → type de DMC (1 mode = 1 type).
--    ⚠️ Le rapprochement par libellé est indicatif : VÉRIFIER les libellés réels de tr_mode_passation
--       (ou mapper par ID_MODE) avant application. Les modes non mappés restent NULL → la création
--       du DMC est refusée avec un message de configuration (à mapper via l'écran admin des modes).
UPDATE "tr_mode_passation" SET "ID_TYPE_DMC" =
    (SELECT "ID_TYPE_DMC" FROM "t_type_dmc" WHERE "CODE" = 'DAO')
    WHERE "LIBELLE" ILIKE 'Appel d''offres ouvert%';

UPDATE "tr_mode_passation" SET "ID_TYPE_DMC" =
    (SELECT "ID_TYPE_DMC" FROM "t_type_dmc" WHERE "CODE" = 'DC')
    WHERE "LIBELLE" ILIKE 'Consultation de Prix Ouverte%';

UPDATE "tr_mode_passation" SET "ID_TYPE_DMC" =
    (SELECT "ID_TYPE_DMC" FROM "t_type_dmc" WHERE "CODE" = 'BC')
    WHERE "LIBELLE" ILIKE 'Achat Direct%';
