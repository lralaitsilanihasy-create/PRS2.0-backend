-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V73 — Le DAO complet, un seul document (demande front du 2026-10-06 ; arbitrage : Word sur le serveur).
--
-- 1. §B1 — Le type de document DAO_COMPLET (un .docx et un .pdf par version validée), assemblé par Word à partir des
--    documents produits, des textes fixes de l'ARMP (Instructions aux candidats, CCAG) et des spécifications techniques.
-- 2. §B2 — Les spécifications techniques jointes par la PRMP : un .docx par version de fiche, figé à la validation, recopié à
--    la révision (comme le besoin), supprimé avec la version.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT IF EXISTS ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'LF', 'BP', 'TC', 'A1', 'A2', 'A3', 'A4', 'C1', 'C2', 'AVIS',
                      'LETTRE_INVITATION', 'DAO_COMPLET'));

CREATE TABLE IF NOT EXISTS public.t_specifications_fiche (
    "ID_FICHE"       integer NOT NULL,
    "NOM_FICHIER"    character varying(255) NOT NULL,
    "TAILLE_OCTETS"  bigint NOT NULL,
    "EMPREINTE"      character varying(64) NOT NULL,
    "CONTENU"        bytea NOT NULL,
    "DEPOSE_LE"      timestamp without time zone NOT NULL,
    "DEPOSE_PAR"     character varying(255),
    CONSTRAINT t_specifications_fiche_pkey PRIMARY KEY ("ID_FICHE"),
    CONSTRAINT fk_specifications_fiche FOREIGN KEY ("ID_FICHE") REFERENCES public.t_fiche_marche ("ID_FICHE") ON DELETE CASCADE
);
COMMENT ON TABLE public.t_specifications_fiche IS
    'Specifications techniques (.docx) jointes par la PRMP a une version de fiche DAO, inserees dans le DAO complet (rang 5 bis) — V73';
