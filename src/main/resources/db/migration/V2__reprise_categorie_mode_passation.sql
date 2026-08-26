-- V2 — Reprise de données (conversion de l'ex-CategorieModePassationMigration, runner Java
-- supprimé au chantier LOT 2, 2026-08-26 ; règle d'origine : 2026-08-13).
--
-- tr_mode_passation.CATEGORIE a été ajoutée à chaud (ddl-auto=update) : NULL sur l'existant.
-- Seule classification officielle et déterministe : le Code des marchés publics fait de
-- l'appel d'offres ouvert le mode de droit commun, marqué par DECLENCHE_AGPM (jamais de
-- détection par mot-clé de libellé) → catégorie NORMAL. Les autres restent NULL (non
-- classés) : l'Administrateur les classe via l'écran référentiel.
-- Ne remplit que les NULL — jamais d'écrasement d'un classement admin. Idempotente.
UPDATE public.tr_mode_passation
SET "CATEGORIE" = 'NORMAL'
WHERE "DECLENCHE_AGPM" = true
  AND "CATEGORIE" IS NULL;
