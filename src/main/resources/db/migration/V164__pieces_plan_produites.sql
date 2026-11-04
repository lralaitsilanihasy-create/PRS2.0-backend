-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V164 — Les pièces d'un plan de passation : ne plus proposer ce que l'application produit (demande front du 2026-11-04
-- `demande-backend-2026-11-04-pieces-plan.md`, B1 ; décision du pilote du 04/11 : retrait à la source, pas par un filtre de l'écran).
--
-- Les six types de la famille DDP, tous facultatifs, sont DÉTACHÉS de la famille (ID_TYPE_DOSSIER nul, comme la bibliothèque des
-- pièces du manuel) : la liste d'un plan (sous-types PPM, PPM-AGPM, sans liste propre) ne les propose plus.
--   1 Projet de PPM (le plan saisi lui-même) · 2 Fiche de présentation (calculée des lignes) · 5 Journal de publication de l'AGPM
--   (déposé une fois sur le plan validé, V118) · 21 AGPM (dérivé du plan) · 22 PV du dossier précédent · 23 PPM antérieur (constitués
--   par l'application à la soumission d'une mise à jour).
-- Rien n'est supprimé : les types restent, les pièces déjà jointes restent lisibles, et la garde des versions (MiseAJourPpmService)
-- continue d'écrire les pièces 22 et 23 par leur identifiant.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.t_type_piece_jointe
SET "ID_TYPE_DOSSIER" = NULL
WHERE "ID_TYPE_DOSSIER" = 'DDP' AND "ID_TYPE_PIECE" IN (1, 2, 5, 21, 22, 23);
