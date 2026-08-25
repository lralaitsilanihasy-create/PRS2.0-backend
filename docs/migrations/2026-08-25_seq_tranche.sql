-- 2026-08-25 — PK auto de t_tranche (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : TrancheService.create allouait la PK par
-- max(ID_TRANCHE) + 1, que deux saisies simultanées lisaient à l'identique.
-- Le serveur alloue toujours la PK via nextval('seq_tranche') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_TRANCHE) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_tranche;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_TRANCHE) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_tranche', (SELECT coalesce(max("ID_TRANCHE"), 0) FROM t_tranche) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_TRANCHE"), 0) FROM t_tranche) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_tranche;
