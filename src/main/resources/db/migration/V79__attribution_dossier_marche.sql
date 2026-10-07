-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V79 — Évaluation des offres, lot 2, tranche 2a (demande front du 2026-10-07 « de la proposition d'attribution à la notification »,
-- §B1, §B2 ; arbitrages du pilote du 07/10 : un dossier de marché par lot (Q2), le projet de marché produit par le serveur (Q1),
-- chaque marché en ligne au contrôle de la Commission (Q11)).
--
-- 1. L'attribution d'un lot de la procédure : son état, l'offre proposée par le rapport, le dossier de marché (famille DDM) soumis au
--    contrôle de la Commission, et le projet de marché produit par le serveur (PDF, Word). Un lot par ligne ; le dossier DAO garde
--    seul le lien t_dossier.ID_DMC — le dossier de marché se rattache ici.
-- 2. Les codes des types de pièces du dossier de marché (14 à 18), pour les joindre d'office sans dépendre de leur identifiant ;
--    sans effet là où ces types n'existent pas (base neuve : les référentiels ne sont pas posés par les migrations).
-- Les points de contrôle du dossier de marché sont semés au démarrage (PointsCtrlDossierMarcheSeeder), comme ceux de V16.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_attribution (
    "ID_DMC"             bigint NOT NULL,
    "LOT"                integer NOT NULL,
    "ETAT"               character varying(20) NOT NULL,
    "ID_OFFRE_PROPOSEE"  character varying(36),
    "ID_DOSSIER"         integer,
    "DOSSIER_CREE_LE"    timestamp without time zone,
    "DOSSIER_CREE_PAR"   character varying(100),
    "PROJET_PDF"         bytea,
    "PROJET_DOCX"        bytea,
    CONSTRAINT t_attribution_pkey PRIMARY KEY ("ID_DMC", "LOT")
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_attribution_dossier ON public.t_attribution ("ID_DOSSIER") WHERE "ID_DOSSIER" IS NOT NULL;

UPDATE public.t_type_piece_jointe SET "CODE" = 'PROJET_MARCHE'
 WHERE "ID_TYPE_DOSSIER" = 'DDM' AND "LIBELLE_PIECE" = 'Projet de marché signé' AND "CODE" IS NULL
   AND NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe WHERE "CODE" = 'PROJET_MARCHE');
UPDATE public.t_type_piece_jointe SET "CODE" = 'CAHIER_CHARGES'
 WHERE "ID_TYPE_DOSSIER" = 'DDM' AND "LIBELLE_PIECE" = 'Cahier des charges' AND "CODE" IS NULL
   AND NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe WHERE "CODE" = 'CAHIER_CHARGES');
UPDATE public.t_type_piece_jointe SET "CODE" = 'DEVIS_ESTIMATIF'
 WHERE "ID_TYPE_DOSSIER" = 'DDM' AND "LIBELLE_PIECE" = 'Devis estimatif détaillé' AND "CODE" IS NULL
   AND NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe WHERE "CODE" = 'DEVIS_ESTIMATIF');
UPDATE public.t_type_piece_jointe SET "CODE" = 'PV_OUVERTURE'
 WHERE "ID_TYPE_DOSSIER" = 'DDM' AND "LIBELLE_PIECE" = 'Procès-verbal d''ouverture des offres' AND "CODE" IS NULL
   AND NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe WHERE "CODE" = 'PV_OUVERTURE');
UPDATE public.t_type_piece_jointe SET "CODE" = 'RAPPORT_ANALYSE'
 WHERE "ID_TYPE_DOSSIER" = 'DDM' AND "LIBELLE_PIECE" = 'Rapport d''analyse des offres' AND "CODE" IS NULL
   AND NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe WHERE "CODE" = 'RAPPORT_ANALYSE');
