-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V128 — ⚠️ 2026-10-17 : demande front « identite-dao-importe », §B2 (H1 : le serveur garde le verdict de chaque import).
--
-- t_import_verdict — le verdict d'identité rendu par la lecture d'un DAO (POST …/import), par fiche (DMC), empreinte du fichier et
-- utilisateur. L'application de l'import (PUT …/import/appliquer, PUT …/articles porteur de la trace) le relit : ETRANGER est refusé,
-- NON_PROUVE exige l'attestation de la PRMP, une lecture absente ou de plus de quatre heures est à refaire. Conservation courte : les
-- lignes de plus d'un jour sont purgées à chaque lecture. Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_import_verdict (
    "ID"           bigserial PRIMARY KEY,
    "ID_DMC"       bigint NOT NULL,
    "EMPREINTE"    character varying(64) NOT NULL,
    "UTILISATEUR"  character varying(100) NOT NULL,
    "VERDICT"      character varying(12) NOT NULL,
    "MOTIF"        character varying(2000),
    "LU_LE"        timestamp without time zone NOT NULL,
    CONSTRAINT uq_import_verdict UNIQUE ("ID_DMC", "EMPREINTE", "UTILISATEUR"),
    CONSTRAINT ck_import_verdict CHECK ("VERDICT" IN ('CONFORME', 'NON_PROUVE', 'ETRANGER'))
);

CREATE INDEX IF NOT EXISTS idx_import_verdict_lu_le ON public.t_import_verdict ("LU_LE");

COMMENT ON TABLE public.t_import_verdict IS 'Verdict d''identite d''un DAO lu a l''import (V128) : relu a l''application, conserve quatre heures.';
