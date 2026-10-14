-- ⚠️ 2026-10-14 (demande front « q-piece », §B1 ; arbitrage du pilote du 14/10 : le calendrier annuel de passation est saisi
-- en entier dans le plan, toutes les étapes datées ; H2 : un processus du modèle peut être marqué « sans objet » par la PRMP).
-- Un processus sans objet ne porte aucune date et exige un motif ; il compte comme renseigné pour le calendrier complet.

alter table t_marche_prevision add column "SANS_OBJET" boolean not null default false;
alter table t_marche_prevision add column "MOTIF_SANS_OBJET" varchar(500);
alter table t_marche_prevision add constraint ck_marche_prevision_sans_objet check (
    not "SANS_OBJET" or ("DATE_DEBUT" is null and "DATE_FIN" is null and "MOTIF_SANS_OBJET" is not null));

-- L'archive de rectification fige l'état de la ligne : le marqueur et son motif y suivent.
alter table t_snapshot_rectif_prevision add column "SANS_OBJET" boolean not null default false;
alter table t_snapshot_rectif_prevision add column "MOTIF_SANS_OBJET" varchar(500);
