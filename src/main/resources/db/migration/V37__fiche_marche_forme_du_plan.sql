-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V37 — Le type de marché se déduit du plan (demande front du 2026-09-23, lot 1c, §B1)
--
-- Le type de marché de la fiche n'est plus une réponse du cadrage : il est dérivé de t_marche.FORME_MARCHE
-- de la ligne courante, à chaque lecture. Il rejoint donc les informations reprises du PPM : un 23e champ
-- de source PPM, B01-AC-19 « Forme du marché » (CLE_PPM = FORME_MARCHE), et la rubrique Acheteur attend
-- 19 champs au lieu de 18. Insertion idempotente (l'import CSV a pu le créer).
--
-- Aucune reprise des fiches : la clé typeMarche d'un cadrage déjà enregistré est ignorée à la lecture.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "REPRISES", "CLE_PPM")
SELECT 'B01-AC-19', 'B01-AC', 19, 'Forme du marché', 'TEXTE', 'PPM', 'DPAO', 'AE,CCAP', 'FORME_MARCHE'
WHERE NOT EXISTS (SELECT 1 FROM public.tr_champ_fiche_marche WHERE "CODE" = 'B01-AC-19');

UPDATE public.tr_rubrique_fiche_marche SET "NB_ATTENDU" = 19 WHERE "CODE" = 'B01-AC' AND "NB_ATTENDU" = 18;
