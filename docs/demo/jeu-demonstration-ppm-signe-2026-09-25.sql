-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- DBPRS20 — jeu de démonstration après le vidage du 2026-09-25 (vidage-suivi-dossiers-2026-09-25.sql)
--
-- Resème UN plan de passation signé, jouable pour la fiche DAO : le dossier DDP (PPM-AGPM, CLOTURE) de la PRMP
-- IMP001 (compte PRMP001), entité 10 « JIRO SY RANO MALAGASY », localité ANT (centrale) ; son PPM ; son circuit réduit
-- à l'essentiel (réception complète par SECANT1, dispatch par CCANT01 au Membre MEMANT1, examen, PV FAVORABLE signé
-- par le Membre et le Chef de commission) ; et huit lignes de marché :
--
--   six en « Appel d'offres ouvert » (mode 1, type DMC DAO), une par cas outillé de la fiche —
--     fournitures à quantité fixe · fournitures à commande en deux lots · fournitures en contrat-cadre ·
--     travaux à quantité fixe en deux lots · prestations intellectuelles à quantité fixe · services à commande ;
--   deux hors DAO (gré à gré, consultation de prix), pour voir le filtre de GET /api/dmcs/eligibles.
--
-- Références prises aux compteurs de l'application (t_sequence_reference), comme ReferenceService le ferait : le
-- dossier reçoit le numéro DDP suivant (« 0000n/PPM-AGPM/CNM/2026 »), le PPM le numéro JSRM suivant.
--
-- Volontairement absents (l'écran s'en passe) : prévisions de dates (CAPM), bénéficiaires, grille d'examen, journal
-- du dossier — la frise du suivi n'a donc pas de dates d'étapes. Ce n'est PAS une migration.
-- Rejouable : ne fait rien si le plan de démonstration existe déjà (repéré par le libellé de son PPM).
--
-- Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/demo/jeu-demonstration-ppm-signe-2026-09-25.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

DO $$
DECLARE
    libelle_ppm constant text := '[DÉMO] Plan de passation des marchés 2026 — jeu de démonstration de la fiche DAO';
    v_dossier integer;
    v_ppm integer;
    v_reception integer;
    v_dispatch integer;
    v_examen integer;
    v_pv integer;
    v_num bigint;
    v_num_ppm bigint;
    v_ligne integer;
    l record;
BEGIN
    IF EXISTS (SELECT 1 FROM public.t_ppm WHERE "LIBELLE" = libelle_ppm) THEN
        RAISE NOTICE 'Plan de démonstration déjà présent : rien à faire.';
        RETURN;
    END IF;

    -- Références : compteurs de l'application (global DDP de 2026 ; PPM de l'entité JSRM).
    INSERT INTO public.t_sequence_reference ("ANNEE_EXERCICE", "CODE_LOCALITE", "TYPE_DOSSIER", "DERNIERE_VALEUR")
    VALUES (2026, 'DOSSIER', 'DDP', 1)
    ON CONFLICT ("ANNEE_EXERCICE", "CODE_LOCALITE", "TYPE_DOSSIER")
    DO UPDATE SET "DERNIERE_VALEUR" = public.t_sequence_reference."DERNIERE_VALEUR" + 1
    RETURNING "DERNIERE_VALEUR" INTO v_num;
    INSERT INTO public.t_sequence_reference ("ANNEE_EXERCICE", "CODE_LOCALITE", "TYPE_DOSSIER", "DERNIERE_VALEUR")
    VALUES (2026, 'JSRM', 'PPM_REF', 1)
    ON CONFLICT ("ANNEE_EXERCICE", "CODE_LOCALITE", "TYPE_DOSSIER")
    DO UPDATE SET "DERNIERE_VALEUR" = public.t_sequence_reference."DERNIERE_VALEUR" + 1
    RETURNING "DERNIERE_VALEUR" INTO v_num_ppm;

    v_dossier := nextval('public.seq_dossier');
    INSERT INTO public.t_dossier ("ID_DOSSIER", "DATE_REF", "ID_TYPE_DOSSIER", "ID_SOUS_TYPE", "REFE_DOSSIER", "STATUT",
                                  "ID_LOCALITE", "ID_PRMP", "ID_ENTITE_CONTRACT", "DATE_SOUMISSION", "CREE_PAR",
                                  "SOUMIS_PAR", "VERSION")
    VALUES (v_dossier, DATE '2026-09-01', 'DDP', 'PPM-AGPM', lpad(v_num::text, 5, '0') || '/PPM-AGPM/CNM/2026',
            'CLOTURE', 'ANT', 'IMP001', 10, TIMESTAMP '2026-09-01 09:00', 'PRMP001', 'PRMP001', 0);

    v_ppm := nextval('public.seq_ppm');
    INSERT INTO public.t_ppm ("ID_PPM", "ID_DOSSIER", "EXERCICE", "LIBELLE", "REFERENCE", "SIGNATAIRE", "DATE_SIGNATURE",
                              "DATE_PPM_INIT", "ID_PRMP", "ID_LOCALITE", "NUM_MAJ", "VERSION")
    VALUES (v_ppm, v_dossier, 2026, libelle_ppm, lpad(v_num_ppm::text, 5, '0') || '/JSRM/PPM/2026',
            'La Personne RANDRIANARIVO', DATE '2026-08-28', DATE '2026-08-28', 'IMP001', 'ANT', 0, 0);

    FOR l IN
        SELECT * FROM (VALUES
            (1, 'Acquisition de mobilier de bureau pour les directions régionales', 1, 2, 'QUANTITE_FIXE', 85000000::numeric, '6011', 'FOURNITURES_SERVICES', 0),
            (2, 'Fourniture de consommables informatiques', 1, 2, 'A_COMMANDE', 180000000::numeric, '6011', 'FOURNITURES_SERVICES', 2),
            (3, 'Fourniture de pièces de rechange pour les stations de pompage', 1, 5, 'CONTRAT_CADRE', 240000000::numeric, '6132', 'FOURNITURES_SERVICES', 0),
            (4, 'Réhabilitation du réseau d''adduction d''eau potable', 1, 1, 'QUANTITE_FIXE', 950000000::numeric, '6231', 'TRAVAUX_NON_ROUTIERS', 2),
            (5, 'Étude du schéma directeur d''assainissement', 1, 4, 'QUANTITE_FIXE', 120000000::numeric, '6280', 'PRESTATIONS_INTELLECTUELLES', 0),
            (6, 'Prestations de gardiennage des sites', 1, 3, 'A_COMMANDE', 96000000::numeric, '6280', 'FOURNITURES_SERVICES', 0),
            (7, 'Entretien des véhicules de service', 3, 3, 'QUANTITE_FIXE', 18000000::numeric, '6132', 'FOURNITURES_SERVICES', 0),
            (8, 'Fournitures de bureau', 4, 2, 'QUANTITE_FIXE', 12000000::numeric, '6011', 'FOURNITURES_SERVICES', 0)
        ) AS v(rang, designation, id_mode, id_nature, forme, montant, compte, seuil, nb_lots)
        ORDER BY rang
    LOOP
        v_ligne := nextval('public.seq_marche');
        INSERT INTO public.t_marche ("ID_DETAIL", "ID_DOSSIER", "ID_PPM", "DESIGNATION_MARCHE", "ID_MODE", "ID_NATURE",
                                     "MONT_ESTIM", "FINANCEMENT", "NUM_COMPTE", "STATUT", "FORME_MARCHE", "SUPPRIMEE",
                                     "CATEGORIE_SEUIL", "VERSION")
        VALUES (v_ligne, v_dossier, v_ppm, l.designation, l.id_mode, l.id_nature, l.montant, 'RPI', l.compte, 'PREVU',
                l.forme, false, l.seuil, 0);
        FOR n IN 1 .. l.nb_lots LOOP
            INSERT INTO public.t_lot ("ID_LOT", "ID_DOSSIER", "ID_DETAIL", "DESIGNATION_LOT", "MONT_LOT")
            VALUES (nextval('public.seq_lot'), v_dossier, v_ligne, 'Lot ' || n, round(l.montant / l.nb_lots));
        END LOOP;
    END LOOP;

    -- Circuit : réception complète, dispatch, examen, PV favorable signé (Membre + Chef de commission).
    v_reception := nextval('public.seq_reception');
    INSERT INTO public.t_reception ("ID_RECEPTION", "ID_DOSSIER", "NUM_PASSAGE", "TYPE_PASSAGE", "IM_CTRL_RECEPT",
                                    "DATE_RECEPTION", "COMPLET")
    VALUES (v_reception, v_dossier, 1, 'INITIAL', 'SECANT1', TIMESTAMP '2026-09-02 10:00', true);
    v_dispatch := nextval('public.seq_dispatch');
    INSERT INTO public.t_dispatch ("ID_DISPATCH", "ID_RECEPTION", "IM_CTRL_CC", "IM_CTRL_MEMBRE", "IM_CTRL_DISPATCH",
                                   "DATE_DISPATCH", "INTERIM_DISPATCH")
    VALUES (v_dispatch, v_reception, 'CCANT01', 'MEMANT1', 'CCANT01', TIMESTAMP '2026-09-03 09:00', false);
    v_examen := nextval('public.seq_examen');
    INSERT INTO public.t_examen ("ID_EXAMEN", "ID_DISPATCH", "IM_CTRL_MEMBRE", "DATE_EXAMEN", "VERSION")
    VALUES (v_examen, v_dispatch, 'MEMANT1', DATE '2026-09-05', 0);
    v_pv := nextval('public.seq_pv_examen');
    INSERT INTO public.t_pv_examen ("ID_PV", "ID_EXAMEN", "ID_AVIS", "STATUT_PV", "IM_CTRL_MEMBRE", "IM_CTRL_CC",
                                    "DATE_PV", "DATE_SIGNATURE_MEMBRE", "DATE_SIGNATURE_CC", "NB_NAVETTES", "VERSION")
    VALUES (v_pv, v_examen, 'FAV', 'SIGNE', 'MEMANT1', 'CCANT01', DATE '2026-09-08', DATE '2026-09-08',
            DATE '2026-09-09', 0, 0);

    RAISE NOTICE 'Plan de démonstration semé : dossier %, PPM %, PV %.', v_dossier, v_ppm, v_pv;
END $$;

SELECT d."ID_DOSSIER" AS dossier, d."REFE_DOSSIER" AS reference, d."STATUT" AS statut, p."REFERENCE" AS ppm,
       (SELECT count(*) FROM public.t_marche m WHERE m."ID_DOSSIER" = d."ID_DOSSIER") AS lignes,
       (SELECT count(*) FROM public.t_marche m WHERE m."ID_DOSSIER" = d."ID_DOSSIER" AND m."ID_MODE" = 1) AS lignes_dao
  FROM public.t_dossier d JOIN public.t_ppm p ON p."ID_DOSSIER" = d."ID_DOSSIER"
 WHERE p."LIBELLE" LIKE '[DÉMO]%';

COMMIT;
