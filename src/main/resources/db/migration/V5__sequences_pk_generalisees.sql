-- V5 — Généralisation des séquences de clé primaire (chantier LOT 3b, 2026-08-26).
--
-- Jusqu'ici, quatre tables seulement disposaient d'une séquence serveur (seq_dossier,
-- seq_ppm, seq_marche, seq_reception — V1). Partout ailleurs, la PK était soit fournie
-- par le client, soit allouée en Java par `max(id) + 1` : deux créations concurrentes
-- lisent le même maximum, calculent le même identifiant, et la seconde ÉCRASE la
-- première (save() sur une PK existante = UPDATE, pas INSERT). Le calcul max+1 n'est pas
-- atomique ; seule une séquence l'est.
--
-- Une séquence par table concernée, démarrée juste au-dessus de l'existant pour ne
-- jamais entrer en collision avec les lignes déjà en base. `setval(..., false)` fait que
-- le PREMIER nextval() rend bien MAX+1 (et non MAX+2).
--
-- Idempotente : CREATE SEQUENCE IF NOT EXISTS + setval recalculé sur l'état réel.
-- Rejouée sur une base déjà à jour, elle repositionne simplement chaque séquence au bon
-- rang. ⚠️ setval ne fait que MONTER le curseur ici parce qu'il est calculé depuis le
-- MAX courant de la table ; il ne redonne jamais un identifiant déjà pris.

-- — Tables dont la PK était allouée en Java par max+1 (repositories .findMaxId*) —
CREATE SEQUENCE IF NOT EXISTS public.seq_audit_log;
SELECT setval('public.seq_audit_log', COALESCE((SELECT MAX("ID_LOG") FROM public.t_audit_log), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_changement_ligne;
SELECT setval('public.seq_changement_ligne', COALESCE((SELECT MAX("ID_CHANGEMENT") FROM public.t_changement_ligne), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_entite_contract;
SELECT setval('public.seq_entite_contract', COALESCE((SELECT MAX("ID_ENTITE_CONTRACT") FROM public.tr_entite_contract), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_lot;
SELECT setval('public.seq_lot', COALESCE((SELECT MAX("ID_LOT") FROM public.t_lot), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_marche_prevision;
SELECT setval('public.seq_marche_prevision', COALESCE((SELECT MAX("ID_PREVISION") FROM public.t_marche_prevision), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_message;
SELECT setval('public.seq_message', COALESCE((SELECT MAX("ID_MESSAGE") FROM public.t_message), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_notification;
SELECT setval('public.seq_notification', COALESCE((SELECT MAX("ID_NOTIFICATION") FROM public.t_notification), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_piece_jointe;
SELECT setval('public.seq_piece_jointe', COALESCE((SELECT MAX("ID_PIECE") FROM public.t_piece_jointe), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_prmp_entite;
SELECT setval('public.seq_prmp_entite', COALESCE((SELECT MAX("ID_PRMP_ENTITE") FROM public.t_prmp_entite), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_prmp_entite_demande;
SELECT setval('public.seq_prmp_entite_demande', COALESCE((SELECT MAX("ID_DEMANDE") FROM public.t_prmp_entite_demande), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_pv_examen;
SELECT setval('public.seq_pv_examen', COALESCE((SELECT MAX("ID_PV") FROM public.t_pv_examen), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_pv_navette;
SELECT setval('public.seq_pv_navette', COALESCE((SELECT MAX("ID_NAVETTE") FROM public.t_pv_navette), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_service_beneficiaire;
SELECT setval('public.seq_service_beneficiaire', COALESCE((SELECT MAX("ID_BENEF") FROM public.t_service_beneficiaire), 0) + 1, false);

-- — Tables alimentées par un écran qui CALCULE l'identifiant côté client (max+1 en TypeScript :
--   crud-page.ts `autoId`, dispatch-form.ts, examen-dossier.ts, soumettre-dossier.ts). L'id du
--   client reste honoré s'il est libre (le front s'y réfère parfois localement après la création) ;
--   la séquence sert de plan de repli quand il est déjà pris, à la place de l'écrasement silencieux.
CREATE SEQUENCE IF NOT EXISTS public.seq_capm;
SELECT setval('public.seq_capm', COALESCE((SELECT MAX("ID_CAPM") FROM public.t_capm), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_delegation_profil;
SELECT setval('public.seq_delegation_profil', COALESCE((SELECT MAX("ID_DELEGATION") FROM public.t_delegation_profil), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_dispatch;
SELECT setval('public.seq_dispatch', COALESCE((SELECT MAX("ID_DISPATCH") FROM public.t_dispatch), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_examen;
SELECT setval('public.seq_examen', COALESCE((SELECT MAX("ID_EXAMEN") FROM public.t_examen), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_examen_detail;
SELECT setval('public.seq_examen_detail', COALESCE((SELECT MAX("ID_DETAIL_EXAMEN") FROM public.t_examen_detail), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_examen_piece;
SELECT setval('public.seq_examen_piece', COALESCE((SELECT MAX("ID_EXAMEN_PIECE") FROM public.t_examen_piece), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_ministere;
SELECT setval('public.seq_ministere', COALESCE((SELECT MAX("ID_MINISTERE") FROM public.tr_ministere), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_mode_passation;
SELECT setval('public.seq_mode_passation', COALESCE((SELECT MAX("ID_MODE") FROM public.tr_mode_passation), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_nature;
SELECT setval('public.seq_nature', COALESCE((SELECT MAX("ID_NATURE") FROM public.tr_nature), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_organigramme;
SELECT setval('public.seq_organigramme', COALESCE((SELECT MAX("ID_ORGANIGRAMME") FROM public.t_organigramme), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_points_ctrl;
SELECT setval('public.seq_points_ctrl', COALESCE((SELECT MAX("ID_POINT_CTRL") FROM public.tr_points_ctrl), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_regle_alerte;
SELECT setval('public.seq_regle_alerte', COALESCE((SELECT MAX("ID_REGLE_ALERTE") FROM public.t_regle_alerte), 0) + 1, false);

CREATE SEQUENCE IF NOT EXISTS public.seq_regle_anomalie;
SELECT setval('public.seq_regle_anomalie', COALESCE((SELECT MAX("ID_REGLE_ANOMALIE") FROM public.t_regle_anomalie), 0) + 1, false);
