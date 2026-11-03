-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V161 — L'appel à candidatures en ligne de l'appel d'offres restreint (demande front du 2026-11-02 `demande-backend-2026-11-02-
-- appel-offres-restreint.md`, §B4 ; arbitrage Q2 du pilote ; décret n° 2019-1310, art. 11-1).
--
-- L'appel à candidatures reprend le chemin de l'AMI en ligne (t_ami et ses tables : avis, dépôt, présélection, liste, rapport signé).
-- t_ami.NATURE dit lequel : AMI (prestations intellectuelles, l'existant) ou APPEL_CANDIDATURES (appel d'offres restreint de
-- fournitures ou de travaux), fixée à la création selon la catégorie de la fiche. Les lignes existantes restent des AMI.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_ami ADD COLUMN IF NOT EXISTS "NATURE" character varying(20) NOT NULL DEFAULT 'AMI';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_ami_nature') THEN
        ALTER TABLE public.t_ami ADD CONSTRAINT ck_ami_nature CHECK ("NATURE" IN ('AMI', 'APPEL_CANDIDATURES'));
    END IF;
END $$;
