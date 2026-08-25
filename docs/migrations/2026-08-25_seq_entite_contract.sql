-- 2026-08-25 — PK auto de tr_entite_contract (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : InscriptionService allouait la PK
-- d'une entite CREEE a la validation d'une inscription (entite proposee acceptee par
-- l'Administrateur) via un compteur local initialise a max(ID_ENTITE_CONTRACT) + 1 puis incremente.
-- Le serveur alloue toujours la PK via nextval('seq_entite_contract') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_ENTITE_CONTRACT) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_entite_contract;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_ENTITE_CONTRACT) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_entite_contract', (SELECT coalesce(max("ID_ENTITE_CONTRACT"), 0) FROM tr_entite_contract) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_ENTITE_CONTRACT"), 0) FROM tr_entite_contract) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_entite_contract;
