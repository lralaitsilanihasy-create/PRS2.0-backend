-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- DBPRS20 — réparation du jeu de données du 2026-09-25 (dossier 100344, PV 35), signalement front du même jour
--
-- La réception 1100074 a été créée sans réceptionnaire (script front jeu-donnees-dao-2463.mjs : POST /api/receptions
-- par SECANT1 sans imCtrlRecept — l'écran, lui, l'envoie). Or la localité du circuit se lit sur le réceptionnaire
-- (PvExamenRepository.findLocaliteByPv et une cinquantaine de requêtes de périmètre) : sans lui, le PV n'a pas de
-- localité et le visa refuse TOUT co-signataire (§3.3). On pose SECANT1, qui l'a faite. Le serveur pose désormais
-- lui-même l'acteur courant quand le corps n'en donne pas (ReceptionService.create).
--
-- Rejouable. Usage : psql -U postgres -d DBPRS20 -v ON_ERROR_STOP=1 -f docs/demo/reparation-reception-1100074-2026-09-25.sql
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET client_encoding = 'UTF8';
BEGIN;

UPDATE public.t_reception SET "IM_CTRL_RECEPT" = 'SECANT1'
 WHERE "ID_RECEPTION" = 1100074 AND "ID_DOSSIER" = 100344 AND "IM_CTRL_RECEPT" IS NULL;

SELECT r."ID_RECEPTION", r."IM_CTRL_RECEPT", c."ID_LOCALITE" AS localite_du_circuit
  FROM public.t_reception r LEFT JOIN public.tr_controleur c ON c."IM_CONTROLEUR" = r."IM_CTRL_RECEPT"
 WHERE r."ID_RECEPTION" = 1100074;

COMMIT;
