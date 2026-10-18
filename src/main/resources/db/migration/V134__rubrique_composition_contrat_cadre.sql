-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V134 — La rubrique « Composition du dossier » (B04-CD) s'ouvre au contrat-cadre (demande front du 2026-10-18
-- `demande-backend-2026-10-18-grille-contrat-cadre.md`, recette du front après V133, §B3).
--
-- Le 16/10 (docs/referentiel/2026-10-16-formulaires-contrat-cadre.sql), B04-CD-01 (modèles de fiches de renseignements A1 à A4,
-- obligatoire) et B04-CD-02 (modèle de garantie de soumission) ont été ouverts au contrat-cadre, mais pas leur rubrique (V41 :
-- quantités fixes et à commandes). Le référentiel servi au front n'avait donc pas la rubrique : aucun écran ne proposait ces
-- champs pour un contrat-cadre, alors que la fiche les évalue (B04-CD-01 obligatoire) et que la grille d'examen les cite (V133).
-- C'était le seul champ actif ouvert au-delà de sa rubrique ; ChampFicheMarcheService le refuse désormais (400).
--
-- B04-CD-03 (travaux, quantités fixes et à commandes) reste fermé au contrat-cadre : seule la rubrique change. Les fiches ne sont
-- pas touchées. Idempotente ; sans effet sur une base sans la rubrique.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_rubrique_fiche_marche SET "TYPES_MARCHE" = "TYPES_MARCHE" || ',CONTRAT_CADRE'
 WHERE "CODE" = 'B04-CD' AND ',' || "TYPES_MARCHE" || ',' NOT LIKE '%,CONTRAT_CADRE,%';
