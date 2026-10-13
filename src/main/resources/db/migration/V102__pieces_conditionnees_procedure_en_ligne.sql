-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V102 — Manuel de contrôle révisé pour l'application, lot 1, tranche R2 : les pièces conditionnées à la procédure en ligne (demande
-- front du 2026-10-10 `demande-backend-2026-10-10-manuel-revise-lot1.md`, §B4 ; arbitrages du pilote du 10/10 : Q2/Q14 la validation
-- tracée vaut signature des pièces internes, Q4 le rapport vaut PV de validation, Q3 double mode transitoire pour les imprimés SIGMP ;
-- arbitrages au backend du 13/10 : condition administrable par association, avis et lettre facultatifs en ligne dès R2, marché signé
-- joint d'office à l'acte).
--
-- t_piece_sous_type.CONDITION :
--   NULL                 — la pièce s'applique toujours (comme avant) ;
--   PAPIER               — exigée seulement quand la procédure n'est PAS conduite dans l'application (masquée en ligne) ;
--   FACULTATIVE_EN_LIGNE — toujours listée ; obligatoire hors ligne, facultative en ligne (imprimés SIGMP pendant la transition).
-- « En ligne » : un dossier DMC né d'une fiche (ID_DMC), un dossier de marché attribué en ligne (t_attribution.ID_DOSSIER), un acte de
-- gestion dont le marché l'est, une déclaration sans suite demandée depuis la procédure (t_sans_suite.ID_DOSSIER).
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_piece_sous_type ADD COLUMN IF NOT EXISTS "CONDITION" character varying(30);
ALTER TABLE public.t_piece_sous_type DROP CONSTRAINT IF EXISTS ck_piece_sous_type_condition;
ALTER TABLE public.t_piece_sous_type ADD CONSTRAINT ck_piece_sous_type_condition
    CHECK ("CONDITION" IS NULL OR "CONDITION" IN ('PAPIER', 'FACULTATIVE_EN_LIGNE'));

-- Les pièces internes dont la donnée est dans l'application : hors ligne seulement.
UPDATE public.t_piece_sous_type a SET "CONDITION" = 'PAPIER'
 FROM public.t_type_piece_jointe t
 WHERE t."ID_TYPE_PIECE" = a."ID_TYPE_PIECE"
   AND (
        (t."CODE" = 'FICHE_PRESENTATION' AND a."ID_SOUS_TYPE" IN ('DAOO', 'DAOOI', 'DAOOPREQUAL', 'DAOR', 'DAORI', 'DC',
                                                                  'MAOO', 'MAOOI', 'MAOOPREQUAL', 'MAOR', 'MAORI', 'MPI', 'DSS'))
     OR t."CODE" IN ('AGPM_CONTROLE', 'PPM_CONTROLE')
     OR (t."CODE" IN ('PV_CNM_DMC', 'PV_CNM_MARCHE') AND a."ID_SOUS_TYPE" IN ('MAOO', 'MAOOI', 'MAOOPREQUAL', 'MAOR', 'MAORI', 'MPI',
                                                                            'AVN', 'DR', 'INDEMN', 'PENAL', 'SURSIS'))
     OR (t."CODE" IN ('PV_VALIDATION_CAO', 'PV_VALID_TECHNIQUE', 'PV_VALID_FINALE')
         AND a."ID_SOUS_TYPE" IN ('MAOO', 'MAOOI', 'MAOOPREQUAL', 'MAOR', 'MAORI', 'MPI'))
     OR (t."CODE" = 'MARCHE_INITIAL' AND a."ID_SOUS_TYPE" IN ('AVN', 'DR', 'INDEMN', 'PENAL', 'SURSIS'))
   );

-- Les imprimés du SIGMP, remplacés par les documents générés : facultatifs en ligne pendant la transition (R3 les retirera à son terme).
UPDATE public.t_piece_sous_type a SET "CONDITION" = 'FACULTATIVE_EN_LIGNE'
 FROM public.t_type_piece_jointe t
 WHERE t."ID_TYPE_PIECE" = a."ID_TYPE_PIECE"
   AND t."CODE" IN ('AVIS_SPECIFIQUE', 'LETTRE_INVITATION')
   AND a."ID_SOUS_TYPE" IN ('DAOO', 'DAOOI', 'DAOOPREQUAL', 'DPREQUAL', 'DAOR', 'DAORI', 'DC');
