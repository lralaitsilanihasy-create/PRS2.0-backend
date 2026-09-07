-- V20 — DATE_SOUMISSION devient la date de DÉPÔT du dossier (demande pilote du 2026-09-06, « Suivi des
-- dossiers CNM » ; doc front docs/demande-backend-2026-09-06-date-soumission-dossier.md, commit a2a3312).
--
-- CE QUE LA RÈGLE PRÉCISE. « La date de soumission EST la date de dépôt du dossier » (pilote). Or
-- t_dossier.DATE_SOUMISSION était écrite à la CRÉATION du brouillon (POST /api/saisies/…), pas à la
-- soumission : sous un nom de soumission, elle portait une date de saisie. Le Secrétaire la lisait déjà
-- sur ReceptionDto.dateSoumission ; la PRMP va la lire sur DossierDto. Elle doit dire ce qu'elle
-- prétend dire. Depuis ce commit, elle est posée par POST /api/dossiers/{id}/soumettre, effacée par un
-- retrait accepté (retour en brouillon), et jamais posée à la création.
--
-- REPRISE. Le journal du dossier (t_action_dossier, type SOUMISSION) connaît la vraie date de dépôt de
-- tout dossier soumis depuis qu'il existe : elle prime sur la date de création. Trois cas :
--   1. dossier avec au moins une action SOUMISSION → DATE_SOUMISSION = la plus récente (un dossier
--      retiré puis re-soumis a pour dépôt sa dernière soumission) ;
--   2. dossier BROUILLON sans action SOUMISSION → NULL (jamais déposé : la date de création n'est pas
--      un dépôt) ;
--   3. dossier hors brouillon sans journal (antérieur au journal) → conservé tel quel : la date de
--      création reste la meilleure approximation disponible, faute de mieux.
--
-- Idempotente : chaque UPDATE est borné par sa condition ; rejouer ne change rien.

UPDATE public.t_dossier d
   SET "DATE_SOUMISSION" = s.derniere
  FROM (SELECT a."ID_DOSSIER", max(a."DATE_ACTION") AS derniere
          FROM public.t_action_dossier a
         WHERE a."TYPE_ACTION" = 'SOUMISSION'
         GROUP BY a."ID_DOSSIER") s
 WHERE s."ID_DOSSIER" = d."ID_DOSSIER"
   AND d."DATE_SOUMISSION" IS DISTINCT FROM s.derniere;

UPDATE public.t_dossier d
   SET "DATE_SOUMISSION" = NULL
 WHERE d."STATUT" = 'BROUILLON'
   AND d."DATE_SOUMISSION" IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM public.t_action_dossier a
                    WHERE a."ID_DOSSIER" = d."ID_DOSSIER" AND a."TYPE_ACTION" = 'SOUMISSION');

COMMENT ON COLUMN public.t_dossier."DATE_SOUMISSION" IS
    'Date-heure de DEPOT du dossier = sa soumission par la PRMP (POST /api/dossiers/{id}/soumettre). '
    'NULL pour un brouillon. Servie sur DossierDto.dateSoumission et ReceptionDto.dateSoumission.';
