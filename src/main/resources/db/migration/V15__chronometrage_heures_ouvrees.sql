-- V15 — Le chronométrage passe en HEURES ouvrées (règle du pilote, 2026-09-02)
--
-- LA REGLE. « Mettre le délai standard en heure de jour ouvré » — révision de l'unité du chronométrage
-- livré la veille (V14, commit c66db71). L'arbitrage porte sur TOUT le chronométrage : délais standards,
-- prévision saisie à la prise en charge, restes et durées restituées. Une seule unité partout, aucune
-- somme ne mélange heures et jours. Taux : 8 heures ouvrées = 1 jour ouvré.
--
-- MULTIPLICATION x 8, JAMAIS DE REINITIALISATION. Les valeurs stockées étaient des jours ; un jour vaut
-- 8 heures par arbitrage. La conversion est donc exacte et préserve le sens de chaque ligne — y compris
-- celles que l'Administrateur aurait ajustées depuis le seed. Réécrire le référentiel avec les nouvelles
-- valeurs par défaut aurait silencieusement effacé ces réglages.
--
-- PAS DE PURGE DE L'HISTORIQUE. La spec laissait le choix entre convertir les tâches déjà enregistrées
-- et les supprimer (dossier de recette). Convertir est à la fois correct et gratuit : le x 8 s'applique
-- aux occurrences comme au référentiel. Supprimer aurait detruit sans nécessité l'historique des
-- environnements qui en portent — sur la base de développement, t_tache_dossier est vide, la question ne
-- s'y pose même pas.
--
-- CE QUE LA CONVERSION NE CHANGE PAS. Un dossier entièrement au délai standard totalisait 14 jours
-- ouvrés (1+1+5+2+1+3+1, l'archivage étant hors compteur global) ; il totalise désormais 112 heures,
-- soit 112 / 8 = 14 jours. La date annoncée à la PRMP est identique avant et après la bascule : c'est un
-- changement d'unité, pas de promesse. Un test le verrouille.
--
-- Renommer PUIS multiplier. Les deux etapes sont gardees par un test d'existence de colonne, pour que la
-- migration reste rejouable sans doubler les valeurs.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'tr_delai_standard'
                 AND column_name = 'DELAI_JOURS') THEN
        ALTER TABLE public.tr_delai_standard RENAME COLUMN "DELAI_JOURS" TO "DELAI_HEURES";
        UPDATE public.tr_delai_standard SET "DELAI_HEURES" = "DELAI_HEURES" * 8;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 't_tache_dossier'
                 AND column_name = 'PREVISION_JOURS') THEN
        ALTER TABLE public.t_tache_dossier RENAME COLUMN "PREVISION_JOURS" TO "PREVISION_HEURES";
        UPDATE public.t_tache_dossier SET "PREVISION_HEURES" = "PREVISION_HEURES" * 8;
    END IF;
END $$;

-- Filet pour une base où le référentiel serait incomplet : le repli applicatif est passé de 1 jour à
-- 8 heures, et une ligne laissée à 0 ou négative ferait disparaître un terme de la somme sans que la
-- date annoncée en porte la trace.
UPDATE public.tr_delai_standard SET "DELAI_HEURES" = 8 WHERE "DELAI_HEURES" IS NULL OR "DELAI_HEURES" < 1;
