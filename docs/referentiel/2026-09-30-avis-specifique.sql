-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — avis spécifique d'appel d'offres (demande front du 2026-09-30,
-- demande-backend-2026-09-30-avis-specifique.md §B5).
--
-- Ce que l'avis imprime et que les marchés ordinaires n'avaient pas :
-- - B04-DS-05 (montant du dossier) et B04-DS-07 à -10 (adresse de consultation), réservés au contrat-cadre, sont servis
--   aussi à la quantité fixe et au marché à commande, fournitures et travaux ; ils restent facultatifs ;
-- - B04-DS-11 « Adresse de consultation du dossier : e-mail » est créé : TEXTE, facultatif, les six formes ;
-- - B05-GS-03 (garantie de soumission, fournitures) est servi aussi au contrat-cadre des fournitures, et B05-GQ-03 (idem,
--   travaux) au contrat-cadre de travaux, sous la même condition garantieSoumission = OUI.
--
-- ⚠️ À passer APRÈS le redémarrage du serveur : la migration V56 élargit les rubriques B04-DS et B05-GQ (sans elle, les
-- champs seraient servis hors rubrique) et prépare les documents de type AVIS. Le script s'arrête net si V56 manque.
--
-- Ce n'est PAS une migration : les fichiers de correspondance portent ces valeurs. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-30-avis-specifique.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 't_document_fiche_marche' AND column_name = 'PUBLICATION') THEN
        RAISE EXCEPTION 'Migration V56 absente : redémarrer le serveur avant ce script.';
    END IF;
END $$;

UPDATE public.tr_champ_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'
 WHERE "CODE" IN ('B04-DS-05', 'B04-DS-07', 'B04-DS-08', 'B04-DS-09', 'B04-DS-10', 'B05-GS-03', 'B05-GQ-03');

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "CONTROLE", "OPTIONS", "PAR_LOT", "ACTIF") VALUES
    ('B04-DS-11', 'B04-DS', 11, 'Adresse de consultation du dossier : e-mail', 'TEXTE', 'SAISIE', 'DPAC',
     'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX', NULL, false, NULL, NULL, false, true)
ON CONFLICT ("CODE") DO UPDATE SET "CODE_RUBRIQUE" = EXCLUDED."CODE_RUBRIQUE", "RANG" = EXCLUDED."RANG", "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "SOURCE" = EXCLUDED."SOURCE", "DOCUMENT_MAITRE" = EXCLUDED."DOCUMENT_MAITRE", "TYPES_MARCHE" = EXCLUDED."TYPES_MARCHE", "CATEGORIES" = EXCLUDED."CATEGORIES", "CONDITION" = EXCLUDED."CONDITION", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE", "CONTROLE" = EXCLUDED."CONTROLE", "OPTIONS" = EXCLUDED."OPTIONS", "PAR_LOT" = EXCLUDED."PAR_LOT", "ACTIF" = EXCLUDED."ACTIF";

SELECT "CODE", "TYPES_MARCHE", coalesce("CATEGORIES", 'FOURNITURES_SERVICES') AS "CATEGORIES", "OBLIGATOIRE",
       coalesce("CONDITION", '-') AS "CONDITION", "ACTIF"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B04-DS-05', 'B04-DS-07', 'B04-DS-08', 'B04-DS-09', 'B04-DS-10', 'B04-DS-11', 'B05-GS-03', 'B05-GQ-03')
 ORDER BY 1;

SELECT "CODE", "TYPES_MARCHE", "CATEGORIES" FROM public.tr_rubrique_fiche_marche
 WHERE "CODE" IN ('B04-DS', 'B05-GS', 'B05-GQ') ORDER BY 1;

COMMIT;
