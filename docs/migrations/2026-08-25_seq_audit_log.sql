-- 2026-08-25 — PK auto de t_audit_log (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : AuditLogService.enregistrer(), DossierService.tracerEvenementDossier() /
-- tracerRectification() et VerificationService.tracerObservationNonLevee() allouaient la PK par
-- `select max(ID_LOG) + 1`. Le journal est écrit DANS la transaction métier de l'appelant (le projet
-- ne pose aucun Propagation.REQUIRES_NEW) : deux écritures concurrentes lisaient le même maximum,
-- inséraient la même PK, et la violation d'unicité de la seconde annulait TOUTE la transaction
-- métier — le dossier n'était pas validé, pour un doublon qui ne décrivait pas l'action de
-- l'utilisateur. nextval() est atomique et hors transaction : jamais deux fois la même valeur.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_LOG) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est donc à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_audit_log;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_LOG) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_audit_log', (SELECT coalesce(max("ID_LOG"), 0) FROM t_audit_log) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Si ce n'est pas le cas, NE PAS déployer et rejouer le setval.
-- SELECT (SELECT coalesce(max("ID_LOG"), 0) FROM t_audit_log) AS max_actuel,
--        last_value                                          AS prochain_nextval
--   FROM seq_audit_log;
