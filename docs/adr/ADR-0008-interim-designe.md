# ADR-0008 : Intérim désigné — le dispatcheur enregistré est le titulaire, l'intérimaire est tracé à côté

**Statut :** Adopté
**Date :** 2026-09-21
**Origine :** demande front `frontend/docs/demande-backend-2026-09-21-gestion-interim.md` (lot 1 : Président et
Chef de commission), arbitrages du pilote du 2026-09-21. Migration `V34__interim_designe.sql`.

## Contexte

Le pilote a arbitré l'introduction d'un **intérim désigné** : « X (titulaire, Président ou Chef de commission) est
absent du D1 au D2 ; Y (intérimaire) agit à sa place, dans SON périmètre, sous sa PROPRE identité ». Jusqu'ici,
l'intérim n'était qu'une exception auto-déclarée à l'acte (`INTERIM_DISPATCH`, note PDF au visa), et le Président
n'était suppléé par personne.

Deux décisions structurantes se posaient, et elles engagent tout le circuit :

1. **Qui est écrit dans `t_dispatch.IM_CTRL_DISPATCH` quand un intérimaire dispatche ?** Cette colonne n'est pas une
   simple trace : elle est lue par la garde du visa (« seul le dispatcheur vise »), par le retrait (« le dispatcheur
   retire »), et par le **discriminant de la navette à deux niveaux**, qui lit le *profil* du dispatcheur (un CC
   dispatcheur sur un dossier central prouve le chemin Président → CC → Membre). Y écrire l'intérimaire ferait
   basculer le régime de navette d'un dossier central selon la personne qui a cliqué, et priverait le titulaire de
   retour du droit de viser le PV que son intérimaire a dispatché.
2. **Comment les gardes apprennent-elles qu'un connecté supplée quelqu'un ?** La garde centrale
   (`@perm.peutExercer`) est évaluée à chaque `@PreAuthorize` ; le périmètre de visibilité
   (`Visibilite.voitTout()`) en 77 endroits ; les identités (« le dispatcheur », « le CC du circuit », « le CC
   désigné ») dans une dizaine de gardes. Le CC intérimaire du Président doit *voir* toutes les localités pendant
   l'intérim.

## Décision

**1. `IM_CTRL_DISPATCH` porte le TITULAIRE ; l'intérimaire est tracé à côté.** Un dispatch, une réattribution ou un
retrait posé par intérim enregistre le titulaire suppléé comme dispatcheur, et l'intérim dans `ID_INTERIM`
(→ `t_interim.IM_INTERIMAIRE`). Le journal (`INTERIM_DE`, détail « — par intérim de X ») et le chronométrage
(`IM_ACTEUR` = l'intérimaire, `PROFIL` = celui du titulaire, `INTERIM_DE`) disent qui a réellement agi. Le PV, lui,
enregistre l'intérimaire comme viseur (`IM_CTRL_PRESIDENT` / `IM_CTRL_CC`, comme le visa par intérim de 2026-09-01)
et le titulaire dans `INTERIM_DE` : le document imprime celui qui a signé.

C'est le modèle de `ChronometrageService.cloturerExamen` (2026-09-07) : *agir au nom de* n'est pas *être*. Tout
l'aval continue de désigner celui dont c'est le rôle, et qui a cliqué reste lisible.

**2. Un contexte de requête, posé une fois après l'authentification.** `InterimContexteInterceptor` lit les
suppléances actives du connecté (une requête SQL, pour les seuls profils qui peuvent être intérimaires : CC et
Membre) et les pose dans `InterimContexte` (ThreadLocal, effacé en fin de requête). `PermissionService`,
`Visibilite` et les gardes d'identité le lisent ; aucune ne fait de requête, aucune n'est oubliée.
`PredicatsIdentite` reçoit des surcharges « parmi les identités » (la sienne plus celles des titulaires suppléés),
qui restent pures.

**3. La pièce dans une table à part**, `t_interim_piece`, à clé partagée : `t_interim` est lue à chaque requête d'un
CC ou d'un Membre et ne doit jamais charger un PDF. En base et non sur le FSX, pour la raison de V11 (geste
atomique, pas de fichier orphelin au rollback).

**Options écartées.**

- *Écrire l'intérimaire dans `IM_CTRL_DISPATCH`* : basculement du régime de navette selon la personne, titulaire de
  retour privé du visa, et chaque garde d'aval à réécrire pour reconnaître « le dispatcheur ou son titulaire ».
- *Interroger `t_interim` dans chaque garde* : autant de requêtes que de gardes par requête HTTP, et la certitude
  d'en oublier une parmi les 77 points de visibilité.
- *Étendre `t_delegation_profil`* : la délégation est par profil et ascendante ; l'intérim est nominatif, daté, et
  descend (un Membre supplée un CC). Ce ne sont pas les mêmes objets.

## Conséquences

**Ce qui devient possible :** le Président est suppléable (pré-dispatch central, visa, part Président) ; un CC
absent l'est par un Membre de sa localité ; qui supplée qui se lit à l'avance (`/api/interims/mes`, annuaire), et
chaque acte posé par intérim est tracé « par intérim de X » sans qu'aucune règle du circuit soit réécrite.

**Ce qu'il faut surveiller :**

- **Le ThreadLocal.** Hors requête HTTP (tâche de fond, service appelé en direct), le contexte est vide : aucun droit
  étendu, jamais d'exception. Une évolution qui ferait agir un intérimaire depuis une tâche asynchrone devrait poser
  le contexte elle-même.
- **`IM_CTRL_DISPATCH` ne dit plus « qui a cliqué »** quand `ID_INTERIM` est non nul. Toute lecture qui voudrait
  l'auteur réel doit passer par l'intérim ou par le journal.
- **Le coût** : +1 ordre SQL par requête d'un CC ou d'un Membre. L'accueil « À faire » et la page dossier passent de
  13 à 14 pour ces deux profils (test figé).
- **Hors lot, non étendus** : l'examen et la soumission du PV (actes de l'attributaire), la part Membre, les lettres
  de renvoi — l'intérimaire y est jugé sur son propre profil.

## Marche arrière

La ressource se retire en cessant d'enregistrer le contexte (l'intercepteur) : toutes les gardes retrouvent leur
comportement d'avant, puisque le contexte vide n'étend rien. Les colonnes de V34 restent inertes (nulles). Ce qui
ne se défait pas sans reprise : les dispatchs déjà posés par intérim portent le titulaire comme dispatcheur — ce
qui est, précisément, ce que le circuit attend.
