-- ⚠️ Référentiel ajouté (2026-07-26) — catégories d'entité contractante + niveau hiérarchique.
-- Source unique du niveau : tr_entite_contract.CATEGORIE_ENTITE (texte validé) -> niveau dérivé.
-- Idempotent : CREATE IF NOT EXISTS (au cas où lancé AVANT le redémarrage qui crée la table via ddl-auto)
-- + INSERT ... ON CONFLICT DO NOTHING. Identifiants cités (globally_quoted_identifiers = true).

CREATE TABLE IF NOT EXISTS "tr_categorie_entite" (
    "LIBELLE"             varchar(20) NOT NULL,
    "NIVEAU_HIERARCHIQUE" integer     NOT NULL,
    CONSTRAINT "tr_categorie_entite_pkey" PRIMARY KEY ("LIBELLE")
);

INSERT INTO "tr_categorie_entite" ("LIBELLE", "NIVEAU_HIERARCHIQUE") VALUES
    ('MINISTERE',            1),
    ('CABINET',              2),   -- directement sous le Ministre, même niveau que le Secrétariat Général
    ('SECRETARIAT GENERAL',  2),
    ('DIRECTION GENERALE',   3),
    ('DIRECTION',            4),
    ('SERVICE',              5),
    ('DIVISION',             6)
ON CONFLICT ("LIBELLE") DO NOTHING;

-- Alignement des entités existantes : NIVEAU_HIERARCHIQUE dérivé de la catégorie (même règle que
-- EntiteContractService.deriverNiveau, appliquée aux données déjà en base). Idempotent : ne touche QUE les
-- lignes réellement désalignées ; une entité sans catégorie ou dont la catégorie est hors référentiel est
-- laissée intacte (jointure interne).
UPDATE "tr_entite_contract" e
   SET "NIVEAU_HIERARCHIQUE" = c."NIVEAU_HIERARCHIQUE"
  FROM "tr_categorie_entite" c
 WHERE e."CATEGORIE_ENTITE" = c."LIBELLE"
   AND e."NIVEAU_HIERARCHIQUE" IS DISTINCT FROM c."NIVEAU_HIERARCHIQUE";
