-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V52 — Rubrique B02-VA « Offres variantes » des fournitures (demande front du 2026-09-29,
-- demande-backend-2026-09-29-lot-d2-fournitures.md §B2).
--
-- Le DPAO des fournitures (document type ARMP, lot D2) demande, quand les variantes sont autorisées, laquelle des deux
-- rédactions retenir : « Offre de base évaluée la moins-disante » ou « Toutes les offres conformes aux spécifications ».
-- Ses conditions citent le champ B02-VA-01, dont la rubrique n'existait pas. Elle se range juste après B02-LV « Lots et
-- variantes » (même rang, ordre par code). Le champ lui-même, comme les six autres champs du lot, vient du fichier de
-- correspondance des fournitures (import CSV) et de docs/referentiel/2026-09-29-lot-d2-champs-fournitures.sql.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.tr_rubrique_fiche_marche
    ("CODE", "CODE_BLOC", "CODE_COURT", "LIBELLE", "RANG", "DOCUMENT_MAITRE", "NB_ATTENDU", "TYPES_MARCHE", "CATEGORIES")
SELECT 'B02-VA', 'B02', 'VA', 'Offres variantes', 2, 'DPAO', 1, 'QUANTITE_FIXE,A_COMMANDE', 'FOURNITURES_SERVICES'
 WHERE NOT EXISTS (SELECT 1 FROM public.tr_rubrique_fiche_marche WHERE "CODE" = 'B02-VA');
