-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V162 — La grille du marché sur appel d'offres face au manuel de contrôle a priori, ch. 2, III.C, p. 47-55 (demande front du
-- 2026-11-03 `demande-backend-2026-11-03-grille-marche-aoo.md`, B1, B2).
--
-- 1. tr_points_ctrl.SOUS_TYPES_EXCLUS : les sous-types (séparés par des virgules) où un point hérité par la grille de base ne
--    s'applique pas — ainsi « Publicité effective de l'avis » (MAOO) est écarté des MAOR, MAORI et MAOR-CC, dont les lettres
--    d'invitation sont contrôlées par leur propre point. Les trois points nouveaux du MAOO sont semés par PointsCtrlManuelSeeder.
-- 2. B2 — trois descriptions précisées (variantes techniques ; signé et paraphé ; offre complète), seulement si elles sont encore
--    celles d'origine : une description retouchée par l'Administrateur n'est pas écrasée. Une base neuve les reçoit du semeur.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_points_ctrl ADD COLUMN IF NOT EXISTS "SOUS_TYPES_EXCLUS" character varying(200);

UPDATE public.tr_points_ctrl
SET "DECRIPT_POINT_CTRL" = 'Les erreurs arithmétiques sont-elles corrigées selon les IC, et le montant évalué n''inclut-il que le rabais, la '
        || 'préférence, les critères et les variantes techniques prévus par le DAO ?'
WHERE "ID_TYPE_DOSSIER" = 'DDM' AND "ID_SOUS_TYPE" IS NULL AND "LIBEL_POINT_CTRL" = 'Montant évalué conforme au DAO'
  AND "DECRIPT_POINT_CTRL" = 'Les erreurs arithmétiques sont-elles corrigées selon les IC, et le montant évalué n''inclut-il que le '
        || 'rabais, la préférence et les critères prévus par le DAO ?';

UPDATE public.tr_points_ctrl
SET "DECRIPT_POINT_CTRL" = 'Le rapport d''évaluation est-il signé et paraphé de tous les membres, observations de désaccord comprises ?'
WHERE "ID_TYPE_DOSSIER" = 'DDM' AND "ID_SOUS_TYPE" IS NULL AND "LIBEL_POINT_CTRL" = 'Rapport signé de tous les membres'
  AND "DECRIPT_POINT_CTRL" = 'Le rapport d''évaluation est-il signé de tous les membres, observations de désaccord comprises ?';

UPDATE public.tr_points_ctrl
SET "DECRIPT_POINT_CTRL" = 'AE daté et signé, validité de l''offre, délai, garantie de soumission (montant, forme, validité), attestation '
        || 'du fabricant (fournitures), certificat de visite (travaux) ; offre complète : bordereau des prix ou DQE entier et non '
        || 'modifié ?'
WHERE "ID_TYPE_DOSSIER" = 'DDM' AND "ID_SOUS_TYPE" = 'MAOO' AND "LIBEL_POINT_CTRL" = 'Conformité des documents essentiels'
  AND "DECRIPT_POINT_CTRL" = 'AE daté et signé, validité de l''offre, délai, garantie de soumission (montant, forme, validité), '
        || 'attestation du fabricant (fournitures), certificat de visite (travaux) ?';
