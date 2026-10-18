-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Référentiel de la fiche DAO — le DAO d'une procédure en remise électronique (demande front du 2026-10-18,
-- demande-backend-2026-10-18-dao-electronique.md, §B1 et §B5).
--
-- B1 : les champs des clauses papier ne valent qu'en remise papier (condition « modeRemise != ELECTRONIQUE » : une fiche
-- sans mode est papier) — ni saisis, ni attendus au bilan, ni imprimés en électronique :
--   contrat-cadre : B04-RQ-01/02/03 (remise sous pli), B04-DS-04 (adresse de retrait), B04-DS-07/08/09/11 (consultation au
--   guichet) ; B04-DS-10 (localité) reste ouvert : il date la signature de l'avis et de la lettre AOR ;
--   fournitures : B04-RO-01/02 (copies, mentions des plis) ; travaux : B04-FP-01/02 ; prestations intellectuelles :
--   B04-FL-01/02. Restent ouverts : B04-LR-02 (lieu de remise de l'original de la garantie), B04-OP-01..03 et B04-OV-01..02
--   (date, heure et lieu de la séance, lus par les échéances de la remise électronique).
-- B5 : le délai d'envoi des questions (jours avant la date limite) ne peut être plus court que les 10 jours de l'art. 35-II
-- (délai légal n° 3) : règle DELAI_QUESTIONS, bloquante, sur B04-RC-02 (contrat-cadre, libellé corrigé : c'est un délai
-- d'envoi, pas de réponse), B04-DE-02 (fournitures), B04-EQ-01 (travaux), B04-EP-03 (prestations intellectuelles).
--
-- Ce n'est PAS une migration : les fichiers de correspondance portent ces valeurs ; ce script aligne une base où ils ont
-- déjà été chargés. Idempotent. Les fiches ne sont pas touchées : leurs valeurs papier restent en base, simplement fermées
-- en électronique.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-18-dao-electronique.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "CONDITION" = 'modeRemise != ELECTRONIQUE'
 WHERE "CODE" IN ('B04-RQ-01', 'B04-RQ-02', 'B04-RQ-03', 'B04-DS-04', 'B04-DS-07', 'B04-DS-08', 'B04-DS-09', 'B04-DS-11',
                  'B04-RO-01', 'B04-RO-02', 'B04-FP-01', 'B04-FP-02', 'B04-FL-01', 'B04-FL-02')
   AND "CONDITION" IS DISTINCT FROM 'modeRemise != ELECTRONIQUE';

UPDATE public.tr_champ_fiche_marche
   SET "CONTROLE" = CASE WHEN "CONTROLE" IS NULL OR "CONTROLE" = '' THEN 'DELAI_QUESTIONS:DELAI'
                         ELSE "CONTROLE" || ',DELAI_QUESTIONS:DELAI' END
 WHERE "CODE" IN ('B04-RC-02', 'B04-DE-02', 'B04-EQ-01', 'B04-EP-03')
   AND coalesce("CONTROLE", '') NOT LIKE '%DELAI_QUESTIONS%';

UPDATE public.tr_champ_fiche_marche
   SET "LIBELLE" = 'Délai d''envoi des demandes d''informations (jours avant la date limite)'
 WHERE "CODE" = 'B04-RC-02';

COMMIT;
