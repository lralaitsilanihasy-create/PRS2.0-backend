-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V133 — La grille d'examen du contrat-cadre cite les informations du contrat-cadre (demande front du 2026-10-18
-- `demande-backend-2026-10-18-grille-contrat-cadre.md`).
--
-- Constat : les points détaillés de DAOO (saisis par l'Administrateur le 26/09, « Conformité au plan de passation » …
-- « Cohérence des mentions du dossier ») citent les codes de la fiche des formes à quantités fixes et à commandes ; sur un
-- contrat-cadre (DAOO-CC, qui hérite de DAOO par GRILLE_BASE, V131), 41 des 54 codes cités sont absents de la fiche.
--
-- B1 — chaque point dont le texte change reçoit une variante propre au contrat-cadre (FORME = CONTRAT_CADRE, mêmes libellé,
-- ordre, portée, caractère obligatoire), qui cite les codes du contrat-cadre (fournitures, et travaux entre parenthèses quand
-- le code diffère) ; l'original passe en FORME = AUTRE. La condition de forme (V96) choisit la variante selon la fiche du
-- dossier. « Spécifications techniques » (B12 seul, présent pour toutes les formes) reste commun.
-- B2 — les codes retirés du référentiel (champs non utilisés) sont ôtés ou remplacés dans les originaux : B05-TP-02, B06-EO-05,
-- B08-AC-01 (→ B08-PA-05), B08-PA-08, B09-DG-02 (→ B09-DG-04).
-- Q1 — B01 n'est plus cité : le texte dit déjà « la ligne du plan examinée ».
--
-- Rien n'est créé sans l'original (une base sans ces points — la base de test — n'est pas touchée). Idempotente : une variante
-- déjà créée ne l'est pas deux fois, les remplacements de texte ne trouvent plus rien à la seconde passe.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP TABLE IF EXISTS v133_variante;
CREATE TEMPORARY TABLE v133_variante (libelle varchar(255) PRIMARY KEY, description varchar(255) NOT NULL) ON COMMIT DROP;

INSERT INTO v133_variante (libelle, description) VALUES
('Conformité au plan de passation',
 'Objet, mode (AOO), forme (contrat-cadre), catégorie et financement du DAO sont-ils ceux de la ligne du plan examinée et de l''AGPM (B02-OB-01, B02-OE-01) ?'),
('Allotissement',
 'Lots, contrat-cadre mono ou multi-attributaire (B02-AL-03 ; travaux : B07-OM-02), attribution des marchés subséquents par lot (B07-MA-04) : identiques entre le DPAC, l''acte d''engagement et le besoin (B02-LV, B02-AL-02, B12) ?'),
('Montants et estimation',
 'Montant indicatif hors taxes et toutes taxes comprises, minimum éventuel (B05-MT-01, B05-MT-02, B05-MT-03) : dans l''enveloppe du plan, cohérents avec l''estimation de la ligne et les quantités des articles (B12) ?'),
('Garantie de soumission',
 'Garantie exigée et forme de remise (B05-GS-01, B05-GS-10) conformes au CMP ; montant (B05-GS-03 ; travaux : B05-GQ-03) entre 1 et 2 % du montant indicatif ; modèle joint (B04-CD-02) ; validité des offres (B04-VO-01) ?'),
('Délais de la consultation',
 'Validité des offres (B04-VO-01), réponse aux demandes d''informations (B04-RC-02), lancement, remise des offres et séance d''évaluation (B04-CP-01, B04-CP-02, B04-CP-03) : cohérents entre eux, avec le plan et le délai légal ?'),
('Candidats : pièces et capacités',
 'Pièces de la candidature et de l''offre (B04-PO-01, B04-PO-02), fiches de renseignements (B04-CD-01), élimination des candidatures (B06-SC-02), antécédents (B03-CQ-09, B03-CQ-10) : proportionnés et non discriminants ?'),
('Évaluation et attribution',
 'Critères d''attribution et leur appréciation (B06-CA-01), jugement des offres (B06-SO-05), prix critère d''attribution (B05-PM-02), nombre de titulaires (B07-MA-08), remise en concurrence (B07-MA-06) : conformes au CMP ?'),
('Paiements, avance et garanties financières',
 'Avance (taux ≤ 20 % : B08-AV, B08-FI-02 ; travaux : B08-AT-01, B08-AT-03), demandes de paiement (B08-FP-01, B08-FP-05), délai (B08-FP-03 ; travaux : B08-FT-03), intérêts moratoires (B08-FP-04) : conformes au CCAG ?'),
('Pénalités, garantie et dérogations',
 'Pénalités des marchés subséquents et leur régime (B07-PE-01, B07-PE-02), garanties particulières et délai de garantie (B09-GP-01, B09-GP-03, B09-GP-04) ; toute dérogation au CCAG est-elle justifiée (B07-PE-03) ?'),
('Exécution et livraison',
 'Forme et délais des marchés subséquents (B07-FS-01, B07-DE-01, B07-DE-02, B07-DE-03), demandes aux titulaires (B07-PS-02, B07-PS-03), exécution commune (B09-EA-01), vérification et admission (B09-VA-01) : réalistes ?'),
('Cohérence des mentions du dossier',
 'Références, intitulés, dates et mentions d''un document à l''autre — numéro de l''appel d''offres (B02-OE-01), objet sur le pli (B04-RQ-02), objet (B02-OB-01), dates en blanc, restes d''un autre dossier — sans contradiction ?');

-- B1 — la variante contrat-cadre, à côté de l'original (même ordre : elle prend sa place dans la grille d'un contrat-cadre).
INSERT INTO public.tr_points_ctrl ("ID_POINT_CTRL", "LIBEL_POINT_CTRL", "DECRIPT_POINT_CTRL", "ORDRE_POINT_CTRL", "OBLIGATOIRE",
                                   "ID_TYPE_DOSSIER", "ID_SOUS_TYPE", "PORTEE", "CATEGORIE", "FORME")
SELECT nextval('seq_points_ctrl'), o."LIBEL_POINT_CTRL", v.description, o."ORDRE_POINT_CTRL", o."OBLIGATOIRE",
       o."ID_TYPE_DOSSIER", o."ID_SOUS_TYPE", o."PORTEE", o."CATEGORIE", 'CONTRAT_CADRE'
FROM public.tr_points_ctrl o
JOIN v133_variante v ON v.libelle = o."LIBEL_POINT_CTRL"
WHERE o."ID_TYPE_DOSSIER" = 'DMC' AND o."ID_SOUS_TYPE" = 'DAOO' AND o."FORME" IS NULL
  AND NOT EXISTS (SELECT 1 FROM public.tr_points_ctrl x
                  WHERE x."ID_SOUS_TYPE" = 'DAOO' AND x."FORME" = 'CONTRAT_CADRE' AND x."LIBEL_POINT_CTRL" = o."LIBEL_POINT_CTRL")
ORDER BY o."ORDRE_POINT_CTRL", o."ID_POINT_CTRL";

-- L'original ne vaut plus que pour les autres formes (quantités fixes, à commandes).
UPDATE public.tr_points_ctrl o SET "FORME" = 'AUTRE'
WHERE o."ID_TYPE_DOSSIER" = 'DMC' AND o."ID_SOUS_TYPE" = 'DAOO' AND o."FORME" IS NULL
  AND o."LIBEL_POINT_CTRL" IN (SELECT libelle FROM v133_variante)
  AND EXISTS (SELECT 1 FROM public.tr_points_ctrl x
              WHERE x."ID_SOUS_TYPE" = 'DAOO' AND x."FORME" = 'CONTRAT_CADRE' AND x."LIBEL_POINT_CTRL" = o."LIBEL_POINT_CTRL");

-- B2 et Q1 — les codes retirés du référentiel, et B01, dans les originaux.
UPDATE public.tr_points_ctrl SET "DECRIPT_POINT_CTRL" =
       replace(replace(replace(replace(replace(replace("DECRIPT_POINT_CTRL",
           '(B01, B02-OB-01, B02-OB-03)', '(B02-OB-01, B02-OB-03)'),
           '(B05-TP-02, B05-TP-03)', '(B05-TP-03)'),
           'offre évaluée (B06-EO-05)', 'offre évaluée'),
           'acomptes (B08-AC-01)', 'acomptes (B08-PA-05)'),
           'délai de paiement (B08-PA-08)', 'délai de paiement'),
           '(B09-DG-01, B09-DG-02)', '(B09-DG-01, B09-DG-04)')
WHERE "ID_TYPE_DOSSIER" = 'DMC' AND "ID_SOUS_TYPE" = 'DAOO' AND "FORME" IS DISTINCT FROM 'CONTRAT_CADRE'
  AND "DECRIPT_POINT_CTRL" ~ '\(B01, |B05-TP-02|B06-EO-05|B08-AC-01|B08-PA-08|B09-DG-02';

DROP TABLE v133_variante;
