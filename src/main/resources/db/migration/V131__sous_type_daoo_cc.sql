-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V131 — Le sous-type « Dossier d'appel d'offres ouvert — contrat-cadre » (DAOO-CC), produit par la fiche DAO (demande front du
-- 2026-10-18 `demande-backend-2026-10-18-sous-type-contrat-cadre.md` ; arbitrage du pilote du 18/10 : le contrat-cadre garde son
-- sous-type distinct).
--
-- 1. Le sous-type, famille DMC : créé, ou repris s'il a été ajouté à la main depuis l'écran d'administration (libellé harmonisé).
--    GRILLE_BASE = DAOO : les points de contrôle et les motifs-types de DAOO s'y appliquent (ceux de forme CONTRAT_CADRE compris,
--    manuel p. 32-33), filtrés comme ailleurs par la forme et la catégorie de la fiche.
-- 2. Un dossier né d'une fiche (ID_DMC) peut porter DAOO-CC (contrainte ck_dossier_dmc_dao de V94 recréée).
-- 3. Sa liste de pièces (manuel, p. 32), reprise des lignes de DAOO qui valent pour le contrat-cadre (FORME nulle ou CONTRAT_CADRE),
--    mêmes conditions (PAPIER, FACULTATIVE_EN_LIGNE), même ordre : fiche de présentation, calendrier, AGPM contrôlé, publication de
--    l'AGPM, projet d'avis, DAO complet, descriptif et plans (travaux), tout autre document (facultatif). Ni canevas de rapport ni
--    bordereau ou DQE estimatif (FORME = AUTRE).
-- Les dossiers existants ne changent pas de sous-type (Q1). Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- (Sur une base sans la famille DMC — base de test vierge, dont le socle pose les référentiels —, rien n'est créé.)
INSERT INTO public.tr_sous_type_dossier ("ID_SOUS_TYPE", "ID_TYPE_DOSSIER", "LIBELLE_SOUS_TYPE", "GRILLE_BASE", "GRILLE_PROPRE")
SELECT 'DAOO-CC', 'DMC', 'Dossier d''appel d''offres ouvert — contrat-cadre', 'DAOO', false
WHERE EXISTS (SELECT 1 FROM public.tr_type_dossier WHERE "ID_TYPE_DOSSIER" = 'DMC')
ON CONFLICT ("ID_SOUS_TYPE") DO UPDATE SET "ID_TYPE_DOSSIER" = 'DMC', "LIBELLE_SOUS_TYPE" = EXCLUDED."LIBELLE_SOUS_TYPE",
    "GRILLE_BASE" = 'DAOO', "GRILLE_PROPRE" = false;

ALTER TABLE public.t_dossier DROP CONSTRAINT IF EXISTS ck_dossier_dmc_dao;
ALTER TABLE public.t_dossier ADD CONSTRAINT ck_dossier_dmc_dao
    CHECK ("ID_DMC" IS NULL OR "ID_SOUS_TYPE" IN ('DAOO', 'DAOO-CC', 'DAOR', 'DAOOI', 'DAORI', 'DAOOPREQUAL', 'DC'));

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME", "CONDITION")
SELECT p."ID_TYPE_PIECE", 'DAOO-CC', p."OBLIGATOIRE", p.rang, p."CATEGORIE", NULL, p."CONDITION"
FROM (SELECT s.*, ROW_NUMBER() OVER (ORDER BY s."ORDRE", s."ID") AS rang
      FROM public.t_piece_sous_type s
      WHERE s."ID_SOUS_TYPE" = 'DAOO' AND (s."FORME" IS NULL OR s."FORME" = 'CONTRAT_CADRE')) p
WHERE NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_SOUS_TYPE" = 'DAOO-CC' AND a."ID_TYPE_PIECE" = p."ID_TYPE_PIECE"
                    AND COALESCE(a."CATEGORIE", '') = COALESCE(p."CATEGORIE", ''));
