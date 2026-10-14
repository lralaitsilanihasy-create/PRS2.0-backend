-- ⚠️ 2026-10-14 (demande front « q11-prix-estimatifs », §B1 ; arbitrage du pilote du 14/10 sur Q11, manuel révisé §23) — le prix
-- unitaire estimé (HT, en ariary) de chaque article du besoin d'une fiche DAO (bordereau des fournitures, devis quantitatif des
-- travaux). Facultatif à l'écriture (un brouillon se saisit en plusieurs fois) ; exigé à la validation de la fiche
-- (PRIX_ESTIME_MANQUANT), et le total de chaque lot égale le montant du lot au plan (ESTIMATION_ECART). Jamais publié.
-- Une fiche validée avant reste valide (H5) : ses articles n'ont pas de prix, sa prochaine révision les exige.

alter table public.t_fiche_article add column "PRIX_UNITAIRE_ESTIME" numeric(17, 2);
alter table public.t_fiche_article add constraint ck_fiche_article_prix_estime check ("PRIX_UNITAIRE_ESTIME" is null or "PRIX_UNITAIRE_ESTIME" > 0);
