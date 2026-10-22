-- ⚠️ 2026-10-22 (demande front « modeles-documents-marche », lot M1) — le projet de marché devient un modèle de document.
-- 1. Une nature nouvelle, PROJET_MARCHE, pour les modèles (à côté de PV et LETTRE_RENVOI).
-- 2. Son rattachement se fait par CATÉGORIE de procédure (arbitrage Q1 du pilote : un modèle par catégorie), pas par sous-type :
--    une table à part, un modèle au plus par catégorie et par nature.

alter table public.t_modele_pv drop constraint "t_modele_pv_NATURE_check";
alter table public.t_modele_pv add constraint "t_modele_pv_NATURE_check"
    check ("NATURE" in ('PV', 'LETTRE_RENVOI', 'PROJET_MARCHE'));

create table public.t_rattachement_categorie_modele_pv (
    "CATEGORIE"     varchar(40)   not null check ("CATEGORIE" in ('FOURNITURES_SERVICES', 'TRAVAUX', 'PRESTATIONS_INTELLECTUELLES')),
    "NATURE"        varchar(20)   not null check ("NATURE" in ('PROJET_MARCHE')),
    "ID_MODELE"     bigint        not null references public.t_modele_pv ("ID_MODELE"),
    "RATTACHE_LE"   timestamp     not null,
    "RATTACHE_PAR"  varchar(100),
    primary key ("CATEGORIE", "NATURE")
);

create index idx_rattachement_categorie_modele_pv_modele on public.t_rattachement_categorie_modele_pv ("ID_MODELE");

comment on table public.t_rattachement_categorie_modele_pv is
    'Modèle de projet de marché qui sert une catégorie de procédure (fournitures et services, travaux, prestations intellectuelles).';
