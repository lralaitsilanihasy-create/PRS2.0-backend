-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V136 — La copie de son offre, relisible par le candidat (demande front du 2026-10-19, « copie-offre-candidat », §B1 ;
-- arbitrage du pilote du 19/10).
--
-- Une copie par offre : le récapitulatif de la saisie, chiffré sur le poste du candidat par une clé dérivée de sa phrase de
-- relecture (PBKDF2-SHA-256, AES-256-GCM) — le serveur la garde comme l'enveloppe d'une clé de détenteur (V66), il ne peut
-- pas la lire. Un nouveau dépôt la remplace. À la clôture de la séance qui ouvre l'offre, l'enveloppe est EFFACÉE (colonnes
-- vidées, EFFACEE_LE posée) : la ligne reste pour dire au candidat pourquoi il n'a plus de copie. La purge de conservation
-- supprime la ligne avec les contenus de l'offre.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.t_copie_offre (
    "ID_OFFRE"           character varying(36) NOT NULL,
    "ENV_CHIFFRE"        text,
    "ENV_IV"             character varying(64),
    "ENV_SEL"            character varying(64),
    "ENV_ITERATIONS"     integer,
    "ENV_KDF"            character varying(30),
    "ENV_ALGORITHME"     character varying(30),
    "VERSION"            integer,
    "ENREGISTREE_LE"     timestamp without time zone NOT NULL,
    "EFFACEE_LE"         timestamp without time zone,
    CONSTRAINT t_copie_offre_pkey PRIMARY KEY ("ID_OFFRE"),
    CONSTRAINT fk_copie_offre_offre FOREIGN KEY ("ID_OFFRE") REFERENCES public.t_offre ("ID_OFFRE") ON DELETE CASCADE,
    CONSTRAINT ck_copie_offre_effacee CHECK (("EFFACEE_LE" IS NULL) = ("ENV_CHIFFRE" IS NOT NULL))
);

COMMENT ON TABLE public.t_copie_offre IS
    'Copie de l''offre chiffree par le candidat (phrase de relecture), illisible du serveur ; effacee a la cloture de la seance qui ouvre l''offre — V136';
COMMENT ON COLUMN public.t_copie_offre."EFFACEE_LE" IS
    'Effacement de l''enveloppe a la cloture de la seance (offre connue de la CAO) : colonnes ENV_* videes, ligne conservee — V136';
