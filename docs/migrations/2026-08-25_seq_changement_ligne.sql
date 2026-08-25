-- 2026-08-25 — PK auto de t_changement_ligne (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : MiseAJourPpmService allouait les PK de
-- la trace de changement via un compteur local initialise a max(ID_CHANGEMENT) + 1 puis incremente
-- -- une ligne PAR CHAMP MODIFIE, donc des dizaines d'affilee par mise a jour de PPM.
-- Le serveur alloue toujours la PK via nextval('seq_changement_ligne') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_CHANGEMENT) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_changement_ligne;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_CHANGEMENT) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_changement_ligne', (SELECT coalesce(max("ID_CHANGEMENT"), 0) FROM t_changement_ligne) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_CHANGEMENT"), 0) FROM t_changement_ligne) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_changement_ligne;
