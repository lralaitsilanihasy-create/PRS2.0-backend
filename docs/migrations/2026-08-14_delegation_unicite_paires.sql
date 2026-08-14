-- 2026-08-14 — Délégation ascendante de profils : unicité de la paire (délégant, délégué).
-- La table t_delegation_profil devient la SOURCE UNIQUE de la règle « qui peut exercer les
-- tâches de qui » (garde centrale PermissionService.peutExercer). Une seule ligne par paire :
-- l'habilitation se pilote par ACTIF (true/false), jamais par des doublons.
--
-- NB : les 9 paires officielles (Président → Secrétaire/CC/Membre/Vérificateur/Assistant ;
-- CC → Secrétaire/Membre/Vérificateur/Assistant) sont créées par le seed applicatif
-- DelegationHierarchieSeeder au démarrage (idempotent, ne touche jamais une paire existante).

-- 1) Dédoublonnage préalable : on garde, par paire, la ligne de plus petit ID_DELEGATION,
--    en la forçant ACTIVE si N'IMPORTE QUEL doublon de la paire était actif (pas de perte de droit).
UPDATE "t_delegation_profil" d
SET "ACTIF" = TRUE
WHERE d."ID_DELEGATION" = (
        SELECT MIN(d2."ID_DELEGATION") FROM "t_delegation_profil" d2
        WHERE d2."ID_PROFILE_DELEGANT" = d."ID_PROFILE_DELEGANT"
          AND d2."ID_PROFILE_DELEGUE" = d."ID_PROFILE_DELEGUE")
  AND EXISTS (
        SELECT 1 FROM "t_delegation_profil" d3
        WHERE d3."ID_PROFILE_DELEGANT" = d."ID_PROFILE_DELEGANT"
          AND d3."ID_PROFILE_DELEGUE" = d."ID_PROFILE_DELEGUE"
          AND d3."ACTIF" = TRUE);

DELETE FROM "t_delegation_profil" d
WHERE d."ID_DELEGATION" <> (
        SELECT MIN(d2."ID_DELEGATION") FROM "t_delegation_profil" d2
        WHERE d2."ID_PROFILE_DELEGANT" = d."ID_PROFILE_DELEGANT"
          AND d2."ID_PROFILE_DELEGUE" = d."ID_PROFILE_DELEGUE");

-- 2) Contrainte d'unicité sur la paire.
ALTER TABLE "t_delegation_profil"
    ADD CONSTRAINT "UQ_DELEGATION_PAIRE" UNIQUE ("ID_PROFILE_DELEGANT", "ID_PROFILE_DELEGUE");

-- Rollback :
-- ALTER TABLE "t_delegation_profil" DROP CONSTRAINT "UQ_DELEGATION_PAIRE";
-- (le dédoublonnage n'est pas réversible — les doublons supprimés étaient redondants par définition)
