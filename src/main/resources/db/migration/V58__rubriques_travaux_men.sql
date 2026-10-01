-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V58 — DAO de travaux : ce que le premier DAO réel (MEN) a montré (demande front du 2026-10-01,
-- demande-backend-2026-10-01-dao-travaux-men.md, §B2.2).
--
-- B02-AU-07 « Nombre maximum de lots attribuables à un même candidat » (DPAO 1.1 du MEN) et B06-EO-07 « Traitement des
-- offres anormalement hautes ou basses » (DPAO 9.4.5) sont ouverts à la catégorie TRAVAUX. Une rubrique n'est servie qu'à
-- ses catégories (V54) : B02-AU et B06-EO, réservées jusqu'ici aux fournitures, le sont aussi aux travaux. Les champs
-- eux-mêmes sont ouverts par le fichier de correspondance des travaux et docs/referentiel/2026-10-01-dao-travaux-men.sql ;
-- une rubrique n'est servie que si l'un de ses champs actifs l'est.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_rubrique_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX'
 WHERE "CODE" IN ('B02-AU', 'B06-EO') AND "CATEGORIES" = 'FOURNITURES_SERVICES';

-- §B2.2 — la valeur par défaut de B03-CQ-01 est la liste des pièces administratives du document type, une pièce par
-- ligne (environ 380 caractères) : la colonne passe de 200 à 1000 caractères.
ALTER TABLE public.tr_champ_fiche_marche ALTER COLUMN "VALEUR_DEFAUT" TYPE character varying(1000);
