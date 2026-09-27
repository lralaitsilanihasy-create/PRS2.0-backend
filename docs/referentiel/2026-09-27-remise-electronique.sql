-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — la remise électronique des offres (demande front du 2026-09-27,
-- demande-backend-2026-09-27-remise-electronique.md §B1.3 et §B1.5 ; ADR-0010).
--
-- Ce n'est PAS une migration : les champs se chargent par l'import des fichiers de correspondance (les cinq CSV du
-- front, colonnes reconnues par nom). Ce script aligne une base où ils ont déjà été chargés. Le schéma (types
-- DATE_HEURE / URL, rubrique B04-SE, tables des paramètres internes et du responsable, paramètres FICHE_SE_*) est dans
-- la migration V50 : à lancer APRÈS elle — la contrainte ck_champ_fiche_marche_type refuse DATE_HEURE avant V50.
--
--   1. Rubrique B04-SE « Remise électronique » (rappel de V50, idempotent) ; B04-VE-01 / B04-VE-02 désactivés (Q1).
--   2. Les 26 champs : B04-SE-01 à -17 (trois formes, trois catégories), B05-GS-10 à -14 et B04-OP-10 à -13 (rubriques
--      des fournitures : catégorie FOURNITURES_SERVICES — voir l'encadré ⚠️ du 27/09 dans la demande, §B1.3).
--      Créés ou remis à l'identique (ON CONFLICT … DO UPDATE sur tous les attributs).
--   3. Les contrôles ajoutés aux champs existants des fournitures : B04-LR-03 (SE_HEURE_LIMITE:DATE, en plus de
--      DATES_ORDRE:REMISE), B04-LR-04 (SE_HEURE_LIMITE:HEURE), B04-OP-02 (SE_OUVERTURE_PLIS:DATE), B04-OP-03
--      (SE_OUVERTURE_PLIS:HEURE), B04-LR-02 (SE_ORIGINAL_GARANTIE:LIEU_REMISE, entrée du calcul de B05-GS-12).
--   4. Les paramètres FICHE_SE_* (rappel de V50, idempotent ; FICHE_SE_DELAI_MIN_REMISE_JOURS = 30 proposé).
--
-- Les fiches existantes ne reçoivent pas les défauts (ils se recopient à la CRÉATION d'une fiche) : les champs
-- obligatoires de B04-SE y sont exigés au bilan, en mode électronique seulement. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-27-remise-electronique.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

-- 1. Rubrique et désactivation
INSERT INTO public.tr_rubrique_fiche_marche
    ("CODE", "CODE_BLOC", "CODE_COURT", "LIBELLE", "RANG", "DOCUMENT_MAITRE", "NB_ATTENDU", "TYPES_MARCHE", "CATEGORIES")
VALUES ('B04-SE', 'B04', 'SE', 'Remise électronique', 61, 'DPAO', 17,
        'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES')
ON CONFLICT ("CODE") DO NOTHING;

UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" IN ('B04-VE-01', 'B04-VE-02');

-- 2. Les champs
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS", "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF") VALUES
    ('B04-SE-01', 'B04-SE', 1, 'Mode de remise des offres', 'LISTE', 'CADRAGE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', NULL, true, NULL, NULL, 'PAPIER,ELECTRONIQUE', 'modeRemise', NULL, false, NULL, true),
    ('B04-SE-02', 'B04-SE', 2, 'Adresse de la plateforme de dépôt', 'URL', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, NULL, NULL, NULL, false, 'PARAM:FICHE_SE_PLATEFORME_URL', true),
    ('B04-SE-03', 'B04-SE', 3, 'Date d''ouverture des dépôts', 'DATE_HEURE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', false, NULL, 'SE_OUVERTURE_DEPOTS:DEPOTS', NULL, NULL, NULL, false, NULL, true),
    ('B04-SE-04', 'B04-SE', 4, 'Heure de référence', 'LISTE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, 'Heure du serveur (UTC+03:00 Indian/Antananarivo)', NULL, NULL, false, 'Heure du serveur (UTC+03:00 Indian/Antananarivo)', true),
    ('B04-SE-05', 'B04-SE', 5, 'Niveau de signature électronique exigé', 'LISTE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, 'SE_SIGNATURE_MIN:NIVEAU,SE_PRESTATAIRES:NIVEAU', 'Qualifiée,Avancée,Simple', NULL, NULL, false, 'PARAM:FICHE_SE_SIGNATURE_MIN', true),
    ('B04-SE-06', 'B04-SE', 6, 'Prestataires de certification acceptés', 'LISTE_MULTIPLE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', false, NULL, 'SE_PRESTATAIRES:PRESTATAIRES', 'À définir par l''Administrateur (liste officielle des prestataires de certification)', NULL, NULL, false, NULL, true),
    ('B04-SE-07', 'B04-SE', 7, 'Formats de fichiers acceptés', 'LISTE_MULTIPLE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, 'PDF,PDF/A,XLSX,DOCX,ZIP', NULL, NULL, false, 'PDF,PDF/A', true),
    ('B04-SE-08', 'B04-SE', 8, 'Taille maximale par fichier (Mo)', 'NOMBRE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, 'SE_TAILLES:FICHIER', NULL, NULL, NULL, false, '50', true),
    ('B04-SE-09', 'B04-SE', 9, 'Taille maximale par offre (Mo)', 'NOMBRE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, 'SE_TAILLES:OFFRE', NULL, NULL, NULL, false, '500', true),
    ('B04-SE-10', 'B04-SE', 10, 'Remplacement et retrait avant la date limite', 'OUI_NON', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, NULL, NULL, NULL, false, 'OUI', true),
    ('B04-SE-11', 'B04-SE', 11, 'Copie de sauvegarde autorisée', 'LISTE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, 'Non,Support physique sous pli scellé', NULL, NULL, false, 'Non', true),
    ('B04-SE-12', 'B04-SE', 12, 'Seuil d''indisponibilité déclenchant la prorogation (heures)', 'NOMBRE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, NULL, NULL, NULL, false, '2', true),
    ('B04-SE-13', 'B04-SE', 13, 'Durée de la prorogation (jours ouvrables)', 'NOMBRE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, NULL, NULL, NULL, false, '2', true),
    ('B04-SE-14', 'B04-SE', 14, 'Assistance aux candidats (contact, horaires)', 'TEXTE_LONG', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, NULL, NULL, NULL, false, 'PARAM:FICHE_SE_ASSISTANCE', true),
    ('B04-SE-15', 'B04-SE', 15, 'Date limite des demandes d''assistance', 'DATE_HEURE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B04-SE-16', 'B04-SE', 16, 'Fenêtre avant l''échéance où le seuil s''applique (heures)', 'NOMBRE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, NULL, NULL, NULL, false, '24', true),
    ('B04-SE-17', 'B04-SE', 17, 'Date de publication de l''avis', 'DATE_HEURE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES,TRAVAUX,PRESTATIONS_INTELLECTUELLES', 'modeRemise = ELECTRONIQUE', true, NULL, 'SE_OUVERTURE_DEPOTS:PUBLICATION,SE_CEREMONIE:PUBLICATION', NULL, NULL, NULL, false, NULL, true),
    ('B05-GS-10', 'B05-GS', 10, 'Forme de remise de la garantie acceptée', 'LISTE_MULTIPLE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE et garantieSoumission = OUI', true, NULL, NULL, 'Dépôt direct par le garant (voie A),Téléversement avec code de vérification (voie B),Original papier', NULL, NULL, false, 'Téléversement avec code de vérification (voie B)', true),
    ('B05-GS-11', 'B05-GS', 11, 'Original papier exigé en plus', 'OUI_NON', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE et garantieSoumission = OUI', true, NULL, 'SE_ORIGINAL_GARANTIE:EXIGE', NULL, NULL, NULL, false, 'NON', true),
    ('B05-GS-12', 'B05-GS', 12, 'Lieu du dépôt de l''original', 'TEXTE_LONG', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE et garantieSoumission = OUI', false, NULL, 'SE_ORIGINAL_GARANTIE:LIEU', NULL, NULL, NULL, false, NULL, true),
    ('B05-GS-13', 'B05-GS', 13, 'Garants habilités', 'LISTE_MULTIPLE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE et garantieSoumission = OUI', false, NULL, NULL, 'À définir par l''Administrateur (liste officielle des garants habilités)', NULL, NULL, false, NULL, true),
    ('B05-GS-14', 'B05-GS', 14, 'Date et heure limite du dépôt de l''original', 'DATE_HEURE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE et garantieSoumission = OUI', false, NULL, 'SE_ORIGINAL_GARANTIE:LIMITE', NULL, NULL, NULL, false, NULL, true),
    ('B04-OP-10', 'B04-OP', 10, 'Modalité de la séance', 'LISTE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, 'Présentiel,En ligne,Mixte', NULL, NULL, false, 'Mixte', true),
    ('B04-OP-11', 'B04-OP', 11, 'Lien de suivi de la séance pour les soumissionnaires', 'URL', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE', false, NULL, NULL, NULL, NULL, NULL, false, NULL, true),
    ('B04-OP-12', 'B04-OP', 12, 'Délai entre l''heure limite et l''ouverture (minutes)', 'NOMBRE', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE', true, NULL, 'SE_OUVERTURE_PLIS:DELAI', NULL, NULL, NULL, false, '60', true),
    ('B04-OP-13', 'B04-OP', 13, 'Publication du procès-verbal sur la plateforme', 'OUI_NON', 'SAISIE', 'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE', 'FOURNITURES_SERVICES', 'modeRemise = ELECTRONIQUE', true, NULL, NULL, NULL, NULL, NULL, false, 'OUI', true)
ON CONFLICT ("CODE") DO UPDATE SET "CODE_RUBRIQUE" = EXCLUDED."CODE_RUBRIQUE", "RANG" = EXCLUDED."RANG", "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "SOURCE" = EXCLUDED."SOURCE", "DOCUMENT_MAITRE" = EXCLUDED."DOCUMENT_MAITRE", "REPRISES" = EXCLUDED."REPRISES", "TYPES_MARCHE" = EXCLUDED."TYPES_MARCHE", "CATEGORIES" = EXCLUDED."CATEGORIES", "CONDITION" = EXCLUDED."CONDITION", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE", "TEXTE_TYPE" = EXCLUDED."TEXTE_TYPE", "CONTROLE" = EXCLUDED."CONTROLE", "OPTIONS" = EXCLUDED."OPTIONS", "CLE_CADRAGE" = EXCLUDED."CLE_CADRAGE", "CLE_PPM" = EXCLUDED."CLE_PPM", "PAR_LOT" = EXCLUDED."PAR_LOT", "VALEUR_DEFAUT" = EXCLUDED."VALEUR_DEFAUT", "ACTIF" = EXCLUDED."ACTIF";

-- 3. Les contrôles des champs existants (fournitures)
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'SE_ORIGINAL_GARANTIE:LIEU_REMISE' WHERE "CODE" = 'B04-LR-02';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'DATES_ORDRE:REMISE,SE_HEURE_LIMITE:DATE' WHERE "CODE" = 'B04-LR-03';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'SE_HEURE_LIMITE:HEURE' WHERE "CODE" = 'B04-LR-04';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'DATES_ORDRE:OUVERTURE,SE_OUVERTURE_PLIS:DATE' WHERE "CODE" = 'B04-OP-02';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'SE_OUVERTURE_PLIS:HEURE' WHERE "CODE" = 'B04-OP-03';

-- 4. Les paramètres administrables
INSERT INTO public.t_parametre ("CLE", "VALEUR", "DATE_MAJ") VALUES
    ('FICHE_SE_FUSEAU', 'Indian/Antananarivo', now()),
    ('FICHE_SE_SIGNATURE_MIN', 'Avancée', now()),
    ('FICHE_SE_TAILLE_MAX_PLATEFORME_MO', '500', now()),
    ('FICHE_SE_DELAI_MIN_REMISE_JOURS', '30', now()),
    ('FICHE_SE_QUORUM_DEFAUT', '3/5', now())
ON CONFLICT ("CLE") DO NOTHING;

-- Bilan
SELECT "CODE", "TYPE", "OBLIGATOIRE", coalesce("CONTROLE", '-') AS "CONTROLE", coalesce("VALEUR_DEFAUT", '-') AS "DEFAUT", "ACTIF"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" LIKE 'B04-SE-%' OR "CODE" IN ('B05-GS-10', 'B05-GS-11', 'B05-GS-12', 'B05-GS-13', 'B05-GS-14', 'B04-OP-10',
       'B04-OP-11', 'B04-OP-12', 'B04-OP-13', 'B04-LR-02', 'B04-LR-03', 'B04-LR-04', 'B04-OP-02', 'B04-OP-03', 'B04-VE-01', 'B04-VE-02')
 ORDER BY 1;
SELECT "CLE", "VALEUR" FROM public.t_parametre WHERE "CLE" LIKE 'FICHE_SE_%' ORDER BY 1;

COMMIT;
