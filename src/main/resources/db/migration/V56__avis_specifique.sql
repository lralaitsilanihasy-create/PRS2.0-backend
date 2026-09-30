-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V56 — Avis spécifique d'appel d'offres (demande front du 2026-09-30, demande-backend-2026-09-30-avis-specifique.md).
--
-- 1. §B2/§B3 — l'avis s'imprime à la demande, avec des informations de publication saisies à l'impression (date de
--    publication, numéro et date du JMP de l'avis général, autres supports). Elles ne sont pas des données de la fiche :
--    elles sont conservées AVEC le document produit, en trace (JSON), sur t_document_fiche_marche (type AVIS).
-- 2. §B5 — l'avis imprime l'adresse de consultation et le montant du dossier (B04-DS-05, -07 à -11) pour les trois
--    formes : la rubrique B04-DS, jusqu'ici réservée au contrat-cadre, est servie aussi à la quantité fixe et au marché
--    à commande. Il imprime le montant de la garantie de soumission du contrat-cadre de travaux (B05-GQ-03) : la
--    rubrique B05-GQ, réservée aux travaux à quantité fixe et à commande, est servie aussi au contrat-cadre. (B05-GS,
--    celle des fournitures, couvre déjà les trois formes.) Les champs eux-mêmes sont ouverts par les fichiers de
--    correspondance et docs/referentiel/2026-09-30-avis-specifique.sql ; une rubrique n'est servie que si l'un de ses
--    champs actifs l'est.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.t_document_fiche_marche ADD COLUMN IF NOT EXISTS "PUBLICATION" text;

COMMENT ON COLUMN public.t_document_fiche_marche."PUBLICATION" IS
    'Avis spécifique : informations de publication saisies à l''impression (JSON) ; null pour les documents du DAO';

-- Le type AVIS rejoint la liste fermée des types de document (V38, étendue par V42, V45 et V46).
ALTER TABLE public.t_document_fiche_marche DROP CONSTRAINT IF EXISTS ck_document_fiche_marche_type;
ALTER TABLE public.t_document_fiche_marche ADD CONSTRAINT ck_document_fiche_marche_type
    CHECK ("TYPE" IN ('DPAO', 'DPAC', 'DPIC', 'AE', 'CCAP', 'LF', 'BP', 'TC', 'A1', 'A2', 'A3', 'A4', 'C1', 'C2', 'AVIS'));

-- Un document du DAO est unique par version, type, extension et lot (V43) ; l'avis se réimprime à volonté : chaque
-- impression ajoute une paire, les précédentes restent consultables. L'unicité ne vaut donc plus pour le type AVIS.
DROP INDEX IF EXISTS public.uq_document_fiche_marche;
CREATE UNIQUE INDEX uq_document_fiche_marche
    ON public.t_document_fiche_marche ("ID_FICHE", "TYPE", "EXTENSION", COALESCE("LOT", 0))
 WHERE "TYPE" <> 'AVIS';

UPDATE public.tr_rubrique_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'
 WHERE "CODE" = 'B04-DS' AND "TYPES_MARCHE" = 'CONTRAT_CADRE';

UPDATE public.tr_rubrique_fiche_marche SET "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE'
 WHERE "CODE" = 'B05-GQ' AND "TYPES_MARCHE" = 'QUANTITE_FIXE,A_COMMANDE';
