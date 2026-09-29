-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — lot D3, prestations intellectuelles (demande front du 2026-09-29,
-- demande-backend-2026-09-29-lot-d3-prestations-intellectuelles.md §B2.1 et §B2.2 ; B2.3 attend le pilote).
--
-- §B2.1 — sept champs créés (codes proposés par le front, aucun ne heurte un code existant).
-- §B2.2 — corrections : B02-MS-01 (quatre options, séparateur « | » car l'une contient une virgule), B04-LP-01 (+ « Une
-- autre langue que le français »), B04-LH-02 (DATE_HEURE), B08-IP-01 (NOMBRE : points ajoutés au taux directeur),
-- B09-PP-01 (retiré), B09-FC-01 (réactivé, facultatif), B09-DP-01 (OUI_NON « Le délai court de l'ordre de service de
-- commencer », facultatif).
-- La question de cadrage des pénalités (B09-PR-01) est ouverte aux prestations intellectuelles par la migration V53,
-- appliquée au démarrage du serveur ; ce script ne la touche pas.
--
-- Ce n'est PAS une migration : le fichier de correspondance des prestations intellectuelles porte ces valeurs ; ce script
-- aligne une base où il a déjà été chargé. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-29-lot-d3-prestations-intellectuelles.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

-- §B2.1 — les sept champs.
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "CONTROLE", "OPTIONS", "PAR_LOT", "ACTIF") VALUES
    ('B04-EP-04', 'B04-EP', 4, 'Délai de réponse de la PRMP aux demandes d''éclaircissement (jours avant la date limite)', 'NOMBRE', 'SAISIE', 'DPIC', 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES', NULL, true, NULL, NULL, false, true),
    ('B05-PF-13', 'B05-PF', 13, 'Budget disponible (Ariary)', 'MONTANT', 'SAISIE', 'DPIC', 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES', NULL, false, NULL, NULL, false, true),
    ('B06-TP-07', 'B06-TP', 7, 'Score technique minimum (points)', 'NOMBRE', 'SAISIE', 'DPIC', 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES', NULL, false, NULL, NULL, false, true),
    ('B06-CS-02', 'B06-CS', 2, 'Poids de la proposition technique (T)', 'NOMBRE', 'SAISIE', 'DPIC', 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES', NULL, false, NULL, NULL, false, true),
    ('B06-CS-03', 'B06-CS', 3, 'Poids de la proposition financière (F)', 'NOMBRE', 'SAISIE', 'DPIC', 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES', NULL, false, NULL, NULL, false, true),
    ('B08-AI-03', 'B08-AI', 3, 'Taux de l''avance forfaitaire (%)', 'POURCENTAGE', 'SAISIE', 'CCAP', 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES', 'avance = OUI', true, 'AVANCE_MAX_20:TAUX', NULL, false, true),
    ('B09-OP-02', 'B09-OP', 2, 'Délai des opérations de vérification (jours, à défaut les 30 jours du CCAG)', 'NOMBRE', 'SAISIE', 'CCAP', 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES', NULL, false, NULL, NULL, false, true)
ON CONFLICT ("CODE") DO UPDATE SET "CODE_RUBRIQUE" = EXCLUDED."CODE_RUBRIQUE", "RANG" = EXCLUDED."RANG", "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "SOURCE" = EXCLUDED."SOURCE", "DOCUMENT_MAITRE" = EXCLUDED."DOCUMENT_MAITRE", "TYPES_MARCHE" = EXCLUDED."TYPES_MARCHE", "CATEGORIES" = EXCLUDED."CATEGORIES", "CONDITION" = EXCLUDED."CONDITION", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE", "CONTROLE" = EXCLUDED."CONTROLE", "OPTIONS" = EXCLUDED."OPTIONS", "PAR_LOT" = EXCLUDED."PAR_LOT", "ACTIF" = EXCLUDED."ACTIF";

-- §B2.2 — les corrections.
UPDATE public.tr_champ_fiche_marche SET "OPTIONS" = 'Qualité technique, expérience et proposition financière|Budget prédéterminé dont le candidat propose la meilleure utilisation|Meilleure proposition financière parmi les candidats ayant obtenu la note technique minimale|Qualité technique exclusivement'
 WHERE "CODE" = 'B02-MS-01';
UPDATE public.tr_champ_fiche_marche SET "OPTIONS" = 'Français,Français et une seconde langue,Une autre langue que le français'
 WHERE "CODE" = 'B04-LP-01';
UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'DATE_HEURE' WHERE "CODE" = 'B04-LH-02';
UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'NOMBRE',
       "LIBELLE" = 'Intérêts moratoires : points ajoutés au taux directeur de la Banque centrale'
 WHERE "CODE" = 'B08-IP-01';
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" = 'B09-PP-01';
UPDATE public.tr_champ_fiche_marche SET "ACTIF" = true, "OBLIGATOIRE" = false WHERE "CODE" = 'B09-FC-01';
UPDATE public.tr_champ_fiche_marche SET "TYPE" = 'OUI_NON', "OBLIGATOIRE" = false,
       "LIBELLE" = 'Le délai court de l''ordre de service de commencer'
 WHERE "CODE" = 'B09-DP-01';

-- Aucune fiche ne porte une moitié de l'ancienne option de B02-MS-01 (attendu : 0).
SELECT count(*) AS "VALEURS_B02_MS_01_COUPEES" FROM public.t_fiche_marche_valeur
 WHERE "CODE_CHAMP" = 'B02-MS-01' AND "VALEUR" IN ('Qualité technique', 'expérience et proposition financière');

SELECT "CODE", "TYPE", "ACTIF", "OBLIGATOIRE", coalesce("CONTROLE", '-') AS "CONTROLE", left(coalesce("OPTIONS", '-'), 70) AS "OPTIONS"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B04-EP-04', 'B05-PF-13', 'B06-TP-07', 'B06-CS-02', 'B06-CS-03', 'B08-AI-03', 'B09-OP-02', 'B02-MS-01',
                  'B04-LP-01', 'B04-LH-02', 'B08-IP-01', 'B09-PP-01', 'B09-FC-01', 'B09-DP-01')
 ORDER BY 1;

COMMIT;
