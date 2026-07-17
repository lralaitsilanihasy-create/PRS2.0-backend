-- 2026-07-17 — Grille de contrôle affinée par SOUS-TYPE (⚠️ règle ajoutée).
--
-- tr_points_ctrl porte désormais un ID_SOUS_TYPE facultatif (FK tr_sous_type_dossier) :
--   NULL      = point COMMUN à toute la famille (ID_TYPE_DOSSIER) — les 7 points DDP existants ;
--   renseigné = point SPÉCIFIQUE à ce sous-type.
-- Grille effective d'un dossier (GET /api/points-ctrls?sousType=X) = communs + spécifiques de X.
-- La colonne est créée par Hibernate (ddl-auto) ; ce script ne fait que GARNIR le point AGPM.
--
-- ⚠️ Paramétrage ajouté : un point de contrôle SPÉCIFIQUE au sous-type PPM-AGPM (vérification de
-- l'AGPM joint), qui matérialise « grille d'un PPM ≠ grille d'un PPM-AGPM » (7 vs 8 points).
--
-- Ordre d'exécution : démarrer l'application (ddl-auto crée ID_SOUS_TYPE), PUIS exécuter ce script.
-- Idempotent : INSERT gardé par NOT EXISTS (repéré par famille+sous-type+libellé).

SET client_encoding TO 'UTF8';
BEGIN;

INSERT INTO tr_points_ctrl ("ID_POINT_CTRL", "LIBEL_POINT_CTRL", "DECRIPT_POINT_CTRL",
                            "ORDRE_POINT_CTRL", "OBLIGATOIRE", "ID_TYPE_DOSSIER", "ID_SOUS_TYPE")
SELECT (SELECT COALESCE(MAX("ID_POINT_CTRL"), 0) + 1 FROM tr_points_ctrl),
       'Avis Général de Passation de Marché joint et conforme',
       'L''AGPM accompagne le PPM et son contenu est cohérent avec les marchés en appel d''offres ouvert.',
       8, true, 'DDP', 'PPM-AGPM'
WHERE NOT EXISTS (
    SELECT 1 FROM tr_points_ctrl
    WHERE "ID_TYPE_DOSSIER" = 'DDP' AND "ID_SOUS_TYPE" = 'PPM-AGPM'
      AND "LIBEL_POINT_CTRL" = 'Avis Général de Passation de Marché joint et conforme'
);

COMMIT;

-- Vérifications :
--   SELECT count(*) FROM tr_points_ctrl WHERE "ID_TYPE_DOSSIER"='DDP' AND "ID_SOUS_TYPE" IS NULL;      -> 7 (communs)
--   SELECT count(*) FROM tr_points_ctrl WHERE "ID_TYPE_DOSSIER"='DDP' AND "ID_SOUS_TYPE"='PPM-AGPM';   -> 1
--   Grille PPM = 7 points ; grille PPM-AGPM = 8 points.

-- Réversion :
-- DELETE FROM tr_points_ctrl WHERE "ID_TYPE_DOSSIER"='DDP' AND "ID_SOUS_TYPE"='PPM-AGPM';
