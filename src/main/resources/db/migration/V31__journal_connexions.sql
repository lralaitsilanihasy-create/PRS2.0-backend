-- =====================================================================================================
-- V31 — Journal des connexions : rendre t_session_utilisateur écrivable par TOUS les acteurs, et donner
-- enfin une date de dépôt à t_compte_auth (demande front du 2026-09-17, §B4,
-- frontend/docs/demande-backend-2026-09-17-espace-admin.md).
--
-- LE CONSTAT. Aucune connexion n'est tracée durablement, nulle part : AuditConfig exclut /api/auth/** du
-- journal d'audit (et l'intercepteur ignore de toute façon les réponses >= 400, donc les échecs) ;
-- t_session_utilisateur n'est écrite par aucun code applicatif depuis la baseline ; les échecs ne vivent
-- que dans les ConcurrentHashMap de LoginRateLimiter, perdus à chaque redémarrage. La FK
-- t_audit_log.SESSION_ID -> t_session_utilisateur(ID_SESSION) est morte depuis V1.
--
-- (a) IM_CONTROLEUR : varchar(7) -> varchar(10), ET LA FK TOMBE.
--     La colonne est dimensionnée sur tr_controleur.IM_CONTROLEUR (7) et contrainte par une FK vers
--     cette table. Or un ID_PRMP fait 10 caractères (t_prmp.ID_PRMP, V1) : sans cet élargissement AUCUNE
--     connexion de PRMP ni d'UGPM ne peut s'écrire — 22001 au flush. C'est exactement le défaut C3 de
--     l'audit du 2026-09-14, sur une autre colonne (V29 : t_tache_dossier.IM_ACTEUR).
--     La FK vers tr_controleur ne peut PAS survivre : une PRMP ou une UGPM n'y figure pas, l'insertion
--     serait refusée pour violation de clé étrangère. Elle est donc retirée, et la colonne porte
--     désormais la référence de N'IMPORTE QUEL acteur (IM_CONTROLEUR, ID_PRMP ou ID_UGPM).
--     ⚠️ SON NOM DEVIENT TROMPEUR et il est conservé tel quel : le renommer coûterait une réécriture de
--     l'entité, du mapper, de l'index de V8 et du nettoyage de ControleurService pour un gain cosmétique.
--     Le sens réel est écrit dans le COMMENT ci-dessous, dans la javadoc de SessionUtilisateur.imControleur
--     et dans docs/api-endpoints.md.
--
-- (a bis) LOGIN : colonne AJOUTÉE, hors de la lettre de la demande mais exigée par son point 2.
--     « Une ligne neuve avec SUCCES = false et l'identifiant tenté » : l'identifiant tenté est un LOGIN
--     (varchar(100) dans t_compte_auth), pas une référence d'acteur — et quand il est inconnu, il N'EXISTE
--     AUCUNE référence d'acteur à écrire. Le loger dans IM_CONTROLEUR était exclu deux fois : 10
--     caractères ne tiennent pas un login, et un login inconnu n'est pas une référence d'acteur. C'est
--     précisément « la ligne qui manque le plus » qui rend cette colonne obligatoire.
--
-- (b) t_compte_auth.DATE_DEMANDE — la table ne porte AUCUNE date de dépôt : seule DATE_DECISION existe,
--     écrite quand l'Administrateur tranche, donc JAMAIS pour une inscription en attente. C'est pour cela
--     que KpiService.inscriptionDoyenneLe est aujourd'hui DÉRIVÉE de t_piece_jointe.DATE_DEPOT (la
--     première pièce, écrite dans la même transaction que l'inscription) avec repli sur la première
--     déclaration d'entité. Exact, mais fragile : la dérivation tombe le jour où une inscription est créée
--     sans pièce, et elle repose sur une coïncidence de transaction, pas sur une donnée.
--
-- REPRISE DES LIGNES EXISTANTES (b) : l'ancienne dérivation est rejouée UNE FOIS, ici, pour les comptes
-- déjà en base — première pièce, à défaut première déclaration d'entité, à défaut date de décision. Après
-- quoi le code Java lit la colonne et rien d'autre (le repli est retiré de KpiService). Un compte qui ne
-- porte aucun des trois reste à NULL : c'est ce que l'ancienne dérivation rendait déjà pour lui.
--
-- REPRISE DES LIGNES EXISTANTES (a) : rien à faire. Élargir un varchar est une opération de métadonnées en
-- PostgreSQL — pas de réécriture de table, pas de perte, les lignes déjà présentes restent valides.
--
-- INDEX. Une ligne par TENTATIVE de connexion, échecs compris : la table devient la plus écrite du
-- schéma après t_audit_log. Trois index couvrent les trois lectures prévues (GET /api/sessions trié par
-- date, echecsConnexion24h, sessionsOuvertes) ; IM_CONTROLEUR est déjà indexée depuis V8
-- (idx_session_utilisateur_ctrl). AUCUNE PURGE N'EST POSÉE : la rétention d'un journal de preuve est une
-- décision produit, pas un choix d'implémentation.
--
-- Idempotente : DROP CONSTRAINT dynamique, ADD COLUMN IF NOT EXISTS, CREATE INDEX IF NOT EXISTS, et la
-- reprise ne touche que les lignes encore nulles.
-- =====================================================================================================

-- (a) La FK vers tr_controleur d'abord : elle interdirait toute connexion de PRMP ou d'UGPM.
-- Retrait par recherche dans le catalogue et non par nom en dur : « FKamgm0ujyucg4w96x1dgfuqpi4 » est un
-- nom engendré par Hibernate (V1__baseline.sql, issu d'un pg_dump), qu'une base recréée autrement
-- n'aurait pas.
DO $$
DECLARE contrainte text;
BEGIN
    FOR contrainte IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'public.t_session_utilisateur'::regclass
          AND c.contype = 'f'
          AND c.confrelid = 'public.tr_controleur'::regclass
    LOOP
        EXECUTE format('ALTER TABLE public.t_session_utilisateur DROP CONSTRAINT %I', contrainte);
    END LOOP;
END $$;

ALTER TABLE public.t_session_utilisateur ALTER COLUMN "IM_CONTROLEUR" TYPE varchar(10);

ALTER TABLE public.t_session_utilisateur ADD COLUMN IF NOT EXISTS "LOGIN" varchar(100);

COMMENT ON COLUMN public.t_session_utilisateur."IM_CONTROLEUR" IS
    'V31 : reference de N''IMPORTE QUEL acteur connecte - IM_CONTROLEUR (tr_controleur), ID_PRMP (t_prmp) '
    'ou ID_UGPM (t_ugpm). Le nom de la colonne est HISTORIQUE et trompeur : il n''y a plus de cle etrangere '
    'vers tr_controleur, retiree ici, car une PRMP n''y figure pas. NULL quand la tentative a echoue sur un '
    'login inconnu : il n''existe alors aucun acteur a designer.';
COMMENT ON COLUMN public.t_session_utilisateur."LOGIN" IS
    'V31 : identifiant TENTE, renseigne a chaque tentative, reussie ou non. C''est la seule trace d''un '
    'echec sur un login inconnu (IM_CONTROLEUR est alors NULL).';

-- Lecture 1 — GET /api/sessions : tri date decroissante impose par le serveur, bornes ?du=/?au=.
CREATE INDEX IF NOT EXISTS idx_session_utilisateur_connexion
    ON public.t_session_utilisateur USING btree ("DATE_CONNEXION" DESC);

-- Lecture 2 — compteur echecsConnexion24h, et filtre ?succes=false du journal.
CREATE INDEX IF NOT EXISTS idx_session_utilisateur_succes_date
    ON public.t_session_utilisateur USING btree ("SUCCES", "DATE_CONNEXION" DESC);

-- Lecture 3 — compteur sessionsOuvertes. Index PARTIEL : une session ouverte est une exception
-- (quelques dizaines de lignes) au milieu de toutes celles qui ont ete fermees ou qui ont echoue.
CREATE INDEX IF NOT EXISTS idx_session_utilisateur_ouvertes
    ON public.t_session_utilisateur USING btree ("DATE_CONNEXION" DESC)
    WHERE "DATE_DECONNEXION" IS NULL;

-- (b) Date de depot de la demande d'inscription.
ALTER TABLE public.t_compte_auth ADD COLUMN IF NOT EXISTS "DATE_DEMANDE" timestamp(6) without time zone;

COMMENT ON COLUMN public.t_compte_auth."DATE_DEMANDE" IS
    'V31 : horodatage du DEPOT de la demande (creation du compte), a ne pas confondre avec DATE_DECISION '
    'qui date la validation ou le refus par l''Administrateur. Renseignee a l''inscription (@PrePersist de '
    'CompteAuth) ; c''est la source de CompteursAdminDto.inscriptionDoyenneLe.';

-- Reprise : l'ancienne derivation de KpiService, rejouee une fois pour l'existant.
UPDATE public.t_compte_auth c
SET "DATE_DEMANDE" = COALESCE(
        (SELECT min(p."DATE_DEPOT") FROM public.t_piece_jointe p WHERE p."LOGIN" = c."LOGIN"),
        (SELECT min(d."DATE_DECLARATION")::timestamp FROM public.t_prmp_entite_demande d
          WHERE d."LOGIN" = c."LOGIN"),
        c."DATE_DECISION")
WHERE c."DATE_DEMANDE" IS NULL;
