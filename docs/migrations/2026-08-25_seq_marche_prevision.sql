-- 2026-08-25 — PK auto de t_marche_prevision (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : MarchePrevisionService.create
-- allouait la PK par max(ID_PREVISION) + 1. La saisie d'un PPM en crée une par processus, et
-- SaisieService tenait en plus un compteur local que create() écrasait de toute façon : ce compteur
-- est supprimé, l'allocation est désormais unique et centralisée sur la séquence.
-- Le serveur alloue toujours la PK via nextval('seq_marche_prevision') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_PREVISION) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_marche_prevision;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_PREVISION) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_marche_prevision', (SELECT coalesce(max("ID_PREVISION"), 0) FROM t_marche_prevision) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_PREVISION"), 0) FROM t_marche_prevision) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_marche_prevision;
