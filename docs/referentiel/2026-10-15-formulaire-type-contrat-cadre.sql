-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — le formulaire du contrat-cadre face au document type ARMP (demande front du 2026-10-15,
-- demande-backend-2026-10-15-formulaire-type-contrat-cadre.md ; décision du pilote du 15/10 ; décret n° 2019-1064).
--
-- 1. §B1 — 20 champs RETIRÉS (actif = non, valeurs conservées) : à l'emplacement que le front proposait, le document type
--    porte un TEXTE FIXE, déjà repris tel quel par DPAC-CC / AE-CC (ADR-0011), ou rien (faits de la notification) :
--    B06-SO-01..04, B04-DS-02, -03, -06 (DPAC art. 3 et 6), B07-MA-03, B07-PI-01, B07-DU-01, B07-DE-04, B08-FI-04,
--    B09-AU-02, B09-EA-03, B10-RS-01, B03-SS-01, -02 (AE art. 4, 6, 7, 10, 11, 12, 16, 18), B06-NO-01, -02
--    (notification), B08-FI-05 (avance au sous-traitant, plus lue depuis l'arbitrage (b) de l'article 10).
--    B08-FP-03 (délai de paiement) reste, facultatif : lu par la règle DELAI_PAIEMENT_75.
-- 2. §B2 — B08-FP-05 « Règlement des prestations » imprimé à l'art. 15.1 (« Le règlement des prestations se fera … »),
--    obligatoire, servi aux deux catégories du contrat-cadre comme B08-FP-01 qu'il remplace dans la phrase.
-- 3. Champs nouveaux, servis au contrat-cadre des fournitures et des travaux :
--    - B05-PM-06 « Indice d'actualisation des prix » (§B3, facultatif) : renseigné, l'art. 9.3 imprime la formule et l'indice ;
--    - B05-MT-03 « Minimum du contrat-cadre » (§B4, facultatif, décret art. 34 : l'acheteur n'y est pas tenu) : renseigné, l'art. 8
--      dit le minimum et l'art. 18.1 perd la phrase « aucune indemnité » ;
--    - B07-MA-07 « Règles de détermination du prix moyen du marché » (Q2 de la demande « marchés subséquents », décret art. 23),
--      obligatoire en multi-attributaire, imprimé à l'art. 4 avec les deux clauses de l'article 23.
--
-- Ce n'est PAS une migration : le fichier de correspondance du contrat-cadre porte ces valeurs ; ce script aligne une base où
-- il a déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-15-formulaire-type-contrat-cadre.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false
 WHERE "CODE" IN ('B06-SO-01', 'B06-SO-02', 'B06-SO-03', 'B06-SO-04', 'B04-DS-02', 'B04-DS-03', 'B04-DS-06', 'B07-MA-03',
                  'B07-PI-01', 'B07-DU-01', 'B07-DE-04', 'B08-FI-04', 'B08-FI-05', 'B09-AU-02', 'B09-EA-03', 'B10-RS-01',
                  'B03-SS-01', 'B03-SS-02', 'B06-NO-01', 'B06-NO-02');

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = true, "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX'
 WHERE "CODE" = 'B08-FP-05';

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF")
SELECT n.code, n.rubrique, (SELECT coalesce(max(c."RANG"), 0) + 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE_RUBRIQUE" = n.rubrique),
       n.libelle, n.type, 'SAISIE', 'AE', NULL, 'CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX', n.condition, n.obligatoire,
       NULL, n.controle, NULL, NULL, NULL, false, NULL, true
  FROM (VALUES
        ('B05-MT-03', 'B05-MT', 'Minimum du contrat-cadre hors taxes (Ariary, laisser vide si le contrat-cadre est conclu sans minimum)',
         'MONTANT', NULL, false, 'MONTANT_POSITIF'),
        ('B05-PM-06', 'B05-PM', 'Indice d''actualisation des prix des marchés subséquents (laisser vide sans actualisation)',
         'TEXTE', NULL, false, NULL),
        ('B07-MA-07', 'B07-MA', 'Règles de détermination du prix moyen du marché (décret n° 2019-1064, article 23)',
         'TEXTE_LONG', 'attributaires = MULTI', true, NULL)
       ) AS n(code, rubrique, libelle, type, condition, obligatoire, controle)
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE" = n.code);

-- Contrôles (information).
SELECT "CODE", "CODE_RUBRIQUE", "RANG", "ACTIF", "OBLIGATOIRE", "CATEGORIES", coalesce("CONDITION", '-') AS "CONDITION"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B08-FP-05', 'B05-MT-03', 'B05-PM-06', 'B07-MA-07') ORDER BY 1;

SELECT count(*) FILTER (WHERE NOT "ACTIF") AS "RETIRES", count(*) FILTER (WHERE "ACTIF") AS "ENCORE_ACTIFS"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B06-SO-01', 'B06-SO-02', 'B06-SO-03', 'B06-SO-04', 'B04-DS-02', 'B04-DS-03', 'B04-DS-06', 'B07-MA-03',
                  'B07-PI-01', 'B07-DU-01', 'B07-DE-04', 'B08-FI-04', 'B08-FI-05', 'B09-AU-02', 'B09-EA-03', 'B10-RS-01',
                  'B03-SS-01', 'B03-SS-02', 'B06-NO-01', 'B06-NO-02');

COMMIT;
