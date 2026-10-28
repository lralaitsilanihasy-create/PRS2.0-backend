-- ⚠️ 2026-10-28 (demande front « compte-connexion-controleur », B2) — un compte de connexion pour chaque contrôleur qui n'en a
-- pas : un contrôleur créé à l'écran n'en recevait aucun (seul CompteSeeder en créait, en développement). Désormais
-- ControleurService.create l'ouvre avec la fiche ; cette migration rattrape les fiches d'avant.
-- Le compte est actif mais SANS MOT DE PASSE UTILISABLE : la valeur n'est pas une empreinte BCrypt, aucune saisie ne lui
-- correspond. L'Administrateur fixe le mot de passe provisoire par POST /api/comptes-auth/{login}/reinitialiser-mot-de-passe.
-- Idempotente ; un login déjà pris (par un compte d'un autre type) n'est pas touché.
INSERT INTO public.t_compte_auth ("LOGIN", "MOT_DE_PASSE", "TYPE_ACTEUR", "REF_ACTEUR", "ACTIF", "STATUT")
SELECT c."IM_CONTROLEUR", '!aucun-mot-de-passe-fixe', 'CONTROLEUR', c."IM_CONTROLEUR", true, 'ACTIF'
FROM public.tr_controleur c
WHERE NOT EXISTS (SELECT 1 FROM public.t_compte_auth a WHERE a."LOGIN" = c."IM_CONTROLEUR");
