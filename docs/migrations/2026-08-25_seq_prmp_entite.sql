-- 2026-08-25 — PK auto de t_prmp_entite (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : PrmpEntiteService.create et
-- autoRattacherEnAttenteSiPrmp allouaient la PK par max(ID_PRMP_ENTITE) + 1 ;
-- InscriptionService.creerAffectation la recevait d'un COMPTEUR LOCAL initialise de la meme facon
-- puis incremente. Ce compteur laissait la sequence en retard sur les lignes ecrites : la validation
-- d'inscription suivante aurait reattribue les memes ids et ECRASE les rattachements de la
-- precedente (save() sur PK assignee = merge). La sequence est desormais consommee ligne par ligne.
-- Le serveur alloue toujours la PK via nextval('seq_prmp_entite') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_PRMP_ENTITE) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_prmp_entite;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_PRMP_ENTITE) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_prmp_entite', (SELECT coalesce(max("ID_PRMP_ENTITE"), 0) FROM t_prmp_entite) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_PRMP_ENTITE"), 0) FROM t_prmp_entite) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_prmp_entite;
