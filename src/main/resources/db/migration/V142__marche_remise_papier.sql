-- ⚠️ 2026-10-27 (demande front « dossier-marche-remise-papier », P2) — le dossier de marché d'une procédure à remise papier, rattaché
-- à sa procédure : il naît d'un lot de la fiche (t_attribution, comme un dossier né de l'attribution), sans évaluation ni offre dans
-- l'application. La ligne le dit : la séance, l'évaluation et la signature du marché ont eu lieu hors de l'application (un acte de
-- gestion ne l'attend donc pas signé ici).

ALTER TABLE public.t_attribution ADD COLUMN IF NOT EXISTS "REMISE_PAPIER" boolean NOT NULL DEFAULT false;
