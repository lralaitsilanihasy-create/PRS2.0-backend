-- 2026-08-25 — PK auto de t_pv_navette (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : PvExamenService.ajouterNavette allouait la PK par
-- max(ID_NAVETTE) + 1. ⚠️ NE PAS CONFONDRE avec NUM_NAVETTE, rang METIER du mouvement dans SON PV
-- (1, 2, 3...), affiche a l'utilisateur et repris dans NB_NAVETTES : celui-la reste calcule par
-- max + 1 sur le PV concerne et n'a PAS de sequence -- une sequence globale le rendrait faux.
-- Le serveur alloue toujours la PK via nextval('seq_pv_navette') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_NAVETTE) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_pv_navette;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_NAVETTE) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_pv_navette', (SELECT coalesce(max("ID_NAVETTE"), 0) FROM t_pv_navette) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_NAVETTE"), 0) FROM t_pv_navette) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_pv_navette;
