-- V17 — Navette du PV à DEUX NIVEAUX et co-signature élargie (spec pilote du 2026-09-04).
--
-- CE QUE LA RÈGLE AJOUTE. Quand un dossier CENTRAL a été dispatché par le Président au Chef de
-- commission, PUIS réattribué par ce CC à un Membre, la navette du projet de PV suit le même chemin,
-- à deux étages : le Membre soumet AU CC, le CC accepte et TRANSMET au Président, le Président vise
-- — ou retourne AU CC, qui redescend au Membre ou re-transmet. Rien ne saute d'étage.
--
-- POURQUOI UNE COLONNE NIVEAU_NAVETTE, ET NON UNE DÉRIVATION. Le statut du PV ne suffit pas : entre
-- « soumis au CC » et « transmis au Président », il vaut PROJET_SOUMIS dans les deux cas. Le niveau
-- est un ÉTAT COURANT du circuit, au même titre que STATUT_PV — pas un drapeau calculable depuis les
-- autres colonnes. Le déduire de la dernière ligne de t_pv_navette serait possible, mais coûterait
-- une requête par PV sur les listes (« Projets de PV »), pour une information que le front lit à
-- chaque affichage. NULL = navette simple (un seul niveau), ou PV hors navette P/CC.
--
-- POURQUOI IM_CC_COSIGNATAIRE, ET NON IM_CTRL_CC. IM_CTRL_CC désigne le CC qui a VISÉ ou SIGNÉ ;
-- c'est lui qu'imprime le bloc de signature du PV. La co-signature élargie du 2026-09-04 introduit
-- autre chose : un CC DÉSIGNÉ par le Président au visa, qui signera SA part plus tard. Confondre les
-- deux rendrait indécidable la bascule en SIGNE (« ce CC doit-il encore signer, ou a-t-il déjà
-- signé ? ») et ferait porter au document le nom d'un signataire qui ne l'est pas encore. Même
-- raisonnement, et même convention, que IM_MEMBRE_COSIGNATAIRE face à IM_CTRL_MEMBRE (cf. V10).
--
-- POURQUOI ÉLARGIR SENS. Les deux nouveaux sens de navette sont TRANSMISSION_PRESIDENT (22
-- caractères) et RETOUR_CC. Le premier déborde du varchar(20) d'origine. Abréger le libellé aurait
-- été possible — la spec dit « ou équivalent » — mais SENS est lu tel quel par le front pour
-- étiqueter l'historique de navette : un nom tronqué serait une dette de contrat, pas une économie.
-- 30 laisse la marge d'un sens futur sans re-migration.
--
-- AUCUNE REPRISE DE DONNÉES. Les PV en cours au déploiement gardent NIVEAU_NAVETTE à NULL : ils sont
-- en navette SIMPLE et le restent, quel que soit le chemin de dispatch de leur dossier. Le niveau ne
-- se pose qu'à la SOUMISSION, geste qu'ils ont déjà passé. Requalifier a posteriori déplacerait un
-- PV déjà soumis au Président vers un CC qui ne l'attend pas.
--
-- Idempotente : ADD COLUMN IF NOT EXISTS, et l'élargissement d'un varchar est rejouable.

ALTER TABLE public.t_pv_examen
    ADD COLUMN IF NOT EXISTS "NIVEAU_NAVETTE" character varying(20);

COMMENT ON COLUMN public.t_pv_examen."NIVEAU_NAVETTE" IS
    'Etage courant de la navette a deux niveaux : CC (le projet est chez le Chef de commission) ou '
    'PRESIDENT (le CC l''a transmis). NULL = navette simple, ou projet hors navette P/CC.';

ALTER TABLE public.t_pv_examen
    ADD COLUMN IF NOT EXISTS "IM_CC_COSIGNATAIRE" character varying(7);

COMMENT ON COLUMN public.t_pv_examen."IM_CC_COSIGNATAIRE" IS
    'Chef de commission DESIGNE par le President au visa pour co-signer le PV (il signera sa part '
    'ensuite). Distinct de IM_CTRL_CC, qui designe le CC ayant effectivement vise ou signe.';

ALTER TABLE public.t_pv_navette
    ALTER COLUMN "SENS" TYPE character varying(30);

-- ----------------------------------------------------------------------------
-- LA CONTRAINTE QUI EXIGEAIT UNE SIGNATURE DU MEMBRE
-- ----------------------------------------------------------------------------
-- t_pv_examen_cosignataire_check (baseline V1) imposait, sur tout PV SIGNE :
--     DATE_SIGNATURE_MEMBRE IS NOT NULL AND (DATE_SIGNATURE_PRESIDENT OR DATE_SIGNATURE_CC)
-- Autrement dit : le Membre signe TOUJOURS. C'est précisément ce que l'arbitrage 3 du 2026-09-04
-- lève — la combinaison « Président + Chef de commission », sans Membre, est désormais valide.
-- Laisser la contrainte en l'état aurait fait échouer la signature en base après que toutes les
-- gardes métier l'aient acceptée : un 409 « violation de contrainte » sans message utile, sur un
-- geste légitime. La règle est close en DEUX endroits (le service et la base) ; les deux bougent.
--
-- CE QUE LA NOUVELLE CONTRAINTE GARANTIT, et qui est la règle réelle du pilote :
--   1. le PV signé porte AU MOINS DEUX signatures — « au minimum 2 personnes distinctes au total » ;
--   2. le viseur a signé (Président ou CC) — un PV ne se clôt pas sans lui ;
--   3. CHAQUE désigné a posé sa part : un co-signataire nommé au visa ne peut pas être oublié.
--      C'est le miroir en base de PvExamenService#partsCompletes — la garantie ne dépend donc plus
--      du seul chemin applicatif.
--
-- Ce qu'elle ne dit plus : « le Membre a signé ». Ce n'était pas une règle d'intégrité, c'était
-- l'ancienne composition du PV.
--
-- Idempotente : DROP ... IF EXISTS puis ADD. Rejouée, elle repose la même contrainte.

ALTER TABLE public.t_pv_examen
    DROP CONSTRAINT IF EXISTS t_pv_examen_cosignataire_check;

ALTER TABLE public.t_pv_examen
    ADD CONSTRAINT t_pv_examen_cosignataire_check CHECK (
        ("STATUT_PV")::text <> 'SIGNE'::text
        OR (
            ("DATE_SIGNATURE_PRESIDENT" IS NOT NULL OR "DATE_SIGNATURE_CC" IS NOT NULL)
            AND ("IM_MEMBRE_COSIGNATAIRE" IS NULL OR "DATE_SIGNATURE_MEMBRE" IS NOT NULL)
            AND ("IM_CC_COSIGNATAIRE" IS NULL OR "DATE_SIGNATURE_CC" IS NOT NULL)
            AND (
                (CASE WHEN "DATE_SIGNATURE_PRESIDENT" IS NOT NULL THEN 1 ELSE 0 END)
                + (CASE WHEN "DATE_SIGNATURE_CC" IS NOT NULL THEN 1 ELSE 0 END)
                + (CASE WHEN "DATE_SIGNATURE_MEMBRE" IS NOT NULL THEN 1 ELSE 0 END)
            ) >= 2
        )
    );
