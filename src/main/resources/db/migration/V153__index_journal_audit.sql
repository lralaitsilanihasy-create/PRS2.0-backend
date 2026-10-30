-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V153 — ⚠️ 2026-10-30 : demande front « performance », §B4 — le journal d'audit indexé.
--
-- t_audit_log (136 584 lignes en recette le 30/10, une de plus à chaque écriture) n'avait que sa clé ID_LOG : chaque page de
-- l'écran d'audit et chaque bandeau « dernières modifications » (?size=4&table=…) parcouraient toute la table.
-- 1. DATE_ACTION : la page sans filtre de table, triée du plus récent au plus ancien, bornée ou non par les dates.
-- 2. (NOM_TABLE, DATE_ACTION) : le bandeau d'un référentiel (une ou quelques tables, les plus récentes d'abord).
-- La recherche ne place plus dans sa requête les filtres absents (AuditLogRecherche), condition pour que ces index servent.
-- Idempotente. (Construction en quelques secondes sur le volume de recette ; pas de CONCURRENTLY, Flyway migrant en transaction.)
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE INDEX IF NOT EXISTS ix_audit_log_date_action ON public.t_audit_log ("DATE_ACTION");
CREATE INDEX IF NOT EXISTS ix_audit_log_table_date ON public.t_audit_log ("NOM_TABLE", "DATE_ACTION");
