-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V130 — « Tout autre document utile » (demande front du 2026-10-17 `demande-backend-2026-10-17-canevas-rapport-evaluation.md`,
-- §B3 ; manuel de contrôle a priori : chaque liste de pièces finit par « tout autre document que la PRMP estime utile »).
--
-- Un type de pièce AUTRE_DOCUMENT, facultatif, ajouté en dernier à chaque liste de sous-type (t_piece_sous_type) : téléversé, un
-- dossier peut en porter plusieurs fichiers (le dépôt l'admet déjà), le nom du fichier tient lieu d'intitulé. Sans condition : il
-- vaut en ligne comme sur papier, pour toute catégorie et toute forme. Les sous-types sans liste propre (pièces de leur famille)
-- ne changent pas.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.t_type_piece_jointe ("ID_TYPE_PIECE", "ID_TYPE_DOSSIER", "LIBELLE_PIECE", "OBLIGATOIRE", "ORDRE", "CODE")
SELECT COALESCE(MAX("ID_TYPE_PIECE"), 0) + 1, NULL, 'Tout autre document utile à l''appréciation du dossier', false, NULL, 'AUTRE_DOCUMENT'
FROM public.t_type_piece_jointe
HAVING NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe t WHERE t."CODE" = 'AUTRE_DOCUMENT');

SELECT setval(pg_get_serial_sequence('public.t_type_piece_jointe', 'ID_TYPE_PIECE'),
              GREATEST((SELECT COALESCE(MAX("ID_TYPE_PIECE"), 0) FROM public.t_type_piece_jointe), 1));

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME")
SELECT t."ID_TYPE_PIECE", l."ID_SOUS_TYPE", false, l.dernier + 1, NULL, NULL
FROM (SELECT "ID_SOUS_TYPE", MAX("ORDRE") AS dernier FROM public.t_piece_sous_type GROUP BY "ID_SOUS_TYPE") l
JOIN public.t_type_piece_jointe t ON t."CODE" = 'AUTRE_DOCUMENT'
WHERE NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_TYPE_PIECE" = t."ID_TYPE_PIECE" AND a."ID_SOUS_TYPE" = l."ID_SOUS_TYPE");
