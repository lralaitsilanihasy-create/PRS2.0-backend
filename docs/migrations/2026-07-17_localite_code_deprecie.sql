-- 2026-07-17 — Dépréciation de tr_localite.LOCALITE (code max 3, ⚠️ retrait du contrat).
--
-- Même traitement que REFERENCEMENT (2026-07-17_localite_referencement_deprecie.sql) : la colonne
-- (héritée du MLD, sans description) n'est LUE nulle part — le segment localité des références
-- (« CRM-ANT ») est bâti sur la PK ID_LOCALITE, les documents lisent LIBELLE_LOCALITE. Les valeurs
-- en base dupliquent la PK (ANT/TMS). L'admin devait pourtant la saisir (@NotBlank).
--
-- Décision : champ RETIRÉ du contrat (LocaliteDto/entité/validation) ; colonne CONSERVÉE en base
-- mais rendue NULLABLE. L'écran admin se réduit à id / libellé.
-- Idempotent : DROP NOT NULL est sans effet si déjà nullable.

ALTER TABLE tr_localite ALTER COLUMN "LOCALITE" DROP NOT NULL;

-- Vérification :
--   SELECT is_nullable FROM information_schema.columns
--   WHERE table_name='tr_localite' AND column_name='LOCALITE';   -> YES

-- Réversion (re-renseigner les lignes NULL d'abord) :
--   UPDATE tr_localite SET "LOCALITE" = substr("ID_LOCALITE", 1, 3) WHERE "LOCALITE" IS NULL;
--   ALTER TABLE tr_localite ALTER COLUMN "LOCALITE" SET NOT NULL;
