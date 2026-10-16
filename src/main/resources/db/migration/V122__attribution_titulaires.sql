-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V122 — ⚠️ 2026-10-15 : demande front « marchés subséquents », tranche MT-b1 (plusieurs titulaires : l'attribution ;
-- réponses du pilote du 15/10 ; décret n° 2019-1064, art. 13-15, 18).
--
-- Un contrat-cadre multi-attributaire a plusieurs titulaires par lot : la PRMP les retient parmi les qualifiés que la commission
-- propose (art. 13-14), tout ou partie, dans la limite d'un nombre maximal facultatif du DAO. Une ligne par titulaire retenu, avec
-- les valeurs de son offre figées au choix. t_attribution garde l'attribution du lot (son cycle de vie) ; ID_OFFRE_ATTRIBUEE y vaut
-- le premier titulaire retenu, dans l'ordre du classement. Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_attribution_titulaire (
    "ID_DMC"       bigint NOT NULL,
    "LOT"          integer NOT NULL,
    "ID_OFFRE"     character varying(36) NOT NULL,
    "RANG"         integer,
    "MONTANT"      numeric(18,2),
    "MONTANT_TTC"  numeric(18,2),
    "DELAI"        character varying(50),
    "RETENU_LE"    timestamp without time zone NOT NULL,
    "RETENU_PAR"   character varying(100),
    CONSTRAINT t_attribution_titulaire_pkey PRIMARY KEY ("ID_DMC", "LOT", "ID_OFFRE"),
    CONSTRAINT fk_attribution_titulaire_lot FOREIGN KEY ("ID_DMC", "LOT") REFERENCES public.t_attribution ("ID_DMC", "LOT")
);
