-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V39 — Les rubriques du contrat-cadre (demande front du 2026-09-23, lot 4, §B1)
--
-- Blocs et rubriques de la fiche marché sont figés par migration (V35) ; les champs, eux, se chargent par l'import
-- du fichier de correspondance (docs/referentiel-champs-fiche-marche-contrat-cadre.csv du front, 114 champs). Ce
-- fichier range ses champs dans 37 rubriques qui n'existent pas encore : elles sont créées ici, réservées au
-- contrat-cadre (TYPES_MARCHE = 'CONTRAT_CADRE'), avec pour document maître celui de la majorité de leurs champs et
-- pour compte attendu le nombre de champs du fichier. Les rangs 51 et suivants les placent après les rubriques
-- partagées d'un même bloc ; B07, réservé au contrat-cadre, reçoit enfin ses huit rubriques (rangs 1 à 8).
-- Libellés dérivés de la conversion du front (§ « Ce que donne la conversion ») : à reprendre par une migration si
-- le pilote les nomme autrement. Insertion idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.tr_rubrique_fiche_marche
    ("CODE", "CODE_BLOC", "CODE_COURT", "LIBELLE", "RANG", "DOCUMENT_MAITRE", "NB_ATTENDU", "TYPES_MARCHE") VALUES
    -- B02 Objet, allotissement & forme du contrat-cadre
    ('B02-OE', 'B02', 'OE', 'Objet et étendue du contrat-cadre', 51, 'DPAC', 1, 'CONTRAT_CADRE'),
    ('B02-SG', 'B02', 'SG', 'Personne responsable et délégation', 52, 'AE', 3, 'CONTRAT_CADRE'),
    ('B02-PC', 'B02', 'PC', 'Procédure de passation', 53, 'DPAC', 3, 'CONTRAT_CADRE'),
    ('B02-DC', 'B02', 'DC', 'Durée du contrat-cadre', 55, 'AE', 4, 'CONTRAT_CADRE'),
    ('B02-AL', 'B02', 'AL', 'Allotissement et attributaires', 56, 'DPAC', 2, 'CONTRAT_CADRE'),
    -- B03 Candidats
    ('B03-TI', 'B03', 'TI', 'Titulaire', 51, 'AE', 5, 'CONTRAT_CADRE'),
    ('B03-GC', 'B03', 'GC', 'Groupement', 52, 'AE', 5, 'CONTRAT_CADRE'),
    ('B03-SS', 'B03', 'SS', 'Sous-traitance', 53, 'AE', 2, 'CONTRAT_CADRE'),
    -- B04 Dossier, remise & ouverture des offres
    ('B04-DS', 'B04', 'DS', 'Dossier de consultation', 51, 'DPAC', 6, 'CONTRAT_CADRE'),
    ('B04-PO', 'B04', 'PO', 'Présentation des candidatures et des offres', 52, 'DPAC', 2, 'CONTRAT_CADRE'),
    ('B04-RQ', 'B04', 'RQ', 'Remise des offres', 53, 'DPAC', 5, 'CONTRAT_CADRE'),
    ('B04-RC', 'B04', 'RC', 'Renseignements complémentaires', 54, 'DPAC', 2, 'CONTRAT_CADRE'),
    ('B04-CP', 'B04', 'CP', 'Calendrier prévisionnel', 55, 'DPAC', 5, 'CONTRAT_CADRE'),
    -- B05 Prix et montants
    ('B05-UM', 'B05', 'UM', 'Unité monétaire', 51, 'DPAC', 1, 'CONTRAT_CADRE'),
    ('B05-MT', 'B05', 'MT', 'Montant indicatif du contrat-cadre', 52, 'AE', 2, 'CONTRAT_CADRE'),
    ('B05-PM', 'B05', 'PM', 'Prix des marchés subséquents', 53, 'AE', 3, 'CONTRAT_CADRE'),
    -- B06 Évaluation, attribution & notification
    ('B06-SC', 'B06', 'SC', 'Examen des candidatures', 51, 'DPAC', 2, 'CONTRAT_CADRE'),
    ('B06-SO', 'B06', 'SO', 'Sélection des offres', 52, 'DPAC', 5, 'CONTRAT_CADRE'),
    ('B06-CA', 'B06', 'CA', 'Critères d''attribution', 53, 'DPAC', 1, 'CONTRAT_CADRE'),
    ('B06-NO', 'B06', 'NO', 'Notification', 54, 'AE', 2, 'CONTRAT_CADRE'),
    -- B07 Marchés subséquents (bloc réservé au contrat-cadre)
    ('B07-PS', 'B07', 'PS', 'Passation des marchés subséquents', 1, 'AE', 3, 'CONTRAT_CADRE'),
    ('B07-FS', 'B07', 'FS', 'Forme des marchés subséquents', 2, 'AE', 2, 'CONTRAT_CADRE'),
    ('B07-MA', 'B07', 'MA', 'Attribution des marchés subséquents', 3, 'AE', 5, 'CONTRAT_CADRE'),
    ('B07-TN', 'B07', 'TN', 'Termes non couverts par le contrat-cadre', 4, 'AE', 2, 'CONTRAT_CADRE'),
    ('B07-PI', 'B07', 'PI', 'Pièces contractuelles', 5, 'AE', 1, 'CONTRAT_CADRE'),
    ('B07-DU', 'B07', 'DU', 'Durée et reconduction', 6, 'AE', 6, 'CONTRAT_CADRE'),
    ('B07-DE', 'B07', 'DE', 'Délais d''exécution', 7, 'AE', 4, 'CONTRAT_CADRE'),
    ('B07-PE', 'B07', 'PE', 'Pénalités', 8, 'AE', 3, 'CONTRAT_CADRE'),
    -- B08 Paiements, avances & garanties
    ('B08-FI', 'B08', 'FI', 'Financement et sûretés', 51, 'AE', 4, 'CONTRAT_CADRE'),
    ('B08-FP', 'B08', 'FP', 'Facturation et paiement', 52, 'AE', 9, 'CONTRAT_CADRE'),
    -- B09 Exécution du marché
    ('B09-EA', 'B09', 'EA', 'Exécution administrative', 51, 'AE', 3, 'CONTRAT_CADRE'),
    ('B09-VA', 'B09', 'VA', 'Vérification et admission', 52, 'AE', 2, 'CONTRAT_CADRE'),
    ('B09-GP', 'B09', 'GP', 'Garanties particulières', 53, 'AE', 2, 'CONTRAT_CADRE'),
    ('B09-AU', 'B09', 'AU', 'Assurance', 54, 'AE', 3, 'CONTRAT_CADRE'),
    -- B10 Modifications, résiliation & litiges
    ('B10-VR', 'B10', 'VR', 'Voies de recours', 51, 'AE', 1, 'CONTRAT_CADRE'),
    ('B10-MT', 'B10', 'MT', 'Modifications en cours d''exécution', 52, 'AE', 2, 'CONTRAT_CADRE'),
    ('B10-RS', 'B10', 'RS', 'Résiliation', 53, 'AE', 1, 'CONTRAT_CADRE')
ON CONFLICT ("CODE") DO NOTHING;

-- Le bloc B07, jusqu'ici « Contrat-cadre (réservé) », prend le nom que lui donne le fichier du pilote.
UPDATE public.tr_bloc_fiche_marche SET "LIBELLE" = 'Marchés subséquents'
 WHERE "CODE" = 'B07' AND "LIBELLE" = 'Contrat-cadre (réservé)';
