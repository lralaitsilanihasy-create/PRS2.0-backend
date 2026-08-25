-- 2026-08-25 — PK auto de t_service_beneficiaire (Voie B, séquence serveur).
-- Migration manuelle (PostgreSQL / pgAdmin) — pas de Flyway (cf. CLAUDE.md).
--
-- Motif corrigé : ServiceBeneficiaireService.create
-- et les boucles de SaisieService.creerBeneficiaires / MiseAJourPpmService.copierLignes allouaient la
-- PK par max(ID_BENEF) + 1, une fois puis incrémentée localement — même défaut que t_lot : la
-- ventilation suivante écrasait la précédente.
-- Le serveur alloue toujours la PK via nextval('seq_service_beneficiaire') et ignore tout id envoyé par le client.
--
-- ⚠️ RANG : la séquence est positionnée sur max(ID_BENEF) + 1 CALCULÉ SUR LA TABLE, et non sur une
-- constante. Une constante devinée trop bas collisionne dès le premier appel en production. Le
-- calcul ci-dessous est à exécuter TEL QUEL sur la base cible, sans le remplacer par un nombre.

CREATE SEQUENCE IF NOT EXISTS seq_service_beneficiaire;

-- Positionne la séquence au bon rang : le prochain nextval() rendra max(ID_BENEF) + 1.
-- (`false` en 3e argument = « valeur non encore consommée » → nextval rend exactement cette valeur.)
SELECT setval('seq_service_beneficiaire', (SELECT coalesce(max("ID_BENEF"), 0) FROM t_service_beneficiaire) + 1, false);

-- Vérification (à exécuter APRÈS, avant d'ouvrir le service) : la 2e colonne doit être
-- strictement supérieure à la 1re. Sinon, NE PAS déployer et rejouer le setval ci-dessus.
-- SELECT (SELECT coalesce(max("ID_BENEF"), 0) FROM t_service_beneficiaire) AS max_actuel,
--        last_value                                        AS prochain_nextval
--   FROM seq_service_beneficiaire;
