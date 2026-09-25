-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — la fiche « à commande » au niveau d'un dossier réel (demande front du 2026-09-25,
-- demande-backend-2026-09-25-dao-a-commande-par-lot.md, §B1, §B3, §B4).
--
-- Ce n'est PAS une migration : les champs se chargent par l'import des fichiers de correspondance, corrigés à
-- l'identique (fournitures, travaux, prestations intellectuelles). Ce script aligne une base où ils ont déjà été
-- chargés, sans réimporter le reste. Idempotent.
--
-- Ce qui relève du schéma est dans la migration V43 (appliquée au démarrage) : la colonne PAR_LOT et ses quatre champs
-- (§B2), le LOT des documents générés, les rubriques B04-CD et B04-VE ouvertes aux fournitures et services.
--
-- Usage : psql -U postgres -d DBPRS20 -f docs/referentiel/2026-09-25-dao-a-commande-par-lot.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

-- §B1 — la durée de validité du marché à commande est reprise dans l'acte d'engagement (article 5.3 du dossier réel).
UPDATE public.tr_champ_fiche_marche SET "REPRISES" = 'AE' WHERE "CODE" = 'B02-AU-04';

-- §B3 — réemploi par les fournitures et services.
UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'TRAVAUX,FOURNITURES_SERVICES'
 WHERE "CODE" IN ('B04-CD-01', 'B04-CD-02');
-- Libellé neutralisé ; maître DPAO (substitué en DPIC pour les prestations intellectuelles : une fiche de fournitures ne
-- produit pas de DPIC).
UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'PRESTATIONS_INTELLECTUELLES,FOURNITURES_SERVICES',
       "DOCUMENT_MAITRE" = 'DPAO', "LIBELLE" = 'Remise des offres ou propositions par voie électronique admise'
 WHERE "CODE" = 'B04-VE-01';
UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'PRESTATIONS_INTELLECTUELLES,FOURNITURES_SERVICES',
       "DOCUMENT_MAITRE" = 'DPAO',
       "LIBELLE" = 'Conditions et modalités de transmission électronique des offres ou propositions'
 WHERE "CODE" = 'B04-VE-02';

-- §B3 — créations (B02-AU-07 : le code B02-AU-06 proposé est déjà pris par « Rythme de commande », inactif).
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "ACTIF") VALUES
    ('B02-AU-07', 'B02-AU', 7, 'Nombre maximum de lots attribuables à un même candidat', 'NOMBRE', 'SAISIE', 'DPAO',
        NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', 'alloti = OUI', false, true),
    ('B02-OB-03', 'B02-OB', 3, 'Numéro du dossier d''appel d''offres', 'TEXTE', 'SAISIE', 'DPAO',
        'AE', 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', NULL, false, true),
    ('B03-NA-03', 'B03-NA', 3, 'Comptable assignataire des paiements', 'TEXTE', 'SAISIE', 'AE',
        NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', NULL, false, true),
    ('B04-RO-03', 'B04-RO', 3, 'Présentation des plis (plis séparés par lot, enveloppes intérieures originale et copie)',
        'TEXTE_LONG', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', NULL, false, true),
    ('B09-PC-02', 'B09-PC', 2, 'Annexes de l''acte d''engagement (cadre du bordereau de prix, état des sommes versées à '
        || 'des tiers, déclaration des bénéficiaires effectifs)', 'TEXTE_LONG', 'SAISIE', 'AE',
        NULL, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES', NULL, false, true)
ON CONFLICT ("CODE") DO NOTHING;

-- §B4 — le délai de livraison unique ne se fixe au dossier que pour la quantité fixe ; à commande, seul le plafond
-- (B06-EO-12) — le délai de chaque commande est fixé dans le bon de commande.
UPDATE public.tr_champ_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE' WHERE "CODE" = 'B06-EO-11';

SELECT "CODE", "DOCUMENT_MAITRE", coalesce("REPRISES", '—') AS "REPRISES", "TYPES_MARCHE", "CATEGORIES"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B02-AU-04', 'B04-CD-01', 'B04-CD-02', 'B04-VE-01', 'B04-VE-02', 'B02-AU-07', 'B02-OB-03',
                  'B03-NA-03', 'B04-RO-03', 'B09-PC-02', 'B06-EO-11')
 ORDER BY 1;

COMMIT;
