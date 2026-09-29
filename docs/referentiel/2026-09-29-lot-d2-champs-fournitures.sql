-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — lot D2 (demande front du 2026-09-29, demande-backend-2026-09-29-lot-d2-fournitures.md
-- §B2) : sept champs des fournitures (quantité fixe et à commande) que les documents types officiels demandent et que la
-- fiche n'avait pas. Saisie, facultatifs. Les options de B02-VA-01 sont celles que les conditions du DPAO citent.
--
-- Ce n'est PAS une migration : le fichier de correspondance des fournitures les porte ; ce script aligne une base où il a
-- déjà été chargé. La rubrique B02-VA vient de la migration V52 (appliquée au démarrage du serveur) : lancer ce script
-- APRÈS le redémarrage. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-29-lot-d2-champs-fournitures.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "OPTIONS", "PAR_LOT", "ACTIF") VALUES
    ('B02-VA-01', 'B02-VA', 1, 'Offres variantes prises en considération', 'LISTE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', 'variantes = OUI', false, 'Offre de base évaluée la moins-disante,Toutes les offres conformes aux spécifications', false, true),
    ('B05-CP-04', 'B05-CP', 4, 'Transport intérieur jusqu''à la destination finale à la charge du fournisseur (fournitures importées)', 'OUI_NON', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', 'provenance = IMPORTEES', false, NULL, false, true),
    ('B05-CP-05', 'B05-CP', 5, 'Lieu (CIP) ou port (CIF) de destination des fournitures importées', 'TEXTE', 'SAISIE', 'DPAO', 'CCAP', 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', 'provenance = IMPORTEES', false, NULL, false, true),
    ('B05-VP-03', 'B05-VP', 3, 'Indices d''actualisation des prix fermes (nature et sources)', 'TEXTE_LONG', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', 'prixRevisable = NON', false, NULL, false, true),
    ('B09-DG-03', 'B09-DG', 3, 'Pénalité pour non-respect des garanties contractuelles (% du prix initial)', 'POURCENTAGE', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', NULL, false, NULL, false, true),
    ('B09-DG-04', 'B09-DG', 4, 'Délai accordé pour remédier aux défauts pendant la garantie', 'TEXTE', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', NULL, false, NULL, false, true),
    ('B10-IR-03', 'B10-IR', 3, 'Taux de l''indemnité de résiliation (%, si différent des 4 % du CCAG)', 'POURCENTAGE', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', NULL, false, NULL, false, true)
ON CONFLICT ("CODE") DO UPDATE SET "CODE_RUBRIQUE" = EXCLUDED."CODE_RUBRIQUE", "RANG" = EXCLUDED."RANG", "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "SOURCE" = EXCLUDED."SOURCE", "DOCUMENT_MAITRE" = EXCLUDED."DOCUMENT_MAITRE", "REPRISES" = EXCLUDED."REPRISES", "TYPES_MARCHE" = EXCLUDED."TYPES_MARCHE", "CATEGORIES" = EXCLUDED."CATEGORIES", "CONDITION" = EXCLUDED."CONDITION", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE", "OPTIONS" = EXCLUDED."OPTIONS", "PAR_LOT" = EXCLUDED."PAR_LOT", "ACTIF" = EXCLUDED."ACTIF";

SELECT "CODE", "TYPE", "DOCUMENT_MAITRE", coalesce("CONDITION", '-') AS "CONDITION", coalesce("OPTIONS", '-') AS "OPTIONS", "ACTIF"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B02-VA-01', 'B05-CP-04', 'B05-CP-05', 'B05-VP-03', 'B09-DG-03', 'B09-DG-04', 'B10-IR-03') ORDER BY 1;

COMMIT;
