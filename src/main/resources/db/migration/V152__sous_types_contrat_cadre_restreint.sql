-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V152 — ⚠️ 2026-10-30 : demande front « contrat-cadre-4b », §B2 (décision du pilote « suivre les textes en vigueur » ; décret
-- n° 2019-1064, art. 7 : le contrat-cadre se passe « par voie d'appel d'offres tel que prévu aux articles 35 et 38 du Code [ouvert,
-- restreint], soit par consultation […] (art. 41) »).
--
-- 1. DAOR-CC « Dossier d'appel d'offres restreint — contrat-cadre », famille DMC, grille de base DAOO (comme DAOR et DAOO-CC : les
--    variantes CONTRAT_CADRE des points de DAOO, V133-V135, s'y appliquent par la forme de la fiche). Ses pièces : celles de DAOR qui
--    valent pour le contrat-cadre (FORME nulle ou CONTRAT_CADRE), même ordre, mêmes conditions (comme V131 depuis DAOO).
-- 2. MAOR-CC « Marché sur appel d'offres restreint — contrat-cadre », famille DDM, grille de base MAOO (comme MAOR et MAOO-CC) ; ses
--    pièces : celles de MAOR (comme V137 depuis MAOO).
-- 3. Un dossier né d'une fiche peut porter DAOR-CC (ck_dossier_dmc_dao recréée).
-- La consultation (art. 41) n'a pas de sous-type propre en fournitures et travaux : elle produit DAOO (DAOO-CC en contrat-cadre),
-- comme avant. Les dossiers existants ne changent pas de sous-type. Idempotente ; rien n'est créé sans les familles et sous-types
-- d'origine (base de test vierge).
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.tr_sous_type_dossier ("ID_SOUS_TYPE", "ID_TYPE_DOSSIER", "LIBELLE_SOUS_TYPE", "GRILLE_BASE", "GRILLE_PROPRE")
SELECT 'DAOR-CC', 'DMC', 'Dossier d''appel d''offres restreint — contrat-cadre', 'DAOO', false
WHERE EXISTS (SELECT 1 FROM public.tr_type_dossier WHERE "ID_TYPE_DOSSIER" = 'DMC')
  AND EXISTS (SELECT 1 FROM public.tr_sous_type_dossier WHERE "ID_SOUS_TYPE" = 'DAOO')
ON CONFLICT ("ID_SOUS_TYPE") DO UPDATE SET "ID_TYPE_DOSSIER" = 'DMC', "LIBELLE_SOUS_TYPE" = EXCLUDED."LIBELLE_SOUS_TYPE",
    "GRILLE_BASE" = 'DAOO', "GRILLE_PROPRE" = false;

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME", "CONDITION")
SELECT p."ID_TYPE_PIECE", 'DAOR-CC', p."OBLIGATOIRE", p.rang, p."CATEGORIE", NULL, p."CONDITION"
FROM (SELECT s.*, ROW_NUMBER() OVER (ORDER BY s."ORDRE", s."ID") AS rang
      FROM public.t_piece_sous_type s
      WHERE s."ID_SOUS_TYPE" = 'DAOR' AND (s."FORME" IS NULL OR s."FORME" = 'CONTRAT_CADRE')) p
WHERE EXISTS (SELECT 1 FROM public.tr_sous_type_dossier t WHERE t."ID_SOUS_TYPE" = 'DAOR-CC')
  AND NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_SOUS_TYPE" = 'DAOR-CC' AND a."ID_TYPE_PIECE" = p."ID_TYPE_PIECE"
                    AND COALESCE(a."CATEGORIE", '') = COALESCE(p."CATEGORIE", ''));

INSERT INTO public.tr_sous_type_dossier ("ID_SOUS_TYPE", "ID_TYPE_DOSSIER", "LIBELLE_SOUS_TYPE", "GRILLE_BASE", "GRILLE_PROPRE")
SELECT 'MAOR-CC', 'DDM', 'Marché sur appel d''offres restreint — contrat-cadre', 'MAOO', false
WHERE EXISTS (SELECT 1 FROM public.tr_type_dossier WHERE "ID_TYPE_DOSSIER" = 'DDM')
  AND EXISTS (SELECT 1 FROM public.tr_sous_type_dossier WHERE "ID_SOUS_TYPE" = 'MAOO')
ON CONFLICT ("ID_SOUS_TYPE") DO UPDATE SET "ID_TYPE_DOSSIER" = 'DDM', "LIBELLE_SOUS_TYPE" = EXCLUDED."LIBELLE_SOUS_TYPE",
    "GRILLE_BASE" = 'MAOO', "GRILLE_PROPRE" = false;

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME", "CONDITION")
SELECT s."ID_TYPE_PIECE", 'MAOR-CC', s."OBLIGATOIRE", s."ORDRE", s."CATEGORIE", s."FORME", s."CONDITION"
FROM public.t_piece_sous_type s
WHERE s."ID_SOUS_TYPE" = 'MAOR'
  AND EXISTS (SELECT 1 FROM public.tr_sous_type_dossier t WHERE t."ID_SOUS_TYPE" = 'MAOR-CC')
  AND NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_SOUS_TYPE" = 'MAOR-CC' AND a."ID_TYPE_PIECE" = s."ID_TYPE_PIECE"
                    AND COALESCE(a."CATEGORIE", '') = COALESCE(s."CATEGORIE", '') AND COALESCE(a."FORME", '') = COALESCE(s."FORME", ''));

ALTER TABLE public.t_dossier DROP CONSTRAINT IF EXISTS ck_dossier_dmc_dao;
ALTER TABLE public.t_dossier ADD CONSTRAINT ck_dossier_dmc_dao
    CHECK ("ID_DMC" IS NULL OR "ID_SOUS_TYPE" IN ('DAOO', 'DAOO-CC', 'DAOR', 'DAOR-CC', 'DAOOI', 'DAORI', 'DAOOPREQUAL', 'DC'));
