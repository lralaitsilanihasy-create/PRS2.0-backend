-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V54 — Lot D4, travaux (demande front du 2026-09-29, demande-backend-2026-09-29-lot-d4-travaux.md).
--
-- 1. §B2.2.3 — la question de cadrage « pénalités » (reflet B09-PR-01, clé penalites) est posée aussi aux travaux :
--    le CCAP-T choisit sa rédaction des pénalités sur elle, comme les fournitures et les prestations intellectuelles
--    (V53). Le champ propre B09-PE-01 est retiré par le fichier de correspondance des travaux.
--    ⚠️ La rubrique B09-PR elle-même n'était servie qu'aux fournitures : depuis V53 le reflet était servi aux
--    prestations intellectuelles SANS sa rubrique (le référentiel filtre les rubriques par catégorie). Elle est
--    élargie aux trois catégories.
-- 2. §B2.1 — la rubrique B09-BT « Travaux de bâtiment » (CCAP-T : contrôle des prix de revient, tous corps d'état,
--    garantie décennale) porte le nouveau champ B09-BT-01, semé par le fichier de correspondance.
-- 3. §B3 — harmonisation du contrat-cadre : le contrat-cadre de travaux emploie désormais les codes du contrat-cadre
--    des fournitures (DPAC-CC et AE-CC sont un seul document type). Les champs ouverts aux travaux par le fichier de
--    correspondance ont leur rubrique élargie de même — sans quoi le champ serait servi hors de toute rubrique.
--    Une rubrique élargie n'apparaît pour une forme et une catégorie que si l'un de ses champs actifs y est servi.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_champ_fiche_marche
   SET "CATEGORIES" = 'FOURNITURES_SERVICES,PRESTATIONS_INTELLECTUELLES,TRAVAUX'
 WHERE "CODE" = 'B09-PR-01' AND "CATEGORIES" = 'FOURNITURES_SERVICES,PRESTATIONS_INTELLECTUELLES';

UPDATE public.tr_rubrique_fiche_marche
   SET "CATEGORIES" = 'FOURNITURES_SERVICES,PRESTATIONS_INTELLECTUELLES,TRAVAUX'
 WHERE "CODE" = 'B09-PR' AND "CATEGORIES" = 'FOURNITURES_SERVICES';

INSERT INTO public.tr_rubrique_fiche_marche
    ("CODE", "CODE_BLOC", "CODE_COURT", "LIBELLE", "RANG", "DOCUMENT_MAITRE", "NB_ATTENDU", "TYPES_MARCHE", "CATEGORIES")
SELECT 'B09-BT', 'B09', 'BT', 'Travaux de bâtiment', 133, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX'
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_rubrique_fiche_marche WHERE "CODE" = 'B09-BT');

UPDATE public.tr_rubrique_fiche_marche
   SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX'
 WHERE "CATEGORIES" = 'FOURNITURES_SERVICES'
   AND "CODE" IN ('B02-AL', 'B02-DC', 'B02-OE', 'B02-PC', 'B02-SG',
                  'B04-CP', 'B04-DS', 'B04-PO', 'B04-RC', 'B04-RQ',
                  'B05-MT', 'B05-PM', 'B05-UM',
                  'B06-AN', 'B06-CA', 'B06-SC', 'B06-SO',
                  'B07-DE', 'B07-DU', 'B07-FS', 'B07-MA', 'B07-PE', 'B07-PS', 'B07-TN',
                  'B08-FI', 'B08-FP',
                  'B09-AU', 'B09-EA', 'B09-GP', 'B09-VA',
                  'B10-MT', 'B10-RS', 'B10-VR');
