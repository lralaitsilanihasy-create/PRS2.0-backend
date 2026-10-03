-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- Fiche DAO des travaux — le DQE dans la fiche et des seuils de qualification calculés (demande front
-- demande-backend-2026-10-02-dqe-et-seuils-travaux.md ; arbitrage du pilote du 02/10).
--
--   §B1.5 — B08-MR-06 (découpage du forfait, texte libre) désactivé : le découpage se dérive des séries du DQE, jeton
--           {{BESOIN.series}}. Inactif, ses valeurs éventuelles restent lisibles. (Le DQE lui-même : migration V59.)
--   §B2.1 — B03-QT-15 « Liquidité minimale en pourcentage du montant de l'offre » (POURCENTAGE, par lot), règle
--           bloquante LIQUIDITE_DOUBLE (rôles : B03-QT-14 = MONTANT, B03-QT-15 = POURCENTAGE).
--   §B2.2 — B03-QT-16 / 17 (n meilleures des m dernières années, NOMBRE), B03-QT-18 (domaine, TEXTE, défaut « travaux de
--           construction »), règle bloquante CA_MOYENNE (rôles : B03-QT-07 = CA, 16 = MEILLEURES, 17 = ANNEES) ;
--           libellé de B03-QT-07 : « Chiffre d'affaires minimum exigé (Ariary) ».
--   §B2.3 — B03-QT-19 (nombre maximal de marchés cumulables, NOMBRE), B03-QT-20 (montant cumulé minimum, MONTANT, par
--           lot), règle bloquante REFERENCES_CUMUL (rôles : 19 = NOMBRE, 20 = MONTANT).
-- Tous facultatifs. Le fichier de correspondance des travaux porte les mêmes lignes. Ce n'est pas une migration. Idempotent.
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/referentiel/2026-10-02-dqe-et-seuils-travaux.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.tr_champ_fiche_marche SET "LIBELLE" = 'Chiffre d''affaires minimum exigé (Ariary)',
       "CONTROLE" = 'MONTANT_POSITIF,CA_MOYENNE:CA' WHERE "CODE" = 'B03-QT-07';
UPDATE public.tr_champ_fiche_marche SET "CONTROLE" = 'MONTANT_POSITIF,LIQUIDITE_DOUBLE:MONTANT' WHERE "CODE" = 'B03-QT-14';

INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE",
        "REPRISES", "TYPES_MARCHE", "CATEGORIES", "CONDITION", "OBLIGATOIRE", "TEXTE_TYPE", "CONTROLE", "OPTIONS",
        "CLE_CADRAGE", "CLE_PPM", "PAR_LOT", "VALEUR_DEFAUT", "ACTIF") VALUES
    ('B03-QT-15', 'B03-QT', 15, 'Liquidité minimale en pourcentage du montant de l''offre', 'POURCENTAGE', 'SAISIE', 'DPAO',
     NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, 'LIQUIDITE_DOUBLE:POURCENTAGE', NULL, NULL, NULL, true, NULL, true),
    ('B03-QT-16', 'B03-QT', 16, 'Nombre de meilleures années retenues pour le chiffre d''affaires moyen', 'NOMBRE', 'SAISIE',
     'DPAO', NULL, 'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, 'CA_MOYENNE:MEILLEURES', NULL, NULL, NULL, false, NULL, true),
    ('B03-QT-17', 'B03-QT', 17, 'Sur les n dernières années (chiffre d''affaires moyen)', 'NOMBRE', 'SAISIE', 'DPAO', NULL,
     'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, 'CA_MOYENNE:ANNEES', NULL, NULL, NULL, false, NULL, true),
    ('B03-QT-18', 'B03-QT', 18, 'Domaine du chiffre d''affaires', 'TEXTE', 'SAISIE', 'DPAO', NULL,
     'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, NULL, NULL, NULL, NULL, false, 'travaux de construction', true),
    ('B03-QT-19', 'B03-QT', 19, 'Nombre maximal de marchés de référence cumulables', 'NOMBRE', 'SAISIE', 'DPAO', NULL,
     'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, 'REFERENCES_CUMUL:NOMBRE', NULL, NULL, NULL, false, NULL, true),
    ('B03-QT-20', 'B03-QT', 20, 'Montant cumulé minimum des marchés de référence (Ariary)', 'MONTANT', 'SAISIE', 'DPAO', NULL,
     'QUANTITE_FIXE,A_COMMANDE', 'TRAVAUX', NULL, false, NULL, 'MONTANT_POSITIF,REFERENCES_CUMUL:MONTANT', NULL, NULL, NULL,
     true, NULL, true)
ON CONFLICT ("CODE") DO UPDATE SET "LIBELLE" = EXCLUDED."LIBELLE", "TYPE" = EXCLUDED."TYPE", "OBLIGATOIRE" = EXCLUDED."OBLIGATOIRE",
    "CONTROLE" = EXCLUDED."CONTROLE", "PAR_LOT" = EXCLUDED."PAR_LOT", "VALEUR_DEFAUT" = EXCLUDED."VALEUR_DEFAUT",
    "CATEGORIES" = EXCLUDED."CATEGORIES", "ACTIF" = true;

UPDATE public.tr_champ_fiche_marche SET "ACTIF" = false WHERE "CODE" = 'B08-MR-06';

-- Information.
SELECT "CODE", "TYPE", "PAR_LOT", "ACTIF", coalesce("CONTROLE", '') AS "CONTROLE", left("LIBELLE", 60) AS "LIBELLE"
  FROM public.tr_champ_fiche_marche
 WHERE "CODE" IN ('B03-QT-07', 'B03-QT-14', 'B03-QT-15', 'B03-QT-16', 'B03-QT-17', 'B03-QT-18', 'B03-QT-19', 'B03-QT-20',
                  'B08-MR-06')
 ORDER BY "CODE";
SELECT count(*) AS "VALEURS_B08_MR_06_CONSERVEES" FROM public.t_fiche_marche_valeur WHERE "CODE_CHAMP" = 'B08-MR-06';

COMMIT;
