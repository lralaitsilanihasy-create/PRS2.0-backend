-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V88 — Évaluation des offres, lot 3 (prestations intellectuelles), tranche PI-d1 (demande front du 2026-10-07,
-- `demande-backend-2026-10-07-evaluation-pi.md`, §B3 ; arbitrage du pilote Q1 : en « qualité technique exclusivement », seule
-- l'enveloppe financière du premier classé s'ouvre).
--
-- La seconde séance d'ouverture : après l'arrêt de l'évaluation technique de chaque lot, le responsable l'ouvre ; elle n'ouvre que les
-- enveloppes FINANCIERES des propositions qualifiées techniquement (ou du seul premier classé, selon la méthode) ; les autres ne sont
-- jamais ouvertes. Les détenteurs apportent les parts de ces seules enveloppes, avec les mêmes clés de la cérémonie ; au quorum, elles
-- s'ouvrent ensemble ; le PV lit les notes techniques et les montants.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_seance_financiere (
    "ID_DMC"           bigint NOT NULL,
    "ETAT"             character varying(12) NOT NULL,
    "METHODE"          character varying(200),
    "A_OUVRIR"         text NOT NULL,
    "OUVERTE_LE"       timestamp without time zone NOT NULL,
    "OUVERTE_PAR"      character varying(100),
    "DECHIFFREE_LE"    timestamp without time zone,
    "PRESENTS"         character varying(1000),
    "AUTRES"           text,
    "SECOURS_EMPLOYE"  boolean NOT NULL DEFAULT false,
    "SECOURS_MOTIF"    text,
    "OBSERVATIONS"     text,
    "CLOSE_LE"         timestamp without time zone,
    "PV"               bytea,
    "PV_DOCX"          bytea,
    CONSTRAINT t_seance_financiere_pkey PRIMARY KEY ("ID_DMC"),
    CONSTRAINT ck_seance_financiere_etat CHECK ("ETAT" IN ('OUVERTE', 'DECHIFFREE', 'CLOSE'))
);
