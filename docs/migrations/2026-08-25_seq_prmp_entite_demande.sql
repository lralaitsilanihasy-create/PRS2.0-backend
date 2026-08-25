-- 2026-08-25 — PK auto de t_prmp_entite_demande (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : AuthService.inscrire allouait les PK
-- des declarations d'entites via un compteur local initialise a max(ID_DEMANDE) + 1 puis incremente.
-- ⚠️ Site le plus expose de la serie : l'inscription est le SEUL acte du systeme ouvert a un
-- utilisateur NON authentifie, donc le seul dont deux executions simultanees ne supposent aucune
-- coordination prealable entre acteurs.
-- Le serveur alloue toujours la PK via nextval('seq_prmp_entite_demande') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_DEMANDE) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_prmp_entite_demande;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_DEMANDE) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_prmp_entite_demande', (SELECT coalesce(max("ID_DEMANDE"), 0) FROM t_prmp_entite_demande) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_DEMANDE"), 0) FROM t_prmp_entite_demande) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_prmp_entite_demande;
