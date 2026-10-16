-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V124 — ⚠️ 2026-10-16 : demande front « marchés subséquents », lot MS-1 (le contrat-cadre ou le marché à commandes en
-- vigueur ; décret n° 2019-1064, art. 8, 18, 19, 34 ; loi n° 2016-055, art. 30).
--
-- 1. t_contrat_vigueur — un par lot notifié d'un contrat-cadre ou d'un marché à commandes : ce qui le régit une fois conclu,
--    figé depuis la dernière version validée de la fiche à la notification (période de validité, durée maximale, reconductions,
--    préavis, montants, forme et rythme des marchés subséquents). Sa liste de titulaires est celle de l'attribution, fermée
--    (art. 18 : aucun nouvel opérateur).
-- 2. t_contrat_reconduction — chaque décision écrite de l'autorité contractante de reconduire ou non (AE-CC, art. 7.3).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_contrat_vigueur (
    "ID"                  bigserial PRIMARY KEY,
    "ID_DMC"              bigint NOT NULL,
    "LOT"                 integer NOT NULL,
    "TYPE_MARCHE"         character varying(20) NOT NULL,
    "DATE_EFFET"          date NOT NULL,
    "DATE_FIN"            date NOT NULL,
    "PERIODE_MOIS"        integer,
    "DUREE_MAX_MOIS"      integer,
    "RECONDUCTIBLE"       boolean NOT NULL DEFAULT false,
    "RECONDUCTIONS_MAX"   integer,
    "RECONDUCTIONS"       integer NOT NULL DEFAULT 0,
    "PREAVIS_MOIS"        integer,
    "MONTANT_ESTIME"      numeric(18,2),
    "MONTANT_MINIMUM"     numeric(18,2),
    "MONTANT_MAXIMUM"     numeric(18,2),
    "FORME_SUBSEQUENTS"   character varying(200),
    "RYTHME"              character varying(200),
    "CALENDRIER"          text,
    "CREE_LE"             timestamp without time zone NOT NULL,
    CONSTRAINT uq_contrat_vigueur_lot UNIQUE ("ID_DMC", "LOT"),
    CONSTRAINT ck_contrat_vigueur_type CHECK ("TYPE_MARCHE" IN ('CONTRAT_CADRE', 'A_COMMANDE'))
);

CREATE TABLE IF NOT EXISTS public.t_contrat_reconduction (
    "ID"               bigserial PRIMARY KEY,
    "ID_CONTRAT"       bigint NOT NULL REFERENCES public.t_contrat_vigueur ("ID"),
    "DECISION"         character varying(20) NOT NULL,
    "REFERENCE"        character varying(100) NOT NULL,
    "DATE_DECISION"    date NOT NULL,
    "FIN_AVANT"        date NOT NULL,
    "NOUVELLE_FIN"     date,
    "HORS_PREAVIS"     boolean NOT NULL DEFAULT false,
    "LE"               timestamp without time zone NOT NULL,
    "PAR"              character varying(100),
    CONSTRAINT ck_contrat_reconduction_decision CHECK ("DECISION" IN ('RECONDUIRE', 'NE_PAS_RECONDUIRE'))
);
CREATE INDEX IF NOT EXISTS idx_contrat_reconduction_contrat ON public.t_contrat_reconduction ("ID_CONTRAT");
