-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V159 — Le type de document du projet de décision autorisant l'appel d'offres restreint (demande front du 2026-11-02
-- `demande-backend-2026-11-02-appel-offres-restreint.md`, §B5 ; suite de V158) : DECISION_AOR_PROJET, produit à la validation d'une
-- version en mode restreint et joint d'office au DAOR sous la pièce DECISION_AO_RESTR.
-- La contrainte reprend la liste de V129 et l'étend.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT IF EXISTS ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'LF', 'BP', 'TC', 'A1', 'A2', 'A3', 'A4', 'C1', 'C2', 'AVIS',
                      'LETTRE_INVITATION', 'DAO_COMPLET', 'AVIS_PROJET', 'LETTRE_PROJET', 'BORDEREAU_ESTIMATIF', 'DQE_ESTIMATIF',
                      'DESCRIPTIF_PLANS', 'DECISION_AOR_PROJET'));
