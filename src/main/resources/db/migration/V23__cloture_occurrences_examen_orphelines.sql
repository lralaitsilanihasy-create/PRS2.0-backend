-- =====================================================================================================
-- V23 — Reprise : clôture des occurrences EXAMEN laissées OUVERTES par une réattribution ou un retrait
--       antérieurs au correctif (signalement pilote du 2026-09-08, dossier 00305).
--
-- LE DÉFAUT. Jusqu'ici, réattribuer un dossier ouvrait bien l'occurrence DISPATCH du redispatcheur mais
-- ne fermait pas celle du Membre qui tenait déjà l'examen. Elle restait ouverte à jamais, et le nouvel
-- attributaire s'en trouvait EN IMPASSE : l'étape paraissait prise en charge par quelqu'un qui n'était
-- plus là, donc aucun bouton « Prendre en charge » ne lui était offert.
--
-- POURQUOI UNE MIGRATION. Le correctif agit AU MOMENT DU GESTE : il ne peut rien pour les dossiers déjà
-- réattribués. Contrairement aux corrections du journal, dérivées à la lecture et donc rétroactives par
-- construction, celle-ci écrit — il lui faut une reprise pour rattraper l'existant.
--
-- CE QU'ON FERME, ET RIEN D'AUTRE : une occurrence EXAMEN encore ouverte dont l'acteur n'est PLUS
-- l'attributaire d'aucun dispatch du dossier. C'est la signature exacte de l'orphelin — réattribution
-- (le dispatch a changé de main) comme retrait (le dispatch a disparu). Une occurrence tenue par
-- l'attributaire courant est un examen EN COURS : on n'y touche pas.
--
-- FERMER, PAS SUPPRIMER : l'examen entamé a eu lieu, sa durée doit être mesurée. La fin est posée à
-- l'instant du redispatch (DATE_DISPATCH du dispatch courant, mis à jour en place lors de la
-- réattribution) ; à défaut — dispatch retiré, donc supprimé — à la dernière trace de circuit connue au
-- journal, et en tout dernier recours à maintenant. GREATEST garantit qu'une fin ne précède jamais sa
-- prise en charge, quelle que soit la source retenue.
-- =====================================================================================================

UPDATE t_tache_dossier t
SET "DATE_FIN" = GREATEST(
        t."DATE_PRISE_EN_CHARGE",
        COALESCE(
            -- 1) l'instant du redispatch : le dispatch a été mis à jour en place, sa date fait foi
            (SELECT MAX(d."DATE_DISPATCH")
               FROM t_dispatch d
               JOIN t_reception r ON r."ID_RECEPTION" = d."ID_RECEPTION"
              WHERE r."ID_DOSSIER" = t."ID_DOSSIER"),
            -- 2) dispatch retiré (supprimé) : la dernière action de circuit consignée au journal
            (SELECT MAX(a."DATE_ACTION")
               FROM t_action_dossier a
              WHERE a."ID_DOSSIER" = t."ID_DOSSIER"
                AND a."TYPE_ACTION" IN ('REATTRIBUTION', 'REPRISE', 'RETRAIT_DISPATCH')
                AND a."DATE_ACTION" >= t."DATE_PRISE_EN_CHARGE"),
            -- 3) filet : l'occurrence ne peut pas rester ouverte, elle bloque le porteur suivant
            LOCALTIMESTAMP))
WHERE t."ETAPE" = 'EXAMEN'
  AND t."DATE_FIN" IS NULL
  AND NOT EXISTS (
        SELECT 1
          FROM t_dispatch d
          JOIN t_reception r ON r."ID_RECEPTION" = d."ID_RECEPTION"
         WHERE r."ID_DOSSIER" = t."ID_DOSSIER"
           AND d."IM_CTRL_MEMBRE" = t."IM_ACTEUR");
