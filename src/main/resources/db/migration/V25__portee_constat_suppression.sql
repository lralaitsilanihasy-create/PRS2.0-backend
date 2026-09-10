-- =====================================================================================================
-- V25 — Portée « SUPPRESSION » des points de contrôle (demande pilote du 2026-09-10).
--
-- L'examen d'une MISE À JOUR ne porte que sur ce qui a changé. Une ligne RETIRÉE par la nouvelle version
-- n'est pas examinée comme une ligne du plan — elle n'en fait plus partie — mais elle ne peut pas non
-- plus disparaître de l'examen sans un mot : la Commission doit CONSTATER le retrait, et pouvoir
-- l'assortir d'une observation s'il appelle une réserve. D'où une portée de plus.
--
-- CETTE MIGRATION NE FAIT QU'OUVRIR LA CONTRAINTE. Le point lui-même est semé par
-- PointsCtrlFicheAgpmSeeder, au démarrage — patron posé par V16, et pour la même raison :
-- tr_points_ctrl porte deux clés étrangères (ID_TYPE_DOSSIER, ID_SOUS_TYPE) vers des référentiels
-- qu'AUCUNE migration ne crée. Un INSERT ici échouerait en 23503 sur toute base neuve. Sans
-- l'élargissement ci-dessous, en revanche, le seeder échouerait sur le CHECK posé par V16 : les deux
-- moitiés sont indissociables.
--
-- OÙ LE RÉSULTAT SE RANGE. Nulle part de nouveau : c'est un t_examen_detail comme les autres, dont
-- l'ID_DETAIL est celui de LA LIGNE RETIRÉE ELLE-MÊME. Elle existe toujours dans la version — la copie
-- d'une mise à jour conserve les lignes supprimées, restaurables (règle du 2026-08-05) — donc rien
-- n'est à inventer pour l'y rattacher, et l'observation éventuelle passe par t_observation_controle
-- comme pour tout autre point.
--
-- POURQUOI UNE PORTÉE PLUTÔT QU'UN IDENTIFIANT CONNU. tr_points_ctrl n'a pas de colonne « code » : un
-- point ne se reconnaît que par sa PORTÉE, mécanisme déjà data-driven (LIGNE / DOSSIER / FICHE / AGPM).
-- Le reconnaître par son ID_POINT_CTRL aurait mis un numéro en dur dans le code — le genre de constante
-- qu'une reprise de données déplace sans prévenir.
-- =====================================================================================================

-- ⚠️ La colonne fait 10 caractères depuis l'origine : « SUPPRESSION » en compte 11. L'élargir fait
-- partie de la même livraison — sans quoi le seeder échouerait sur la longueur avant même le CHECK.
ALTER TABLE public.tr_points_ctrl ALTER COLUMN "PORTEE" TYPE varchar(20);

ALTER TABLE public.tr_points_ctrl DROP CONSTRAINT IF EXISTS "tr_points_ctrl_PORTEE_check";
ALTER TABLE public.tr_points_ctrl ADD CONSTRAINT "tr_points_ctrl_PORTEE_check"
    CHECK ("PORTEE" IN ('LIGNE', 'DOSSIER', 'FICHE', 'AGPM', 'SUPPRESSION'));
