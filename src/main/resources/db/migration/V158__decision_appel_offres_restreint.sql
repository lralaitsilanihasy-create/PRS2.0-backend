-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V158 — La décision autorisant le recours à l'appel d'offres restreint (demande front du 2026-11-02 `demande-backend-2026-11-02-
-- appel-offres-restreint.md`, §B5 ; manuel de procédures internes, § III.4 : projet de décision au DAOR ; § III.6 : décision signée et
-- lettres avec accusé au marché).
--
-- 1. DECISION_AO_RESTR (V95, « Décision autorisant l'appel d'offres restreint ») devient une pièce EXIGÉE des dossiers DAOR, DAORI et
--    DAOR-CC, au rang de la lettre d'invitation : son projet est produit par l'application à la validation de la fiche et joint d'office.
--    Au marché (MAOR, MAORI, MAOR-CC), la pièce était déjà exigée (V95, V152) : la version signée s'y dépose.
-- 2. DAOR-CC (V152, copié de DAOR avant V155) reçoit aussi la pièce facultative JUSTIFICATIF_AOR.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME")
SELECT t."ID_TYPE_PIECE", l."ID_SOUS_TYPE", true, l."ORDRE", NULL, NULL
FROM public.t_piece_sous_type l
JOIN public.t_type_piece_jointe p ON p."ID_TYPE_PIECE" = l."ID_TYPE_PIECE" AND p."CODE" = 'LETTRE_INVITATION'
JOIN public.t_type_piece_jointe t ON t."CODE" = 'DECISION_AO_RESTR'
WHERE l."ID_SOUS_TYPE" IN ('DAOR', 'DAORI', 'DAOR-CC')
  AND NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_TYPE_PIECE" = t."ID_TYPE_PIECE" AND a."ID_SOUS_TYPE" = l."ID_SOUS_TYPE");

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME")
SELECT t."ID_TYPE_PIECE", l."ID_SOUS_TYPE", false, l."ORDRE", NULL, NULL
FROM public.t_piece_sous_type l
JOIN public.t_type_piece_jointe p ON p."ID_TYPE_PIECE" = l."ID_TYPE_PIECE" AND p."CODE" = 'PV_LISTE_RESTREINTE'
JOIN public.t_type_piece_jointe t ON t."CODE" = 'JUSTIFICATIF_AOR'
WHERE l."ID_SOUS_TYPE" = 'DAOR-CC'
  AND NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_TYPE_PIECE" = t."ID_TYPE_PIECE" AND a."ID_SOUS_TYPE" = l."ID_SOUS_TYPE");
