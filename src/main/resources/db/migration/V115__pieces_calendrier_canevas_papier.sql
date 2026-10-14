-- ⚠️ 2026-10-14 (demande front « q-piece », §B2 et §B3 ; arbitrage du pilote du 14/10 sur Q-pièce) — deux pièces du dossier de
-- mise en concurrence dont la donnée est dans l'application ne sont plus demandées au dossier conduit en ligne (comme R2, V102) :
--   * CALENDRIER « Calendrier annuel de passation mis à jour et signé » : produit à partir du plan (GET …/calendrier-annuel),
--     dont la saisie complète est exigée à la soumission (V114) ;
--   * CANEVAS_RAPPORT « Canevas de rapport d'évaluation » : le rapport d'évaluation est produit par l'application (canevas par
--     sous-type à venir, modèles fournis par le pilote).
-- Un dossier saisi hors de l'application garde les deux pièces (H4 : « les listes des dossiers saisis ne changent pas »).

UPDATE public.t_piece_sous_type a SET "CONDITION" = 'PAPIER'
 FROM public.t_type_piece_jointe t
 WHERE t."ID_TYPE_PIECE" = a."ID_TYPE_PIECE"
   AND t."CODE" IN ('CALENDRIER', 'CANEVAS_RAPPORT');
