-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- DÉMONSTRATION — une ligne de PRESTATIONS INTELLECTUELLES ouvrable par la fiche DAO (troisième référentiel, demande front du 2026-09-24 ;
-- même besoin que le §B4 du lot 3). Base de travail seulement (DBPRS20) : ce n'est PAS une
-- migration, rien ne l'applique automatiquement.
--
-- Même arbitrage que les lignes à commande et de travaux (utilisateur, 23/09) : la ligne est ajoutée au plan signé 100328 (00001/PPM-AGPM/CNM/2026) plutôt
-- que dans un plan de démonstration séparé, qui aurait paru dans toutes les listes. Contrepartie assumée : le plan
-- porte une ligne que son PV n'a pas vue — d'où le préfixe « [DÉMO] » de la désignation, qui la repère, et le script
-- de retrait (ligne-prestations-intellectuelles-100328-retrait.sql).
--
-- La ligne : mode 1 « Appel d'offres ouvert » (type DMC DAO), forme QUANTITE_FIXE, nature « Prestations intellectuelles » (catégorie PRESTATIONS_INTELLECTUELLES),
-- montant estimatif, deux lots, financement et compte repris de la ligne 302873, aucun DMC. Idempotent : rien si la ligne
-- existe déjà.
--
-- Usage : psql -U postgres -d DBPRS20 -f docs/demo/ligne-prestations-intellectuelles-100328.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

BEGIN;

WITH modele AS (
    SELECT "ID_PPM", "ID_NATURE", "FINANCEMENT", "NUM_COMPTE", "STATUT", "CATEGORIE_SEUIL"
      FROM public.t_marche WHERE "ID_DETAIL" = 302873
), nouvelle AS (
    INSERT INTO public.t_marche ("ID_DETAIL", "ID_DOSSIER", "ID_PPM", "DESIGNATION_MARCHE", "ID_MODE", "ID_NATURE",
                                 "MONT_ESTIM", "FINANCEMENT", "NUM_COMPTE", "STATUT", "FORME_MARCHE", "SUPPRIMEE",
                                 "CATEGORIE_SEUIL", "VERSION")
    SELECT nextval('public.seq_marche'), 100328, m."ID_PPM",
           '[DÉMO] Étude de faisabilité et assistance à maîtrise d''ouvrage', 1,
           (SELECT "ID_NATURE" FROM public.tr_nature WHERE upper(trim("LIBELLE")) = 'PRESTATIONS INTELLECTUELLES' ORDER BY 1 LIMIT 1),
           120000000, m."FINANCEMENT", m."NUM_COMPTE", m."STATUT", 'QUANTITE_FIXE', false, m."CATEGORIE_SEUIL", 0
      FROM modele m
     WHERE NOT EXISTS (SELECT 1 FROM public.t_marche WHERE "DESIGNATION_MARCHE" = '[DÉMO] Étude de faisabilité et assistance à maîtrise d''ouvrage')
    RETURNING "ID_DETAIL"
)
INSERT INTO public.t_lot ("ID_LOT", "ID_DOSSIER", "ID_DETAIL", "DESIGNATION_LOT", "MONT_LOT", "QTE_LOT", "UNITE_LOT")
SELECT nextval('public.seq_lot'), 100328, n."ID_DETAIL", l.designation, l.montant, NULL, NULL
  FROM nouvelle n
 CROSS JOIN (VALUES ('Lot 1 : étude de faisabilité', 70000000::numeric),
                    ('Lot 2 : assistance à maîtrise d''ouvrage', 50000000::numeric)) AS l(designation, montant);

SELECT "ID_DETAIL", "DESIGNATION_MARCHE", "FORME_MARCHE", "ID_MODE", "MONT_ESTIM",
       (SELECT count(*) FROM public.t_lot t WHERE t."ID_DETAIL" = m."ID_DETAIL") AS lots
  FROM public.t_marche m WHERE "DESIGNATION_MARCHE" = '[DÉMO] Étude de faisabilité et assistance à maîtrise d''ouvrage';

COMMIT;
