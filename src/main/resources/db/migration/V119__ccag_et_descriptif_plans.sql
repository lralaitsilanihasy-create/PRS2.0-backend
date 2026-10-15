-- ⚠️ 2026-10-15 (demande front « ccag-descriptif-travaux » ; décisions du pilote du 15/10 ; arbitrage de l'utilisateur au backend le
-- 15/10 : pièce du dossier, servie aux candidats).
--
-- §B1 — Le CCAG n'est plus une pièce exigée : il est recopié en partie 2.3 du DAO complet (texte fixe de l'ARMP de la catégorie,
-- depuis le 06/10). La liste de la famille DMC le garde, facultatif ; un dossier qui l'avait joint le garde. Aucun sous-type ne
-- l'associe (V95).
UPDATE public.t_type_piece_jointe SET "OBLIGATOIRE" = false WHERE "CODE" = 'CCAG';
UPDATE public.t_piece_sous_type a SET "OBLIGATOIRE" = false
  FROM public.t_type_piece_jointe t
 WHERE t."ID_TYPE_PIECE" = a."ID_TYPE_PIECE" AND t."CODE" = 'CCAG';

-- §B3 — Le descriptif technique et les plans d'un marché de travaux : une vraie pièce, exigée pour la catégorie TRAVAUX des sous-types
-- d'appel d'offres (pas de condition PAPIER : le dossier produit par la fiche ne se soumet pas sans elle). Plusieurs fichiers admis
-- (le descriptif, chaque plan) ; ils sont servis aux candidats avec les documents du DAO.
INSERT INTO public.t_type_piece_jointe ("ID_TYPE_PIECE", "ID_TYPE_DOSSIER", "LIBELLE_PIECE", "OBLIGATOIRE", "ORDRE", "CODE")
SELECT (SELECT COALESCE(MAX("ID_TYPE_PIECE"), 0) + 1 FROM public.t_type_piece_jointe), NULL,
       'Descriptif technique et plans des travaux', false, NULL, 'DESCRIPTIF_PLANS'
 WHERE NOT EXISTS (SELECT 1 FROM public.t_type_piece_jointe WHERE "CODE" = 'DESCRIPTIF_PLANS');

SELECT setval(pg_get_serial_sequence('public.t_type_piece_jointe', 'ID_TYPE_PIECE'),
              GREATEST((SELECT COALESCE(MAX("ID_TYPE_PIECE"), 0) FROM public.t_type_piece_jointe), 1));

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME")
SELECT t."ID_TYPE_PIECE", s."ID_SOUS_TYPE", true,
       (SELECT COALESCE(MAX(a."ORDRE"), 0) + 1 FROM public.t_piece_sous_type a WHERE a."ID_SOUS_TYPE" = s."ID_SOUS_TYPE"),
       'TRAVAUX', NULL
  FROM public.t_type_piece_jointe t, public.tr_sous_type_dossier s
 WHERE t."CODE" = 'DESCRIPTIF_PLANS'
   AND s."ID_SOUS_TYPE" IN ('DAOO', 'DAOOI', 'DAOOPREQUAL', 'DAOR', 'DAORI')
   AND NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type x WHERE x."ID_TYPE_PIECE" = t."ID_TYPE_PIECE" AND x."ID_SOUS_TYPE" = s."ID_SOUS_TYPE");
