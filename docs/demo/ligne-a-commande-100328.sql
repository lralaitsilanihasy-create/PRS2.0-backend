-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- DÉMONSTRATION — une ligne « marché à commande » ouvrable par la fiche marché (lot 3, demande front du 2026-09-23,
-- §B4 de demande-backend-2026-09-23-marche-a-commande.md). Base de travail seulement (DBPRS20) : ce n'est PAS une
-- migration, rien ne l'applique automatiquement.
--
-- Arbitrage de l'utilisateur (23/09) : la ligne est ajoutée au plan signé 100328 (00001/PPM-AGPM/CNM/2026) plutôt
-- que dans un plan de démonstration séparé, qui aurait paru dans toutes les listes. Contrepartie assumée : le plan
-- porte une ligne que son PV n'a pas vue — d'où le préfixe « [DÉMO] » de la désignation, qui la repère, et le script
-- de retrait (ligne-a-commande-100328-retrait.sql).
--
-- La ligne : mode 1 « Appel d'offres ouvert » (type DMC DAO), forme A_COMMANDE, montant estimatif, deux lots, nature,
-- financement et compte repris de la ligne 302873 du même plan, aucun DMC. Idempotent : ne fait rien si la ligne
-- [DÉMO] existe déjà.
--
-- Usage : psql -U postgres -d DBPRS20 -f docs/demo/ligne-a-commande-100328.sql
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
           '[DÉMO] Fourniture de consommables informatiques (marché à commande)', 1, m."ID_NATURE",
           180000000, m."FINANCEMENT", m."NUM_COMPTE", m."STATUT", 'A_COMMANDE', false, m."CATEGORIE_SEUIL", 0
      FROM modele m
     WHERE NOT EXISTS (SELECT 1 FROM public.t_marche WHERE "DESIGNATION_MARCHE" LIKE '[DÉMO]%')
    RETURNING "ID_DETAIL"
)
INSERT INTO public.t_lot ("ID_LOT", "ID_DOSSIER", "ID_DETAIL", "DESIGNATION_LOT", "MONT_LOT", "QTE_LOT", "UNITE_LOT")
SELECT nextval('public.seq_lot'), 100328, n."ID_DETAIL", l.designation, l.montant, NULL, NULL
  FROM nouvelle n
 CROSS JOIN (VALUES ('Lot 1 : cartouches et toners', 120000000::numeric),
                    ('Lot 2 : petits équipements et périphériques', 60000000::numeric)) AS l(designation, montant);

SELECT "ID_DETAIL", "DESIGNATION_MARCHE", "FORME_MARCHE", "ID_MODE", "MONT_ESTIM",
       (SELECT count(*) FROM public.t_lot t WHERE t."ID_DETAIL" = m."ID_DETAIL") AS lots
  FROM public.t_marche m WHERE "DESIGNATION_MARCHE" LIKE '[DÉMO]%';

COMMIT;
