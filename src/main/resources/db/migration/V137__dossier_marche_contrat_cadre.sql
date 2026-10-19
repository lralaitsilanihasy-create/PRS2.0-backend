-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V137 — Le dossier de marché aligné sur le dossier de mise en concurrence (demande front du 2026-10-19
-- `demande-backend-2026-10-19-dossier-marche-contrat-cadre.md` ; demande du pilote).
--
-- 1. B1 — sous-type MAOO-CC, famille DDM, grille de base MAOO (comme DAOO-CC hérite de DAOO, V131) : l'attribution d'un lot dont le
--    dossier de mise en concurrence est en DAOO-CC le produit (SousTypesDossier.dossierMarche). Sa liste de pièces est celle de MAOO
--    (manuel, p. 50), même ordre, mêmes conditions.
-- 2. B3 — t_piece_jointe_dossier.PRODUITE_PAR : 'ATTRIBUTION' pour une pièce jointe d'office par la création du dossier de marché ;
--    elle ne se retire ni ne se remplace à la main (409 PIECE_PRODUITE_PAR_APPLICATION). Les pièces déjà jointes sont marquées : nom
--    produit par le serveur et dépôt dans les cinq minutes de la création du dossier.
-- 3. Q1 — un dossier de marché né d'une attribution, encore sans référence (ni réceptionné ni examiné), en MAOO alors que son dossier de
--    mise en concurrence est en DAOO-CC, est requalifié en MAOO-CC (en recette : 100383), avec une ligne SOUS_TYPE_REQUALIFIE au journal.
-- 4. B4 — sur ces mêmes dossiers sans référence, le devis estimatif devient le bordereau (ou DQE) estimatif de la dernière version
--    validée de la fiche ; le bordereau de l'offre retenue passe en « tout autre document ».
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- 1. Le sous-type et sa liste de pièces (sur une base sans la famille DDM — base de test vierge —, rien n'est créé).
INSERT INTO public.tr_sous_type_dossier ("ID_SOUS_TYPE", "ID_TYPE_DOSSIER", "LIBELLE_SOUS_TYPE", "GRILLE_BASE", "GRILLE_PROPRE")
SELECT 'MAOO-CC', 'DDM', 'Marché sur appel d''offres ouvert — contrat-cadre', 'MAOO', false
WHERE EXISTS (SELECT 1 FROM public.tr_type_dossier WHERE "ID_TYPE_DOSSIER" = 'DDM')
  AND EXISTS (SELECT 1 FROM public.tr_sous_type_dossier WHERE "ID_SOUS_TYPE" = 'MAOO')
ON CONFLICT ("ID_SOUS_TYPE") DO UPDATE SET "ID_TYPE_DOSSIER" = 'DDM', "LIBELLE_SOUS_TYPE" = EXCLUDED."LIBELLE_SOUS_TYPE",
    "GRILLE_BASE" = 'MAOO', "GRILLE_PROPRE" = false;

INSERT INTO public.t_piece_sous_type ("ID_TYPE_PIECE", "ID_SOUS_TYPE", "OBLIGATOIRE", "ORDRE", "CATEGORIE", "FORME", "CONDITION")
SELECT s."ID_TYPE_PIECE", 'MAOO-CC', s."OBLIGATOIRE", s."ORDRE", s."CATEGORIE", s."FORME", s."CONDITION"
FROM public.t_piece_sous_type s
WHERE s."ID_SOUS_TYPE" = 'MAOO'
  AND EXISTS (SELECT 1 FROM public.tr_sous_type_dossier t WHERE t."ID_SOUS_TYPE" = 'MAOO-CC')
  AND NOT EXISTS (SELECT 1 FROM public.t_piece_sous_type a
                  WHERE a."ID_SOUS_TYPE" = 'MAOO-CC' AND a."ID_TYPE_PIECE" = s."ID_TYPE_PIECE"
                    AND COALESCE(a."CATEGORIE", '') = COALESCE(s."CATEGORIE", '') AND COALESCE(a."FORME", '') = COALESCE(s."FORME", ''));

-- 2. Le marqueur des pièces jointes d'office.
ALTER TABLE public.t_piece_jointe_dossier ADD COLUMN IF NOT EXISTS "PRODUITE_PAR" character varying(20);
ALTER TABLE public.t_piece_jointe_dossier DROP CONSTRAINT IF EXISTS ck_piece_jointe_produite_par;
ALTER TABLE public.t_piece_jointe_dossier ADD CONSTRAINT ck_piece_jointe_produite_par
    CHECK ("PRODUITE_PAR" IS NULL OR "PRODUITE_PAR" IN ('ATTRIBUTION'));
COMMENT ON COLUMN public.t_piece_jointe_dossier."PRODUITE_PAR" IS
    'ATTRIBUTION : pièce jointe d''office par la création du dossier de marché (V137) ; ni retrait ni remplacement à la main. NULL : pièce téléversée.';

UPDATE public.t_piece_jointe_dossier p SET "PRODUITE_PAR" = 'ATTRIBUTION'
FROM public.t_attribution a, public.t_type_piece_jointe t
WHERE p."ID_DOSSIER" = a."ID_DOSSIER" AND t."ID_TYPE_PIECE" = p."ID_TYPE_PIECE" AND p."PRODUITE_PAR" IS NULL
  AND a."DOSSIER_CREE_LE" IS NOT NULL
  AND p."DATE_UPLOAD" BETWEEN a."DOSSIER_CREE_LE" AND a."DOSSIER_CREE_LE" + INTERVAL '5 minutes'
  AND (   (t."CODE" = 'PROJET_MARCHE' AND p."NOM_FICHIER" LIKE 'projet-de-marche\_%')
       OR (t."CODE" = 'DEVIS_ESTIMATIF' AND p."NOM_FICHIER" LIKE 'bordereau-offre-%')
       OR (t."CODE" = 'PV_OUVERTURE' AND p."NOM_FICHIER" LIKE 'pv-ouverture\_%')
       OR (t."CODE" = 'RAPPORT_ANALYSE' AND p."NOM_FICHIER" LIKE 'rapport-evaluation\_%')
       OR (t."CODE" = 'QUESTIONS_REPONSES' AND p."NOM_FICHIER" LIKE 'questions-reponses\_%')
       OR (t."CODE" = 'CAHIER_CHARGES' AND EXISTS (
              SELECT 1 FROM public.t_document_fiche_marche df JOIN public.t_fiche_marche f ON f."ID_FICHE" = df."ID_FICHE"
              WHERE f."ID_DMC" = a."ID_DMC" AND df."NOM_FICHIER" = p."NOM_FICHIER")));

-- 3 et 4. Les dossiers de marché nés d'une attribution, encore sans référence.
DROP TABLE IF EXISTS v137_cible;
CREATE TEMPORARY TABLE v137_cible ON COMMIT DROP AS
SELECT d."ID_DOSSIER" AS id, a."ID_DMC" AS id_dmc,
       (d."ID_SOUS_TYPE" = 'MAOO' AND mec."ID_SOUS_TYPE" = 'DAOO-CC'
        AND EXISTS (SELECT 1 FROM public.tr_sous_type_dossier s WHERE s."ID_SOUS_TYPE" = 'MAOO-CC')) AS requalifier,
       (SELECT df."ID_DOCUMENT" FROM public.t_fiche_marche f JOIN public.t_document_fiche_marche df ON df."ID_FICHE" = f."ID_FICHE"
         WHERE f."ID_DMC" = a."ID_DMC" AND f."STATUT" = 'VALIDEE' AND df."EXTENSION" = 'pdf'
           AND df."TYPE" IN ('BORDEREAU_ESTIMATIF', 'DQE_ESTIMATIF')
         ORDER BY f."NUMERO_VERSION" DESC, df."ID_DOCUMENT" LIMIT 1) AS id_estimatif
FROM public.t_dossier d
JOIN public.t_attribution a ON a."ID_DOSSIER" = d."ID_DOSSIER"
LEFT JOIN public.t_dossier mec ON mec."ID_DMC" = a."ID_DMC"
WHERE d."ID_TYPE_DOSSIER" = 'DDM' AND d."REFE_DOSSIER" IS NULL AND d."STATUT" IN ('BROUILLON', 'SOUMIS')
  AND NOT EXISTS (SELECT 1 FROM public.t_reception r WHERE r."ID_DOSSIER" = d."ID_DOSSIER");

-- Le bordereau estimatif ne remplace que là où il existe et où le dossier n'en porte pas déjà un (seconde passe : rien).
DROP TABLE IF EXISTS v137_estimatif;
CREATE TEMPORARY TABLE v137_estimatif ON COMMIT DROP AS
SELECT c.id, c.id_estimatif, p."ID_PIECE" AS id_bordereau_offre, p."ID_TYPE_PIECE" AS id_type_devis
FROM v137_cible c
JOIN public.t_piece_jointe_dossier p ON p."ID_DOSSIER" = c.id AND p."PRODUITE_PAR" = 'ATTRIBUTION' AND p."NOM_FICHIER" LIKE 'bordereau-offre-%'
JOIN public.t_type_piece_jointe t ON t."ID_TYPE_PIECE" = p."ID_TYPE_PIECE" AND t."CODE" = 'DEVIS_ESTIMATIF'
WHERE c.id_estimatif IS NOT NULL
  AND EXISTS (SELECT 1 FROM public.t_type_piece_jointe WHERE "CODE" = 'AUTRE_DOCUMENT');

UPDATE public.t_piece_jointe_dossier p
SET "ID_TYPE_PIECE" = (SELECT min("ID_TYPE_PIECE") FROM public.t_type_piece_jointe WHERE "CODE" = 'AUTRE_DOCUMENT')
FROM v137_estimatif e WHERE p."ID_PIECE" = e.id_bordereau_offre;

INSERT INTO public.t_piece_jointe_dossier ("APRES_LETTRE_RENVOI", "CONTENU", "DATE_UPLOAD", "FORMAT", "ID_DOSSIER", "ID_TYPE_PIECE",
                                           "NOM_FICHIER", "TAILLE", "VERSION_CORRIGEE", "PRODUITE_PAR")
SELECT false, df."CONTENU", LOCALTIMESTAMP, 'PDF', e.id, e.id_type_devis, df."NOM_FICHIER", length(df."CONTENU"), NULL, 'ATTRIBUTION'
FROM v137_estimatif e JOIN public.t_document_fiche_marche df ON df."ID_DOCUMENT" = e.id_estimatif;

UPDATE public.t_dossier d SET "ID_SOUS_TYPE" = 'MAOO-CC'
FROM v137_cible c WHERE d."ID_DOSSIER" = c.id AND c.requalifier;

INSERT INTO public.t_action_dossier ("ID_DOSSIER", "DATE_ACTION", "TYPE_ACTION", "AUTEUR", "DETAIL")
SELECT c.id, LOCALTIMESTAMP, 'SOUS_TYPE_REQUALIFIE', 'migration V137',
       left('Requalification contrat-cadre du dossier de marché (sous-type MAOO-CC, V137) : MAOO → MAOO-CC, dossier encore sans référence'
            || CASE WHEN EXISTS (SELECT 1 FROM v137_estimatif e WHERE e.id = c.id)
                    THEN ' ; devis estimatif : le bordereau estimatif de la fiche, le bordereau de l''offre retenue en « tout autre document »'
                    ELSE '' END, 500)
FROM v137_cible c WHERE c.requalifier;

DROP TABLE v137_estimatif;
DROP TABLE v137_cible;
