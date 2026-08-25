-- 2026-08-25 — PK auto de t_notification (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : NotificationService.creer() allouait la PK par `select max(ID_NOTIFICATION) + 1`.
-- Une notification est émise dans la transaction métier de l'appelant (validation, dispatch,
-- rectification, observation…) et le projet ne pose aucun Propagation.REQUIRES_NEW : deux
-- transitions simultanées lisaient le même maximum, et la violation d'unicité de la seconde
-- annulait l'ACTE MÉTIER lui-même, pas seulement son avis. nextval() est atomique et hors
-- transaction : jamais deux fois la même valeur.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_NOTIFICATION) + 1 CALCULÉ SUR LA TABLE, et non
-- sur une constante. Une constante devinée trop bas collisionne dès le premier appel en production.
-- Le calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_notification;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_NOTIFICATION) + 1.
SELECT setval('seq_notification', (SELECT coalesce(max("ID_NOTIFICATION"), 0) FROM t_notification) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Si ce n'est pas le cas, NE PAS déployer et rejouer le setval.
-- SELECT (SELECT coalesce(max("ID_NOTIFICATION"), 0) FROM t_notification) AS max_actuel,
--        last_value                                                      AS prochain_nextval
--   FROM seq_notification;
