-- V10 — Co-signature du PV : le Membre co-signataire est DÉSIGNÉ par le Président / le Chef de
-- commission (arbitrage du pilote du 2026-08-28, option (ii) + ordre B).
--
-- CE QUI EST ABANDONNÉ. La décision produit du 2026-08-15 laissait un P/CC auto-attribué porter les
-- DEUX parts de signature du PV : la part Membre (comme attributaire) puis sa part de rôle. Le verrou
-- « une signature par personne » (§2.6) était levé par une paire (profil → Membre) active de
-- t_delegation_profil. Le pilote a tranché : l'auto-co-signature n'est pas autorisée, jamais.
--
-- CE QUI LA REMPLACE. Le P/CC DÉSIGNE, au moment de signer, le Membre appelé à co-signer ; ce Membre
-- signe ensuite lui-même. Deux personnes, deux actions. La désignation est PRÉALABLE : la part Membre
-- n'est signable qu'après elle (ordre B) — sans quoi le choix du P/CC pourrait lui échapper par simple
-- antériorité, un Membre signant spontanément avant qu'on ait choisi.
--
-- POURQUOI UNE COLONNE ET NON IM_CTRL_MEMBRE. IM_CTRL_MEMBRE désigne l'ATTRIBUTAIRE de l'examen —
-- QUI A EXAMINÉ le dossier. Il est re-dérivé du dispatch à chaque création/mise à jour du PV
-- (PvExamenService, « jamais le corps ») et il est IMPRIMÉ SUR LE PV OFFICIEL
-- (PvDocumentService#nomMembreAttributaire). Y écrire le co-signataire ferait dire au document
-- qu'une autre personne a mené l'examen. Les deux rôles sont distincts : ils ont deux colonnes.
--
-- Nullable, et ce n'est pas un provisoire : la colonne reste vide de la création du PV jusqu'à la
-- signature du P/CC. Elle ne se remplit qu'à ce moment-là.
--
-- AUCUNE REPRISE DE DONNÉES — volontaire. Le PV 2 (00002/PPM/CNM/PV/2026, dossier 100256 CLOTURÉ) a
-- été signé le 2026-08-15 par CCANT01 pour les deux parts, sous la règle d'alors. Le régulariser
-- exigerait une seconde signature réelle qui n'a jamais eu lieu, sur un PV signé d'un dossier clos.
-- Il reste tel quel, au titre de l'historique (décision du 2026-08-28, front + pilote).
--
-- Type et longueur alignés sur ID_SECRETAIRE_SEANCE, l'autre désignation nominative de cette table :
-- varchar(7), sans contrainte de clé étrangère — même convention, la cohérence est portée par le
-- service (ControleurDirectory#peutEtreMembreCoSignataire : Membre titulaire de la localité du
-- dossier, distinct du signataire).
--
-- Idempotente : ADD COLUMN IF NOT EXISTS. Rejouée, elle ne fait rien.

ALTER TABLE public.t_pv_examen
    ADD COLUMN IF NOT EXISTS "IM_MEMBRE_COSIGNATAIRE" character varying(7);

COMMENT ON COLUMN public.t_pv_examen."IM_MEMBRE_COSIGNATAIRE" IS
    'Membre designe par le President/CC pour co-signer le PV (pose a la signature du P/CC). '
    'Distinct de IM_CTRL_MEMBRE, qui designe l''attributaire ayant examine le dossier.';
