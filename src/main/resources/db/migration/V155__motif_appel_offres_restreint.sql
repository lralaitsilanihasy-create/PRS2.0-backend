-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V155 — Le motif du recours à l'appel d'offres restreint (demande front du 2026-11-02 `demande-backend-2026-11-02-appel-offres-
-- restreint.md`, §B2 ; loi n° 2016-055, art. 38-IV ; décret n° 2019-1310, art. 10 ; fiche de présentation du manuel, ligne
-- « Justification (si DAOR) »).
--
-- 1. t_fiche_motif_restreint : le motif (l'un des quatre cas) et sa justification, par VERSION de fiche (une ligne au plus), comme les
--    sous-critères : saisis en brouillon, figés à la validation, copiés à la révision. Exigés au bilan d'une fiche dont la ligne du
--    plan est en mode restreint (contrôle bloquant MOTIF_RESTREINT).
-- 2. Les pièces justificatives, facultatives : un type de pièce JUSTIFICATIF_AOR ajouté aux listes DAOR et DAORI, après le PV de
--    validation de la liste restreinte ; un dossier peut en porter plusieurs fichiers.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_fiche_motif_restreint (
    "ID_FICHE"       integer NOT NULL,
    "MOTIF"          character varying(30) NOT NULL,
    "JUSTIFICATION"  character varying(4000) NOT NULL,
    "SAISI_LE"       timestamp without time zone NOT NULL,
    "SAISI_PAR"      character varying(50),
    CONSTRAINT t_fiche_motif_restreint_pkey PRIMARY KEY ("ID_FICHE"),
    CONSTRAINT fk_fiche_motif_restreint_fiche FOREIGN KEY ("ID_FICHE") REFERENCES public.t_fiche_marche ("ID_FICHE") ON DELETE CASCADE,
    CONSTRAINT ck_fiche_motif_restreint CHECK ("MOTIF" IN ('URGENCE_AVEREE', 'CONFIDENTIEL', 'PRESTATAIRE_DEFAILLANT', 'PETIT_NOMBRE'))
);

INSERT INTO public.t_type_piece_jointe ("ID_TYPE_PIECE", "ID_TYPE_DOSSIER", "LIBELLE_PIECE", "OBLIGATOIRE", "ORDRE", "CODE")
SELECT COALESCE(MAX("ID_TYPE_PIECE"), 0) + 1, NULL, 'Pièces justificatives du recours à l''appel d''offres restreint', false, NULL,
       'JUSTIFICATIF_AOR'
FROM public.t_type_piece_jointe
HAVING NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe t WHERE t."CODE" = 'JUSTIFICATIF_AOR');

SELECT setval(pg_get_serial_sequence('public.t_type_piece_jointe', 'ID_TYPE_PIECE'),
              GREATEST((SELECT COALESCE(MAX("ID_TYPE_PIECE"), 0) FROM public.t_type_piece_jointe), 1));

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME")
SELECT t."ID_TYPE_PIECE", l."ID_SOUS_TYPE", false, l."ORDRE", NULL, NULL
FROM public.t_piece_sous_type l
JOIN public.t_type_piece_jointe p ON p."ID_TYPE_PIECE" = l."ID_TYPE_PIECE" AND p."CODE" = 'PV_LISTE_RESTREINTE'
JOIN public.t_type_piece_jointe t ON t."CODE" = 'JUSTIFICATIF_AOR'
WHERE l."ID_SOUS_TYPE" IN ('DAOR', 'DAORI')
  AND NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_TYPE_PIECE" = t."ID_TYPE_PIECE" AND a."ID_SOUS_TYPE" = l."ID_SOUS_TYPE");
