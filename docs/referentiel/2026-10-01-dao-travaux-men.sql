-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO des travaux — ce que le premier DAO réel (MEN) a montré (demande front
-- demande-backend-2026-10-01-dao-travaux-men.md, §B2 ; arbitrages du pilote du 2026-10-01).
--
-- Aligne DBPRS20 sur les fichiers de correspondance (referentiel-champs-fiche-dao-travaux.csv,
-- referentiel-champs-fiche-marche-fournitures.csv) :
--   B2.1 — B03-QT-12 (période de référence, NOMBRE, obligatoire, défaut 5), B03-QT-13 (personnel clé, TEXTE_LONG),
--          B03-QT-14 (liquidité minimale, MONTANT, par lot) ;
--   B2.2 — B03-CQ-01 : valeur par défaut = les six pièces du document type, une par ligne ;
--          B05-GQ-03, B03-QT-08, B09-DL-01 : par lot ;
--          B05-GQ-02, B05-GE-03 : LISTE → LISTE_MULTIPLE (les valeurs saisies, toutes à un seul élément, restent valides) ;
--          B05-GE-04 : option « Libérée à 100 % à la réception provisoire » ;
--          B02-AU-07, B06-EO-07 : actifs, servis aux travaux seulement (retirés des fournitures le 29/09).
--
-- ⚠️ À passer APRÈS le redémarrage du backend qui applique V58 (rubriques B02-AU et B06-EO ouvertes aux travaux,
-- colonne VALEUR_DEFAUT élargie à 1000 caractères) : le script s'arrête sinon. Ce n'est pas une migration. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-01-dao-travaux-men.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

DO $$
BEGIN
    IF (SELECT character_maximum_length FROM information_schema.columns
         WHERE table_name = 'tr_champ_fiche_marche' AND column_name = 'VALEUR_DEFAUT') < 1000 THEN
        RAISE EXCEPTION 'V58 non appliquée (VALEUR_DEFAUT trop courte) : redémarrer le backend avant ce script.';
    END IF;
END $$;

-- B2.1 — trois champs créés.
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF") VALUES
    ('B03-QT-12', 'B03-QT', 12, 'Période de référence des marchés similaires (années)', 'NOMBRE', 'SAISIE', 'DPAO', NULL,
     'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, true, NULL, NULL, NULL, NULL, NULL, false, '5', true),
    ('B03-QT-13', 'B03-QT', 13, 'Personnel clé exigé (fonctions, diplômes, années d''expérience, justificatifs)', 'TEXTE_LONG',
     'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B03-QT-14', 'B03-QT', 14, 'Liquidité ou ligne de crédit bancaire minimale (Ariary)', 'MONTANT', 'SAISIE', 'DPAO', NULL,
     'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, 'MONTANT_POSITIF', NULL, NULL, NULL, true, NULL, true)
ON CONFLICT ("CODE") DO UPDATE SET "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE",
    "CONTROLE" = EXCLUDED."CONTROLE", "PAR_LOT" = EXCLUDED."PAR_LOT", "VALEUR_DEFAUT" = EXCLUDED."VALEUR_DEFAUT",
    "CATEGORIES" = EXCLUDED."CATEGORIES", "ACTIF" = true;

-- B2.2 — les six pièces du document type, une par ligne, proposées à la création d'une fiche.
UPDATE public.tr_champ_fiche_marche SET "VALEUR_DEFAUT" =
       E'une photocopie certifiée de la Carte Professionnelle de l''année en cours\n'
    || E'une photocopie certifiée de l''Etat 211 bis datée de moins de TROIS (03) mois\n'
    || E'une photocopie certifiée de l''Extrait du Registre de Commerce\n'
    || E'un certificat de non faillite datée de moins de 2 mois\n'
    || E'une photocopie certifiée du Numéro d''Identification Fiscale (NIF)\n'
    || 'une photocopie certifiée de la carte statistique'
 WHERE "CODE" = 'B03-CQ-01';

UPDATE public.tr_champ_fiche_marche SET "PAR_LOT" = true WHERE "CODE" IN ('B05-GQ-03', 'B03-QT-08', 'B09-DL-01');

UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'LISTE_MULTIPLE' WHERE "CODE" IN ('B05-GQ-02', 'B05-GE-03');

UPDATE public.tr_champ_fiche_marche SET "OPTIONS" = "OPTIONS" || ',Libérée à 100 % à la réception provisoire'
 WHERE "CODE" = 'B05-GE-04' AND "OPTIONS" NOT LIKE '%réception provisoire%';

UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'TRAVAUX', "ACTIF" = true WHERE "CODE" IN ('B02-AU-07', 'B06-EO-07');

-- Information.
SELECT "CODE", "TYPE", "PAR_LOT", "ACTIF", "CATEGORIES", left(replace(coalesce("VALEUR_DEFAUT", ''), E'\n', ' / '), 60) AS "DEFAUT"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B03-QT-12', 'B03-QT-13', 'B03-QT-14', 'B03-CQ-01', 'B05-GQ-02', 'B05-GQ-03', 'B05-GE-03', 'B05-GE-04',
                  'B03-QT-08', 'B09-DL-01', 'B02-AU-07', 'B06-EO-07')
 ORDER BY "CODE";
-- Les valeurs déjà saisies sous le code nu d'un champ désormais par lot : lisibles hors allotissement, à ressaisir par lot
-- sur une ligne allotie.
SELECT "CODE_CHAMP", count(*) AS "VALEURS_SANS_LOT" FROM public.t_fiche_marche_valeur
 WHERE "CODE_CHAMP" IN ('B05-GQ-03', 'B03-QT-08', 'B09-DL-01') GROUP BY 1 ORDER BY 1;

COMMIT;
