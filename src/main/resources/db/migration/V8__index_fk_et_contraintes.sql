-- V8 — Index sur les colonnes de clé étrangère, contrainte dupliquée retirée, FK manquante posée
-- (chantier AUDIT 2026-08, lot D §1).
--
-- ── Pourquoi ────────────────────────────────────────────────────────────────────────────────────
-- PostgreSQL indexe automatiquement la colonne RÉFÉRENCÉE d'une clé étrangère (elle est PK ou
-- UNIQUE), jamais la colonne RÉFÉRENÇANTE. Le schéma porte 92 clés étrangères pour 15 index
-- (V1__baseline.sql:2247-2345) : ~80 colonnes FK sont donc balayées séquentiellement à chaque
-- lecture par le parent — et, plus grave, à chaque SUPPRESSION du parent, où PostgreSQL doit
-- prouver qu'aucune ligne fille ne subsiste.
--
-- Les colonnes retenues ci-dessous ne sont pas « toutes les FK » : ce sont celles qu'une requête
-- du projet filtre réellement (relevé exhaustif des 76 repositories, 2026-08-27). Les référentiels
-- de quelques dizaines de lignes (tr_points_ctrl, tr_sous_type_dossier, t_type_piece_jointe…) en
-- sont volontairement exclus : un parcours complet y coûte moins cher que la maintenance d'un index.
--
-- Idempotente : CREATE INDEX IF NOT EXISTS, et blocs DO gardés pour les contraintes (PostgreSQL
-- n'offre pas IF NOT EXISTS sur ADD CONSTRAINT). Rejouée, elle ne fait rien.
--
-- ⚠️ Casse : tous les identifiants de colonne sont en MAJUSCULES et cités, comme dans la baseline
-- (hibernate.globally_quoted_identifiers=true). Un nom non cité serait replié en minuscules par
-- PostgreSQL et ne désignerait aucune colonne.


-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 1) Chaîne de visibilité par localité — le prédicat le plus emprunté du projet.
--    Presque toutes les listes des contrôleurs remontent le dossier par
--    t_reception → t_dispatch → t_examen, puis résolvent la localité par
--    t_reception.IM_CTRL_RECEPT → tr_controleur.ID_LOCALITE. Une quarantaine de requêtes
--    (findVisiblesParLocalite / existsDansLocalite de Dispatch, Examen, ExamenDetail,
--    ExamenPiece, PvExamen, PvNavette, LettreRenvoi, Verification, Dossier, DemandeRetrait…)
--    empruntent cette chaîne, dont AUCUN maillon n'était indexé.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_reception_ctrl_recept ON public.t_reception USING btree ("IM_CTRL_RECEPT");
CREATE INDEX IF NOT EXISTS idx_controleur_localite   ON public.tr_controleur USING btree ("ID_LOCALITE");
CREATE INDEX IF NOT EXISTS idx_controleur_profile    ON public.tr_controleur USING btree ("ID_PROFILE");
CREATE INDEX IF NOT EXISTS idx_dispatch_reception    ON public.t_dispatch USING btree ("ID_RECEPTION");
CREATE INDEX IF NOT EXISTS idx_dispatch_membre       ON public.t_dispatch USING btree ("IM_CTRL_MEMBRE");
CREATE INDEX IF NOT EXISTS idx_examen_dispatch       ON public.t_examen USING btree ("ID_DISPATCH");
CREATE INDEX IF NOT EXISTS idx_examen_membre         ON public.t_examen USING btree ("IM_CTRL_MEMBRE");

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 2) Aval de l'examen — saisie point par point, pièces examinées, PV, navettes, vérifications.
--    Sollicité à chaque ouverture d'un examen et à chaque purge de circuit (deleteParDossier).
-- ═══════════════════════════════════════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_examen_detail_examen        ON public.t_examen_detail USING btree ("ID_EXAMEN");
CREATE INDEX IF NOT EXISTS idx_examen_piece_examen         ON public.t_examen_piece USING btree ("ID_EXAMEN");
CREATE INDEX IF NOT EXISTS idx_pv_examen_examen            ON public.t_pv_examen USING btree ("ID_EXAMEN");
CREATE INDEX IF NOT EXISTS idx_pv_navette_pv               ON public.t_pv_navette USING btree ("ID_PV");
CREATE INDEX IF NOT EXISTS idx_observation_controle_detail ON public.t_observation_controle USING btree ("ID_DETAIL");
CREATE INDEX IF NOT EXISTS idx_verification_pv             ON public.t_verification USING btree ("ID_PV");
CREATE INDEX IF NOT EXISTS idx_verification_reception      ON public.t_verification USING btree ("ID_RECEPTION");
CREATE INDEX IF NOT EXISTS idx_suivi_observation_obs_pv    ON public.t_suivi_observation USING btree ("ID_OBSERVATION_PV");

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 3) Fan-out « par dossier ». Quinze tables portent ID_DOSSIER ; cinq seulement étaient indexées.
--    C'est le chemin de la fiche dossier ET celui de la cascade de suppression : sans ces index,
--    supprimer UN dossier impose autant de balayages complets que de tables filles.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_lettre_renvoi_dossier      ON public.t_lettre_renvoi USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_demande_retrait_dossier    ON public.t_demande_retrait USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_lot_dossier                ON public.t_lot USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_copie_dossier_dossier      ON public.t_copie_dossier USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_notification_dossier       ON public.t_notification USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_message_dossier            ON public.t_message USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_piece_jointe_dossier_doss  ON public.t_piece_jointe_dossier USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_observation_pv_dossier     ON public.t_observation_pv USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_snapshot_rectif_dossier    ON public.t_snapshot_rectif_ligne USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_transmission_sigmp_dossier ON public.t_transmission_sigmp USING btree ("ID_DOSSIER");
CREATE INDEX IF NOT EXISTS idx_verif_piece_depot_dossier  ON public.t_verification_piece_depot USING btree ("ID_DOSSIER");

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 4) Fan-out « par ligne de marché » (ID_DETAIL) et « par lot ». Aucune de ces colonnes n'était
--    indexée, alors que l'affichage d'un dossier les balaie toutes (findParDossiers / findIdDossier)
--    et que la cascade de suppression d'un marché les balaie une seconde fois.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_lot_detail                  ON public.t_lot USING btree ("ID_DETAIL");
CREATE INDEX IF NOT EXISTS idx_echeance_detail             ON public.t_echeance USING btree ("ID_DETAIL");
CREATE INDEX IF NOT EXISTS idx_marche_prevision_detail     ON public.t_marche_prevision USING btree ("ID_DETAIL");
CREATE INDEX IF NOT EXISTS idx_service_beneficiaire_detail ON public.t_service_beneficiaire USING btree ("ID_DETAIL");
CREATE INDEX IF NOT EXISTS idx_anomalie_detail             ON public.t_anomalie USING btree ("ID_DETAIL");
CREATE INDEX IF NOT EXISTS idx_anomalie_ppm                ON public.t_anomalie USING btree ("ID_PPM");
CREATE INDEX IF NOT EXISTS idx_tranche_lot                 ON public.t_tranche USING btree ("ID_LOT");

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 5) Destinataires. La cloche de notification et la messagerie interrogent ces colonnes à chaque
--    rafraîchissement, pour chaque utilisateur connecté : ce sont les lectures les plus répétées
--    de l'application. Le couple (DESTINATAIRE_REF, DESTINATAIRE_TYPE) est la destination
--    POLYMORPHE (PRMP / UGPM / contrôleur) : indexé ensemble parce que filtré ensemble.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_notification_destinataire ON public.t_notification USING btree ("DESTINATAIRE_REF", "DESTINATAIRE_TYPE");
CREATE INDEX IF NOT EXISTS idx_message_destinataire      ON public.t_message USING btree ("DESTINATAIRE_IM");
CREATE INDEX IF NOT EXISTS idx_message_expediteur        ON public.t_message USING btree ("EXPEDITEUR_IM");

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 6) Périmètre PRMP / UGPM et pièces rattachées à un compte.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_demande_retrait_prmp       ON public.t_demande_retrait USING btree ("ID_PRMP");
CREATE INDEX IF NOT EXISTS idx_prmp_entite_prmp           ON public.t_prmp_entite USING btree ("ID_PRMP");
CREATE INDEX IF NOT EXISTS idx_prmp_entite_entite         ON public.t_prmp_entite USING btree ("ID_ENTITE_CONTRACT");
CREATE INDEX IF NOT EXISTS idx_ugpm_tutelle               ON public.t_ugpm USING btree ("ID_PRMP_TUTELLE");
CREATE INDEX IF NOT EXISTS idx_piece_jointe_login         ON public.t_piece_jointe USING btree ("LOGIN");
CREATE INDEX IF NOT EXISTS idx_prmp_entite_demande_login  ON public.t_prmp_entite_demande USING btree ("LOGIN");
CREATE INDEX IF NOT EXISTS idx_session_utilisateur_ctrl   ON public.t_session_utilisateur USING btree ("IM_CONTROLEUR");


-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 7) Contrainte d'unicité DUPLIQUÉE sur t_delegation_profil.
--
--    La paire (délégant, délégué) porte DEUX contraintes identiques, héritées de l'époque
--    ddl-auto : "UQ_DELEGATION_PAIRE" (citée, donc en majuscules — V1:1599-1600) et
--    uq_delegation_paire (non citée, donc repliée en minuscules — V1:2231-2232). PostgreSQL les
--    tient pour distinctes et maintient DEUX index uniques pour la même règle : double coût à
--    chaque écriture, et un message d'erreur qui nomme, selon l'ordre d'évaluation, une contrainte
--    dont le code ne parle jamais.
--
--    Celle que l'entité déclare est la version citée (DelegationProfil.java:27-28,
--    @UniqueConstraint(name = "UQ_DELEGATION_PAIRE", …)) : c'est elle qui reste.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════
ALTER TABLE public.t_delegation_profil DROP CONSTRAINT IF EXISTS uq_delegation_paire;


-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 8) Clé étrangère MANQUANTE : t_piece_jointe_dossier.ID_DOSSIER → t_dossier.
--
--    Cette table est la seule fille de t_dossier à n'avoir jamais porté de FK, alors que sa colonne
--    ID_DOSSIER est NOT NULL et que l'application la traite comme une référence. Rien n'empêchait
--    donc une pièce de désigner un dossier inexistant — et rien ne signalait qu'une suppression de
--    dossier laissait ses pièces derrière elle (le lot D §1 a fermé la cascade côté applicatif ;
--    cette FK est le filet qui empêche la régression).
--
--    ⚠️ NETTOYAGE PRÉALABLE, DESTRUCTIF : les pièces dont le dossier n'existe plus sont supprimées.
--    Elles sont déjà irrécupérables (aucun écran ne sait les afficher, aucune requête ne les joint),
--    mais leur contenu binaire disparaît définitivement. Sur une base de production, exporter
--    `SELECT * FROM public.t_piece_jointe_dossier p WHERE NOT EXISTS (…)` AVANT de migrer si l'on
--    veut en garder trace.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════
DELETE FROM public.t_piece_jointe_dossier p
 WHERE NOT EXISTS (SELECT 1 FROM public.t_dossier d WHERE d."ID_DOSSIER" = p."ID_DOSSIER");

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_piece_jointe_dossier_dossier') THEN
        ALTER TABLE public.t_piece_jointe_dossier
            ADD CONSTRAINT fk_piece_jointe_dossier_dossier
            FOREIGN KEY ("ID_DOSSIER") REFERENCES public.t_dossier("ID_DOSSIER");
    END IF;
END $$;
