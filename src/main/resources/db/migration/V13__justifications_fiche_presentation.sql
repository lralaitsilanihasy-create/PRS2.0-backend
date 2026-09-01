-- V13 — Justifications de la fiche de présentation (arbitrage du pilote, 2026-09-01)
--
-- LA REGLE. La « Fiche de présentation » du dossier de planification énumère trois catégories de
-- marchés qui appellent une justification : les marchés passés selon un mode DEROGATOIRE, ceux dont
-- le délai entre lancement et ouverture des plis est AMENAGE (inférieur au plancher réglementaire du
-- mode), et les CONTRATS-CADRES. Ces justifications étaient jusqu'ici absentes du modèle : le front
-- affichait « À compléter ». Le pilote a tranché le 01/09 — elles se saisissent A LA CREATION du
-- dossier et sont BLOQUANTES.
--
-- DEUX COLONNES SUR LA LIGNE, UNE SUR LE PLAN. Un même marché peut être à la fois dérogatoire et à
-- délai aménagé : ce sont deux justifications distinctes, qui répondent à deux questions distinctes
-- (pourquoi ce mode ? pourquoi ce délai ?), d'où deux colonnes et non un texte unique qui les
-- mélangerait. Les contrats-cadres, eux, n'ont pas de colonne par ligne : la justification GLOBALE de
-- la fiche (t_ppm.JUSTIFICATION_FICHE, la « Justification : » du bas du formulaire officiel) les
-- couvre — c'est ainsi que le document papier est rempli.
--
-- AUCUNE REPRISE DE DONNEES. Les plans existants gardent NULL : la lecture rend null, le front
-- continue d'afficher « À compléter », et la garde ne porte que sur les écritures faites par la
-- façade de saisie après ce déploiement. Rétro-remplir n'aurait aucun sens — personne ne peut
-- inventer a posteriori la justification d'un mode dérogatoire choisi il y a un an.
--
-- LONGUEUR. varchar(1000) : une justification est une phrase ou deux de motivation réglementaire, pas
-- un rapport. Le plafond est aligné sur la validation @Size des DTO, de sorte qu'un dépassement soit
-- refusé en 400 par la validation d'entrée plutôt qu'en 500 par la base.

-- Idempotente : ADD COLUMN IF NOT EXISTS. Rejouée, elle ne fait rien.
--
-- ⚠️ Identifiants CITÉS en majuscules, comme tout le schéma (baseline V1) : sans guillemets,
-- PostgreSQL replierait le nom en minuscules et Hibernate, qui cite ses identifiants, ne
-- retrouverait pas la colonne au démarrage.

ALTER TABLE public.t_marche
    ADD COLUMN IF NOT EXISTS "JUSTIF_MODE_DEROGATOIRE" character varying(1000);

ALTER TABLE public.t_marche
    ADD COLUMN IF NOT EXISTS "JUSTIF_DELAI_AMENAGE" character varying(1000);

ALTER TABLE public.t_ppm
    ADD COLUMN IF NOT EXISTS "JUSTIFICATION_FICHE" character varying(1000);
