-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V125 — ⚠️ 2026-10-16 : demande front « marchés subséquents », lot MS-2 (la remise en concurrence et le marché subséquent ;
-- décret n° 2019-1064, art. 20-33 ; loi n° 2016-055, art. 33).
--
-- 1. t_remise_concurrence — une remise en concurrence des titulaires d'un contrat-cadre en vigueur (ou la complétude de l'offre
--    du titulaire unique) : sa lettre d'invitation (annexe 1 du document type ARMP), son échéance, puis l'évaluation de la CAO
--    (rapport simplifié, prix moyen du marché, proposition) et l'attribution de la PRMP à UN titulaire (art. 29, art. 20).
-- 2. t_remise_consulte — les titulaires consultés (tous : art. 21), la notification de l'invitation et le constat de l'absence de
--    réponse (art. 22).
-- 3. t_remise_reponse — la réponse d'un titulaire (devis, facture pro forma ou document convenu, art. 27), en ligne ou enregistrée
--    depuis le papier ; fermée jusqu'à l'échéance ; sa décision de conformité à l'évaluation.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_remise_concurrence (
    "ID"                 bigserial PRIMARY KEY,
    "ID_CONTRAT"         bigint NOT NULL REFERENCES public.t_contrat_vigueur ("ID"),
    "NUMERO"             integer NOT NULL,
    "MODE"               character varying(30) NOT NULL,
    "ETAT"               character varying(20) NOT NULL,
    "OBJET"              text NOT NULL,
    "SPECIFICATIONS"     text NOT NULL,
    "CRITERES"           text,
    "LIEU_LIVRAISON"     character varying(300),
    "DELAI_EXECUTION"    character varying(100) NOT NULL,
    "VALIDITE_JOURS"     integer,
    "VISITE"             text,
    "ECHEANCE"           timestamp without time zone NOT NULL,
    "LETTRE_PDF"         bytea,
    "LETTRE_DOCX"        bytea,
    "LANCEE_LE"          timestamp without time zone NOT NULL,
    "LANCEE_PAR"         character varying(100),
    "PRIX_MOYEN"         numeric(18,2),
    "ID_OFFRE_PROPOSEE"  character varying(36),
    "OBSERVATIONS"       text,
    "EVALUEE_LE"         timestamp without time zone,
    "EVALUEE_PAR"        character varying(100),
    "RAPPORT_PDF"        bytea,
    "RAPPORT_DOCX"       bytea,
    "ID_OFFRE_RETENUE"   character varying(36),
    "MONTANT_RETENU"     numeric(18,2),
    "MOTIF_DECISION"     text,
    "DECIDEE_LE"         timestamp without time zone,
    "DECIDEE_PAR"        character varying(100),
    CONSTRAINT uq_remise_numero UNIQUE ("ID_CONTRAT", "NUMERO"),
    CONSTRAINT ck_remise_mode CHECK ("MODE" IN ('REMISE_EN_CONCURRENCE', 'COMPLETUDE')),
    CONSTRAINT ck_remise_etat CHECK ("ETAT" IN ('LANCEE', 'EVALUEE', 'ATTRIBUEE', 'INFRUCTUEUSE'))
);

CREATE TABLE IF NOT EXISTS public.t_remise_consulte (
    "ID_REMISE"     bigint NOT NULL REFERENCES public.t_remise_concurrence ("ID"),
    "ID_OFFRE"      character varying(36) NOT NULL,
    "NOTIFIE_LE"    timestamp without time zone NOT NULL,
    "SANS_REPONSE"  boolean NOT NULL DEFAULT false,
    CONSTRAINT t_remise_consulte_pkey PRIMARY KEY ("ID_REMISE", "ID_OFFRE")
);

CREATE TABLE IF NOT EXISTS public.t_remise_reponse (
    "ID"            bigserial PRIMARY KEY,
    "ID_REMISE"     bigint NOT NULL REFERENCES public.t_remise_concurrence ("ID"),
    "ID_OFFRE"      character varying(36) NOT NULL,
    "NATURE"        character varying(30) NOT NULL,
    "VOIE"          character varying(10) NOT NULL,
    "MONTANT_HT"    numeric(18,2) NOT NULL,
    "MONTANT_TTC"   numeric(18,2),
    "DELAI"         character varying(100),
    "NOM"           character varying(255) NOT NULL,
    "FORMAT"        character varying(100) NOT NULL,
    "TAILLE"        bigint NOT NULL,
    "CONTENU"       bytea NOT NULL,
    "RECUE_LE"      timestamp without time zone NOT NULL,
    "DEPOSEE_PAR"   character varying(100),
    "CONFORME"      boolean,
    "MOTIF"         text,
    CONSTRAINT uq_remise_reponse_offre UNIQUE ("ID_REMISE", "ID_OFFRE"),
    CONSTRAINT ck_remise_reponse_nature CHECK ("NATURE" IN ('DEVIS', 'FACTURE_PRO_FORMA', 'DOCUMENT_CONVENU')),
    CONSTRAINT ck_remise_reponse_voie CHECK ("VOIE" IN ('EN_LIGNE', 'PAPIER'))
);
