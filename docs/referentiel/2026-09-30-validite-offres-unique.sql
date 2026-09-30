-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — un seul délai de validité des offres (demande front du 2026-09-29,
-- demande-backend-2026-09-29-lot-d4-travaux.md §B3, point ouvert tranché le 2026-09-30).
--
-- B04-VO-01 (délai de validité des offres, fournitures, trois formes) est ouvert aux travaux : DPAO-T et AE-CC le
-- citent. Ses doublons propres aux travaux sont retirés, valeurs conservées : B04-DV-01 (quantité fixe, à commande) et
-- B04-VT-01 (contrat-cadre).
--
-- ⚠️ À passer APRÈS le redémarrage du serveur : la migration V55 élargit la rubrique B04-VO aux travaux. Le script
-- s'arrête net si elle manque.
--
-- Ce n'est PAS une migration : les fichiers de correspondance portent ces valeurs ; ce script aligne une base où ils ont
-- déjà été chargés. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-30-validite-offres-unique.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.tr_rubrique_fiche_marche WHERE "CODE" = 'B04-VO' AND "CATEGORIES" LIKE '%TRAVAUX%') THEN
        RAISE EXCEPTION 'Rubrique B04-VO non ouverte aux travaux : redémarrer le serveur (migration V55) avant ce script.';
    END IF;
END $$;

UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX' WHERE "CODE" = 'B04-VO-01';
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" IN ('B04-DV-01', 'B04-VT-01');

-- Contrôles (information) : les valeurs saisies sur les deux champs retirés, conservées par la fiche (attendu : 0).
SELECT "CODE_CHAMP", count(*) AS "VALEURS_CONSERVEES" FROM public.t_fiche_marche_valeur
 WHERE "CODE_CHAMP" IN ('B04-DV-01', 'B04-VT-01') GROUP BY 1 ORDER BY 1;

SELECT "CODE", "ACTIF", "OBLIGATOIRE", coalesce("CATEGORIES", 'FOURNITURES_SERVICES') AS "CATEGORIES", "TYPES_MARCHE"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B04-VO-01', 'B04-DV-01', 'B04-VT-01')
 ORDER BY 1;

COMMIT;
