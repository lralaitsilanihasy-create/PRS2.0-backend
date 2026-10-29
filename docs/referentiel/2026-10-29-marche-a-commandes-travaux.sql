-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — le marché à commandes en travaux (demande front du 2026-10-29,
-- demande-backend-2026-10-29-marche-a-commandes-champs.md, décision du pilote D4 : « suivre les textes en vigueur » ; loi
-- n° 2016-055, art. 30-I : le marché à commandes se conclut avec un fournisseur, un ENTREPRENEUR ou un prestataire).
--
-- 1. B02-AU-04 (durée de validité du marché à commandes, mois) est aussi servi aux travaux ; règle DUREE_MARCHE_A_COMMANDES_MAX :
--    36 mois au plus.
-- 2. B09-DL-06 « Modalités et période de passation des commandes (marché à commandes) », champ nouveau des travaux (rubrique B09-DL,
--    délai d'exécution des travaux), facultatif, imprimé par aucun document : l'équivalent de B09-DX-03 des fournitures, dont la
--    rubrique B09-DX n'est pas ouverte aux travaux. Le contrat en vigueur le lit à défaut de B09-DX-03.
-- Le délai de garantie des travaux reste B09-GT-01 (lu par la réception). B05-TP-02 et B05-TP-03 ne changent pas (D3).
--
-- Ce n'est PAS une migration : les fichiers de correspondance (fournitures, travaux) portent ces valeurs ; ce script aligne une
-- base où ils ont déjà été chargés. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-29-marche-a-commandes-travaux.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX' WHERE "CODE" = 'B02-AU-04';

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF")
SELECT 'B09-DL-06', 'B09-DL', (SELECT coalesce(max(c."RANG"), 0) + 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE_RUBRIQUE" = 'B09-DL'),
       'Modalités et période de passation des commandes (marché à commandes)', 'TEXTE_LONG', 'SAISIE', 'AUCUN',
       NULL, 'A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL,
       NULL, NULL, false, NULL, true
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche c WHERE c."CODE" = 'B09-DL-06');

SELECT "CODE", "ACTIF", "OBLIGATOIRE", "TYPES_MARCHE", "CATEGORIES"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B02-AU-04', 'B09-DL-06') ORDER BY 1;

COMMIT;
