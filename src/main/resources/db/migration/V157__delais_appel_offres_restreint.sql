-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V157 — Les délais de l'appel d'offres restreint (demande front du 2026-11-02 `demande-backend-2026-11-02-appel-offres-restreint.md`,
-- §B6 ; décret n° 2019-1310, art. 2-2, 2-3 b) et 14 : vingt jours au moins entre l'envoi des lettres d'invitation et la remise des
-- offres, dix en cas d'urgence avérée).
--
-- 1. PUBLICITE_AOR : 15 → 20 jours, texte rattaché à l'article 14 — seulement si l'Administrateur ne l'a jamais modifié (DATE_MAJ
--    nulle et valeur d'origine) : une valeur réglée à la main n'est pas écrasée.
-- 2. PUBLICITE_AOR_URGENCE : 10 jours, appliqué quand le motif du recours (V155) est URGENCE_AVEREE.
-- Les deux restent des paramètres administrables, SIGNALÉS et non bloquants (arbitrage Q6 du pilote, V106) : leur passage en délai
-- légal bloquant est une question posée au pilote dans la demande.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.t_delai_parametre
SET "VALEUR" = 20,
    "TEXTE" = 'Appel d''offres restreint : envoi des lettres d''invitation → remise des offres (décret 2019-1310, art. 14 : 20 jours au moins)'
WHERE "CODE" = 'PUBLICITE_AOR' AND "VALEUR" = 15 AND "DATE_MAJ" IS NULL;

INSERT INTO public.t_delai_parametre ("CODE", "VALEUR", "UNITE", "TEXTE", "ACTIF") VALUES
    ('PUBLICITE_AOR_URGENCE', 10, 'JOURS', 'Appel d''offres restreint en urgence avérée : envoi des lettres d''invitation → remise des offres (décret 2019-1310, art. 14 : 10 jours au moins)', true)
ON CONFLICT ("CODE") DO NOTHING;
