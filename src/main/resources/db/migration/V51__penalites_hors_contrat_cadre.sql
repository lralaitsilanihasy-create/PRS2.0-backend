-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V51 — Le contrat-cadre ne pose plus la question de cadrage « pénalités » (demande front du 2026-09-28,
-- demande-backend-2026-09-28-contrat-cadre-modele-officiel.md §B10 ; modèle officiel ARMP 2019).
--
-- Le reflet B09-PR-01 « Régime des pénalités de retard » (clé de cadrage penalites) a été semé par V35 pour les trois
-- formes. En contrat-cadre, les pénalités se disent dans la rubrique B07-PE, qui sait écrire « fixées dans les marchés
-- subséquents » — ce que la question penalites (CCAG / plafond différent / non) ne permet pas. Le reflet perd donc
-- CONTRAT_CADRE : il n'est plus servi ni imprimé pour cette forme. Une réponse penalites encore présente dans un cadrage
-- reste acceptée (tolérance) ; rien ne l'exige. Le reste du lot (champs ajoutés, désactivés, retypés) passe par les
-- fichiers de correspondance et docs/referentiel/2026-09-28-contrat-cadre-modele-officiel.sql.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_champ_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE'
 WHERE "CODE" = 'B09-PR-01';
