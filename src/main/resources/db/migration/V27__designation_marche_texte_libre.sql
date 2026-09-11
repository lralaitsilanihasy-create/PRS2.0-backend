-- =====================================================================================================
-- V27 — L'objet d'un marché devient du texte libre (demande pilote du 2026-09-10).
--
-- DESIGNATION_MARCHE plafonnait à 500 caractères. Un objet de marché réel les dépasse — libellé
-- administratif complet, lieu, tranche, référence de financement — et la saisie était refusée. Ce n'est
-- pas une donnée qu'on borne utilement : c'est une désignation rédigée, elle passe donc en « text ».
--
-- ⚠️ QUATRE COLONNES, PAS UNE. La désignation ne vit pas que dans t_marche : elle est RECOPIÉE le long
-- du circuit, et une seule de ces copies restée en varchar(500) suffit à faire échouer, plus tard, le
-- geste qui l'écrit — sur un dossier déjà accepté à la saisie, donc au pire moment.
--   • t_marche.DESIGNATION_MARCHE            — la ligne du plan elle-même.
--   • t_snapshot_rectif_ligne.DESIGNATION_MARCHE — l'archive d'une version remplacée (RECTIFICATION).
--   • t_changement_ligne.DESIGNATION         — la trace figée d'une MISE À JOUR.
--   • t_changement_ligne.VALEUR_AVANT/APRES  — quand le champ qui change EST la désignation, ce sont
--     ses deux versions qu'on y écrit. Ces colonnes accueillent aussi les autres champs comparés (lots,
--     bénéficiaires...), déjà concaténés : elles y gagnent la même respiration.
--
-- CE QUI NE BOUGE PAS. t_lot.DESIGNATION_LOT et t_snapshot_rectif_lot.DESIGNATION_LOT restent à 200 :
-- c'est un AUTRE champ, un intitulé de lot, hors de cette demande.
--
-- Opération de métadonnées en PostgreSQL (varchar → text sans contrainte de longueur) : pas de
-- réécriture de table, aucune donnée touchée, et aucun index ne porte sur ces colonnes.
-- =====================================================================================================

ALTER TABLE public.t_marche                ALTER COLUMN "DESIGNATION_MARCHE" TYPE text;
ALTER TABLE public.t_snapshot_rectif_ligne ALTER COLUMN "DESIGNATION_MARCHE" TYPE text;
ALTER TABLE public.t_changement_ligne      ALTER COLUMN "DESIGNATION"        TYPE text;
ALTER TABLE public.t_changement_ligne      ALTER COLUMN "VALEUR_AVANT"       TYPE text;
ALTER TABLE public.t_changement_ligne      ALTER COLUMN "VALEUR_APRES"       TYPE text;
