-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V144 — Le DAO complet produit en file, hors de la validation (demande front du 2026-10-28, « dao-complet-en-file », Q1-Q3, H2).
--
-- La validation d'une fiche DAO n'attend plus Word : elle fige la version et produit les documents séparés, puis inscrit la version
-- ici. Un ouvrier unique prend les versions dans l'ordre d'arrivée (l'avis spécifique qui trouve le DAO complet absent met la sienne
-- en tête : PRIORITAIRE), une à la fois, sans transaction pendant Word. Une ligne par version validée (ID_FICHE) :
--   EN_FILE  : attend son tour (REPRISE_APRES : pas avant cette heure, après un échec) ;
--   EN_COURS : Word l'assemble (remise EN_FILE au démarrage : un arrêt pendant l'assemblage ne la perd pas) ;
--   PRET     : le DAO complet est enregistré (DEPUIS = l'heure de fin) ;
--   ECHEC    : abandonnée après le nombre d'essais (MOTIF = le dernier échec, en clair).
-- La table survit aux redémarrages (H2). Reprise : les dernières versions validées sans DAO complet entrent en file, dans l'ordre de
-- leur validation. Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_dao_complet_file (
    "ID_FICHE"       integer NOT NULL,
    "ID_DMC"         bigint NOT NULL,
    "ETAT"           character varying(10) NOT NULL,
    "ENTREE_LE"      timestamp without time zone NOT NULL,
    "DEPUIS"         timestamp without time zone NOT NULL,
    "PRIORITAIRE"    boolean NOT NULL DEFAULT false,
    "ESSAIS"         integer NOT NULL DEFAULT 0,
    "REPRISE_APRES"  timestamp without time zone,
    "MOTIF"          character varying(1000),
    CONSTRAINT t_dao_complet_file_pkey PRIMARY KEY ("ID_FICHE"),
    CONSTRAINT fk_dao_complet_file_fiche FOREIGN KEY ("ID_FICHE") REFERENCES public.t_fiche_marche ("ID_FICHE") ON DELETE CASCADE,
    CONSTRAINT ck_dao_complet_file_etat CHECK ("ETAT" IN ('EN_FILE', 'EN_COURS', 'PRET', 'ECHEC'))
);

CREATE INDEX IF NOT EXISTS ix_dao_complet_file_etat ON public.t_dao_complet_file ("ETAT", "PRIORITAIRE", "ENTREE_LE");

COMMENT ON TABLE public.t_dao_complet_file IS
    'File d''assemblage du DAO complet par Word, une ligne par version validee ; un seul ouvrier, ordre d''arrivee, tete de file pour l''avis specifique — V144';
COMMENT ON COLUMN public.t_dao_complet_file."DEPUIS" IS
    'Entree dans l''etat courant (mise en file, debut de l''assemblage, fin, abandon) — V144';
COMMENT ON COLUMN public.t_dao_complet_file."REPRISE_APRES" IS
    'Apres un echec : la version n''est reprise qu''a partir de cette heure, les suivantes passent avant — V144';

-- Reprise : la derniere version validee de chaque fiche qui n'a pas encore son DAO complet.
INSERT INTO public.t_dao_complet_file ("ID_FICHE", "ID_DMC", "ETAT", "ENTREE_LE", "DEPUIS")
SELECT f."ID_FICHE", f."ID_DMC", 'EN_FILE', COALESCE(f."DATE_VALIDATION", now()), COALESCE(f."DATE_VALIDATION", now())
  FROM public.t_fiche_marche f
 WHERE f."STATUT" = 'VALIDEE'
   AND f."NUMERO_VERSION" = (SELECT max(g."NUMERO_VERSION") FROM public.t_fiche_marche g
                              WHERE g."ID_DMC" = f."ID_DMC" AND g."STATUT" = 'VALIDEE')
   AND EXISTS (SELECT 1 FROM public.t_document_fiche_marche d WHERE d."ID_FICHE" = f."ID_FICHE")
   AND NOT EXISTS (SELECT 1 FROM public.t_document_fiche_marche d WHERE d."ID_FICHE" = f."ID_FICHE" AND d."TYPE" = 'DAO_COMPLET')
ON CONFLICT ("ID_FICHE") DO NOTHING;
