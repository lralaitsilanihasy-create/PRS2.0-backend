-- V6 — Verrou optimiste (@Version) sur les six entités chaudes du circuit
-- (chantier LOT 4, 2026-08-26 ; constat de l'audit croisé : aucun @Version dans le projet).
--
-- Sans colonne de version, toute écriture concurrente est en « last-write-wins » : deux
-- acteurs qui chargent le même dossier, le modifient et l'enregistrent l'un après l'autre
-- ne se voient pas — la seconde écriture écrase silencieusement la première, sans qu'aucune
-- des deux ne l'apprenne. Sur le circuit (dossier, PPM, marché, PV, lettre de renvoi,
-- demande de retrait), plusieurs profils travaillent en parallèle sur la même ligne : c'est
-- exactement le scénario où la perte est invisible et irrattrapable.
--
-- Hibernate incrémente VERSION à chaque UPDATE et l'ajoute à la clause WHERE. Si la ligne a
-- changé entre-temps, l'UPDATE ne touche aucune ligne et Hibernate lève
-- ObjectOptimisticLockingFailureException → rendue en 409 par GlobalExceptionHandler,
-- avec un message qui invite à recharger.
--
-- ⚠️ NOT NULL DEFAULT 0 est impératif : Hibernate refuse une version NULL sur une ligne
-- existante (il l'interpréterait comme une entité transitoire). Le DEFAULT renseigne d'un
-- coup tout l'existant ; le NOT NULL garantit qu'aucune insertion ultérieure ne rouvre le
-- trou. Les six tables sont volumineuses mais un ADD COLUMN avec DEFAULT constant est en
-- place depuis PostgreSQL 11 (pas de réécriture de table).
--
-- Idempotente : ADD COLUMN IF NOT EXISTS. Rejouée, elle ne fait rien.

ALTER TABLE public.t_dossier         ADD COLUMN IF NOT EXISTS "VERSION" integer NOT NULL DEFAULT 0;
ALTER TABLE public.t_ppm             ADD COLUMN IF NOT EXISTS "VERSION" integer NOT NULL DEFAULT 0;
ALTER TABLE public.t_marche          ADD COLUMN IF NOT EXISTS "VERSION" integer NOT NULL DEFAULT 0;
ALTER TABLE public.t_pv_examen       ADD COLUMN IF NOT EXISTS "VERSION" integer NOT NULL DEFAULT 0;
ALTER TABLE public.t_lettre_renvoi   ADD COLUMN IF NOT EXISTS "VERSION" integer NOT NULL DEFAULT 0;
ALTER TABLE public.t_demande_retrait ADD COLUMN IF NOT EXISTS "VERSION" integer NOT NULL DEFAULT 0;
