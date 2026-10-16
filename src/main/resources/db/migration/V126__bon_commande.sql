-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V126 — ⚠️ 2026-10-16 : demande front « marchés subséquents », lot MS-3 (les bons de commande ; loi n° 2016-055, définition de
-- l'accord-cadre et art. 30 ; décret n° 2019-1064, art. 8, 33, 34).
--
-- 1. t_bon_commande — un bon de commande d'un contrat en vigueur, numéroté par contrat : le bon « réglementaire » du marché
--    subséquent d'un contrat-cadre (ID_REMISE : la remise attribuée, son titulaire retenu, aux prix de son offre), ou un bon du
--    marché à commandes (aux prix du bordereau). Émis et notifié par la PRMP ; l'accusé de réception du titulaire ; l'annulation
--    motivée (un bon annulé ne compte plus dans les cumuls).
-- 2. t_bon_commande_ligne — ses articles : désignation, unité, quantité, prix unitaire HT (repris du bordereau scellé quand il est
--    lisible : PRIX_DU_BORDEREAU), montant.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_bon_commande (
    "ID"                 bigserial PRIMARY KEY,
    "ID_CONTRAT"         bigint NOT NULL REFERENCES public.t_contrat_vigueur ("ID"),
    "NUMERO"             integer NOT NULL,
    "ID_REMISE"          bigint REFERENCES public.t_remise_concurrence ("ID"),
    "ID_OFFRE"           character varying(36) NOT NULL,
    "ETAT"               character varying(20) NOT NULL,
    "OBJET"              text NOT NULL,
    "LIEU_LIVRAISON"     character varying(300),
    "DELAI_LIVRAISON"    character varying(100) NOT NULL,
    "OBSERVATIONS"       text,
    "MONTANT_HT"         numeric(18,2) NOT NULL,
    "EMIS_LE"            timestamp without time zone NOT NULL,
    "EMIS_PAR"           character varying(100),
    "PDF"                bytea,
    "DOCX"               bytea,
    "ACCUSE_LE"          timestamp without time zone,
    "ANNULE_LE"          timestamp without time zone,
    "ANNULE_PAR"         character varying(100),
    "MOTIF_ANNULATION"   text,
    CONSTRAINT uq_bon_commande_numero UNIQUE ("ID_CONTRAT", "NUMERO"),
    CONSTRAINT ck_bon_commande_etat CHECK ("ETAT" IN ('EMIS', 'ANNULE'))
);

CREATE INDEX IF NOT EXISTS ix_bon_commande_offre ON public.t_bon_commande ("ID_OFFRE");

CREATE TABLE IF NOT EXISTS public.t_bon_commande_ligne (
    "ID"                 bigserial PRIMARY KEY,
    "ID_BON"             bigint NOT NULL REFERENCES public.t_bon_commande ("ID"),
    "RANG"               integer NOT NULL,
    "ID_ARTICLE"         integer,
    "DESIGNATION"        text NOT NULL,
    "UNITE"              character varying(50),
    "QUANTITE"           numeric(18,3) NOT NULL,
    "PRIX_UNITAIRE"      numeric(18,2) NOT NULL,
    "MONTANT"            numeric(18,2) NOT NULL,
    "PRIX_DU_BORDEREAU"  boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS ix_bon_commande_ligne_bon ON public.t_bon_commande_ligne ("ID_BON");
