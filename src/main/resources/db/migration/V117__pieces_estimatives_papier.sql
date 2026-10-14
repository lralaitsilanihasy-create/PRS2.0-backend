-- ⚠️ 2026-10-14 (demande front « q11-prix-estimatifs », §B3 ; arbitrage du pilote du 14/10 sur Q11, manuel révisé §23) — les pièces
-- estimatives signées ne sont plus demandées au dossier conduit dans l'application (comme le calendrier annuel, V115) : la fiche DAO
-- porte les prix unitaires estimés (V116), et l'application produit à la validation le bordereau des prix estimatifs (fournitures)
-- ou le devis quantitatif et estimatif (travaux), par lot, joints d'office au dossier sous ces mêmes pièces. La validation tracée
-- de la fiche vaut signature (R2).
--   * BORDEREAU_PRIX « Bordereau des prix estimatifs signé » (fournitures) ;
--   * DQE « Devis quantitatif et estimatif signé » (travaux).
-- Un dossier saisi hors de l'application garde les deux pièces. Restent inchangés (arbitrages du 14/10) : TABLEAU_COUTS de la
-- consultation de prestations intellectuelles (H3) et DEVIS_ESTIMATIF du dossier de marché (H4).

UPDATE public.t_piece_sous_type a SET "CONDITION" = 'PAPIER'
 FROM public.t_type_piece_jointe t
 WHERE t."ID_TYPE_PIECE" = a."ID_TYPE_PIECE"
   AND t."CODE" IN ('BORDEREAU_PRIX', 'DQE');

-- Les deux nouveaux types de document de la fiche (un PDF par lot, rattaché à la version validée).
ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'LF', 'BP', 'TC', 'A1', 'A2', 'A3', 'A4', 'C1', 'C2', 'AVIS',
                      'LETTRE_INVITATION', 'DAO_COMPLET', 'AVIS_PROJET', 'LETTRE_PROJET', 'BORDEREAU_ESTIMATIF', 'DQE_ESTIMATIF'));
