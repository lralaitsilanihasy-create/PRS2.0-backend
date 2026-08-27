-- V9 — Verrou optimiste (@Version) sur l'examen et ses détails
-- (chantier AUDIT 2026-08, lot D §7 ; prolonge la V6, qui avait couvert les six entités du circuit).
--
-- La V6 a protégé dossier, PPM, marché, PV, lettre de renvoi et demande de retrait. Elle a laissé de
-- côté l'écriture LA PLUS CONCURRENTE du circuit : la saisie de l'examen, point par point. Un examen
-- se remplit à plusieurs mains (le Membre attributaire, le Chef de commission qui relit, le Président
-- qui arbitre) et point par point : c'est précisément le motif « je charge, je réfléchis, j'enregistre »
-- où deux enregistrements successifs se recouvrent sans que personne ne l'apprenne. En dernier-écrit-
-- gagne, l'avis du premier disparaît silencieusement — et un point de contrôle déclaré non conforme
-- peut redevenir conforme sans qu'aucune trace n'en subsiste.
--
-- Hibernate incrémente VERSION à chaque UPDATE et l'ajoute à la clause WHERE. Si la ligne a changé
-- entre-temps, l'UPDATE ne touche aucune ligne et Hibernate lève
-- ObjectOptimisticLockingFailureException → rendue en 409 CONFLIT_VERSION par GlobalExceptionHandler.
--
-- ⚠️ CONTRAT HTTP INCHANGÉ (décision du lot D) : contrairement aux cinq DTO du chantier
-- « conflit de version », ExamenDto et ExamenDetailDto ne portent PAS de champ `version` et le client
-- n'a rien à envoyer. La protection joue au seul niveau de l'entrelacement transactionnel — là où le
-- risque est réel — sans imposer un aller-retour de version au front.
--
-- ⚠️ NOT NULL DEFAULT 0 est impératif (même raison qu'en V6) : Hibernate refuse une version NULL sur
-- une ligne existante, il l'interpréterait comme une entité transitoire. Le DEFAULT renseigne d'un
-- coup tout l'existant, le NOT NULL empêche toute réouverture du trou. ADD COLUMN avec DEFAULT
-- constant ne réécrit pas la table (PostgreSQL ≥ 11).
--
-- Idempotente : ADD COLUMN IF NOT EXISTS. Rejouée, elle ne fait rien.

ALTER TABLE public.t_examen        ADD COLUMN IF NOT EXISTS "VERSION" integer NOT NULL DEFAULT 0;
ALTER TABLE public.t_examen_detail ADD COLUMN IF NOT EXISTS "VERSION" integer NOT NULL DEFAULT 0;
