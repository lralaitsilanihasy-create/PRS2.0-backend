-- ⚠️ 2026-10-22 (demande front « modeles-dao », D0-D3 et D-ARMP) — les documents du DAO deviennent des modèles de document.
-- 1. Une nature nouvelle, DOCUMENT_DAO.
-- 2. Le rattachement se fait par SIGLE de document intégré (DPAO-F, AE-T, AVIS-F…) : un modèle au plus par sigle ; il sert toutes
--    les couvertures du sigle (formes, catégories), comme le fichier intégré.
-- 3. La validation du juriste (D-ARMP) : nom, qualité et date, enregistrés avec la version mise en vigueur qui s'écarte du document
--    type ; le motif est celui de la mise en vigueur.

alter table public.t_modele_pv drop constraint "t_modele_pv_NATURE_check";
alter table public.t_modele_pv add constraint "t_modele_pv_NATURE_check"
    check ("NATURE" in ('PV', 'LETTRE_RENVOI', 'PROJET_MARCHE', 'DOCUMENT_DAO'));

create table public.t_rattachement_sigle_modele_pv (
    "SIGLE"         varchar(20)   not null,
    "NATURE"        varchar(20)   not null check ("NATURE" in ('DOCUMENT_DAO')),
    "ID_MODELE"     bigint        not null references public.t_modele_pv ("ID_MODELE"),
    "RATTACHE_LE"   timestamp     not null,
    "RATTACHE_PAR"  varchar(100),
    primary key ("SIGLE", "NATURE")
);

create index idx_rattachement_sigle_modele_pv_modele on public.t_rattachement_sigle_modele_pv ("ID_MODELE");

comment on table public.t_rattachement_sigle_modele_pv is
    'Modèle de document du DAO qui remplace le fichier intégré d''un sigle (DPAO-F, AE-T, AVIS-F…), pour toutes ses couvertures.';

alter table public.t_version_modele_pv add column "JURISTE_NOM" varchar(150);
alter table public.t_version_modele_pv add column "JURISTE_QUALITE" varchar(150);
alter table public.t_version_modele_pv add column "JURISTE_DATE" date;

comment on column public.t_version_modele_pv."JURISTE_NOM" is
    'Juriste qui a validé une version s''écartant du document type de l''ARMP (saisi par l''Administrateur à la mise en vigueur).';
