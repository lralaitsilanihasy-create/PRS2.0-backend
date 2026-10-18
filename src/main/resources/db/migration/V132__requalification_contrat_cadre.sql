-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V132 — Requalification ponctuelle en DAOO-CC des dossiers de contrat-cadre déjà nés en DAOO (demande front du 2026-10-18
-- `demande-backend-2026-10-18-requalification-contrat-cadre.md` ; le pilote, le 18/10 : la référence doit refléter le sous-type).
--
-- Cible : un dossier de la famille DMC, né d'une fiche (ID_DMC), en DAOO, dont la version de la fiche soumise — à défaut la
-- dernière validée — est un contrat-cadre (TYPE_MARCHE), non clôturé (Q2) et sans PV signé (Q1 : sa référence n'a pas été
-- diffusée). En recette : le seul dossier 100380 (00001/DAOO/CNM/2026).
--
-- Effet, sans renuméroter (le compteur de la référence est celui de la famille et de l'exercice, ReferenceService) :
--   - le sous-type passe à DAOO-CC (V131) ;
--   - le segment « /DAOO/ » devient « /DAOO-CC/ » dans la référence du dossier, de ses réceptions, de ses versions archivées et
--     de ses PV non signés (le segment exact : un segment d'entité n'est pas touché) ;
--   - une ligne SOUS_TYPE_REQUALIFIE au journal du dossier (ancienne et nouvelle référence, motif), sans opérateur.
-- Le dossier garde son étape, ses délais et sa place dans la file ; les écrans figés et les notifications envoyées ne sont pas
-- réécrits. Pour l'avenir, un contrat-cadre naît en DAOO-CC (V131) et un dossier soumis ne change plus de sous-type.
-- Idempotente (une seconde passe ne trouve plus de dossier en DAOO).
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP TABLE IF EXISTS v132_cible;
CREATE TEMPORARY TABLE v132_cible ON COMMIT DROP AS
SELECT d."ID_DOSSIER" AS id, d."REFE_DOSSIER" AS ancienne,
       CASE WHEN d."REFE_DOSSIER" LIKE '%/DAOO/%' THEN replace(d."REFE_DOSSIER", '/DAOO/', '/DAOO-CC/') ELSE d."REFE_DOSSIER" END AS nouvelle
FROM public.t_dossier d
WHERE d."ID_TYPE_DOSSIER" = 'DMC' AND d."ID_SOUS_TYPE" = 'DAOO' AND d."ID_DMC" IS NOT NULL
  AND d."STATUT" IS DISTINCT FROM 'CLOTURE'
  AND EXISTS (SELECT 1 FROM public.tr_sous_type_dossier s WHERE s."ID_SOUS_TYPE" = 'DAOO-CC')
  AND 'CONTRAT_CADRE' = (
        SELECT f."TYPE_MARCHE" FROM public.t_fiche_marche f
        WHERE f."ID_DMC" = d."ID_DMC" AND f."STATUT" = 'VALIDEE'
        ORDER BY (f."NUMERO_VERSION" = d."VERSION_FICHE_SOUMISE") DESC NULLS LAST, f."NUMERO_VERSION" DESC
        LIMIT 1)
  AND NOT EXISTS (
        SELECT 1 FROM public.t_pv_examen pv
        JOIN public.t_examen e ON e."ID_EXAMEN" = pv."ID_EXAMEN"
        JOIN public.t_dispatch di ON di."ID_DISPATCH" = e."ID_DISPATCH"
        JOIN public.t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
        WHERE r."ID_DOSSIER" = d."ID_DOSSIER" AND pv."STATUT_PV" IN ('SIGNE', 'ARCHIVE'));

UPDATE public.t_dossier d SET "ID_SOUS_TYPE" = 'DAOO-CC', "REFE_DOSSIER" = c.nouvelle
FROM v132_cible c WHERE d."ID_DOSSIER" = c.id;

UPDATE public.t_reception r SET "REFERENCE" = replace(r."REFERENCE", '/DAOO/', '/DAOO-CC/')
FROM v132_cible c WHERE r."ID_DOSSIER" = c.id AND r."REFERENCE" LIKE '%/DAOO/%';

UPDATE public.t_version_dossier v SET "REFERENCE" = replace(v."REFERENCE", '/DAOO/', '/DAOO-CC/')
FROM v132_cible c WHERE v."ID_DOSSIER" = c.id AND v."REFERENCE" LIKE '%/DAOO/%';

UPDATE public.t_pv_examen pv SET "REFERENCE_PV" = replace(pv."REFERENCE_PV", '/DAOO/', '/DAOO-CC/'),
                                 "REFE_PV" = replace(pv."REFE_PV", '/DAOO/', '/DAOO-CC/')
FROM public.t_examen e, public.t_dispatch di, public.t_reception r, v132_cible c
WHERE e."ID_EXAMEN" = pv."ID_EXAMEN" AND di."ID_DISPATCH" = e."ID_DISPATCH" AND r."ID_RECEPTION" = di."ID_RECEPTION"
  AND r."ID_DOSSIER" = c.id AND (pv."REFERENCE_PV" LIKE '%/DAOO/%' OR pv."REFE_PV" LIKE '%/DAOO/%');

INSERT INTO public.t_action_dossier ("ID_DOSSIER", "DATE_ACTION", "TYPE_ACTION", "AUTEUR", "DETAIL")
SELECT c.id, LOCALTIMESTAMP, 'SOUS_TYPE_REQUALIFIE', 'migration V132',
       left('Requalification contrat-cadre (sous-type DAOO-CC, V131) : DAOO → DAOO-CC ; référence '
            || COALESCE(c.ancienne, '(aucune)') || ' → ' || COALESCE(c.nouvelle, '(aucune)') || ', même numéro', 500)
FROM v132_cible c;

DROP TABLE v132_cible;
