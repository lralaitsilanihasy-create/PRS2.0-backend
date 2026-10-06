-- ⚠️ 2026-10-06 — DAO complet, constats de recette C1 à C5 (frontend/docs/demande-backend-2026-10-06-dao-complet.md, §C) :
-- la page de garde, les couvertures des textes fixes, le plan de l'ARMP et le nom du fichier changent. Les DAO complets
-- produits avant ce correctif (premier rattrapage du 06/10) sont retirés pour être reproduits par la tâche de rattrapage
-- (DaoCompletRattrapage), sur le nouveau gabarit. Un document DAO complet déjà joint comme pièce d'un dossier reste en
-- place : la pièce soumise ne change pas. Aucune donnée saisie n'est touchée : ces documents sont dérivés de la fiche.
DELETE FROM public.t_document_fiche_marche d
 WHERE d."TYPE" = 'DAO_COMPLET'
   AND NOT EXISTS (SELECT 1 FROM public.t_piece_jointe_dossier p WHERE p."ID_DOCUMENT_FICHE" = d."ID_DOCUMENT");
