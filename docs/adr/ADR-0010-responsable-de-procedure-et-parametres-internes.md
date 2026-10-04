# ADR-0010 : Le rôle « Responsable de la procédure » et les paramètres internes de la remise électronique

**Statut :** Adopté
**Date :** 2026-09-27
**Origine :** demande front `frontend/docs/demande-backend-2026-09-27-remise-electronique.md` (cahier des charges du
pilote du 27/09, plan `docs/plan-2026-09-27-remise-electronique.md`, quatorze questions arbitrées « selon les
recommandations »). Migration `V50__remise_electronique.sql`.

## Contexte

La fiche DAO se prépare à la **remise électronique des offres** : une rubrique et des champs nouveaux, des règles de
bilan, l'impression dans les documents. Trois besoins n'entrent pas dans ce que la fiche savait faire :

1. **Des paramètres qui ne doivent pas sortir.** Les membres de la commission détenteurs d'une part de clé de
   déchiffrement, le quorum, la date de la cérémonie des clés (`INT-SE-01` à `INT-SE-05` du cahier des charges) sont
   des paramètres *internes* de la procédure : ils n'ont rien à faire dans un DAO, un avis ou une garantie, et ils ne
   doivent pas être lisibles de tous ceux qui lisent la fiche — l'Administrateur compris (Q7 : le journal d'audit global
   est lisible par lui).
2. **Un droit qui ne suit pas un profil.** Celui qui tient ces paramètres est une personne désignée *pour cette
   procédure*. Le projet ne connaît que des droits par profil de session (`ProfilUtilisateur`), des délégations de
   profil (`t_delegation_profil`) et l'intérim désigné (ADR-0008) — trois mécanismes qui étendent un profil, aucun ne
   donne un droit *exclusif* sur un objet.
3. **Un moteur de rendu qui ne doit rien pouvoir en dire.** Les formulaires du candidat sont rendus depuis des
   fichiers de commande où tout `{{CODE}}` est résolu par le référentiel : il faut qu'un `{{INT-SE-03}}` glissé dans
   un modèle ne résolve jamais rien, et soit refusé avant même d'être servi.

## Décision

1. **Un rôle nominatif par procédure**, « Responsable de la procédure », porté par une table dédiée
   (`t_responsable_procedure`) : par DMC, **un seul titulaire actif** (index partiel `DATE_RETRAIT IS NULL`), désigné
   et retiré par l'Administrateur (`POST` / `DELETE /api/fiches-marche/{idDmc}/responsable`), l'historique conservé.
   Ce n'est **ni** un `ProfilUtilisateur`, **ni** une ligne de `t_delegation_profil`, **ni** un intérim : le rôle porte
   des droits exclusifs sur un objet, il ne prolonge aucun profil. *Différence avec l'arbitrage du 25/09 (« pas de
   rôle service bénéficiaire »)* : là, un rôle n'aurait été qu'une trace ; ici il ouvre des droits que personne d'autre
   n'a.
2. **Les paramètres internes vivent en stockage séparé** (`t_parametre_interne_procedure`), hors référentiel des
   champs, hors `SelectionDocumentsFiche`, hors `FormulairesCandidat`. Le moteur ne les voit pas : `jeton()` rend
   `null` (jeton laissé tel quel) pour tout nom `INT-…`, et `ModelesCandidat` **refuse au démarrage** un fichier de
   commande qui en porte un, fichier et ligne nommés.
3. **La garde est par identité, et exclusive.** `GET` / `PUT …/parametres-internes` et `…/candidats` ne passent que
   pour le titulaire du rôle de *cette* fiche (`PredicatsIdentite.estResponsableProcedure`, prédicat pur) : 403 pour
   tout autre, **Administrateur et PRMP compris**, et pour le responsable d'une autre procédure. Ordre des gardes :
   profil authentifié → identité → corps. Après validation de la fiche en mode électronique, lecture seule (409
   `FICHE_VALIDEE`) ; une révision les rouvre.
4. **Deux journaux.** Le journal global `t_audit_log` reçoit, par l'intercepteur, la route et l'acteur de chaque
   écriture — **jamais les valeurs**. Le journal dédié `t_parametre_interne_journal` porte qui, quand, quel champ,
   ancienne et nouvelle valeur, y compris la désignation et le retrait du responsable (champ `responsable`) ; il n'est
   servi qu'au titulaire, avec l'écran des paramètres internes.
5. **La fiche dit le droit au front, sans rien exposer.** `FicheMarcheDto` et `FicheMarcheResumeDto` portent
   `responsableProcedure { im, nom }`, `peutModifierParametresInternes` (vrai pour le titulaire connecté) et l'**état
   seul** des paramètres internes (`COMPLETS` / `INCOMPLETS` / `ABSENTS`). Le front n'ajoute ni rôle de session, ni
   entrée de menu, ni garde de route : le serveur répond 403 (Q6).
6. **Le bilan lit ce contexte** (règles 6, 8, 10, 11, mode électronique seulement, toutes bloquantes) : un membre
   détenteur d'une part ne peut pas être le responsable (dans les deux sens, 409 `MEMBRE_COMMISSION`), le quorum va
   de 2 au nombre de membres, la cérémonie précède la publication, la fiche ne se valide ni sans paramètres complets
   ni sans responsable désigné.

## Conséquences

- Les lectures de la fiche coûtent quelques requêtes de plus (titulaire, paramètres internes, paramètres
  administrables en mode électronique) ; l'état est recalculé à chaque lecture, jamais stocké.
- Les comptes désignables comme responsable sont les contrôleurs de la localité de la fiche et ceux sans localité
  (compétents partout), hors membres détenteurs d'une part ; les membres désignables sont les Présidents, Chefs de
  commission et Membres de la localité. PRMP et UGPM ne sont désignables à aucun des deux titres (parties à la
  procédure).
- Un Administrateur peut être désigné responsable d'une procédure : il lit alors ses paramètres internes à ce titre,
  jamais à celui de son profil.
- Le jour où la plateforme de dépôt existera, c'est elle qui consommera ces paramètres ; leur forme (liste de
  matricules, quorum, date) est celle du cahier des charges, sans engagement sur le protocole de partage de clés.

## Alternatives écartées

- **Un profil de session « Responsable de la procédure »** : un profil vaut pour toutes les procédures, le droit
  voulu vaut pour une seule ; il aurait fallu croiser profil et objet à chaque garde, et le front aurait dû connaître
  un rôle de plus.
- **Une délégation de profil ou un intérim** : ils étendent les droits d'un profil existant vers un autre profil ;
  aucun profil n'a le droit à étendre.
- **Les valeurs dans `t_audit_log`** : lisibles par l'Administrateur, ce que Q7 refuse ; d'où le journal dédié.
- **Les membres détenteurs déduits de l'examen du dossier** (attributaire, co-signataires) : la commission de
  déchiffrement n'est pas la commission d'examen ; c'est un choix du responsable, journalisé.

## Amendement du 2026-10-04 — les détenteurs de parts sont les membres de la CAO (Q11, décision du pilote ; V67)

- **Ce qui change.** Les membres détenteurs d'une part de clé ne sont plus des contrôleurs de la CNM choisis par le
  responsable (« Présidents, Chefs de commission et Membres de la localité »), mais les **membres de la commission d'appel
  d'offres** (qualité `MEMBRE`, hors experts adjoints), désignés par la **PRMP** par une décision, une CAO par DAO, un président
  parmi eux, issus de l'entité contractante ou experts de l'objet du DAO. Ils ont un compte propre, **`MEMBRE_CAO`**, hors
  coquille interne (comme `CANDIDAT`), créé à la désignation et activé par invitation. Exclus par construction : PRMP, UGPM,
  contrôleurs de la CNM, candidats inscrits.
- **Ce qui reste.** Le rôle « Responsable de la procédure » (décision 1), gardien neutre du quorum, de la date de cérémonie et du
  dépositaire ; il conduit la cérémonie et la séance d'ouverture avec le président de la CAO (à confirmer, question 1 de la
  demande 2a). La garde par identité (décision 3), les deux journaux (décision 4), l'état sur la fiche (décision 5), le bilan
  (décision 6). La motivation « commission de déchiffrement ≠ commission d'examen » tient plus encore : les détenteurs ne sont
  plus des contrôleurs.
- **Conséquences sur V50.** `membresCommission` est **dérivé** de la CAO (`PUT …/parametres-internes` ne le reçoit plus : 400) ;
  `GET …/parametres-internes/candidats` répond 410 ; règle 13 `SE_CAO` (la CAO constituée) bloque la validation en mode
  électronique ; la règle 6 (« le responsable ne détient pas de part ») est vraie par construction, et vérifiée quand même ; le
  journal dédié garde la trace des détenteurs à chaque désignation (acteur : la PRMP) ; la composition de la CAO est un **acte
  public** de la PRMP, lisible par qui lit la fiche — le secret (Q7) ne couvre plus que le quorum, la cérémonie, le dépositaire et
  les parts.
- **Référence** : `docs/api-endpoints.md`, § *La commission d'appel d'offres (CAO), détentrice des parts de clé — V67*, et
  `frontend/docs/demande-backend-2026-10-04-commission-appel-offres.md`.
