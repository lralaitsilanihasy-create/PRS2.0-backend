-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V103 — ⚠️ 2026-10-13 : manuel révisé pour l'application, lot 1, tranche R3b (§B3 : le projet d'avis
-- spécifique et le projet de lettre d'invitation dès la fiche validée).
--
-- Deux types de documents de fiche nouveaux, produits à la validation de chaque version et joints d'office
-- au dossier DMC (pièces AVIS_SPECIFIQUE / LETTRE_INVITATION) : AVIS_PROJET (fournitures, travaux) et
-- LETTRE_PROJET (prestations intellectuelles). Un par version et par extension : ils entrent dans l'unicité
-- ordinaire (uq_document_fiche_marche), contrairement à l'avis et aux lettres imprimés.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT IF EXISTS ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'LF', 'BP', 'TC', 'A1', 'A2', 'A3', 'A4', 'C1', 'C2', 'AVIS',
                      'LETTRE_INVITATION', 'DAO_COMPLET', 'AVIS_PROJET', 'LETTRE_PROJET'));
