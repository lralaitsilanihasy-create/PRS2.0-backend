-- 2026-08-25 — PK auto de t_pv_examen (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : PvExamenService.creerProjet allouait la PK par max(ID_PV) + 1,
-- et create() reprenait ensuite tel quel l'idPv du corps -- de sorte qu'un POST /api/pv-examens
-- portant l'id d'un PV existant l'ECRASAIT (save() sur PK assignee = merge), statut, avis et
-- signatures compris, sur une piece que §3.8 veut immuable une fois signee. L'id client est
-- desormais ignore.
-- Le serveur alloue toujours la PK via nextval('seq_pv_examen') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_PV) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_pv_examen;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_PV) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_pv_examen', (SELECT coalesce(max("ID_PV"), 0) FROM t_pv_examen) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_PV"), 0) FROM t_pv_examen) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_pv_examen;
