-- V22 — L'appel à manifestation d'intérêt déclenche l'AGPM, mais SOUS CONDITION DE MONTANT
-- (arbitrage pilote du 2026-09-07, « Suite » de docs/demande-backend-2026-09-07-reference-ppm-agpm.md).
--
-- CE QUE LA RÈGLE AJOUTE. La V21 a posé DECLENCHE_AGPM sur toute la famille « appel d'offres » et a
-- volontairement EXCLU l'appel à manifestation d'intérêt. Le pilote l'en sort : l'AMI déclenche l'AGPM
-- lui aussi — mais seulement si le marché atteint un montant. Ce n'est donc pas un second booléen
-- inconditionnel : c'est un déclenchement CONDITIONNEL, qui se dit en deux morceaux.
--
--   1. QUELS MODES sont conditionnels → colonne AGPM_SI_SEUIL, ici, sur le référentiel des modes.
--      Administrable au même titre que DECLENCHE_AGPM (écran des modes de passation).
--   2. À PARTIR DE QUEL MONTANT → paramètre AGPM_SEUIL_MONTANT dans t_parametre, saisi et ajusté par
--      le pilote depuis l'administration, sans redéploiement. AUCUNE valeur métier n'est écrite en dur
--      dans le code : la règle vit ici, le chiffre vit dans l'admin.
--
-- POURQUOI PAS UN TEST DE LIBELLÉ. Reconnaître « manifestation d'intérêt » dans le code aurait figé la
-- règle : le pilote n'aurait pas pu déclarer un mode conditionnel de plus sans redéploiement. Le libellé
-- ne sert qu'à POSER le drapeau — ici pour l'existant, et à la volée pour un mode créé par un import PDF,
-- qui n'apporte qu'un libellé. Ensuite, c'est le drapeau qui fait foi.
--
-- LA COMPARAISON EST PAR MARCHÉ, jamais sur le total du dossier : c'est la maille de la dérivation depuis
-- toujours (un seul marché déclencheur suffit à faire basculer le plan), et le pilote l'a confirmée. Le
-- montant retenu est celui EN VIGUEUR — le nouveau montant estimatif s'il a été posé, l'initial sinon.
--
-- LE SEUIL PAR DÉFAUT EST ZÉRO, c'est-à-dire « tout marché AMI déclenche l'AGPM » tant que le pilote n'a
-- pas saisi le sien. Le défaut penche du côté de la publicité : manquer un AGPM dû est un manquement
-- réglementaire, en produire un de trop ne l'est pas. C'est la seule valeur que la demande ne fixait pas ;
-- elle se resserre d'une saisie, sans redéploiement.
--
-- Idempotente : ADD COLUMN IF NOT EXISTS, insertion du paramètre conditionnelle, reprises bornées.

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 1. Quels modes déclenchent l'AGPM sous condition de montant
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.tr_mode_passation
    ADD COLUMN IF NOT EXISTS "AGPM_SI_SEUIL" boolean;

COMMENT ON COLUMN public.tr_mode_passation."AGPM_SI_SEUIL" IS
    'Ce mode declenche l''AGPM SI le montant estime du marche atteint le seuil administrable '
    '(t_parametre.AGPM_SEUIL_MONTANT). Porte par l''appel a manifestation d''interet. Independant de '
    'DECLENCHE_AGPM, qui est le declenchement inconditionnel (appels d''offres).';

UPDATE public.tr_mode_passation
   SET "AGPM_SI_SEUIL" = true
 WHERE "AGPM_SI_SEUIL" IS DISTINCT FROM true
   AND lower(translate(coalesce("LIBELLE", ''), 'ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûü',
                                               'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuu')) LIKE '%manifestation%'
   AND lower(translate(coalesce("LIBELLE", ''), 'ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûü',
                                               'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuu')) LIKE '%interet%';

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 2. Le seuil, dans l'administration
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO public.t_parametre ("CLE", "VALEUR", "DATE_MAJ", "IM_ACTEUR")
SELECT 'AGPM_SEUIL_MONTANT', '0', now(), NULL
 WHERE NOT EXISTS (SELECT 1 FROM public.t_parametre WHERE "CLE" = 'AGPM_SEUIL_MONTANT');

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 3. Reprise : sous-type et références des plans que la nouvelle règle fait basculer
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- Même critère que la V21, enrichi du déclenchement conditionnel : un marché dont le mode est
-- conditionnel et dont le montant EN VIGUEUR atteint le seuil courant.
UPDATE public.t_dossier d
   SET "ID_SOUS_TYPE" = 'PPM-AGPM'
 WHERE d."ID_TYPE_DOSSIER" = 'DDP'
   AND d."ID_SOUS_TYPE" IS DISTINCT FROM 'PPM-AGPM'
   AND EXISTS (SELECT 1 FROM public.t_marche m
                 JOIN public.tr_mode_passation mo ON mo."ID_MODE" = m."ID_MODE"
                WHERE m."ID_DOSSIER" = d."ID_DOSSIER"
                  AND mo."AGPM_SI_SEUIL" = true
                  AND coalesce(m."NOUV_MONT_ESTIM", m."MONT_ESTIM")
                      >= (SELECT coalesce(nullif("VALEUR", '')::numeric, 0)
                            FROM public.t_parametre WHERE "CLE" = 'AGPM_SEUIL_MONTANT'));

-- Le segment de référence suit, dossier + PPM + réception + PV ENSEMBLE (le front relie le PV au
-- dossier par refePv.replace('/PV/','/') == refeDossier), et seulement tant qu'aucun PV n'est signé :
-- une référence imprimée sur un document officiel ne se renomme pas. Même règle qu'en V21.
CREATE TEMPORARY TABLE tmp_v22_dossiers AS
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
  FROM tmp_v22_dossiers t
 WHERE t."ID_DOSSIER" = d."ID_DOSSIER" AND d."REFE_DOSSIER" LIKE '%/PPM/%';

UPDATE public.t_ppm p
   SET "REFERENCE" = replace(p."REFERENCE", '/PPM/', '/PPM-AGPM/')
  FROM tmp_v22_dossiers t
 WHERE t."ID_DOSSIER" = p."ID_DOSSIER" AND p."REFERENCE" LIKE '%/PPM/%';

UPDATE public.t_reception r
   SET "REFERENCE" = replace(r."REFERENCE", '/PPM/', '/PPM-AGPM/')
  FROM tmp_v22_dossiers t
 WHERE t."ID_DOSSIER" = r."ID_DOSSIER" AND r."REFERENCE" LIKE '%/PPM/%';

UPDATE public.t_pv_examen pv
   SET "REFE_PV" = replace(pv."REFE_PV", '/PPM/', '/PPM-AGPM/')
 WHERE pv."REFE_PV" LIKE '%/PPM/%'
   AND EXISTS (SELECT 1 FROM public.t_examen e
                 JOIN public.t_dispatch di ON di."ID_DISPATCH" = e."ID_DISPATCH"
                 JOIN public.t_reception r ON r."ID_RECEPTION" = di."ID_RECEPTION"
                 JOIN tmp_v22_dossiers t ON t."ID_DOSSIER" = r."ID_DOSSIER"
                WHERE e."ID_EXAMEN" = pv."ID_EXAMEN");

DROP TABLE tmp_v22_dossiers;
