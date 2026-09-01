-- V14 — Chronométrage et prévision des délais de traitement (règle du pilote, 2026-09-01)
--
-- LA REGLE. La PRMP doit connaître la date prévisionnelle d'achèvement du traitement de son dossier.
-- Chaque tâche affectée à un profil est chronométrée (prise en charge, fin, durée effective) ; à la
-- prise en charge le profil saisit sa prévision ; la date annoncée est « aujourd'hui + somme des
-- prévisions des étapes restantes ». Le compteur global court de l'enregistrement du dossier à la
-- validation sur SIGMP.
--
-- TROIS TABLES, ET POURQUOI.
--
-- 1. t_tache_dossier — les occurrences de tâches, APPEND-ONLY. Une étape est REJOUABLE : un réexamen
--    après lettre de renvoi, une nouvelle navette de visa, un passage supplémentaire du Vérificateur
--    dans la boucle FAVR sont autant de tâches DISTINCTES, chacune avec sa prise en charge et sa
--    prévision propres. Écraser la ligne précédente effacerait précisément ce que le chronométrage
--    doit montrer : combien de fois le dossier est repassé par la même main. D'où la colonne
--    OCCURRENCE (1..n par dossier et par étape) et l'absence de toute mise à jour destructive.
--
-- 2. tr_delai_standard — le référentiel administrable des délais par étape (arbitrage ②). Il fournit
--    la prévision des étapes pas encore prises en charge, ce qui permet d'annoncer une date à la PRMP
--    DES LA SOUMISSION, avant que quiconque à la CNM ait touché le dossier. Il est remplacé, étape par
--    étape, par la prévision réellement saisie.
--
-- 3. t_suspension_dossier — les fenêtres où « la balle est chez la PRMP » (arbitrage ④). Elles servent
--    UNIQUEMENT au compteur net CNM. Le drapeau d'attente exposé à la PRMP, lui, est dérivé du statut
--    COURANT du dossier et non de cette table : deux sources pour deux usages, la plus fiable pour la
--    plus visible — un enregistrement manqué fausserait un cumul, jamais l'affichage.
--
-- HORODATAGE A LA SECONDE, JAMAIS EN JOURS OUVRES. Les colonnes de date sont des timestamps ; la
-- conversion en jours ouvrés (samedi/dimanche exclus, fériés hors périmètre v1) est faite à la
-- restitution par JoursOuvres. Stocker des jours ouvrés interdirait tout recalcul le jour où les
-- fériés entreront dans le périmètre.
--
-- AUCUNE REPRISE D'HISTORIQUE. La base a été réinitialisée le 01/09 : les dossiers créés après ce
-- déploiement sont chronométrés dès leur soumission, et rien n'est reconstitué rétroactivement.
--
-- Identifiants CITES en majuscules, comme tout le schéma (baseline V1) : sans guillemets, PostgreSQL
-- replierait les noms en minuscules et Hibernate, qui cite les siens, ne les retrouverait pas.

CREATE TABLE IF NOT EXISTS public.t_tache_dossier (
    "ID_TACHE"             integer                NOT NULL,
    "ID_DOSSIER"           integer                NOT NULL,
    "ETAPE"                character varying(30)  NOT NULL,
    "OCCURRENCE"           integer                NOT NULL DEFAULT 1,
    "IM_ACTEUR"            character varying(7),
    "PROFIL"               character varying(30),
    "DATE_PRISE_EN_CHARGE" timestamp              NOT NULL,
    "DATE_FIN"             timestamp,
    "PREVISION_JOURS"      integer                NOT NULL,
    "PREVISION_STANDARD"   boolean                NOT NULL DEFAULT false,
    CONSTRAINT pk_tache_dossier PRIMARY KEY ("ID_TACHE")
);

-- Pas de clé étrangère vers t_controleur sur IM_ACTEUR : le référentiel des contrôleurs admet des
-- suppressions administratives, et une FK transformerait le retrait d'un agent en échec d'intégrité
-- sur un historique qui doit précisément survivre à son départ. Même arbitrage que IM_RATTACHE (V12).
ALTER TABLE public.t_tache_dossier
    DROP CONSTRAINT IF EXISTS fk_tache_dossier_dossier;
ALTER TABLE public.t_tache_dossier
    ADD CONSTRAINT fk_tache_dossier_dossier FOREIGN KEY ("ID_DOSSIER")
    REFERENCES public.t_dossier ("ID_DOSSIER");

CREATE INDEX IF NOT EXISTS idx_tache_dossier_dossier ON public.t_tache_dossier ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_tache_dossier_ouverte ON public.t_tache_dossier ("ID_DOSSIER", "DATE_FIN");

CREATE SEQUENCE IF NOT EXISTS public.seq_tache_dossier;
SELECT setval('public.seq_tache_dossier',
    COALESCE((SELECT MAX("ID_TACHE") FROM public.t_tache_dossier), 0) + 1, false);

CREATE TABLE IF NOT EXISTS public.tr_delai_standard (
    "ETAPE"       character varying(30)  NOT NULL,
    "DELAI_JOURS" integer                NOT NULL,
    "LIBELLE"     character varying(100),
    CONSTRAINT pk_delai_standard PRIMARY KEY ("ETAPE")
);

CREATE TABLE IF NOT EXISTS public.t_suspension_dossier (
    "ID_SUSPENSION" integer                NOT NULL,
    "ID_DOSSIER"    integer                NOT NULL,
    "STATUT"        character varying(40)  NOT NULL,
    "DEBUT"         timestamp              NOT NULL,
    "FIN"           timestamp,
    CONSTRAINT pk_suspension_dossier PRIMARY KEY ("ID_SUSPENSION")
);

ALTER TABLE public.t_suspension_dossier
    DROP CONSTRAINT IF EXISTS fk_suspension_dossier_dossier;
ALTER TABLE public.t_suspension_dossier
    ADD CONSTRAINT fk_suspension_dossier_dossier FOREIGN KEY ("ID_DOSSIER")
    REFERENCES public.t_dossier ("ID_DOSSIER");

CREATE INDEX IF NOT EXISTS idx_suspension_dossier ON public.t_suspension_dossier ("ID_DOSSIER", "FIN");

CREATE SEQUENCE IF NOT EXISTS public.seq_suspension_dossier;
SELECT setval('public.seq_suspension_dossier',
    COALESCE((SELECT MAX("ID_SUSPENSION") FROM public.t_suspension_dossier), 0) + 1, false);

-- Seed des délais standards. Valeurs de départ proposées par la spec (1, 1, 5, 2, 1, 3, 2), auxquelles
-- s'ajoute TRANSMISSION_SIGMP née de la scission de l'étape « Vérification & validation SIGMP » : la
-- transmission est un acte court une fois les observations levées, d'où 1 jour. L'Administrateur
-- ajuste ensuite par l'écran dédié — ces chiffres sont un point de départ, pas une norme.
-- ON CONFLICT DO NOTHING : rejouée, la migration ne réécrase pas un réglage administratif.
INSERT INTO public.tr_delai_standard ("ETAPE", "DELAI_JOURS", "LIBELLE") VALUES
    ('RECEPTION',          1, 'Réception & enregistrement'),
    ('DISPATCH',           1, 'Dispatch'),
    ('EXAMEN',             5, 'Examen'),
    ('VISA',               2, 'Visa'),
    ('COSIGNATURE',        1, 'Co-signature'),
    ('VERIFICATION',       3, 'Vérification'),
    ('TRANSMISSION_SIGMP', 1, 'Transmission SIGMP'),
    ('ARCHIVAGE',          2, 'Archivage')
ON CONFLICT ("ETAPE") DO NOTHING;
