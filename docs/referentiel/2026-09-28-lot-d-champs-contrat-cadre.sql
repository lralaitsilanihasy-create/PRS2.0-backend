-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — lot D (demande front du 2026-09-28, demande-backend-2026-09-28-lot-d-dao-complet.md §B4) :
-- neuf champs du contrat-cadre (fournitures et services) que le document type officiel demande et que la fiche n'avait
-- pas. Saisie, facultatifs, sans condition. Les options de B09-GP-04 sont celles que les conditions de l'AE citent.
--
-- Ce n'est PAS une migration : le fichier de correspondance du contrat-cadre les porte ; ce script aligne une base où il
-- a déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-28-lot-d-champs-contrat-cadre.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "TYPES_MARCHE", "CATEGORIES", "OBLIGATOIRE", "OPTIONS", "PAR_LOT", "ACTIF") VALUES
    ('B04-DS-07', 'B04-DS', 7, 'Adresse de consultation du dossier : nom du responsable', 'TEXTE', 'SAISIE', 'DPAC', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, NULL, false, true),
    ('B04-DS-08', 'B04-DS', 8, 'Adresse de consultation du dossier : fonction', 'TEXTE', 'SAISIE', 'DPAC', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, NULL, false, true),
    ('B04-DS-09', 'B04-DS', 9, 'Adresse de consultation du dossier : bureau, n° de porte, étage', 'TEXTE', 'SAISIE', 'DPAC', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, NULL, false, true),
    ('B04-DS-10', 'B04-DS', 10, 'Adresse de consultation du dossier : localité', 'TEXTE_LONG', 'SAISIE', 'DPAC', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, NULL, false, true),
    ('B09-GP-03', 'B09-GP', 3, 'Délai de garantie des prestations (mois)', 'NOMBRE', 'SAISIE', 'AE', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, NULL, false, true),
    ('B09-GP-04', 'B09-GP', 4, 'Point de départ du délai de garantie', 'LISTE', 'SAISIE', 'AE', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, 'À partir de l''admission,À partir de la date de mise en service', false, true),
    ('B09-GP-05', 'B09-GP', 5, 'Garantie exécutée conformément au CCAG', 'OUI_NON', 'SAISIE', 'AE', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, NULL, false, true),
    ('B10-RS-02', 'B10-RS', 2, 'Préavis de résiliation sans faute (mois avant la date anniversaire)', 'NOMBRE', 'SAISIE', 'AE', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, NULL, false, true),
    ('B10-RS-03', 'B10-RS', 3, 'Fautes du titulaire ouvrant la résiliation du contrat-cadre', 'TEXTE_LONG', 'SAISIE', 'AE', 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', false, NULL, false, true)
ON CONFLICT ("CODE") DO UPDATE SET "CODE_RUBRIQUE" = EXCLUDED."CODE_RUBRIQUE", "RANG" = EXCLUDED."RANG", "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "SOURCE" = EXCLUDED."SOURCE", "DOCUMENT_MAITRE" = EXCLUDED."DOCUMENT_MAITRE", "TYPES_MARCHE" = EXCLUDED."TYPES_MARCHE", "CATEGORIES" = EXCLUDED."CATEGORIES", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE", "OPTIONS" = EXCLUDED."OPTIONS", "PAR_LOT" = EXCLUDED."PAR_LOT", "ACTIF" = EXCLUDED."ACTIF";

SELECT "CODE", "TYPE", "DOCUMENT_MAITRE", coalesce("OPTIONS", '-') AS "OPTIONS", "ACTIF"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B04-DS-07', 'B04-DS-08', 'B04-DS-09', 'B04-DS-10', 'B09-GP-03', 'B09-GP-04', 'B09-GP-05', 'B10-RS-02', 'B10-RS-03') ORDER BY 1;

COMMIT;
