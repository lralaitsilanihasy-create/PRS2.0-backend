-- 2026-07-17 — Restructuration des types de dossier en FAMILLES + SOUS-TYPES (⚠️ règle ajoutée).
--
-- 1) Renomme les codes de tr_type_dossier (familles) : PPM→DDP, DAO→DMC, MAOO→DDM,
--    en cascade sur les FK (t_dossier, t_type_piece_jointe, tr_points_ctrl — seules tables référentes).
-- 2) Garnit le nouveau référentiel tr_sous_type_dossier (liste OUVERTE, administrable) :
--    DDP ⊃ {PPM, PPM-AGPM} ; DMC ⊃ {DAO, DAOR} ; DDM ⊃ {MAOO, MAOR}.
-- 3) Backfill t_dossier.ID_SOUS_TYPE : famille DDP → PPM-AGPM si ≥1 marché en appel d'offres ouvert
--    (DECLENCHE_AGPM), sinon PPM ; DMC → DAO ; DDM → MAOO.
-- 4) Migre le compteur de références (t_sequence_reference) : (DOSSIER, PPM) → (DOSSIER, DDP), pour que
--    la numérotation des nouvelles références (segment famille) CONTINUE au lieu de repartir à 1.
--    Les références déjà générées (ex. 00011/PPM/CRM-ANT/2026) sont des snapshots : CONSERVÉES telles quelles.
--    La référence initiale PPM (xxxxx/<acronyme>/PPM/<année>, clé PPM_REF) garde son segment PPM : il nomme
--    le DOCUMENT (Plan de Passation de Marché = sous-type), pas la famille — inchangée.
--
-- ⚠️ Ordre d'exécution : démarrer d'abord l'application avec le nouveau code (ddl-auto crée
-- tr_sous_type_dossier et t_dossier.ID_SOUS_TYPE), PUIS exécuter ce script.
-- Idempotent : réexécutable sans effet de bord (gardes NOT EXISTS / WHERE sur anciens codes).

SET client_encoding TO 'UTF8';
BEGIN;

-- 1a) Nouvelles familles (insérées d'abord pour accueillir les FK re-pointées).
INSERT INTO tr_type_dossier ("ID_TYPE_DOSSIER", "LIBELLE_TYPE")
SELECT v.code, v.libelle FROM (VALUES
    ('DDP', 'Dossier de Planification'),
    ('DMC', 'Dossier de Mise en Concurrence'),
    ('DDM', 'Dossier de Marché')
) AS v(code, libelle)
WHERE NOT EXISTS (SELECT 1 FROM tr_type_dossier t WHERE t."ID_TYPE_DOSSIER" = v.code);

-- 1b) Re-pointage des FK vers les nouvelles familles.
UPDATE t_dossier SET "ID_TYPE_DOSSIER"='DDP' WHERE "ID_TYPE_DOSSIER"='PPM';
UPDATE t_dossier SET "ID_TYPE_DOSSIER"='DMC' WHERE "ID_TYPE_DOSSIER"='DAO';
UPDATE t_dossier SET "ID_TYPE_DOSSIER"='DDM' WHERE "ID_TYPE_DOSSIER"='MAOO';
UPDATE t_type_piece_jointe SET "ID_TYPE_DOSSIER"='DDP' WHERE "ID_TYPE_DOSSIER"='PPM';
UPDATE t_type_piece_jointe SET "ID_TYPE_DOSSIER"='DMC' WHERE "ID_TYPE_DOSSIER"='DAO';
UPDATE t_type_piece_jointe SET "ID_TYPE_DOSSIER"='DDM' WHERE "ID_TYPE_DOSSIER"='MAOO';
UPDATE tr_points_ctrl SET "ID_TYPE_DOSSIER"='DDP' WHERE "ID_TYPE_DOSSIER"='PPM';
UPDATE tr_points_ctrl SET "ID_TYPE_DOSSIER"='DMC' WHERE "ID_TYPE_DOSSIER"='DAO';
UPDATE tr_points_ctrl SET "ID_TYPE_DOSSIER"='DDM' WHERE "ID_TYPE_DOSSIER"='MAOO';

-- 1c) Suppression des anciens codes (plus référencés).
DELETE FROM tr_type_dossier WHERE "ID_TYPE_DOSSIER" IN ('PPM','DAO','MAOO');

-- 2) Sous-types initiaux (liste ouverte — l'admin en ajoutera d'autres via /api/sous-type-dossiers).
INSERT INTO tr_sous_type_dossier ("ID_SOUS_TYPE", "LIBELLE_SOUS_TYPE", "ID_TYPE_DOSSIER")
SELECT v.code, v.libelle, v.famille FROM (VALUES
    ('PPM',      'Plan de Passation de Marché',                                          'DDP'),
    ('PPM-AGPM', 'Plan de Passation de Marché et Avis Général de Passation de Marché',   'DDP'),
    ('DAO',      'Dossier d''Appel d''Offres',                                           'DMC'),
    ('DAOR',     'Dossier d''Appel d''Offres Restreint',                                 'DMC'),
    ('MAOO',     'Marché sur Appel d''Offres Ouvert',                                    'DDM'),
    ('MAOR',     'Marché sur Appel d''Offres Ouvert Restreint',                          'DDM')
) AS v(code, libelle, famille)
WHERE NOT EXISTS (SELECT 1 FROM tr_sous_type_dossier s WHERE s."ID_SOUS_TYPE" = v.code);

-- 3) Backfill du sous-type des dossiers existants (uniquement là où il est encore NULL).
UPDATE t_dossier d SET "ID_SOUS_TYPE" =
    CASE WHEN EXISTS (SELECT 1 FROM t_marche m JOIN tr_mode_passation mo ON mo."ID_MODE" = m."ID_MODE"
                      WHERE m."ID_DOSSIER" = d."ID_DOSSIER" AND mo."DECLENCHE_AGPM" = true)
         THEN 'PPM-AGPM' ELSE 'PPM' END
WHERE d."ID_TYPE_DOSSIER" = 'DDP' AND d."ID_SOUS_TYPE" IS NULL;
UPDATE t_dossier SET "ID_SOUS_TYPE"='DAO'  WHERE "ID_TYPE_DOSSIER"='DMC' AND "ID_SOUS_TYPE" IS NULL;
UPDATE t_dossier SET "ID_SOUS_TYPE"='MAOO' WHERE "ID_TYPE_DOSSIER"='DDM' AND "ID_SOUS_TYPE" IS NULL;

-- 4) Compteur des références de dossier : la clé type passe de PPM à DDP (numérotation continue).
UPDATE t_sequence_reference SET "TYPE_DOSSIER"='DDP'
WHERE "TYPE_DOSSIER"='PPM' AND "CODE_LOCALITE"='DOSSIER'
  AND NOT EXISTS (SELECT 1 FROM t_sequence_reference s
                  WHERE s."TYPE_DOSSIER"='DDP' AND s."CODE_LOCALITE"='DOSSIER');

COMMIT;

-- Vérifications :
--   SELECT * FROM tr_type_dossier ORDER BY "ID_TYPE_DOSSIER";                       -> DDM, DMC, DDP
--   SELECT * FROM tr_sous_type_dossier ORDER BY "ID_TYPE_DOSSIER","ID_SOUS_TYPE";   -> 6 lignes
--   SELECT "ID_TYPE_DOSSIER","ID_SOUS_TYPE", count(*) FROM t_dossier GROUP BY 1,2;  -> DDP/PPM + DDP/PPM-AGPM
--   SELECT * FROM t_sequence_reference WHERE "CODE_LOCALITE"='DOSSIER';             -> (DDP, DOSSIER, 2026)

-- Réversion (ordre inverse) :
--   UPDATE t_sequence_reference SET "TYPE_DOSSIER"='PPM' WHERE "TYPE_DOSSIER"='DDP' AND "CODE_LOCALITE"='DOSSIER';
--   UPDATE t_dossier SET "ID_SOUS_TYPE"=NULL;
--   INSERT INTO tr_type_dossier VALUES ('PPM','Dossiers de Planification'),('DAO','Dossiers de Mise en Concurrence'),('MAOO','Dossiers de marchés');
--   UPDATE t_dossier SET "ID_TYPE_DOSSIER"='PPM' WHERE "ID_TYPE_DOSSIER"='DDP';  (idem DMC→DAO, DDM→MAOO, sur les 3 tables)
--   DELETE FROM tr_sous_type_dossier; DELETE FROM tr_type_dossier WHERE "ID_TYPE_DOSSIER" IN ('DDP','DMC','DDM');
