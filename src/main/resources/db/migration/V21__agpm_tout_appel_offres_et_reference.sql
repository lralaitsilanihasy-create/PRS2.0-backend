-- V21 — L'AGPM est requis pour TOUT appel d'offres, et la référence porte le sous-type dérivé
-- (arbitrage pilote du 2026-09-07 ; doc front docs/demande-backend-2026-09-07-reference-ppm-agpm.md,
-- commit 2128468). Constat : le dossier 100299 est de sous-type PPM-AGPM mais porte 00002/MTP/PPM/2026.
--
-- ═══ 1. LE DÉCLENCHEUR ═══
-- La règle n'était pas « appel d'offres OUVERT » mais « appel d'offres », toutes variantes : ouvert,
-- restreint, avec préqualification, en deux étapes. Seul « Appel d'offres ouvert » portait
-- DECLENCHE_AGPM ; « Appel d'offres restreint » ne le portait pas, et les modes créés à la volée par un
-- import PDF ne le portaient jamais (colonne laissée nulle). Le drapeau reste la source de vérité et
-- reste administrable : on le POSE ici sur toute la famille, on ne remplace pas la logique par un test
-- de libellé en dur.
--
-- Le motif « appel » + « offre » exclut volontairement l'« Appel à manifestation d'intérêt », qui n'est
-- pas une procédure d'appel d'offres. unaccent n'étant pas garanti sur l'installation, la comparaison
-- passe par translate() sur les seules voyelles accentuées utiles.
--
-- ═══ 2. LA RÉFÉRENCE ═══
-- Deux générateurs coexistent : la référence PPM initiale (00002/MTP/PPM/2026, dérivée de l'entité, posée
-- à la CRÉATION donc avant tout marché) et la référence de réception (00013/PPM/CRM-ANT/2026, qui prend
-- déjà le sous-type). La première codait « PPM » en dur, et comme elle a le format d'une référence
-- structurée, la réception la reprend telle quelle sans jamais appliquer le sous-type : d'où le constat.
-- Désormais le segment est recomposé à chaque recalcul du sous-type (sans consommer de numéro).
--
-- REPRISE. On réaligne ici l'existant, dossier + PPM + PV ENSEMBLE — le front relie le PV au dossier par
-- refePv.replace('/PV/','/') == refeDossier, désaligner les deux casserait la jointure. Bornée aux
-- dossiers dont AUCUN PV n'est signé : une référence imprimée sur un document officiel ne se renomme
-- pas. Le dossier 100299 (PV au statut PROJET_ACCEPTE) entre donc dans la reprise.
--
-- Idempotente : chaque UPDATE est borné par sa condition et ne fait rien s'il a déjà été joué.

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 1. Le drapeau AGPM sur tous les modes d'appel d'offres
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_mode_passation
   SET "DECLENCHE_AGPM" = true
 WHERE "DECLENCHE_AGPM" IS DISTINCT FROM true
   AND lower(translate(coalesce("LIBELLE", ''), 'ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûü',
                                               'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuu')) LIKE '%appel%'
   AND lower(translate(coalesce("LIBELLE", ''), 'ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûü',
                                               'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuu')) LIKE '%offre%';

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 2. Le sous-type des dossiers de planification, recalculé sur le drapeau à jour
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.t_dossier d
   SET "ID_SOUS_TYPE" = 'PPM-AGPM'
 WHERE d."ID_TYPE_DOSSIER" = 'DDP'
   AND d."ID_SOUS_TYPE" IS DISTINCT FROM 'PPM-AGPM'
   AND EXISTS (SELECT 1 FROM public.t_marche m
                 JOIN public.tr_mode_passation mo ON mo."ID_MODE" = m."ID_MODE"
                WHERE m."ID_DOSSIER" = d."ID_DOSSIER" AND mo."DECLENCHE_AGPM" = true);

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 3. Le segment de type des références, aligné sur le sous-type — dossier, PPM et PV ensemble
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- Dossiers éligibles : DDP, sous-type PPM-AGPM, aucun PV signé. Le segment '/PPM/' est remplacé par
-- '/PPM-AGPM/' ; le segment '/PV/' du numéro de PV n'est jamais touché (motif ancré sur '/PPM/').
CREATE TEMPORARY TABLE tmp_v21_dossiers AS
SELECT d."ID_DOSSIER"
  FROM public.t_dossier d
 WHERE d."ID_TYPE_DOSSIER" = 'DDP'
   AND d."ID_SOUS_TYPE" = 'PPM-AGPM'
   AND NOT EXISTS (SELECT 1 FROM public.t_pv_examen pv
                     JOIN public.t_examen e ON e."ID_EXAMEN" = pv."ID_EXAMEN"
                     JOIN public.t_dispatch di ON di."ID_DISPATCH" = e."ID_DISPATCH"
                     JOIN public.t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
                    WHERE r."ID_DOSSIER" = d."ID_DOSSIER" AND pv."STATUT_PV" = 'SIGNE');

UPDATE public.t_dossier d
   SET "REFE_DOSSIER" = replace(d."REFE_DOSSIER", '/PPM/', '/PPM-AGPM/')
  FROM tmp_v21_dossiers t
 WHERE t."ID_DOSSIER" = d."ID_DOSSIER"
   AND d."REFE_DOSSIER" LIKE '%/PPM/%';

UPDATE public.t_ppm p
   SET "REFERENCE" = replace(p."REFERENCE", '/PPM/', '/PPM-AGPM/')
  FROM tmp_v21_dossiers t
 WHERE t."ID_DOSSIER" = p."ID_DOSSIER"
   AND p."REFERENCE" LIKE '%/PPM/%';

UPDATE public.t_reception r
   SET "REFERENCE" = replace(r."REFERENCE", '/PPM/', '/PPM-AGPM/')
  FROM tmp_v21_dossiers t
 WHERE t."ID_DOSSIER" = r."ID_DOSSIER"
   AND r."REFERENCE" LIKE '%/PPM/%';

UPDATE public.t_pv_examen pv
   SET "REFE_PV" = replace(pv."REFE_PV", '/PPM/', '/PPM-AGPM/')
 WHERE pv."REFE_PV" LIKE '%/PPM/%'
   AND EXISTS (SELECT 1 FROM public.t_examen e
                 JOIN public.t_dispatch di ON di."ID_DISPATCH" = e."ID_DISPATCH"
                 JOIN public.t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
                 JOIN tmp_v21_dossiers t ON t."ID_DOSSIER" = r."ID_DOSSIER"
                WHERE e."ID_EXAMEN" = pv."ID_EXAMEN");

DROP TABLE tmp_v21_dossiers;
