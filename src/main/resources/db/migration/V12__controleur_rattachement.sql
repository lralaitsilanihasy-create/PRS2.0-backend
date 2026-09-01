-- V12 — Rattachements Membre → Vérificateur → Assistant (arbitrage du pilote, 2026-09-01)
--
-- LA REGLE. « Chaque Membre a un contrôleur Vérificateur rattaché à lui, et chaque Vérificateur a
-- lui-même un Assistant contrôleur rattaché à lui. » La vérification des documents témoins rectifiés
-- par la PRMP revient au Vérificateur rattaché au MEMBRE EXAMINATEUR ; l'archivage, à l'Assistant
-- rattaché à ce Vérificateur.
--
-- UNE SEULE COLONNE, ET NON DEUX. Le rattaché d'un Membre est un Vérificateur, celui d'un
-- Vérificateur un Assistant : c'est LA MÊME relation — « mon rattaché » — dont le sens est fixé par le
-- profil du porteur. Une colonne unique impose STRUCTURELLEMENT la règle « au plus un rattaché par
-- porteur », là où deux colonnes se contenteraient de l'espérer ; elle autorise le partage (plusieurs
-- porteurs peuvent désigner le même rattaché) sans rien ajouter ; et elle évite deux colonnes nulles
-- sur la quasi-totalité des lignes. La chaîne se parcourt en suivant la colonne deux fois.
--
-- PAS DE CLE ETRANGERE. IM_RATTACHE désigne un tr_controleur, mais la table ne porte pas de FK vers
-- elle-même : le référentiel des contrôleurs admet des suppressions administratives, et une FK
-- transformerait le retrait d'un contrôleur en échec d'intégrité au lieu d'un simple rattachement
-- devenu caduc. La cohérence est portée par le service (RattachementService), qui vérifie le profil du
-- rattaché et sa localité à CHAQUE écriture ; un rattaché disparu se comporte comme une chaîne
-- incomplète, cas déjà prévu par l'arbitrage 2 (repli localité).
--
-- AUCUNE REPRISE — c'est le coeur de la transition voulue par la spec. Au déploiement, aucun
-- rattachement n'existe : tout fonctionne comme aujourd'hui (repli localité intégral). La
-- personnalisation s'active rattachement par rattachement, sans migration de données.
--
-- Index sur la colonne : le sens INVERSE est celui qu'on interroge le plus (« quels Membres me sont
-- rattachés ? » pour la rubrique « Les miens » du front, et le signalement des chaînes incomplètes).
--
-- Idempotente : ADD COLUMN IF NOT EXISTS. Rejouée, elle ne fait rien.

ALTER TABLE public.tr_controleur
    ADD COLUMN IF NOT EXISTS "IM_RATTACHE" character varying(7);

CREATE INDEX IF NOT EXISTS idx_controleur_rattache
    ON public.tr_controleur ("IM_RATTACHE");

COMMENT ON COLUMN public.tr_controleur."IM_RATTACHE" IS
    'Controleur rattache a celui-ci : un VERIFICATEUR si le porteur est MEMBRE, un ASSISTANT si le '
    'porteur est VERIFICATEUR. Au plus un par porteur (colonne unique) ; partage autorise. '
    'Coherence de profil et de localite verifiee par le service a chaque ecriture, pas par une FK.';
