-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V36 — Relier la fiche marché au dossier soumis à la CNM (demande front du 2026-09-23, lot 1b, §B1)
--
-- t_dossier reçoit ID_DMC : le DMC (t_dossier_mec) dont la fiche marché a produit le dossier, ou auquel
-- un dossier existant a été rattaché. Un DMC produit au plus un dossier, un dossier porte au plus une
-- fiche : colonne UNIQUE. C'est le dossier (objet du circuit) qui gagne l'attribut, pas le DMC (objet de
-- préparation). Seul un dossier de sous-type DAO peut le porter (CHECK, doublé d'une garde de service).
--
-- Migration seule, aucune reprise : les dossiers existants restent à NULL.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_dossier ADD COLUMN IF NOT EXISTS "ID_DMC" bigint;

ALTER TABLE public.t_dossier
    ADD CONSTRAINT uq_dossier_dmc UNIQUE ("ID_DMC");

ALTER TABLE public.t_dossier
    ADD CONSTRAINT fk_dossier_dmc FOREIGN KEY ("ID_DMC") REFERENCES public.t_dossier_mec ("ID_DMC");

ALTER TABLE public.t_dossier
    ADD CONSTRAINT ck_dossier_dmc_dao CHECK ("ID_DMC" IS NULL OR "ID_SOUS_TYPE" = 'DAO');

COMMENT ON COLUMN public.t_dossier."ID_DMC" IS
    'DMC dont la fiche marche a produit ce dossier (POST /api/fiches-marche/{idDmc}/dossier) ou auquel il a ete '
    'rattache (PUT /api/dossiers/{id}/fiche-marche). Unique ; sous-type DAO seulement ; NULL pour tout autre dossier.';
