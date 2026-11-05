-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V167 — Seuls les éléments NOUVEAUX du dossier de marché portent des points (demande front du 2026-11-05
-- `demande-backend-2026-11-05-points-par-element.md`, B3 ; principe du pilote du 05/11, après V166).
--
-- Le PV de la CNM, les informations du DAO et le bordereau estimatif ont été examinés au dossier de mise en concurrence : DAO et
-- BORDEREAU sortent du rattachement, comme PV_CNM.
-- 1. Les neuf points qui portaient DAO ou BORDEREAU en élément secondaire sont RÉÉCRITS, par libellé, seulement s'ils portent encore
--    la valeur posée par V166 (un réglage de l'Administrateur n'est pas écrasé), dans tout sous-type DDM.
-- 2. Les points de même libellé des autres sous-types DDM (MAOR…), encore sans élément, reçoivent le même rattachement que MAOO.
-- Une base neuve reçoit ce rattachement des semeurs (ElementsDossier.RATTACHEMENT_DDM). Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_points_ctrl p
SET "ELEMENTS" = r.cible
FROM (VALUES
    ('Délai de remise des offres', 'SEANCE,DAO', 'SEANCE'),
    ('Rejets motivés par le DAO', 'EVALUATION,DAO', 'EVALUATION'),
    ('Montant évalué conforme au DAO', 'EVALUATION,BORDEREAU', 'EVALUATION'),
    ('Offres anormales : processus et seuils', 'EVALUATION,DAO', 'EVALUATION'),
    ('Post-qualification sur les seuls critères du DAO', 'EVALUATION,DAO', 'EVALUATION'),
    ('Qualification : activités et critères', 'EVALUATION,DAO', 'EVALUATION'),
    ('Spécifications techniques', 'EVALUATION,DAO', 'EVALUATION'),
    ('Moralité des prix', 'EVALUATION,BORDEREAU', 'EVALUATION'),
    ('Offre retenue acceptable', 'ATTRIBUTION,PRESENTATION,BORDEREAU', 'ATTRIBUTION,PRESENTATION')
) AS r(libelle, v166, cible)
WHERE p."ID_TYPE_DOSSIER" = 'DDM' AND p."LIBEL_POINT_CTRL" = r.libelle AND p."ELEMENTS" = r.v166;

UPDATE public.tr_points_ctrl p
SET "ELEMENTS" = r.elements
FROM (VALUES
    ('CAO désignée, membres déclarés', 'SEANCE,PIECES'),
    ('PV d''ouverture signé et publié', 'SEANCE'),
    ('PV d''ouverture : date et heure', 'SEANCE'),
    ('PV d''ouverture : quorum et signataires', 'SEANCE'),
    ('Délai de remise des offres', 'SEANCE'),
    ('Conformité des documents essentiels', 'SEANCE,EVALUATION'),
    ('Rejets motivés par le DAO', 'EVALUATION'),
    ('Montant évalué conforme au DAO', 'EVALUATION'),
    ('Offres anormales : justification demandée', 'EVALUATION'),
    ('Offres anormales : processus et seuils', 'EVALUATION'),
    ('Classement et attributaire proposé', 'EVALUATION,ATTRIBUTION'),
    ('Post-qualification sur les seuls critères du DAO', 'EVALUATION'),
    ('Qualification : activités et critères', 'EVALUATION'),
    ('Spécifications techniques', 'EVALUATION'),
    ('Moralité des prix', 'EVALUATION'),
    ('Rapport signé de tous les membres', 'EVALUATION'),
    ('PV de validation cohérent', 'EVALUATION'),
    ('Projet de marché conforme à l''offre retenue', 'ATTRIBUTION'),
    ('Validité de l''offre retenue', 'ATTRIBUTION'),
    ('Offre retenue acceptable', 'ATTRIBUTION,PRESENTATION'),
    ('Publicité effective de l''avis', 'PIECES')
) AS r(libelle, elements)
WHERE p."ID_TYPE_DOSSIER" = 'DDM' AND p."LIBEL_POINT_CTRL" = r.libelle AND p."ELEMENTS" IS NULL;
