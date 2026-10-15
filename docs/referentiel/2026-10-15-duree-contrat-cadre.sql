-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — la durée du contrat-cadre (demande front du 2026-10-15,
-- demande-backend-2026-10-15-marches-subsequents.md, §B0.5 ; décret n° 2019-1064, art. 19).
--
-- Règle DUREE_CONTRAT_CADRE_MAX (ControlesFicheMarche), bloquante : la plus longue des durées saisies — maximale
-- (B02-DC-01, mois), première période (B02-DC-02, mois), maximale en années (B07-DU-05) — au-delà de 24 mois exige la
-- justification d'un cas exceptionnel, portée par le champ nouveau B02-DC-05 (facultatif, lu par la règle, imprimé par aucun
-- document : document maître AUCUN).
--
-- Ce n'est PAS une migration : le fichier de correspondance du contrat-cadre porte ces valeurs ; ce script aligne une base où
-- il a déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-15-duree-contrat-cadre.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'DUREE_CONTRAT_CADRE_MAX:MOIS' WHERE "CODE" = 'B02-DC-01';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'DUREE_CONTRAT_CADRE_MAX:PERIODE' WHERE "CODE" = 'B02-DC-02';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'DUREE_CONTRAT_CADRE_MAX:ANS' WHERE "CODE" = 'B07-DU-05';

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF")
SELECT 'B02-DC-05', 'B02-DC', (SELECT coalesce(max(c."RANG"), 0) + 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE_RUBRIQUE" = 'B02-DC'),
       'Justification d''une durée supérieure à deux ans (décret n° 2019-1064, article 19)', 'TEXTE_LONG', 'SAISIE', 'AUCUN',
       NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX', NULL, false, NULL, 'DUREE_CONTRAT_CADRE_MAX:JUSTIFICATION', NULL,
       NULL, NULL, false, NULL, true
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE" = 'B02-DC-05');

SELECT "CODE", "RANG", "ACTIF", "OBLIGATOIRE", "DOCUMENT_MAITRE", coalesce("CONTROLE", '-') AS "CONTROLE"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B02-DC-01', 'B02-DC-02', 'B02-DC-05', 'B07-DU-05') ORDER BY 1;

COMMIT;
