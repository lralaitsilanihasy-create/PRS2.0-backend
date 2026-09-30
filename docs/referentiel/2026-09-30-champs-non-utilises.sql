-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — les champs qu'aucun document n'utilise (arbitrage du pilote du 2026-09-30).
--
-- Règle, celle des fournitures du 29/09 : un champ qu'aucun document ne cite (jeton ou condition des modèles du DAO,
-- formulaires du candidat) et qu'aucune règle du serveur ne lit est RETIRÉ (actif = non), ses valeurs conservées ; un
-- champ non imprimé mais lu par une règle RESTE, facultatif (il ne bloque plus la validation).
--
-- - Travaux, quantité fixe et à commande (demande D4 §B2.2.6) : 17 retirés ; B08-MO-01 (intérêts
--   moratoires) et B08-AF-02 (montant de l'avance) facultatifs.
-- - Contrat-cadre de travaux : les 61 champs propres restés actifs après l'harmonisation du 29/09 (V54) et
--   qu'aucun document n'utilise ; B08-AT-03 (taux de l'avance) et B08-FT-03 (délai de paiement) facultatifs. Les champs
--   de la remise électronique (B04-SE-*) ne sont pas touchés : ils attendent le juriste.
-- - Prestations intellectuelles (demande D3 §B2.3) : 13 retirés ; B03-SP-03, B03-NP-01 et B05-PF-02,
--   lus par MONTANT_POSITIF et déjà facultatifs, restent.
--
-- Ce n'est PAS une migration : les fichiers de correspondance portent ces valeurs ; ce script aligne une base où ils ont
-- déjà été chargés. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-09-30-champs-non-utilises.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false
 WHERE "CODE" IN (
                  'B03-GT-02', 'B03-SU-01', 'B03-SU-02', 'B08-DB-01', 'B11-AN-01', 'B11-AN-02', 'B11-AN-03', 'B11-AN-04',
                  'B11-AN-05', 'B10-RE-01', 'B05-GA-02', 'B03-QT-11', 'B02-MW-03', 'B08-MR-02', 'B08-MR-03', 'B08-MR-04',
                  'B09-RP-02', 'B02-OC-01', 'B02-OC-02', 'B02-DK-02', 'B02-DK-03', 'B02-DK-04', 'B02-DK-05', 'B02-MC-01',
                  'B02-MC-02', 'B02-CT-01', 'B02-CT-02', 'B02-SN-01', 'B02-SN-02', 'B02-SN-03', 'B03-TT-01', 'B03-TT-02',
                  'B03-TT-03', 'B03-TT-04', 'B03-TT-05', 'B03-GM-02', 'B03-GM-03', 'B03-GM-04', 'B03-GM-05', 'B03-GM-06',
                  'B03-SM-01', 'B03-SM-02', 'B04-DK-01', 'B04-DK-02', 'B04-PT-01', 'B04-VS-01', 'B04-VS-02', 'B04-RT-02',
                  'B04-RT-03', 'B04-RN-01', 'B06-ET-01', 'B06-ET-03', 'B06-ET-04', 'B07-PT-01', 'B07-OM-01', 'B07-AT-03',
                  'B07-AT-04', 'B07-AT-05', 'B07-PJ-01', 'B07-DT-01', 'B07-DT-03', 'B07-DT-05', 'B07-DT-07', 'B07-XE-04',
                  'B08-AT-02', 'B08-AT-04', 'B08-FT-02', 'B08-FT-05', 'B08-FT-06', 'B08-FT-07', 'B08-FT-08', 'B08-FT-09',
                  'B09-XM-03', 'B09-AT-02', 'B10-RT-01', 'B10-RT-02', 'B11-AT-01', 'B11-AT-02', 'B03-TP-01', 'B03-TP-02',
                  'B03-TP-03', 'B03-TP-04', 'B03-TP-05', 'B08-AI-02', 'B02-CL-03', 'B09-DP-02', 'B04-QT-01', 'B04-QT-02',
                  'B06-TP-01', 'B06-OF-01', 'B06-CS-01'
                 );

UPDATE public.tr_champ_fiche_marche SET "OBLIGATOIRE" = false
 WHERE "CODE" IN ('B08-MO-01', 'B08-AF-02', 'B08-AT-03', 'B08-FT-03');

-- Contrôles (information) : les valeurs conservées sur les champs retirés, par champ.
SELECT split_part(v."CODE_CHAMP", '#', 1) AS "CHAMP", count(*) AS "VALEURS_CONSERVEES"
  FROM public.t_fiche_marche_valeur v
 WHERE split_part(v."CODE_CHAMP", '#', 1) IN (
                  'B03-GT-02', 'B03-SU-01', 'B03-SU-02', 'B08-DB-01', 'B11-AN-01', 'B11-AN-02', 'B11-AN-03', 'B11-AN-04',
                  'B11-AN-05', 'B10-RE-01', 'B05-GA-02', 'B03-QT-11', 'B02-MW-03', 'B08-MR-02', 'B08-MR-03', 'B08-MR-04',
                  'B09-RP-02', 'B02-OC-01', 'B02-OC-02', 'B02-DK-02', 'B02-DK-03', 'B02-DK-04', 'B02-DK-05', 'B02-MC-01',
                  'B02-MC-02', 'B02-CT-01', 'B02-CT-02', 'B02-SN-01', 'B02-SN-02', 'B02-SN-03', 'B03-TT-01', 'B03-TT-02',
                  'B03-TT-03', 'B03-TT-04', 'B03-TT-05', 'B03-GM-02', 'B03-GM-03', 'B03-GM-04', 'B03-GM-05', 'B03-GM-06',
                  'B03-SM-01', 'B03-SM-02', 'B04-DK-01', 'B04-DK-02', 'B04-PT-01', 'B04-VS-01', 'B04-VS-02', 'B04-RT-02',
                  'B04-RT-03', 'B04-RN-01', 'B06-ET-01', 'B06-ET-03', 'B06-ET-04', 'B07-PT-01', 'B07-OM-01', 'B07-AT-03',
                  'B07-AT-04', 'B07-AT-05', 'B07-PJ-01', 'B07-DT-01', 'B07-DT-03', 'B07-DT-05', 'B07-DT-07', 'B07-XE-04',
                  'B08-AT-02', 'B08-AT-04', 'B08-FT-02', 'B08-FT-05', 'B08-FT-06', 'B08-FT-07', 'B08-FT-08', 'B08-FT-09',
                  'B09-XM-03', 'B09-AT-02', 'B10-RT-01', 'B10-RT-02', 'B11-AT-01', 'B11-AT-02', 'B03-TP-01', 'B03-TP-02',
                  'B03-TP-03', 'B03-TP-04', 'B03-TP-05', 'B08-AI-02', 'B02-CL-03', 'B09-DP-02', 'B04-QT-01', 'B04-QT-02',
                  'B06-TP-01', 'B06-OF-01', 'B06-CS-01'
                 )
 GROUP BY 1 ORDER BY 1;

SELECT count(*) FILTER (WHERE NOT "ACTIF") AS "RETIRES", count(*) FILTER (WHERE "ACTIF") AS "ENCORE_ACTIFS"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN (
                  'B03-GT-02', 'B03-SU-01', 'B03-SU-02', 'B08-DB-01', 'B11-AN-01', 'B11-AN-02', 'B11-AN-03', 'B11-AN-04',
                  'B11-AN-05', 'B10-RE-01', 'B05-GA-02', 'B03-QT-11', 'B02-MW-03', 'B08-MR-02', 'B08-MR-03', 'B08-MR-04',
                  'B09-RP-02', 'B02-OC-01', 'B02-OC-02', 'B02-DK-02', 'B02-DK-03', 'B02-DK-04', 'B02-DK-05', 'B02-MC-01',
                  'B02-MC-02', 'B02-CT-01', 'B02-CT-02', 'B02-SN-01', 'B02-SN-02', 'B02-SN-03', 'B03-TT-01', 'B03-TT-02',
                  'B03-TT-03', 'B03-TT-04', 'B03-TT-05', 'B03-GM-02', 'B03-GM-03', 'B03-GM-04', 'B03-GM-05', 'B03-GM-06',
                  'B03-SM-01', 'B03-SM-02', 'B04-DK-01', 'B04-DK-02', 'B04-PT-01', 'B04-VS-01', 'B04-VS-02', 'B04-RT-02',
                  'B04-RT-03', 'B04-RN-01', 'B06-ET-01', 'B06-ET-03', 'B06-ET-04', 'B07-PT-01', 'B07-OM-01', 'B07-AT-03',
                  'B07-AT-04', 'B07-AT-05', 'B07-PJ-01', 'B07-DT-01', 'B07-DT-03', 'B07-DT-05', 'B07-DT-07', 'B07-XE-04',
                  'B08-AT-02', 'B08-AT-04', 'B08-FT-02', 'B08-FT-05', 'B08-FT-06', 'B08-FT-07', 'B08-FT-08', 'B08-FT-09',
                  'B09-XM-03', 'B09-AT-02', 'B10-RT-01', 'B10-RT-02', 'B11-AT-01', 'B11-AT-02', 'B03-TP-01', 'B03-TP-02',
                  'B03-TP-03', 'B03-TP-04', 'B03-TP-05', 'B08-AI-02', 'B02-CL-03', 'B09-DP-02', 'B04-QT-01', 'B04-QT-02',
                  'B06-TP-01', 'B06-OF-01', 'B06-CS-01'
                 );

SELECT "CODE", "ACTIF", "OBLIGATOIRE", coalesce("CONTROLE", '-') AS "CONTROLE"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B08-MO-01', 'B08-AF-02', 'B08-AT-03', 'B08-FT-03')
 ORDER BY 1;

COMMIT;
