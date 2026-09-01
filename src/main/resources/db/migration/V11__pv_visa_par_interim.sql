-- V11 — Visa par intérim du PV (arbitrage du pilote, 2026-09-01)
--
-- LA RÈGLE. Depuis le 2026-08-31, seul le DISPATCHEUR vise un projet de PV (contrainte d'identité,
-- 403 sinon, y compris couvert par une paire de délégation active). Le pilote ouvre une exception
-- JUSTIFIÉE : « le P/CC non dispatcheur peut effectuer le visa en cas d'absence du dispatcheur ;
-- cette absence est justifiée par une note d'intérim ». C'est le pendant, à la clôture, de
-- l'INTERIM_DISPATCH qui existe déjà au dispatch — mais avec pièce justificative, et SANS levée de
-- la garde de localité : un CC ne supplée que dans SA localité, seul le Président supplée partout.
--
-- POURQUOI LE CONTENU EN BASE ET NON SUR LE FSX. Le PDF du PV est écrit sur le FSX
-- (CHEMIN_DOCUMENT) ; les pièces jointes, elles, sont stockées en base (t_piece_jointe_dossier.
-- CONTENU). La note suit les pièces jointes, et pour une raison précise : le visa est un geste
-- ATOMIQUE (multipart, décision du 2026-09-01). Un fichier écrit sur le FSX survivrait à un rollback
-- de la transaction et laisserait une note orpheline sans visa — exactement ce que l'atomicité doit
-- empêcher. Un bytea, lui, disparaît avec la transaction qui l'a posé.
--
-- PAS DE COLONNE « QUI » NI « QUAND ». Elles existeraient en double : le visa pose déjà
-- IM_CTRL_PRESIDENT ou IM_CTRL_CC (l'intérimaire est le signataire) et DATE_ACCEPTATION (la date du
-- visa). Une seconde source de vérité sur le même fait finit toujours par diverger — on l'a vu cette
-- semaine avec la version de PostgreSQL déclarée à trois endroits. VISE_PAR_INTERIM qualifie le visa
-- déjà tracé ; il ne le redit pas.
--
-- PAS DE COLONNE DE FORMAT. Seul le PDF est accepté (garde en service, sur les octets d'en-tête et
-- non sur le nom du fichier) : une colonne ne pourrait porter que « PDF ».
--
-- AUCUNE REPRISE. Les PV existants ont tous été visés par leur dispatcheur ou sous un contrat
-- antérieur : VISE_PAR_INTERIM à false est la valeur juste, pas un défaut de commodité.
--
-- Idempotente : ADD COLUMN IF NOT EXISTS. Rejouée, elle ne fait rien.

ALTER TABLE public.t_pv_examen
    ADD COLUMN IF NOT EXISTS "VISE_PAR_INTERIM" boolean NOT NULL DEFAULT false;

ALTER TABLE public.t_pv_examen
    ADD COLUMN IF NOT EXISTS "NOTE_INTERIM" bytea;

ALTER TABLE public.t_pv_examen
    ADD COLUMN IF NOT EXISTS "NOTE_INTERIM_NOM" character varying(255);

ALTER TABLE public.t_pv_examen
    ADD COLUMN IF NOT EXISTS "NOTE_INTERIM_TAILLE" bigint;

COMMENT ON COLUMN public.t_pv_examen."VISE_PAR_INTERIM" IS
    'Vrai si le visa a ete pose par un P/CC AUTRE que le dispatcheur, justifie par une note d''interim. '
    'Le signataire et la date restent portes par IM_CTRL_PRESIDENT / IM_CTRL_CC et DATE_ACCEPTATION.';

COMMENT ON COLUMN public.t_pv_examen."NOTE_INTERIM" IS
    'Note d''interim au format PDF, justifiant l''absence du dispatcheur. En base et non sur le FSX : '
    'le visa etant atomique, un rollback ne doit pas laisser de note orpheline.';
