-- =====================================================================================================
-- V35 — FICHE MARCHÉ d'un appel d'offres (DAO), lot 1 : référentiel de champs, fiche par DMC, cadrage,
-- contrôles, validation (demande front du 2026-09-22, frontend/docs/demande-backend-2026-09-22-fiche-marche-dao.md,
-- issue de l'esquisse de conception du DAO du pilote ; hypothèses H1-H7 de la demande prises telles quelles).
--
-- LE CONSTAT. Un dossier de mise en concurrence est aujourd'hui SANS CONTENU : un sous-type, une entité, des
-- pièces PDF — le DAO se rédige hors de l'application, se dépose en PDF et la Commission le lit. Rien ne relie
-- ses informations au PPM d'où elles viennent, rien ne les contrôle avant dépôt. Décision du pilote (22/09) :
-- la création d'un DAO est un FORMULAIRE, aucun import de PDF. t_dossier_mec (lot 3a) porte déjà « un DMC
-- par ligne de marché », typé par le mode de passation : c'est l'ancrage de la fiche.
--
-- CE QUI EST AJOUTÉ.
--   1. Un RÉFÉRENTIEL à trois étages — tr_bloc_fiche_marche (B01…B10), tr_rubrique_fiche_marche
--      (« B05-GS » = garantie de soumission), tr_champ_fiche_marche (« B05-GS-02 ») — d'où le front DESSINE
--      l'écran, comme la grille de contrôle depuis tr_points_ctrl : ajouter un champ ou changer une condition
--      ne recompile rien. La LISTE NOMINATIVE des ~130 champs à saisir n'est pas connue (le PDF ne donne que
--      les comptes par rubrique) : blocs et rubriques sont livrés ici avec leur COMPTE ATTENDU, les champs se
--      chargent depuis le fichier de correspondance nettoyé (outil d'import CSV, hors API). Seuls sont semés
--      les champs dont la donnée est CONNUE : les 22 informations reprises du PPM (B01, B02) et les réponses
--      de cadrage reflétées dans la fiche (source CADRAGE). Un champ semé porte le même code que celui du
--      fichier : l'import le remplace.
--   2. La FICHE — t_fiche_marche (une par DMC et par NUMERO_VERSION ; BROUILLON puis VALIDEE, figée ; une
--      modification après validation ouvre une nouvelle version) et t_fiche_marche_valeur (une ligne par
--      champ SAISIE renseigné). Le cadrage (dix questions) est un petit objet JSON en texte sur la fiche :
--      ce sont des réponses fermées, lues ensemble, jamais requêtées une à une.
--
-- CE QUI N'EST PAS STOCKÉ, ET C'EST DÉLIBÉRÉ. Les 22 informations du PPM (H7) : elles vivraient une seconde
-- fois et divergeraient du plan. Elles sont RELUES à chaque lecture depuis la ligne de marché, le dossier, le
-- PPM, ses bénéficiaires et ses dates prévisionnelles, avec le numéro de version du PPM lu. Les valeurs de
-- source CADRAGE : dérivées des réponses, jamais reçues.
--
-- CONTRÔLES ET CONDITIONS. Une condition d'affichage est une expression sur le cadrage (« garantieSoumission
-- = OUI », « et », « ou »), évaluée en Java (ConditionCadrage). Un contrôle est un nom de règle du catalogue
-- (ControlesFicheMarche), éventuellement suffixé d'un RÔLE quand la règle lit plusieurs champs
-- (« VALIDITE_GARANTIE_SUP_OFFRE:GARANTIE » / « :OFFRE ») : c'est ce qui permet de lier une règle à des champs
-- dont on ne connaît pas encore le code.
--
-- CLÉS ÉTRANGÈRES : rubrique → bloc, champ → rubrique, valeur → fiche (ON DELETE CASCADE : une fiche ne se
-- supprime pas par l'API, mais une purge de recette emporte ses valeurs). PAS de FK de t_fiche_marche vers
-- t_dossier_mec ni de t_fiche_marche_valeur vers tr_champ_fiche_marche : un champ écarté du fichier reste
-- dans l'historique (ACTIF = false) et une valeur saisie sous un code ancien ne doit pas bloquer l'import.
--
-- Idempotente : CREATE TABLE IF NOT EXISTS, CREATE INDEX IF NOT EXISTS, semis en INSERT … ON CONFLICT DO NOTHING.
-- =====================================================================================================

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 1. Le référentiel : blocs, rubriques, champs
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.tr_bloc_fiche_marche (
    "CODE"          character varying(3) NOT NULL,
    "LIBELLE"       character varying(150) NOT NULL,
    "RANG"          integer NOT NULL,
    "TYPES_MARCHE"  character varying(60) NOT NULL DEFAULT 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE',
    CONSTRAINT tr_bloc_fiche_marche_pkey PRIMARY KEY ("CODE")
);

CREATE TABLE IF NOT EXISTS public.tr_rubrique_fiche_marche (
    "CODE"             character varying(20) NOT NULL,
    "CODE_BLOC"        character varying(3) NOT NULL,
    "CODE_COURT"       character varying(10) NOT NULL,
    "LIBELLE"          character varying(150) NOT NULL,
    "RANG"             integer NOT NULL,
    "DOCUMENT_MAITRE"  character varying(10),
    "NB_ATTENDU"       integer NOT NULL DEFAULT 0,
    "TYPES_MARCHE"     character varying(60) NOT NULL DEFAULT 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE',
    CONSTRAINT tr_rubrique_fiche_marche_pkey PRIMARY KEY ("CODE"),
    CONSTRAINT fk_rubrique_fiche_marche_bloc FOREIGN KEY ("CODE_BLOC")
        REFERENCES public.tr_bloc_fiche_marche ("CODE"),
    CONSTRAINT ck_rubrique_fiche_marche_document CHECK ("DOCUMENT_MAITRE" IS NULL
        OR "DOCUMENT_MAITRE" IN ('DPAO', 'DPAC', 'AE', 'CCAP', 'AUCUN'))
);

CREATE TABLE IF NOT EXISTS public.tr_champ_fiche_marche (
    "CODE"             character varying(20) NOT NULL,
    "CODE_RUBRIQUE"    character varying(20) NOT NULL,
    "RANG"             integer NOT NULL,
    "LIBELLE"          character varying(200) NOT NULL,
    "TYPE"             character varying(20) NOT NULL,
    "SOURCE"           character varying(10) NOT NULL,
    "DOCUMENT_MAITRE"  character varying(10) NOT NULL DEFAULT 'AUCUN',
    "REPRISES"         character varying(40),
    "TYPES_MARCHE"     character varying(60) NOT NULL DEFAULT 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE',
    "CONDITION"        character varying(300),
    "OBLIGATOIRE"      boolean NOT NULL DEFAULT false,
    "TEXTE_TYPE"       character varying(1000),
    "CONTROLE"         character varying(60),
    "OPTIONS"          character varying(500),
    "CLE_CADRAGE"      character varying(40),
    "CLE_PPM"          character varying(40),
    "ACTIF"            boolean NOT NULL DEFAULT true,
    CONSTRAINT tr_champ_fiche_marche_pkey PRIMARY KEY ("CODE"),
    CONSTRAINT fk_champ_fiche_marche_rubrique FOREIGN KEY ("CODE_RUBRIQUE")
        REFERENCES public.tr_rubrique_fiche_marche ("CODE"),
    CONSTRAINT ck_champ_fiche_marche_type CHECK ("TYPE" IN (
        'TEXTE', 'TEXTE_LONG', 'NOMBRE', 'MONTANT', 'POURCENTAGE', 'DATE', 'LISTE', 'OUI_NON', 'PIECE')),
    CONSTRAINT ck_champ_fiche_marche_source CHECK ("SOURCE" IN ('PPM', 'SAISIE', 'CADRAGE')),
    CONSTRAINT ck_champ_fiche_marche_document CHECK ("DOCUMENT_MAITRE" IN ('DPAO', 'DPAC', 'AE', 'CCAP', 'AUCUN'))
);

CREATE INDEX IF NOT EXISTS idx_champ_fiche_marche_rubrique
    ON public.tr_champ_fiche_marche USING btree ("CODE_RUBRIQUE", "RANG");

COMMENT ON TABLE public.tr_bloc_fiche_marche IS
    'Blocs de la fiche marche d''un appel d''offres (esquisse du pilote, 22/09/2026) : B01 identification et '
    'donnees du PPM ... B10 modifications, resiliation, litiges. B07 est reserve au contrat-cadre. Figes par '
    'migration, pas d''API d''ecriture.';
COMMENT ON TABLE public.tr_rubrique_fiche_marche IS
    'Rubriques d''un bloc (une rubrique = une carte a l''ecran). NB_ATTENDU = nombre d''informations que le '
    'fichier de correspondance annonce pour la rubrique (quantite fixe) : tant que les champs ne sont pas '
    'charges, le front affiche « n informations attendues, referentiel a completer ».';
COMMENT ON TABLE public.tr_champ_fiche_marche IS
    'Une ligne par INFORMATION du fichier de correspondance. Le front dessine l''ecran depuis cette table. '
    'SOURCE : PPM = reprise de la ligne du PPM (CLE_PPM, verrouillee, jamais stockee dans la fiche) ; SAISIE ; '
    'CADRAGE = reflet d''une reponse de cadrage (CLE_CADRAGE, derivee). CONDITION : expression sur le cadrage '
    '(« garantieSoumission = OUI », « et », « ou ») ; fausse = champ ignore et rubrique fermee. CONTROLE : nom '
    'd''une regle du catalogue, suffixe d''un role si la regle lit plusieurs champs (REGLE:ROLE). REPRISES, '
    'TYPES_MARCHE, OPTIONS : listes separees par des virgules. Chargee par l''outil d''import CSV (hors API) '
    'et administrable (POST/PUT Administrateur).';

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 2. La fiche et ses valeurs
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_fiche_marche (
    "ID_FICHE"         integer GENERATED BY DEFAULT AS IDENTITY,
    "ID_DMC"           bigint NOT NULL,
    "NUMERO_VERSION"   integer NOT NULL,
    "STATUT"           character varying(20) NOT NULL,
    "TYPE_MARCHE"      character varying(20),
    "CADRAGE"          character varying(2000),
    "DATE_CREATION"    timestamp(6) without time zone NOT NULL,
    "CREE_PAR"         character varying(100),
    "DATE_MAJ"         timestamp(6) without time zone,
    "DATE_VALIDATION"  timestamp(6) without time zone,
    "VALIDE_PAR"       character varying(10),
    CONSTRAINT t_fiche_marche_pkey PRIMARY KEY ("ID_FICHE"),
    CONSTRAINT uq_fiche_marche_dmc_version UNIQUE ("ID_DMC", "NUMERO_VERSION"),
    CONSTRAINT ck_fiche_marche_statut CHECK ("STATUT" IN ('BROUILLON', 'VALIDEE')),
    CONSTRAINT ck_fiche_marche_type CHECK ("TYPE_MARCHE" IS NULL
        OR "TYPE_MARCHE" IN ('QUANTITE_FIXE', 'A_COMMANDE', 'CONTRAT_CADRE'))
);

CREATE TABLE IF NOT EXISTS public.t_fiche_marche_valeur (
    "ID_VALEUR"    integer GENERATED BY DEFAULT AS IDENTITY,
    "ID_FICHE"     integer NOT NULL,
    "CODE_CHAMP"   character varying(20) NOT NULL,
    "VALEUR"       character varying(4000),
    CONSTRAINT t_fiche_marche_valeur_pkey PRIMARY KEY ("ID_VALEUR"),
    CONSTRAINT uq_fiche_marche_valeur UNIQUE ("ID_FICHE", "CODE_CHAMP"),
    CONSTRAINT fk_fiche_marche_valeur_fiche FOREIGN KEY ("ID_FICHE")
        REFERENCES public.t_fiche_marche ("ID_FICHE") ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_fiche_marche_dmc
    ON public.t_fiche_marche USING btree ("ID_DMC", "NUMERO_VERSION" DESC);

COMMENT ON TABLE public.t_fiche_marche IS
    'Fiche marche d''un DMC de type DAO : une ligne par version. BROUILLON (enregistree bloc par bloc) puis '
    'VALIDEE par la PRMP (figee) ; une modification apres validation ouvre une nouvelle version, l''ancienne '
    'reste lisible. NUMERO_VERSION et non VERSION : ce n''est pas le verrou optimiste du projet.';
COMMENT ON COLUMN public.t_fiche_marche."CADRAGE" IS
    'Reponses du questionnaire de cadrage, objet JSON en texte (typeMarche, alloti, nbLots, variantes, '
    'groupement, formeGroupement, provenance, typePrix, prixRevisable, garantieSoumission, avance, tauxAvance, '
    'penalites, attributaires). Lues ensemble, jamais requetees une a une.';
COMMENT ON TABLE public.t_fiche_marche_valeur IS
    'Valeurs saisies d''une version de fiche : une ligne par champ de source SAISIE renseigne (CODE_CHAMP = '
    'tr_champ_fiche_marche.CODE, sans FK : un code ancien ne bloque pas l''import du referentiel). Les valeurs '
    'PPM et CADRAGE ne sont jamais ecrites ici.';

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 3. Semis : les blocs et rubriques de l'esquisse (quantité fixe, comptes attendus), et les
--    champs dont la donnée est connue (22 reprises du PPM, reflets du cadrage)
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.tr_bloc_fiche_marche ("CODE", "LIBELLE", "RANG", "TYPES_MARCHE") VALUES
    ('B01', 'Identification & données du PPM', 1, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'),
    ('B02', 'Objet, allotissement & forme du marché', 2, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'),
    ('B03', 'Candidats : groupement, sous-traitance, qualifications', 3, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'),
    ('B04', 'Dossier, remise & ouverture des offres', 4, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'),
    ('B05', 'Prix, montants & garantie de soumission', 5, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'),
    ('B06', 'Évaluation, attribution & notification', 6, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'),
    ('B07', 'Contrat-cadre (réservé)', 7, 'CONTRAT_CADRE'),
    ('B08', 'Paiements, avances & garanties financières', 8, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'),
    ('B09', 'Exécution du marché & livraison', 9, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'),
    ('B10', 'Modifications, résiliation & litiges', 10, 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE')
ON CONFLICT ("CODE") DO NOTHING;

INSERT INTO public.tr_rubrique_fiche_marche ("CODE", "CODE_BLOC", "CODE_COURT", "LIBELLE", "RANG", "DOCUMENT_MAITRE", "NB_ATTENDU") VALUES
    -- B01
    ('B01-AC', 'B01', 'AC', 'Acheteur', 1, NULL, 18),
    -- B02
    ('B02-OB', 'B02', 'OB', 'Objet de l''appel d''offres', 1, 'DPAO', 1),
    ('B02-LV', 'B02', 'LV', 'Lots et variantes', 2, 'DPAO', 6),
    ('B02-AU', 'B02', 'AU', 'Autres éléments du bloc', 3, 'DPAO', 4),
    -- B03
    ('B03-GR', 'B03', 'GR', 'Groupement', 1, 'DPAO', 3),
    ('B03-CQ', 'B03', 'CQ', 'Capacité et qualifications des candidats', 2, 'DPAO', 8),
    ('B03-ST', 'B03', 'ST', 'Sous-traitance', 3, 'AE', 2),
    ('B03-NA', 'B03', 'NA', 'Nantissement', 4, 'AE', 2),
    -- B04
    ('B04-DE', 'B04', 'DE', 'Demande d''éclaircissement', 1, 'DPAO', 3),
    ('B04-CO', 'B04', 'CO', 'Contenu des offres', 2, 'DPAO', 1),
    ('B04-VO', 'B04', 'VO', 'Délai de validité des offres', 3, 'DPAO', 1),
    ('B04-LA', 'B04', 'LA', 'Langue', 4, 'DPAO', 2),
    ('B04-RO', 'B04', 'RO', 'Remise des offres – forme des plis', 5, 'DPAO', 2),
    ('B04-LR', 'B04', 'LR', 'Lieu, date et heure de remise', 6, 'DPAO', 4),
    ('B04-OP', 'B04', 'OP', 'Ouverture des plis', 7, 'DPAO', 2),
    -- B05
    ('B05-CP', 'B05', 'CP', 'Contenu et décomposition des prix', 1, 'DPAO', 6),
    ('B05-VP', 'B05', 'VP', 'Variation des prix', 2, 'DPAO', 2),
    ('B05-MO', 'B05', 'MO', 'Monnaie', 3, 'DPAO', 2),
    ('B05-GS', 'B05', 'GS', 'Garantie de soumission', 4, 'DPAO', 6),
    ('B05-TP', 'B05', 'TP', 'Type de prix', 5, 'AE', 4),
    -- B06
    ('B06-EP', 'B06', 'EP', 'Évaluation des plis, relations candidats / PRMP', 1, 'DPAO', 1),
    ('B06-EO', 'B06', 'EO', 'Évaluation des offres, montant évalué', 2, 'DPAO', 13),
    ('B06-AN', 'B06', 'AN', 'Attribution et notification du marché', 3, 'CCAP', 2),
    ('B06-SD', 'B06', 'SD', 'Attribution, appel infructueux, notification (sans document)', 4, 'AUCUN', 3),
    -- B08
    ('B08-PA', 'B08', 'PA', 'Paiements', 1, 'CCAP', 13),
    ('B08-AV', 'B08', 'AV', 'Avance', 2, 'CCAP', 9),
    ('B08-AC', 'B08', 'AC', 'Acompte', 3, 'CCAP', 1),
    ('B08-IM', 'B08', 'IM', 'Intérêts moratoires', 4, 'CCAP', 1),
    ('B08-GB', 'B08', 'GB', 'Garantie de bonne exécution', 5, 'CCAP', 1),
    ('B08-RG', 'B08', 'RG', 'Retenue de garantie', 6, 'CCAP', 1),
    ('B08-SD', 'B08', 'SD', 'Périodicité (sans document)', 7, 'AUCUN', 1),
    -- B09
    ('B09-LL', 'B09', 'LL', 'Lieu de livraison', 1, 'DPAO', 1),
    ('B09-OM', 'B09', 'OM', 'Ordres de modification et avenants', 2, 'CCAP', 3),
    ('B09-DX', 'B09', 'DX', 'Délai d''exécution', 3, 'CCAP', 3),
    ('B09-PC', 'B09', 'PC', 'Pièces contractuelles', 4, 'CCAP', 1),
    ('B09-PS', 'B09', 'PS', 'Protection du secret', 5, 'CCAP', 3),
    ('B09-PR', 'B09', 'PR', 'Pénalités de retard', 6, 'CCAP', 3),
    ('B09-MC', 'B09', 'MC', 'Matériels confiés', 7, 'CCAP', 1),
    ('B09-SK', 'B09', 'SK', 'Stockage', 8, 'CCAP', 1),
    ('B09-EM', 'B09', 'EM', 'Emballage', 9, 'CCAP', 2),
    ('B09-RT', 'B09', 'RT', 'Responsabilité du transport', 10, 'CCAP', 3),
    ('B09-LF', 'B09', 'LF', 'Livraison des fournitures', 11, 'CCAP', 2),
    ('B09-AS', 'B09', 'AS', 'Assurance', 12, 'CCAP', 2),
    ('B09-CR', 'B09', 'CR', 'Contrôle des prix de revient', 13, 'CCAP', 1),
    ('B09-IV', 'B09', 'IV', 'Inspections, vérifications et essais', 14, 'CCAP', 1),
    ('B09-DI', 'B09', 'DI', 'Décision après inspection', 15, 'CCAP', 1),
    ('B09-DG', 'B09', 'DG', 'Délai de garantie', 16, 'CCAP', 2),
    -- B10
    ('B10-IR', 'B10', 'IR', 'Indemnité de résiliation', 1, 'CCAP', 2),
    ('B10-AR', 'B10', 'AR', 'Arbitrage', 2, 'CCAP', 1),
    ('B10-DD', 'B10', 'DD', 'Dérogation aux documents généraux', 3, 'CCAP', 1)
ON CONFLICT ("CODE") DO NOTHING;

-- Les 22 informations reprises du PPM (source PPM, verrouillées, CLE_PPM = clé de la dérivation Java).
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "REPRISES", "CLE_PPM") VALUES
    ('B01-AC-01', 'B01-AC', 1,  'Autorité contractante', 'TEXTE', 'PPM', 'DPAO', 'AE,CCAP', 'ENTITE'),
    ('B01-AC-02', 'B01-AC', 2,  'Adresse de l''autorité contractante', 'TEXTE', 'PPM', 'DPAO', 'AE,CCAP', 'ADRESSE'),
    ('B01-AC-03', 'B01-AC', 3,  'Ministère ou organisme de rattachement', 'TEXTE', 'PPM', 'DPAO', 'AE', 'MINISTERE'),
    ('B01-AC-04', 'B01-AC', 4,  'Commission des marchés compétente', 'TEXTE', 'PPM', 'DPAO', NULL, 'LOCALITE'),
    ('B01-AC-05', 'B01-AC', 5,  'Personne responsable des marchés publics', 'TEXTE', 'PPM', 'DPAO', 'AE,CCAP', 'PRMP'),
    ('B01-AC-06', 'B01-AC', 6,  'Courriel de la PRMP', 'TEXTE', 'PPM', 'DPAO', NULL, 'PRMP_EMAIL'),
    ('B01-AC-07', 'B01-AC', 7,  'Téléphone de la PRMP', 'TEXTE', 'PPM', 'DPAO', NULL, 'PRMP_TEL'),
    ('B01-AC-08', 'B01-AC', 8,  'Référence du plan de passation', 'TEXTE', 'PPM', 'DPAO', NULL, 'PPM_REFERENCE'),
    ('B01-AC-09', 'B01-AC', 9,  'Exercice budgétaire', 'NOMBRE', 'PPM', 'DPAO', 'AE', 'PPM_EXERCICE'),
    ('B01-AC-10', 'B01-AC', 10, 'Version du plan de passation', 'NOMBRE', 'PPM', 'AUCUN', NULL, 'PPM_VERSION'),
    ('B01-AC-11', 'B01-AC', 11, 'Référence du dossier de planification', 'TEXTE', 'PPM', 'AUCUN', NULL, 'DOSSIER_REFERENCE'),
    ('B01-AC-12', 'B01-AC', 12, 'Nature du marché', 'TEXTE', 'PPM', 'DPAO', 'AE,CCAP', 'NATURE'),
    ('B01-AC-13', 'B01-AC', 13, 'Mode de passation', 'TEXTE', 'PPM', 'DPAO', 'AE', 'MODE'),
    ('B01-AC-14', 'B01-AC', 14, 'Montant estimatif (Ariary)', 'MONTANT', 'PPM', 'DPAO', 'AE', 'MONTANT_ESTIMATIF'),
    ('B01-AC-15', 'B01-AC', 15, 'Source de financement', 'TEXTE', 'PPM', 'DPAO', 'AE,CCAP', 'FINANCEMENT'),
    ('B01-AC-16', 'B01-AC', 16, 'Service(s) bénéficiaire(s)', 'TEXTE', 'PPM', 'DPAO', 'CCAP', 'BENEFICIAIRES'),
    ('B01-AC-17', 'B01-AC', 17, 'Compte(s) budgétaire(s)', 'TEXTE', 'PPM', 'AE', 'CCAP', 'COMPTES'),
    ('B01-AC-18', 'B01-AC', 18, 'Dates prévisionnelles (calendrier de passation)', 'TEXTE', 'PPM', 'DPAO', NULL, 'DATES_PREVISIONNELLES'),
    ('B02-OB-01', 'B02-OB', 1,  'Objet de l''appel d''offres', 'TEXTE_LONG', 'PPM', 'DPAO', 'AE,CCAP', 'OBJET'),
    ('B02-LV-01', 'B02-LV', 1,  'Nombre de lots du plan', 'NOMBRE', 'PPM', 'DPAO', 'AE', 'NB_LOTS_PPM'),
    ('B02-LV-02', 'B02-LV', 2,  'Désignation des lots du plan', 'TEXTE_LONG', 'PPM', 'DPAO', 'AE', 'LOTS_DESIGNATION'),
    ('B02-LV-03', 'B02-LV', 3,  'Montant par lot (Ariary)', 'TEXTE', 'PPM', 'DPAO', 'AE', 'LOTS_MONTANTS')
ON CONFLICT ("CODE") DO NOTHING;

-- Les reflets du cadrage (source CADRAGE, dérivés des réponses, jamais reçus).
INSERT INTO public.tr_champ_fiche_marche ("CODE", "CODE_RUBRIQUE", "RANG", "LIBELLE", "TYPE", "SOURCE", "DOCUMENT_MAITRE", "REPRISES", "CONDITION", "CLE_CADRAGE") VALUES
    ('B02-LV-04', 'B02-LV', 4, 'Marché alloti', 'OUI_NON', 'CADRAGE', 'DPAO', 'AE', NULL, 'alloti'),
    ('B02-LV-05', 'B02-LV', 5, 'Nombre de lots', 'NOMBRE', 'CADRAGE', 'DPAO', 'AE', 'alloti = OUI', 'nbLots'),
    ('B02-LV-06', 'B02-LV', 6, 'Variantes admises', 'OUI_NON', 'CADRAGE', 'DPAO', NULL, NULL, 'variantes'),
    ('B03-GR-01', 'B03-GR', 1, 'Groupement autorisé', 'OUI_NON', 'CADRAGE', 'DPAO', 'AE,CCAP', NULL, 'groupement'),
    ('B03-GR-02', 'B03-GR', 2, 'Forme du groupement', 'LISTE', 'CADRAGE', 'DPAO', 'AE,CCAP', 'groupement = OUI', 'formeGroupement'),
    ('B05-CP-00', 'B05-CP', 0, 'Provenance des fournitures', 'LISTE', 'CADRAGE', 'DPAO', 'AE,CCAP', NULL, 'provenance'),
    ('B05-VP-01', 'B05-VP', 1, 'Prix ferme ou révisable', 'OUI_NON', 'CADRAGE', 'DPAO', 'CCAP', NULL, 'prixRevisable'),
    ('B05-GS-01', 'B05-GS', 1, 'Garantie de soumission exigée', 'OUI_NON', 'CADRAGE', 'DPAO', 'AE', NULL, 'garantieSoumission'),
    ('B05-TP-01', 'B05-TP', 1, 'Type de prix', 'LISTE', 'CADRAGE', 'AE', 'CCAP', NULL, 'typePrix'),
    ('B08-AV-01', 'B08-AV', 1, 'Avance accordée', 'OUI_NON', 'CADRAGE', 'CCAP', 'AE', NULL, 'avance'),
    ('B08-AV-02', 'B08-AV', 2, 'Taux de l''avance (%)', 'POURCENTAGE', 'CADRAGE', 'CCAP', 'AE', 'avance = OUI', 'tauxAvance'),
    ('B09-PR-01', 'B09-PR', 1, 'Régime des pénalités de retard', 'LISTE', 'CADRAGE', 'CCAP', NULL, NULL, 'penalites')
ON CONFLICT ("CODE") DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 4. Le type de DMC « DAO » (t_type_dmc n'a jamais été semé par migration : sans lui, la garde H4
--    « mode mappé au type DAO » ne pourrait jamais passer sur une base neuve). Le mapping des modes
--    de passation vers ce type reste un geste d'administration (tr_mode_passation.ID_TYPE_DMC).
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.t_type_dmc ("CODE", "LIBELLE", "ACTIF")
SELECT 'DAO', 'Dossier d''Appel d''Offres', true
WHERE NOT EXISTS (SELECT 1 FROM public.t_type_dmc WHERE "CODE" = 'DAO');
