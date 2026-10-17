-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V129 — ⚠️ 2026-10-17 : demande front « specifications-donnees-et-plans-pdf », §B2 (arbitrage du pilote du 17/10).
--
-- En travaux, le devis descriptif et les plans se déposent sur la fiche en un seul PDF ; à la validation, il devient un document de
-- la version, de type DESCRIPTIF_PLANS : listé avec le DAO complet, servi au retrait des candidats, joint d'office au dossier sous
-- la pièce DESCRIPTIF_PLANS (V119). La contrainte des types de document l'admet (liste de V117, plus DESCRIPTIF_PLANS).
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'LF', 'BP', 'TC', 'A1', 'A2', 'A3', 'A4', 'C1', 'C2', 'AVIS',
                      'LETTRE_INVITATION', 'DAO_COMPLET', 'AVIS_PROJET', 'LETTRE_PROJET', 'BORDEREAU_ESTIMATIF', 'DQE_ESTIMATIF',
                      'DESCRIPTIF_PLANS'));
