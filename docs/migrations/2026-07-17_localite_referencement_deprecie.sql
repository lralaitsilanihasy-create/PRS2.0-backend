-- 2026-07-17 — Dépréciation de tr_localite.REFERENCEMENT (⚠️ retrait du contrat API).
--
-- Constat : la colonne (héritée du MLD, sans description ni sémantique) n'est LUE nulle part —
-- ni par la génération des références (le segment « CRM-ANT » utilise la PK ID_LOCALITE),
-- ni par les documents (PV / lettres : LIBELLE_LOCALITE), ni par aucun job. Valeurs en base
-- trivialement dérivables (« REF-<id> »). L'admin devait pourtant la saisir (@NotBlank).
--
-- Décision : champ RETIRÉ du contrat (LocaliteDto/entité/validation) ; colonne CONSERVÉE en base
-- mais rendue NULLABLE (les créations ne la renseignent plus). Suppression physique envisageable
-- plus tard (DROP COLUMN) une fois le front aligné.
-- Idempotent : DROP NOT NULL est sans effet si déjà nullable.

ALTER TABLE tr_localite ALTER COLUMN "REFERENCEMENT" DROP NOT NULL;

-- Vérification :
--   SELECT is_nullable FROM information_schema.columns
--   WHERE table_name='tr_localite' AND column_name='REFERENCEMENT';   -> YES

-- Réversion (nécessite de re-renseigner les lignes NULL d'abord) :
--   UPDATE tr_localite SET "REFERENCEMENT" = 'REF-' || "ID_LOCALITE" WHERE "REFERENCEMENT" IS NULL;
--   ALTER TABLE tr_localite ALTER COLUMN "REFERENCEMENT" SET NOT NULL;
