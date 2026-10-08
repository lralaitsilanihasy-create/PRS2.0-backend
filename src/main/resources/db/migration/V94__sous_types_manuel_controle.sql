-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V94 — Le référentiel du contrôle aligné sur le Manuel de contrôle a priori (CNM, février 2026), tranche M1 (demande front du
-- 2026-10-08 `demande-backend-2026-10-08-manuel-controle-a-priori.md`, §B1 ; arbitrages du pilote du 08/10 : Q1 `DAO` renommé
-- `DAOO` références comprises ; Q2 variantes internationales en sous-types propres ; Q3 `MGG` ; Q4 `TEXTMP` hors application ; codes
-- sans accent ; DSS garde sa famille propre (V93) ; sous-type déduit du mode du plan).
--
-- 1. Les sous-types du manuel (p. 7) : DMC (DAOO, DPREQUAL, DAOOPREQUAL, DAOOI, DAORI, DC, DP, RJ), DDM (MAOOPREQUAL, MAOOI, MAORI,
--    MGG), et la famille nouvelle DGC « Acte de gestion contractuelle » (AVN, DR, INDEMN, PENAL, SURSIS). Libellé de MAOR corrigé.
-- 2. Le renommage DAO → DAOO : dossiers, points de contrôle, références (« n° / DAO / commission / année ») ; la contrainte qui
--    réservait le lien à la fiche (ID_DMC) au seul sous-type DAO s'ouvre aux six sous-types qu'une fiche produit.
-- Idempotente. Le TYPE DE DMC « DAO » (t_type_dmc) n'est pas touché.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.tr_type_dossier ("ID_TYPE_DOSSIER", "LIBELLE_TYPE")
SELECT 'DGC', 'Acte de gestion contractuelle'
WHERE NOT EXISTS (SELECT 1 FROM public.tr_type_dossier WHERE "ID_TYPE_DOSSIER" = 'DGC');

INSERT INTO public.tr_sous_type_dossier ("ID_SOUS_TYPE", "ID_TYPE_DOSSIER", "LIBELLE_SOUS_TYPE")
SELECT v.code, v.famille, v.libelle
FROM (VALUES
    ('DAOO',        'DMC', 'Dossier d''appel d''offres ouvert'),
    ('DPREQUAL',    'DMC', 'Dossier de pré-qualification'),
    ('DAOOPREQUAL', 'DMC', 'Dossier d''appel d''offres ouvert avec pré-qualification'),
    ('DAOOI',       'DMC', 'Dossier d''appel d''offres ouvert international'),
    ('DAORI',       'DMC', 'Dossier d''appel d''offres restreint international'),
    ('DC',          'DMC', 'Dossier de consultation (prestations intellectuelles)'),
    ('DP',          'DMC', 'Demande de proposition (bailleur)'),
    ('RJ',          'DMC', 'Rapport justificatif (gré à gré)'),
    ('MAOOPREQUAL', 'DDM', 'Marché sur appel d''offres ouvert avec pré-qualification'),
    ('MAOOI',       'DDM', 'Marché sur appel d''offres ouvert international'),
    ('MAORI',       'DDM', 'Marché sur appel d''offres restreint international'),
    ('MGG',         'DDM', 'Marché de gré à gré'),
    ('AVN',         'DGC', 'Avenant'),
    ('DR',          'DGC', 'Décision de résiliation'),
    ('INDEMN',      'DGC', 'Décision d''octroi d''indemnité'),
    ('PENAL',       'DGC', 'Décision de remise de pénalités'),
    ('SURSIS',      'DGC', 'Décision de sursis d''exécution')
) AS v(code, famille, libelle)
WHERE EXISTS (SELECT 1 FROM public.tr_type_dossier t WHERE t."ID_TYPE_DOSSIER" = v.famille)
  AND NOT EXISTS (SELECT 1 FROM public.tr_sous_type_dossier s WHERE s."ID_SOUS_TYPE" = v.code);

UPDATE public.tr_sous_type_dossier SET "LIBELLE_SOUS_TYPE" = 'Marché sur appel d''offres restreint' WHERE "ID_SOUS_TYPE" = 'MAOR';
UPDATE public.tr_sous_type_dossier SET "LIBELLE_SOUS_TYPE" = 'Dossier d''appel d''offres restreint' WHERE "ID_SOUS_TYPE" = 'DAOR';

-- Le renommage DAO → DAOO (Q1) : la contrainte du lien à la fiche d'abord, puis les données, puis l'ancien sous-type.
ALTER TABLE public.t_dossier DROP CONSTRAINT IF EXISTS ck_dossier_dmc_dao;

UPDATE public.t_dossier SET "ID_SOUS_TYPE" = 'DAOO' WHERE "ID_SOUS_TYPE" = 'DAO';
UPDATE public.tr_points_ctrl SET "ID_SOUS_TYPE" = 'DAOO' WHERE "ID_SOUS_TYPE" = 'DAO';
UPDATE public.t_reception SET "REFERENCE" = replace("REFERENCE", '/DAO/', '/DAOO/') WHERE "REFERENCE" LIKE '%/DAO/%';
UPDATE public.t_version_dossier SET "REFERENCE" = replace("REFERENCE", '/DAO/', '/DAOO/') WHERE "REFERENCE" LIKE '%/DAO/%';
UPDATE public.t_pv_examen SET "REFERENCE_PV" = replace("REFERENCE_PV", '/DAO/', '/DAOO/') WHERE "REFERENCE_PV" LIKE '%/DAO/%';
DELETE FROM public.tr_sous_type_dossier WHERE "ID_SOUS_TYPE" = 'DAO'
  AND NOT EXISTS (SELECT 1 FROM public.t_dossier WHERE "ID_SOUS_TYPE" = 'DAO')
  AND NOT EXISTS (SELECT 1 FROM public.tr_points_ctrl WHERE "ID_SOUS_TYPE" = 'DAO');

ALTER TABLE public.t_dossier ADD CONSTRAINT ck_dossier_dmc_dao
    CHECK ("ID_DMC" IS NULL OR "ID_SOUS_TYPE" IN ('DAOO', 'DAOR', 'DAOOI', 'DAORI', 'DAOOPREQUAL', 'DC'));
