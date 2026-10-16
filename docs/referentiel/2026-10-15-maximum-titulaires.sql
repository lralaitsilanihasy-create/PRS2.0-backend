-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — le nombre maximal de titulaires du contrat-cadre (demande front du 2026-10-15,
-- demande-backend-2026-10-15-marches-subsequents.md, tranche MT-b1, réponse 4 du pilote : « pas de plafond dans le décret ;
-- demandé, un nombre maximal facultatif dans la fiche »).
--
-- B07-MA-08 : nombre, facultatif, posé au seul contrat-cadre multi-attributaire (condition attributaires = MULTI), imprimé par
-- aucun document (document maître AUCUN), lu à l'attribution (AttributionService, 400 TITULAIRES_TROP_NOMBREUX) ; vide, tous les
-- qualifiés peuvent être retenus.
--
-- Ce n'est PAS une migration : le fichier de correspondance du contrat-cadre porte cette ligne ; ce script aligne une base où il a
-- déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-15-maximum-titulaires.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF")
SELECT 'B07-MA-08', 'B07-MA', (SELECT coalesce(max(c."RANG"), 0) + 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE_RUBRIQUE" = 'B07-MA'),
       'Nombre maximal de titulaires du contrat-cadre (laisser vide : tous les qualifiés peuvent être retenus)', 'NOMBRE', 'SAISIE',
       'AUCUN', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX', 'attributaires = MULTI', false, NULL, NULL, NULL, NULL, NULL,
       false, NULL, true
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE" = 'B07-MA-08');

SELECT "CODE", "RANG", "ACTIF", "OBLIGATOIRE", "DOCUMENT_MAITRE", "CONDITION"
  FROM public.tr_champ_fiche_marche WHERE "CODE" = 'B07-MA-08';

COMMIT;
