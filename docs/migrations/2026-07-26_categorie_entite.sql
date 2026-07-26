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

-- NB : les 3 entités actuelles (MINISTERE x2, DIRECTION) référencent déjà des catégories du seed.
-- Leur NIVEAU_HIERARCHIQUE n'est PAS rétro-corrigé ici (ex. entité 4 = DIRECTION mais niveau 1) :
-- il sera aligné au prochain PUT /api/entite-contracts (dérivation), ou via un UPDATE manuel si souhaité.
