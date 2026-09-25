-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V44 — Une observation d'examen qui pointe une information de la fiche DAO (demande front du 2026-09-25,
-- demande-backend-2026-09-25-observation-sur-la-fiche.md)
--
-- Une ligne d'observation (« Au lieu de / Lire ») peut viser une information de la fiche marché du dossier examiné :
--   ID_DMC_FICHE          la fiche (t_dossier_mec.ID_DMC, celle du dossier examiné) ;
--   CHAMP_FICHE           la clé de l'information (« B04-VO-01 », « B05-GS-03#2 » pour le lot 2) ;
--   LIBELLE_CHAMP_FICHE   son libellé au référentiel, et VALEUR_CHAMP_FICHE sa valeur, FIGÉS quand l'observation est
--                         posée : la fiche peut être révisée ensuite, l'observation garde ce qui a été observé.
-- Recopiés au périmètre du PV (t_observation_pv) à la signature, comme la cellule visée de V30.
-- Le CHECK tient la règle 1 de la demande : une clé de champ ne veut rien dire sans sa fiche.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_observation_controle ADD COLUMN IF NOT EXISTS "ID_DMC_FICHE" bigint;
ALTER TABLE public.t_observation_controle ADD COLUMN IF NOT EXISTS "CHAMP_FICHE" character varying(24);
ALTER TABLE public.t_observation_controle ADD COLUMN IF NOT EXISTS "LIBELLE_CHAMP_FICHE" character varying(200);
ALTER TABLE public.t_observation_controle ADD COLUMN IF NOT EXISTS "VALEUR_CHAMP_FICHE" character varying(4000);
ALTER TABLE public.t_observation_controle DROP CONSTRAINT IF EXISTS "t_observation_controle_FICHE_check";
ALTER TABLE public.t_observation_controle ADD CONSTRAINT "t_observation_controle_FICHE_check"
    CHECK ("CHAMP_FICHE" IS NULL OR "ID_DMC_FICHE" IS NOT NULL);

ALTER TABLE public.t_observation_pv ADD COLUMN IF NOT EXISTS "ID_DMC_FICHE" bigint;
ALTER TABLE public.t_observation_pv ADD COLUMN IF NOT EXISTS "CHAMP_FICHE" character varying(24);
ALTER TABLE public.t_observation_pv ADD COLUMN IF NOT EXISTS "LIBELLE_CHAMP_FICHE" character varying(200);
ALTER TABLE public.t_observation_pv ADD COLUMN IF NOT EXISTS "VALEUR_CHAMP_FICHE" character varying(4000);
ALTER TABLE public.t_observation_pv DROP CONSTRAINT IF EXISTS "t_observation_pv_FICHE_check";
ALTER TABLE public.t_observation_pv ADD CONSTRAINT "t_observation_pv_FICHE_check"
    CHECK ("CHAMP_FICHE" IS NULL OR "ID_DMC_FICHE" IS NOT NULL);

COMMENT ON COLUMN public.t_observation_controle."CHAMP_FICHE" IS
    'Cle de l''information de la fiche DAO visee (CODE ou CODE#n pour un lot) ; libelle et valeur figes a l''observation.';
COMMENT ON COLUMN public.t_observation_pv."CHAMP_FICHE" IS
    'Information de la fiche DAO visee, recopiee de t_observation_controle a la signature du PV.';
