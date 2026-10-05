-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V71 — Le dépositaire génère lui-même la part de secours (demande front du 2026-10-05, soumission en ligne).
--
-- 1. §B1 — Le compte du dépositaire (profil DEPOSITAIRE, hors coquille interne, identifiant D + 9 chiffres), sur le modèle
--    des membres de CAO : créé à la désignation, activé par invitation ; une adresse, un compte, plusieurs procédures.
-- 2. §B1 — Le dépositaire désigné gagne son adresse (obligatoire au PUT), son téléphone et le compte rattaché.
-- 3. §B2, §B4 — La clé de secours dit qui l'a générée (RESPONSABLE : l'ancien geste, conservé ; DEPOSITAIRE : le nouveau) et,
--    pour le nouveau geste, le compte du dépositaire qui l'a publiée (lui seul relit son enveloppe, ouvre un défi, la déclare
--    perdue). Les clés de secours existantes sont marquées RESPONSABLE.
-- 4. §B3 — La séance garde la demande de la part de secours (motif, date), posée par le responsable.
-- Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

CREATE SEQUENCE IF NOT EXISTS public.seq_compte_depositaire START 1;
CREATE TABLE IF NOT EXISTS public.t_compte_depositaire (
    "ID_COMPTE"            character varying(10) NOT NULL,
    "EMAIL"                character varying(255) NOT NULL,
    "NOM"                  character varying(200) NOT NULL,
    "TELEPHONE"            character varying(50),
    "ETAT"                 character varying(10) NOT NULL,
    "DATE_CREATION"        timestamp without time zone NOT NULL,
    "DATE_INVITATION"      timestamp without time zone,
    "DATE_ACTIVATION"      timestamp without time zone,
    "DERNIERE_CONNEXION"   timestamp without time zone,
    CONSTRAINT t_compte_depositaire_pkey PRIMARY KEY ("ID_COMPTE"),
    CONSTRAINT uq_compte_depositaire_email UNIQUE ("EMAIL"),
    CONSTRAINT ck_compte_depositaire_etat CHECK ("ETAT" IN ('A_ACTIVER', 'ACTIF', 'ARCHIVE'))
);
COMMENT ON TABLE public.t_compte_depositaire IS
    'Compte du depositaire d''une part de secours (profil DEPOSITAIRE, hors coquille interne) : cree a la designation, active par invitation ; une adresse, un compte, plusieurs procedures — V71';

ALTER TABLE public.t_parametre_interne_procedure ADD COLUMN IF NOT EXISTS "DEPOSITAIRE_EMAIL"     character varying(255);
ALTER TABLE public.t_parametre_interne_procedure ADD COLUMN IF NOT EXISTS "DEPOSITAIRE_TELEPHONE" character varying(50);
ALTER TABLE public.t_parametre_interne_procedure ADD COLUMN IF NOT EXISTS "ID_COMPTE_DEPOSITAIRE" character varying(10);
COMMENT ON COLUMN public.t_parametre_interne_procedure."ID_COMPTE_DEPOSITAIRE" IS
    'Le compte DEPOSITAIRE (D...) du depositaire designe — V71';

ALTER TABLE public.t_cle_detenteur ADD COLUMN IF NOT EXISTS "GENERE_PAR"     character varying(12);
ALTER TABLE public.t_cle_detenteur ADD COLUMN IF NOT EXISTS "ID_DEPOSITAIRE" character varying(10);
UPDATE public.t_cle_detenteur SET "GENERE_PAR" = 'RESPONSABLE' WHERE "ROLE" = 'SECOURS' AND "GENERE_PAR" IS NULL;
ALTER TABLE public.t_cle_detenteur DROP CONSTRAINT IF EXISTS ck_cle_detenteur_genere_par;
ALTER TABLE public.t_cle_detenteur ADD CONSTRAINT ck_cle_detenteur_genere_par
    CHECK ("GENERE_PAR" IS NULL OR "GENERE_PAR" IN ('RESPONSABLE', 'DEPOSITAIRE'));
COMMENT ON COLUMN public.t_cle_detenteur."GENERE_PAR" IS
    'Part de secours : RESPONSABLE (ancien geste, poste du responsable) ou DEPOSITAIRE (poste du depositaire, ID_DEPOSITAIRE) ; nul pour un membre — V71';

ALTER TABLE public.t_seance ADD COLUMN IF NOT EXISTS "SECOURS_DEMANDE_MOTIF" text;
ALTER TABLE public.t_seance ADD COLUMN IF NOT EXISTS "SECOURS_DEMANDE_LE"    timestamp without time zone;

COMMENT ON COLUMN public.t_code_candidat."ID_CANDIDAT" IS
    'Identifiant court du compte : C… (candidat), K… (membre de CAO) ou D… (depositaire, t_compte_depositaire) — V71';
