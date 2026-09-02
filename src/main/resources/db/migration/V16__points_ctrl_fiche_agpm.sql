-- V16 — La fiche de présentation et l'AGPM entrent dans l'examen (règle du pilote, 2026-09-02)
--
-- LA REGLE. « Faire entrer la fiche de présentation et l'AGPM (s'il y en a) dans l'examen de dossier —
-- chacun d'eux a SA PROPRE grille de contrôle. » Le modèle portait déjà tout ce qu'il fallait : la
-- colonne PORTEE, le rattachement par sous-type, la grille effective ?sousType= et le stockage des
-- résultats hors-ligne (t_examen_detail.ID_DETAIL = null). Il ne manquait que deux portées et leurs
-- points.
--
-- RATTACHEMENT : FICHE EN COMMUN SUR LA FAMILLE, AGPM EN SPECIFIQUE.
--
-- La demande proposait d'attacher chaque point FICHE aux DEUX sous-types (PPM et PPM-AGPM), par crainte
-- qu'un point commun « arrose DMC/DDM, qui n'ont pas de fiche ». Verification faite, cette crainte ne
-- tient pas : findGrilleEffective filtre deja sur la FAMILLE (p.idTypeDossier = :famille), et la famille
-- DDP ne contient exactement que PPM et PPM-AGPM — DMC = {DAO, DAOR} et DDM = {MAOO, MAOR} sont d'autres
-- familles, hors de portee d'une ligne DDP.
--
-- Un point commun DDP atteint donc EXACTEMENT PPM et PPM-AGPM, ce qui est le besoin. Dupliquer chaque
-- point sur deux sous-types aurait double le travail de l'Administrateur — corriger un libelle veut dire
-- editer deux lignes, qui divergeront un jour — pour un resultat identique. D'ou : FICHE en commun
-- (ID_SOUS_TYPE null sur DDP), decision validee par le pilote.
--
-- L'AGPM, lui, reste SPECIFIQUE a PPM-AGPM : un plan sans AGPM ne doit jamais voir cette grille, et c'est
-- le sous-type qui porte cette distinction. Meme motif que le point 8 deja en place.
--
-- STOCKAGE INCHANGE. Un resultat sur un point FICHE ou AGPM s'enregistre comme un point DOSSIER
-- (t_examen_detail, ID_DETAIL null, observations « AU LIEU DE / LIRE » comprises) et suit le circuit
-- normal : synthese, PV, boucle FAVR. Aucune URL, aucun DTO modifie.
--
-- Idempotente : ON CONFLICT DO NOTHING sur la PK. Rejouee, elle n'ajoute pas de doublon et ne reecrase
-- pas un libelle que l'Administrateur aurait ajuste.

-- ⚠️ LA PORTEE EST CONTRAINTE EN DEUX ENDROITS. L'enum Java n'est pas seul a fermer la liste : la
-- baseline V1 porte un CHECK sur tr_points_ctrl.PORTEE limite a ('LIGNE','DOSSIER'). Ajouter les
-- valeurs cote Java sans elargir la contrainte fait echouer l'INSERT en 23514, et le referentiel reste
-- inutilisable. Les deux definitions doivent bouger ensemble — a garder en tete pour toute portee
-- future.

ALTER TABLE public.tr_points_ctrl DROP CONSTRAINT IF EXISTS "tr_points_ctrl_PORTEE_check";
ALTER TABLE public.tr_points_ctrl ADD CONSTRAINT "tr_points_ctrl_PORTEE_check"
    CHECK ("PORTEE" IN ('LIGNE', 'DOSSIER', 'FICHE', 'AGPM'));

-- ⚠️ LES POINTS EUX-MEMES NE SONT PAS SEMES ICI, ET C'EST DELIBERE. tr_points_ctrl porte deux cles
-- etrangeres : ID_TYPE_DOSSIER -> tr_type_dossier et ID_SOUS_TYPE -> tr_sous_type_dossier. Or AUCUNE
-- migration ne cree ces referentiels — la baseline V1 ne pose que le schema. Un INSERT de points depuis
-- une migration echoue donc en 23503 sur toute base neuve (conteneur de test, nouvel environnement),
-- avant meme que l'application ait pu peupler ses referentiels.
--
-- Les six points vivent donc dans PointsCtrlFicheAgpmSeeder, au demarrage, sur le patron deja en place
-- pour les delegations : idempotent, il cree ce qui manque, ne reecrase jamais un libelle ajuste par
-- l'Administrateur, et s'abstient si la famille ou le sous-type reference n'existe pas encore.
