-- V7 — Lettre de renvoi « lue » : suivi par AGENT et non plus par tutelle
-- (chantier « lettre lue par agent », décision métier du 2026-08-27 ;
--  plan : docs/plan-lettre-lue-par-agent.md §2.1).
--
-- Jusqu'ici, la trace de lecture d'une lettre de renvoi était portée par le seul ID_PRMP
-- (unicité uk_lettre_lue (ID_LETTRE, ID_PRMP)). Or, pour une UGPM, la claim « ref » du jeton
-- porte l'ID_PRMP de sa TUTELLE (AuthService#login, branche UGPM) : la trace posée par une
-- UGPM était indiscernable d'une trace posée par la PRMP elle-même, et éteignait le badge
-- « Mes lettres de renvoi » de sa PRMP de tutelle. Le PO a tranché le 2026-08-27 : la lecture
-- devient un suivi INDIVIDUEL.
--
-- Identifiant d'agent retenu : le LOGIN du compte (t_compte_auth."LOGIN", = claim « sub » du
-- jeton) — seul identifiant individuel disponible dans le JWT. Aucune claim nouvelle, aucun
-- aller-retour en base au marquage.
--
-- ID_PRMP est CONSERVÉ (NOT NULL) : il reste le périmètre de tutelle de la trace (« qui, dans
-- quelle tutelle, a lu quoi ») ; la purge par dossier passe par ID_LETTRE et ne bouge pas.
--
-- REPRISE DES DONNÉES (choix figé au plan §2.1) : chaque ligne existante est attribuée au
-- compte PRMP TITULAIRE de la tutelle → les lettres déjà tracées restent « lues » pour la
-- PRMP, donc AUCUNE avalanche de badges au déploiement. En contrepartie, une UGPM reverra
-- « Non lue » une lettre qu'elle seule avait consultée : assumé, c'est le sens même de la
-- décision. L'alternative (purger toutes les lignes) aurait re-signalé « non lue » à toutes
-- les PRMP — rejetée.

-- 1) Nouvelle colonne (casse et type alignés sur t_compte_auth."LOGIN" varchar(100)).
ALTER TABLE public.t_lettre_renvoi_lue ADD COLUMN IF NOT EXISTS "LOGIN_AGENT" character varying(100);

-- 2) Reprise : attribuer chaque ligne existante au compte PRMP titulaire de la tutelle.
--    min("LOGIN") protège du cas (improbable) de plusieurs comptes PRMP pour un même REF_ACTEUR.
UPDATE public.t_lettre_renvoi_lue l
   SET "LOGIN_AGENT" = (SELECT min(c."LOGIN") FROM public.t_compte_auth c
                         WHERE c."TYPE_ACTEUR" = 'PRMP' AND c."REF_ACTEUR" = l."ID_PRMP");

-- 3) Lignes inattribuables (tutelle sans compte PRMP) : trace orpheline, supprimée (volontaire).
DELETE FROM public.t_lettre_renvoi_lue WHERE "LOGIN_AGENT" IS NULL;

-- 4) Verrouiller, puis remplacer l'unicité « tutelle » par l'unicité « agent ».
ALTER TABLE public.t_lettre_renvoi_lue ALTER COLUMN "LOGIN_AGENT" SET NOT NULL;
ALTER TABLE public.t_lettre_renvoi_lue DROP CONSTRAINT IF EXISTS uk_lettre_lue;
ALTER TABLE public.t_lettre_renvoi_lue
    ADD CONSTRAINT uk_lettre_lue_agent UNIQUE ("ID_LETTRE", "LOGIN_AGENT");
