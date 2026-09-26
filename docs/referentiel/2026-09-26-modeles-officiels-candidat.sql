-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — formulaires du candidat sur les modèles officiels (arbitrages du pilote du 2026-09-26,
-- demande-backend-2026-09-25-formulaires-du-candidat.md §B8, R1, R4, R5/R6).
--
-- Ce n'est PAS une migration : les champs se chargent par l'import du fichier de correspondance des fournitures, corrigé
-- à l'identique. Ce script aligne une base où il a déjà été chargé. Le schéma (colonne VALEUR_DEFAUT, rubrique B03-CQ
-- ouverte aux trois catégories) est dans la migration V47 : à lancer APRÈS elle.
--
--   R1   B02-OB-03  numéro du dossier d'appel d'offres  → obligatoire, et trois catégories (les fiches A1 à A4 et les
--                   garanties C1/C2 le portent pour tout appel d'offres)
--   R4   B03-CQ-01  pièces d'identification exigées     → trois catégories
--   R5/6 B03-CQ-09  durée des antécédents juridiques    → créé, défaut 5 (années)
--        B03-CQ-10  durée des antécédents financiers    → créé, défaut 3 (années)
--
-- Les fiches existantes ne reçoivent pas les défauts (ils se recopient à la CRÉATION d'une fiche) : B03-CQ-09 et -10 y
-- sont exigés au bilan et se saisissent. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-26-modeles-officiels-candidat.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = true,
       "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES'
 WHERE "CODE" = 'B02-OB-03';
UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES'
 WHERE "CODE" = 'B03-CQ-01';

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "TYPES_MARCHE", "CATEGORIES", "OBLIGATOIRE", "ACTIF", "VALEUR_DEFAUT") VALUES
    ('B03-CQ-09', 'B03-CQ', 9, 'Durée des antécédents juridiques (années)', 'NOMBRE', 'SAISIE', 'DPAO',
        'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', true, true, '5'),
    ('B03-CQ-10', 'B03-CQ', 10, 'Durée des antécédents financiers (années)', 'NOMBRE', 'SAISIE', 'DPAO',
        'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', true, true, '3')
ON CONFLICT ("CODE") DO UPDATE SET "VALEUR_DEFAUT" = EXCLUDED."VALEUR_DEFAUT", "CATEGORIES" = EXCLUDED."CATEGORIES";

SELECT "CODE", "OBLIGATOIRE", "CATEGORIES", coalesce("VALEUR_DEFAUT", '—') AS "VALEUR_DEFAUT"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B02-OB-03', 'B03-CQ-01', 'B03-CQ-09', 'B03-CQ-10') ORDER BY 1;

COMMIT;
