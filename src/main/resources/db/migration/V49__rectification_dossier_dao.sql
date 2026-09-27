-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V49 — La rectification d'un dossier DAO après le PV (lot C, demande front du 2026-09-26,
-- demande-backend-2026-09-26-rectification-dossier-dao.md ; arbitrages du pilote Q2/Q3/Q4 du 26/09).
--
-- Principe : la rectification d'un dossier DAO est une RÉVISION VALIDÉE de la fiche marché — jamais un nouveau dossier,
-- jamais un ré-import. Le dossier garde sa référence, son circuit et son PV ; c'est la fiche qui change de version.
--
-- 1. t_dossier.VERSION_FICHE_SOUMISE  — la version de la fiche que le dossier a soumise (posée à la soumission, avancée à
--    chaque resoumission / transmission de compléments) : la référence de la garde FICHE_NON_REVISEE (B3).
--    t_dossier.VERSION_FICHE_EXAMINEE — la version que la Commission a examinée (posée à la soumission, puis = l'ancienne
--    version soumise quand les compléments sont transmis) : la borne « avant » du périmètre de réexamen (B5).
-- 2. t_observation_controle.VERSION_FICHE / t_observation_pv.VERSION_FICHE — la version de la fiche dont l'observation a
--    figé la valeur (B4 : « versionFicheObservee »), recopiée au PV comme le libellé et la valeur.
--
-- Reprise : les dossiers déjà soumis qui portent une fiche reçoivent, pour les deux versions, la dernière version validée
-- avant leur soumission (à défaut la dernière validée) ; les observations existantes restent sans version (inconnue).
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_dossier ADD COLUMN IF NOT EXISTS "VERSION_FICHE_SOUMISE" integer;
ALTER TABLE public.t_dossier ADD COLUMN IF NOT EXISTS "VERSION_FICHE_EXAMINEE" integer;
ALTER TABLE public.t_observation_controle ADD COLUMN IF NOT EXISTS "VERSION_FICHE" integer;
ALTER TABLE public.t_observation_pv ADD COLUMN IF NOT EXISTS "VERSION_FICHE" integer;

COMMENT ON COLUMN public.t_dossier."VERSION_FICHE_SOUMISE" IS
    'Version de la fiche marché soumise avec le dossier DAO (soumission, resoumission, compléments) — V49, lot C';
COMMENT ON COLUMN public.t_dossier."VERSION_FICHE_EXAMINEE" IS
    'Version de la fiche marché que la Commission a examinée (borne « avant » du réexamen) — V49, lot C';
COMMENT ON COLUMN public.t_observation_controle."VERSION_FICHE" IS
    'Version de la fiche dont l''observation a figé la valeur — V49, lot C';
COMMENT ON COLUMN public.t_observation_pv."VERSION_FICHE" IS
    'Version de la fiche dont l''observation a figé la valeur, recopiée au PV — V49, lot C';

UPDATE public.t_dossier d
   SET "VERSION_FICHE_SOUMISE" = v."NUMERO_VERSION", "VERSION_FICHE_EXAMINEE" = v."NUMERO_VERSION"
  FROM (SELECT f."ID_DMC", dd."ID_DOSSIER", max(f."NUMERO_VERSION") AS "NUMERO_VERSION"
          FROM public.t_fiche_marche f
          JOIN public.t_dossier dd ON dd."ID_DMC" = f."ID_DMC"
         WHERE f."STATUT" = 'VALIDEE'
           AND (dd."DATE_SOUMISSION" IS NULL OR f."DATE_VALIDATION" IS NULL OR f."DATE_VALIDATION" <= dd."DATE_SOUMISSION")
         GROUP BY f."ID_DMC", dd."ID_DOSSIER") v
 WHERE d."ID_DOSSIER" = v."ID_DOSSIER" AND d."STATUT" <> 'BROUILLON' AND d."VERSION_FICHE_SOUMISE" IS NULL;

UPDATE public.t_dossier d
   SET "VERSION_FICHE_SOUMISE" = v."NUMERO_VERSION", "VERSION_FICHE_EXAMINEE" = v."NUMERO_VERSION"
  FROM (SELECT f."ID_DMC", max(f."NUMERO_VERSION") AS "NUMERO_VERSION" FROM public.t_fiche_marche f
         WHERE f."STATUT" = 'VALIDEE' GROUP BY f."ID_DMC") v
 WHERE d."ID_DMC" = v."ID_DMC" AND d."STATUT" <> 'BROUILLON' AND d."VERSION_FICHE_SOUMISE" IS NULL;
