-- V19 — Une observation du PV connaît sa LIGNE D'EXAMEN (règle pilote du 2026-09-06 : rectification
-- avec écart toléré de 3 lignes par sens ; demande front docs/demande-backend-2026-09-06-rectification-
-- ecart-3-lignes.md, commit 4828431).
--
-- CE QUE LA RÈGLE AJOUTE. La rectification n'est plus à structure strictement figée : la PRMP peut
-- retirer jusqu'à 3 lignes de marché. Or les observations du PV portent sur des lignes ; en retirer
-- une qui porte une observation NON LEVÉE reviendrait à escamoter l'observation au lieu d'y répondre.
-- Le serveur refuse donc ce retrait — encore faut-il savoir, pour chaque observation, quelle ligne de
-- marché elle vise.
--
-- POURQUOI UNE COLONNE. t_observation_pv est le PÉRIMÈTRE FIGÉ des observations arrêtées au PV (snapshot
-- à la signature). Une observation « POINT » ne pointe aujourd'hui vers sa ligne d'examen qu'à travers
-- ID_OBSERVATION_CTRL — et seulement quand le Membre a saisi des lignes détaillées « Au lieu de /
-- Lire ». Un point non conforme SANS ligne détaillée (le cas courant : « non conforme » + texte libre)
-- n'est rattaché à rien : impossible de dire quelle ligne de marché il concerne. ID_DETAIL_EXAMEN fige
-- ce rattachement au moment du snapshot, pour toutes les observations « POINT », avec ou sans lignes
-- détaillées. De la ligne d'examen (t_examen_detail.ID_DETAIL) on remonte au marché.
--
-- REPRISE. Les observations existantes qui portent un ID_OBSERVATION_CTRL en héritent (la ligne
-- d'observation connaît sa ligne d'examen) ; les autres restent NULL — une observation « POINT »
-- ancienne sans ligne détaillée ne peut pas être rattachée après coup, et ne protège donc pas sa
-- ligne. Pas de FK : c'est un snapshot, et la purge du circuit efface les observations avant les lignes
-- d'examen (ordre déjà en place dans CircuitCascadeService).
--
-- Idempotente : ADD COLUMN IF NOT EXISTS, reprise bornée aux lignes encore NULL.

ALTER TABLE public.t_observation_pv
    ADD COLUMN IF NOT EXISTS "ID_DETAIL_EXAMEN" integer;

COMMENT ON COLUMN public.t_observation_pv."ID_DETAIL_EXAMEN" IS
    'Ligne d''examen (t_examen_detail.ID_DETAIL_EXAMEN) visee par une observation POINT, figee au '
    'snapshot du PV ; NULL pour une observation PIECE ou anterieure a la V19 sans ligne detaillee.';

UPDATE public.t_observation_pv o
   SET "ID_DETAIL_EXAMEN" = oc."ID_DETAIL"
  FROM public.t_observation_controle oc
 WHERE oc."ID_OBSERVATION" = o."ID_OBSERVATION_CTRL"
   AND o."ID_DETAIL_EXAMEN" IS NULL;

CREATE INDEX IF NOT EXISTS idx_observation_pv_detail_examen
    ON public.t_observation_pv USING btree ("ID_DETAIL_EXAMEN");
