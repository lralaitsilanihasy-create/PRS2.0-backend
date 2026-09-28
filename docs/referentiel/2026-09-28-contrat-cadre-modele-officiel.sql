-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — le contrat-cadre (fournitures et services) aligné sur le modèle officiel ARMP 2019
-- (demande front du 2026-09-28, demande-backend-2026-09-28-contrat-cadre-modele-officiel.md B1-B10 ; analyse
-- analyse-2026-09-28-modele-officiel-contrat-cadre.md, écarts E1-E13).
--
-- Ce n'est PAS une migration : les champs se chargent par les fichiers de correspondance (contrat-cadre et fournitures,
-- corrigés à l'identique). Ce script aligne une base où ils ont déjà été chargés. À lancer APRÈS V50 (type DATE_HEURE) ;
-- V51 retire déjà le reflet B09-PR-01 du contrat-cadre (rappelé ici, idempotent).
--
--   B1  B04-CP-02 DATE -> DATE_HEURE (date limite de remise avec son heure)
--   B2  B04-CP-06/07/08 créés (offres optimisées, courriers de rejet), ordonnés par DATES_ORDRE
--   B3  B04-RQ-04/05 inactifs (le mode de remise B04-SE-01 est la seule réponse)
--   B4  B07-PS-01 et B07-MA-05 inactifs ; options de B07-MA-04 nommées
--   B5  B03-TI-01..05, B03-GC-02..06, B08-FP-06..09 inactifs (informations du candidat)
--   B6  B04-VO-01 ouvert au contrat-cadre ; B06-AN-03 créé (recours, DPAC)
--   B7  B02-SG-03 réactivé : acte de nomination de la PRMP, obligatoire, défaut lu dans le mandat en vigueur ;
--       B02-SG-04 créé : personne habilitée à signer le contrat-cadre
--   B8  B07-MA-06, B05-PM-04, B05-PM-05 créés
--   B9  B07-DU-03 en jours ; B07-DU-07 créé (préavis de reconduction)
--   B10 B08-FI-03 inactif (le taux d'avance est la réponse de cadrage tauxAvance) ; B09-PR-01 hors contrat-cadre (V51)
--
-- Aucune fiche de contrat-cadre n'existait sur DBPRS20 au 28/09 : aucune valeur orpheline. Jamais de DELETE. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-28-contrat-cadre-modele-officiel.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

-- Garde : aucune fiche de contrat-cadre (sinon, voir la demande : valeurs orphelines conservées, non recopiées)
SELECT count(*) AS "FICHES_CONTRAT_CADRE" FROM public.t_fiche_marche WHERE "TYPE_MARCHE" = 'CONTRAT_CADRE';

-- Créations
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS", "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF") VALUES
    ('B04-CP-06', 'B04-CP', 6, 'Envoi des demandes d''offres optimisées', 'DATE', 'SAISIE', 'DPAC', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, false, NULL, 'DATES_ORDRE:OPTIMISEES_DEMANDE', NULL, NULL, NULL, false, NULL, true),
    ('B04-CP-07', 'B04-CP', 7, 'Date limite de réception des offres optimisées', 'DATE', 'SAISIE', 'DPAC', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, false, NULL, 'DATES_ORDRE:OPTIMISEES_RECEPTION', NULL, NULL, NULL, false, NULL, true),
    ('B04-CP-08', 'B04-CP', 8, 'Envoi des courriers de rejet aux candidats non retenus', 'DATE', 'SAISIE', 'DPAC', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, false, NULL, 'DATES_ORDRE:REJET', NULL, NULL, NULL, false, NULL, true),
    ('B06-AN-03', 'B06-AN', 3, 'Voies et délais de recours (instance chargée des recours)', 'TEXTE_LONG', 'SAISIE', 'DPAC', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B02-SG-04', 'B02-SG', 4, 'Personne habilitée à signer le contrat-cadre (nom, délégation, date de la décision)', 'TEXTE_LONG', 'SAISIE', 'AE', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, true, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B07-MA-06', 'B07-MA', 6, 'Critères et sous-critères pondérés de la remise en concurrence', 'TEXTE_LONG', 'SAISIE', 'AE', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'attributaires = MULTI', true, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B05-PM-04', 'B05-PM', 4, 'Plafond d''augmentation des prix à chaque complétude ou remise en concurrence (%)', 'POURCENTAGE', 'SAISIE', 'AE', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B05-PM-05', 'B05-PM', 5, 'Catalogue joint au contrat-cadre', 'OUI_NON', 'SAISIE', 'AE', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B07-DU-07', 'B07-DU', 7, 'Préavis de la décision de reconduction (mois)', 'NOMBRE', 'SAISIE', 'AE', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true)
ON CONFLICT ("CODE") DO UPDATE SET "CODE_RUBRIQUE" = EXCLUDED."CODE_RUBRIQUE", "RANG" = EXCLUDED."RANG", "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "SOURCE" = EXCLUDED."SOURCE", "DOCUMENT_MAITRE" = EXCLUDED."DOCUMENT_MAITRE", "REPRISES" = EXCLUDED."REPRISES", "TYPES_MARCHE" = EXCLUDED."TYPES_MARCHE", "CATEGORIES" = EXCLUDED."CATEGORIES", "CONDITION" = EXCLUDED."CONDITION", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE", "TEXTE_TYPE" = EXCLUDED."TEXTE_TYPE", "CONTROLE" = EXCLUDED."CONTROLE", "OPTIONS" = EXCLUDED."OPTIONS", "CLE_CADRAGE" = EXCLUDED."CLE_CADRAGE", "CLE_PPM" = EXCLUDED."CLE_PPM", "PAR_LOT" = EXCLUDED."PAR_LOT", "VALEUR_DEFAUT" = EXCLUDED."VALEUR_DEFAUT", "ACTIF" = EXCLUDED."ACTIF";

-- Désactivations
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" IN ('B04-RQ-04', 'B04-RQ-05', 'B07-PS-01', 'B07-MA-05', 'B03-TI-01', 'B03-TI-02', 'B03-TI-03', 'B03-TI-04', 'B03-TI-05', 'B03-GC-02', 'B03-GC-03', 'B03-GC-04', 'B03-GC-05', 'B03-GC-06', 'B08-FP-06', 'B08-FP-07', 'B08-FP-08', 'B08-FP-09', 'B08-FI-03');

-- Modifications
UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'DATE_HEURE' WHERE "CODE" = 'B04-CP-02';
UPDATE public.tr_champ_fiche_marche SET "OPTIONS" = 'Titulaires des lots correspondant à l''objet du marché,Titulaires de tous les lots' WHERE "CODE" = 'B07-MA-04';
UPDATE public.tr_champ_fiche_marche SET "LIBELLE" = 'Durée des marchés subséquents (jours)' WHERE "CODE" = 'B07-DU-03';
UPDATE public.tr_champ_fiche_marche SET "LIBELLE" = 'Acte de nomination de la PRMP (nature, numéro, date)', "OBLIGATOIRE" = true, "ACTIF" = true, "VALEUR_DEFAUT" = 'MANDAT:ACTE_NOMINATION' WHERE "CODE" = 'B02-SG-03';
UPDATE public.tr_champ_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE' WHERE "CODE" = 'B04-VO-01';
UPDATE public.tr_champ_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE' WHERE "CODE" = 'B09-PR-01';

-- Bilan
SELECT "CODE", "TYPE", "TYPES_MARCHE", "OBLIGATOIRE", coalesce("CONTROLE", '-') AS "CONTROLE", coalesce("VALEUR_DEFAUT", '-') AS "DEFAUT", "ACTIF"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B04-CP-06', 'B04-CP-07', 'B04-CP-08', 'B06-AN-03', 'B02-SG-04', 'B07-MA-06', 'B05-PM-04', 'B05-PM-05', 'B07-DU-07', 'B04-RQ-04', 'B04-RQ-05', 'B07-PS-01', 'B07-MA-05', 'B03-TI-01', 'B03-TI-02', 'B03-TI-03', 'B03-TI-04', 'B03-TI-05', 'B03-GC-02', 'B03-GC-03', 'B03-GC-04', 'B03-GC-05', 'B03-GC-06', 'B08-FP-06', 'B08-FP-07', 'B08-FP-08', 'B08-FP-09', 'B08-FI-03', 'B04-CP-02', 'B07-MA-04', 'B07-DU-03', 'B02-SG-03', 'B04-VO-01', 'B09-PR-01') ORDER BY 1;

COMMIT;
