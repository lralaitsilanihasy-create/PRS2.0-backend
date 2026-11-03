-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V163 — La ligne du plan d'un dossier créé hors fiche (demande front du 2026-11-03 `demande-backend-2026-11-03-chaine-par-ligne.md`,
-- B2) : un dossier DMC (rapport justificatif RJ…) ou DDM (marché de gré à gré MGG…) saisi par « Créer un dossier » peut être rattaché
-- à la ligne du plan qu'il réalise, pour entrer dans la filiation du plan sous la version qui porte cette ligne.
-- Facultative, posée à la création seulement (contrôlée par le service) ; nulle pour les dossiers existants et ceux nés d'une fiche.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_dossier ADD COLUMN IF NOT EXISTS "ID_DETAIL_ORIGINE" integer;
CREATE INDEX IF NOT EXISTS ix_dossier_detail_origine ON public.t_dossier ("ID_DETAIL_ORIGINE") WHERE "ID_DETAIL_ORIGINE" IS NOT NULL;
