-- ⚠️ 2026-10-06 — DAO complet, contre-recette du même jour (frontend/docs/demande-backend-2026-10-06-dao-complet.md, §D) :
-- D1 (ministère imprimé deux fois sur la page de garde) et D2 (styles du sommaire hétérogènes) changent le gabarit. Comme V74,
-- les DAO complets déjà produits et non joints à un dossier sont retirés ; la tâche de rattrapage (DaoCompletRattrapage) les
-- reproduit. Les autres documents des versions validées (DPAO, AE, CCAP…) ne sont pas touchés : ils restent ceux de la validation.
DELETE FROM public.t_document_fiche_marche d
 WHERE d."TYPE" = 'DAO_COMPLET'
   AND NOT EXISTS (SELECT 1 FROM public.t_piece_jointe_dossier p WHERE p."ID_DOCUMENT_FICHE" = d."ID_DOCUMENT");
