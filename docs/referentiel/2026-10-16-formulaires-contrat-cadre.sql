-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — les formulaires de soumission du contrat-cadre (demande front du 2026-10-16,
-- demande-backend-2026-10-16-charte-dao.md, recette du front, R4 : « le sommaire n'a pas de section 1.3 Formulaires de
-- soumission »).
--
-- B04-CD-01 (modèles de fiches de renseignements A1 à A4) et B04-CD-02 (modèle de garantie de soumission C1, C2) ne valaient que
-- pour la quantité fixe et le marché à commandes : un contrat-cadre ne produisait donc jamais ses formulaires, que le plan de
-- l'ARMP place en 1.3 et que le DAO de démonstration de la fiche 2 porte. Ils valent désormais aussi pour le contrat-cadre ; le
-- document maître (DPAO) y devient le DPAC, comme pour les autres champs partagés.
--
-- Ce n'est PAS une migration : le fichier de correspondance des travaux (référentiel commun aux trois catégories) porte ces
-- lignes ; ce script aligne une base où il a déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-16-formulaires-contrat-cadre.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'
 WHERE "CODE" IN ('B04-CD-01', 'B04-CD-02') AND "TYPES_MARCHE" NOT LIKE '%CONTRAT_CADRE%';

SELECT "CODE", "TYPES_MARCHE", "CATEGORIES", "OBLIGATOIRE", "DOCUMENT_MAITRE"
  FROM public.tr_champ_fiche_marche WHERE "CODE" IN ('B04-CD-01', 'B04-CD-02') ORDER BY "CODE";

COMMIT;
