-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO des travaux — recette du DAO du MEN rejoué en fiche 40 (demande front
-- demande-backend-2026-10-02-recette-dao-men.md, §B2 et §B3.2).
--
-- Aligne DBPRS20 sur les fichiers de correspondance (referentiel-champs-fiche-dao-travaux.csv,
-- referentiel-champs-fiche-marche-fournitures.csv) :
--   B2.1 — facultatifs : B03-QT-07 (chiffre d'affaires), B03-CQ-10 (antécédents financiers), B09-DL-04 (date de
--          réception) ; B10-PC-01 (procédure contentieuse) proposé par défaut — rédaction à faire valider par le juriste ;
--   B2.2 — libellés : B09-MA-01 à -04, B09-MD-01, B03-NT-01, B02-MW-04 ;
--   B3.2 — six champs créés, facultatifs : B05-GQ-04 (bénéficiaire des chèques), B05-VR-02 (indices d'actualisation),
--          B08-RE-04 (plafond de la régie), B08-MR-05 (délai du projet de décompte), B08-MR-06 (découpage du forfait),
--          B09-PE-03 (plafond des pénalités).
-- Ce n'est pas une migration. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-02-recette-dao-men.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

-- B2.1
UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false WHERE "CODE" IN ('B03-QT-07', 'B03-CQ-10', 'B09-DL-04');
UPDATE public.tr_champ_fiche_marche SET "VALEUR_DEFAUT" = 'Les différends nés de l''exécution du marché sont réglés selon la '
    || 'procédure prévue à l''article 50 du Cahier des Clauses Administratives Générales applicable aux marchés de travaux.'
 WHERE "CODE" = 'B10-PC-01';

-- B2.2
UPDATE public.tr_champ_fiche_marche SET "LIBELLE" = "LIBELLE" || ' : pourcentage de la masse initiale (ex. « vingt pour cent (20 %) »)'
 WHERE "CODE" IN ('B09-MA-01', 'B09-MA-02', 'B09-MA-03', 'B09-MA-04') AND "LIBELLE" NOT LIKE '%pourcentage de la masse initiale%';
UPDATE public.tr_champ_fiche_marche SET "LIBELLE" = 'Durée cumulée maximale de prolongation ou de report sans avenant (ex. « soixante (60) jours »)'
 WHERE "CODE" = 'B09-MD-01';
UPDATE public.tr_champ_fiche_marche SET "LIBELLE" = 'Comptable assignataire des paiements (désignation seule)' WHERE "CODE" = 'B03-NT-01';
UPDATE public.tr_champ_fiche_marche SET "LIBELLE" = 'Maître d''ouvrage délégué : nom et coordonnées (laisser vide s''il n''y en a pas)'
 WHERE "CODE" = 'B02-MW-04';

-- B3.2
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF") VALUES
    ('B05-GQ-04', 'B05-GQ', 4, 'Bénéficiaire des chèques de banque (garanties de soumission, de bonne exécution, de restitution d''avance)',
     'TEXTE', 'SAISIE', 'DPAO', 'CCAP', 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B05-VR-02', 'B05-VR', 2, 'Indices d''actualisation des prix fermes et sources (laisser vide sans actualisation)',
     'TEXTE_LONG', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B08-RE-04', 'B08-RE', 4, 'Plafond des travaux en régie (% du montant du marché)',
     'POURCENTAGE', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B08-MR-05', 'B08-MR', 5, 'Délai de remise du projet de décompte mensuel (jours ouvrables)',
     'NOMBRE', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B08-MR-06', 'B08-MR', 6, 'Découpage du prix forfaitaire par poste ou corps d''état (un poste par ligne, avec son pourcentage)',
     'TEXTE_LONG', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B09-PE-03', 'B09-PE', 3, 'Plafond des pénalités de retard (% du montant global du marché ou de la tranche)',
     'POURCENTAGE', 'SAISIE', 'CCAP', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true)
ON CONFLICT ("CODE") DO UPDATE SET "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "DOCUMENT_MAITRE" = EXCLUDED."DOCUMENT_MAITRE",
    "REPRISES" = EXCLUDED."REPRISES", "OBLIGATOIRE" = false, "CATEGORIES" = EXCLUDED."CATEGORIES", "ACTIF" = true;

-- Information.
SELECT "CODE", "TYPE", "OBLIGATOIRE", "ACTIF", left("LIBELLE", 70) AS "LIBELLE", left(coalesce("VALEUR_DEFAUT", ''), 40) AS "DEFAUT"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B03-QT-07', 'B03-CQ-10', 'B09-DL-04', 'B10-PC-01', 'B09-MA-03', 'B09-MD-01', 'B03-NT-01', 'B02-MW-04',
                  'B05-GQ-04', 'B05-VR-02', 'B08-RE-04', 'B08-MR-05', 'B08-MR-06', 'B09-PE-03')
 ORDER BY "CODE";

COMMIT;
