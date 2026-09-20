-- =====================================================================================================
-- V33 — Pré-contrôle du PPM (assistant IA, lot 3, étape 2) : dire, dans le référentiel des modes, À QUEL
-- PALIER DE L'ARRÊTÉ chaque mode appartient.
--
-- LE BESOIN. La règle « le mode de passation est-il conforme au seuil ? » (manuel de contrôle a priori,
-- p. 14) compare deux choses : ce que le montant appelle — appel d'offres ouvert, consultation ou achat
-- direct, selon l'arrêté n° 13 156/2019-MEF, art. 2, 2° — et ce que la PRMP a SAISI. Le premier terme
-- se lit désormais dans tr_seuil_marche (V32). Le second manquait : rien, dans tr_mode_passation, ne dit
-- qu'« Appel d'offres ouvert » est le palier haut et « Consultation de prix » le palier intermédiaire.
--
-- POURQUOI UNE COLONNE ET NON UNE DÉTECTION PAR MOT-CLÉ. Le référentiel des modes est administrable, et
-- le projet a déjà tranché ce point pour l'AGPM (V21/V22) : « détection déterministe et data-driven
-- (l'admin coche le(s) mode(s) concerné(s)), jamais par mot-clé de libellé ». Un mot-clé reste utile pour
-- POSER la valeur initiale — c'est ce que fait ModePassation.procedureSeuilDepuisLibelle, appelée par le
-- seeder au démarrage sur les modes qui n'en portent pas encore, exactement comme DECLENCHE_AGPM. Mais la
-- source de vérité est la colonne, que l'Administrateur corrige sans redéploiement.
--
-- CE QUE LA COLONNE NE FAIT PAS. Elle ne détermine aucun mode, n'en refuse aucun et ne bloque aucune
-- saisie : elle sert à SIGNALER un écart, et le signalement s'écarte. Un mode laissé à NULL (par exemple
-- créé à la volée par un import PDF, qui n'apporte qu'un libellé non reconnu) rend la règle muette pour
-- ce mode — on ne devine pas.
--
-- Idempotente : ADD COLUMN IF NOT EXISTS, CHECK recréé après DROP IF EXISTS.
-- =====================================================================================================

ALTER TABLE public.tr_mode_passation
    ADD COLUMN IF NOT EXISTS "PROCEDURE_SEUIL" character varying(30);

ALTER TABLE public.tr_mode_passation DROP CONSTRAINT IF EXISTS "ck_mode_passation_procedure_seuil";
ALTER TABLE public.tr_mode_passation ADD CONSTRAINT "ck_mode_passation_procedure_seuil"
    CHECK ("PROCEDURE_SEUIL" IS NULL OR "PROCEDURE_SEUIL" IN (
        'APPEL_OFFRES_OUVERT', 'CONSULTATION', 'ACHAT_DIRECT'));

COMMENT ON COLUMN public.tr_mode_passation."PROCEDURE_SEUIL" IS
    'Palier de l''arrete n 13 156/2019-MEF (art. 2, 2) auquel ce mode appartient : APPEL_OFFRES_OUVERT, '
    'CONSULTATION ou ACHAT_DIRECT. Sert au pre-controle du PPM a comparer le mode SAISI a ce que le '
    'montant appelle. NULL = mode non classe, la regle reste muette pour lui. Administrable ; la valeur '
    'initiale est posee au demarrage par ReglesPreControleSeeder d''apres le libelle.';
