-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V127 — ⚠️ 2026-10-16 : demande front « marchés subséquents », lot MS-4 (la réception ; loi n° 2016-055, art. 13 ; CCAG
-- fournitures, art. 22 ; réponse Q1 du pilote du 15/10 : la commission de réception est désignée par décision de la PRMP).
--
-- 1. t_commission_reception — la commission de réception d'un contrat en vigueur : la décision de la PRMP (référence, date) et sa
--    présidence (la PRMP elle-même, ou un membre qui la représente).
-- 2. t_commission_reception_membre — ses membres, chacun avec son compte (comptes K… des commissions, invités par courriel).
-- 3. t_reception_commande — une réception d'un bon de commande : provisoire (après la livraison déclarée) ou définitive (au terme du
--    délai de garantie) ; la décision de la commission (réception, ajournement, réfaction, rejet), le retard et la pénalité
--    indicative, le PV.
-- 4. t_reception_signature — les signatures du PV, une par membre (et par la PRMP qui préside).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_commission_reception (
    "ID_CONTRAT"           bigint PRIMARY KEY REFERENCES public.t_contrat_vigueur ("ID"),
    "DECISION_REFERENCE"   character varying(100) NOT NULL,
    "DECISION_DATE"        date NOT NULL,
    "PRESIDEE_PAR_PRMP"    boolean NOT NULL,
    "MAJ_LE"               timestamp without time zone NOT NULL,
    "MAJ_PAR"              character varying(100)
);

CREATE TABLE IF NOT EXISTS public.t_commission_reception_membre (
    "ID"                   bigserial PRIMARY KEY,
    "ID_CONTRAT"           bigint NOT NULL REFERENCES public.t_commission_reception ("ID_CONTRAT"),
    "RANG"                 integer NOT NULL,
    "NOM"                  character varying(100) NOT NULL,
    "PRENOM"               character varying(100) NOT NULL,
    "EMAIL"                character varying(255) NOT NULL,
    "FONCTION"             character varying(200),
    "PRESIDENT"            boolean NOT NULL DEFAULT false,
    "ID_COMPTE"            character varying(20) NOT NULL,
    CONSTRAINT uq_commission_reception_compte UNIQUE ("ID_CONTRAT", "ID_COMPTE")
);

CREATE TABLE IF NOT EXISTS public.t_reception_commande (
    "ID"                   bigserial PRIMARY KEY,
    "ID_BON"               bigint NOT NULL REFERENCES public.t_bon_commande ("ID"),
    "TYPE"                 character varying(20) NOT NULL,
    "ETAT"                 character varying(20) NOT NULL,
    "DATE_LIVRAISON"       date,
    "LIVRAISON_OBSERVATIONS" text,
    "DECLAREE_LE"          timestamp without time zone NOT NULL,
    "DECLAREE_PAR"         character varying(100),
    "DECISION"             character varying(20),
    "DATE_RECEPTION"       date,
    "MOTIF"                text,
    "RESERVES"             text,
    "MONTANT_REFACTION"    numeric(18,2),
    "JOURS_RETARD"         integer,
    "TAUX_PENALITE"        numeric(9,4),
    "PENALITE_INDICATIVE"  numeric(18,2),
    "PV_PDF"               bytea,
    "PV_DOCX"              bytea,
    "ETABLI_LE"            timestamp without time zone,
    "ETABLI_PAR"           character varying(100),
    "SIGNE_LE"             timestamp without time zone,
    CONSTRAINT ck_reception_type CHECK ("TYPE" IN ('PROVISOIRE', 'DEFINITIVE')),
    CONSTRAINT ck_reception_etat CHECK ("ETAT" IN ('A_ETABLIR', 'PV_ETABLI', 'SIGNE')),
    CONSTRAINT ck_reception_decision CHECK ("DECISION" IS NULL OR "DECISION" IN ('RECEPTION', 'AJOURNEMENT', 'REFACTION', 'REJET'))
);

CREATE INDEX IF NOT EXISTS ix_reception_commande_bon ON public.t_reception_commande ("ID_BON");

CREATE TABLE IF NOT EXISTS public.t_reception_signature (
    "ID_RECEPTION"         bigint NOT NULL REFERENCES public.t_reception_commande ("ID"),
    "SIGNATAIRE"           character varying(100) NOT NULL,
    "NOM"                  character varying(220),
    "PRESIDENT"            boolean NOT NULL DEFAULT false,
    "SIGNE_LE"             timestamp without time zone NOT NULL,
    PRIMARY KEY ("ID_RECEPTION", "SIGNATAIRE")
);
