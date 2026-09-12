-- =====================================================================================================
-- V28 — Le chronométrage se passe de la PRISE EN CHARGE (demande pilote du 2026-09-12)
--
-- LA REGLE. « Supprimer la prise en charge du chronométrage. Le délai par étape est automatique : fin
-- d'étape − entrée dans l'étape, en heures ouvrées, dérivé des transitions déjà horodatées. Aucune
-- saisie. » Réception, dispatch, examen, visa, signature, vérification et archivage s'exécutent donc
-- directement ; POST /api/dossiers/{id}/prise-en-charge et la garde « aucune action sans PEC »
-- disparaissent.
--
-- CE QUE DEVIENT t_tache_dossier. Elle ne porte plus qu'une chose : la FIN de chaque passage par une
-- étape — l'horodatage du geste métier qui l'achève, celui-là même qui datait déjà la frise et le
-- journal. Trois colonnes n'ont plus d'objet et sont retirées :
--
--   * DATE_PRISE_EN_CHARGE — l'entrée dans une étape est désormais DERIVEE : c'est la fin du passage
--     précédent (ou, si elle est postérieure, le dépôt du dossier / la sortie d'une attente PRMP). La
--     stocker à côté aurait été une COPIE de la ligne précédente : deux colonnes censées porter le même
--     instant, donc deux occasions de diverger, et un écart qui ne se verrait qu'en recette.
--   * PREVISION_HEURES / PREVISION_STANDARD — la prévision du porteur était la seule saisie du
--     chronométrage. Elle n'existe plus : la date annoncée à la PRMP s'appuie sur le seul référentiel
--     administrable tr_delai_standard, qui reste en place (c'est même devenu sa seule raison d'être).
--
-- LES LIGNES ENCORE OUVERTES SONT SUPPRIMEES, ET C'EST VOULU. Une ligne sans DATE_FIN ne disait qu'une
-- chose — « quelqu'un a pris cette étape en charge » — et c'est précisément la notion qu'on retire. Le
-- passage, lui, n'est pas perdu : l'étape en cours d'un dossier se déduit de son statut, et sa durée
-- court depuis la fin du passage précédent. Aucune étape TERMINEE n'est touchée : leurs fins sont la
-- matière même du nouveau calcul, et les durées de l'historique se recalculent donc toutes seules.
--
-- DATE_FIN DEVIENT OBLIGATOIRE : une ligne n'existe que parce qu'un passage s'est achevé. C'est le
-- garde-fou qui empêche la prise en charge de revenir par la fenêtre.
--
-- Idempotente : chaque étape est gardée par un test d'existence de colonne.
-- =====================================================================================================

-- 1. Les prises en charge jamais closes : rien à mesurer, la notion disparaît avec elles.
DELETE FROM public.t_tache_dossier WHERE "DATE_FIN" IS NULL;

-- 2. Les colonnes de la prise en charge et de la prévision saisie.
ALTER TABLE public.t_tache_dossier DROP COLUMN IF EXISTS "DATE_PRISE_EN_CHARGE";
ALTER TABLE public.t_tache_dossier DROP COLUMN IF EXISTS "PREVISION_HEURES";
ALTER TABLE public.t_tache_dossier DROP COLUMN IF EXISTS "PREVISION_STANDARD";

-- 3. Une ligne = une étape terminée.
ALTER TABLE public.t_tache_dossier ALTER COLUMN "DATE_FIN" SET NOT NULL;

-- L'index servait à retrouver les occurrences OUVERTES d'un dossier (ID_DOSSIER, DATE_FIN) ; il sert
-- maintenant à les lire dans l'ordre des fins, qui est l'ordre de la chaîne dont on dérive les entrées.
DROP INDEX IF EXISTS public.idx_tache_dossier_ouverte;
CREATE INDEX IF NOT EXISTS idx_tache_dossier_chaine
    ON public.t_tache_dossier ("ID_DOSSIER", "DATE_FIN", "ID_TACHE");

COMMENT ON TABLE public.t_tache_dossier IS
    'Passages par les etapes du circuit, append-only : une ligne = une etape TERMINEE (2026-09-12). '
    'L''entree dans l''etape n''est pas stockee, elle est derivee de la fin du passage precedent ; la '
    'duree en heures ouvrees s''en deduit. Plus de prise en charge, plus de prevision saisie.';

COMMENT ON COLUMN public.t_tache_dossier."DATE_FIN" IS
    'Instant du geste metier qui a acheve l''etape. Jamais nul.';
