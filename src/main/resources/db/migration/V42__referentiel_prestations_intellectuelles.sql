-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V42 — Le référentiel des prestations intellectuelles : le DPIC et 44 rubriques (demande front du 2026-09-24)
--
-- 1. Un document de plus : le DPIC, « données particulières des instructions aux consultants » — le document de
--    consultation des prestations intellectuelles, là où les fournitures et les travaux ont le DPAO et le
--    contrat-cadre le DPAC. Il rejoint les valeurs admises du document maître (champs, rubriques) et des documents
--    générés. Une fiche de prestations intellectuelles produit DPIC, CCAP et AE.
-- 2. 44 rubriques de catégorie PRESTATIONS_INTELLECTUELLES, reprises du tableau du document de conversion
--    (referentiel-champs-fiche-dao-prestations-intellectuelles.md, § « Les rubriques à semer par migration ») ;
--    rangs 121 et suivants ; aucun bloc neuf. Les 90 champs se chargent par l'import du fichier de correspondance.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_champ_fiche_marche DROP CONSTRAINT IF EXISTS ck_champ_fiche_marche_document;
ALTER TABLE public.tr_champ_fiche_marche ADD CONSTRAINT ck_champ_fiche_marche_document
    CHECK ("DOCUMENT_MAITRE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'AUCUN'));

ALTER TABLE public.tr_rubrique_fiche_marche DROP CONSTRAINT IF EXISTS ck_rubrique_fiche_marche_document;
ALTER TABLE public.tr_rubrique_fiche_marche ADD CONSTRAINT ck_rubrique_fiche_marche_document
    CHECK ("DOCUMENT_MAITRE" IS NULL OR "DOCUMENT_MAITRE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'AUCUN'));

ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT IF EXISTS ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP'));

INSERT INTO public.tr_rubrique_fiche_marche
    ("CODE", "CODE_BLOC", "CODE_COURT", "LIBELLE", "RANG", "DOCUMENT_MAITRE", "NB_ATTENDU", "TYPES_MARCHE", "CATEGORIES") VALUES
    ('B02-CL', 'B02', 'CL', 'Client et personne responsable', 121, 'AE', 4, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B02-MS', 'B02', 'MS', 'Mode de sélection', 122, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B02-OP', 'B02', 'OP', 'Objet des prestations', 123, 'DPIC', 3, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B02-SP', 'B02', 'SP', 'Signature de l''acte d''engagement', 124, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B03-GP', 'B03', 'GP', 'Groupement de consultants', 121, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B03-NP', 'B03', 'NP', 'Nantissement', 122, 'AE', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B03-SP', 'B03', 'SP', 'Sous-traitance', 123, 'AE', 4, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B03-TP', 'B03', 'TP', 'Titulaire', 124, 'AE', 5, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-AI', 'B04', 'AI', 'Assistance du client pendant la consultation', 121, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-DP', 'B04', 'DP', 'Délai de validité des propositions', 122, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-EP', 'B04', 'EP', 'Demande d''éclaircissements', 123, 'DPIC', 3, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-FL', 'B04', 'FL', 'Forme des plis', 124, 'DPIC', 3, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-LH', 'B04', 'LH', 'Lieu, date et heure de la remise', 125, 'DPIC', 2, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-LP', 'B04', 'LP', 'Langue', 126, 'DPIC', 2, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-NP', 'B04', 'NP', 'Contenu des propositions', 127, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-QT', 'B04', 'QT', 'Proposition technique', 128, 'DPIC', 3, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-RU', 'B04', 'RU', 'Réunion préparatoire', 129, 'DPIC', 2, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B04-VE', 'B04', 'VE', 'Remise par voie électronique', 130, 'DPIC', 2, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B05-MP', 'B05', 'MP', 'Monnaie', 121, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B05-PF', 'B05', 'PF', 'Proposition financière et mode de rémunération', 122, 'DPIC', 12, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B05-RP', 'B05', 'RP', 'Caractère ferme ou révisable des prix', 123, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B06-CS', 'B06', 'CS', 'Classement des propositions', 121, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B06-NG', 'B06', 'NG', 'Négociations', 122, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B06-OF', 'B06', 'OF', 'Ouverture des propositions financières', 123, 'DPIC', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B06-TP', 'B06', 'TP', 'Évaluation des propositions techniques', 124, 'DPIC', 6, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B08-AI', 'B08', 'AI', 'Avance forfaitaire', 121, 'AE', 2, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B08-DP', 'B08', 'DP', 'Domiciliation bancaire', 122, 'AE', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B08-IP', 'B08', 'IP', 'Intérêts moratoires', 123, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B08-RP', 'B08', 'RP', 'Modalités de règlement des comptes', 124, 'CCAP', 3, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-AI', 'B09', 'AI', 'Assistance du client', 121, 'CCAP', 2, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-AP', 'B09', 'AP', 'Assurance', 122, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-DK', 'B09', 'DK', 'Documents contractuels', 123, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-DP', 'B09', 'DP', 'Durée et délais', 124, 'AE', 4, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-FC', 'B09', 'FC', 'Fourniture de matériel par le consultant', 125, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-MF', 'B09', 'MF', 'Matériel confié par le client', 126, 'CCAP', 2, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-MS', 'B09', 'MS', 'Mesures de sécurité et protection du secret', 127, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-MV', 'B09', 'MV', 'Modifications et avenants', 128, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-NC', 'B09', 'NC', 'Notification au consultant', 129, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-OP', 'B09', 'OP', 'Opération de vérification', 130, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-PP', 'B09', 'PP', 'Pénalités et retenues', 131, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B09-UR', 'B09', 'UR', 'Utilisation des résultats', 132, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B10-DP', 'B10', 'DP', 'Dérogations aux documents généraux', 121, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B10-IN', 'B10', 'IN', 'Indemnisation en cas de résiliation', 122, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES'),
    ('B10-PP', 'B10', 'PP', 'Procédure contentieuse', 123, 'CCAP', 1, 'QUANTITE_FIXE,A_COMMANDE', 'PRESTATIONS_INTELLECTUELLES')
ON CONFLICT ("CODE") DO NOTHING;
