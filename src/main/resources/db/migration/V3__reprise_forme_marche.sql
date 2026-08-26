-- V3 — Reprise de données (conversion de l'ex-FormeMarcheMigration, runner Java supprimé
-- au chantier LOT 2, 2026-08-26 ; règle d'origine : 2026-07-18).
--
-- t_marche.FORME_MARCHE a été ajoutée à chaud (ddl-auto=update) : NULL sur les lignes
-- historiques. Forme dérivée de la désignation, mêmes motifs que l'import PPM
-- (FormeMarche#detecterDansDesignation) : « contrat(s) cadre(s) » → CONTRAT_CADRE,
-- « à commande(s) » → A_COMMANDE, sinon défaut QUANTITE_FIXE.
--
-- Transposition SQL du normalisateur Java (NFD + suppression des diacritiques + majuscules
-- + tout non-alphanumérique réduit à une espace) : translate() couvre les diacritiques du
-- français réellement présents dans les désignations. Les bases à jour n'ont plus de ligne
-- NULL (le runner Java tournait à chaque démarrage) : cette migration est un filet pour
-- toute base en retard. Idempotente (ne touche que les NULL).
UPDATE public.t_marche
SET "FORME_MARCHE" = CASE
    WHEN ' ' || regexp_replace(
            upper(translate("DESIGNATION_MARCHE",
                'àâäáãåèéêëìíîïòóôöõùúûüçñÀÂÄÁÃÅÈÉÊËÌÍÎÏÒÓÔÖÕÙÚÛÜÇÑ',
                'aaaaaaeeeeiiiiooooouuuucnAAAAAAEEEEIIIIOOOOOUUUUCN')),
            '[^A-Z0-9]+', ' ', 'g') || ' ' ~ ' CONTRATS? CADRES? '
        THEN 'CONTRAT_CADRE'
    WHEN ' ' || regexp_replace(
            upper(translate("DESIGNATION_MARCHE",
                'àâäáãåèéêëìíîïòóôöõùúûüçñÀÂÄÁÃÅÈÉÊËÌÍÎÏÒÓÔÖÕÙÚÛÜÇÑ',
                'aaaaaaeeeeiiiiooooouuuucnAAAAAAEEEEIIIIOOOOOUUUUCN')),
            '[^A-Z0-9]+', ' ', 'g') || ' ' ~ ' A COMMANDES? '
        THEN 'A_COMMANDE'
    ELSE 'QUANTITE_FIXE'
END
WHERE "FORME_MARCHE" IS NULL;
