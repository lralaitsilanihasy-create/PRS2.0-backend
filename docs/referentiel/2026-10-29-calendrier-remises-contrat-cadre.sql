-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — le calendrier structuré des remises en concurrence d'un contrat-cadre (demande front du
-- 2026-10-29, demande-backend-2026-10-29-contrat-cadre-4a.md, §B3).
--
-- Deux champs nouveaux de la rubrique B02-PC, à côté du texte libre B02-PC-03 (qui reste celui imprimé au DPAC-CC et à l'AE-CC) :
--   B02-PC-04  périodicité des remises, en mois (NOMBRE) ;
--   B02-PC-05  date de la première remise (DATE).
-- Imprimés par aucun document (document maître AUCUN), figés au contrat en vigueur à la notification (V147) pour que la tâche
-- planifiée rappelle chaque remise à la PRMP. Facultatifs au référentiel : la règle CALENDRIER_REMISES (ControlesFicheMarche),
-- bloquante, les exige quand B02-PC-02 = « Selon le calendrier fixé ci-après » ; B02-PC-02 porte le rôle RYTHME.
--
-- Ce n'est PAS une migration : le fichier de correspondance du contrat-cadre porte ces valeurs ; ce script aligne une base où
-- il a déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-29-calendrier-remises-contrat-cadre.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'CALENDRIER_REMISES:RYTHME' WHERE "CODE" = 'B02-PC-02';

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF")
SELECT 'B02-PC-04', 'B02-PC', (SELECT coalesce(max(c."RANG"), 0) + 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE_RUBRIQUE" = 'B02-PC'),
       'Périodicité des remises en concurrence selon le calendrier (mois)', 'NOMBRE', 'SAISIE', 'AUCUN',
       NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX', NULL, false, NULL, 'CALENDRIER_REMISES:PERIODICITE', NULL,
       NULL, NULL, false, NULL, true
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE" = 'B02-PC-04');

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF")
SELECT 'B02-PC-05', 'B02-PC', (SELECT coalesce(max(c."RANG"), 0) + 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE_RUBRIQUE" = 'B02-PC'),
       'Date de la première remise en concurrence selon le calendrier', 'DATE', 'SAISIE', 'AUCUN',
       NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX', NULL, false, NULL, 'CALENDRIER_REMISES:PREMIERE', NULL,
       NULL, NULL, false, NULL, true
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE" = 'B02-PC-05');

SELECT "CODE", "RANG", "TYPE", "ACTIF", "OBLIGATOIRE", "DOCUMENT_MAITRE", coalesce("CONTROLE", '-') AS "CONTROLE"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" LIKE 'B02-PC-%' ORDER BY 1;

COMMIT;
