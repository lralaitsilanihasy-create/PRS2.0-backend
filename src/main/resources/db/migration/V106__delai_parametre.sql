-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V106 — ⚠️ 2026-10-13 : manuel révisé pour l'application, lot 2, tranche R6a (§B1 : les délais de la
-- loi n° 2016-055, arbitrage Q6 du pilote).
--
-- Les neuf délais légaux sont une table en code (DelaisLegaux) ; les autres délais de publicité
-- (appel d'offres restreint, prestations intellectuelles, AMI, contrat-cadre, pré-qualification, délai
-- aménagé) sont des paramètres administrables, SIGNALÉS et jamais bloquants tant que le pilote ne les a
-- pas confirmés : valeurs proposées, actives, ajustables par l'Administrateur (écran Délais).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_delai_parametre (
    "CODE"       varchar(40) PRIMARY KEY,
    "VALEUR"     integer NOT NULL,
    "UNITE"      varchar(20) NOT NULL,
    "TEXTE"      varchar(300),
    "ACTIF"      boolean NOT NULL DEFAULT true,
    "DATE_MAJ"   timestamp,
    "IM_ACTEUR"  varchar(100),
    CONSTRAINT ck_delai_parametre_valeur CHECK ("VALEUR" >= 0),
    CONSTRAINT ck_delai_parametre_unite CHECK ("UNITE" IN ('JOURS', 'JOURS_FRANCS', 'JOURS_OUVRABLES', 'MOIS'))
);

INSERT INTO public.t_delai_parametre ("CODE", "VALEUR", "UNITE", "TEXTE", "ACTIF") VALUES
    ('PUBLICITE_AOR', 15, 'JOURS', 'Appel d''offres restreint : lettres d''invitation → remise des offres (décret 2019-1310, art. 2-3 ; valeur à confirmer)', true),
    ('PUBLICITE_DC_PI', 30, 'JOURS', 'Prestations intellectuelles : lettres d''invitation → remise des propositions (valeur à confirmer)', true),
    ('PUBLICITE_AMI', 15, 'JOURS', 'Appel à manifestation d''intérêt : publication → dépôt des manifestations (art. 32-III ; valeur à confirmer)', true),
    ('PUBLICITE_CONTRAT_CADRE', 30, 'JOURS', 'Contrat-cadre : publication → remise des offres (valeur à confirmer)', true),
    ('PUBLICITE_PREQUALIFICATION', 30, 'JOURS', 'Pré-qualification : publication → dépôt des candidatures (valeur à confirmer)', true),
    ('DELAI_AMENAGE', 15, 'JOURS', 'Délai aménagé justifié au plan : publication → remise des offres (manuel 2-II-A P7 ; valeur à confirmer)', true)
ON CONFLICT ("CODE") DO NOTHING;
