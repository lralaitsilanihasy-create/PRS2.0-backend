-- 2026-08-25 — PK auto de t_message (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : MessageService.envoyer allouait la PK par max(ID_MESSAGE) + 1 ;
-- MessageService.create reprenait en outre l'idMessage du CORPS quand le client en fournissait un.
-- Sur PK assignee save() est un merge : un id pointant un message existant l'ecrasait, expediteur et
-- date compris. L'id client est desormais ignore, comme partout ailleurs (Voie B).
-- Le serveur alloue toujours la PK via nextval('seq_message') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_MESSAGE) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_message;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_MESSAGE) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_message', (SELECT coalesce(max("ID_MESSAGE"), 0) FROM t_message) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_MESSAGE"), 0) FROM t_message) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_message;
