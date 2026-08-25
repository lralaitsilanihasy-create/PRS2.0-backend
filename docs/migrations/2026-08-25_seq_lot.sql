-- 2026-08-25 — PK auto de t_lot (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : LotService.create et les boucles de SaisieService.creerLots /
-- MiseAJourPpmService.copierLignes allouaient la PK par max(ID_LOT) + 1, une seule fois puis
-- incrémentée localement. Deux saisies concurrentes lisaient le même maximum ; et la séquence
-- restant en retard, la saisie suivante réattribuait les mêmes ids — save() sur PK assignée étant
-- un merge, elle ÉCRASAIT les lots précédents au lieu de s'y ajouter.
-- Le serveur alloue toujours la PK via nextval('seq_lot') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_LOT) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_lot;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_LOT) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_lot', (SELECT coalesce(max("ID_LOT"), 0) FROM t_lot) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_LOT"), 0) FROM t_lot) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_lot;
