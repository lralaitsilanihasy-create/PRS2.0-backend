-- =====================================================================================================
-- V30 — Une observation d'examen vise une CELLULE du document (demande front du 2026-09-14,
-- frontend/docs/demande-backend-2026-09-14-observation-cellule-document.md).
--
-- LE CONSTAT. t_observation_controle porte N lignes « Au lieu de / Lire » par t_examen_detail. La ligne de
-- marché n'y est connue qu'à travers t_examen_detail.ID_DETAIL, nul pour les portées DOSSIER, FICHE et
-- AGPM ; et rien n'indique la COLONNE visée. Le front retrouvait la cellule en cherchant la valeur « Au
-- lieu de » dans la ligne : ambigu (deux montants égaux, bénéficiaires fusionnés, même mode sur plusieurs
-- lignes) et impossible pour la fiche et l'AGPM, dont les résultats n'ont pas de ligne.
--
-- CE QUI EST AJOUTÉ, sur les deux tables (l'observation d'examen et son instantané au PV) :
--   * CHAMP_CIBLE     — code de la colonne visée, liste fermée côté Java (enum ChampCible) ;
--   * ID_MARCHE_CIBLE — ligne de marché visée (t_marche.ID_DETAIL) ;
--   * ID_BENEF_CIBLE  — bénéficiaire visé (t_service_beneficiaire.ID_BENEF), colonnes par bénéficiaire.
-- Le CHECK tient l'invariant de la règle 1 : une cible sans champ n'a pas de sens.
--
-- PAS DE CLÉ ÉTRANGÈRE, comme en V19 : t_observation_pv est un instantané, les deux tables sont soumises
-- à la purge du circuit, et une ligne de marché reste retirable en rectification.
--
-- PAS DE REPRISE : l'existant vaut NULL, c'est-à-dire le comportement d'avant (aucune cellule encadrée).
--
-- Idempotente : ADD COLUMN IF NOT EXISTS, et chaque CHECK est retiré puis reposé (patron de V25).
-- =====================================================================================================

ALTER TABLE public.t_observation_controle
    ADD COLUMN IF NOT EXISTS "CHAMP_CIBLE"     varchar(40),
    ADD COLUMN IF NOT EXISTS "ID_MARCHE_CIBLE" integer,
    ADD COLUMN IF NOT EXISTS "ID_BENEF_CIBLE"  integer;

ALTER TABLE public.t_observation_pv
    ADD COLUMN IF NOT EXISTS "CHAMP_CIBLE"     varchar(40),
    ADD COLUMN IF NOT EXISTS "ID_MARCHE_CIBLE" integer,
    ADD COLUMN IF NOT EXISTS "ID_BENEF_CIBLE"  integer;

ALTER TABLE public.t_observation_controle DROP CONSTRAINT IF EXISTS "t_observation_controle_CIBLE_check";
ALTER TABLE public.t_observation_controle ADD CONSTRAINT "t_observation_controle_CIBLE_check"
    CHECK ("CHAMP_CIBLE" IS NOT NULL OR ("ID_MARCHE_CIBLE" IS NULL AND "ID_BENEF_CIBLE" IS NULL));

ALTER TABLE public.t_observation_pv DROP CONSTRAINT IF EXISTS "t_observation_pv_CIBLE_check";
ALTER TABLE public.t_observation_pv ADD CONSTRAINT "t_observation_pv_CIBLE_check"
    CHECK ("CHAMP_CIBLE" IS NOT NULL OR ("ID_MARCHE_CIBLE" IS NULL AND "ID_BENEF_CIBLE" IS NULL));

COMMENT ON COLUMN public.t_observation_controle."CHAMP_CIBLE" IS
    'Code de la cellule visee (enum ChampCible : colonne PPM, derogatoires./delaisAmenages./contratsCadres. '
    'pour la fiche, agpm. pour l''AGPM) ; NULL = aucune cellule (comportement anterieur a la V30).';
COMMENT ON COLUMN public.t_observation_controle."ID_MARCHE_CIBLE" IS
    'Ligne de marche visee (t_marche.ID_DETAIL), sans FK ; NULL si CHAMP_CIBLE est NULL.';
COMMENT ON COLUMN public.t_observation_controle."ID_BENEF_CIBLE" IS
    'Beneficiaire vise (t_service_beneficiaire.ID_BENEF), colonnes par beneficiaire seulement, sans FK.';
COMMENT ON COLUMN public.t_observation_pv."CHAMP_CIBLE" IS
    'Cellule visee, recopiee de t_observation_controle a la signature du PV FAVR ; NULL pour une piece ou '
    'un point sans ligne detaillee.';
