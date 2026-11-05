-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V168 — La grille d'un dossier né d'une fiche DAOO rattachée à ses éléments (demande front du 2026-11-05
-- `demande-backend-2026-11-05-points-par-element.md`, B4 ; rattachement validé par le pilote le 05/11).
--
-- Codes ajoutés côté application : PLAN, AVIS, CANEVAS. La ligne du plan est une référence déjà examinée : aucun point sur PLAN.
-- Rattachement PAR FAMILLE (DMC, sous-types DAOO et DAOO-CC, variantes contrat-cadre comprises) et par libellé — jamais par libellé
-- seul sur toute la table : « Délai de remise des offres » vaut SEANCE au dossier de marché (V167), AVIS,DAO ici. Seulement un point
-- encore sans élément (un réglage de l'Administrateur n'est pas écrasé). Les autres grilles nées d'une fiche (DC, AMI, AOR…) suivront.
-- Une base neuve reçoit le même rattachement des semeurs (ElementsDossier.RATTACHEMENT_DAOO). Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_points_ctrl p
SET "ELEMENTS" = r.elements
FROM (VALUES
    ('Fiche de présentation cohérente', 'PRESENTATION,DAO'),
    ('Avis spécifique conforme', 'AVIS'),
    ('Délai de remise des offres', 'AVIS,DAO'),
    ('Remise et ouverture des plis', 'AVIS,DAO'),
    ('Visite des lieux', 'AVIS,DAO'),
    ('AGPM contrôlé et publié', 'PIECES'),
    ('Évaluation et attribution', 'DAO,CANEVAS'),
    ('Prix unitaires ou forfaitaires', 'DAO,BORDEREAU'),
    ('Spécifications et DQE', 'DAO,BORDEREAU'),
    ('Conformité au plan de passation', 'DAO'),
    ('Allotissement', 'DAO'),
    ('Montants et estimation', 'DAO'),
    ('Garantie de soumission', 'DAO'),
    ('Délais de la consultation', 'DAO'),
    ('Candidats : pièces et capacités', 'DAO'),
    ('Paiements, avance et garanties financières', 'DAO'),
    ('Pénalités, garantie et dérogations', 'DAO'),
    ('Exécution et livraison', 'DAO'),
    ('Spécifications techniques', 'DAO'),
    ('Cohérence des mentions du dossier', 'DAO'),
    ('Conformité aux documents types', 'DAO'),
    ('Offres anormales et quantités', 'DAO'),
    ('Formulaires de soumission', 'DAO'),
    ('Qualifications particulières proportionnées', 'DAO'),
    ('Quantités fixes ou à commande', 'DAO'),
    ('Spécifications neutres', 'DAO'),
    ('Garantie décennale', 'DAO'),
    ('Personnel et matériel exigés', 'DAO'),
    ('Recours au contrat-cadre justifié', 'DAO'),
    ('Critères d''élimination et d''attribution', 'DAO'),
    ('Critères de conformité de l''offre', 'DAO'),
    ('Pourcentages du règlement', 'DAO'),
    ('Délais de remise (contrat-cadre)', 'DAO')
) AS r(libelle, elements)
WHERE p."ID_TYPE_DOSSIER" = 'DMC' AND p."ID_SOUS_TYPE" IN ('DAOO', 'DAOO-CC')
  AND p."LIBEL_POINT_CTRL" = r.libelle AND p."ELEMENTS" IS NULL;
