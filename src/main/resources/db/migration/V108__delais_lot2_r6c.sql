-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V108 — ⚠️ 2026-10-13 : manuel révisé pour l'application, lot 2, tranche R6c (§B1, règles 6 à 9 ;
-- arbitrages au backend du 13/10).
--
-- Règle 6 (QB1) : le délai d'attente de 10 jours francs part de la réception présumée de la lettre
-- d'information par les candidats non retenus = l'envoi + n jours pour tous ; n est un paramètre
-- administrable (RECEPTION_LETTRE, 3 jours proposés, à confirmer ; inactif ou 0 = l'envoi vaut
-- réception). Règle 9 : la relance à J+5 d'une demande de déclaration sans suite sans avis, une fois
-- (RELANCE_LE). Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_sans_suite ADD COLUMN IF NOT EXISTS "RELANCE_LE" timestamp without time zone;

INSERT INTO public.t_delai_parametre ("CODE", "VALEUR", "UNITE", "TEXTE", "ACTIF") VALUES
    ('RECEPTION_LETTRE', 3, 'JOURS', 'Réception présumée de la lettre d''information par un candidat non retenu : envoi + n jours (art. 52-I ; point de départ du délai d''attente ; valeur à confirmer)', true)
ON CONFLICT ("CODE") DO NOTHING;
