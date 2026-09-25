-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — les formulaires du candidat (demande front du 2026-09-25,
-- demande-backend-2026-09-25-formulaires-du-candidat.md, §B2 et §B4).
--
-- Ce n'est PAS une migration : les champs se chargent par l'import des fichiers de correspondance, corrigés à
-- l'identique (fournitures ; travaux pour B04-CD). Ce script aligne une base où ils ont déjà été chargés. Le schéma
-- (bloc B12, tables du besoin, type LISTE_MULTIPLE, documents BP/TC/LF, paramètres) est dans la migration V45.
--
--   B04-CD-01  fiches A1 à A4 jointes   → LISTE_MULTIPLE (A1, A2, A3, A4), toutes catégories
--   B04-CD-02  modèle de garantie joint → LISTE (C1, C2, C1 et C2), toutes catégories, rôle GARANTIE_MANQUANTE:FORME
--   B09-LL-01  lieu de livraison        → par lot
--   B02-AU-03  quantités min et max     → désactivé : le besoin les porte (jamais supprimé, un code ne se réaffecte pas)
--   B05-GS-03 / B05-TP-03               → rôles GARANTIE_TAUX:GARANTIE / :MAXIMUM (MONTANT_POSITIF reste appliqué à tout
--                                          montant)
--
-- Les versions de fiche déjà validées gardent leurs valeurs et leurs documents : une valeur en texte libre de B04-CD-01
-- ou B04-CD-02, ou un lieu de livraison unique, se ressaisit à la révision suivante. Idempotent. À lancer APRÈS V45
-- (le type LISTE_MULTIPLE n'est admis qu'à partir d'elle).
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-25-formulaires-du-candidat.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'LISTE_MULTIPLE', "OPTIONS" = 'A1,A2,A3,A4',
       "CATEGORIES" = 'TRAVAUX,FOURNITURES_SERVICES,PRESTATIONS_INTELLECTUELLES'
 WHERE "CODE" = 'B04-CD-01';
UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'LISTE', "OPTIONS" = 'C1,C2,C1 et C2',
       "CATEGORIES" = 'TRAVAUX,FOURNITURES_SERVICES,PRESTATIONS_INTELLECTUELLES', "CONTROLE" = 'GARANTIE_MANQUANTE:FORME'
 WHERE "CODE" = 'B04-CD-02';
UPDATE public.tr_champ_fiche_marche SET "PAR_LOT" = true WHERE "CODE" = 'B09-LL-01';
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" = 'B02-AU-03';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'GARANTIE_TAUX:GARANTIE' WHERE "CODE" = 'B05-GS-03';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'GARANTIE_TAUX:MAXIMUM' WHERE "CODE" = 'B05-TP-03';

SELECT "CODE", "TYPE", coalesce("OPTIONS", '—') AS "OPTIONS", "CATEGORIES", coalesce("CONTROLE", '—') AS "CONTROLE",
       "PAR_LOT", "ACTIF"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B04-CD-01', 'B04-CD-02', 'B09-LL-01', 'B02-AU-03', 'B05-GS-03', 'B05-TP-03')
 ORDER BY 1;

COMMIT;
