-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V90 — Évaluation des offres, lot 3 (prestations intellectuelles), tranche PI-d2b (demande front du 2026-10-07,
-- `demande-backend-2026-10-07-evaluation-pi.md`, §B7 : « dossier de marché, sous-type PI à fixer par le backend » ; arbitrage du
-- pilote du 2026-10-08 : un sous-type à part).
--
-- Le sous-type « MPI — Marché de Prestations Intellectuelles » de la famille DDM (dossier de marché) : le dossier de marché d'une
-- consultation PI s'y crée (AttributionService.SOUS_TYPE_PI) ; il hérite des points de contrôle communs à la famille DDM.
-- Rien si la famille DDM manque au référentiel. Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.tr_sous_type_dossier ("ID_SOUS_TYPE", "ID_TYPE_DOSSIER", "LIBELLE_SOUS_TYPE")
SELECT 'MPI', 'DDM', 'Marché de Prestations Intellectuelles'
WHERE EXISTS (SELECT 1 FROM public.tr_type_dossier WHERE "ID_TYPE_DOSSIER" = 'DDM')
  AND NOT EXISTS (SELECT 1 FROM public.tr_sous_type_dossier WHERE "ID_SOUS_TYPE" = 'MPI');
