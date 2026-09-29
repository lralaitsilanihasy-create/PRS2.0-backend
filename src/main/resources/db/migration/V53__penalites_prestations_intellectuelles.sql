-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V53 — La question de cadrage « pénalités » posée aux prestations intellectuelles (demande front du 2026-09-29,
-- demande-backend-2026-09-29-lot-d3-prestations-intellectuelles.md §B2.2.5, arbitrage Q7).
--
-- Le CPS des prestations intellectuelles (lot D3) choisit sa rédaction de l'article 16 sur la clé de cadrage penalites
-- (NON / CCAG / PLAFOND_DIFFERENT), comme les fournitures. Le reflet B09-PR-01 « Régime des pénalités de retard », semé
-- par V35 et réservé aux fournitures (catégorie par défaut, puis V51 pour la forme), n'était pas servi aux prestations
-- intellectuelles : la fiche ne posait pas la question. Il vaut désormais pour les deux catégories. Le champ propre
-- B09-PP-01 (OUI/NON) est retiré, lui, par le fichier de correspondance des prestations intellectuelles et
-- docs/referentiel/2026-09-29-lot-d3-prestations-intellectuelles.sql.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_champ_fiche_marche SET "CATEGORIES" = 'FOURNITURES_SERVICES,PRESTATIONS_INTELLECTUELLES'
 WHERE "CODE" = 'B09-PR-01' AND "CATEGORIES" = 'FOURNITURES_SERVICES';
