-- =====================================================================================================
-- V24 — Référentiel « Statut de marché » (demande pilote du 2026-09-09).
--
-- t_marche.STATUT stockait un TEXTE LIBRE : en pratique toujours 'PREVU' (64 lignes en base, plus 2 à
-- NULL), mais rien ne l'imposait, et l'Administrateur n'avait aucun moyen d'ajouter une valeur. Le code
-- devient une valeur de référentiel, sur le moule de tr_nature et tr_mode_passation.
--
-- LA CLÉ EST LE CODE, et non un identifiant technique : c'est lui que t_marche.STATUT porte déjà.
-- Passer par un entier aurait impose une reprise de toutes les lignes existantes pour ne rien gagner.
--
-- PAS DE CONTRAINTE DE CLÉ ÉTRANGÈRE sur t_marche.STATUT, volontairement. La demande porte sur une
-- validation À L'ÉCRITURE (400 si le code est inconnu), que le service applique sur les trois voies
-- (endpoints granulaires, façade de saisie, rectification). La donnée s'y prêterait — seul 'PREVU' est
-- présent — mais une FK transformerait toute écriture d'un code retiré du référentiel en violation de
-- contrainte plutôt qu'en refus lisible, et ferait échouer la suppression d'un statut en cours d'usage
-- par une erreur technique au lieu d'une règle métier. À poser plus tard si le pilote veut le verrou
-- au niveau du SGBD.
--
-- SEED : 'PREVU' seul, celui que portent les lignes existantes et que la saisie posait en dur. Les
-- autres valeurs seront saisies par l'Administrateur — c'est tout l'objet de la demande.
-- =====================================================================================================

CREATE TABLE IF NOT EXISTS tr_statut_marche (
    "CODE"    varchar(20)  NOT NULL,
    "LIBELLE" varchar(100) NOT NULL,
    "ORDRE"   integer,
    "ACTIF"   boolean      NOT NULL DEFAULT true,
    CONSTRAINT tr_statut_marche_pkey PRIMARY KEY ("CODE")
);

-- Idempotent : la reprise ne réécrit pas un libellé que l'Administrateur aurait déjà ajusté.
INSERT INTO tr_statut_marche ("CODE", "LIBELLE", "ORDRE", "ACTIF")
VALUES ('PREVU', 'Prévu', 10, true)
ON CONFLICT ("CODE") DO NOTHING;
