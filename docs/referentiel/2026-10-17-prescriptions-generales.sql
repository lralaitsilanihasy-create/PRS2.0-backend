-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — les prescriptions générales des fournitures (demande front du 2026-10-17,
-- demande-backend-2026-10-17-specifications-donnees-et-plans-pdf.md, §A1 ; arbitrage du pilote du 17/10 : en fournitures et
-- services, les spécifications sont les données du besoin, plus de Word).
--
-- B12-BE-01 : « Prescriptions générales applicables à tous les articles » (conditionnement, garantie du fabricant, normes,
-- marquage, notice) ; texte long, facultatif, au bloc du besoin (rubrique B12-BE), en fournitures et services (quantité fixe, à
-- commandes, contrat-cadre). Document maître AUCUN : seul le DAO complet l'imprime, en tête des spécifications techniques, avant le
-- tableau (DaoCompletService.PRESCRIPTIONS).
--
-- Ce n'est PAS une migration : le fichier de correspondance des fournitures porte cette ligne ; ce script aligne une base où il a
-- déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-17-prescriptions-generales.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF")
SELECT 'B12-BE-01', 'B12-BE', (SELECT coalesce(max(c."RANG"), 0) FROM public.tr_champ_fiche_marche c WHERE c."CODE_RUBRIQUE" = 'B12-BE') + 1,
       'Prescriptions générales applicables à tous les articles', 'TEXTE_LONG', 'SAISIE', 'AUCUN', NULL,
       'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, false, NULL, NULL, NULL, NULL, NULL,
       false, NULL, true
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE" = 'B12-BE-01');

SELECT "CODE", "CODE_RUBRIQUE", "RANG", "TYPE", "DOCUMENT_MAITRE", "TYPES_MARCHE", "CATEGORIES", "OBLIGATOIRE", "PAR_LOT"
  FROM public.tr_champ_fiche_marche WHERE "CODE" = 'B12-BE-01';

COMMIT;
