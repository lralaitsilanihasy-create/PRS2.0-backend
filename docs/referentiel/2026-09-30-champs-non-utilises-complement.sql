-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — complément du 2026-09-30 à 2026-09-30-champs-non-utilises.sql (même règle, arbitrée
-- par le pilote : un champ qu'aucun document n'utilise est retiré, valeurs conservées ; un champ qui n'a pas de place
-- aujourd'hui mais pourrait en avoir une reste, facultatif).
--
-- Travaux, quantité fixe et à commande — retirés :
--   B03-QT-01 à -04  la clause 6.3 du DPAO-T écrit ces quatre rubriques (situation juridique, capacité technique,
--                    capacité financière, marchés similaires) en texte fixe ; le candidat les remplit dans A1 à A4 ;
--   B09-DL-02        doublon de B09-PT-02 (durée de la période de préparation, CCAP-T art. 26.1) ;
--   B09-DL-03        l'AE-T écrit en dur « donné en annexe au CCAP ».
-- Restent, facultatifs :
--   B04-CD-03        plans joints au dossier : le CCAP-T prévoit une annexe « Liste de plans », sans jeton ;
--   B03-CQ-01        pièces exigées pour la situation juridique (trois catégories), cité nulle part.
--
-- Ce n'est PAS une migration : les fichiers de correspondance portent ces valeurs. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-30-champs-non-utilises-complement.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false
 WHERE "CODE" IN ('B03-QT-01', 'B03-QT-02', 'B03-QT-03', 'B03-QT-04', 'B09-DL-02', 'B09-DL-03');

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false WHERE "CODE" IN ('B04-CD-03', 'B03-CQ-01');

SELECT "CODE", "ACTIF", "OBLIGATOIRE",
       (SELECT count(*) FROM public.t_fiche_marche_valeur v WHERE split_part(v."CODE_CHAMP", '#', 1) = c."CODE") AS "VALEURS"
  FROM public.tr_champ_fiche_marche c
 WHERE "CODE" IN ('B03-QT-01', 'B03-QT-02', 'B03-QT-03', 'B03-QT-04', 'B09-DL-02', 'B09-DL-03', 'B04-CD-03', 'B03-CQ-01')
 ORDER BY 1;

COMMIT;
