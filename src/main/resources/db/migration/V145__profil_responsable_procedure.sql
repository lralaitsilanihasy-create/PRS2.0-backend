-- ⚠️ 2026-10-28 (demande front « profil-responsable-procedure », B1, B4 ; arbitrage du pilote) — le profil dédié au rôle de
-- responsable de procédure, au référentiel des profils. Le rôle de sécurité se déduit du libellé (ProfilUtilisateur.resolve :
-- « responsable » → RESPONSABLE_PROCEDURE). Aucun compte n'est créé d'office : l'Administrateur crée les comptes dans
-- « Comptes & personnes », comme un contrôleur (localité facultative).
-- Identifiant : le suivant après les neuf profils d'origine (10 sur une base complète) ; rien si un libellé « responsable »
-- existe déjà.
INSERT INTO public.tr_profile ("ID_PROFILE", "PROFILE")
SELECT GREATEST(COALESCE(MAX("ID_PROFILE"), 0), 9) + 1, 'Responsable de procédure'
FROM public.tr_profile
WHERE NOT EXISTS (SELECT 1 FROM public.tr_profile WHERE lower("PROFILE") LIKE '%responsable%');
