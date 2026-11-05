-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V166 — Les éléments du dossier que contrôle chaque point de la grille (demande front du 2026-11-05
-- `demande-backend-2026-11-05-points-par-element.md`, B1, B2 ; rattachement validé par le pilote le 05/11).
--
-- 1. tr_points_ctrl.ELEMENTS : codes séparés par des virgules, le principal d'abord (PRESENTATION, PV_CNM, DAO, SEANCE, EVALUATION,
--    ATTRIBUTION, BORDEREAU, PIECES) ; nul : aucun. Ne sert qu'au rangement de la grille à l'écran : ordre, portée, obligation,
--    périmètre, examens et PV ne changent pas.
-- 2. Le rattachement des 21 points de la grille du dossier de marché (points communs DDM et points MAOO), par LIBELLÉ (les identifiants
--    diffèrent selon la base), seulement quand le point n'a pas encore d'éléments (un réglage de l'Administrateur n'est pas écrasé).
--    Aucun point sur PV_CNM. Une base neuve reçoit le même rattachement des semeurs (ElementsDossier.RATTACHEMENT_DDM).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_points_ctrl ADD COLUMN IF NOT EXISTS "ELEMENTS" character varying(200);

UPDATE public.tr_points_ctrl p
SET "ELEMENTS" = r.elements
FROM (VALUES
    ('CAO désignée, membres déclarés', 'SEANCE,PIECES'),
    ('PV d''ouverture signé et publié', 'SEANCE'),
    ('PV d''ouverture : date et heure', 'SEANCE'),
    ('PV d''ouverture : quorum et signataires', 'SEANCE'),
    ('Délai de remise des offres', 'SEANCE,DAO'),
    ('Conformité des documents essentiels', 'SEANCE,EVALUATION'),
    ('Rejets motivés par le DAO', 'EVALUATION,DAO'),
    ('Montant évalué conforme au DAO', 'EVALUATION,BORDEREAU'),
    ('Offres anormales : justification demandée', 'EVALUATION'),
    ('Offres anormales : processus et seuils', 'EVALUATION,DAO'),
    ('Classement et attributaire proposé', 'EVALUATION,ATTRIBUTION'),
    ('Post-qualification sur les seuls critères du DAO', 'EVALUATION,DAO'),
    ('Qualification : activités et critères', 'EVALUATION,DAO'),
    ('Spécifications techniques', 'EVALUATION,DAO'),
    ('Moralité des prix', 'EVALUATION,BORDEREAU'),
    ('Rapport signé de tous les membres', 'EVALUATION'),
    ('PV de validation cohérent', 'EVALUATION'),
    ('Projet de marché conforme à l''offre retenue', 'ATTRIBUTION'),
    ('Validité de l''offre retenue', 'ATTRIBUTION'),
    ('Offre retenue acceptable', 'ATTRIBUTION,PRESENTATION,BORDEREAU'),
    ('Publicité effective de l''avis', 'PIECES')
) AS r(libelle, elements)
WHERE p."ID_TYPE_DOSSIER" = 'DDM' AND (p."ID_SOUS_TYPE" IS NULL OR p."ID_SOUS_TYPE" = 'MAOO')
  AND p."LIBEL_POINT_CTRL" = r.libelle AND p."ELEMENTS" IS NULL;
