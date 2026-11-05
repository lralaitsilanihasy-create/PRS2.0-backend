-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V165 — « Tout autre document » sur un plan de passation (demande front du 2026-11-04
-- `demande-backend-2026-11-04-document-complementaire-plan.md`, B1 ; décision du pilote du 04/11, suite à l'écart signalé sur V164).
--
-- Les sous-types de plan PPM et PPM-AGPM reçoivent une liste propre (t_piece_sous_type) d'une seule pièce : AUTRE_DOCUMENT (V130),
-- facultative, sans condition ; plusieurs fichiers admis (le dépôt l'admet déjà pour ce type). Depuis V164, la famille DDP ne porte plus
-- aucune pièce : cette liste ne masque donc rien, et les six types détachés restent détachés. Un plan sans pièce reste soumissible.
-- Idempotente ; rien pour un sous-type absent du référentiel.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME")
SELECT t."ID_TYPE_PIECE", s."ID_SOUS_TYPE", false, 1, NULL, NULL
FROM public.t_type_piece_jointe t
JOIN public.tr_sous_type_dossier s ON s."ID_SOUS_TYPE" IN ('PPM', 'PPM-AGPM')
WHERE t."CODE" = 'AUTRE_DOCUMENT'
  AND NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_TYPE_PIECE" = t."ID_TYPE_PIECE" AND a."ID_SOUS_TYPE" = s."ID_SOUS_TYPE");
