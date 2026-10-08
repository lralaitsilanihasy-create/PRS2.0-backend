-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V85 — Le mode « Appel à manifestation d'intérêt » rattaché au type de DMC « DAO » (demande front du 2026-10-08,
-- `demande-backend-2026-10-08-mode-ami-type-dmc.md`, §B1 ; accord du pilote du 07/10).
--
-- Une ligne de prestations intellectuelles en mode AMI (le mode réel de ces marchés au plan de passation) doit créer sa fiche :
-- la fiche porte d'abord l'AMI, puis la demande de propositions (lot 3). La chaîne de la fiche (création du DMC, fiche, dossier,
-- lettres d'invitation) exige le type de DMC de code DAO : le mode y est rattaché (Q1), plutôt que de créer un type propre.
--
-- Q2 — le référentiel des modes n'a pas de code stable : le mode est reconnu à son LIBELLÉ, sans accents ni casse
-- (« manifestation » puis « interet ») ; le type, à son CODE (DAO), jamais à son identifiant. Un mode déjà rattaché par
-- l'Administrateur n'est pas touché. Sans effet là où ni l'un ni l'autre n'existe (base neuve) ; idempotente.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

UPDATE public.tr_mode_passation
   SET "ID_TYPE_DMC" = (SELECT "ID_TYPE_DMC" FROM public.t_type_dmc WHERE upper("CODE") = 'DAO' ORDER BY "ID_TYPE_DMC" LIMIT 1)
 WHERE "ID_TYPE_DMC" IS NULL
   AND translate(lower(coalesce("LIBELLE", '')), 'àâäéèêëîïôöùûüç', 'aaaeeeeiioouuuc') LIKE '%manifestation%interet%'
   AND EXISTS (SELECT 1 FROM public.t_type_dmc WHERE upper("CODE") = 'DAO');
