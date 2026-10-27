-- ⚠️ 2026-10-27 (demande front « numero-dossier-unique », N1-N2 ; arbitrages du pilote) — un seul compteur de dossiers par exercice,
-- toutes familles confondues (plans, mise en concurrence, marchés…), national, attribué à la réception.
-- Clé du compteur unique : TYPE_DOSSIER = 'TOUS', CODE_LOCALITE = 'DOSSIER'. Les compteurs par famille (CODE_LOCALITE = 'DOSSIER',
-- TYPE_DOSSIER = DDP, DMC, DDM…) ne sont plus incrémentés ; ils restent en place, comme les références déjà attribuées.
-- N2 : pour chaque exercice déjà ouvert, le compteur unique reprend après le plus grand numéro attribué, toutes familles confondues.
-- Les lettres de renvoi (LR / LETTRE) et les références PPM (PPM_REF / entité) gardent leurs compteurs.

INSERT INTO public.t_sequence_reference ("TYPE_DOSSIER", "CODE_LOCALITE", "ANNEE_EXERCICE", "DERNIERE_VALEUR")
SELECT 'TOUS', 'DOSSIER', s."ANNEE_EXERCICE", max(s."DERNIERE_VALEUR")
  FROM public.t_sequence_reference s
 WHERE s."CODE_LOCALITE" = 'DOSSIER' AND s."TYPE_DOSSIER" <> 'TOUS'
 GROUP BY s."ANNEE_EXERCICE"
ON CONFLICT ("ANNEE_EXERCICE", "CODE_LOCALITE", "TYPE_DOSSIER")
DO UPDATE SET "DERNIERE_VALEUR" = greatest(public.t_sequence_reference."DERNIERE_VALEUR", EXCLUDED."DERNIERE_VALEUR");
