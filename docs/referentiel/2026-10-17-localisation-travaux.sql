-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — la localisation des travaux par lot (demande front du 2026-10-17,
-- demande-backend-2026-10-17-charte-dao-travaux.md, T1 et Q1 ; proposition du backend acceptée par le pilote le 17/10).
--
-- B02-OT-03 à B02-OT-06 : lieu, points kilométriques, coordonnées GPS de début et de fin ; textes facultatifs, par lot, en
-- travaux (quantité fixe et à commandes). Ils ne sont imprimés par aucun document type (document maître AUCUN) : seule la garde
-- du DAO complet les lit (DaoCompletService.garde), sous la ligne du lot. Aucune localité de la ligne du plan n'est utilisable
-- telle quelle.
--
-- Ce n'est PAS une migration : le fichier de correspondance des travaux porte ces lignes ; ce script aligne une base où il a déjà
-- été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-17-localisation-travaux.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF")
SELECT v.code, 'B02-OT', (SELECT coalesce(max(c."RANG"), 0) FROM public.tr_champ_fiche_marche c WHERE c."CODE_RUBRIQUE" = 'B02-OT') + v.n,
       v.libelle, 'TEXTE', 'SAISIE', 'AUCUN', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL,
       true, NULL, true
  FROM (VALUES (1, 'B02-OT-03', 'Localisation des travaux : lieu, commune, district, région (garde du DAO)'),
               (2, 'B02-OT-04', 'Points kilométriques de début et de fin d''un ouvrage linéaire (ex. « PK 0 à PK 32 »)'),
               (3, 'B02-OT-05', 'Coordonnées GPS du début des travaux'),
               (4, 'B02-OT-06', 'Coordonnées GPS de la fin des travaux')) AS v(n, code, libelle)
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE" = v.code);

SELECT "CODE", "RANG", "PAR_LOT", "OBLIGATOIRE", "DOCUMENT_MAITRE", "ACTIF"
  FROM public.tr_champ_fiche_marche WHERE "CODE" LIKE 'B02-OT-%' ORDER BY "CODE";

COMMIT;
