-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V57 — Lettres d'invitation des prestations intellectuelles (lot AV-4.1, demande front du 2026-10-01,
-- demande-backend-2026-10-01-lettre-invitation-pi.md).
--
-- Le pendant de l'avis spécifique (V56) pour les prestations intellectuelles : une paire .docx / .pdf par candidat de
-- la liste restreinte, imprimée à la demande après le PV signé favorable, rattachée à la version validée qu'elle rend,
-- avec la saisie de l'impression en trace (colonne PUBLICATION de V56). Jamais jointe au dossier.
--
-- 1. Le type LETTRE_INVITATION (17 caractères) : la colonne TYPE passe de 10 à 20 caractères.
-- 2. Il rejoint la liste fermée des types de document.
-- 3. Comme l'avis, la lettre se réimprime à volonté (et une impression en produit une par candidat) : l'unicité par
--    version, type, extension et lot ne vaut pas pour elle.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_document_fiche_marche ALTER COLUMN "TYPE" TYPE character varying(20);

ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT IF EXISTS ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'LF', 'BP', 'TC', 'A1', 'A2', 'A3', 'A4', 'C1', 'C2', 'AVIS',
                      'LETTRE_INVITATION'));

DROP INDEX IF EXISTS public.uq_document_fiche_marche;
CREATE UNIQUE INDEX uq_document_fiche_marche
    ON public.t_document_fiche_marche ("ID_FICHE", "TYPE", "EXTENSION", COALESCE("LOT", 0))
 WHERE "TYPE" NOT IN ('AVIS', 'LETTRE_INVITATION');
