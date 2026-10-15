-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V121 — ⚠️ 2026-10-15 : demande front « marchés subséquents », §B0.1 (décret n° 2019-1064 du 28 mai 2019,
-- article 10, remis par le pilote le 15/10).
--
-- Le délai de remise d'un contrat-cadre n'est plus un paramètre « à confirmer » (30 jours, V106) : le décret le
-- fixe à 10 jours au moins par appel d'offres et à 5 jours calendaires par consultation. Ces deux valeurs
-- rejoignent la table des délais légaux, en code (DelaisLegaux, n° 10 et 11), bloquantes. Le paramètre est
-- désactivé, gardé pour l'historique de l'écran Délais. Idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.t_delai_parametre
   SET "ACTIF" = false,
       "TEXTE" = 'Contrat-cadre : remplacé le 15/10/2026 par le décret n° 2019-1064, art. 10 (10 jours par appel d''offres, 5 par consultation : délais légaux n° 10 et 11)'
 WHERE "CODE" = 'PUBLICITE_CONTRAT_CADRE';
