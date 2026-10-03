-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V62 — Les pièces de l'offre ouvertes aux fournitures (demande front du 2026-10-03,
-- demande-backend-2026-10-03-pieces-offre-fournitures.md, choix A du pilote).
--
-- Le bloc B14 « Pièces de l'offre » et ses rubriques B14-AD / B14-OF sont servis aux fournitures et services, en
-- quantité fixe et à commande. Le contrat-cadre en sort, pour les fournitures (H1) comme pour les travaux : aucun DPAC
-- n'a de place pour ces pièces (ni B04-CO-01, ni B04-PI-01, ni B03-CQ-01), et PUT /pieces y répond 409.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_bloc_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE',
       "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX' WHERE "CODE" = 'B14';
UPDATE public.tr_rubrique_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE',
       "CATEGORIES" = 'FOURNITURES_SERVICES,TRAVAUX' WHERE "CODE" IN ('B14-AD', 'B14-OF');
